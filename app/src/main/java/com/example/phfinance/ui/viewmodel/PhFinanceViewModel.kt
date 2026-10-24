package com.example.phfinance.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phfinance.data.local.AppDatabase
import com.example.phfinance.data.local.BusinessSettingsEntity
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ContractEntity
import com.example.phfinance.data.local.ExpenseCategoryEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.FinancialAccountEntity
import com.example.phfinance.data.local.GoalEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.local.ServiceEntity
import com.example.phfinance.data.model.ClientType
import com.example.phfinance.data.model.ContractStatus
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.data.repository.PhFinanceRepository
import com.example.phfinance.util.DashboardMetrics
import com.example.phfinance.util.FinancialEngine
import com.example.phfinance.util.PhInsight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class Tuple5<A, B, C, D, E>(
    val acc: A,
    val rec: B,
    val pay: C,
    val exp: D,
    val con: E
)

class PhFinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PhFinanceRepository

    val selectedCompetence = MutableStateFlow(DateUtils.getCurrentCompetence())
    val isDarkMode = MutableStateFlow(true)
    val searchQuery = MutableStateFlow("")

    val clients: StateFlow<List<ClientEntity>>
    val allClients: StateFlow<List<ClientEntity>>
    val services: StateFlow<List<ServiceEntity>>
    val activeServices: StateFlow<List<ServiceEntity>>
    val contracts: StateFlow<List<ContractEntity>>
    val receivables: StateFlow<List<ReceivableEntity>>
    val payments: StateFlow<List<PaymentEntity>>
    val expenses: StateFlow<List<ExpenseEntity>>
    val accounts: StateFlow<List<FinancialAccountEntity>>
    val categories: StateFlow<List<ExpenseCategoryEntity>>
    val goal: StateFlow<GoalEntity?>
    val settings: StateFlow<BusinessSettingsEntity?>

    val dashboardMetrics: StateFlow<DashboardMetrics>
    val insights: StateFlow<List<PhInsight>>
    val monthlyDRE: StateFlow<com.example.phfinance.util.MonthlyDREResult>
    val currentClosure: StateFlow<com.example.phfinance.data.local.MonthlyClosureEntity?>
    val messageTemplates: StateFlow<List<com.example.phfinance.data.local.MessageTemplateEntity>>

    val growthParams = MutableStateFlow(com.example.phfinance.util.GrowthSimulationParams())
    val growthProjections: StateFlow<List<com.example.phfinance.util.SimulationMonthProjection>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = PhFinanceRepository(db.phFinanceDao())

        // Run automatic billing reconciliation on startup
        viewModelScope.launch {
            repository.generateMonthlyRecurrentDues()
        }

        clients = repository.observeActiveClients().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allClients = repository.observeAllClients().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        services = repository.observeAllServices().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        activeServices = repository.observeActiveServices().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        contracts = repository.observeAllContracts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        receivables = repository.observeAllReceivables().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        payments = repository.observeAllPayments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        expenses = repository.observeAllExpenses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        accounts = repository.observeAllAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        categories = repository.observeExpenseCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        goal = repository.observeGoal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        settings = repository.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        messageTemplates = repository.observeAllMessageTemplates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val coreFlow = combine(accounts, receivables, payments, expenses, contracts) { acc, rec, pay, exp, con ->
            Tuple5(acc, rec, pay, exp, con)
        }

        dashboardMetrics = combine(coreFlow, allClients, goal, selectedCompetence) { tuple, cliList, g, comp ->
            FinancialEngine.calculateDashboardMetrics(
                accounts = tuple.acc,
                receivables = tuple.rec,
                payments = tuple.pay,
                expenses = tuple.exp,
                contracts = tuple.con,
                clients = cliList,
                goal = g,
                selectedCompetence = comp
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DashboardMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000000L, 0.0)
        )

        insights = combine(dashboardMetrics, receivables, expenses, allClients) { m, recList, expList, cliList ->
            FinancialEngine.generateInsights(m, recList, expList, cliList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        monthlyDRE = combine(receivables, payments, expenses, selectedCompetence) { rec, pay, exp, comp ->
            FinancialEngine.calculateMonthlyDRE(rec, pay, exp, comp)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.example.phfinance.util.MonthlyDREResult(
                competence = DateUtils.getCurrentCompetence(),
                accrualGrossRevenueCents = 0L,
                accrualDiscountsCents = 0L,
                accrualNetRevenueCents = 0L,
                accrualExpensesCents = 0L,
                accrualOperatingResultCents = 0L,
                cashGrossRevenueCents = 0L,
                cashPaymentFeesCents = 0L,
                cashNetRevenueCents = 0L,
                cashOperationalExpensesCents = 0L,
                cashOperatingResultCents = 0L,
                cashOwnerWithdrawalsCents = 0L,
                cashNetPeriodVariationCents = 0L,
                serviceRevenueBreakdown = emptyList(),
                expenseCategoryBreakdown = emptyList()
            )
        )

        currentClosure = selectedCompetence.combine(receivables) { comp, _ ->
            repository.getClosureByCompetence(comp)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        growthProjections = growthParams.combine(dashboardMetrics) { params, metrics ->
            val updatedParams = params.copy(
                currentActiveClients = metrics.activeClientsCount.takeIf { it > 0 } ?: params.currentActiveClients,
                ticketMedioCents = metrics.ticketMedioCents.takeIf { it > 0 } ?: params.ticketMedioCents
            )
            com.example.phfinance.util.GrowthSimulatorEngine.calculateProjections(updatedParams)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val sixMonthsHistory = combine(receivables, payments, expenses, selectedCompetence) { rec, pay, exp, comp ->
        FinancialEngine.calculateLast6MonthsHistory(rec, pay, exp, comp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- ACTIONS ---


    fun setCompetence(competence: String) {
        selectedCompetence.value = competence
    }

    fun toggleDarkMode() {
        isDarkMode.value = !isDarkMode.value
    }

    // Client CRUD
    fun saveClient(
        id: String?,
        fullName: String,
        nickname: String,
        phone: String,
        whatsapp: String,
        email: String,
        agreedValueCents: Long,
        dueDay: Int,
        notes: String
    ) {
        viewModelScope.launch {
            val client = ClientEntity(
                id = id.takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
                fullName = fullName,
                nickname = nickname.ifBlank { fullName },
                phone = phone,
                whatsapp = whatsapp.ifBlank { phone },
                email = email,
                agreedValueCents = agreedValueCents,
                dueDay = dueDay,
                notes = notes,
                clientType = ClientType.ATIVO
            )
            repository.saveClient(client)
        }
    }

    fun archiveClient(clientId: String) {
        viewModelScope.launch {
            repository.archiveClient(clientId)
        }
    }

    fun unarchiveClient(clientId: String) {
        viewModelScope.launch {
            repository.unarchiveClient(clientId)
        }
    }

    // Service CRUD
    fun saveService(
        id: String?,
        name: String,
        description: String,
        priceCents: Long,
        sessionCount: Int
    ) {
        viewModelScope.launch {
            val service = ServiceEntity(
                id = id.takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
                name = name,
                description = description,
                defaultPriceCents = priceCents,
                sessionCount = sessionCount
            )
            repository.saveService(service)
        }
    }

    // Contract Creation
    fun createContract(
        clientId: String,
        serviceId: String,
        contractedValueCents: Long,
        dueDay: Int,
        sessionCount: Int = 0
    ) {
        viewModelScope.launch {
            val contract = ContractEntity(
                id = UUID.randomUUID().toString(),
                clientId = clientId,
                serviceId = serviceId,
                startDate = System.currentTimeMillis(),
                contractedValueCents = contractedValueCents,
                dueDay = dueDay
            )
            repository.createContractWithSession(contract, sessionCount)
        }
    }

    // Receivables & Recurring
    fun generateMonthlyDues() {
        viewModelScope.launch {
            repository.generateMonthlyRecurrentDues()
        }
    }

    fun addCustomReceivable(
        clientId: String,
        description: String,
        valueCents: Long,
        dueDate: Long
    ) {
        viewModelScope.launch {
            repository.generateCustomReceivable(
                clientId = clientId,
                description = description,
                valueCents = valueCents,
                discountCents = 0L,
                dueDate = dueDate,
                competence = selectedCompetence.value
            )
        }
    }

    // Payment Settlement
    fun registerPayment(
        receivableId: String,
        amountReceivedCents: Long,
        feeCents: Long,
        destinationAccountId: String,
        paymentMethod: PaymentMethod,
        transactionRef: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            repository.registerPayment(
                receivableId = receivableId,
                amountReceivedCents = amountReceivedCents,
                feeCents = feeCents,
                destinationAccountId = destinationAccountId,
                paymentMethod = paymentMethod,
                transactionRef = transactionRef,
                notes = notes
            )
        }
    }

    // Expense
    fun addExpense(
        description: String,
        category: String,
        supplier: String,
        valueCents: Long,
        dueDate: Long,
        isOwnerPersonalWithdrawal: Boolean
    ) {
        viewModelScope.launch {
            val expense = ExpenseEntity(
                id = UUID.randomUUID().toString(),
                description = description,
                category = category,
                supplier = supplier,
                valueCents = valueCents,
                competence = selectedCompetence.value,
                dueDate = dueDate,
                status = ExpenseStatus.PENDENTE,
                isOwnerPersonalWithdrawal = isOwnerPersonalWithdrawal
            )
            repository.saveExpense(expense)
        }
    }

    fun payExpense(
        expenseId: String,
        accountId: String,
        paymentMethod: PaymentMethod
    ) {
        viewModelScope.launch {
            repository.payExpense(
                expenseId = expenseId,
                accountId = accountId,
                paymentMethod = paymentMethod
            )
        }
    }

    // Accounts & Internal Transfers
    fun transferAccounts(
        sourceAccountId: String,
        destinationAccountId: String,
        amountCents: Long,
        description: String
    ) {
        viewModelScope.launch {
            repository.transferBetweenAccounts(
                sourceAccountId = sourceAccountId,
                destinationAccountId = destinationAccountId,
                amountCents = amountCents,
                description = description
            )
        }
    }

    fun saveGoal(monthlyRevenueGoalCents: Long, activeClientsGoal: Int) {
        viewModelScope.launch {
            val current = goal.value ?: GoalEntity()
            repository.saveGoal(
                current.copy(
                    monthlyRevenueGoalCents = monthlyRevenueGoalCents,
                    activeClientsGoal = activeClientsGoal
                )
            )
        }
    }

    fun saveSettings(businessName: String, tradeName: String, pixKey: String) {
        viewModelScope.launch {
            val current = settings.value ?: BusinessSettingsEntity()
            repository.saveSettings(
                current.copy(
                    businessName = businessName,
                    tradeName = tradeName,
                    pixKey = pixKey,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun createBackup(context: android.content.Context, password: String, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                com.example.phfinance.util.BackupManager.createEncryptedBackup(context, db.phFinanceDao(), password)
                onResult("Backup criptografado gerado e compartilhado com sucesso!")
            } catch (e: Exception) {
                onResult("Erro ao criar backup: ${e.localizedMessage}")
            }
        }
    }

    fun restoreBackup(context: android.content.Context, backupContent: String, password: String, onResult: (com.example.phfinance.util.BackupImportResult) -> Unit) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(context)
            val res = com.example.phfinance.util.BackupManager.restoreEncryptedBackup(backupContent, password, db.phFinanceDao())
            onResult(res)
        }
    }

    // --- ETAPA 2 ACTIONS ---

    fun saveMonthlyClosure(status: String, notes: String = "") {
        viewModelScope.launch {
            val comp = selectedCompetence.value
            val dre = monthlyDRE.value
            repository.saveMonthlyClosure(
                competence = comp,
                status = status,
                accrualRevenueCents = dre.accrualNetRevenueCents,
                accrualExpenseCents = dre.accrualExpensesCents,
                cashRevenueCents = dre.cashNetRevenueCents,
                cashExpenseCents = dre.cashOperationalExpensesCents,
                notes = notes
            )
        }
    }

    fun reopenMonthlyClosure(reason: String) {
        viewModelScope.launch {
            repository.reopenMonthlyClosure(selectedCompetence.value, reason)
        }
    }

    fun saveMessageTemplate(template: com.example.phfinance.data.local.MessageTemplateEntity) {
        viewModelScope.launch {
            repository.saveMessageTemplate(template)
        }
    }

    fun cancelReceivable(receivableId: String) {
        viewModelScope.launch {
            repository.cancelReceivable(receivableId)
        }
    }

    fun updateGrowthParams(newParams: com.example.phfinance.util.GrowthSimulationParams) {
        growthParams.value = newParams
    }

    fun sendWhatsappMessage(
        context: android.content.Context,
        client: ClientEntity,
        receivable: ReceivableEntity?,
        templateType: com.example.phfinance.data.model.WhatsappTemplateType
    ) {
        viewModelScope.launch {
            val templates = messageTemplates.value
            val customTemplate = templates.find { it.templateType == templateType }
            val rawText = customTemplate?.text ?: templateType.defaultText
            val pix = settings.value?.pixKey ?: "Pix PH Personal"

            val formattedText = rawText
                .replace("{nome}", client.fullName)
                .replace("{valor}", receivable?.let { CurrencyUtils.formatCents(it.netValueCents - it.receivedValueCents) } ?: "R$ 0,00")
                .replace("{vencimento}", receivable?.let { DateUtils.formatDate(it.dueDate) } ?: "-")
                .replace("{competencia}", receivable?.competence ?: selectedCompetence.value)
                .replace("{pix}", pix)
                .replace("{servico}", "Consultoria Personalizada")

            val rawPhone = client.whatsapp.ifBlank { client.phone }.replace(Regex("[^0-9]"), "")
            val cleanPhone = if (rawPhone.length in 10..11) "55$rawPhone" else rawPhone

            try {
                val encodedText = java.net.URLEncoder.encode(formattedText, "UTF-8")
                val uri = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)

                // Log notification
                repository.logNotification(
                    com.example.phfinance.data.local.NotificationLogEntity(
                        id = UUID.randomUUID().toString(),
                        clientId = client.id,
                        receivableId = receivable?.id ?: "",
                        templateType = templateType,
                        messageText = formattedText,
                        sentAt = System.currentTimeMillis(),
                        isConfirmedSent = true
                    )
                )
            } catch (e: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(context, "Aplicativo WhatsApp não instalado.", android.widget.Toast.LENGTH_SHORT).show()
                repository.logNotification(
                    com.example.phfinance.data.local.NotificationLogEntity(
                        id = UUID.randomUUID().toString(),
                        clientId = client.id,
                        receivableId = receivable?.id ?: "",
                        templateType = templateType,
                        messageText = "FALHA: WhatsApp não instalado",
                        sentAt = System.currentTimeMillis(),
                        isConfirmedSent = false
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

