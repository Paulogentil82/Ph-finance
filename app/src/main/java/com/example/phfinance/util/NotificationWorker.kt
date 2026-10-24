package com.example.phfinance.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.phfinance.data.local.AppDatabase
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.ReceivableStatus
import kotlinx.coroutines.flow.first

class NotificationWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(appContext)
            val dao = db.phFinanceDao()

            val receivables = dao.observeAllReceivables().first()
            val now = System.currentTimeMillis()
            val threeDaysMs = 3 * 86400000L

            val pendingOrOverdue = receivables.filter { r ->
                r.status != ReceivableStatus.QUITADA && r.status != ReceivableStatus.CANCELADA
            }

            val dueSoonOrOverdue = pendingOrOverdue.filter { r ->
                r.dueDate <= (now + threeDaysMs)
            }

            if (dueSoonOrOverdue.isNotEmpty()) {
                val overdueCount = dueSoonOrOverdue.count { it.dueDate < now }
                val totalAmount = dueSoonOrOverdue.sumOf { it.netValueCents - it.receivedValueCents }

                val title = "PH Finance — Lembrete de Cobranças"
                val message = if (overdueCount > 0) {
                    "Existem $overdueCount cobranças em atraso e outras a vencer (${CurrencyUtils.formatCents(totalAmount)} total)."
                } else {
                    "Existem ${dueSoonOrOverdue.size} mensalidades vencendo nos próximos 3 dias (${CurrencyUtils.formatCents(totalAmount)})."
                }

                sendSystemNotification(appContext, title, message, notificationId = 1001)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun sendSystemNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int
    ) {
        val channelId = "ph_finance_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Lembretes Financeiros",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações de cobranças e vencimentos de contratos"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }
}
