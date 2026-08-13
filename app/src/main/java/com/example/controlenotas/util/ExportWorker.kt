package com.example.controlenotas.util

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.controlenotas.R
import com.example.controlenotas.data.AppDatabase
import com.example.controlenotas.data.Invoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gera o arquivo de exportação em segundo plano.
 *
 * O trabalho continua mesmo se o app for fechado; o progresso aparece em uma
 * notificação e, ao terminar, uma segunda notificação abre o compartilhamento.
 */
class ExportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val withAttachments = inputData.getBoolean(KEY_WITH_ATTACHMENTS, false)
        val year = inputData.getInt(KEY_YEAR, currentInvoiceYear())
        val month = inputData.getInt(KEY_MONTH, 0).takeIf { it in 1..12 }

        val context = applicationContext
        val notifier = NotificationManagerCompat.from(context)

        try {
            publishProgress(0, 0)
            showProgressNotification(notifier, 0, 0, indeterminate = true)

            val invoices = loadInvoices(year, month)
            if (invoices.isEmpty()) {
                notifier.safeCancel(Notifications.ID_PROGRESS)
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "Nenhuma nota no período selecionado.")
                )
            }

            val file = if (withAttachments) {
                writeZipWithProgress(context, invoices, notifier)
            } else {
                writeCsvFile(context, invoices)
            }

            publishProgress(invoices.size, invoices.size)
            notifier.safeCancel(Notifications.ID_PROGRESS)
            showDoneNotification(notifier, file, invoices.size)

            Result.success(
                workDataOf(
                    KEY_OUTPUT_PATH to file.absolutePath,
                    KEY_COUNT to invoices.size
                )
            )
        } catch (e: Throwable) {
            // Throwable e nao Exception de proposito: falta de memoria chega
            // como OutOfMemoryError, que nao e uma Exception. Sem isto o
            // trabalho morria sem aviso e a tela ficava "exportando" para sempre.
            notifier.safeCancel(Notifications.ID_PROGRESS)
            showFailureNotification(notifier)
            val reason = if (e is OutOfMemoryError) {
                "Memoria insuficiente para gerar o arquivo."
            } else {
                e.message ?: "Falha ao exportar."
            }
            Result.failure(workDataOf(KEY_ERROR to reason))
        }
    }

    /**
     * Monta o .zip em uma corrotina paralela enquanto esta reporta o progresso
     * (a escrita do arquivo é bloqueante e não pode chamar suspend functions).
     */
    private suspend fun writeZipWithProgress(
        context: Context,
        invoices: List<Invoice>,
        notifier: NotificationManagerCompat
    ): File = coroutineScope {
        val done = AtomicInteger(0)
        val total = invoices.size

        val job = async(Dispatchers.IO) {
            writeZipFile(context, invoices) { processed, _ -> done.set(processed) }
        }

        while (job.isActive) {
            val current = done.get()
            publishProgress(current, total)
            showProgressNotification(notifier, current, total, indeterminate = false)
            delay(PROGRESS_INTERVAL_MS)
        }

        job.await()
    }

    private suspend fun loadInvoices(year: Int, month: Int?): List<Invoice> {
        val dao = AppDatabase.getInstance(applicationContext).invoiceDao()
        return dao.getAllForExport().filter { invoice ->
            yearOf(invoice.invoiceDate) == year &&
                (month == null || monthOf(invoice.invoiceDate) == month)
        }
    }

    private suspend fun publishProgress(done: Int, total: Int) {
        setProgress(
            workDataOf(
                KEY_PROGRESS_DONE to done,
                KEY_PROGRESS_TOTAL to total
            )
        )
    }

    // ------------------------------------------------------------ notificações

    private fun showProgressNotification(
        notifier: NotificationManagerCompat,
        done: Int,
        total: Int,
        indeterminate: Boolean
    ) {
        val text = if (indeterminate || total <= 0) {
            "Preparando os dados..."
        } else {
            "$done de $total notas"
        }
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Exportando notas")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(if (indeterminate) 0 else total, done, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notifier.safeNotify(applicationContext, Notifications.ID_PROGRESS, notification)
    }

    private fun showDoneNotification(
        notifier: NotificationManagerCompat,
        file: File,
        count: Int
    ) {
        val chooser = buildShareChooser(applicationContext, file)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            Notifications.ID_DONE,
            chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Exportação concluída")
            .setContentText("$count nota(s) em ${file.name}. Toque para compartilhar.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$count nota(s) exportada(s) para ${file.name}.\n" +
                        "Toque para enviar por e-mail, WhatsApp ou salvar no Drive."
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notifier.safeNotify(applicationContext, Notifications.ID_DONE, notification)
    }

    private fun showFailureNotification(notifier: NotificationManagerCompat) {
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Falha na exportação")
            .setContentText("Não foi possível gerar o arquivo. Tente novamente.")
            .setAutoCancel(true)
            .build()
        notifier.safeNotify(applicationContext, Notifications.ID_DONE, notification)
    }

    companion object {
        const val UNIQUE_NAME = "export_invoices"

        /** Intervalo entre atualizações do progresso na tela e na notificação. */
        private const val PROGRESS_INTERVAL_MS = 350L

        const val KEY_WITH_ATTACHMENTS = "with_attachments"
        const val KEY_YEAR = "year"
        const val KEY_MONTH = "month"
        const val KEY_PROGRESS_DONE = "progress_done"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_COUNT = "count"
        const val KEY_ERROR = "error"

        /** Enfileira a exportação; uma nova substitui a anterior ainda pendente. */
        fun enqueue(
            context: Context,
            withAttachments: Boolean,
            year: Int,
            month: Int?
        ) {
            val input: Data = workDataOf(
                KEY_WITH_ATTACHMENTS to withAttachments,
                KEY_YEAR to year,
                KEY_MONTH to (month ?: 0)
            )
            val request = OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(input)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

/** Só notifica quando a permissão existe (Android 13+ exige POST_NOTIFICATIONS). */
private fun NotificationManagerCompat.safeNotify(
    context: Context,
    id: Int,
    notification: android.app.Notification
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    runCatching { notify(id, notification) }
}

private fun NotificationManagerCompat.safeCancel(id: Int) {
    runCatching { cancel(id) }
}
