@file:OptIn(ExperimentalLayoutApi::class)

package com.mizania.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.max

fun directionLabel(owedToMe: Boolean) = if (owedToMe) "إلي" else "عليّ"

fun entryLabel(d: Debt, kind: Int) = when {
    kind == Db.PAY && d.owedToMe -> "رجعلي"
    kind == Db.PAY -> "سددت"
    d.owedToMe -> "أعطيته كمان"
    else -> "استلفت كمان"
}

private fun parseAmount(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

@Composable
fun DebtsScreen(db: Db, tick: Int, refresh: () -> Unit) {
    val debts = remember(tick) { db.debts() }
    var open by remember { mutableStateOf<Long?>(null) }
    val iOwe = debts.filter { !it.owedToMe }.sumOf { max(0.0, it.remaining) }
    val owedMe = debts.filter { it.owedToMe }.sumOf { max(0.0, it.remaining) }
    val active = debts.filter { !it.closed }
    val done = debts.filter { it.closed }

    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
        item {
            Section("الملخص") {
                KV("عليّ للناس", fmt(iOwe), color = MaterialTheme.colorScheme.error, bold = true)
                KV("إلي عند الناس", fmt(owedMe), color = MaterialTheme.colorScheme.secondary, bold = true)
                KV("الصافي", fmt(owedMe - iOwe))
            }
        }
        if (debts.isEmpty()) item {
            Text("ما في ديون مسجلة. اضغط ➕ عشان تضيف دين إلك أو عليك.", Modifier.padding(24.dp))
        }
        items(active, key = { it.id }) { d -> DebtCard(d) { open = d.id } }
        if (done.isNotEmpty()) item {
            Text("مسكّرة", Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(done, key = { it.id }) { d -> DebtCard(d) { open = d.id } }
    }

    open?.let { id -> DebtDialog(db, id) { open = null; refresh() } }
}

@Composable
fun DebtCard(d: Debt, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.person, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (d.closed) "خلص ✓" else directionLabel(d.owedToMe),
                    fontWeight = FontWeight.Bold,
                    color = when {
                        d.closed -> MaterialTheme.colorScheme.onSurfaceVariant
                        d.owedToMe -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
            KV("المبلغ الكلي", fmt(d.total))
            KV(if (d.owedToMe) "رجعلي" else "سددت", fmt(d.paid))
            KV("الباقي", fmt(max(0.0, d.remaining)), bold = true)
            if (d.total > 0) {
                LinearProgressIndicator(
                    progress = { (d.paid / d.total).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (d.note.isNotBlank()) {
                Text(d.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** New debt, or edit one ([debt] not null). */
@Composable
fun DebtFormDialog(db: Db, debt: Debt?, onDone: () -> Unit) {
    var person by remember { mutableStateOf(debt?.person ?: "") }
    var owedToMe by remember { mutableStateOf(debt?.owedToMe ?: false) }
    var amount by remember {
        mutableStateOf(debt?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var note by remember { mutableStateOf(debt?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val value = parseAmount(amount)

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (debt == null) "دين جديد" else "تعديل الدين") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("مين الشخص") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("الدين", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!owedToMe, { owedToMe = false }, { Text("عليّ (أنا مستلف)") })
                    FilterChip(owedToMe, { owedToMe = true }, { Text("إلي (أنا مسلّف)") })
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("المبلغ الأصلي") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة (اختياري)") },
                    modifier = Modifier.fillMaxWidth())
                if (confirmDelete) {
                    Text("متأكد؟ رح ينمسح الدين وكل دفعاته.", color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = person.isNotBlank() && value != null && value > 0,
                onClick = {
                    db.saveDebt(debt?.id ?: 0, person.trim(), owedToMe, value ?: 0.0, note.trim())
                    onDone()
                },
            ) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                if (debt != null) {
                    TextButton(onClick = {
                        if (confirmDelete) { db.deleteDebt(debt.id); onDone() } else confirmDelete = true
                    }) { Text(if (confirmDelete) "أكيد احذف" else "حذف", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDone) { Text("إلغاء") }
            }
        },
    )
}

/** A debt's history, plus adding a repayment or an increase. */
@Composable
fun DebtDialog(db: Db, id: Long, onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var local by remember { mutableIntStateOf(0) }
    val d = remember(local) { db.debt(id) }
    if (d == null) {
        LaunchedEffect(id) { onDone() }
        return
    }
    val entries = remember(local) { db.debtEntries(id) }
    var editing by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(Db.PAY) }
    var note by remember { mutableStateOf("") }
    var fixing by remember { mutableStateOf(false) }
    var fixValue by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val value = parseAmount(amount)

    // Saves whatever is typed but not yet saved (new payment, corrected total), so "تم" never drops it.
    fun savePending(): Boolean {
        var saved = false
        if (fixing) parseAmount(fixValue)?.let { db.setDebtPaid(d.id, it); fixing = false; saved = true }
        val v = parseAmount(amount)
        if (v != null && v > 0) {
            db.addDebtEntry(d.id, kind, v, System.currentTimeMillis(), note.trim(), null)
            android.widget.Toast.makeText(ctx, "انسجلت: ${entryLabel(d, kind)} ${fmt(v)}", android.widget.Toast.LENGTH_SHORT).show()
            amount = ""; note = ""; saved = true
        }
        return saved
    }

    if (editing) {
        DebtFormDialog(db, d) { editing = false; local++ }
        return
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("${d.person} — ${directionLabel(d.owedToMe)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KV("المبلغ الأصلي", fmt(d.amount))
                if (d.added > 0) KV("زيادات", fmt(d.added))
                KV(if (d.owedToMe) "رجعلي" else "سددت", fmt(d.paid))
                KV("الباقي", fmt(max(0.0, d.remaining)), bold = true,
                    color = if (d.closed) MaterialTheme.colorScheme.secondary else Color0)
                if (fixing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = fixValue, onValueChange = { fixValue = it }, singleLine = true,
                            label = { Text(if (d.owedToMe) "رجعلي لحد هلأ" else "سددت لحد هلأ") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        TextButton(enabled = parseAmount(fixValue) != null, onClick = {
                            db.setDebtPaid(d.id, parseAmount(fixValue) ?: 0.0); fixing = false; local++
                        }) { Text("حفظ") }
                    }
                    Text("بيتسجل كـ«تعديل»، مش كدفعة جديدة.", style = MaterialTheme.typography.bodySmall)
                } else {
                    TextButton(onClick = {
                        fixing = true
                        fixValue = if (d.paid % 1.0 == 0.0) d.paid.toLong().toString() else d.paid.toString()
                    }) { Text(if (d.owedToMe) "تعديل اللي رجعلي" else "تعديل المسدد") }
                }
                if (d.note.isNotBlank()) Text(d.note, style = MaterialTheme.typography.bodySmall)
                ReceiptButtons("كشف الدين (PDF)", "debt-${d.id}.pdf") { Receipt.forDebt(it, d.id) }

                Spacer(Modifier.height(12.dp))
                Text("دفعة جديدة", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(kind == Db.PAY, { kind = Db.PAY }, { Text(entryLabel(d, Db.PAY)) })
                    FilterChip(kind == Db.ADD, { kind = Db.ADD }, { Text(entryLabel(d, Db.ADD)) })
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text("المبلغ") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة (اختياري)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    enabled = value != null && value > 0,
                    onClick = { savePending(); local++ },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text("سجّل الدفعة") }

                if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("السجل", fontWeight = FontWeight.SemiBold)
                    entries.forEach { e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (e.note == Db.CORRECTION) "تعديل " + (if (e.amount >= 0) "+" else "−") + fmt(kotlin.math.abs(e.amount))
                                    else "${entryLabel(d, e.kind)} ${fmt(e.amount)}"
                                )
                                Text(
                                    Period.stamp(e.time) + (if (e.txnId != null) " · من البنك" else "") +
                                        (if (e.note.isNotBlank() && e.note != Db.CORRECTION) " · ${e.note}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { db.deleteDebtEntry(e.id); local++ }) {
                                Icon(Icons.Filled.Delete, "حذف")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            val pending = (value != null && value > 0) || (fixing && parseAmount(fixValue) != null)
            TextButton(onClick = { savePending(); onDone() }) { Text(if (pending) "حفظ وإغلاق" else "تم") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    if (confirmDelete) { db.deleteDebt(d.id); onDone() } else confirmDelete = true
                }) { Text(if (confirmDelete) "أكيد احذف" else "حذف الدين", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { editing = true }) { Text("تعديل") }
            }
        },
    )
}

private val Color0 = androidx.compose.ui.graphics.Color.Unspecified
