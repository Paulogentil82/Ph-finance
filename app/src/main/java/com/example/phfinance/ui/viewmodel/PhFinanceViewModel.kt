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

import com.example.phfinance.data.local.BankImportEntity
import com.example.phfinance.data.local.BankTransactionEntity
import com.example.phfinance.data.local.ReconciliationAuditEntity
import com.example.phfinance.data.local.ReconciliationEntity
import com.example.phfinance.data.local.CostAllocationEntity
import com.example.phfinance.data.local.ApportionmentPolicyEntity
import com.example.phfinance.data.local.TimeTrackingEntity
import com.example.phfinance.data.local.PricingSimulationScenarioEntity
import com.example.phfinance.data.model.ApportionmentMethod
import com.example.phfinance.data.model.ApportionmentPreviewItem
import com.example.phfinance.data.model.CostAllocationType
import com.example.phfinance.data.model.PricingScenarioResult
import com.example.phfinance.data.model.ProfitabilityMetricResult
import com.example.phfinance.data.model.ServiceProfitabilitySummary
import com.example.phfinance.data.model.StudentProfitabilitySummary
import com.example.phfinance.util.ProfitabilityEngine
import com.example.phfinance.util.PricingSimulatorEngine
import com.example.phfinance.util.ReportExporter
import android.content.Context
import com.example.phfinance.data.model.BankTransactionStatus
import com.example.phfinance.data.model.CsvConfig
import com.example.phfinance.data.model.ReconciliationMatchSuggestion
import com.example.phfinance.util.reconciliation.CsvParser
import com.example.phfinance.util.reconciliation.OfxParser
import java.io.InputStream

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

    // Reconciliation state flows
    val bankImports: StateFlow<List<BankImportEntity>>
    val bankTransactions: StateFlow<List<BankTransactionEntity>>
    val reconciliations: StateFlow<List<ReconciliationEntity>>
    val reconciliationAudits: StateFlow<List<ReconciliationAuditEntity>>

    val selectedReconciliationAccountId = MutableStateFlow<String?>(null)
    val reconciliationFilterStatus = MutableStateFlow("TODAS")
    val selectedTransactionForMatch = MutableStateFlow<BankTransactionEntity?>(null)
    val currentMatchSuggestion = MutableStateFlow<ReconciliationMatchSuggestion?>(null)
    val isReconciling = MutableStateFlow(false)
    val reconciliationFeedback = MutableStateFlow<String?>(null)

    // Profitability state flows (Etapa 3.2)
    val costAllocations: StateFlow<List<CostAllocationEntity>>
    val apportionmentPolicies: StateFlow<List<ApportionmentPolicyEntity>>
    val timeTrackings: StateFlow<List<TimeTrackingEntity>>
    val pricingSimulations: StateFlow<List<PricingSimulationScenarioEntity>>
    val hourlyEconomicRateCents = MutableStateFlow(5000L)
    val selectedProfitabilityServiceId = MutableStateFlow<String?>(null)
    val selectedProfitabilityClientId = MutableStateFlow<String?>(null)
    val profitabilityMetrics: StateFlow<ProfitabilityMetricResult>
    val serviceProfitabilityList: StateFlow<List<ServiceProfitabilitySummary>>
    val studentProfitabilityList: StateFlow<List<StudentProfitabilitySummary>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = PhFinanceRepository(db.phFinanceDao(), db)

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
        bankImports = repository.observeAllBankImports().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        bankTransactions = repository.observeAllBankTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        reconciliations = repository.observeAllReconciliations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        reconciliationAudits = repository.observeAllReconciliationAudits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        costAllocations = repository.observeAllCostAllocations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        apportionmentPolicies = repository.observeApportionmentPolicies().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        timeTrackings = repository.observeAllTimeTrackings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        pricingSimulations = repository.observeAllPricingSimulations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

        profitabilityMetrics = combine(receivables, payments, expenses) { rec, pay, exp ->
            Triple(rec, pay, exp)
        }.combine(combine(costAllocations, timeTrackings, hourlyEconomicRateCents) { alloc, time, rate ->
            Triple(alloc, time, rate)
        }.combine(selectedCompetence) { t2Sub, comp ->
            Pair(t2Sub, comp)
        }) { t1, t2 ->
            ProfitabilityEngine.calculateOverviewMetrics(
                receivables = t1.first,
                payments = t1.second,
                expenses = t1.third,
                costAllocations = t2.first.first,
                timeTrackings = t2.first.second,
                clients = allClients.value,
                contracts = contracts.value,
                hourlyEconomicRateCents = t2.first.third,
                competenceFilter = t2.second
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ProfitabilityMetricResult()
        )

        serviceProfitabilityList = combine(services, contracts, receivables) { srv, con, rec ->
            Triple(srv, con, rec)
        }.combine(combine(payments, costAllocations, timeTrackings) { pay, alloc, time ->
            Triple(pay, alloc, time)
        }.combine(combine(hourlyEconomicRateCents, selectedCompetence) { rate, comp ->
            Pair(rate, comp)
        }) { t2Sub, t3Sub ->
            Pair(t2Sub, t3Sub)
        }) { t1, t2 ->
            ProfitabilityEngine.calculateServiceSummaries(
                services = t1.first,
                contracts = t1.second,
                receivables = t1.third,
                payments = t2.first.first,
                costAllocations = t2.first.second,
                timeTrackings = t2.first.third,
                hourlyEconomicRateCents = t2.second.first,
                competenceFilter = t2.second.second
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        studentProfitabilityList = combine(allClients, contracts, receivables) { cli, con, rec ->
            Triple(cli, con, rec)
        }.combine(combine(payments, costAllocations, timeTrackings) { pay, alloc, time ->
            Triple(pay, alloc, time)
        }.combine(combine(hourlyEconomicRateCents, selectedCompetence) { rate, comp ->
            Pair(rate, comp)
        }) { t2Sub, t3Sub ->
            Pair(t2Sub, t3Sub)
        }) { t1, t2 ->
            ProfitabilityEngine.calculateStudentSummaries(
                clients = t1.first,
                contracts = t1.second,
                receivables = t1.third,
                payments = t2.first.first,
                costAllocations = t2.first.second,
                timeTrackings = t2.first.third,
                hourlyEconomicRateCents = t2.second.first,
                competenceFilter = t2.second.second
            )
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
            val res = com.example.phfinance.util.BackupManager.restoreEncryptedBackup(backupContent, password, db.phFinanceDao(), db)
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

    // --- BANK RECONCILIATION ACTIONS ---

    fun setReconciliationAccount(accountId: String?) {
        selectedReconciliationAccountId.value = accountId
    }

    fun setReconciliationFilter(filter: String) {
        reconciliationFilterStatus.value = filter
    }

    fun importOfxStream(
        inputStream: InputStream,
        accountId: String,
        fileName: String,
        fileSize: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                isReconciling.value = true
                val parsed = OfxParser.parse(inputStream, accountId, fileName, fileSize)
                val (_, count) = repository.importBankStatement(parsed)
                isReconciling.value = false
                val msg = "Extrato OFX importado: $count transações novas processadas."
                reconciliationFeedback.value = msg
                onResult(true, msg)
            } catch (e: Exception) {
                isReconciling.value = false
                val err = "Falha ao importar OFX: ${e.localizedMessage ?: "Formato inválido"}"
                reconciliationFeedback.value = err
                onResult(false, err)
            }
        }
    }

    fun importCsvStream(
        inputStream: InputStream,
        accountId: String,
        fileName: String,
        fileSize: Long,
        config: CsvConfig?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                isReconciling.value = true
                val parsed = CsvParser.parse(inputStream, accountId, fileName, fileSize, config)
                val (_, count) = repository.importBankStatement(parsed)
                isReconciling.value = false
                val msg = "Extrato CSV importado: $count transações novas processadas."
                reconciliationFeedback.value = msg
                onResult(true, msg)
            } catch (e: Exception) {
                isReconciling.value = false
                val err = "Falha ao importar CSV: ${e.localizedMessage ?: "Erro na estrutura do arquivo"}"
                reconciliationFeedback.value = err
                onResult(false, err)
            }
        }
    }

    fun loadMatchSuggestion(tx: BankTransactionEntity) {
        selectedTransactionForMatch.value = tx
        viewModelScope.launch {
            val suggestion = repository.getMatchSuggestion(tx.id)
            currentMatchSuggestion.value = suggestion
        }
    }

    fun clearMatchSuggestion() {
        selectedTransactionForMatch.value = null
        currentMatchSuggestion.value = null
    }

    fun confirmFluxoA(
        txId: String,
        paymentId: String,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.reconcileFluxoA(txId, paymentId)
                clearMatchSuggestion()
                val msg = "Fluxo A: Transação vinculada com sucesso ao pagamento existente."
                reconciliationFeedback.value = msg
                onDone(true, msg)
            } catch (e: Exception) {
                val err = "Falha na conciliação (Fluxo A): ${e.localizedMessage}"
                reconciliationFeedback.value = err
                onDone(false, err)
            }
        }
    }

    fun confirmFluxoB(
        txId: String,
        receivableId: String,
        feeCents: Long,
        method: PaymentMethod = PaymentMethod.PIX,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.reconcileFluxoB(txId, receivableId, feeCents, method)
                clearMatchSuggestion()
                val msg = "Fluxo B: Recebimento baixado e creditado na conta com sucesso."
                reconciliationFeedback.value = msg
                onDone(true, msg)
            } catch (e: Exception) {
                val err = "Falha na baixa da cobrança (Fluxo B): ${e.localizedMessage}"
                reconciliationFeedback.value = err
                onDone(false, err)
            }
        }
    }

    fun confirmExpense(
        txId: String,
        expenseId: String,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.reconcileExpense(txId, expenseId)
                clearMatchSuggestion()
                val msg = "Despesa vinculada e baixada com sucesso."
                reconciliationFeedback.value = msg
                onDone(true, msg)
            } catch (e: Exception) {
                val err = "Falha ao vincular despesa: ${e.localizedMessage}"
                reconciliationFeedback.value = err
                onDone(false, err)
            }
        }
    }

    fun undoReconciliation(
        reconciliationId: String,
        reason: String,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.undoReconciliation(reconciliationId, reason)
                val msg = "Conciliação desfeita com registro de auditoria."
                reconciliationFeedback.value = msg
                onDone(true, msg)
            } catch (e: Exception) {
                val err = "Falha ao desfazer conciliação: ${e.localizedMessage}"
                reconciliationFeedback.value = err
                onDone(false, err)
            }
        }
    }

    fun ignoreBankTransaction(txId: String) {
        viewModelScope.launch {
            repository.ignoreTransaction(txId)
            clearMatchSuggestion()
        }
    }

    fun restoreBankTransaction(txId: String) {
        viewModelScope.launch {
            repository.restoreTransaction(txId)
            clearMatchSuggestion()
        }
    }

    // --- ETAPA 3.2: RENTABILIDADE & PRECIFICAÇÃO ACTIONS ---

    fun saveCostAllocation(
        allocation: CostAllocationEntity,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.saveCostAllocation(allocation)
                onDone(true, "Custo atribuído com sucesso.")
            } catch (e: Exception) {
                onDone(false, e.localizedMessage ?: "Erro ao atribuir custo.")
            }
        }
    }

    fun deleteCostAllocation(
        id: String,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteCostAllocation(id)
                onDone(true, "Atribuição de custo removida.")
            } catch (e: Exception) {
                onDone(false, e.localizedMessage ?: "Erro ao remover atribuição.")
            }
        }
    }

    fun saveApportionmentPolicy(policy: ApportionmentPolicyEntity) {
        viewModelScope.launch {
            repository.saveApportionmentPolicy(policy)
        }
    }

    fun deleteApportionmentPolicy(id: String) {
        viewModelScope.launch {
            repository.deleteApportionmentPolicy(id)
        }
    }

    fun applyApportionment(
        policy: ApportionmentPolicyEntity,
        expenseId: String,
        competence: String,
        previewItems: List<ApportionmentPreviewItem>,
        notes: String,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.applyApportionment(policy, expenseId, competence, previewItems, notes)
                onDone(true, "Rateio de despesas aplicado com sucesso.")
            } catch (e: Exception) {
                onDone(false, e.localizedMessage ?: "Erro ao aplicar rateio.")
            }
        }
    }

    fun saveTimeTracking(
        entry: TimeTrackingEntity,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.saveTimeTracking(entry)
                onDone(true, "Horas de atendimento registradas com sucesso.")
            } catch (e: Exception) {
                onDone(false, e.localizedMessage ?: "Erro ao registrar horas.")
            }
        }
    }

    fun deleteTimeTracking(id: String) {
        viewModelScope.launch {
            repository.deleteTimeTracking(id)
        }
    }

    fun savePricingSimulation(simulation: PricingSimulationScenarioEntity) {
        viewModelScope.launch {
            repository.savePricingSimulation(simulation)
        }
    }

    fun deletePricingSimulation(id: String) {
        viewModelScope.launch {
            repository.deletePricingSimulation(id)
        }
    }

    fun simulatePricingScenarios(
        serviceId: String,
        serviceName: String,
        currentPriceCents: Long,
        proposedPriceCents: Long,
        currentClientsCount: Int,
        directCostPerClientCents: Long,
        allocatedExpensePerClientCents: Long,
        averageHoursPerClient: Double,
        baseChurnPercent: Double,
        targetMarginPercent: Double
    ): List<PricingScenarioResult> {
        return PricingSimulatorEngine.generateScenarios(
            serviceId = serviceId,
            serviceName = serviceName,
            currentPriceCents = currentPriceCents,
            proposedPriceCents = proposedPriceCents,
            currentClientsCount = currentClientsCount,
            directCostPerClientCents = directCostPerClientCents,
            allocatedExpensePerClientCents = allocatedExpensePerClientCents,
            averageHoursPerClient = averageHoursPerClient,
            baseChurnPercent = baseChurnPercent,
            targetMarginPercent = targetMarginPercent
        )
    }

    fun exportProfitabilityPdf(context: Context) {
        val bizName = settings.value?.businessName ?: "PH Personal Trainer"
        ReportExporter.exportProfitabilityPdf(
            context = context,
            overview = profitabilityMetrics.value,
            services = serviceProfitabilityList.value,
            competence = selectedCompetence.value,
            businessName = bizName
        )
    }

    fun exportServiceProfitabilityCsv(context: Context) {
        ReportExporter.exportServiceProfitabilityCsv(
            context = context,
            services = serviceProfitabilityList.value,
            competence = selectedCompetence.value
        )
    }

    fun exportStudentProfitabilityCsv(context: Context) {
        ReportExporter.exportStudentProfitabilityCsv(
            context = context,
            students = studentProfitabilityList.value,
            competence = selectedCompetence.value
        )
    }

    fun exportPricingSimulationCsv(
        context: Context,
        serviceName: String,
        scenarios: List<PricingScenarioResult>
    ) {
        ReportExporter.exportPricingSimulationCsv(
            context = context,
            serviceName = serviceName,
            scenarios = scenarios
        )
    }
}


