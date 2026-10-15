package com.mizania.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Money in each bank account, with a way to type a balance in,
 * and how "allowed today" is worked out from it.
 */
@Composable
fun BalanceSheet(db: Db, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var local by remember { mutableIntStateOf(0) }
    val accounts = remember { db.accounts() }
    val balances = remember(local) { db.bankBalances() }
    val now = remember(local) { Budget.allowance(db) }
    val cycle = now.cycle.range
    val cats = remember { db.categories() }
    val spent = remember(local) { db.spentByCategory(cycle.from, cycle.to) }
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var value by remember { mutableStateOf("") }

    // Accounts with no SMS yet still get a row, so a balance can be typed in.
    val rows = balances.map { it.accountId to it.acctNo } +
        accounts.filter { a -> balances.none { it.accountId == a.id } }.map { it.id to "" }

    SheetFrame(onDone) {
        Text("رصيدك", style = MaterialTheme.typography.titleLarge)
        Text("آخر رصيد وصل بمسج البنك لكل حساب. إذا مسج ما فيه رصيد، بنطرح أو بنضيف مبلغه على آخر رصيد معروف.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        rows.forEachIndexed { i, key ->
            if (i > 0) HorizontalDivider(color = Ui.p.line)
            val b = balances.firstOrNull { it.accountId == key.first && it.acctNo == key.second }
            val acc = accounts.firstOrNull { it.id == key.first }
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (acc?.name ?: "حساب") + if (key.second.isNotEmpty()) "  ${key.second}" else "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            when {
                                b?.balance == null -> "البنك ما بعت رصيد لهالحساب لسا"
                                b.estimated -> "تقدير: آخر رصيد ${Period.stamp(b.asOf ?: 0)} والحركات بعده"
                                else -> "حسب آخر مسج ${Period.stamp(b.asOf ?: 0)}"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val excluded = db.isExcluded(key.first, key.second)
                    if (b?.balance != null) {
                        Amount(b.balance, 18.sp, if (excluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    } else Text("—", fontSize = 18.sp)
                }
                if (editing == key) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = value, onValueChange = { value = it }, singleLine = true,
                            placeholder = { Text("الرصيد هلأ") }, suffix = { Text("د.أ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        )
                        TextButton(
                            enabled = value.replace(',', '.').toDoubleOrNull() != null,
                            onClick = {
                                db.setBalance(key.first, key.second, value.replace(',', '.').toDouble())
                                db.reconcileDirections()
                                AllowanceWidget.refreshAll(ctx)
                                editing = null; value = ""; local++
                            },
                        ) { Text("حفظ") }
                    }
                } else {
                    Row {
                        TextButton(onClick = { editing = key; value = "" }) {
                            Text(if (b?.balance == null) "أدخل الرصيد" else "تعديل الرصيد")
                        }
                        val excluded = db.isExcluded(key.first, key.second)
                        TextButton(onClick = {
                            db.setExcluded(key.first, key.second, !excluded)
                            AllowanceWidget.refreshAll(ctx); local++
                        }) {
                            Text(if (excluded) "رجّعه للمجموع" else "ما تحسبه بالمجموع",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (now.balance != null) {
            Spacer(Modifier.height(12.dp))
            Text("كيف بنحسب «مسموحلك اليوم»", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp)
            ) {
                KV("رصيدك", fmt(now.balance))
                Text("محجوز لهالشهر (أشّر على اللي دفعته):", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                cats.filter { it.type == Db.FIXED || it.type == Db.RESERVE }.forEach { c ->
                    val left = max(0.0, c.budget - (spent[c.id] ?: 0.0))
                    if (left <= 0) return@forEach
                    val paid = db.isPaid(c.id, cycle.from)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { db.setPaid(c.id, cycle.from, !paid); AllowanceWidget.refreshAll(ctx); local++ },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = paid, onCheckedChange = {
                            db.setPaid(c.id, cycle.from, it); AllowanceWidget.refreshAll(ctx); local++
                        })
                        Text("${c.icon} ${c.name}", Modifier.weight(1f),
                            color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text(if (paid) "دفعته" else "− " + fmt(left), fontFamily = Ui.numbers,
                            color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Ui.p.line)
                KV("بتقدر تصرف", fmt(now.spendable ?: 0.0), bold = true,
                    color = if ((now.spendable ?: 0.0) < 0) Ui.p.pomegranate else Ui.p.mint)
                KV("÷ الأيام لحد الراتب", "${now.daysLeft}")
                KV("= مسموحلك باليوم", fmt(now.allowance), bold = true)
            }
            Text("الفاتورة بتنشال من الحجز لحالها لما تصنف دفعتها على بندها، أو لما تأشر عليها هون. ولما ينزل الراتب، الحساب بيبلش من جديد.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("تم", fontWeight = FontWeight.SemiBold) }
    }
}
