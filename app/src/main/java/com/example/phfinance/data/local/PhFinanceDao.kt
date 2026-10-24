package com.example.phfinance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.phfinance.data.model.AccountType
import com.example.phfinance.data.model.ClientType
import com.example.phfinance.data.model.ContractStatus
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.MovementType
import com.example.phfinance.data.model.ReceivableStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PhFinanceDao {

    // --- CLIENTS ---
    @Query("SELECT * FROM clients WHERE isArchived = 0 ORDER BY fullName ASC")
    fun observeActiveClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients ORDER BY fullName ASC")
    fun observeAllClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :clientId")
    suspend fun getClientById(clientId: String): ClientEntity?

    @Query("SELECT * FROM clients WHERE id = :clientId")
    fun observeClientById(clientId: String): Flow<ClientEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity)

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Query("UPDATE clients SET isArchived = 1, clientType = 'INATIVO', updatedAt = :updatedAt WHERE id = :clientId")
    suspend fun archiveClient(clientId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE clients SET isArchived = 0, clientType = 'ATIVO', updatedAt = :updatedAt WHERE id = :clientId")
    suspend fun unarchiveClient(clientId: String, updatedAt: Long = System.currentTimeMillis())

    // --- SERVICES ---
    @Query("SELECT * FROM services ORDER BY name ASC")
    fun observeAllServices(): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE isActive = 1 ORDER BY name ASC")
    fun observeActiveServices(): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE id = :serviceId")
    suspend fun getServiceById(serviceId: String): ServiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: ServiceEntity)

    @Update
    suspend fun updateService(service: ServiceEntity)

    // --- CONTRACTS ---
    @Query("SELECT * FROM contracts ORDER BY createdAt DESC")
    fun observeAllContracts(): Flow<List<ContractEntity>>

    @Query("SELECT * FROM contracts WHERE clientId = :clientId ORDER BY createdAt DESC")
    fun observeContractsByClient(clientId: String): Flow<List<ContractEntity>>

    @Query("SELECT * FROM contracts WHERE status = 'ATIVO'")
    fun observeActiveContracts(): Flow<List<ContractEntity>>

    @Query("SELECT * FROM contracts WHERE id = :contractId")
    suspend fun getContractById(contractId: String): ContractEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContract(contract: ContractEntity)

    @Update
    suspend fun updateContract(contract: ContractEntity)

    @Query("UPDATE contracts SET status = :status, updatedAt = :updatedAt WHERE id = :contractId")
    suspend fun updateContractStatus(contractId: String, status: ContractStatus, updatedAt: Long = System.currentTimeMillis())

    // --- CONTRACT SESSIONS ---
    @Query("SELECT * FROM contract_sessions WHERE clientId = :clientId")
    fun observeSessionsByClient(clientId: String): Flow<List<ContractSessionEntity>>

    @Query("SELECT * FROM contract_sessions WHERE contractId = :contractId")
    suspend fun getSessionByContract(contractId: String): ContractSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContractSession(session: ContractSessionEntity)

    @Update
    suspend fun updateContractSession(session: ContractSessionEntity)

    // --- RECEIVABLES ---
    @Query("SELECT * FROM receivables ORDER BY dueDate ASC")
    fun observeAllReceivables(): Flow<List<ReceivableEntity>>

    @Query("SELECT * FROM receivables WHERE clientId = :clientId ORDER BY dueDate DESC")
    fun observeReceivablesByClient(clientId: String): Flow<List<ReceivableEntity>>

    @Query("SELECT * FROM receivables WHERE competence = :competence ORDER BY dueDate ASC")
    fun observeReceivablesByCompetence(competence: String): Flow<List<ReceivableEntity>>

    @Query("SELECT * FROM receivables WHERE contractId = :contractId AND competence = :competence LIMIT 1")
    suspend fun getReceivableByContractAndCompetence(contractId: String, competence: String): ReceivableEntity?

    @Query("SELECT * FROM receivables WHERE id = :id")
    suspend fun getReceivableById(id: String): ReceivableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceivable(receivable: ReceivableEntity)

    @Update
    suspend fun updateReceivable(receivable: ReceivableEntity)

    // --- PAYMENTS ---
    @Query("SELECT * FROM payments ORDER BY paymentDate DESC")
    fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE receivableId = :receivableId ORDER BY paymentDate DESC")
    fun observePaymentsByReceivable(receivableId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE clientId = :clientId ORDER BY paymentDate DESC")
    fun observePaymentsByClient(clientId: String): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)

    // --- EXPENSES ---
    @Query("SELECT * FROM expenses ORDER BY dueDate ASC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE competence = :competence ORDER BY dueDate ASC")
    fun observeExpensesByCompetence(competence: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    // --- EXPENSE CATEGORIES ---
    @Query("SELECT * FROM expense_categories ORDER BY name ASC")
    fun observeExpenseCategories(): Flow<List<ExpenseCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseCategory(category: ExpenseCategoryEntity)

    // --- FINANCIAL ACCOUNTS ---
    @Query("SELECT * FROM financial_accounts ORDER BY name ASC")
    fun observeAllAccounts(): Flow<List<FinancialAccountEntity>>

    @Query("SELECT * FROM financial_accounts WHERE id = :id")
    suspend fun getAccountById(id: String): FinancialAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: FinancialAccountEntity)

    @Update
    suspend fun updateAccount(account: FinancialAccountEntity)

    @Query("UPDATE financial_accounts SET currentBalanceCents = currentBalanceCents + :deltaCents, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun updateAccountBalance(accountId: String, deltaCents: Long, updatedAt: Long = System.currentTimeMillis())

    // --- ACCOUNT MOVEMENTS ---
    @Query("SELECT * FROM account_movements WHERE accountId = :accountId ORDER BY date DESC")
    fun observeMovementsByAccount(accountId: String): Flow<List<AccountMovementEntity>>

    @Query("SELECT * FROM account_movements ORDER BY date DESC")
    fun observeAllMovements(): Flow<List<AccountMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: AccountMovementEntity)

    // --- ACCOUNT TRANSFERS ---
    @Query("SELECT * FROM account_transfers ORDER BY date DESC")
    fun observeAllTransfers(): Flow<List<AccountTransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: AccountTransferEntity)

    // --- GOALS ---
    @Query("SELECT * FROM goals WHERE id = 'default_goal'")
    fun observeGoal(): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE id = 'default_goal'")
    suspend fun getGoal(): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGoal(goal: GoalEntity)

    // --- BUSINESS SETTINGS ---
    @Query("SELECT * FROM business_settings WHERE id = 'default_settings'")
    fun observeSettings(): Flow<BusinessSettingsEntity?>

    @Query("SELECT * FROM business_settings WHERE id = 'default_settings'")
    suspend fun getSettings(): BusinessSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: BusinessSettingsEntity)

    // --- NOTIFICATION LOGS ---
    @Query("SELECT * FROM notification_logs ORDER BY sentAt DESC")
    fun observeNotificationLogs(): Flow<List<NotificationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationLog(log: NotificationLogEntity)

    // --- MONTHLY CLOSURES ---
    @Query("SELECT * FROM monthly_closures WHERE competence = :competence LIMIT 1")
    fun observeClosureByCompetence(competence: String): Flow<MonthlyClosureEntity?>

    @Query("SELECT * FROM monthly_closures WHERE competence = :competence LIMIT 1")
    suspend fun getClosureByCompetence(competence: String): MonthlyClosureEntity?

    @Query("SELECT * FROM monthly_closures ORDER BY competence DESC")
    fun observeAllClosures(): Flow<List<MonthlyClosureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateClosure(closure: MonthlyClosureEntity)

    // --- CLOSURE HISTORIES ---
    @Query("SELECT * FROM closure_histories ORDER BY timestamp DESC")
    fun observeAllClosureHistories(): Flow<List<ClosureHistoryEntity>>

    @Query("SELECT * FROM closure_histories WHERE closureId = :closureId ORDER BY timestamp DESC")
    fun observeClosureHistory(closureId: String): Flow<List<ClosureHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClosureHistory(history: ClosureHistoryEntity)

    // --- CONTRACT SESSIONS ---
    @Query("SELECT * FROM contract_sessions")
    fun observeAllContractSessions(): Flow<List<ContractSessionEntity>>

    // --- MESSAGE TEMPLATES ---
    @Query("SELECT * FROM message_templates ORDER BY title ASC")
    fun observeAllTemplates(): Flow<List<MessageTemplateEntity>>

    @Query("SELECT * FROM message_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: String): MessageTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageTemplate(template: MessageTemplateEntity)

    @Query("UPDATE receivables SET status = 'CANCELADA', updatedAt = :updatedAt WHERE id = :receivableId")
    suspend fun cancelReceivable(receivableId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM clients")
    suspend fun clearAllClients()

    @Query("DELETE FROM receivables")
    suspend fun clearAllReceivables()

    @Query("DELETE FROM payments")
    suspend fun clearAllPayments()

    @Query("DELETE FROM expenses")
    suspend fun clearAllExpenses()
}

