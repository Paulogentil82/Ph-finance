package com.example.phfinance.data.repository

import com.example.phfinance.data.local.AccountMovementEntity
import com.example.phfinance.data.local.AccountTransferEntity
import com.example.phfinance.data.local.BusinessSettingsEntity
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ClosureHistoryEntity
import com.example.phfinance.data.local.ContractEntity
import com.example.phfinance.data.local.ContractSessionEntity
import com.example.phfinance.data.local.ExpenseCategoryEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.FinancialAccountEntity
import com.example.phfinance.data.local.GoalEntity
import com.example.phfinance.data.local.MonthlyClosureEntity
import com.example.phfinance.data.local.NotificationLogEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.PhFinanceDao
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.local.ServiceEntity
import com.example.phfinance.data.local.BankImportEntity
import com.example.phfinance.data.local.BankTransactionEntity
import com.example.phfinance.data.local.ReconciliationAuditEntity
import com.example.phfinance.data.local.ReconciliationEntity
import com.example.phfinance.data.model.BankTransactionStatus
import com.example.phfinance.data.model.BillingPeriod
import com.example.phfinance.data.model.ClientType
import com.example.phfinance.data.model.ContractStatus
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.MatchConfidence
import com.example.phfinance.data.model.MovementType
import com.example.phfinance.data.model.ParsedBankStatement
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.data.model.ReconciliationMatchSuggestion
import com.example.phfinance.data.model.ReconciliationType
import com.example.phfinance.util.reconciliation.ReconciliationEngine
import com.example.phfinance.data.local.CostAllocationEntity
import com.example.phfinance.data.local.ApportionmentPolicyEntity
import com.example.phfinance.data.local.TimeTrackingEntity
import com.example.phfinance.data.local.PricingSimulationScenarioEntity
import com.example.phfinance.data.model.ApportionmentMethod
import com.example.phfinance.data.model.ApportionmentPreviewItem
import com.example.phfinance.data.model.CostAllocationType
import com.example.phfinance.data.model.PricingScenarioResult
import com.example.phfinance.util.ProfitabilityEngine
import com.example.phfinance.util.PricingSimulatorEngine
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs

class PhFinanceRepository(
    private val dao: PhFinanceDao,
    private val database: androidx.room.RoomDatabase
) {

    // --- CLIENTS ---
    fun observeActiveClients(): Flow<List<ClientEntity>> = dao.observeActiveClients()
    fun observeAllClients(): Flow<List<ClientEntity>> = dao.observeAllClients()
    fun observeClientById(clientId: String): Flow<ClientEntity?> = dao.observeClientById(clientId)
    suspend fun getClientById(clientId: String): ClientEntity? = dao.getClientById(clientId)

    suspend fun saveClient(client: ClientEntity) {
        val existing = dao.getClientById(client.id)
        if (existing == null) {
            dao.insertClient(client)
        } else {
            dao.updateClient(client.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun archiveClient(clientId: String) {
        dao.archiveClient(clientId)
    }

    suspend fun unarchiveClient(clientId: String) {
        dao.unarchiveClient(clientId)
    }

    // --- SERVICES ---
    fun observeAllServices(): Flow<List<ServiceEntity>> = dao.observeAllServices()
    fun observeActiveServices(): Flow<List<ServiceEntity>> = dao.observeActiveServices()

    suspend fun saveService(service: ServiceEntity) {
        val existing = dao.getServiceById(service.id)
        if (existing == null) {
            dao.insertService(service)
        } else {
            dao.updateService(service.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // --- CONTRACTS ---
    fun observeAllContracts(): Flow<List<ContractEntity>> = dao.observeAllContracts()
    fun observeContractsByClient(clientId: String): Flow<List<ContractEntity>> = dao.observeContractsByClient(clientId)
    fun observeActiveContracts(): Flow<List<ContractEntity>> = dao.observeActiveContracts()

    suspend fun createContractWithSession(
        contract: ContractEntity,
        sessionCount: Int = 0
    ) {
        dao.insertContract(contract)
        if (sessionCount > 0) {
            dao.insertContractSession(
                ContractSessionEntity(
                    id = UUID.randomUUID().toString(),
                    contractId = contract.id,
                    clientId = contract.clientId,
                    totalSessions = sessionCount,
                    completedSessions = 0,
                    cancelledSessions = 0,
                    remainingSessions = sessionCount
                )
            )
        }
        // Auto-generate initial receivable for contract
        val competence = DateUtils.getCurrentCompetence()
        val calendar = Calendar.getInstance()
        val dueDate = DateUtils.getAdjustedDueDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            contract.dueDay
        )
        generateReceivableIdempotent(
            contract = contract,
            competence = competence,
            dueDate = dueDate
        )
    }

    suspend fun updateContractStatus(contractId: String, newStatus: ContractStatus) {
        dao.updateContractStatus(contractId, newStatus)
    }

    // --- SESSIONS ---
    fun observeSessionsByClient(clientId: String): Flow<List<ContractSessionEntity>> = dao.observeSessionsByClient(clientId)
    fun observeAllContractSessions(): Flow<List<ContractSessionEntity>> = dao.observeAllContractSessions()

    suspend fun registerSessionCompleted(sessionId: String, notes: String = "") {
        val sessions = dao.observeAllContractSessions().first()
        val target = sessions.find { it.id == sessionId } ?: return
        if (target.remainingSessions > 0) {
            dao.updateContractSession(
                target.copy(
                    completedSessions = target.completedSessions + 1,
                    remainingSessions = target.remainingSessions - 1,
                    lastSessionDate = System.currentTimeMillis(),
                    notes = if (notes.isBlank()) target.notes else notes,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // --- RECEIVABLES & RECURRENCE ---
    fun observeAllReceivables(): Flow<List<ReceivableEntity>> = dao.observeAllReceivables()
    fun observeReceivablesByClient(clientId: String): Flow<List<ReceivableEntity>> = dao.observeReceivablesByClient(clientId)
    fun observeReceivablesByCompetence(competence: String): Flow<List<ReceivableEntity>> = dao.observeReceivablesByCompetence(competence)

    suspend fun generateReceivableIdempotent(
        contract: ContractEntity,
        competence: String,
        dueDate: Long
    ): Boolean {
        // Enforce domain lock for closed monthly periods
        val closure = dao.getClosureByCompetence(competence)
        if (closure?.status == "DEFINITIVO") {
            throw IllegalStateException("Competência $competence possui Fechamento Definitivo e está bloqueada para novas cobranças.")
        }

        // Idempotency rule: Check if a charge for this contract & competence already exists
        val existing = dao.getReceivableByContractAndCompetence(contract.id, competence)
        if (existing != null) return false

        val netValue = (contract.contractedValueCents - contract.discountCents).coerceAtLeast(0L)
        val receivable = ReceivableEntity(
            id = UUID.randomUUID().toString(),
            clientId = contract.clientId,
            contractId = contract.id,
            competence = competence,
            issueDate = System.currentTimeMillis(),
            dueDate = dueDate,
            originalValueCents = contract.contractedValueCents,
            discountCents = contract.discountCents,
            additionCents = 0L,
            netValueCents = netValue,
            receivedValueCents = 0L,
            status = if (dueDate < System.currentTimeMillis()) ReceivableStatus.VENCIDA else ReceivableStatus.PENDENTE,
            notes = "Gerado via contrato"
        )
        dao.insertReceivable(receivable)
        return true
    }

    suspend fun generateCustomReceivable(
        clientId: String,
        description: String,
        valueCents: Long,
        discountCents: Long,
        dueDate: Long,
        competence: String
    ) {
        // Enforce domain lock for closed monthly periods
        val closure = dao.getClosureByCompetence(competence)
        if (closure?.status == "DEFINITIVO") {
            throw IllegalStateException("Competência $competence possui Fechamento Definitivo e está bloqueada para novas cobranças.")
        }

        val netValue = (valueCents - discountCents).coerceAtLeast(0L)
        val receivable = ReceivableEntity(
            id = UUID.randomUUID().toString(),
            clientId = clientId,
            contractId = "",
            competence = competence,
            issueDate = System.currentTimeMillis(),
            dueDate = dueDate,
            originalValueCents = valueCents,
            discountCents = discountCents,
            additionCents = 0L,
            netValueCents = netValue,
            receivedValueCents = 0L,
            status = if (dueDate < System.currentTimeMillis()) ReceivableStatus.VENCIDA else ReceivableStatus.PENDENTE,
            notes = description
        )
        dao.insertReceivable(receivable)
    }

    suspend fun generateMonthlyRecurrentDues(): Int {
        val activeContracts = dao.observeActiveContracts().first()
        val competence = DateUtils.getCurrentCompetence()
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        // If current competence is locked, skip generation gracefully
        val closure = dao.getClosureByCompetence(competence)
        if (closure?.status == "DEFINITIVO") return 0

        var createdCount = 0
        for (contract in activeContracts) {
            val dueDate = DateUtils.getAdjustedDueDate(year, month, contract.dueDay)
            val created = generateReceivableIdempotent(contract, competence, dueDate)
            if (created) createdCount++
        }
        return createdCount
    }

    // --- PAYMENT SETTLEMENT (BAIXA FINANCEIRA) ---
    fun observeAllPayments(): Flow<List<PaymentEntity>> = dao.observeAllPayments()
    fun observePaymentsByReceivable(receivableId: String): Flow<List<PaymentEntity>> = dao.observePaymentsByReceivable(receivableId)
    fun observePaymentsByClient(clientId: String): Flow<List<PaymentEntity>> = dao.observePaymentsByClient(clientId)

    suspend fun registerPayment(
        receivableId: String,
        amountReceivedCents: Long,
        feeCents: Long,
        destinationAccountId: String,
        paymentMethod: PaymentMethod,
        transactionRef: String = "",
        notes: String = ""
    ) {
        database.withTransaction {
            val receivable = dao.getReceivableById(receivableId) ?: return@withTransaction

            // Enforce domain lock for closed monthly periods
            val closure = dao.getClosureByCompetence(receivable.competence)
            if (closure?.status == "DEFINITIVO") {
                throw IllegalStateException("Competência ${receivable.competence} possui Fechamento Definitivo e está bloqueada para registro de pagamentos.")
            }

            val netAmountCents = (amountReceivedCents - feeCents).coerceAtLeast(0L)

            // 1. Create Payment record
            val payment = PaymentEntity(
                id = UUID.randomUUID().toString(),
                receivableId = receivableId,
                clientId = receivable.clientId,
                grossAmountCents = amountReceivedCents,
                feeCents = feeCents,
                netAmountCents = netAmountCents,
                paymentDate = System.currentTimeMillis(),
                destinationAccountId = destinationAccountId,
                paymentMethod = paymentMethod,
                transactionRef = transactionRef,
                notes = notes
            )
            dao.insertPayment(payment)

            // 2. Update Receivable received value & status
            val newReceivedTotal = receivable.receivedValueCents + amountReceivedCents
            val newStatus = if (newReceivedTotal >= receivable.netValueCents) {
                ReceivableStatus.QUITADA
            } else {
                ReceivableStatus.PARCIALMENTE_PAGA
            }
            dao.updateReceivable(
                receivable.copy(
                    receivedValueCents = newReceivedTotal,
                    status = newStatus,
                    updatedAt = System.currentTimeMillis()
                )
            )

            // 3. Update Destination Account Balance
            dao.updateAccountBalance(destinationAccountId, netAmountCents)

            // 4. Log Account Movement
            val client = dao.getClientById(receivable.clientId)
            val clientName = client?.fullName ?: "Aluno"
            dao.insertMovement(
                AccountMovementEntity(
                    id = UUID.randomUUID().toString(),
                    accountId = destinationAccountId,
                    type = MovementType.ENTRADA,
                    amountCents = netAmountCents,
                    date = System.currentTimeMillis(),
                    description = "Recebimento: $clientName (${receivable.competence})",
                    relatedEntityId = payment.id
                )
            )
        }
    }

    // --- EXPENSES (CONTAS A PAGAR) ---
    fun observeAllExpenses(): Flow<List<ExpenseEntity>> = dao.observeAllExpenses()
    fun observeExpensesByCompetence(competence: String): Flow<List<ExpenseEntity>> = dao.observeExpensesByCompetence(competence)
    fun observeExpenseCategories(): Flow<List<ExpenseCategoryEntity>> = dao.observeExpenseCategories()

    suspend fun saveExpense(expense: ExpenseEntity) {
        // Enforce domain lock for closed monthly periods
        val closure = dao.getClosureByCompetence(expense.competence)
        if (closure?.status == "DEFINITIVO") {
            throw IllegalStateException("Competência ${expense.competence} possui Fechamento Definitivo e está bloqueada para lançamentos de despesas.")
        }

        val existing = dao.getExpenseById(expense.id)
        if (existing == null) {
            dao.insertExpense(expense)
        } else {
            dao.updateExpense(expense.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun payExpense(
        expenseId: String,
        accountId: String,
        paymentMethod: PaymentMethod,
        paymentDate: Long = System.currentTimeMillis()
    ) {
        val expense = dao.getExpenseById(expenseId) ?: return
        if (expense.status == ExpenseStatus.PAGO) return

        // Enforce domain lock for closed monthly periods
        val closure = dao.getClosureByCompetence(expense.competence)
        if (closure?.status == "DEFINITIVO") {
            throw IllegalStateException("Competência ${expense.competence} possui Fechamento Definitivo e está bloqueada para baixa de despesas.")
        }

        // 1. Update Expense
        dao.updateExpense(
            expense.copy(
                status = ExpenseStatus.PAGO,
                paymentDate = paymentDate,
                accountId = accountId,
                paymentMethod = paymentMethod,
                updatedAt = System.currentTimeMillis()
            )
        )

        // 2. Deduct Balance from Financial Account
        dao.updateAccountBalance(accountId, -expense.valueCents)

        // 3. Log Account Movement
        val desc = if (expense.isOwnerPersonalWithdrawal) {
            "Retirada Pessoal / Pro-Labore: ${expense.description}"
        } else {
            "Despesa Operacional: ${expense.description}"
        }
        dao.insertMovement(
            AccountMovementEntity(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                type = MovementType.SAIDA,
                amountCents = expense.valueCents,
                date = paymentDate,
                description = desc,
                relatedEntityId = expense.id
            )
        )
    }

    suspend fun addExpenseCategory(name: String) {
        dao.insertExpenseCategory(
            ExpenseCategoryEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                isSystemDefault = false
            )
        )
    }

    // --- ACCOUNTS & TRANSFERS ---
    fun observeAllAccounts(): Flow<List<FinancialAccountEntity>> = dao.observeAllAccounts()
    fun observeMovementsByAccount(accountId: String): Flow<List<AccountMovementEntity>> = dao.observeMovementsByAccount(accountId)
    fun observeAllMovements(): Flow<List<AccountMovementEntity>> = dao.observeAllMovements()

    suspend fun saveAccount(account: FinancialAccountEntity) {
        val existing = dao.getAccountById(account.id)
        if (existing == null) {
            dao.insertAccount(account)
        } else {
            dao.updateAccount(account.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun transferBetweenAccounts(
        sourceAccountId: String,
        destinationAccountId: String,
        amountCents: Long,
        description: String = "Transferência Interna"
    ): Boolean {
        if (sourceAccountId == destinationAccountId || amountCents <= 0) return false
        val source = dao.getAccountById(sourceAccountId) ?: return false
        val dest = dao.getAccountById(destinationAccountId) ?: return false

        val transferId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 1. Deduct source
        dao.updateAccountBalance(sourceAccountId, -amountCents)
        dao.insertMovement(
            AccountMovementEntity(
                id = UUID.randomUUID().toString(),
                accountId = sourceAccountId,
                type = MovementType.TRANSFERENCIA,
                amountCents = amountCents,
                date = now,
                description = "Transferência para ${dest.name}: $description",
                relatedEntityId = transferId
            )
        )

        // 2. Add destination
        dao.updateAccountBalance(destinationAccountId, amountCents)
        dao.insertMovement(
            AccountMovementEntity(
                id = UUID.randomUUID().toString(),
                accountId = destinationAccountId,
                type = MovementType.TRANSFERENCIA,
                amountCents = amountCents,
                date = now,
                description = "Transferência de ${source.name}: $description",
                relatedEntityId = transferId
            )
        )

        // 3. Log Transfer
        dao.insertTransfer(
            AccountTransferEntity(
                id = transferId,
                sourceAccountId = sourceAccountId,
                destinationAccountId = destinationAccountId,
                amountCents = amountCents,
                date = now,
                description = description
            )
        )
        return true
    }

    // --- GOALS & SETTINGS ---
    fun observeGoal(): Flow<GoalEntity?> = dao.observeGoal()
    fun observeSettings(): Flow<BusinessSettingsEntity?> = dao.observeSettings()

    suspend fun saveGoal(goal: GoalEntity) {
        dao.insertOrUpdateGoal(goal)
    }

    suspend fun saveSettings(settings: BusinessSettingsEntity) {
        dao.insertOrUpdateSettings(settings)
    }

    // --- NOTIFICATION LOGS ---
    fun observeNotificationLogs(): Flow<List<NotificationLogEntity>> = dao.observeNotificationLogs()

    suspend fun logNotification(log: NotificationLogEntity) {
        dao.insertNotificationLog(log)
    }

    // --- MONTHLY CLOSURES ---
    fun observeClosureByCompetence(competence: String): Flow<MonthlyClosureEntity?> = dao.observeClosureByCompetence(competence)
    fun observeClosureHistory(closureId: String): Flow<List<ClosureHistoryEntity>> = dao.observeClosureHistory(closureId)
    fun observeAllClosureHistories(): Flow<List<ClosureHistoryEntity>> = dao.observeAllClosureHistories()
    suspend fun getClosureByCompetence(competence: String): MonthlyClosureEntity? = dao.getClosureByCompetence(competence)

    suspend fun saveMonthlyClosure(
        competence: String,
        status: String,
        accrualRevenueCents: Long,
        accrualExpenseCents: Long,
        cashRevenueCents: Long,
        cashExpenseCents: Long,
        notes: String = ""
    ) {
        val closureId = "closure_${competence.replace("/", "_")}"
        val existing = dao.getClosureByCompetence(competence)
        val netResultCents = cashRevenueCents - cashExpenseCents

        val updatedClosure = MonthlyClosureEntity(
            id = closureId,
            competence = competence,
            status = status,
            closedAt = System.currentTimeMillis(),
            closedBy = "Proprietário",
            accrualRevenueCents = accrualRevenueCents,
            accrualExpenseCents = accrualExpenseCents,
            cashRevenueCents = cashRevenueCents,
            cashExpenseCents = cashExpenseCents,
            netResultCents = netResultCents,
            notes = notes,
            isReopened = existing?.isReopened ?: false,
            reopenedAt = existing?.reopenedAt,
            reopenReason = existing?.reopenReason ?: ""
        )
        dao.insertOrUpdateClosure(updatedClosure)

        val actionType = if (status == "DEFINITIVO") "FECHADO_DEFINITIVO" else "CRIADO_RASCUNHO"
        dao.insertClosureHistory(
            ClosureHistoryEntity(
                id = UUID.randomUUID().toString(),
                closureId = closureId,
                competence = competence,
                actionType = actionType,
                timestamp = System.currentTimeMillis(),
                reason = notes.ifBlank { "Fechamento $status da competência $competence" },
                user = "Proprietário"
            )
        )
    }

    suspend fun reopenMonthlyClosure(competence: String, reason: String) {
        val existing = dao.getClosureByCompetence(competence) ?: return
        val updated = existing.copy(
            status = "RASCUNHO",
            isReopened = true,
            reopenedAt = System.currentTimeMillis(),
            reopenReason = reason
        )
        dao.insertOrUpdateClosure(updated)

        dao.insertClosureHistory(
            ClosureHistoryEntity(
                id = UUID.randomUUID().toString(),
                closureId = existing.id,
                competence = competence,
                actionType = "REABERTO",
                timestamp = System.currentTimeMillis(),
                reason = reason,
                user = "Proprietário"
            )
        )
    }

    // --- MESSAGE TEMPLATES ---
    fun observeAllMessageTemplates(): Flow<List<com.example.phfinance.data.local.MessageTemplateEntity>> = dao.observeAllTemplates()

    suspend fun saveMessageTemplate(template: com.example.phfinance.data.local.MessageTemplateEntity) {
        dao.insertMessageTemplate(template.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun cancelReceivable(receivableId: String) {
        val receivable = dao.getReceivableById(receivableId) ?: return
        val closure = dao.getClosureByCompetence(receivable.competence)
        if (closure?.status == "DEFINITIVO") {
            throw IllegalStateException("Competência ${receivable.competence} possui Fechamento Definitivo e está bloqueada para cancelamento de cobranças.")
        }
        dao.cancelReceivable(receivableId)
    }

    // --- BANK RECONCILIATION ---
    fun observeAllBankImports(): Flow<List<BankImportEntity>> = dao.observeAllBankImports()
    fun observeBankImportsByAccount(accountId: String): Flow<List<BankImportEntity>> = dao.observeBankImportsByAccount(accountId)
    fun observeAllBankTransactions(): Flow<List<BankTransactionEntity>> = dao.observeAllBankTransactions()
    fun observeBankTransactionsByAccount(accountId: String): Flow<List<BankTransactionEntity>> = dao.observeBankTransactionsByAccount(accountId)
    fun observeAllReconciliations(): Flow<List<ReconciliationEntity>> = dao.observeAllReconciliations()
    fun observeActiveReconciliations(): Flow<List<ReconciliationEntity>> = dao.observeActiveReconciliations()
    fun observeAllReconciliationAudits(): Flow<List<ReconciliationAuditEntity>> = dao.observeAllReconciliationAudits()
    fun observeReconciliationAuditsByReconciliation(recId: String): Flow<List<ReconciliationAuditEntity>> = dao.observeReconciliationAuditsByReconciliation(recId)

    suspend fun importBankStatement(statement: ParsedBankStatement): Pair<BankImportEntity, Int> {
        val account = dao.getAccountById(statement.accountId)
            ?: throw IllegalArgumentException("Conta bancária de destino não encontrada.")

        var totalCredits = 0L
        var totalDebits = 0L
        statement.transactions.forEach {
            if (it.amountCents >= 0) totalCredits += it.amountCents
            else totalDebits += abs(it.amountCents)
        }

        val importEntity = BankImportEntity(
            id = UUID.randomUUID().toString(),
            accountId = statement.accountId,
            fileName = statement.fileName,
            fileType = statement.fileType,
            fileSizeBytes = statement.fileSizeBytes,
            importTimestamp = System.currentTimeMillis(),
            startDate = statement.startDate,
            endDate = statement.endDate,
            totalTransactions = statement.transactions.size,
            totalCreditsCents = totalCredits,
            totalDebitsCents = totalDebits,
            status = "PROCESSADO"
        )
        dao.insertBankImport(importEntity)

        var newTransactionsCount = 0
        val toInsert = mutableListOf<BankTransactionEntity>()

        for (parsed in statement.transactions) {
            val rawHash = ReconciliationEngine.computeRawHash(
                statement.accountId,
                parsed.date,
                parsed.amountCents,
                parsed.memo
            )

            // Idempotency: check if identical fitId exists for this account
            val existingByFitId = dao.getBankTransactionByFitId(statement.accountId, parsed.fitId)
            if (existingByFitId != null) {
                // If it already exists and is active/processed, skip to avoid duplicates on re-import
                continue
            }

            // Check if duplicate rawHash exists (same account, date, amount, normalized memo)
            val existingByHash = dao.getBankTransactionsByRawHash(statement.accountId, rawHash)
            val initialStatus = if (existingByHash.isNotEmpty()) {
                BankTransactionStatus.SUSPEITA_DUPLICIDADE
            } else {
                BankTransactionStatus.PENDENTE
            }

            val txEntity = BankTransactionEntity(
                id = UUID.randomUUID().toString(),
                importId = importEntity.id,
                accountId = statement.accountId,
                fitId = parsed.fitId,
                transactionDate = parsed.date,
                amountCents = parsed.amountCents,
                movementType = parsed.movementType,
                memo = parsed.memo,
                payee = parsed.payee,
                status = initialStatus,
                reconciliationId = null,
                rawHash = rawHash,
                createdAt = System.currentTimeMillis()
            )
            toInsert.add(txEntity)
            newTransactionsCount++
        }

        if (toInsert.isNotEmpty()) {
            dao.insertBankTransactions(toInsert)
        }

        return Pair(importEntity, newTransactionsCount)
    }

    suspend fun getMatchSuggestion(bankTxId: String): ReconciliationMatchSuggestion {
        val tx = dao.getBankTransactionById(bankTxId)
            ?: return ReconciliationMatchSuggestion(
                bankTransactionId = bankTxId,
                confidence = MatchConfidence.INSUFICIENTE,
                justification = "Transação não encontrada."
            )

        val payments = dao.observeAllPayments().first()
        val receivables = dao.observeAllReceivables().first()
        val expenses = dao.observeAllExpenses().first()
        val clients = dao.observeAllClients().first()

        return ReconciliationEngine.findBestMatch(tx, payments, receivables, expenses, clients)
    }

    /**
     * Fluxo A: Link an imported bank credit to an ALREADY REGISTERED payment.
     * Prevents duplicate cash entry because cash was already credited upon original payment registration.
     */
    suspend fun reconcileFluxoA(
        bankTxId: String,
        paymentId: String,
        user: String = "Proprietário"
    ): ReconciliationEntity {
        val tx = dao.getBankTransactionById(bankTxId)
            ?: throw IllegalArgumentException("Transação bancária não encontrada.")
        val payment = dao.getPaymentById(paymentId)
            ?: throw IllegalArgumentException("Pagamento com id $paymentId não encontrado.")

        val reconciliation = ReconciliationEntity(
            id = UUID.randomUUID().toString(),
            bankTransactionId = tx.id,
            accountId = tx.accountId,
            reconciliationType = ReconciliationType.VINCULO_PAGAMENTO_EXISTENTE,
            paymentId = payment.id,
            receivableId = payment.receivableId,
            reconciledAmountCents = abs(tx.amountCents),
            feeCents = payment.feeCents,
            grossAmountCents = payment.grossAmountCents,
            reconciledAt = System.currentTimeMillis(),
            reconciledBy = user,
            status = "ATIVA",
            notes = "Vinculado ao pagamento já existente ${payment.id} sem duplicação de caixa."
        )
        dao.insertReconciliation(reconciliation)

        dao.updateBankTransactionStatus(
            id = tx.id,
            status = BankTransactionStatus.CONCILIADA,
            reconciliationId = reconciliation.id
        )

        dao.insertReconciliationAudit(
            ReconciliationAuditEntity(
                id = UUID.randomUUID().toString(),
                reconciliationId = reconciliation.id,
                bankTransactionId = tx.id,
                actionType = "CRIACAO_VINCULO",
                timestamp = System.currentTimeMillis(),
                user = user,
                detailsJson = "Fluxo A: Conciliado com pagamento ${payment.id} (Recebível ${payment.receivableId})"
            )
        )

        return reconciliation
    }

    /**
     * Fluxo B: Settle a pending receivable from an imported bank credit.
     * Creates payment record, updates receivable received amount and status, updates account balance, and logs account movement.
     * Strictly enforces closed monthly period locks.
     */
    suspend fun reconcileFluxoB(
        bankTxId: String,
        receivableId: String,
        feeCents: Long = 0L,
        paymentMethod: PaymentMethod = PaymentMethod.PIX,
        user: String = "Proprietário"
    ): ReconciliationEntity {
        val tx = dao.getBankTransactionById(bankTxId)
            ?: throw IllegalArgumentException("Transação bancária não encontrada.")
        val receivable = dao.getReceivableById(receivableId)
            ?: throw IllegalArgumentException("Cobrança/Recebível com id $receivableId não encontrado.")

        // Enforce monthly closure lock
        val closure = dao.getClosureByCompetence(receivable.competence)
        if (closure?.status == "DEFINITIVO") {
            throw IllegalStateException("Competência ${receivable.competence} possui Fechamento Definitivo e está bloqueada para novos recebimentos.")
        }

        val netAmountCents = abs(tx.amountCents)
        val grossAmountCents = netAmountCents + feeCents

        // 1. Create Payment
        val payment = PaymentEntity(
            id = UUID.randomUUID().toString(),
            receivableId = receivable.id,
            clientId = receivable.clientId,
            grossAmountCents = grossAmountCents,
            feeCents = feeCents,
            netAmountCents = netAmountCents,
            paymentDate = tx.transactionDate,
            destinationAccountId = tx.accountId,
            paymentMethod = paymentMethod,
            transactionRef = tx.fitId,
            notes = "Conciliação bancária: ${tx.memo}"
        )
        dao.insertPayment(payment)

        // 2. Update Receivable
        val newReceivedTotal = receivable.receivedValueCents + grossAmountCents
        val newStatus = if (newReceivedTotal >= receivable.netValueCents) {
            ReceivableStatus.QUITADA
        } else {
            ReceivableStatus.PARCIALMENTE_PAGA
        }
        dao.updateReceivable(
            receivable.copy(
                receivedValueCents = newReceivedTotal,
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            )
        )

        // 3. Update Destination Financial Account Balance
        dao.updateAccountBalance(tx.accountId, netAmountCents)

        // 4. Log Account Movement
        val client = dao.getClientById(receivable.clientId)
        val clientName = client?.fullName ?: "Aluno"
        dao.insertMovement(
            AccountMovementEntity(
                id = UUID.randomUUID().toString(),
                accountId = tx.accountId,
                type = MovementType.ENTRADA,
                amountCents = netAmountCents,
                date = tx.transactionDate,
                description = "Recebimento Conciliado: $clientName (${receivable.competence})",
                relatedEntityId = payment.id
            )
        )

        // 5. Create Reconciliation record
        val reconciliation = ReconciliationEntity(
            id = UUID.randomUUID().toString(),
            bankTransactionId = tx.id,
            accountId = tx.accountId,
            reconciliationType = ReconciliationType.NOVO_RECEBIMENTO,
            paymentId = payment.id,
            receivableId = receivable.id,
            reconciledAmountCents = netAmountCents,
            feeCents = feeCents,
            grossAmountCents = grossAmountCents,
            reconciledAt = System.currentTimeMillis(),
            reconciledBy = user,
            status = "ATIVA",
            notes = "Fluxo B: Baixa de recebível via conciliação bancária."
        )
        dao.insertReconciliation(reconciliation)

        // 6. Update Transaction status
        dao.updateBankTransactionStatus(
            id = tx.id,
            status = BankTransactionStatus.CONCILIADA,
            reconciliationId = reconciliation.id
        )

        // 7. Audit log
        dao.insertReconciliationAudit(
            ReconciliationAuditEntity(
                id = UUID.randomUUID().toString(),
                reconciliationId = reconciliation.id,
                bankTransactionId = tx.id,
                actionType = "CRIACAO_NOVO_RECEBIMENTO",
                timestamp = System.currentTimeMillis(),
                user = user,
                detailsJson = "Fluxo B: Pagamento ${payment.id} criado para recebível ${receivable.id} (Líquido: ${CurrencyUtils.formatCents(netAmountCents)}, Taxa: ${CurrencyUtils.formatCents(feeCents)})"
            )
        )

        return reconciliation
    }

    /**
     * Reconcile Outflow (Debit) with Expense.
     * If expense is PENDENTE, marks it PAGO, decreases account balance and logs account movement.
     */
    suspend fun reconcileExpense(
        bankTxId: String,
        expenseId: String,
        user: String = "Proprietário"
    ): ReconciliationEntity {
        val tx = dao.getBankTransactionById(bankTxId)
            ?: throw IllegalArgumentException("Transação bancária não encontrada.")
        val expense = dao.getExpenseById(expenseId)
            ?: throw IllegalArgumentException("Despesa com id $expenseId não encontrada.")

        val debitAmount = abs(tx.amountCents)

        if (expense.status != ExpenseStatus.PAGO) {
            val closure = dao.getClosureByCompetence(expense.competence)
            if (closure?.status == "DEFINITIVO") {
                throw IllegalStateException("Competência ${expense.competence} possui Fechamento Definitivo e está bloqueada para liquidação de despesas.")
            }

            dao.updateExpense(
                expense.copy(
                    status = ExpenseStatus.PAGO,
                    paymentDate = tx.transactionDate,
                    accountId = tx.accountId,
                    updatedAt = System.currentTimeMillis()
                )
            )

            dao.updateAccountBalance(tx.accountId, -debitAmount)

            dao.insertMovement(
                AccountMovementEntity(
                    id = UUID.randomUUID().toString(),
                    accountId = tx.accountId,
                    type = MovementType.SAIDA,
                    amountCents = debitAmount,
                    date = tx.transactionDate,
                    description = "Despesa Conciliada: ${expense.description}",
                    relatedEntityId = expense.id
                )
            )
        }

        val reconciliation = ReconciliationEntity(
            id = UUID.randomUUID().toString(),
            bankTransactionId = tx.id,
            accountId = tx.accountId,
            reconciliationType = ReconciliationType.NOVA_DESPESA,
            expenseId = expense.id,
            reconciledAmountCents = debitAmount,
            grossAmountCents = debitAmount,
            reconciledAt = System.currentTimeMillis(),
            reconciledBy = user,
            status = "ATIVA",
            notes = "Despesa conciliada: ${expense.description}"
        )
        dao.insertReconciliation(reconciliation)

        dao.updateBankTransactionStatus(
            id = tx.id,
            status = BankTransactionStatus.CONCILIADA,
            reconciliationId = reconciliation.id
        )

        dao.insertReconciliationAudit(
            ReconciliationAuditEntity(
                id = UUID.randomUUID().toString(),
                reconciliationId = reconciliation.id,
                bankTransactionId = tx.id,
                actionType = "CRIACAO_NOVA_DESPESA",
                timestamp = System.currentTimeMillis(),
                user = user,
                detailsJson = "Despesa ${expense.id} vinculada e baixada via conciliação bancária."
            )
        )

        return reconciliation
    }

    /**
     * Undo reconciliation with complete audit trail and financial reversal.
     */
    suspend fun undoReconciliation(
        reconciliationId: String,
        reason: String,
        user: String = "Proprietário"
    ) {
        val rec = dao.getReconciliationById(reconciliationId) ?: return
        if (rec.status == "DESFEITA") return

        val tx = dao.getBankTransactionById(rec.bankTransactionId)

        when (rec.reconciliationType) {
            ReconciliationType.VINCULO_PAGAMENTO_EXISTENTE -> {
                // Fluxo A: No cash changes were introduced; simply unlink
            }
            ReconciliationType.NOVO_RECEBIMENTO -> {
                // Fluxo B: Reverse the payment, receivable received value, and account balance
                val payment = rec.paymentId?.let { dao.getPaymentById(it) }
                val receivable = rec.receivableId?.let { dao.getReceivableById(it) }

                if (receivable != null) {
                    val closure = dao.getClosureByCompetence(receivable.competence)
                    if (closure?.status == "DEFINITIVO") {
                        throw IllegalStateException("Competência ${receivable.competence} possui Fechamento Definitivo e não permite desfazimento.")
                    }

                    val updatedReceived = (receivable.receivedValueCents - rec.grossAmountCents).coerceAtLeast(0L)
                    val updatedStatus = if (updatedReceived <= 0L) ReceivableStatus.PENDENTE else ReceivableStatus.PARCIALMENTE_PAGA
                    dao.updateReceivable(
                        receivable.copy(
                            receivedValueCents = updatedReceived,
                            status = updatedStatus,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                if (payment != null) {
                    dao.deletePayment(payment.id)
                }

                // Revert account balance
                dao.updateAccountBalance(rec.accountId, -rec.reconciledAmountCents)

                // Log reversal movement
                dao.insertMovement(
                    AccountMovementEntity(
                        id = UUID.randomUUID().toString(),
                        accountId = rec.accountId,
                        type = MovementType.SAIDA,
                        amountCents = rec.reconciledAmountCents,
                        date = System.currentTimeMillis(),
                        description = "Estorno de Conciliação Bancária: ${reason.ifBlank { "Desfazimento" }}",
                        relatedEntityId = rec.id
                    )
                )
            }
            ReconciliationType.NOVA_DESPESA -> {
                val expense = rec.expenseId?.let { dao.getExpenseById(it) }
                if (expense != null) {
                    val closure = dao.getClosureByCompetence(expense.competence)
                    if (closure?.status == "DEFINITIVO") {
                        throw IllegalStateException("Competência ${expense.competence} possui Fechamento Definitivo e não permite desfazimento.")
                    }

                    dao.updateExpense(
                        expense.copy(
                            status = ExpenseStatus.PENDENTE,
                            paymentDate = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    dao.updateAccountBalance(rec.accountId, rec.reconciledAmountCents)
                    dao.insertMovement(
                        AccountMovementEntity(
                            id = UUID.randomUUID().toString(),
                            accountId = rec.accountId,
                            type = MovementType.ENTRADA,
                            amountCents = rec.reconciledAmountCents,
                            date = System.currentTimeMillis(),
                            description = "Estorno de Despesa Conciliada: ${reason.ifBlank { "Desfazimento" }}",
                            relatedEntityId = rec.id
                        )
                    )
                }
            }
        }

        // Update reconciliation status
        dao.updateReconciliation(
            rec.copy(
                status = "DESFEITA",
                revertReason = reason,
                revertedAt = System.currentTimeMillis(),
                revertedBy = user
            )
        )

        // Reset bank transaction status to PENDENTE
        if (tx != null) {
            dao.updateBankTransactionStatus(
                id = tx.id,
                status = BankTransactionStatus.PENDENTE,
                reconciliationId = null
            )
        }

        // Audit entry
        dao.insertReconciliationAudit(
            ReconciliationAuditEntity(
                id = UUID.randomUUID().toString(),
                reconciliationId = rec.id,
                bankTransactionId = rec.bankTransactionId,
                actionType = "DESFEITA",
                timestamp = System.currentTimeMillis(),
                user = user,
                detailsJson = "Conciliação desfeita. Motivo: $reason"
            )
        )
    }

    suspend fun ignoreTransaction(bankTxId: String, reason: String = "", user: String = "Proprietário") {
        dao.updateBankTransactionStatus(
            id = bankTxId,
            status = BankTransactionStatus.IGNORADA,
            reconciliationId = null
        )
    }

    suspend fun restoreTransaction(bankTxId: String) {
        dao.updateBankTransactionStatus(
            id = bankTxId,
            status = BankTransactionStatus.PENDENTE,
            reconciliationId = null
        )
    }

    // --- ETAPA 3.2: RENTABILIDADE E CUSTOS ---

    fun observeCostAllocationsByCompetence(competence: String): Flow<List<CostAllocationEntity>> =
        dao.observeCostAllocationsByCompetence(competence)

    fun observeCostAllocationsByContract(contractId: String): Flow<List<CostAllocationEntity>> =
        dao.observeCostAllocationsByContract(contractId)

    fun observeCostAllocationsByClient(clientId: String): Flow<List<CostAllocationEntity>> =
        dao.observeCostAllocationsByClient(clientId)

    fun observeCostAllocationsByExpense(expenseId: String): Flow<List<CostAllocationEntity>> =
        dao.observeCostAllocationsByExpense(expenseId)

    fun observeAllCostAllocations(): Flow<List<CostAllocationEntity>> =
        dao.observeAllCostAllocations()

    suspend fun saveCostAllocation(allocation: CostAllocationEntity) {
        database.withTransaction {
            // 1. Verificar se a competência está fechada em definitivo
            val closure = dao.getClosureByCompetence(allocation.competence)
            if (closure != null && closure.status == "DEFINITIVO") {
                throw IllegalStateException("Não é permitido alocar custos em competência com fechamento DEFINITIVO sem reabertura prévia.")
            }

            // 2. Validar limite em relação à despesa original
            val expense = dao.getExpenseById(allocation.expenseId)
                ?: throw IllegalArgumentException("Despesa original não encontrada.")

            val existing = dao.getCostAllocationsByExpense(allocation.expenseId)
            val valid = ProfitabilityEngine.validateAllocationLimit(
                expense = expense,
                existingAllocations = existing,
                newAllocationCents = allocation.allocatedAmountCents,
                excludeAllocationId = allocation.id
            )
            if (!valid) {
                val available = ProfitabilityEngine.getAvailableAmountForAllocation(expense, existing)
                val availableFormatted = CurrencyUtils.formatCents(available)
                throw IllegalArgumentException("O valor alocado excede o saldo disponível da despesa (Disponível: $availableFormatted).")
            }

            dao.insertCostAllocation(allocation)
        }
    }

    suspend fun deleteCostAllocation(id: String) {
        val allocation = dao.getCostAllocationById(id) ?: return
        val closure = dao.getClosureByCompetence(allocation.competence)
        if (closure != null && closure.status == "DEFINITIVO") {
            throw IllegalStateException("Não é permitido excluir alocação de custos em competência encerrada definitivamente.")
        }
        dao.deleteCostAllocationById(id)
    }

    // --- POLÍTICAS DE RATEIO ---
    fun observeApportionmentPolicies(): Flow<List<ApportionmentPolicyEntity>> =
        dao.observeAllApportionmentPolicies()

    suspend fun saveApportionmentPolicy(policy: ApportionmentPolicyEntity) {
        dao.insertApportionmentPolicy(policy)
    }

    suspend fun deleteApportionmentPolicy(id: String) {
        dao.deleteApportionmentPolicyById(id)
    }

    suspend fun applyApportionment(
        policy: ApportionmentPolicyEntity,
        expenseId: String,
        competence: String,
        previewItems: List<ApportionmentPreviewItem>,
        notes: String = ""
    ) {
        database.withTransaction {
            val closure = dao.getClosureByCompetence(competence)
            if (closure != null && closure.status == "DEFINITIVO") {
                throw IllegalStateException("Não é permitido executar rateio sobre período com fechamento DEFINITIVO.")
            }

            val expense = dao.getExpenseById(expenseId)
                ?: throw IllegalArgumentException("Despesa não encontrada.")

            val existing = dao.getCostAllocationsByExpense(expenseId)
            val totalToAllocate = previewItems.sumOf { it.allocatedCents }
            val available = ProfitabilityEngine.getAvailableAmountForAllocation(expense, existing)

            if (totalToAllocate > available) {
                throw IllegalArgumentException("O valor total do rateio (${CurrencyUtils.formatCents(totalToAllocate)}) excede o disponível na despesa (${CurrencyUtils.formatCents(available)}).")
            }

            previewItems.forEach { item ->
                if (item.allocatedCents > 0) {
                    dao.insertCostAllocation(
                        CostAllocationEntity(
                            id = UUID.randomUUID().toString(),
                            expenseId = expenseId,
                            allocationType = CostAllocationType.INDIRETO_RATEADO,
                            contractId = item.contractId,
                            clientId = item.clientId,
                            serviceId = item.serviceId,
                            competence = competence,
                            allocatedAmountCents = item.allocatedCents,
                            allocationPercentage = item.percentage,
                            notes = "Rateio (${policy.name}): ${notes.ifBlank { "Rateio automático" }}",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    // --- CONTROLE DE TEMPO ---
    fun observeTimeTrackingsByCompetence(competence: String): Flow<List<TimeTrackingEntity>> =
        dao.observeTimeTrackingsByCompetence(competence)

    fun observeTimeTrackingsByClient(clientId: String): Flow<List<TimeTrackingEntity>> =
        dao.observeTimeTrackingsByClient(clientId)

    fun observeAllTimeTrackings(): Flow<List<TimeTrackingEntity>> =
        dao.observeAllTimeTrackings()

    suspend fun saveTimeTracking(entry: TimeTrackingEntity) {
        dao.insertTimeTracking(entry)
    }

    suspend fun deleteTimeTracking(id: String) {
        dao.deleteTimeTrackingById(id)
    }

    // --- SIMULADOR DE PRECIFICAÇÃO ---
    fun observePricingSimulationsByService(serviceId: String): Flow<List<PricingSimulationScenarioEntity>> =
        dao.observePricingSimulationsByService(serviceId)

    fun observeAllPricingSimulations(): Flow<List<PricingSimulationScenarioEntity>> =
        dao.observeAllPricingSimulations()

    suspend fun savePricingSimulation(simulation: PricingSimulationScenarioEntity) {
        dao.insertPricingSimulation(simulation)
    }

    suspend fun deletePricingSimulation(id: String) {
        dao.deletePricingSimulationById(id)
    }
}

