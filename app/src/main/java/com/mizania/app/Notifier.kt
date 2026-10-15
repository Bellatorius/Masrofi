package com.mizania.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifier {
    private const val CH_ASK = "ask"
    private const val CH_ALERT = "alert"
    private const val CH_INFO = "info"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ASK, "حركات بدها تصنيف", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "تنبيهات الميزانية", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "حركات مسجلة", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun open(ctx: Context, requestCode: Int, txnId: Long?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (txnId != null) putExtra(MainActivity.EXTRA_TXN, txnId)
        }
        return PendingIntent.getActivity(
            ctx, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun post(ctx: Context, channel: String, id: Int, title: String, text: String, tap: PendingIntent) {
        createChannels(ctx)
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // Without the notification permission this is silently dropped, never a crash.
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    /** "Which budget does this belong to?" — tapping opens the classify screen. */
    fun ask(ctx: Context, txnId: Long, amount: Double, merchant: String) {
        post(ctx, CH_ASK, txnId.toInt(), "لأي بند تابعة ${fmt(amount)}؟",
            "$merchant — اضغط عشان تختار البند وتكتب ملاحظة", open(ctx, txnId.toInt(), txnId))
    }

    fun cancel(ctx: Context, txnId: Long) {
        ctx.getSystemService(NotificationManager::class.java).cancel(txnId.toInt())
    }

    fun alert(ctx: Context, catId: Long, title: String, text: String) {
        val id = 100_000 + catId.toInt()
        post(ctx, CH_ALERT, id, title, text, open(ctx, id, null))
    }

    fun info(ctx: Context, txnId: Long, title: String, text: String) {
        post(ctx, CH_INFO, txnId.toInt(), title, text, open(ctx, txnId.toInt(), txnId))
    }
}
