package com.example.phfinance.util

import com.example.phfinance.data.local.PricingSimulationScenarioEntity
import com.example.phfinance.data.model.PricingScenarioResult
import com.example.phfinance.data.model.PricingScenarioType
import java.util.UUID
import kotlin.math.ceil

object PricingSimulatorEngine {

    fun generateScenarios(
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
        val totalFixedCostsCents = allocatedExpensePerClientCents * currentClientsCount.coerceAtLeast(1)

        val conservadorChurn = (baseChurnPercent + 15.0).coerceIn(0.0, 90.0)
        val baseChurn = baseChurnPercent.coerceIn(0.0, 90.0)
        val otimistaChurn = (baseChurnPercent - 10.0).coerceAtLeast(0.0)

        return listOf(
            calculateScenario(
                scenarioType = "CONSERVADOR",
                name = "Cenário Conservador",
                serviceId = serviceId,
                currentPriceCents = currentPriceCents,
                proposedPriceCents = proposedPriceCents,
                currentClientsCount = currentClientsCount,
                churnPercent = conservadorChurn,
                directCostPerClientCents = directCostPerClientCents,
                allocatedExpensePerClientCents = allocatedExpensePerClientCents,
                totalFixedCostsCents = totalFixedCostsCents,
                averageHours = averageHoursPerClient,
                targetMarginPercent = targetMarginPercent
            ),
            calculateScenario(
                scenarioType = "BASE",
                name = "Cenário Base (Esperado)",
                serviceId = serviceId,
                currentPriceCents = currentPriceCents,
                proposedPriceCents = proposedPriceCents,
                currentClientsCount = currentClientsCount,
                churnPercent = baseChurn,
                directCostPerClientCents = directCostPerClientCents,
                allocatedExpensePerClientCents = allocatedExpensePerClientCents,
                totalFixedCostsCents = totalFixedCostsCents,
                averageHours = averageHoursPerClient,
                targetMarginPercent = targetMarginPercent
            ),
            calculateScenario(
                scenarioType = "OTIMISTA",
                name = "Cenário Otimista",
                serviceId = serviceId,
                currentPriceCents = currentPriceCents,
                proposedPriceCents = proposedPriceCents,
                currentClientsCount = currentClientsCount,
                churnPercent = otimistaChurn,
                directCostPerClientCents = directCostPerClientCents,
                allocatedExpensePerClientCents = allocatedExpensePerClientCents,
                totalFixedCostsCents = totalFixedCostsCents,
                averageHours = averageHoursPerClient,
                targetMarginPercent = targetMarginPercent
            )
        )
    }

    fun calculateScenario(
        scenarioType: String,
        name: String,
        serviceId: String,
        currentPriceCents: Long,
        proposedPriceCents: Long,
        currentClientsCount: Int,
        churnPercent: Double,
        directCostPerClientCents: Long,
        allocatedExpensePerClientCents: Long,
        totalFixedCostsCents: Long,
        averageHours: Double,
        targetMarginPercent: Double
    ): PricingScenarioResult {
        val scenarioTypeEnum = try {
            PricingScenarioType.valueOf(scenarioType)
        } catch (e: Exception) {
            PricingScenarioType.BASE
        }

        val remainingRate = (100.0 - churnPercent) / 100.0
        val projectedClients = ceil(currentClientsCount * remainingRate).toInt().coerceAtLeast(0)

        val projectedRevenueCents = proposedPriceCents * projectedClients
        val projectedDirectCostsCents = directCostPerClientCents * projectedClients
        val projectedContributionMarginCents = projectedRevenueCents - projectedDirectCostsCents
        val projectedMarginPercent = if (projectedRevenueCents > 0) {
            (projectedContributionMarginCents.toDouble() / projectedRevenueCents.toDouble()) * 100.0
        } else 0.0

        val projectedIndirectCostsCents = allocatedExpensePerClientCents * projectedClients
        val projectedOperatingResultCents = projectedContributionMarginCents - projectedIndirectCostsCents

        val unitContributionMarginCents = proposedPriceCents - directCostPerClientCents
        val breakEvenClients = if (unitContributionMarginCents > 0) {
            ceil(totalFixedCostsCents.toDouble() / unitContributionMarginCents.toDouble()).toInt()
        } else 0

        val totalHours = averageHours * projectedClients
        val projectedRevenuePerHourCents = if (totalHours > 0.0) {
            (projectedRevenueCents / totalHours).toLong()
        } else 0L

        return PricingScenarioResult(
            scenarioType = scenarioTypeEnum,
            scenarioName = name,
            serviceId = serviceId,
            serviceName = "",
            currentPriceCents = currentPriceCents,
            proposedPriceCents = proposedPriceCents,
            priceDifferenceCents = proposedPriceCents - currentPriceCents,
            currentClientsCount = currentClientsCount,
            projectedClientsCount = projectedClients,
            projectedClients = projectedClients,
            projectedRevenueCents = projectedRevenueCents,
            projectedDirectCostsCents = projectedDirectCostsCents,
            projectedContributionMarginCents = projectedContributionMarginCents,
            projectedContributionMarginPercent = projectedMarginPercent,
            projectedMarginPercent = projectedMarginPercent,
            projectedAllocatedExpensesCents = projectedIndirectCostsCents,
            projectedOperatingResultCents = projectedOperatingResultCents,
            resultDifferenceCents = projectedOperatingResultCents,
            breakEvenClientsCount = breakEvenClients,
            breakEvenClients = breakEvenClients,
            revenuePerHourProjectedCents = projectedRevenuePerHourCents,
            projectedRevenuePerHourCents = projectedRevenuePerHourCents
        )
    }

    fun toEntity(
        serviceId: String,
        scenario: PricingScenarioResult,
        currentPriceCents: Long,
        proposedPriceCents: Long,
        currentClientsCount: Int,
        directCostPerClientCents: Long,
        allocatedExpensePerClientCents: Long,
        averageHours: Double,
        churnPercent: Double,
        targetMarginPercent: Double,
        notes: String = ""
    ): PricingSimulationScenarioEntity {
        return PricingSimulationScenarioEntity(
            id = UUID.randomUUID().toString(),
            serviceId = serviceId,
            scenarioName = scenario.scenarioName,
            scenarioType = scenario.scenarioType.name,
            currentPriceCents = currentPriceCents,
            proposedPriceCents = proposedPriceCents,
            currentClientsCount = currentClientsCount,
            directCostPerClientCents = directCostPerClientCents,
            allocatedExpensePerClientCents = allocatedExpensePerClientCents,
            averageDedicatedHoursPerClient = averageHours,
            hypotheticalChurnPercent = churnPercent,
            targetMarginPercent = targetMarginPercent,
            projectedRevenueCents = scenario.projectedRevenueCents,
            projectedContributionMarginCents = scenario.projectedContributionMarginCents,
            projectedOperatingResultCents = scenario.projectedOperatingResultCents,
            breakEvenClientsCount = scenario.breakEvenClients,
            notes = notes
        )
    }
}
