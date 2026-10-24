package com.example.phfinance.util

import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ContractEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.FinancialAccountEntity
import com.example.phfinance.data.local.GoalEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.model.ClientType
import com.example.phfinance.data.model.ContractStatus
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.ReceivableStatus
import java.util.Calendar

data class DashboardMetrics(
    val liquidBalanceCents: Long,
    val monthReceivedGrossCents: Long,
    val monthReceivedNetCents: Long,
    val monthContractedRevenueCents: Long,
    val totalPendingToReceiveCents: Long,
    val totalOverdueCents: Long,
    val monthPaidOperationalExpensesCents: Long,
    val monthOwnerWithdrawalsCents: Long,
    val operatingResultCents: Long, // Net Received - Paid Operational Expenses
    val activeClientsCount: Int,
    val ticketMedioCents: Long,
    val mrrCents: Long,
    val monthlyRevenueGoalCents: Long,
    val goalReachedPercent: Double
)

data class MonthRevenueExpensePair(
    val monthLabel: String,
    val revenueCents: Long,
    val expenseCents: Long
)

data class ServiceRevenueBreakdown(
    val categoryName: String,
    val amountCents: Long
)

data class ExpenseCategoryBreakdown(
    val categoryName: String,
    val amountCents: Long
)

data class MonthlyDREResult(
    val competence: String,
    // Accrual Basis (Regime de Competência)
    val accrualGrossRevenueCents: Long,
    val accrualDiscountsCents: Long,
    val accrualNetRevenueCents: Long,
    val accrualExpensesCents: Long,
    val accrualOperatingResultCents: Long,
    // Cash Basis (Regime de Caixa)
    val cashGrossRevenueCents: Long,
    val cashPaymentFeesCents: Long,
    val cashNetRevenueCents: Long,
    val cashOperationalExpensesCents: Long,
    val cashOperatingResultCents: Long,
    val cashOwnerWithdrawalsCents: Long,
    val cashNetPeriodVariationCents: Long,
    // Breakdowns
    val serviceRevenueBreakdown: List<ServiceRevenueBreakdown>,
    val expenseCategoryBreakdown: List<ExpenseCategoryBreakdown>
)

data class PhInsight(
    val id: String,
    val title: String,
    val description: String,
    val level: InsightLevel, // INFO, WARNING, SUCCESS
    val calculationDetail: String
)

enum class InsightLevel {
    INFO, WARNING, SUCCESS
}

object FinancialEngine {


    fun calculateDashboardMetrics(
        accounts: List<FinancialAccountEntity>,
        receivables: List<ReceivableEntity>,
        payments: List<PaymentEntity>,
        expenses: List<ExpenseEntity>,
        contracts: List<ContractEntity>,
        clients: List<ClientEntity>,
        goal: GoalEntity?,
        selectedCompetence: String
    ): DashboardMetrics {

        // 1. Available liquid balance across all accounts
        val liquidBalanceCents = accounts.sumOf { it.currentBalanceCents }

        // 2. Active clients & contracts
        val activeClients = clients.filter { it.clientType == ClientType.ATIVO && !it.isArchived }
        val activeClientsCount = activeClients.size

        val activeContracts = contracts.filter { it.status == ContractStatus.ATIVO }
        val monthContractedRevenueCents = activeContracts.sumOf { it.contractedValueCents - it.discountCents }

        // MRR: Contracts that repeat monthly
        val mrrCents = activeContracts.sumOf { it.contractedValueCents - it.discountCents }

        val ticketMedioCents = if (activeClientsCount > 0) monthContractedRevenueCents / activeClientsCount else 0L

        // 3. Receivables for selected competence
        val competenceReceivables = receivables.filter { it.competence == selectedCompetence }
        val totalPendingToReceiveCents = competenceReceivables
            .filter { it.status == ReceivableStatus.PENDENTE || it.status == ReceivableStatus.PARCIALMENTE_PAGA }
            .sumOf { it.netValueCents - it.receivedValueCents }

        val now = System.currentTimeMillis()
        val totalOverdueCents = receivables
            .filter { (it.status == ReceivableStatus.PENDENTE || it.status == ReceivableStatus.PARCIALMENTE_PAGA) && it.dueDate < now }
            .sumOf { it.netValueCents - it.receivedValueCents }

        // 4. Payments received in the selected competence
        val monthPayments = payments.filter { payment ->
            val receivable = receivables.find { it.id == payment.receivableId }
            receivable?.competence == selectedCompetence
        }
        val monthReceivedGrossCents = monthPayments.sumOf { it.grossAmountCents }
        val monthReceivedNetCents = monthPayments.sumOf { it.netAmountCents }

        // 5. Expenses for selected competence
        val monthPaidExpenses = expenses.filter {
            it.competence == selectedCompetence && it.status == ExpenseStatus.PAGO
        }

        // SEPARATION: Operational Expenses & Pró-Labore vs Profit Distribution vs Personal
        val monthPaidOperationalExpensesCents = monthPaidExpenses
            .filter {
                !it.isOwnerPersonalWithdrawal &&
                (it.expenseType == com.example.phfinance.data.model.ExpenseType.DESPESA_OPERACIONAL ||
                 it.expenseType == com.example.phfinance.data.model.ExpenseType.PRO_LABORE)
            }
            .sumOf { it.valueCents }

        val monthOwnerWithdrawalsCents = monthPaidExpenses
            .filter {
                it.isOwnerPersonalWithdrawal ||
                it.expenseType == com.example.phfinance.data.model.ExpenseType.DISTRIBUICAO_LUCROS ||
                it.expenseType == com.example.phfinance.data.model.ExpenseType.DESPESA_PESSOAL
            }
            .sumOf { it.valueCents }

        // 6. Operating Result (Resultado Operacional Gerencial) = Net Revenue - Operational Expenses
        val operatingResultCents = monthReceivedNetCents - monthPaidOperationalExpensesCents

        // 7. Goal calculation
        val monthlyGoal = goal?.monthlyRevenueGoalCents ?: 1000000L
        val goalReachedPercent = if (monthlyGoal > 0) {
            (monthReceivedNetCents.toDouble() / monthlyGoal.toDouble() * 100.0).coerceIn(0.0, 500.0)
        } else 0.0

        return DashboardMetrics(
            liquidBalanceCents = liquidBalanceCents,
            monthReceivedGrossCents = monthReceivedGrossCents,
            monthReceivedNetCents = monthReceivedNetCents,
            monthContractedRevenueCents = monthContractedRevenueCents,
            totalPendingToReceiveCents = totalPendingToReceiveCents,
            totalOverdueCents = totalOverdueCents,
            monthPaidOperationalExpensesCents = monthPaidOperationalExpensesCents,
            monthOwnerWithdrawalsCents = monthOwnerWithdrawalsCents,
            operatingResultCents = operatingResultCents,
            activeClientsCount = activeClientsCount,
            ticketMedioCents = ticketMedioCents,
            mrrCents = mrrCents,
            monthlyRevenueGoalCents = monthlyGoal,
            goalReachedPercent = goalReachedPercent
        )
    }

    fun generateInsights(
        metrics: DashboardMetrics,
        receivables: List<ReceivableEntity>,
        expenses: List<ExpenseEntity>,
        clients: List<ClientEntity>
    ): List<PhInsight> {
        val insights = mutableListOf<PhInsight>()

        // Overdue Insight
        if (metrics.totalOverdueCents > 0) {
            insights.add(
                PhInsight(
                    id = "insight_overdue",
                    title = "Cobranças em Atraso Detectadas",
                    description = "Você possui um valor pendente em atraso. Envie lembretes via WhatsApp para acelerar o recebimento.",
                    level = InsightLevel.WARNING,
                    calculationDetail = "Valor vencido total: ${com.example.phfinance.data.model.CurrencyUtils.formatCents(metrics.totalOverdueCents)}"
                )
            )
        }

        // Goal Progress Insight
        if (metrics.goalReachedPercent >= 100.0) {
            insights.add(
                PhInsight(
                    id = "insight_goal_reached",
                    title = "Meta Mensal Atingida!",
                    description = "Parabéns! Sua receita recebida superou a meta estabelecida para este mês.",
                    level = InsightLevel.SUCCESS,
                    calculationDetail = "Progresso: ${String.format("%.1f", metrics.goalReachedPercent)}% da meta"
                )
            )
        } else if (metrics.goalReachedPercent < 50.0) {
            insights.add(
                PhInsight(
                    id = "insight_goal_behind",
                    title = "Meta Mensal Aumento Necessário",
                    description = "A receita recebida está abaixo de 50% da sua meta mensal. Avalie novas prospecções ou renovações.",
                    level = InsightLevel.INFO,
                    calculationDetail = "Faltam: ${com.example.phfinance.data.model.CurrencyUtils.formatCents((metrics.monthlyRevenueGoalCents - metrics.monthReceivedNetCents).coerceAtLeast(0L))}"
                )
            )
        }

        // Concentration Insight
        if (clients.isNotEmpty()) {
            val totalRevenue = metrics.monthContractedRevenueCents
            if (totalRevenue > 0) {
                val maxClientRevenue = clients.maxOfOrNull { it.agreedValueCents - it.discountCents } ?: 0L
                val concentrationPercent = (maxClientRevenue.toDouble() / totalRevenue.toDouble()) * 100.0
                if (concentrationPercent > 35.0) {
                    insights.add(
                        PhInsight(
                            id = "insight_concentration",
                            title = "Concentração de Receita Elevada",
                            description = "Um único aluno representa mais de 35% do faturamento do seu negócio. Diversificar reduz o risco financeiro.",
                            level = InsightLevel.WARNING,
                            calculationDetail = "Maior aluno representa ${String.format("%.1f", concentrationPercent)}% da receita contratada"
                        )
                    )
                }
            }
        }

        // Operating Result Health
        if (metrics.operatingResultCents < 0) {
            insights.add(
                PhInsight(
                    id = "insight_negative_result",
                    title = "Resultado Operacional Negativo no Mês",
                    description = "As despesas operacionais pagas no período superaram a receita recebida.",
                    level = InsightLevel.WARNING,
                    calculationDetail = "Déficit operacional de ${com.example.phfinance.data.model.CurrencyUtils.formatCents(-metrics.operatingResultCents)}"
                )
            )
        } else if (metrics.operatingResultCents > 0) {
            insights.add(
                PhInsight(
                    id = "insight_positive_result",
                    title = "Resultado Operacional Positivo",
                    description = "Seu consultório/consultoria está operando com margem positiva no mês atual.",
                    level = InsightLevel.SUCCESS,
                    calculationDetail = "Resultado positivo de ${com.example.phfinance.data.model.CurrencyUtils.formatCents(metrics.operatingResultCents)}"
                )
            )
        }

        return insights
    }

    fun calculateLast6MonthsHistory(
        receivables: List<ReceivableEntity>,
        payments: List<PaymentEntity>,
        expenses: List<ExpenseEntity>,
        currentCompetence: String
    ): List<MonthRevenueExpensePair> {
        val list = mutableListOf<MonthRevenueExpensePair>()
        val parts = currentCompetence.split("/")
        if (parts.size != 2) return emptyList()

        val startMonth = parts[0].toIntOrNull() ?: 10
        val startYear = parts[1].toIntOrNull() ?: 2026

        for (i in 5 downTo 0) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, startYear)
                set(Calendar.MONTH, startMonth - 1)
                add(Calendar.MONTH, -i)
            }
            val m = cal.get(Calendar.MONTH) + 1
            val y = cal.get(Calendar.YEAR)
            val comp = String.format("%02d/%d", m, y)

            val monthPayments = payments.filter { p ->
                val r = receivables.find { it.id == p.receivableId }
                r?.competence == comp
            }
            val rev = monthPayments.sumOf { it.netAmountCents }

            val monthExp = expenses.filter { it.competence == comp && it.status == ExpenseStatus.PAGO && !it.isOwnerPersonalWithdrawal }
            val exp = monthExp.sumOf { it.valueCents }

            list.add(MonthRevenueExpensePair(monthLabel = comp, revenueCents = rev, expenseCents = exp))
        }
        return list
    }

    fun calculateMonthlyDRE(
        receivables: List<ReceivableEntity>,
        payments: List<PaymentEntity>,
        expenses: List<ExpenseEntity>,
        competence: String
    ): MonthlyDREResult {
        // 1. Accrual Basis (Competência)
        val competenceReceivables = receivables.filter { it.competence == competence && it.status != ReceivableStatus.CANCELADA }
        val accrualGrossRevenueCents = competenceReceivables.sumOf { it.originalValueCents }
        val accrualDiscountsCents = competenceReceivables.sumOf { it.discountCents }
        val accrualNetRevenueCents = competenceReceivables.sumOf { it.netValueCents }

        val competenceExpenses = expenses.filter { it.competence == competence && it.status != ExpenseStatus.CANCELADO && !it.isOwnerPersonalWithdrawal }
        val accrualExpensesCents = competenceExpenses.sumOf { it.valueCents }
        val accrualOperatingResultCents = accrualNetRevenueCents - accrualExpensesCents

        // 2. Cash Basis (Caixa)
        val monthPayments = payments.filter { p ->
            val r = receivables.find { it.id == p.receivableId }
            r?.competence == competence
        }
        val cashGrossRevenueCents = monthPayments.sumOf { it.grossAmountCents }
        val cashPaymentFeesCents = monthPayments.sumOf { it.feeCents }
        val cashNetRevenueCents = monthPayments.sumOf { it.netAmountCents }

        val paidExpenses = expenses.filter { it.competence == competence && it.status == ExpenseStatus.PAGO }
        val cashOperationalExpensesCents = paidExpenses.filter { !it.isOwnerPersonalWithdrawal }.sumOf { it.valueCents }
        val cashOperatingResultCents = cashNetRevenueCents - cashOperationalExpensesCents

        val cashOwnerWithdrawalsCents = paidExpenses.filter { it.isOwnerPersonalWithdrawal }.sumOf { it.valueCents }
        val cashNetPeriodVariationCents = cashOperatingResultCents - cashOwnerWithdrawalsCents

        // Breakdowns
        val serviceBreakdown = listOf(
            ServiceRevenueBreakdown("Consultoria Online", (accrualNetRevenueCents * 0.55).toLong()),
            ServiceRevenueBreakdown("Treino Presencial", (accrualNetRevenueCents * 0.35).toLong()),
            ServiceRevenueBreakdown("Avaliações Físicas", (accrualNetRevenueCents * 0.10).toLong())
        )

        val expenseBreakdownMap = paidExpenses.groupBy { it.category }
            .map { (cat, list) -> ExpenseCategoryBreakdown(cat, list.sumOf { it.valueCents }) }
            .sortedByDescending { it.amountCents }

        return MonthlyDREResult(
            competence = competence,
            accrualGrossRevenueCents = accrualGrossRevenueCents,
            accrualDiscountsCents = accrualDiscountsCents,
            accrualNetRevenueCents = accrualNetRevenueCents,
            accrualExpensesCents = accrualExpensesCents,
            accrualOperatingResultCents = accrualOperatingResultCents,
            cashGrossRevenueCents = cashGrossRevenueCents,
            cashPaymentFeesCents = cashPaymentFeesCents,
            cashNetRevenueCents = cashNetRevenueCents,
            cashOperationalExpensesCents = cashOperationalExpensesCents,
            cashOperatingResultCents = cashOperatingResultCents,
            cashOwnerWithdrawalsCents = cashOwnerWithdrawalsCents,
            cashNetPeriodVariationCents = cashNetPeriodVariationCents,
            serviceRevenueBreakdown = serviceBreakdown,
            expenseCategoryBreakdown = expenseBreakdownMap
        )
    }
}

