package com.mizania.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What was done to one transaction, so it can be undone. */
private data class Done(val id: Long, val prevCat: Long?, val ignored: Boolean, val label: String)

/**
 * "صنّف بسرعة": the waiting transactions one at a time, with every budget line as a big tile.
 * One tap books it and slides in the next; "تراجع" puts the last one back.
 */
@Composable
fun TriageScreen(db: Db, onOpenDetails: (Long) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cats = remember { db.categories() }
    val queue = remember { mutableStateListOf<Txn>().apply { addAll(db.pending()) } }
    val total = remember { queue.size }
    val history = remember { mutableStateListOf<Done>() }
    var learn by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var linking by remember { mutableStateOf<Pair<Txn, Category>?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(confirm, tick) {
        if (confirm != null) { delay(2600); confirm = null }
    }
    val p = Ui.p

    BackHandler(onBack = onClose)

    /**
     * Books [t] under [c]. For savings, debt and jamiya lines [link] says where the money went:
     * Link.None keeps it unlinked, otherwise the chosen goal / debt / jamiya.
     */
    fun book(t: Txn, c: Category, link: Link? = null) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        db.updateTxn(t.id, c.id, t.note)
        // Rules for savings/debt/jamiya lines would book future ones without asking where: skip those.
        if (learn && !db.needsLink(c, t.income) && t.merchant.isNotBlank() && db.validRuleKey(t.merchant)) {
            db.addRule(t.merchant, c.id)
        }
        Notifier.cancel(ctx, t.id)
        Processor.afterCategorized(ctx, t.id, c.id, notifyRecorded = false)
        when (link) {
            null -> {}
            Link.None -> db.unlinkAll(t.id)
            is Link.Goal -> db.syncLinks(t.id, goalId = link.id)
            is Link.Debt -> db.syncLinks(t.id, debtId = link.id)
            is Link.Jam -> db.syncLinks(t.id, jamiyaId = link.id)
        }
        confirm = db.linkSummary(t.id) ?: "انحسبت على «${c.name}»"
        history += Done(t.id, null, false, c.name)
        queue.remove(t)
        // The new rule may have booked others from the same shop: drop them from the queue too.
        if (learn) {
            val still = db.pending().map { it.id }.toSet()
            queue.removeAll { it.id !in still }
        }
        tick++
    }

    fun ignore(t: Txn) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        db.ignoreTxn(t.id)
        Notifier.cancel(ctx, t.id)
        history += Done(t.id, null, true, "تجاهل")
        queue.remove(t)
        tick++
    }

    fun undo() {
        val last = history.removeLastOrNull() ?: return
        if (last.ignored) db.writableDatabase.execSQL("UPDATE txns SET ignored=0 WHERE id=?", arrayOf<Any>(last.id))
        db.updateTxn(last.id, last.prevCat, db.txn(last.id)?.note ?: "")
        db.syncLinks(last.id)
        val t = db.txn(last.id) ?: return
        queue.add(0, t)
        tick++
    }

    androidx.compose.material3.Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Top bar: close, progress.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق") }
            Column(Modifier.weight(1f)) {
                Text("صنّف بسرعة", style = MaterialTheme.typography.titleMedium)
                Text("${total - queue.size} من $total", fontFamily = Ui.numbers, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(history.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = { undo() }) { Text("تراجع") }
            }
        }
        val done = if (total == 0) 1f else (total - queue.size).toFloat() / total
        val smooth by androidx.compose.animation.core.animateFloatAsState(done, tween(500), label = "progress")
        LinearProgressIndicator(
            progress = { smooth },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = p.mint, trackColor = p.line, drawStopIndicator = {},
        )

        val current = queue.firstOrNull()
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (slideInHorizontally(tween(320)) { -it / 2 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(260)) { it / 2 } + fadeOut(tween(180)))
            },
            contentKey = { it?.id },
            modifier = Modifier.weight(1f),
            label = "card",
        ) { t ->
            if (t == null) {
                AllDone(onClose)
            } else {
                Column(Modifier.fillMaxSize()) {
                    TxnCard(t, onDetails = { onOpenDetails(t.id) })
                    Text("لأي بند؟", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(cats, key = { it.id }) { c ->
                            CategoryTile(c) { if (db.needsLink(c, t.income)) linking = t to c else book(t, c) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (t.merchant.isNotBlank() && db.validRuleKey(t.merchant)) {
                            Checkbox(checked = learn, onCheckedChange = { learn = it })
                            Text("تذكّر «${t.merchant}» للمرات الجاية", Modifier.weight(1f), maxLines = 2,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        TextButton(onClick = { queue.remove(t); queue.add(t); tick++ }) { Text("بعدين") }
                        TextButton(onClick = { ignore(t) }) { Text("مش مصروف", color = p.pomegranate) }
                    }
                }
            }
        }
    }
    // Where the last one went, for a moment.
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 64.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(confirm != null, enter = fadeIn() + androidx.compose.animation.slideInVertically { it },
            exit = fadeOut()) {
            Text(
                confirm ?: "", color = p.onPine, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(14.dp)).background(p.hero)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    }
    linking?.let { (t, c) ->
        LinkPicker(db, t, c, onPick = { link -> linking = null; book(t, c, link) }, onCancel = { linking = null })
    }
}

/** Where money booked under a savings, debt or jamiya line goes. */
sealed class Link {
    object None : Link()
    data class Goal(val id: Long) : Link()
    data class Debt(val id: Long) : Link()
    data class Jam(val id: Long) : Link()
}

/** Asks which goal, debt or jamiya — every time, so it is always clear where the money went. */
@Composable
fun LinkPicker(db: Db, t: Txn, c: Category, onPick: (Link) -> Unit, onCancel: () -> Unit) {
    data class Opt(val link: Link, val title: String, val sub: String)
    val (title, empty, opts) = remember(t.id, c.id) {
        when {
            db.isSavings(c) -> Triple("لأي هدف ادخار؟", "ما عندك أهداف ادخار. اعمل هدف من «أموالي ← الادخار».",
                db.goals().map { Opt(Link.Goal(it.id), "${it.icon}  ${it.name}", "جمعت ${fmt(it.saved)} من ${fmt(it.target)}") })
            db.isDebtLine(c) -> Triple("على أي دين؟", "ما في دين مسجّل عليك. ضيفه من «أموالي ← الديون».",
                db.openDebtsIOwe().map { Opt(Link.Debt(it.id), it.person, "باقي عليك ${fmt(it.remaining)}") })
            else -> Triple("أي جمعية؟", "ما في جمعية مسجّلة. ضيفها من «أموالي ← الجمعية».",
                db.jamiyas().map { j ->
                    val i = JamiyaMonths.indexAt(j, t.time)
                    Opt(Link.Jam(j.id), "🤝  ${j.name}",
                        if (i in 0 until j.months) "رح يتعلّم شهر ${JamiyaMonths.label(j, i)} مدفوع" else "تاريخ الحركة برا أشهرها")
                })
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                Text("${fmt(t.amount)}، ${t.merchant.ifEmpty { t.kind }}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (opts.isEmpty()) Text(empty, style = MaterialTheme.typography.bodyMedium)
                opts.forEach { o ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onPick(o.link) }.padding(14.dp)
                    ) {
                        Text(o.title, style = MaterialTheme.typography.titleSmall)
                        Text(o.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(Link.None) }) { Text("صنّفها بدون ربط") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } },
    )
}

@Composable
private fun TxnCard(t: Txn, onDetails: () -> Unit) {
    val p = Ui.p
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(30.dp)).background(p.hero)
            .clickable(onClick = onDetails).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t.kind + "، " + Period.stamp(t.time), color = p.onPine.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall, fontFamily = Ui.numbers)
        Spacer(Modifier.height(6.dp))
        Amount(t.amount, 44.sp, p.dinar, weight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(t.merchant.ifEmpty { t.kind }, color = p.onPine, style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("اضغط للتفاصيل والملاحظة", color = p.onPine.copy(alpha = 0.78f), fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CategoryTile(c: Category, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val color = Color(c.color)
    Column(
        Modifier.height(92.dp).pressScale(src).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(c.icon, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(c.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp)
    }
}

@Composable
private fun AllDone(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(true, enter = scaleIn(tween(400)) + fadeIn()) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(Ui.p.mint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Text("✓", fontSize = 44.sp, color = Ui.p.mint) }
        }
        Spacer(Modifier.height(16.dp))
        Text("كل شي مصنّف", style = MaterialTheme.typography.headlineSmall)
        Text("الميزانية والمسموح اليوم صاروا محسوبين صح.", color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onClose, shape = RoundedCornerShape(16.dp)) { Text("رجوع للرئيسية") }
    }
}
