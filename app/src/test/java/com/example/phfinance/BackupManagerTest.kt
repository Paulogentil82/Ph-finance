package com.example.phfinance

import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.FinancialAccountEntity
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.ExpenseType
import com.example.phfinance.util.BackupManager
import com.example.phfinance.util.BackupPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    private val samplePayload = BackupPayload(
        version = 3,
        timestamp = System.currentTimeMillis(),
        clients = listOf(
            ClientEntity(
                id = "cli_101",
                fullName = "Carlos Eduardo",
                nickname = "Cadu",
                phone = "11988887777",
                whatsapp = "11988887777",
                email = "carlos@example.com"
            )
        ),
        services = emptyList(),
        contracts = emptyList(),
        receivables = emptyList(),
        payments = emptyList(),
        expenses = listOf(
            ExpenseEntity(
                id = "exp_201",
                description = "Pro-Labore Mensal",
                category = "Administrativo",
                valueCents = 500000L,
                competence = "10/2026",
                dueDate = System.currentTimeMillis(),
                status = ExpenseStatus.PAGO,
                expenseType = ExpenseType.PRO_LABORE
            )
        ),
        accounts = listOf(
            FinancialAccountEntity(
                id = "acc_main",
                name = "Conta Principal",
                currentBalanceCents = 1500000L
            )
        )
    )

    @Test
    fun testExportAndDecryptV3BackupWithOwasp600kIterations() {
        val password = "StrongPassword123"
        val wrapper = BackupManager.encryptPayloadToWrapper(samplePayload, password, version = 3)

        assertEquals(3, wrapper.version)
        assertNotNull(wrapper.cipherTextBase64)
        assertNotNull(wrapper.saltBase64)
        assertNotNull(wrapper.ivBase64)

        val wrapperJson = BackupManager.wrapperAdapter.toJson(wrapper)
        val restoredPayload = BackupManager.decryptWrapperToPayload(wrapperJson, password)

        assertEquals(1, restoredPayload.clients.size)
        assertEquals("Carlos Eduardo", restoredPayload.clients.first().fullName)
        assertEquals(1, restoredPayload.expenses.size)
        assertEquals(ExpenseType.PRO_LABORE, restoredPayload.expenses.first().expenseType)
    }

    @Test(expected = Exception::class)
    fun testDecryptFailsWithIncorrectPassword() {
        val password = "CorrectPassword123"
        val wrongPassword = "WrongPassword456"

        val wrapper = BackupManager.encryptPayloadToWrapper(samplePayload, password, version = 3)
        val wrapperJson = BackupManager.wrapperAdapter.toJson(wrapper)

        // Should throw AEADBadTagException or cipher exception
        BackupManager.decryptWrapperToPayload(wrapperJson, wrongPassword)
    }

    @Test(expected = Exception::class)
    fun testDecryptFailsWithTamperedCipherText() {
        val password = "Password123"
        val wrapper = BackupManager.encryptPayloadToWrapper(samplePayload, password, version = 3)

        // Alter cipherText
        val tamperedWrapper = wrapper.copy(cipherTextBase64 = "A" + wrapper.cipherTextBase64.drop(1))
        val wrapperJson = BackupManager.wrapperAdapter.toJson(tamperedWrapper)

        BackupManager.decryptWrapperToPayload(wrapperJson, password)
    }

    @Test
    fun testDecryptLegacyV2Backup() {
        val password = "LegacyPassword123"
        // Encrypt with V2 (10,000 iterations)
        val wrapperV2 = BackupManager.encryptPayloadToWrapper(samplePayload, password, version = 2)
        assertEquals(2, wrapperV2.version)

        val wrapperJson = BackupManager.wrapperAdapter.toJson(wrapperV2)
        val restoredPayload = BackupManager.decryptWrapperToPayload(wrapperJson, password)

        assertEquals(1, restoredPayload.clients.size)
        assertEquals("cli_101", restoredPayload.clients.first().id)
    }

    @Test
    fun testDecryptLegacyV1UnencryptedBackup() {
        val rawJson = BackupManager.payloadAdapter.toJson(samplePayload)
        val restoredPayload = BackupManager.decryptWrapperToPayload(rawJson, "anyPassword")

        assertEquals(1, restoredPayload.clients.size)
        assertEquals("Carlos Eduardo", restoredPayload.clients.first().fullName)
    }

    @Test(expected = Exception::class)
    fun testDecryptFailsWithCorruptedJson() {
        val corruptedJson = "{ \"version\": 3, \"invalidJson\": true "
        BackupManager.decryptWrapperToPayload(corruptedJson, "anyPassword")
    }
}
