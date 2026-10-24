package com.example.phfinance.util

import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ContractEntity
import com.example.phfinance.data.local.CostAllocationEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.local.ServiceEntity
import com.example.phfinance.data.local.TimeTrackingEntity
import com.example.phfinance.data.model.ApportionmentMethod
import com.example.phfinance.data.model.ApportionmentPreviewItem
import com.example.phfinance.data.model.CostAllocationType
import com.example.phfinance.data.model.ProfitabilityMetricResult
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.data.model.ServiceProfitabilitySummary
import com.example.phfinance.data.model.StudentProfitabilitySummary

object ProfitabilityEngine {

    /**
     * Valida se a soma das alocações existentes mais a nova não ultrapassa o valor total da despesa.
     */
    fun validateAllocationLimit(
        expense: ExpenseEntity,
        existingAllocations: List<CostAllocationEntity>,
        newAllocationCents: Long,
        excludeAllocationId: String? = null
    ): Boolean {
        val totalAllocated = existingAllocations
            .filter { it.expenseId == expense.id && it.id != excludeAllocationId }
            .sumOf { it.allocatedAmountCents }
        return (totalAllocated + newAllocationCents) <= expense.valueCents
    }

    /**
     * Calcula o valor residual disponível para alocação de uma despesa.
     */
    fun getAvailableAmountForAllocation(
        expense: ExpenseEntity,
        existingAllocations: List<CostAllocationEntity>
    ): Long {
        val totalAllocated = existingAllocations
            .filter { it.expenseId == expense.id }
            .sumOf { it.allocatedAmountCents }
        return (expense.valueCents - totalAllocated).coerceAtLeast(0L)
    }

    /**
     * Calcula o preview de rateio para uma política e despesas indiretas selecionadas.
     */
    fun calculateApportionmentPreview(
        method: ApportionmentMethod,
        totalIndirectCents: Long,
        activeContracts: List<ContractEntity>,
        clients: List<ClientEntity>,
        timeTrackings: List<TimeTrackingEntity>,
        receivablesInPeriod: List<ReceivableEntity>,
        fixedAmountPerContractCents: Long = 0L
    ): List<ApportionmentPreviewItem> {
        if (activeContracts.isEmpty() || totalIndirectCents <= 0L) return emptyList()

        val clientMap = clients.associateBy { it.id }

        return when (method) {
            ApportionmentMethod.PROPORCIONAL_RECEITA -> {
                val contractRevenueMap = activeContracts.associateWith { contract ->
                    receivablesInPeriod
                        .filter { it.contractId == contract.id }
                        .sumOf { it.netValueCents }
                }
                val totalRevenue = contractRevenueMap.values.sum()
                if (totalRevenue <= 0L) {
                    divideEqually(activeContracts, clientMap, totalIndirectCents)
                } else {
                    var accumulated = 0L
                    activeContracts.mapIndexed { index, contract ->
                        val rev = contractRevenueMap[contract] ?: 0L
                        val ratio = rev.toDouble() / totalRevenue.toDouble()
                        val allocated = if (index == activeContracts.lastIndex) {
                            totalIndirectCents - accumulated
                        } else {
                            (totalIndirectCents * ratio).toLong()
                        }
                        accumulated += allocated
                        ApportionmentPreviewItem(
                            targetId = contract.id,
                            targetLabel = clientMap[contract.clientId]?.fullName ?: "Aluno",
                            baseValue = rev.toDouble(),
                            basePercentage = ratio * 100.0,
                            allocatedAmountCents = allocated,
                            contractId = contract.id,
                            clientId = contract.clientId,
                            clientName = clientMap[contract.clientId]?.fullName ?: "Aluno",
                            serviceId = contract.serviceId,
                            percentage = ratio * 100.0,
                            allocatedCents = allocated
                        )
                    }
                }
            }

            ApportionmentMethod.PROPORCIONAL_TEMPO -> {
                val contractTimeMap = activeContracts.associateWith { contract ->
                    timeTrackings.filter { it.contractId == contract.id }.sumOf { it.durationMinutes }
                }
                val totalTime = contractTimeMap.values.sum()
                if (totalTime <= 0) {
                    divideEqually(activeContracts, clientMap, totalIndirectCents)
                } else {
                    var accumulated = 0L
                    activeContracts.mapIndexed { index, contract ->
                        val minutes = contractTimeMap[contract] ?: 0
                        val ratio = minutes.toDouble() / totalTime.toDouble()
                        val allocated = if (index == activeContracts.lastIndex) {
                            totalIndirectCents - accumulated
                        } else {
                            (totalIndirectCents * ratio).toLong()
                        }
                        accumulated += allocated
                        ApportionmentPreviewItem(
                            targetId = contract.id,
                            targetLabel = clientMap[contract.clientId]?.fullName ?: "Aluno",
                            baseValue = minutes.toDouble(),
                            basePercentage = ratio * 100.0,
                            allocatedAmountCents = allocated,
                            contractId = contract.id,
                            clientId = contract.clientId,
                            clientName = clientMap[contract.clientId]?.fullName ?: "Aluno",
                            serviceId = contract.serviceId,
                            percentage = ratio * 100.0,
                            allocatedCents = allocated
                        )
                    }
                }
            }

            ApportionmentMethod.QUANTIDADE_CLIENTES, ApportionmentMethod.QUANTIDADE_SESSOES -> {
                divideEqually(activeContracts, clientMap, totalIndirectCents)
            }

            ApportionmentMethod.VALOR_FIXO_CONTRATO -> {
                activeContracts.map { contract ->
                    val pct = if (totalIndirectCents > 0) (fixedAmountPerContractCents.toDouble() / totalIndirectCents) * 100 else 0.0
                    ApportionmentPreviewItem(
                        targetId = contract.id,
                        targetLabel = clientMap[contract.clientId]?.fullName ?: "Aluno",
                        baseValue = fixedAmountPerContractCents.toDouble(),
                        basePercentage = pct,
                        allocatedAmountCents = fixedAmountPerContractCents,
                        contractId = contract.id,
                        clientId = contract.clientId,
                        clientName = clientMap[contract.clientId]?.fullName ?: "Aluno",
                        serviceId = contract.serviceId,
                        percentage = pct,
                        allocatedCents = fixedAmountPerContractCents
                    )
                }
            }

            ApportionmentMethod.DISTRIBUICAO_MANUAL -> {
                divideEqually(activeContracts, clientMap, totalIndirectCents)
            }
        }
    }

    private fun divideEqually(
        activeContracts: List<ContractEntity>,
        clientMap: Map<String, ClientEntity>,
        totalIndirectCents: Long
    ): List<ApportionmentPreviewItem> {
        val count = activeContracts.size
        val baseShare = totalIndirectCents / count
        var accumulated = 0L

        return activeContracts.mapIndexed { index, contract ->
            val allocated = if (index == count - 1) {
                totalIndirectCents - accumulated
            } else {
                baseShare
            }
            accumulated += allocated
            val pct = (allocated.toDouble() / totalIndirectCents.toDouble()) * 100.0
            ApportionmentPreviewItem(
                targetId = contract.id,
                targetLabel = clientMap[contract.clientId]?.fullName ?: "Aluno",
                baseValue = 1.0,
                basePercentage = pct,
                allocatedAmountCents = allocated,
                contractId = contract.id,
                clientId = contract.clientId,
                clientName = clientMap[contract.clientId]?.fullName ?: "Aluno",
                serviceId = contract.serviceId,
                percentage = pct,
                allocatedCents = allocated
            )
        }
    }

    /**
     * Calcula métricas agregadas da Central de Rentabilidade para a competência/período.
     */
    fun calculateOverviewMetrics(
        receivables: List<ReceivableEntity>,
        payments: List<PaymentEntity>,
        expenses: List<ExpenseEntity>,
        costAllocations: List<CostAllocationEntity>,
        timeTrackings: List<TimeTrackingEntity>,
        clients: List<ClientEntity>,
        contracts: List<ContractEntity>,
        hourlyEconomicRateCents: Long = 5000L
    ): ProfitabilityMetricResult {
        val netRevenueCents = payments.sumOf { it.grossAmountCents - it.feeCents }

        val directCostsCents = costAllocations
            .filter { it.allocationType != CostAllocationType.INDIRETO_RATEADO && it.allocationType != CostAllocationType.INDIRETO }
            .sumOf { it.allocatedAmountCents }

        val contributionMarginCents = netRevenueCents - directCostsCents
        val contributionMarginPercent = if (netRevenueCents > 0) {
            (contributionMarginCents.toDouble() / netRevenueCents.toDouble()) * 100.0
        } else 0.0

        val allocatedIndirectCents = costAllocations
            .filter { it.allocationType == CostAllocationType.INDIRETO_RATEADO || it.allocationType == CostAllocationType.INDIRETO }
            .sumOf { it.allocatedAmountCents }

        val managerialOperatingResultCents = contributionMarginCents - allocatedIndirectCents

        val totalDedicatedMinutes = timeTrackings.sumOf { it.durationMinutes }
        val dedicatedHours = totalDedicatedMinutes / 60.0

        val revenuePerHourCents = if (dedicatedHours > 0.0) {
            (netRevenueCents / dedicatedHours).toLong()
        } else 0L

        val economicCostOfTimeCents = (dedicatedHours * hourlyEconomicRateCents).toLong()
        val economicResultCents = managerialOperatingResultCents - economicCostOfTimeCents

        return ProfitabilityMetricResult(
            revenueReceivedCents = payments.sumOf { it.grossAmountCents },
            financialFeesCents = payments.sumOf { it.feeCents },
            netRevenueCents = netRevenueCents,
            directCostsCents = directCostsCents,
            contributionMarginCents = contributionMarginCents,
            contributionMarginPercent = contributionMarginPercent,
            allocatedIndirectCostsCents = allocatedIndirectCents,
            managerialOperatingResultCents = managerialOperatingResultCents,
            dedicatedMinutes = totalDedicatedMinutes,
            dedicatedHours = dedicatedHours,
            revenuePerHourCents = revenuePerHourCents,
            economicHourlyRateCents = hourlyEconomicRateCents,
            estimatedEconomicCostOfTimeCents = economicCostOfTimeCents,
            estimatedEconomicResultCents = economicResultCents,
            economicCostOfTimeCents = economicCostOfTimeCents,
            economicResultCents = economicResultCents,
            activeClientsCount = clients.count { !it.isArchived },
            activeContractsCount = contracts.count { it.status.name == "ATIVO" }
        )
    }

    /**
     * Calcula o resumo de rentabilidade por serviço.
     */
    fun calculateServiceSummaries(
        services: List<ServiceEntity>,
        contracts: List<ContractEntity>,
        receivables: List<ReceivableEntity>,
        payments: List<PaymentEntity>,
        costAllocations: List<CostAllocationEntity>,
        timeTrackings: List<TimeTrackingEntity>,
        hourlyEconomicRateCents: Long = 5000L
    ): List<ServiceProfitabilitySummary> {
        val paymentMap = payments.associateBy { it.receivableId }

        return services.map { service ->
            val serviceContracts = contracts.filter { it.serviceId == service.id }
            val contractIds = serviceContracts.map { it.id }.toSet()

            val serviceReceivables = receivables.filter { it.contractId in contractIds }
            val netRevenueCents = serviceReceivables.sumOf { rec ->
                val p = paymentMap[rec.id]
                if (p != null) p.grossAmountCents - p.feeCents else if (rec.status == ReceivableStatus.QUITADA || rec.status.name == "RECEBIDO") rec.netValueCents else 0L
            }

            val directCostsCents = costAllocations
                .filter {
                    (it.serviceId == service.id || it.contractId in contractIds) &&
                            (it.allocationType != CostAllocationType.INDIRETO_RATEADO && it.allocationType != CostAllocationType.INDIRETO)
                }
                .sumOf { it.allocatedAmountCents }

            val indirectCostsCents = costAllocations
                .filter {
                    it.contractId in contractIds &&
                            (it.allocationType == CostAllocationType.INDIRETO_RATEADO || it.allocationType == CostAllocationType.INDIRETO)
                }
                .sumOf { it.allocatedAmountCents }

            val contributionMarginCents = netRevenueCents - directCostsCents
            val marginPercent = if (netRevenueCents > 0) {
                (contributionMarginCents.toDouble() / netRevenueCents.toDouble()) * 100.0
            } else 0.0

            val operatingResultCents = contributionMarginCents - indirectCostsCents

            val dedicatedMinutes = timeTrackings
                .filter { it.serviceId == service.id || it.contractId in contractIds }
                .sumOf { it.durationMinutes }
            val dedicatedHours = dedicatedMinutes / 60.0

            val revenuePerHourCents = if (dedicatedHours > 0.0) {
                (netRevenueCents / dedicatedHours).toLong()
            } else 0L

            val economicCostOfTimeCents = (dedicatedHours * hourlyEconomicRateCents).toLong()
            val economicResultCents = operatingResultCents - economicCostOfTimeCents

            ServiceProfitabilitySummary(
                serviceId = service.id,
                serviceName = service.name,
                serviceCategory = service.category,
                category = service.category.name,
                activeContractsCount = serviceContracts.count { it.status.name == "ATIVO" },
                revenueReceivedCents = netRevenueCents,
                netRevenueCents = netRevenueCents,
                directCostsCents = directCostsCents,
                contributionMarginCents = contributionMarginCents,
                contributionMarginPercent = marginPercent,
                allocatedIndirectCostsCents = indirectCostsCents,
                managerialOperatingResultCents = operatingResultCents,
                dedicatedMinutes = dedicatedMinutes,
                dedicatedHours = dedicatedHours,
                revenuePerHourCents = revenuePerHourCents,
                economicCostOfTimeCents = economicCostOfTimeCents,
                economicResultCents = economicResultCents
            )
        }.sortedByDescending { it.contributionMarginCents }
    }

    /**
     * Calcula a análise financeira individual de cada aluno.
     */
    fun calculateStudentSummaries(
        clients: List<ClientEntity>,
        contracts: List<ContractEntity>,
        receivables: List<ReceivableEntity>,
        payments: List<PaymentEntity>,
        costAllocations: List<CostAllocationEntity>,
        timeTrackings: List<TimeTrackingEntity>,
        hourlyEconomicRateCents: Long = 5000L
    ): List<StudentProfitabilitySummary> {
        val paymentMap = payments.associateBy { it.receivableId }

        return clients.map { client ->
            val clientContracts = contracts.filter { it.clientId == client.id }
            val contractIds = clientContracts.map { it.id }.toSet()

            val clientReceivables = receivables.filter { it.clientId == client.id || it.contractId in contractIds }

            val contractedRevenueCents = clientReceivables.sumOf { it.originalValueCents }
            val receivedRevenueCents = clientReceivables.sumOf { rec ->
                val p = paymentMap[rec.id]
                if (p != null) p.grossAmountCents else if (rec.status == ReceivableStatus.QUITADA || rec.status.name == "RECEBIDO") rec.receivedValueCents.takeIf { it > 0 } ?: rec.originalValueCents else 0L
            }
            val discountsCents = clientReceivables.sumOf { it.discountCents }
            val feesCents = clientReceivables.sumOf { rec ->
                val p = paymentMap[rec.id]
                p?.feeCents ?: 0L
            }
            val pendingRevenueCents = clientReceivables
                .filter { it.status == ReceivableStatus.PENDENTE || it.status.name == "ATRASADO" }
                .sumOf { it.netValueCents }

            val directCostsCents = costAllocations
                .filter {
                    (it.clientId == client.id || it.contractId in contractIds) &&
                            (it.allocationType != CostAllocationType.INDIRETO_RATEADO && it.allocationType != CostAllocationType.INDIRETO)
                }
                .sumOf { it.allocatedAmountCents }

            val allocatedIndirectCostsCents = costAllocations
                .filter {
                    (it.clientId == client.id || it.contractId in contractIds) &&
                            (it.allocationType == CostAllocationType.INDIRETO_RATEADO || it.allocationType == CostAllocationType.INDIRETO)
                }
                .sumOf { it.allocatedAmountCents }

            val netEffectiveRevenueCents = receivedRevenueCents - feesCents
            val contributionMarginCents = netEffectiveRevenueCents - directCostsCents
            val marginPercent = if (netEffectiveRevenueCents > 0) {
                (contributionMarginCents.toDouble() / netEffectiveRevenueCents.toDouble()) * 100.0
            } else 0.0

            val operatingResultCents = contributionMarginCents - allocatedIndirectCostsCents

            val dedicatedMinutes = timeTrackings
                .filter { it.clientId == client.id || it.contractId in contractIds }
                .sumOf { it.durationMinutes }
            val dedicatedHours = dedicatedMinutes / 60.0

            val revenuePerHourCents = if (dedicatedHours > 0.0) {
                (netEffectiveRevenueCents / dedicatedHours).toLong()
            } else 0L

            val economicCostOfTimeCents = (dedicatedHours * hourlyEconomicRateCents).toLong()
            val economicResultCents = operatingResultCents - economicCostOfTimeCents

            StudentProfitabilitySummary(
                clientId = client.id,
                clientName = client.fullName,
                activeContractsCount = clientContracts.count { it.status.name == "ATIVO" },
                contractedServices = clientContracts.map { it.serviceId },
                revenueContractedCents = contractedRevenueCents,
                contractedRevenueCents = contractedRevenueCents,
                revenueReceivedCents = receivedRevenueCents,
                receivedRevenueCents = receivedRevenueCents,
                revenuePendingCents = pendingRevenueCents,
                pendingRevenueCents = pendingRevenueCents,
                discountsCents = discountsCents,
                financialFeesCents = feesCents,
                directCostsCents = directCostsCents,
                contributionMarginCents = contributionMarginCents,
                contributionMarginPercent = marginPercent,
                allocatedIndirectCostsCents = allocatedIndirectCostsCents,
                managerialOperatingResultCents = operatingResultCents,
                dedicatedMinutes = dedicatedMinutes,
                dedicatedHours = dedicatedHours,
                revenuePerHourCents = revenuePerHourCents,
                economicCostOfTimeCents = economicCostOfTimeCents,
                estimatedEconomicResultCents = economicResultCents,
                economicResultCents = economicResultCents
            )
        }.sortedByDescending { it.contributionMarginCents }
    }
}
