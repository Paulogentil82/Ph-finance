package com.example.phfinance

import com.example.phfinance.data.local.ClosureHistoryEntity
import com.example.phfinance.data.local.ContractEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.MessageTemplateEntity
import com.example.phfinance.data.local.MonthlyClosureEntity
import com.example.phfinance.data.local.NotificationLogEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.data.model.WhatsappTemplateType
import com.example.phfinance.util.BackupManager
import com.example.phfinance.util.BackupPayload
import com.example.phfinance.util.GrowthSimulationParams
import com.example.phfinance.util.GrowthSimulatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Etapa2_1IntegrationTest {

    @Test
    fun testEtapa2FullBackupSerializationAndDeserialization() {
        val payload = BackupPayload(
            version = 3,
            timestamp = System.currentTimeMillis(),
            monthlyClosures = listOf(
                MonthlyClosureEntity(
                    id = "closure_10_2026",
                    competence = "10/2026",
                    status = "DEFINITIVO",
                    accrualRevenueCents = 1000000L,
                    accrualExpenseCents = 300000L,
                    cashRevenueCents = 800000L,
                    cashExpenseCents = 250000L,
                    netResultCents = 550000L
                )
            ),
            closureHistories = listOf(
                ClosureHistoryEntity(
                    id = "hist_1",
                    closureId = "closure_10_2026",
                    competence = "10/2026",
                    actionType = "FECHADO_DEFINITIVO",
                    reason = "Fechamento mensal encerrado com sucesso"
                )
            ),
            messageTemplates = listOf(
                MessageTemplateEntity(
                    id = WhatsappTemplateType.LEMBRETE_ANTES.name,
                    templateType = WhatsappTemplateType.LEMBRETE_ANTES,
                    title = "Lembrete Antes do Vencimento",
                    text = "Olá {nome}, lembrete de mensalidade no valor de {valor}."
                )
            ),
            notificationLogs = listOf(
                NotificationLogEntity(
                    id = "log_1",
                    clientId = "cli_101",
                    templateType = WhatsappTemplateType.LEMBRETE_ANTES,
                    messageText = "Mensagem enviada",
                    isConfirmedSent = true
                )
            )
        )

        val password = "Etapa21Password!"
        val wrapper = BackupManager.encryptPayloadToWrapper(payload, password, version = 3)
        val wrapperJson = BackupManager.wrapperAdapter.toJson(wrapper)

        val restored = BackupManager.decryptWrapperToPayload(wrapperJson, password)

        assertEquals(1, restored.monthlyClosures.size)
        assertEquals("closure_10_2026", restored.monthlyClosures.first().id)
        assertEquals("DEFINITIVO", restored.monthlyClosures.first().status)

        assertEquals(1, restored.closureHistories.size)
        assertEquals("FECHADO_DEFINITIVO", restored.closureHistories.first().actionType)

        assertEquals(1, restored.messageTemplates.size)
        assertEquals("Lembrete Antes do Vencimento", restored.messageTemplates.first().title)

        assertEquals(1, restored.notificationLogs.size)
        assertTrue(restored.notificationLogs.first().isConfirmedSent)
    }

    @Test
    fun testGrowthSimulatorSafetyBoundsOnExtremeInputs() {
        val extremeParams = GrowthSimulationParams(
            currentActiveClients = -50, // Should coerce to 0
            monthlyNewClients = -10, // Should coerce to 0
            monthlyChurnPercent = 250.0, // Should coerce to 100.0%
            ticketMedioCents = -10000L, // Should coerce to 0L
            monthlyEvaluationsCount = 0,
            fixedExpensesCents = 0L,
            variableExpensePercent = 150.0, // Should coerce to 100.0%
            estimatedInadimplenciaPercent = -20.0 // Should coerce to 0.0%
        )

        val projections = GrowthSimulatorEngine.calculateProjections(extremeParams)
        assertEquals(4, projections.size)

        val m1 = projections.find { it.monthIndex == 1 }
        assertNotNull(m1)
        assertEquals(0, m1!!.projectedClients)
        assertEquals(0L, m1.mrrCents)
        assertEquals(0.0, m1.profitMarginPercent, 0.001)
    }

    @Test
    fun testGrowthSimulatorUpperBoundsOnProfits() {
        val highMarginParams = GrowthSimulationParams(
            currentActiveClients = 100,
            monthlyNewClients = 10,
            monthlyChurnPercent = 0.0,
            ticketMedioCents = 50000L, // R$ 500,00
            fixedExpensesCents = 0L,
            variableExpensePercent = 0.0,
            estimatedInadimplenciaPercent = 0.0
        )

        val projections = GrowthSimulatorEngine.calculateProjections(highMarginParams)
        val m12 = projections.find { it.monthIndex == 12 }
        assertNotNull(m12)
        assertEquals(100.0, m12!!.profitMarginPercent, 0.001) // 100% margin when zero costs
    }
}
