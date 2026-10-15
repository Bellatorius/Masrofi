package com.mizania.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * One nightly check at about 10 pm:
 * a short day summary, tomorrow's due payments, and the month report on the cycle's last day.
 */
object Daily {
    const val HOUR = 22

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 60_000) add(Calendar.DAY_OF_YEAR, 1)
        }
        // Inexact on purpose: needs no special alarm permission and a few minutes late is fine.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pending(ctx))
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 7, Intent(ctx, DailyReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun run(ctx: Context) {
        val db = Db.get(ctx)
        val now = System.currentTimeMillis()
        val dayKey = Period.day().from

        if (db.setting("daily_summary", "1") == "1" && db.firstTime("summary:$dayKey")) {
            val al = Budget.allowance(db, now)
            val today = Period.day()
            val spentAll = db.spentByCategory(today.from, today.to).values.sum()
            val pending = db.pending().size
            val text = buildString {
                append("صرفت اليوم ${fmt(spentAll)}")
                if (al.leftToday < 0) append(" وزدت عن حدك بـ ${fmt(-al.leftToday)}")
                append(". بكرا مسموحلك ${fmt(al.tomorrow)}.")
                al.balance?.let { append(" رصيدك ${fmt(it)}.") }
                if (pending > 0) append(" في $pending حركة بدها تصنيف.")
            }
            Notifier.alert(ctx, 900_001, "ملخص اليوم", text)
        }

        // Fixed payments due tomorrow that have not been paid this cycle.
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tDay = tomorrow.get(Calendar.DAY_OF_MONTH)
        val lastOfMonth = tDay == tomorrow.getActualMaximum(Calendar.DAY_OF_MONTH)
        val ci = Budget.cycle(db, now = now)
        val cycle = ci.range
        val spent = db.spentByCategory(cycle.from, cycle.to)
        for (c in db.categories()) {
            if (c.dueDay <= 0) continue
            val due = c.dueDay == tDay || (lastOfMonth && c.dueDay > tDay)
            if (!due) continue
            val paid = spent[c.id] ?: 0.0
            val done = if (c.budget > 0) paid >= c.budget * 0.9 else paid > 0
            if (done || !db.firstTime("due:${c.id}:$dayKey")) continue
            Notifier.alert(ctx, 900_100L + c.id, "${c.icon} بكرا موعد ${c.name}",
                if (c.budget > 0) "المبلغ ${fmt(c.budget - paid)}" else "لا تنسى الدفعة")
        }

        // Last day of the budget month: the month report.
        if (cycle.to - Period.day().to < Period.HOUR && db.firstTime("month:${cycle.from}")) {
            val prev = Budget.cycle(db, offset = -1, now = now).range
            val cats = db.categories()
            val prevSpent = db.spentByCategory(prev.from, prev.to)
            val total = spent.values.sum()
            val tip = monthTip(cats, spent, prevSpent, current = false) ?: ""
            Notifier.alert(ctx, 900_002, "تقرير الشهر جاهز 📊",
                (if (ci.received) "صرفت ${fmt(total)} من راتب ${fmt(ci.salary)}. " else "صرفت ${fmt(total)} هالشهر. ") + tip)
        }
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            Daily.run(ctx)
        } finally {
            Daily.schedule(ctx)
            AllowanceWidget.refreshAll(ctx)
        }
    }
}

/** Alarms are cleared on reboot; set the nightly one again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Daily.schedule(ctx)
    }
}
