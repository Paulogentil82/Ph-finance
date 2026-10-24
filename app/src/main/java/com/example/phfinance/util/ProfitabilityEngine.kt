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
     * Rejeita valores negativos estritamente.
     */
    fun validateAllocationLimit(
        expense: ExpenseEntity,
        existingAllocations: List<CostAllocationEntity>,
        newAllocationCents: Long,
        excludeAllocationId: String? = null
    ): Boolean {
        require(newAllocationCents >= 0L) { "O valor da alocação não pode ser negativo." }
        require(expense.valueCents >= 0L) { "O valor da despesa original não pode ser negativo." }

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
     * Trata QUANTIDADE_SESSOES utilizando a quantidade real de sessões por contrato.
     */
    fun calculateApportionmentPreview(
        method: ApportionmentMethod,
        totalIndirectCents: Long,
        activeContracts: List<ContractEntity>,
        clients: List<ClientEntity>,
        timeTrackings: List<TimeTrackingEntity>,
        receivablesInPeriod: List<ReceivableEntity>,
        fixedAmountPerContractCents: Long = 0L,
        contractSessionCounts: Map<String, Int> = emptyMap(),
        services: List<ServiceEntity> = emptyList()
    ): List<ApportionmentPreviewItem> {
        if (activeContracts.isEmpty() || totalIndirectCents <= 0L) return emptyList()

        val clientMap = clients.associateBy { it.id }
        val serviceMap = services.associateBy { it.id }

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

            ApportionmentMethod.QUANTIDADE_SESSOES -> {
                val contractSessionsMap = activeContracts.associateWith { contract ->
                    contractSessionCounts[contract.id] ?: serviceMap[contract.serviceId]?.sessionCount ?: 1
                }
                val totalSessions = contractSessionsMap.values.sum().toDouble()
                if (totalSessions <= 0.0) {
                    divideEqually(activeContracts, clientMap, totalIndirectCents)
                } else {
                    var accumulated = 0L
                    activeContracts.mapIndexed { index, contract ->
                        val sessions = contractSessionsMap[contract] ?: 1
                        val ratio = sessions.toDouble() / totalSessions
                        val allocated = if (index == activeContracts.lastIndex) {
                            totalIndirectCents - accumulated
                        } else {
                            (totalIndirectCents * ratio).toLong()
                        }
                        accumulated += allocated
                        ApportionmentPreviewItem(
                            targetId = contract.id,
                            targetLabel = clientMap[contract.clientId]?.fullName ?: "Aluno",
                            baseValue = sessions.toDouble(),
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

            ApportionmentMethod.QUANTIDADE_CLIENTES -> {
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
        if (count == 0) return emptyList()
        val baseShare = totalIndirectCents / count
        var remainder = totalIndirectCents % count

        return activeContracts.mapIndexed { index, contract ->
            val share = baseShare + if (index < remainder) 1L else 0L
            val pct = (share.toDouble() / totalIndirectCents.toDouble()) * 100.0
            ApportionmentPreviewItem(
                targetId = contract.id,
                targetLabel = clientMap[contract.clientId]?.fullName ?: "Aluno",
                baseValue = share.toDouble(),
                basePercentage = pct,
                allocatedAmountCents = share,
                contractId = contract.id,
                clientId = contract.clientId,
                clientName = clientMap[contract.clientId]?.fullName ?: "Aluno",
                serviceId = contract.serviceId,
                percentage = pct,
                allocatedCents = share
            )
        }
    }

    /**
     * Calcula os indicadores gerais (DRE Gerencial / Visão Geral) respeitando competência e múltiplos pagamentos.
     */
    fun calculateOverviewMetrics(
        receivables: List<ReceivableEntity>,
        payments: List<PaymentEntity>,
        expenses: List<ExpenseEntity>,
        costAllocations: List<CostAllocationEntity>,
        timeTrackings: List<TimeTrackingEntity>,
        clients: List<ClientEntity>,
        contracts: List<ContractEntity>,
        hourlyEconomicRateCents: Long = 5000L,
        competenceFilter: String? = null
    ): ProfitabilityMetricResult {
        val filteredReceivables = if (competenceFilter != null) {
            receivables.filter { it.competence == competenceFilter }
        } else receivables

        val paymentMap = payments.groupBy { it.receivableId }

        var netRevenueCents = 0L
        if (filteredReceivables.isNotEmpty()) {
            filteredReceivables.forEach { rec ->
                val pList = paymentMap[rec.id] ?: emptyList()
                if (pList.isNotEmpty()) {
                    val paidGross = pList.sumOf { it.grossAmountCents }
                    val paidFee = pList.sumOf { it.feeCents }
                    netRevenueCents += (paidGross - paidFee)
                } else if (rec.status == ReceivableStatus.QUITADA || rec.status.name == "RECEBIDO") {
                    netRevenueCents += rec.netValueCents
                }
            }
        } else {
            payments.forEach { p ->
                netRevenueCents += (p.grossAmountCents - p.feeCents)
            }
        }

        val filteredAllocations = if (competenceFilter != null) {
            costAllocations.filter { it.competence == competenceFilter }
        } else costAllocations

        val directCostsCents = filteredAllocations
            .filter { it.allocationType != CostAllocationType.INDIRETO_RATEADO && it.allocationType != CostAllocationType.INDIRETO }
            .sumOf { it.allocatedAmountCents }

        val contributionMarginCents = netRevenueCents - directCostsCents
        val contributionMarginPercent = if (netRevenueCents > 0) {
            (contributionMarginCents.toDouble() / netRevenueCents.toDouble()) * 100.0
        } else 0.0

        val indirectCostsCents = filteredAllocations
            .filter { it.allocationType == CostAllocationType.INDIRETO_RATEADO || it.allocationType == CostAllocationType.INDIRETO }
            .sumOf { it.allocatedAmountCents }

        val managerialOperatingResultCents = contributionMarginCents - indirectCostsCents

        val filteredTime = if (competenceFilter != null) {
            timeTrackings.filter { it.competence == competenceFilter }
        } else timeTrackings

        val dedicatedMinutes = filteredTime.sumOf { it.durationMinutes }
        val dedicatedHours = dedicatedMinutes / 60.0

        val revenuePerHourCents = if (dedicatedHours > 0.0) {
            (netRevenueCents / dedicatedHours).toLong()
        } else 0L

        val economicCostOfTimeCents = (dedicatedHours * hourlyEconomicRateCents).toLong()
        val economicResultCents = managerialOperatingResultCents - economicCostOfTimeCents

        return ProfitabilityMetricResult(
            netRevenueCents = netRevenueCents,
            directCostsCents = directCostsCents,
            contributionMarginCents = contributionMarginCents,
            contributionMarginPercent = contributionMarginPercent,
            allocatedIndirectCostsCents = indirectCostsCents,
            managerialOperatingResultCents = managerialOperatingResultCents,
            dedicatedMinutes = dedicatedMinutes,
            dedicatedHours = dedicatedHours,
            revenuePerHourCents = revenuePerHourCents,
            economicCostOfTimeCents = economicCostOfTimeCents,
            economicResultCents = economicResultCents
        )
    }

    /**
     * Calcula a rentabilidade por serviço suportando múltiplos pagamentos.
     */
    fun calculateServiceSummaries(
        services: List<ServiceEntity>,
        contracts: List<ContractEntity>,
        receivables: List<ReceivableEntity>,
        payments: List<PaymentEntity>,
        costAllocations: List<CostAllocationEntity>,
        timeTrackings: List<TimeTrackingEntity>,
        hourlyEconomicRateCents: Long = 5000L,
        competenceFilter: String? = null
    ): List<ServiceProfitabilitySummary> {
        val filteredReceivables = if (competenceFilter != null) {
            receivables.filter { it.competence == competenceFilter }
        } else receivables

        val paymentMap = payments.groupBy { it.receivableId }

        return services.map { service ->
            val serviceContracts = contracts.filter { it.serviceId == service.id }
            val contractIds = serviceContracts.map { it.id }.toSet()

            val serviceReceivables = filteredReceivables.filter { it.contractId in contractIds }
            var netRevenueCents = 0L
            serviceReceivables.forEach { rec ->
                val pList = paymentMap[rec.id] ?: emptyList()
                if (pList.isNotEmpty()) {
                    val paidGross = pList.sumOf { it.grossAmountCents }
                    val paidFee = pList.sumOf { it.feeCents }
                    netRevenueCents += (paidGross - paidFee)
                } else if (rec.status == ReceivableStatus.QUITADA || rec.status.name == "RECEBIDO") {
                    netRevenueCents += rec.netValueCents
                }
            }

            val filteredAllocations = if (competenceFilter != null) {
                costAllocations.filter { it.competence == competenceFilter }
            } else costAllocations

            val directCostsCents = filteredAllocations
                .filter {
                    (it.serviceId == service.id || it.contractId in contractIds) &&
                            (it.allocationType != CostAllocationType.INDIRETO_RATEADO && it.allocationType != CostAllocationType.INDIRETO)
                }
                .sumOf { it.allocatedAmountCents }

            val indirectCostsCents = filteredAllocations
                .filter {
                    (it.serviceId == service.id || it.contractId in contractIds) &&
                            (it.allocationType == CostAllocationType.INDIRETO_RATEADO || it.allocationType == CostAllocationType.INDIRETO)
                }
                .sumOf { it.allocatedAmountCents }

            val contributionMarginCents = netRevenueCents - directCostsCents
            val marginPercent = if (netRevenueCents > 0) {
                (contributionMarginCents.toDouble() / netRevenueCents.toDouble()) * 100.0
            } else 0.0

            val operatingResultCents = contributionMarginCents - indirectCostsCents

            val filteredTime = if (competenceFilter != null) {
                timeTrackings.filter { it.competence == competenceFilter }
            } else timeTrackings

            val dedicatedMinutes = filteredTime
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
     * Calcula a análise financeira individual de cada aluno suportando múltiplos pagamentos.
     */
    fun calculateStudentSummaries(
        clients: List<ClientEntity>,
        contracts: List<ContractEntity>,
        receivables: List<ReceivableEntity>,
        payments: List<PaymentEntity>,
        costAllocations: List<CostAllocationEntity>,
        timeTrackings: List<TimeTrackingEntity>,
        hourlyEconomicRateCents: Long = 5000L,
        competenceFilter: String? = null
    ): List<StudentProfitabilitySummary> {
        val filteredReceivables = if (competenceFilter != null) {
            receivables.filter { it.competence == competenceFilter }
        } else receivables

        val paymentMap = payments.groupBy { it.receivableId }

        return clients.map { client ->
            val clientContracts = contracts.filter { it.clientId == client.id }
            val contractIds = clientContracts.map { it.id }.toSet()

            val clientReceivables = filteredReceivables.filter { it.clientId == client.id || it.contractId in contractIds }

            val contractedRevenueCents = clientReceivables.sumOf { it.originalValueCents }
            var receivedRevenueCents = 0L
            var feesCents = 0L
            clientReceivables.forEach { rec ->
                val pList = paymentMap[rec.id] ?: emptyList()
                if (pList.isNotEmpty()) {
                    receivedRevenueCents += pList.sumOf { it.grossAmountCents }
                    feesCents += pList.sumOf { it.feeCents }
                } else if (rec.status == ReceivableStatus.QUITADA || rec.status.name == "RECEBIDO") {
                    receivedRevenueCents += (rec.receivedValueCents.takeIf { it > 0 } ?: rec.originalValueCents)
                }
            }

            val discountsCents = clientReceivables.sumOf { it.discountCents }
            val pendingRevenueCents = clientReceivables
                .filter { it.status == ReceivableStatus.PENDENTE || it.status.name == "ATRASADO" }
                .sumOf { it.netValueCents }

            val filteredAllocations = if (competenceFilter != null) {
                costAllocations.filter { it.competence == competenceFilter }
            } else costAllocations

            val directCostsCents = filteredAllocations
                .filter {
                    (it.clientId == client.id || it.contractId in contractIds) &&
                            (it.allocationType != CostAllocationType.INDIRETO_RATEADO && it.allocationType != CostAllocationType.INDIRETO)
                }
                .sumOf { it.allocatedAmountCents }

            val allocatedIndirectCostsCents = filteredAllocations
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

            val filteredTime = if (competenceFilter != null) {
                timeTrackings.filter { it.competence == competenceFilter }
            } else timeTrackings

            val dedicatedMinutes = filteredTime
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
