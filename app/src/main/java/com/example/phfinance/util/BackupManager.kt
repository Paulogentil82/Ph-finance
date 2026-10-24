package com.example.phfinance.util

import androidx.room.withTransaction
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.phfinance.data.local.AccountMovementEntity
import com.example.phfinance.data.local.AccountTransferEntity
import com.example.phfinance.data.local.BankImportEntity
import com.example.phfinance.data.local.BankTransactionEntity
import com.example.phfinance.data.local.BusinessSettingsEntity
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ClosureHistoryEntity
import com.example.phfinance.data.local.ContractEntity
import com.example.phfinance.data.local.ContractSessionEntity
import com.example.phfinance.data.local.ExpenseCategoryEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.FinancialAccountEntity
import com.example.phfinance.data.local.GoalEntity
import com.example.phfinance.data.local.MessageTemplateEntity
import com.example.phfinance.data.local.MonthlyClosureEntity
import com.example.phfinance.data.local.NotificationLogEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.PhFinanceDao
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.local.ReconciliationAuditEntity
import com.example.phfinance.data.local.ReconciliationEntity
import com.example.phfinance.data.local.ServiceEntity
import com.example.phfinance.data.local.CostAllocationEntity
import com.example.phfinance.data.local.ApportionmentPolicyEntity
import com.example.phfinance.data.local.TimeTrackingEntity
import com.example.phfinance.data.local.PricingSimulationScenarioEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupPayload(
    val version: Int = 5,
    val timestamp: Long = System.currentTimeMillis(),
    val clients: List<ClientEntity> = emptyList(),
    val services: List<ServiceEntity> = emptyList(),
    val contracts: List<ContractEntity> = emptyList(),
    val contractSessions: List<ContractSessionEntity> = emptyList(),
    val receivables: List<ReceivableEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val expenseCategories: List<ExpenseCategoryEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val accounts: List<FinancialAccountEntity> = emptyList(),
    val accountMovements: List<AccountMovementEntity> = emptyList(),
    val accountTransfers: List<AccountTransferEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val businessSettings: List<BusinessSettingsEntity> = emptyList(),
    val notificationLogs: List<NotificationLogEntity> = emptyList(),
    val monthlyClosures: List<MonthlyClosureEntity> = emptyList(),
    val closureHistories: List<ClosureHistoryEntity> = emptyList(),
    val messageTemplates: List<MessageTemplateEntity> = emptyList(),
    val bankImports: List<BankImportEntity> = emptyList(),
    val bankTransactions: List<BankTransactionEntity> = emptyList(),
    val reconciliations: List<ReconciliationEntity> = emptyList(),
    val reconciliationAudits: List<ReconciliationAuditEntity> = emptyList(),
    val costAllocations: List<CostAllocationEntity> = emptyList(),
    val apportionmentPolicies: List<ApportionmentPolicyEntity> = emptyList(),
    val timeTrackings: List<TimeTrackingEntity> = emptyList(),
    val pricingSimulations: List<PricingSimulationScenarioEntity> = emptyList()
)

data class EncryptedBackupWrapper(
    val version: Int = 5,
    val timestamp: Long = System.currentTimeMillis(),
    val saltBase64: String,
    val ivBase64: String,
    val cipherTextBase64: String,
    val sha256Checksum: String
)

data class BackupImportResult(
    val success: Boolean,
    val message: String,
    val clientsCount: Int = 0,
    val receivablesCount: Int = 0,
    val expensesCount: Int = 0,
    val closuresCount: Int = 0
)

object BackupManager {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val payloadAdapter = moshi.adapter(BackupPayload::class.java)
    val wrapperAdapter = moshi.adapter(EncryptedBackupWrapper::class.java)

    const val PBKDF2_ITERATIONS_V3 = 600000
    const val PBKDF2_ITERATIONS_V2 = 10000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_SIZE_BYTES = 12
    private const val SALT_SIZE_BYTES = 16

    fun encryptPayloadToWrapper(
        rawPayload: BackupPayload,
        password: String,
        version: Int = 4
    ): EncryptedBackupWrapper {
        require(password.length >= 6) { "A senha de backup deve ter no mínimo 6 caracteres." }

        val jsonString = payloadAdapter.toJson(rawPayload)
        val sha256 = calculateSha256(jsonString)

        val iterations = if (version >= 3) PBKDF2_ITERATIONS_V3 else PBKDF2_ITERATIONS_V2

        // Generate Salt & IV using SecureRandom
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE_BYTES)
        random.nextBytes(salt)

        val iv = ByteArray(IV_SIZE_BYTES)
        random.nextBytes(iv)

        // Derive AES-256 key via PBKDF2-HMAC-SHA256
        val secretKey = deriveKey(password, salt, iterations)

        // Encrypt with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherText = cipher.doFinal(jsonString.toByteArray(Charsets.UTF_8))

        return EncryptedBackupWrapper(
            version = version,
            timestamp = System.currentTimeMillis(),
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            cipherTextBase64 = Base64.getEncoder().encodeToString(cipherText),
            sha256Checksum = sha256
        )
    }

    fun decryptWrapperToPayload(
        wrapperJson: String,
        password: String
    ): BackupPayload {
        if (!wrapperJson.contains("cipherTextBase64")) {
            return payloadAdapter.fromJson(wrapperJson)
                ?: throw IllegalArgumentException("Formato de backup inválido.")
        }

        val wrapper = wrapperAdapter.fromJson(wrapperJson)
            ?: throw IllegalArgumentException("Arquivo de backup corrompido ou formato inválido.")

        val iterations = when (wrapper.version) {
            5 -> PBKDF2_ITERATIONS_V3
            4 -> PBKDF2_ITERATIONS_V3
            3 -> PBKDF2_ITERATIONS_V3
            2 -> PBKDF2_ITERATIONS_V2
            else -> PBKDF2_ITERATIONS_V2
        }

        val salt = Base64.getDecoder().decode(wrapper.saltBase64)
        val iv = Base64.getDecoder().decode(wrapper.ivBase64)
        val cipherText = Base64.getDecoder().decode(wrapper.cipherTextBase64)

        val secretKey = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(cipherText)
        val jsonString = String(decryptedBytes, Charsets.UTF_8)

        // Verify SHA-256 checksum
        val computedSha256 = calculateSha256(jsonString)
        if (computedSha256 != wrapper.sha256Checksum) {
            throw IllegalStateException("Falha de integridade: SHA-256 divergente.")
        }

        return payloadAdapter.fromJson(jsonString)
            ?: throw IllegalArgumentException("Dados de backup corrompidos após descriptografia.")
    }

    suspend fun createEncryptedBackup(
        context: Context,
        dao: PhFinanceDao,
        password: String
    ): String = withContext(Dispatchers.IO) {
        val clients = dao.observeAllClients().first()
        val services = dao.observeAllServices().first()
        val contracts = dao.observeAllContracts().first()
        val contractSessions = dao.observeAllContractSessions().first()
        val receivables = dao.observeAllReceivables().first()
        val payments = dao.observeAllPayments().first()
        val expenseCategories = dao.observeExpenseCategories().first()
        val expenses = dao.observeAllExpenses().first()
        val accounts = dao.observeAllAccounts().first()
        val accountMovements = dao.observeAllMovements().first()
        val accountTransfers = dao.observeAllTransfers().first()
        val goal = dao.getGoal()?.let { listOf(it) } ?: emptyList()
        val settings = dao.getSettings()?.let { listOf(it) } ?: emptyList()
        val notificationLogs = dao.observeNotificationLogs().first()
        val closures = dao.observeAllClosures().first()
        val closureHistories = dao.observeAllClosureHistories().first()
        val templates = dao.observeAllTemplates().first()
        val bankImports = dao.observeAllBankImports().first()
        val bankTransactions = dao.observeAllBankTransactions().first()
        val reconciliations = dao.observeAllReconciliations().first()
        val reconciliationAudits = dao.observeAllReconciliationAudits().first()
        val costAllocations = dao.observeAllCostAllocations().first()
        val apportionmentPolicies = dao.observeAllApportionmentPolicies().first()
        val timeTrackings = dao.observeAllTimeTrackings().first()
        val pricingSimulations = dao.observeAllPricingSimulations().first()

        val rawPayload = BackupPayload(
            version = 5,
            timestamp = System.currentTimeMillis(),
            clients = clients,
            services = services,
            contracts = contracts,
            contractSessions = contractSessions,
            receivables = receivables,
            payments = payments,
            expenseCategories = expenseCategories,
            expenses = expenses,
            accounts = accounts,
            accountMovements = accountMovements,
            accountTransfers = accountTransfers,
            goals = goal,
            businessSettings = settings,
            notificationLogs = notificationLogs,
            monthlyClosures = closures,
            closureHistories = closureHistories,
            messageTemplates = templates,
            bankImports = bankImports,
            bankTransactions = bankTransactions,
            reconciliations = reconciliations,
            reconciliationAudits = reconciliationAudits,
            costAllocations = costAllocations,
            apportionmentPolicies = apportionmentPolicies,
            timeTrackings = timeTrackings,
            pricingSimulations = pricingSimulations
        )

        val wrapper = encryptPayloadToWrapper(rawPayload, password, version = 5)
        val finalEncryptedJson = wrapperAdapter.toJson(wrapper)

        // Share via FileProvider
        try {
            val cacheDir = File(context.cacheDir, "backups")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val fileName = "ph_finance_backup_${System.currentTimeMillis()}.phb"
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(finalEncryptedJson.toByteArray())
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Exportar Backup Criptografado (AES-256-GCM)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        finalEncryptedJson
    }

    suspend fun restoreEncryptedBackup(
        backupContent: String,
        password: String,
        dao: PhFinanceDao,
        database: androidx.room.RoomDatabase
    ): BackupImportResult = withContext(Dispatchers.IO) {
        try {
            val payload = decryptWrapperToPayload(backupContent, password)
            require(payload.version in 1..5) { "Versão de backup inválida ou não suportada: ${payload.version}" }

            database.withTransaction {
                importPayloadInternal(payload, dao)
            }

            BackupImportResult(
                success = true,
                message = "Backup criptografado AES-256-GCM restaurado com sucesso!",
                clientsCount = payload.clients.size,
                receivablesCount = payload.receivables.size,
                expensesCount = payload.expenses.size,
                closuresCount = payload.monthlyClosures.size
            )
        } catch (e: Exception) {
            BackupImportResult(
                success = false,
                message = "Falha ao restaurar backup: ${e.localizedMessage ?: "Senha incorreta ou arquivo adulterado."}"
            )
        }
    }

    suspend fun importPayload(
        payload: BackupPayload,
        dao: PhFinanceDao,
        successMsg: String,
        database: androidx.room.RoomDatabase
    ): BackupImportResult = withContext(Dispatchers.IO) {
        try {
            require(payload.version in 1..5) { "Versão de backup inválida ou não suportada: ${payload.version}" }
            database.withTransaction {
                importPayloadInternal(payload, dao)
            }
            BackupImportResult(
                success = true,
                message = successMsg,
                clientsCount = payload.clients.size,
                receivablesCount = payload.receivables.size,
                expensesCount = payload.expenses.size,
                closuresCount = payload.monthlyClosures.size
            )
        } catch (e: Exception) {
            BackupImportResult(
                success = false,
                message = "Erro ao importar payload: ${e.localizedMessage}"
            )
        }
    }

    private suspend fun importPayloadInternal(payload: BackupPayload, dao: PhFinanceDao) {
        payload.expenseCategories.forEach { dao.insertExpenseCategory(it) }
        payload.clients.forEach { dao.insertClient(it) }
        payload.services.forEach { dao.insertService(it) }
        payload.accounts.forEach { dao.insertAccount(it) }
        payload.contracts.forEach { dao.insertContract(it) }
        payload.contractSessions.forEach { dao.insertContractSession(it) }
        payload.receivables.forEach { dao.insertReceivable(it) }
        payload.payments.forEach { dao.insertPayment(it) }
        payload.expenses.forEach { dao.insertExpense(it) }
        payload.accountMovements.forEach { dao.insertMovement(it) }
        payload.accountTransfers.forEach { dao.insertTransfer(it) }
        payload.goals.forEach { dao.insertOrUpdateGoal(it) }
        payload.businessSettings.forEach { dao.insertOrUpdateSettings(it) }
        payload.notificationLogs.forEach { dao.insertNotificationLog(it) }
        payload.monthlyClosures.forEach { dao.insertOrUpdateClosure(it) }
        payload.closureHistories.forEach { dao.insertClosureHistory(it) }
        payload.messageTemplates.forEach { dao.insertMessageTemplate(it) }
        payload.bankImports.forEach { dao.insertBankImport(it) }
        payload.bankTransactions.forEach { dao.insertBankTransaction(it) }
        payload.reconciliations.forEach { dao.insertReconciliation(it) }
        payload.reconciliationAudits.forEach { dao.insertReconciliationAudit(it) }
        payload.costAllocations.forEach { dao.insertCostAllocation(it) }
        payload.apportionmentPolicies.forEach { dao.insertApportionmentPolicy(it) }
        payload.timeTrackings.forEach { dao.insertTimeTracking(it) }
        payload.pricingSimulations.forEach { dao.insertPricingSimulation(it) }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun calculateSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
