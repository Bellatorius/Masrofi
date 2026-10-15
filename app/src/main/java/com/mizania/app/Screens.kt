@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mizania.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Amber = Color(0xFFE6A100)

// ---------------- shared pieces ----------------

/** Amount in the numbers face with a smaller currency after it. */
@Composable
fun Amount(
    v: Double, size: TextUnit, color: Color = Color.Unspecified,
    sign: String = "", weight: FontWeight = FontWeight.SemiBold,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(sign + String.format(Locale.US, "%,.2f", v), fontFamily = Ui.numbers, fontSize = size,
            fontWeight = weight, color = color, maxLines = 1)
        Text(" د.أ", fontSize = size * 0.5f, color = color.orElse(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(bottom = (size.value * 0.12f).dp))
    }
}

private fun Color.orElse(other: Color) = if (this == Color.Unspecified) other else this

/** A titled group on the page; the rows sit on one raised sheet. */
@Composable
fun Section(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            action?.invoke()
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .background(Ui.p.glass)
                .border(1.dp, Ui.p.line, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content,
        )
    }
}

@Composable
fun KV(k: String, v: String, color: Color = Color.Unspecified, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Text(v, color = color, fontFamily = Ui.numbers,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, fontSize = if (bold) 16.sp else 15.sp)
    }
}

/** Emoji on a soft tile of the category's color. */
@Composable
fun IconBadge(icon: String, color: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = (size.value * 0.46f).sp) }
}

@Composable
fun CatBar(cat: Category?, name: String, spent: Double, budget: Double, onClick: (() -> Unit)? = null) {
    val base = cat?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(cat?.icon ?: "❔", base, 36.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (budget > 0) {
                    val left = budget - spent
                    Text(
                        if (left >= 0) "باقي ${fmt(left)}" else "زيادة ${fmt(-left)}",
                        fontFamily = Ui.numbers, fontSize = 13.sp,
                        color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else Ui.p.pomegranate,
                    )
                } else {
                    Text(fmt(spent), fontFamily = Ui.numbers, fontSize = 13.sp)
                }
            }
            if (budget > 0) {
                val grow = appearProgress(150)
                val p = (spent / budget).toFloat() * grow
                val color = when {
                    p >= 1f -> Ui.p.pomegranate
                    p >= 0.8f -> Amber
                    else -> base
                }
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = base.copy(alpha = 0.14f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
fun TxnRow(t: Txn, cat: Category?, accountName: String? = null, onClick: () -> Unit) {
    val sub = when {
        t.income -> t.kind
        cat != null -> cat.name
        else -> "بدها تصنيف"
    } + (if (accountName != null) "، $accountName" else "")
    val badgeColor = when {
        t.income -> Ui.p.mint
        cat != null -> Color(cat.color)
        else -> Ui.p.pomegranate
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(if (t.income) "💵" else cat?.icon ?: "❔", badgeColor)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.merchant.ifEmpty { t.kind }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = if (!t.income && cat == null) Ui.p.pomegranate else MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.note.isNotBlank()) {
                Text(t.note, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Amount(t.amount, 16.sp, if (t.income) Ui.p.mint else MaterialTheme.colorScheme.onSurface,
                sign = if (t.income) "+" else "")
            Text(Period.clock(t.time), fontFamily = Ui.numbers, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A spend waiting for a budget line: one tap on a suggested line classifies it,
 * swiping toward the end ignores it, swiping toward the start opens the full sheet.
 */
@Composable
fun PendingRow(db: Db, t: Txn, quick: List<Category>, onOpen: (Long) -> Unit, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.EndToStart -> {
                db.ignoreTxn(t.id); Notifier.cancel(ctx, t.id); refresh(); true
            }
            SwipeToDismissBoxValue.StartToEnd -> { onOpen(t.id); false }
            else -> false
        }
    })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toIgnore = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .background(if (toIgnore) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (toIgnore) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Text(if (toIgnore) "تجاهل" else "تفاصيل", fontWeight = FontWeight.SemiBold) }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            TxnRow(t, null) { onOpen(t.id) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quick.forEach { c ->
                    AssistChip(
                        onClick = {
                            if (db.needsLink(c, t.income)) onOpen(t.id) else {
                                db.updateTxn(t.id, c.id, t.note)
                                Notifier.cancel(ctx, t.id)
                                Processor.afterCategorized(ctx, t.id, c.id, notifyRecorded = false)
                                refresh()
                            }
                        },
                        label = { Text("${c.icon}  ${c.name}", maxLines = 1) },
                        shape = RoundedCornerShape(50),
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(c.color).copy(alpha = 0.12f)),
                        border = null,
                    )
                }
                AssistChip(onClick = { onOpen(t.id) }, label = { Text("بند ثاني") }, shape = RoundedCornerShape(50))
            }
        }
    }
}

/** Four budget lines to offer for one-tap classification: most used first, then day-to-day ones. */
fun quickCategories(db: Db, cats: List<Category>): List<Category> {
    val byId = cats.associateBy { it.id }
    val top = db.topCategories(4).mapNotNull { byId[it] }
    return (top + cats.filter { it.type == Db.DAILY } + cats).distinctBy { it.id }.take(4)
}

/** Account filter, shown only when there is more than one account. */
@Composable
fun AccountChips(accounts: List<Account>, selected: Long?, onSelect: (Long?) -> Unit) {
    if (accounts.size < 2) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("كل الحسابات") },
            shape = RoundedCornerShape(50))
        accounts.forEach { a ->
            FilterChip(selected = selected == a.id, onClick = { onSelect(a.id) }, label = { Text(a.name) },
                shape = RoundedCornerShape(50))
        }
    }
}

/** Two or three choices as one pill control (day / week / month). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) Ui.p.hero else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, color = if (on) Ui.p.onPine else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

private val typeNames = mapOf(Db.FIXED to "المصاريف الثابتة", Db.DAILY to "المصروف اليومي", Db.RESERVE to "الاحتياط والادخار")

private val shortDays = arrayOf("", "أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

/** Labels for a bar per day of [r]: weekday names for a week, day numbers otherwise. */
private fun dayLabels(r: Range): List<String> {
    val c = Calendar.getInstance().apply { timeInMillis = r.from }
    return List(r.days) {
        val l = if (r.days <= 7) shortDays[c.get(Calendar.DAY_OF_WEEK)] else c.get(Calendar.DAY_OF_MONTH).toString()
        c.add(Calendar.DAY_OF_YEAR, 1)
        l
    }
}

private fun todayIndex(r: Range): Int {
    val now = System.currentTimeMillis()
    return if (now in r.from until r.to) ((now - r.from) / Period.DAY).toInt() else -1
}

// ---------------- home ----------------

/**
 * The month as a row of days: green = stayed under the daily limit, red = went over,
 * the gold outline is today, faded ones are still ahead.
 */
@Composable
fun DayStrip(perDay: DoubleArray, limit: Double, today: Int, onDark: Color) {
    Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in perDay.indices) {
            val spent = perDay[i]
            val c = when {
                i > today -> onDark.copy(alpha = 0.10f)
                spent <= 0.0 -> onDark.copy(alpha = 0.22f)
                limit > 0 && spent > limit -> Ui.p.pomegranate
                else -> Ui.p.mint
            }
            Box(
                Modifier.weight(1f).height(if (i == today) 26.dp else 18.dp).align(Alignment.Bottom)
                    .clip(RoundedCornerShape(3.dp)).background(c)
                    .then(if (i == today) Modifier.border(2.dp, Ui.p.dinar, RoundedCornerShape(3.dp)) else Modifier)
            )
        }
    }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 4..11 -> "صباح الخير"
        in 12..16 -> "نهارك سعيد"
        else -> "مسا الخير"
    }
}

@Composable
fun HomeScreen(
    db: Db, tick: Int, missingPerms: Boolean,
    onAskPerms: () -> Unit, onOpen: (Long) -> Unit, onGoSettings: () -> Unit, onGoDebts: () -> Unit,
    onTriage: () -> Unit, refresh: () -> Unit,
) {
    val al = remember(tick) { Budget.allowance(db) }
    val ci = al.cycle
    val cycle = ci.range
    val cats = remember(tick) { db.categories() }
    val spent = remember(tick) { db.spentByCategory(cycle.from, cycle.to) }
    val pending = remember(tick) { db.pending() }
    val debts = remember(tick) { db.debts().filter { !it.closed } }
    val senders = remember(tick) { db.senders() }
    val quick = remember(tick) { quickCategories(db, cats) }
    val dailyIds = cats.filter { it.type == Db.DAILY }.map { it.id }.toSet()
    val perDayLimit = cats.filter { it.type == Db.DAILY }.sumOf { it.budget } / cycle.days
    val perDay = remember(tick) { db.spentPerDay(cycle.from, cycle.to, dailyIds) }
    val totalOut = spent.values.sum()
    val p = Ui.p
    var showBalance by remember { mutableStateOf(false) }
    var catSheet by remember { mutableStateOf<Long?>(null) }
    var showMath by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(greeting(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onGoSettings) {
                    Icon(Icons.Filled.Settings, "الإعدادات", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(
                Modifier.fillMaxWidth().clickable { showBalance = true }
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
            ) {
                if (al.balance != null) {
                    Text("رصيدك بالبنك", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedAmount(al.balance, 38.sp, if (al.balance < 0) p.pomegranate else MaterialTheme.colorScheme.onSurface,
                        weight = FontWeight.Bold)
                } else {
                    Text("رصيدك لسا مش معروف", style = MaterialTheme.typography.titleLarge)
                    Text("بيبين من أول مسج فيه «الرصيد»، أو اضغط هون وأدخله.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                SalaryLine(ci)
            }
        }
        if (missingPerms) item {
            Notice("التطبيق بحاجة لصلاحية المسجات والإشعارات",
                "إذا الزر ما فتح شي: إعدادات التطبيق، ثم ⋮، ثم «السماح بالإعدادات المقيدة».", "إعطاء الصلاحيات", onAskPerms)
        }
        if (cats.isNotEmpty() && cats.all { it.budget <= 0.0 }) item {
            Notice("حدد قديش بدك تصرف على كل بند",
                "من الإعدادات ← البنود: اكتب ميزانية شهرية لكل بند (إيجار، فواتير، مواصلات…). هيك بيصير «مسموحلك اليوم» دقيق.",
                "تحديد الميزانية", onGoSettings)
        }
        if (senders.isEmpty()) item {
            Notice("لسا ما اخترت مرسل البنك", "اختار مين بيبعتلك مسجات البنك عشان التطبيق يتابعها.",
                "اختيار من الإعدادات", onGoSettings)
        }
        item {
            val over = al.leftToday < 0
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(28.dp, RoundedCornerShape(30.dp), ambientColor = p.heroStart, spotColor = p.heroStart)
                    .clip(RoundedCornerShape(30.dp)).background(p.hero)
                    .clickable { showBalance = true }.padding(22.dp)
            ) {
                Text(if (over) "زدت عن حدك اليوم" else "مسموحلك تصرف اليوم",
                    color = p.onPine.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                AnimatedAmount(if (over) -al.leftToday else al.leftToday, 52.sp, if (over) Color(0xFFFFC2CC) else p.onPine,
                    weight = FontWeight.Bold)
                if (al.spendable != null) {
                    Text(
                        if (al.spendable < 0) "رصيدك أقل من التزاماتك بـ ${fmt(-al.spendable)}"
                        else "من رصيدك، بعد ما حجزنا ${fmt(al.committed)} للفواتير والادخار، على ${al.daysLeft} يوم",
                        color = if (al.spendable < 0) Color(0xFFFFC2CC) else p.onPine.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    HeroStat("صرفت اليوم", fmt(al.spentToday), Modifier.weight(1f))
                    HeroStat("مسموحلك بكرا", fmt(al.tomorrow), Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                DayStrip(perDay, perDayLimit, todayIndex(cycle), p.onPine)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(Period.label(Period.MODE_DAY, Range(cycle.from, cycle.from + Period.DAY)).substringAfter(' '),
                        Modifier.weight(1f), color = p.onPine.copy(alpha = 0.78f), fontSize = 11.sp, fontFamily = Ui.numbers)
                    Text("الراتب الجاي ${Period.dayMonth(ci.nextSalary)}", color = p.onPine.copy(alpha = 0.78f),
                        fontSize = 11.sp, fontFamily = Ui.numbers)
                }
            }
        }
        if (pending.isNotEmpty()) item {
            TriageCard(pending, cats, onTriage)
        }
        item {
            Section("الشهر (${Period.label(Period.MODE_MONTH, cycle)})") {
                val otherIn = remember(tick) { db.otherIncome(cycle.from, cycle.to) }
                val startBal = remember(tick) { db.totalBalanceAt(cycle.from) }
                val income = ci.salary + otherIn
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val slices = cats.map { c -> Slice(c.name, spent[c.id] ?: 0.0, Color(c.color)) } +
                        listOf(Slice("بدون بند", spent[null] ?: 0.0, Color(0xFF9E9E9E)))
                    Box(Modifier.width(132.dp)) {
                        if (income > 0) {
                            DonutChart(slices, "${((totalOut / income) * 100).toInt()}%", "من الدخل",
                                size = 128.dp, showLegend = false)
                        } else {
                            DonutChart(slices, "", "", size = 128.dp, showLegend = false)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("دخلك", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Amount(income, 22.sp, p.mint)
                        Spacer(Modifier.height(8.dp))
                        Text("صرفت", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Amount(totalOut, 22.sp)
                    }
                }
                // Everything adds up to the real balance; whatever the SMS did not explain shows as its own line.
                if (al.balance != null) {
                    TextButton(onClick = { showMath = !showMath }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(if (showMath) "إخفاء الحسبة" else "كيف وصلنا لرصيدك؟")
                    }
                }
                AnimatedVisibility(showMath && al.balance != null) { Column {
                if (al.balance != null) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = p.line)
                    if (startBal != null) KV(if (ci.bySalary) "رصيدك قبل الراتب" else "رصيدك أول الشهر", fmt(startBal))
                    KV(if (ci.received) "+ الراتب" else "+ الراتب (لسا ما نزل)", fmt(ci.salary))
                    if (otherIn > 0) KV("+ دخل ثاني (كليك وتحويلات واردة)", fmt(otherIn))
                    KV("− صرفت", fmt(totalOut))
                    val expected = (startBal ?: 0.0) + income - totalOut
                    val gap = al.balance - expected
                    if (startBal != null && kotlin.math.abs(gap) >= 0.5) {
                        KV(if (gap < 0) "− حركات ما وصل عليها مسج" else "+ حركات ما وصل عليها مسج",
                            fmt(kotlin.math.abs(gap)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.line)
                    KV("= رصيدك هلأ", fmt(al.balance), bold = true)
                } } }
            }
        }
        if (debts.isNotEmpty()) item {
            Section("الديون", action = { TextButton(onClick = onGoDebts) { Text("التفاصيل") } }) {
                val iOwe = debts.filter { !it.owedToMe }.sumOf { it.remaining }
                val owedMe = debts.filter { it.owedToMe }.sumOf { it.remaining }
                if (iOwe > 0) KV("عليّ للناس", fmt(iOwe), color = p.pomegranate, bold = true)
                if (owedMe > 0) KV("إلي عند الناس", fmt(owedMe), color = p.mint, bold = true)
            }
        }
        for (type in listOf(Db.DAILY, Db.FIXED, Db.RESERVE)) {
            val group = cats.filter { it.type == type }
            if (group.isEmpty()) continue
            item(key = "type$type") {
                Section(typeNames[type] ?: "") {
                    group.forEach { c -> CatBar(c, c.name, spent[c.id] ?: 0.0, c.budget) { catSheet = c.id } }
                    if (type == Db.DAILY) {
                        val unc = spent[null] ?: 0.0
                        if (unc > 0) CatBar(null, "بدون بند", unc, 0.0) { catSheet = -1L }
                    }
                }
            }
        }
    }

    if (showBalance) BalanceSheet(db) { showBalance = false; refresh() }
    catSheet?.let { id ->
        CategorySheet(db, cats.firstOrNull { it.id == id }, cycle, "هالشهر",
            onOpen = { tid -> catSheet = null; onOpen(tid) }, onDone = { catSheet = null })
    }
}

/** Where the salary stands: landed (when and how much), late, or not yet. */
@Composable
fun SalaryLine(ci: CycleInfo) {
    val p = Ui.p
    val (dot, text) = when {
        ci.late -> p.dinar to "الراتب متأخر، كان متوقع ${Period.dayMonth(ci.nextSalary)}"
        ci.received -> p.mint to "نزل الراتب ${Period.dayMonth(ci.salaryAt ?: ci.range.from)}: ${fmt(ci.salary)}"
        else -> MaterialTheme.colorScheme.outline to "الراتب لسا ما نزل"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
    if (!ci.late) {
        val days = max(0, ((ci.nextSalary - Period.day().from) / Period.DAY).toInt())
        Text(
            "الراتب الجاي تقريباً ${Period.dayMonth(ci.nextSalary)}" + if (days > 0) "، بعد $days يوم" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Ui.p.onPine.copy(alpha = 0.78f), fontSize = 12.sp)
        Text(value, color = Ui.p.onPine, fontFamily = Ui.numbers, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** A setup step the user still has to do. */
@Composable
fun Notice(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp))
            .background(Ui.p.dinar.copy(alpha = 0.16f)).padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = onAction, modifier = Modifier.padding(top = 10.dp), shape = RoundedCornerShape(12.dp)) { Text(action) }
    }
}

// ---------------- transactions ----------------

private fun dayHeader(dayStart: Long): String {
    val today = Period.day().from
    return when {
        dayStart == today -> "اليوم"
        dayStart == today - Period.DAY -> "مبارح"
        else -> Period.label(Period.MODE_DAY, Range(dayStart, dayStart + Period.DAY))
    }
}

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun TxnsScreen(db: Db, tick: Int, onOpen: (Long) -> Unit, refresh: () -> Unit, onTriage: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlyPending by rememberSaveable { mutableStateOf(false) }
    var acc by rememberSaveable { mutableStateOf<Long?>(null) }
    val catList = remember(tick) { db.categories() }
    val cats = catList.associateBy { it.id }
    val quick = remember(tick) { quickCategories(db, catList) }
    val accounts = remember(tick) { db.accounts() }
    val accNames = if (accounts.size > 1) accounts.associate { it.id to it.name } else emptyMap()
    val all = remember(tick, onlyPending) { if (onlyPending) db.pending() else db.recent(2000) }
    val q = query.trim().lowercase()
    val list = all.filter { t ->
        (acc == null || t.accountId == acc) && (q.isEmpty() ||
            t.merchant.lowercase().contains(q) || t.note.lowercase().contains(q) || t.kind.contains(q) ||
            (cats[t.categoryId]?.name?.contains(q) == true) ||
            String.format(java.util.Locale.US, "%.3f", t.amount).startsWith(q.replace(",", ".")) ||
            String.format(java.util.Locale.US, "%.2f", t.amount).startsWith(q.replace(",", ".")))
    }
    val days = list.groupBy { startOfDay(it.time) }.toList().sortedByDescending { it.first }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            androidx.compose.material3.OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("ابحث باسم المحل، المبلغ، أو الملاحظة") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "مسح البحث") }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            Segmented(listOf("كل الحركات", "بدها تصنيف"), if (onlyPending) 1 else 0) { onlyPending = it == 1 }
            AccountChips(accounts, acc) { acc = it }
            if (onlyPending && list.size > 1) {
                androidx.compose.material3.Button(
                    onClick = onTriage, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(48.dp),
                ) { Text("صنّف بسرعة، وحدة ورا الثانية") }
            }
            if (q.isNotEmpty()) {
                Text("${list.size} نتيجة، مجموعها ${fmt(list.filter { !it.income }.sumOf { it.amount })}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        }
        if (list.isEmpty()) item {
            Text(
                if (onlyPending) "كل الحركات مصنفة." else "لسا ما في حركات. استورد مسجات هالشهر من الإعدادات، أو استنى أول مسج من البنك.",
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for ((day, txns) in days) {
            item(key = "d$day") {
                val out = txns.filter { !it.income }.sumOf { it.amount }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayHeader(day), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (out > 0) Text("−" + fmt(out), fontFamily = Ui.numbers, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onlyPending) {
                items(txns, key = { it.id }) { t ->
                    Box(Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp)) { PendingRow(db, t, quick, onOpen, refresh) }
                }
            } else {
                item(key = "g$day") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        txns.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = Ui.p.line)
                            TxnRow(t, cats[t.categoryId], accNames[t.accountId]) { onOpen(t.id) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- reports ----------------

@Composable
fun ReportsScreen(db: Db, tick: Int, onOpen: (Long) -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(Period.MODE_MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var acc by rememberSaveable { mutableStateOf<Long?>(null) }
    var catSheet by remember { mutableStateOf<Long?>(null) }
    val monthInfo = remember(tick, mode, offset) { if (mode == Period.MODE_MONTH) Budget.cycle(db, offset) else null }
    val range = remember(tick, mode, offset) { monthInfo?.range ?: Period.range(mode, offset, 1) }
    val accounts = remember(tick) { db.accounts() }
    val accNames = if (accounts.size > 1) accounts.associate { it.id to it.name } else emptyMap()
    val all = remember(tick, mode, offset) { db.txns(range.from, range.to) }
    val list = if (acc == null) all else all.filter { it.accountId == acc }
    val cats = remember(tick) { db.categories() }
    val catMap = cats.associateBy { it.id }

    val outs = list.filter { !it.income }
    val out = outs.sumOf { it.amount }
    val inc = list.filter { it.income }.sumOf { it.amount }
    val now = System.currentTimeMillis()
    val elapsed = if (now in range.from until range.to)
        min(range.days, ((now - range.from) / Period.DAY).toInt() + 1) else range.days
    val byCat = outs.groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }
    val top = outs.filter { it.merchant.isNotBlank() }.groupBy { it.merchant }
        .mapValues { (_, v) -> v.sumOf { it.amount } to v.size }
        .entries.sortedByDescending { it.value.first }.take(5)

    val cycleDays = remember(tick) { Budget.cycle(db).range.days }
    val dailyIds = cats.filter { it.type == Db.DAILY }.map { it.id }.toSet()
    val dailyBudget = cats.filter { it.type == Db.DAILY }.sumOf { it.budget }
    val perDayLimit = dailyBudget / cycleDays
    val dailyTarget = perDayLimit * range.days
    val dailySpent = byCat.filterKeys { it != null && it in dailyIds }.values.sum()

    val bars = remember(list, range) {
        val arr = DoubleArray(range.days)
        for (t in outs) {
            val c = t.categoryId ?: continue
            if (c !in dailyIds) continue
            val i = ((t.time - range.from) / Period.DAY).toInt()
            if (i in arr.indices) arr[i] += t.amount
        }
        arr
    }

    val prev = remember(tick, mode, offset, acc) {
        if (mode != Period.MODE_MONTH) null else {
            val r = Budget.cycle(db, offset - 1).range
            db.txns(r.from, r.to).filter { !it.income && (acc == null || it.accountId == acc) }
                .groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column {
                Segmented(listOf("يوم", "أسبوع", "شهر"), mode) { mode = it; offset = 0 }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { offset-- }) { Icon(Icons.Filled.KeyboardArrowRight, "الفترة السابقة") }
                    Text(Period.label(mode, range), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontFamily = Ui.numbers, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { offset++ }, enabled = offset < 0) {
                        Icon(Icons.Filled.KeyboardArrowLeft, "الفترة التالية")
                    }
                }
                AccountChips(accounts, acc) { acc = it }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("صرفت", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Amount(out, 40.sp, weight = FontWeight.Bold)
                Row(Modifier.padding(top = 4.dp)) {
                    if (mode != Period.MODE_DAY) {
                        Text("معدل ${fmt(out / max(1, elapsed))} باليوم", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
                    }
                    Text("${list.size} حركة", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Section("الملخص") {
                if (inc > 0) KV("كل الدخل", "+" + fmt(inc), color = Ui.p.mint)
                if (monthInfo != null) {
                    KV("الراتب", if (monthInfo.received) fmt(monthInfo.salary) else "ما نزل بهالفترة")
                    KV("الدخل − الصرف", fmt(inc - out), bold = true,
                        color = if (inc - out < 0) Ui.p.pomegranate else Ui.p.mint)
                } else {
                    KV("حد المصروف المرن", fmt(dailyTarget))
                    KV("صرفت منه", fmt(dailySpent), bold = true,
                        color = if (dailySpent > dailyTarget) Ui.p.pomegranate else Ui.p.mint)
                }
            }
        }
        if (mode != Period.MODE_DAY) item {
            Section("المصروف اليومي المرن") {
                BarChart(bars, dayLabels(range), perDayLimit, todayIndex(range), labelEvery = if (range.days > 7) 5 else 1)
                Text("الخط المتقطع حدك اليومي (${fmt(perDayLimit)}). الأحمر يوم زدت فيه.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Section("حسب البند") {
                if (byCat.isEmpty()) Text("ما في صرف بهالفترة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    val slices = cats.map { c -> Slice("${c.icon} ${c.name}", byCat[c.id] ?: 0.0, Color(c.color)) } +
                        listOf(Slice("❔ بدون بند", byCat[null] ?: 0.0, Color(0xFF9E9E9E)))
                    DonutChart(slices, fmt(out), Period.label(mode, range), size = 170.dp, showLegend = false)
                    Spacer(Modifier.height(8.dp))
                }
                cats.forEach { c ->
                    val s = byCat[c.id] ?: 0.0
                    val budget = when {
                        mode == Period.MODE_MONTH -> c.budget
                        c.type == Db.DAILY -> c.budget / cycleDays * range.days
                        else -> 0.0
                    }
                    if (s > 0 || (mode == Period.MODE_MONTH && c.budget > 0)) CatBar(c, c.name, s, budget) { catSheet = c.id }
                }
                val unc = byCat[null] ?: 0.0
                if (unc > 0) CatBar(null, "بدون بند", unc, 0.0) { catSheet = -1L }
            }
        }
        if (prev != null && (prev.isNotEmpty() || byCat.isNotEmpty())) item {
            Section("مقارنة مع الشهر الماضي") {
                val prevTotal = prev.values.sum()
                val diff = out - prevTotal
                KV("الشهر الماضي", fmt(prevTotal))
                KV(if (diff > 0) "صرفت أكثر بـ" else "وفّرت", fmt(abs(diff)), bold = true,
                    color = if (diff > 0) Ui.p.pomegranate else Ui.p.mint)
                val changes = cats.map { c -> c to ((byCat[c.id] ?: 0.0) - (prev[c.id] ?: 0.0)) }
                    .filter { abs(it.second) >= 1 }.sortedByDescending { abs(it.second) }.take(4)
                changes.forEach { (c, d) ->
                    KV("${c.icon}  ${c.name}", (if (d > 0) "▲ " else "▼ ") + fmt(abs(d)),
                        color = if (d > 0) Ui.p.pomegranate else Ui.p.mint)
                }
                monthTip(cats, byCat, prev, offset == 0)?.let { tip ->
                    Text(tip, Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
                        .background(Ui.p.dinar.copy(alpha = 0.14f)).padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (top.isNotEmpty()) item {
            Section("وين صرفت أكثر") {
                top.forEach { e -> KV("${e.key} (${e.value.second} مرة)", fmt(e.value.first)) }
            }
        }
        if (list.isNotEmpty()) item {
            Section("كل الحركات") {
                Column {
                    list.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = Ui.p.line)
                        TxnRow(t, catMap[t.categoryId], accNames[t.accountId]) { onOpen(t.id) }
                    }
                }
            }
        }
    }
    catSheet?.let { id ->
        CategorySheet(db, cats.firstOrNull { it.id == id }, range, Period.label(mode, range),
            onOpen = { tid -> catSheet = null; onOpen(tid) }, onDone = { catSheet = null },
            monthly = mode == Period.MODE_MONTH)
    }
}

/** One plain-language tip from this month vs last month. */
fun monthTip(cats: List<Category>, now: Map<Long?, Double>, prev: Map<Long?, Double>, current: Boolean): String? {
    val over = cats.filter { it.budget > 0 }.map { it to (now[it.id] ?: 0.0) - it.budget }
        .filter { it.second > 0 }.maxByOrNull { it.second }
    if (over != null) {
        return "«${over.first.name}» زاد عن ميزانيته بـ ${fmt(over.second)}. " +
            if (current) "حاول تخفف منه باقي الشهر." else "يا بتزيد ميزانيته، يا بتنتبه عليه الشهر الجاي."
    }
    val rise = cats.map { it to (now[it.id] ?: 0.0) - (prev[it.id] ?: 0.0) }.filter { it.second > 0 }.maxByOrNull { it.second }
    if (rise != null && rise.second >= 10) {
        return "أكثر بند زاد عن الشهر الماضي هو «${rise.first.name}» (+${fmt(rise.second)})."
    }
    return if (now.isNotEmpty()) "ماشي ضمن الميزانية. كمّل هيك." else null
}

/**
 * Everything booked under one budget line in [range]: date, amount and who it went to.
 * [cat] null = transactions without a budget line.
 */
@Composable
fun CategorySheet(
    db: Db, cat: Category?, range: Range, periodLabel: String,
    onOpen: (Long) -> Unit, onDone: () -> Unit, monthly: Boolean = true,
) {
    val ctx = LocalContext.current
    val list = remember(cat, range) { db.txnsInCategory(cat?.id, range.from, range.to) }
    val total = list.sumOf { it.amount }
    val color = cat?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    SheetFrame(onDone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(cat?.icon ?: "❔", color, 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(cat?.name ?: "بدون بند", style = MaterialTheme.typography.titleLarge)
                Text("$periodLabel، ${list.size} حركة", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        KV("صرفت", fmt(total), bold = true)
        if (monthly && cat != null && cat.budget > 0) {
            val left = cat.budget - total
            KV("الميزانية", fmt(cat.budget))
            KV(if (left >= 0) "باقي" else "زيادة", fmt(kotlin.math.abs(left)), bold = true,
                color = if (left >= 0) Ui.p.mint else Ui.p.pomegranate)
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) {
            Text("ما في حركات على هالبند بهالفترة.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp))
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                list.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = Ui.p.line)
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(54.dp)) {
                            Text(Period.dayMonth(t.time), fontFamily = Ui.numbers, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(Period.clock(t.time), fontFamily = Ui.numbers, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(t.merchant.ifEmpty { t.kind }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium)
                            if (t.note.isNotBlank()) {
                                Text(t.note, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Amount(t.amount, 15.sp)
                    }
                }
            }
            Text("اضغط على أي حركة عشان تغيّر بندها أو تكتب ملاحظة.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            ReceiptButtons("كشف البند (PDF)", "category.pdf") { Receipt.forCategory(it, cat, range, periodLabel) }
        }
    }
}

/** "You have N to sort": one big invitation instead of a long list on the home page. */
@Composable
fun TriageCard(pending: List<Txn>, cats: List<Category>, onTriage: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val p = Ui.p
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).pressScale(src)
            .clip(RoundedCornerShape(22.dp)).background(p.dinar.copy(alpha = 0.16f))
            .border(1.dp, p.dinar.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .clickable(interactionSource = src, indication = null, onClick = onTriage)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${pending.size} حركة بدها تصنيف", style = MaterialTheme.typography.titleMedium)
            Text("صنّفها بلمسة وحدة لكل حركة، وبيصير المسموح اليوم دقيق.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                cats.filter { it.type == Db.DAILY }.take(4).forEach { c ->
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(1.dp, Color(c.color).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(c.icon, fontSize = 14.sp) }
                }
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(p.hero).padding(horizontal = 16.dp, vertical = 12.dp)
        ) { Text("صنّف بسرعة", color = p.onPine, fontWeight = FontWeight.SemiBold) }
    }
}
