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
                if (al != null) {
                    v.setTextViewText(R.id.widget_amount, fmt(max(0.0, al.leftToday)))
                    v.setTextViewText(
                        R.id.widget_sub,
                        if (al.leftToday < 0) "زدت اليوم بـ ${fmt(-al.leftToday)}"
                        else al.balance?.let { "رصيدك ${fmt(it)}، باقي ${al.daysLeft} يوم" }
                            ?: "صرفت اليوم ${fmt(al.spentToday)}، باقي ${al.daysLeft} يوم",
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
