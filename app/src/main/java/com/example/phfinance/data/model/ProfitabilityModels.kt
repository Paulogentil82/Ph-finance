package com.example.phfinance.data.model

enum class CostAllocationType(val label: String) {
    DIRETO("Custo Direto"),
    INDIRETO("Custo Indireto / Compartilhado"),
    DIRETO_CLIENTE("Direto por Aluno"),
    DIRETO_SERVICO("Direto por Serviço"),
    DIRETO_CONTRATO("Direto por Contrato"),
    INDIRETO_RATEADO("Indireto / Rateado")
}

enum class ApportionmentMethod(val label: String) {
    PROPORCIONAL_RECEITA("Proporcional à Receita"),
    PROPORCIONAL_TEMPO("Proporcional ao Tempo Dedicado"),
    QUANTIDADE_CLIENTES("Igual por Aluno Ativo"),
    QUANTIDADE_SESSOES("Por Quantidade de Sessões"),
    VALOR_FIXO_CONTRATO("Valor Fixo por Contrato"),
    DISTRIBUICAO_MANUAL("Distribuição Manual")
}

enum class TimeActivityType(val label: String) {
    ATENDIMENTO_PRESENCIAL("Atendimento Presencial"),
    AVALIACAO_FISICA("Avaliação Física"),
    PLANEJAMENTO_TREINO("Elaboração de Planejamento"),
    CONSULTORIA_ONLINE("Acompanhamento Consultoria"),
    ADMINISTRATIVO("Atendimento Administrativo"),
    OUTRO("Outras Atividades")
}

enum class PricingScenarioType(val label: String) {
    CONSERVADOR("Conservador"),
    BASE("Cenário Base"),
    OTIMISTA("Otimista")
}

data class ProfitabilityMetricResult(
    val revenueContractedCents: Long = 0L,
    val revenueReceivedCents: Long = 0L,
    val revenuePendingCents: Long = 0L,
    val discountsCents: Long = 0L,
    val financialFeesCents: Long = 0L,
    val netRevenueCents: Long = 0L,
    val directCostsCents: Long = 0L,
    val contributionMarginCents: Long = 0L,
    val contributionMarginPercent: Double = 0.0,
    val allocatedIndirectCostsCents: Long = 0L,
    val managerialOperatingResultCents: Long = 0L,
    val operatingResultPercent: Double = 0.0,
    val dedicatedMinutes: Int = 0,
    val dedicatedHours: Double = 0.0,
    val revenuePerHourCents: Long = 0L,
    val economicHourlyRateCents: Long = 5000L, // R$ 50,00/h default
    val estimatedEconomicCostOfTimeCents: Long = 0L,
    val estimatedEconomicResultCents: Long = 0L,
    val isTimeSufficient: Boolean = false,
    val economicCostOfTimeCents: Long = estimatedEconomicCostOfTimeCents,
    val economicResultCents: Long = estimatedEconomicResultCents,
    val activeClientsCount: Int = 0,
    val activeContractsCount: Int = 0
)

data class ServiceProfitabilitySummary(
    val serviceId: String,
    val serviceName: String,
    val serviceCategory: ServiceCategory = ServiceCategory.CONSULTORIA_ONLINE,
    val category: String = serviceCategory.name,
    val activeContractsCount: Int = 0,
    val sessionCount: Int = 0,
    val revenueContractedCents: Long = 0L,
    val revenueReceivedCents: Long = 0L,
    val netRevenueCents: Long = revenueReceivedCents,
    val directCostsCents: Long = 0L,
    val contributionMarginCents: Long = 0L,
    val contributionMarginPercent: Double = 0.0,
    val allocatedIndirectCostsCents: Long = 0L,
    val managerialOperatingResultCents: Long = 0L,
    val operatingResultPercent: Double = 0.0,
    val dedicatedMinutes: Int = 0,
    val dedicatedHours: Double = 0.0,
    val revenuePerHourCents: Long = 0L,
    val economicCostOfTimeCents: Long = 0L,
    val economicResultCents: Long = 0L
)

data class StudentProfitabilitySummary(
    val clientId: String,
    val clientName: String,
    val activeContractsCount: Int = 0,
    val contractedServices: List<String> = emptyList(),
    val revenueContractedCents: Long = 0L,
    val contractedRevenueCents: Long = revenueContractedCents,
    val revenueReceivedCents: Long = 0L,
    val receivedRevenueCents: Long = revenueReceivedCents,
    val revenuePendingCents: Long = 0L,
    val pendingRevenueCents: Long = revenuePendingCents,
    val discountsCents: Long = 0L,
    val financialFeesCents: Long = 0L,
    val directCostsCents: Long = 0L,
    val contributionMarginCents: Long = 0L,
    val contributionMarginPercent: Double = 0.0,
    val allocatedIndirectCostsCents: Long = 0L,
    val managerialOperatingResultCents: Long = 0L,
    val dedicatedMinutes: Int = 0,
    val dedicatedHours: Double = 0.0,
    val revenuePerHourCents: Long = 0L,
    val economicCostOfTimeCents: Long = 0L,
    val estimatedEconomicResultCents: Long = 0L,
    val economicResultCents: Long = estimatedEconomicResultCents
)

data class PricingScenarioResult(
    val scenarioType: PricingScenarioType = PricingScenarioType.BASE,
    val scenarioName: String = scenarioType.label,
    val serviceId: String = "",
    val serviceName: String = "",
    val currentPriceCents: Long = 0L,
    val proposedPriceCents: Long = 0L,
    val priceDifferenceCents: Long = proposedPriceCents - currentPriceCents,
    val currentClientsCount: Int = 0,
    val projectedClientsCount: Int = 0,
    val projectedClients: Int = projectedClientsCount,
    val projectedRevenueCents: Long = 0L,
    val projectedDirectCostsCents: Long = 0L,
    val projectedContributionMarginCents: Long = 0L,
    val projectedContributionMarginPercent: Double = 0.0,
    val projectedMarginPercent: Double = projectedContributionMarginPercent,
    val projectedAllocatedExpensesCents: Long = 0L,
    val projectedOperatingResultCents: Long = 0L,
    val resultDifferenceCents: Long = 0L,
    val breakEvenClientsCount: Int = 0,
    val breakEvenClients: Int = breakEvenClientsCount,
    val revenuePerHourProjectedCents: Long = 0L,
    val projectedRevenuePerHourCents: Long = revenuePerHourProjectedCents
)

data class ApportionmentPreviewItem(
    val targetId: String = "",
    val targetLabel: String = "",
    val baseValue: Double = 0.0,
    val basePercentage: Double = 0.0,
    val allocatedAmountCents: Long = 0L,
    val contractId: String = targetId,
    val clientId: String = "",
    val clientName: String = targetLabel,
    val serviceId: String = "",
    val percentage: Double = basePercentage,
    val allocatedCents: Long = allocatedAmountCents
)
