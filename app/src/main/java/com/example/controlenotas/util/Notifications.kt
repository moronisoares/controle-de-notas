package com.example.controlenotas.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Canais e ids de notificação usados pela exportação em segundo plano. */
object Notifications {

    const val CHANNEL_PROGRESS = "export_progress"
    const val CHANNEL_DONE = "export_done"

    const val ID_PROGRESS = 4001
    const val ID_DONE = 4002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            "Exportação em andamento",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mostra o progresso da exportação das notas."
            setShowBadge(false)
        }

        val done = NotificationChannel(
            CHANNEL_DONE,
            "Exportação concluída",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisa quando o arquivo de exportação está pronto."
        }

        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(done)
    }
}
