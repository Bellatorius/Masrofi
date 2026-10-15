package com.mizania.app

import android.widget.Toast
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(db: Db, tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val senders = remember(tick) { db.senders() }
    val cats = remember(tick) { db.categories() }
    val rules = remember(tick) { db.rules() }
    val ownNames = remember(tick) { db.ownNames() }
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    var addingRule by remember { mutableStateOf(false) }
    val catNames = cats.associate { it.id to it.name }

    val accounts = remember(tick) { db.accounts() }
    val senderMap = remember(tick) { db.senderAccounts() }
    var pickFor by remember { mutableStateOf<Account?>(null) }
    var editAcc by remember { mutableStateOf<Account?>(null) }
    var addingAcc by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var addingCat by remember { mutableStateOf(false) }
    var salary by remember { mutableStateOf(db.setting("salary", "0")) }
    var cycleDay by remember { mutableStateOf(db.cycleDay().toString()) }
    var importFrom by remember { mutableStateOf(Period.day(-62).from) }
    var importTo by remember { mutableStateOf(Period.day().from) }
    var pickingRange by remember { mutableStateOf(false) }
    var bySalary by remember { mutableStateOf(db.setting("cycle_mode", "salary") == "salary") }
    var confirmReset by remember { mutableIntStateOf(0) } // 0 none, 1 transactions, 2 everything
    var dailySummary by remember { mutableStateOf(db.setting("daily_summary", "1") == "1") }
    var lockOn by remember { mutableStateOf(db.setting("lock", "0") == "1") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try { Backup.write(ctx, uri, Backup.exportJson(db)); toast("انحفظت النسخة الاحتياطية") }
            catch (e: Exception) { toast("ما زبط الحفظ") }
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try { Backup.write(ctx, uri, Backup.exportCsv(db)); toast("انحفظ ملف Excel (CSV)") }
            catch (e: Exception) { toast("ما زبط الحفظ") }
        }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Section("الحسابات") {
                Text("كل حساب بيقرأ مسجات المرسلين اللي بتختارهم إله، وباقي المسجات بيتجاهلها.",
                    style = MaterialTheme.typography.bodySmall)
                accounts.forEach { a ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { editAcc = a }) { Text("تعديل") }
                        }
                        val mine = senderMap.filterValues { it == a.id }.keys
                        if (mine.isEmpty()) Text("ما في مرسلين لسا", style = MaterialTheme.typography.bodySmall)
                        mine.forEach { s ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("• $s", Modifier.weight(1f))
                                IconButton(onClick = { db.removeSender(s); refresh() }) {
                                    Icon(Icons.Filled.Delete, "حذف")
                                }
                            }
                        }
                        OutlinedButton(onClick = { pickFor = a }) { Text("اختيار مرسلين") }
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { addingAcc = true }) { Text("إضافة حساب") }
            }
        }
        item {
            Section("استيراد المسجات القديمة") {
                Text("اختار من أي تاريخ لأي تاريخ. بيضيف حركات المرسلين المختارين بهالفترة، وما بيكرر اللي انضاف قبل.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickingRange = true }) {
                        Text("${Period.dayMonthYear(importFrom)}  ←  ${Period.dayMonthYear(importTo)}", fontFamily = Ui.numbers)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(enabled = senders.isNotEmpty(), onClick = {
                    val n = Processor.importInbox(ctx, importFrom, importTo + Period.DAY)
                    Toast.makeText(ctx, if (n > 0) "انضافت $n حركة" else "ما في حركات جديدة بهالفترة", Toast.LENGTH_SHORT).show()
                    refresh()
                }) { Text("استيراد") }
            }
        }
        item {
            Section("البنود والميزانية") {
                val total = cats.sumOf { it.budget }
                cats.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = c }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.name, Modifier.weight(1f))
                        Text(
                            (when (c.type) { Db.FIXED -> "ثابت"; Db.DAILY -> "يومي"; else -> "احتياط" }) + " · " + fmt(c.budget),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    "مجموع الميزانية ${fmt(total)}، والراتب المتوقع ${fmt(db.salary())}" +
                        if (db.salary() - total != 0.0) " (فرق ${fmt(db.salary() - total)})" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (total > db.salary()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { addingCat = true }) { Text("إضافة بند") }
            }
        }
        if (ownNames.isNotEmpty()) item {
            Section("أسماءك بالتحويلات") {
                Text("التحويلات من وإلى هالأسماء بتنحسب بين حساباتك، مش صرف ولا دخل.",
                    style = MaterialTheme.typography.bodySmall)
                ownNames.forEach { n ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(n, Modifier.weight(1f))
                        IconButton(onClick = { db.removeOwnName(n); refresh() }) { Icon(Icons.Filled.Delete, "حذف") }
                    }
                }
            }
        }
        item {
            Section("التصنيف التلقائي") {
                Text("أي حركة اسم المحل فيها بيحتوي الكلمة، بتروح للبند لحالها. اضغط على قاعدة لتعدّلها.",
                    style = MaterialTheme.typography.bodySmall)
                rules.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editingRule = r },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(r.keyword, maxLines = 1)
                            Text(catNames[r.categoryId] ?: "?", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { db.deleteRule(r.id); refresh() }) {
                            Icon(Icons.Filled.Delete, "حذف")
                        }
                    }
                }
                OutlinedButton(onClick = { addingRule = true }, modifier = Modifier.padding(top = 6.dp)) { Text("إضافة قاعدة") }
            }
        }
        item {
            Section("عام") {
                OutlinedTextField(
                    value = salary, onValueChange = { salary = it }, label = { Text("الراتب المتوقع (للتخطيط بس)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("اللي بيظهر بالرئيسية هو الراتب الحقيقي من مسج «قيد راتب»، مش هالرقم.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("الشهر بيبلش", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FilterChip(bySalary, { bySalary = true; db.setSetting("cycle_mode", "salary"); refresh() },
                        { Text("يوم ما ينزل الراتب") })
                    FilterChip(!bySalary, { bySalary = false; db.setSetting("cycle_mode", "day"); refresh() },
                        { Text("بيوم ثابت") })
                }
                if (!bySalary) {
                    OutlinedTextField(
                        value = cycleDay, onValueChange = { cycleDay = it },
                        label = { Text("يوم بداية الشهر (1-28)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    Text("لما يوصل مسج الراتب، بيبلش شهر جديد لحاله. وقبل أول راتب، بيستخدم يوم ${db.cycleDay()} من الشهر.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    salary.replace(',', '.').toDoubleOrNull()?.let { db.setSetting("salary", it.toString()) }
                    cycleDay.toIntOrNull()?.let { db.setSetting("cycle_day", it.coerceIn(1, 28).toString()) }
                    cycleDay = db.cycleDay().toString()
                    Toast.makeText(ctx, "انحفظ", Toast.LENGTH_SHORT).show()
                    refresh()
                }) { Text("حفظ") }
            }
        }
        item {
            Section("التنبيهات") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ملخص يومي الساعة 10 بالليل")
                        Text("قديش صرفت، وقديش مسموحلك بكرا", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dailySummary, onCheckedChange = {
                        dailySummary = it; db.setSetting("daily_summary", if (it) "1" else "0")
                    })
                }
                Text("تذكير الدفعات بيشتغل لكل بند حاطط إله «يوم الدفع» (من البنود فوق)، وبيوصلك قبل الموعد بيوم.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (Build.VERSION.SDK_INT >= 28) item {
            Section("الأمان") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("قفل التطبيق بالبصمة")
                        Text("أو برمز قفل الموبايل", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = lockOn, onCheckedChange = {
                        lockOn = it; db.setSetting("lock", if (it) "1" else "0")
                    })
                }
            }
        }
        item {
            Section("نسخة احتياطية") {
                Text("احفظ كل بياناتك بملف، وبتقدر ترجعها على نفس الموبايل أو على موبايل جديد.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { saveJson.launch(Backup.fileName("json")) }) { Text("حفظ نسخة احتياطية") }
                OutlinedButton(onClick = { openJson.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) {
                    Text("استرجاع من نسخة")
                }
                OutlinedButton(onClick = { saveCsv.launch(Backup.fileName("csv")) }) { Text("تصدير الحركات لـ Excel") }
            }
        }
        item {
            Section("مسح البيانات") {
                Text("الحركات المسجلة بس، أو كل شي والرجوع لأول ما نزّلت التطبيق.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmReset = 1 }) { Text("مسح كل الحركات") }
                Button(
                    onClick = { confirmReset = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("إرجاع التطبيق لأوله") }
            }
        }
    }

    editingRule?.let { r -> RuleDialog(db, r) { editingRule = null; refresh() } }
    if (addingRule) RuleDialog(db, null) { addingRule = false; refresh() }
    if (pickingRange) {
        ImportRangeDialog(importFrom, importTo, onDismiss = { pickingRange = false }) { f, t ->
            importFrom = f; importTo = t; pickingRange = false
        }
    }
    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("استرجاع النسخة؟") },
            text = { Text("رح تنمسح بياناتك الحالية وتنحط مكانها بيانات النسخة.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        Backup.restoreJson(db, Backup.read(ctx, uri))
                        salary = db.setting("salary", "0"); cycleDay = db.cycleDay().toString()
                        toast("رجعت البيانات ✓")
                    } catch (e: Exception) {
                        toast("هاد الملف مش نسخة من ميزانيتي")
                    }
                    restoreUri = null
                    refresh()
                }) { Text("استرجع", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("إلغاء") } },
        )
    }
    if (confirmReset != 0) {
        val all = confirmReset == 2
        AlertDialog(
            onDismissRequest = { confirmReset = 0 },
            title = { Text(if (all) "إرجاع التطبيق لأوله؟" else "مسح كل الحركات؟") },
            text = {
                Text(
                    if (all) "رح ينمسح كل شي: الحركات، والبنود، والحسابات، والديون، والتصنيف التلقائي، والإعدادات. وبترجع الميزانية الافتراضية."
                    else "رح تنمسح كل الحركات المسجلة. البنود والحسابات والديون والتصنيف التلقائي بيضلوا. بتقدر بعدها تستورد مسجات هالشهر من جديد."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (all) db.resetAll() else db.clearTransactions()
                    salary = db.setting("salary", "0")
                    cycleDay = db.cycleDay().toString()
                    confirmReset = 0
                    Toast.makeText(ctx, "انمسحت البيانات", Toast.LENGTH_SHORT).show()
                    refresh()
                }) { Text("امسح", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = 0 }) { Text("إلغاء") } },
        )
    }
    pickFor?.let { a -> SenderPickerDialog(db, a) { pickFor = null; refresh() } }
    editAcc?.let { a -> AccountDialog(db, a, a.name, canDelete = accounts.size > 1) { editAcc = null; refresh() } }
    if (addingAcc) AccountDialog(db, null, "الحساب ${accounts.size + 1}", canDelete = false) { addingAcc = false; refresh() }
    editing?.let { c -> CategoryDialog(db, c) { editing = null; refresh() } }
    if (addingCat) CategoryDialog(db, null) { addingCat = false; refresh() }
}

/** Picks a from/to date range; returns local start-of-day times for both ends. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportRangeDialog(from: Long, to: Long, onDismiss: () -> Unit, onPick: (Long, Long) -> Unit) {
    // The picker works in UTC days; convert the local days in and out.
    fun toUtc(local: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = local }
        return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear(); set(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    fun toLocal(utc: Long): Long {
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return java.util.Calendar.getInstance().apply {
            clear(); set(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = toUtc(from),
        initialSelectedEndDateMillis = toUtc(to),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis ?: return@TextButton
                    val e = state.selectedEndDateMillis ?: s
                    onPick(toLocal(s), toLocal(e))
                },
            ) { Text("تم") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text("فترة الاستيراد", Modifier.padding(start = 24.dp, top = 16.dp)) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Add or edit an automatic rule: a shop or provider name and the budget line it goes to. */
@Composable
fun RuleDialog(db: Db, rule: Rule?, onDone: () -> Unit) {
    val cats = remember { db.categories() }
    var keyword by remember { mutableStateOf(rule?.keyword ?: "") }
    var cat by remember { mutableStateOf(rule?.categoryId) }
    val ok = db.validRuleKey(keyword) && cat != null
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (rule == null) "قاعدة جديدة" else "تعديل القاعدة") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it }, singleLine = true,
                    label = { Text("اسم المحل أو المزود") }, placeholder = { Text("مثلاً ZAIN أو CARREFOUR") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (keyword.isNotBlank() && !db.validRuleKey(keyword)) {
                    Text("اكتب اسم المحل بس، مش نص المسج.", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Text("البند", style = MaterialTheme.typography.titleSmall)
                CategoryChips(cats, cat) { cat = it }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                val c = cat ?: return@TextButton
                if (rule == null) db.addRule(keyword, c) else db.updateRule(rule.id, keyword, c)
                db.applyRulesToPending()
                onDone()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("إلغاء") } },
    )
}
