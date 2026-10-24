package com.example.phfinance.util

data class GrowthSimulationParams(
    val currentActiveClients: Int = 20,
    val monthlyNewClients: Int = 3,
    val monthlyChurnPercent: Double = 5.0,
    val ticketMedioCents: Long = 25000L, // R$ 250,00
    val monthlyEvaluationsCount: Int = 2,
    val evaluationPriceCents: Long = 15000L, // R$ 150,00
    val fixedExpensesCents: Long = 200000L, // R$ 2.000,00
    val variableExpensePercent: Double = 5.0,
    val estimatedInadimplenciaPercent: Double = 3.0
)

data class SimulationMonthProjection(
    val monthIndex: Int,
    val monthLabel: String,
    val projectedClients: Int,
    val mrrCents: Long,
    val evaluationsRevenueCents: Long,
    val grossRevenueCents: Long,
    val estimatedLossesCents: Long,
    val netRevenueCents: Long,
    val variableExpensesCents: Long,
    val fixedExpensesCents: Long,
    val totalExpensesCents: Long,
    val operatingResultCents: Long,
    val profitMarginPercent: Double
)

object GrowthSimulatorEngine {

    fun calculateProjections(params: GrowthSimulationParams): List<SimulationMonthProjection> {
        val results = mutableListOf<SimulationMonthProjection>()

        // Input sanitization and safety bounds
        val safeCurrentClients = params.currentActiveClients.coerceAtLeast(0)
        val safeNewClients = params.monthlyNewClients.coerceAtLeast(0)
        val safeChurnPercent = params.monthlyChurnPercent.coerceIn(0.0, 100.0)
        val safeTicketMedioCents = params.ticketMedioCents.coerceAtLeast(0L)
        val safeMonthlyEvalCount = params.monthlyEvaluationsCount.coerceAtLeast(0)
        val safeEvalPriceCents = params.evaluationPriceCents.coerceAtLeast(0L)
        val safeFixedExpensesCents = params.fixedExpensesCents.coerceAtLeast(0L)
        val safeVariableExpPercent = params.variableExpensePercent.coerceIn(0.0, 100.0)
        val safeInadimplenciaPercent = params.estimatedInadimplenciaPercent.coerceIn(0.0, 100.0)

        var clientCount = safeCurrentClients.toDouble()

        for (m in 1..12) {
            // Apply churn
            val churnedClients = clientCount * (safeChurnPercent / 100.0)
            clientCount = (clientCount - churnedClients + safeNewClients).coerceAtLeast(0.0)

            val activeClientsInt = clientCount.toInt()
            val mrrCents = activeClientsInt * safeTicketMedioCents
            val evalRevenueCents = safeMonthlyEvalCount * safeEvalPriceCents
            val grossRevenueCents = mrrCents + evalRevenueCents

            val lossesCents = (grossRevenueCents.toDouble() * (safeInadimplenciaPercent / 100.0)).toLong()
            val netRevenueCents = (grossRevenueCents - lossesCents).coerceAtLeast(0L)

            val variableExpensesCents = (netRevenueCents.toDouble() * (safeVariableExpPercent / 100.0)).toLong()
            val totalExpensesCents = variableExpensesCents + safeFixedExpensesCents

            val operatingResultCents = netRevenueCents - totalExpensesCents
            val profitMargin = if (netRevenueCents > 0) {
                ((operatingResultCents.toDouble() / netRevenueCents.toDouble()) * 100.0).coerceIn(-1000.0, 100.0)
            } else 0.0

            if (m == 1 || m == 3 || m == 6 || m == 12) {
                results.add(
                    SimulationMonthProjection(
                        monthIndex = m,
                        monthLabel = "Mês $m",
                        projectedClients = activeClientsInt,
                        mrrCents = mrrCents,
                        evaluationsRevenueCents = evalRevenueCents,
                        grossRevenueCents = grossRevenueCents,
                        estimatedLossesCents = lossesCents,
                        netRevenueCents = netRevenueCents,
                        variableExpensesCents = variableExpensesCents,
                        fixedExpensesCents = safeFixedExpensesCents,
                        totalExpensesCents = totalExpensesCents,
                        operatingResultCents = operatingResultCents,
                        profitMarginPercent = profitMargin
                    )
                )
            }
        }
        return results
    }
}
