package com.mizania.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlin.math.max

/** Home-screen widget: what you may still spend today. */
class AllowanceWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        update(ctx, manager, ids)
    }

    companion object {
        fun refreshAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx) ?: return
            val ids = m.getAppWidgetIds(ComponentName(ctx, AllowanceWidget::class.java))
            if (ids.isNotEmpty()) update(ctx, m, ids)
        }

        private fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val al = try { Budget.allowance(Db.get(ctx)) } catch (e: Exception) { null }
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_allowance)
                v.setTextViewText(R.id.widget_title, tr("المتاح لك اليوم", "You can spend today"))
                v.setInt(R.id.widget_root, "setLayoutDirection",
                    if (Lang.ar) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR)
                if (al != null) {
                    v.setTextViewText(R.id.widget_amount, fmt(max(0.0, al.leftToday)))
                    v.setTextViewText(
                        R.id.widget_sub,
                        if (al.leftToday < 0) tr("تجاوزت حدك اليوم بمقدار ${fmt(-al.leftToday)}", "${fmt(-al.leftToday)} over today")
                        else al.balance?.let { tr("رصيدك ${fmt(it)}، متبقٍ ${daysText(al.daysLeft)}", "Balance ${fmt(it)}, ${daysText(al.daysLeft)} left") }
                            ?: tr("صرفت اليوم ${fmt(al.spentToday)}، متبقٍ ${daysText(al.daysLeft)}", "Spent ${fmt(al.spentToday)} today, ${daysText(al.daysLeft)} left"),
                    )
                    v.setInt(R.id.widget_root, "setBackgroundResource",
                        if (al.leftToday < 0) R.drawable.widget_bg_over else R.drawable.widget_bg)
                }
                v.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, v)
            }
        }
    }
}
