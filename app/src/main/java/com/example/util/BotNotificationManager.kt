package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.model.TradeLog
import java.util.Locale

class BotNotificationManager(private val context: Context?) {

    companion object {
        const val CHANNEL_STATUS_ID = "bot_status_channel"
        const val CHANNEL_ALERTS_ID = "bot_trade_alerts"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS_ID,
                "Trading Bot Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live background status of the trading bot"
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Trade & Risk Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant notifications when trade orders fill or risk limits trigger"
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(statusChannel)
            manager?.createNotificationChannel(alertsChannel)
        } catch (e: Throwable) {
            // Safe fallback for null/test context
        }
    }

    fun notifyTradeExecuted(log: TradeLog) {
        if (context == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val title = "⚡ Trade Executed: ${log.side} ${log.symbol}"
            val text = String.format(
                Locale.US,
                "%s @ %,.2f | Amt: %,.2f %s | Mode: %s",
                log.side, log.price, log.amount, log.feeCurrency, log.tradingMode.displayName
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: Throwable) {
            // Unit test / permission safeguard
        }
    }

    fun notifyAlert(title: String, message: String) {
        if (context == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: Throwable) {
            // Unit test / permission safeguard
        }
    }
}
