package com.giwnltrema.chuva.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.giwnltrema.chuva.MainActivity
import com.giwnltrema.chuva.R
import com.giwnltrema.chuva.data.Alert

object Notifier {
    private const val CH_RAIN = "rain"
    private const val CH_DAILY = "daily"
    private const val ID_RAIN = 1
    private const val ID_DAILY = 2

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RAIN, "Alertas de chuva", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aviso quando a chuva estiver para começar onde você está"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DAILY, "Resumo da manhã", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Previsão de chuva do dia, uma vez por manhã"
            },
        )
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun rain(context: Context, alert: Alert) = post(context, CH_RAIN, ID_RAIN, alert)

    fun daily(context: Context, alert: Alert) = post(context, CH_DAILY, ID_DAILY, alert)

    @Suppress("MissingPermission") // checado em canNotify()
    private fun post(context: Context, channel: String, id: Int, alert: Alert) {
        if (!canNotify(context)) return
        createChannels(context)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_rain)
            .setColor(0xFF5F6368.toInt())
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(
                if (channel == CH_RAIN) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
