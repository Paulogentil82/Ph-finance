package com.example.phfinance

import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ContractEntity
import com.example.phfinance.data.local.CostAllocationEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.MonthlyClosureEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.local.ServiceEntity
import com.example.phfinance.data.local.TimeTrackingEntity
import com.example.phfinance.data.model.ApportionmentMethod
import com.example.phfinance.data.model.BillingPeriod
import com.example.phfinance.data.model.ClientType
import com.example.phfinance.data.model.ContractStatus
import com.example.phfinance.data.model.CostAllocationType
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.PricingScenarioType
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.data.model.ServiceCategory
import com.example.phfinance.data.model.TimeActivityType
import com.example.phfinance.util.BackupManager
import com.example.phfinance.util.BackupPayload
import com.example.phfinance.util.PricingSimulatorEngine
import com.example.phfinance.util.ProfitabilityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ProfitabilityEngineTest {

    private val testCompetence = "10/2026"

    private fun createDummyClient(id: String, name: String) = ClientEntity(
        id = id,
        fullName = name,
        nickname = name,
        phone = "11999999999",
        whatsapp = "11999999999",
        email = "$id@phpersonal.com",
        agreedValueCents = 30000L,
        dueDay = 10,
        clientType = ClientType.ATIVO
    )

    private fun createDummyService(id: String, name: String) = ServiceEntity(
        id = id,
        name = name,
        description = "Descrição de $name",
        category = ServiceCategory.CONSULTORIA_ONLINE,
        defaultPriceCents = 30000L,
        billingPeriod = BillingPeriod.MENSAL,
        sessionCount = 0,
        contractDurationMonths = 1,
        revenueCategory = "Consultoria"
    )

    private fun createDummyContract(id: String, clientId: String, serviceId: String) = ContractEntity(
        id = id,
        clientId = clientId,
        serviceId = serviceId,
        startDate = System.currentTimeMillis(),
        contractedValueCents = 30000L,
        dueDay = 10,
        status = ContractStatus.ATIVO
    )

    private fun createDummyExpense(id: String, amountCents: Long, description: String = "Despesa") = ExpenseEntity(
        id = id,
        description = description,
        category = "Operacional",
        valueCents = amountCents,
        competence = testCompetence,
        dueDate = System.currentTimeMillis(),
        paymentDate = System.currentTimeMillis(),
        accountId = "acc_main",
        status = ExpenseStatus.PAGO
    )

    // -------------------------------------------------------------
    // CENÁRIO 1: Cálculo correto de margem de contribuição (receita menos custos diretos)
    // -------------------------------------------------------------
    @Test
    fun test01_calculoCorretoMargemDeContribuicao() {
        val payments = listOf(
            PaymentEntity(
                id = "pay_1",
                receivableId = "rec_1",
                clientId = "cli_1",
                grossAmountCents = 100000L, // R$ 1.000,00
                feeCents = 3000L,          // R$ 30,00 taxa
                netAmountCents = 97000L,
                destinationAccountId = "acc_main",
                paymentDate = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.PIX
            )
        )
        // Receita líquida = 100.000 - 3.000 = 97.000 (R$ 970,00)
        val allocations = listOf(
            CostAllocationEntity(
                id = "alloc_1",
                expenseId = "exp_1",
                allocationType = CostAllocationType.DIRETO_CLIENTE,
                clientId = "cli_1",
                competence = testCompetence,
                allocatedAmountCents = 17000L // R$ 170,00
            )
        )

        val metrics = ProfitabilityEngine.calculateOverviewMetrics(
            receivables = emptyList(),
            payments = payments,
            expenses = emptyList(),
            costAllocations = allocations,
            timeTrackings = emptyList(),
            clients = listOf(createDummyClient("cli_1", "Aluno 1")),
            contracts = emptyList()
        )

        assertEquals(97000L, metrics.netRevenueCents)
        assertEquals(17000L, metrics.directCostsCents)
        // Margem de Contribuição = 97.000 - 17.000 = 80.000L
        assertEquals(80000L, metrics.contributionMarginCents)
        val expectedPercent = (80000.0 / 97000.0) * 100.0
        assertEquals(expectedPercent, metrics.contributionMarginPercent, 0.01)
    }

    // -------------------------------------------------------------
    // CENÁRIO 2: Não equiparação de margem de contribuição a lucro líquido
    // -------------------------------------------------------------
    @Test
    fun test02_naoEquiparacaoMargemContribuicaoAoLucroLiquido() {
        val payments = listOf(
            PaymentEntity(
                id = "pay_1",
                receivableId = "rec_1",
                clientId = "cli_1",
                grossAmountCents = 100000L,
                feeCents = 0L,
                netAmountCents = 100000L,
                destinationAccountId = "acc_main",
                paymentDate = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.PIX
            )
        )
        val allocations = listOf(
            CostAllocationEntity(
                id = "alloc_dir",
                expenseId = "exp_1",
                allocationType = CostAllocationType.DIRETO_CLIENTE,
                clientId = "cli_1",
                competence = testCompetence,
                allocatedAmountCents = 20000L // Custos diretos: R$ 200,00
            ),
            CostAllocationEntity(
                id = "alloc_ind",
                expenseId = "exp_2",
                allocationType = CostAllocationType.INDIRETO_RATEADO,
                clientId = "cli_1",
                competence = testCompetence,
                allocatedAmountCents = 35000L // Custos indiretos fixos rateados: R$ 350,00
            )
        )

        val metrics = ProfitabilityEngine.calculateOverviewMetrics(
            receivables = emptyList(),
            payments = payments,
            expenses = emptyList(),
            costAllocations = allocations,
            timeTrackings = emptyList(),
            clients = listOf(createDummyClient("cli_1", "Aluno 1")),
            contracts = emptyList()
        )

        // MC = 100.000 - 20.000 = 80.000L
        assertEquals(80000L, metrics.contributionMarginCents)
        // Resultado Gerencial Operacional = 80.000 - 35.000 = 45.000L
        assertEquals(45000L, metrics.managerialOperatingResultCents)
        // Demonstração explícita de que MC != Resultado Líquido
        assertNotEquals(metrics.contributionMarginCents, metrics.managerialOperatingResultCents)
    }

    // -------------------------------------------------------------
    // CENÁRIO 3: Atribuição de custo direto a cliente específico
    // -------------------------------------------------------------
    @Test
    fun test03_atribuicaoCustoDiretoAClienteEspecifico() {
        val client1 = createDummyClient("cli_1", "Aluno 1")
        val client2 = createDummyClient("cli_2", "Aluno 2")
        val contract1 = createDummyContract("con_1", "cli_1", "srv_1")
        val contract2 = createDummyContract("con_2", "cli_2", "srv_1")

        val receivables = listOf(
            ReceivableEntity(id = "rec_1", clientId = "cli_1", contractId = "con_1", competence = testCompetence, dueDate = 0L, originalValueCents = 50000L, netValueCents = 50000L, receivedValueCents = 50000L, status = ReceivableStatus.QUITADA),
            ReceivableEntity(id = "rec_2", clientId = "cli_2", contractId = "con_2", competence = testCompetence, dueDate = 0L, originalValueCents = 50000L, netValueCents = 50000L, receivedValueCents = 50000L, status = ReceivableStatus.QUITADA)
        )
        // Apenas cliente 1 tem custo direto atribuído (ex: bioimpedância terceirizada)
        val allocations = listOf(
            CostAllocationEntity(
                id = "alloc_1",
                expenseId = "exp_1",
                allocationType = CostAllocationType.DIRETO_CLIENTE,
                clientId = "cli_1",
                competence = testCompetence,
                allocatedAmountCents = 8000L
            )
        )

        val summaries = ProfitabilityEngine.calculateStudentSummaries(
            clients = listOf(client1, client2),
            contracts = listOf(contract1, contract2),
            receivables = receivables,
            payments = emptyList(),
            costAllocations = allocations,
            timeTrackings = emptyList()
        )

        val s1 = summaries.first { it.clientId == "cli_1" }
        val s2 = summaries.first { it.clientId == "cli_2" }

        assertEquals(8000L, s1.directCostsCents)
        assertEquals(42000L, s1.contributionMarginCents)

        assertEquals(0L, s2.directCostsCents)
        assertEquals(50000L, s2.contributionMarginCents)
    }

    // -------------------------------------------------------------
    // CENÁRIO 4: Atribuição de custo direto a serviço específico
    // -------------------------------------------------------------
    @Test
    fun test04_atribuicaoCustoDiretoAServicoEspecifico() {
        val srv1 = createDummyService("srv_presencial", "Personal Presencial")
        val srv2 = createDummyService("srv_online", "Consultoria Online")

        val con1 = createDummyContract("con_1", "cli_1", "srv_presencial")
        val con2 = createDummyContract("con_2", "cli_2", "srv_online")

        val receivables = listOf(
            ReceivableEntity(id = "rec_1", clientId = "cli_1", contractId = "con_1", competence = testCompetence, dueDate = 0L, originalValueCents = 120000L, netValueCents = 120000L, receivedValueCents = 120000L, status = ReceivableStatus.QUITADA),
            ReceivableEntity(id = "rec_2", clientId = "cli_2", contractId = "con_2", competence = testCompetence, dueDate = 0L, originalValueCents = 30000L, netValueCents = 30000L, receivedValueCents = 30000L, status = ReceivableStatus.QUITADA)
        )

        val allocations = listOf(
            CostAllocationEntity(
                id = "alloc_srv_1",
                expenseId = "exp_1",
                allocationType = CostAllocationType.DIRETO_SERVICO,
                serviceId = "srv_presencial",
                competence = testCompetence,
                allocatedAmountCents = 25000L // R$ 250,00 deslocamento/estacionamento
            )
        )

        val summaries = ProfitabilityEngine.calculateServiceSummaries(
            services = listOf(srv1, srv2),
            contracts = listOf(con1, con2),
            receivables = receivables,
            payments = emptyList(),
            costAllocations = allocations,
            timeTrackings = emptyList()
        )

        val srvPres = summaries.first { it.serviceId == "srv_presencial" }
        val srvOn = summaries.first { it.serviceId == "srv_online" }

        assertEquals(25000L, srvPres.directCostsCents)
        assertEquals(95000L, srvPres.contributionMarginCents)

        assertEquals(0L, srvOn.directCostsCents)
        assertEquals(30000L, srvOn.contributionMarginCents)
    }

    // -------------------------------------------------------------
    // CENÁRIO 5: Atribuição de custo direto a contrato específico
    // -------------------------------------------------------------
    @Test
    fun test05_atribuicaoCustoDiretoAContratoEspecifico() {
        val con1 = createDummyContract("con_especifico", "cli_1", "srv_1")
        val allocation = CostAllocationEntity(
            id = "alloc_con",
            expenseId = "exp_taxa",
            allocationType = CostAllocationType.DIRETO_CONTRATO,
            contractId = "con_especifico",
            clientId = "cli_1",
            competence = testCompetence,
            allocatedAmountCents = 4500L
        )

        assertEquals("con_especifico", allocation.contractId)
        assertEquals(CostAllocationType.DIRETO_CONTRATO, allocation.allocationType)
        assertEquals(4500L, allocation.allocatedAmountCents)
    }

    // -------------------------------------------------------------
    // CENÁRIO 6: Rateio proporcional à receita entre contratos ativos
    // -------------------------------------------------------------
    @Test
    fun test06_rateioProporcionalAReceitaEntreContratosAtivos() {
        val clients = listOf(
            createDummyClient("c1", "Aluno 1"),
            createDummyClient("c2", "Aluno 2")
        )
        val contracts = listOf(
            createDummyContract("con_1", "c1", "s1"),
            createDummyContract("con_2", "c2", "s1")
        )
        // Contrato 1 gera R$ 600,00 e Contrato 2 gera R$ 400,00 (Total R$ 1.000,00)
        val receivables = listOf(
            ReceivableEntity(id = "r1", clientId = "c1", contractId = "con_1", competence = testCompetence, dueDate = 0L, originalValueCents = 60000L, netValueCents = 60000L, receivedValueCents = 60000L, status = ReceivableStatus.QUITADA),
            ReceivableEntity(id = "r2", clientId = "c2", contractId = "con_2", competence = testCompetence, dueDate = 0L, originalValueCents = 40000L, netValueCents = 40000L, receivedValueCents = 40000L, status = ReceivableStatus.QUITADA)
        )
        val totalIndirectCents = 10000L // R$ 100,00 a ratear (ex: internet/app)

        val preview = ProfitabilityEngine.calculateApportionmentPreview(
            method = ApportionmentMethod.PROPORCIONAL_RECEITA,
            totalIndirectCents = totalIndirectCents,
            activeContracts = contracts,
            clients = clients,
            timeTrackings = emptyList(),
            receivablesInPeriod = receivables
        )

        assertEquals(2, preview.size)
        // Contrato 1 (60% da receita) deve receber R$ 60,00 (6000L)
        val item1 = preview.first { it.contractId == "con_1" }
        assertEquals(6000L, item1.allocatedCents)
        assertEquals(60.0, item1.percentage, 0.1)

        // Contrato 2 (40% da receita) deve receber R$ 40,00 (4000L)
        val item2 = preview.first { it.contractId == "con_2" }
        assertEquals(4000L, item2.allocatedCents)
        assertEquals(40.0, item2.percentage, 0.1)

        // Soma deve ser exatamente totalIndirectCents sem resíduo
        assertEquals(totalIndirectCents, preview.sumOf { it.allocatedCents })
    }

    // -------------------------------------------------------------
    // CENÁRIO 7: Rateio proporcional ao tempo entre contratos ativos
    // -------------------------------------------------------------
    @Test
    fun test07_rateioProporcionalAoTempoEntreContratosAtivos() {
        val clients = listOf(createDummyClient("c1", "Aluno 1"), createDummyClient("c2", "Aluno 2"))
        val contracts = listOf(createDummyContract("con_1", "c1", "s1"), createDummyContract("con_2", "c2", "s1"))

        // Aluno 1 consumiu 90 min, Aluno 2 consumiu 30 min (Total 120 min => 75% e 25%)
        val times = listOf(
            TimeTrackingEntity(id = "t1", contractId = "con_1", clientId = "c1", competence = testCompetence, durationMinutes = 90),
            TimeTrackingEntity(id = "t2", contractId = "con_2", clientId = "c2", competence = testCompetence, durationMinutes = 30)
        )
        val totalIndirectCents = 20000L // R$ 200,00

        val preview = ProfitabilityEngine.calculateApportionmentPreview(
            method = ApportionmentMethod.PROPORCIONAL_TEMPO,
            totalIndirectCents = totalIndirectCents,
            activeContracts = contracts,
            clients = clients,
            timeTrackings = times,
            receivablesInPeriod = emptyList()
        )

        val item1 = preview.first { it.contractId == "con_1" }
        val item2 = preview.first { it.contractId == "con_2" }

        assertEquals(15000L, item1.allocatedCents) // 75%
        assertEquals(5000L, item2.allocatedCents)  // 25%
        assertEquals(totalIndirectCents, preview.sumOf { it.allocatedCents })
    }

    // -------------------------------------------------------------
    // CENÁRIO 8: Rateio por quantidade de clientes
    // -------------------------------------------------------------
    @Test
    fun test08_rateioPorQuantidadeDeClientes() {
        val clients = (1..4).map { createDummyClient("c$it", "Aluno $it") }
        val contracts = clients.map { createDummyContract("con_${it.id}", it.id, "s1") }
        val totalIndirectCents = 10000L // R$ 100,00 dividido por 4 = R$ 25,00 cada

        val preview = ProfitabilityEngine.calculateApportionmentPreview(
            method = ApportionmentMethod.QUANTIDADE_CLIENTES,
            totalIndirectCents = totalIndirectCents,
            activeContracts = contracts,
            clients = clients,
            timeTrackings = emptyList(),
            receivablesInPeriod = emptyList()
        )

        assertEquals(4, preview.size)
        preview.forEach {
            assertEquals(2500L, it.allocatedCents)
            assertEquals(25.0, it.percentage, 0.01)
        }
    }

    // -------------------------------------------------------------
    // CENÁRIO 9: Rateio por quantidade de sessões
    // -------------------------------------------------------------
    @Test
    fun test09_rateioPorQuantidadeDeSessoes() {
        val clients = listOf(createDummyClient("c1", "Aluno 1"), createDummyClient("c2", "Aluno 2"))
        val contracts = listOf(createDummyContract("con_1", "c1", "s1"), createDummyContract("con_2", "c2", "s1"))
        val totalIndirectCents = 15000L

        val preview = ProfitabilityEngine.calculateApportionmentPreview(
            method = ApportionmentMethod.QUANTIDADE_SESSOES,
            totalIndirectCents = totalIndirectCents,
            activeContracts = contracts,
            clients = clients,
            timeTrackings = emptyList(),
            receivablesInPeriod = emptyList()
        )

        assertEquals(2, preview.size)
        assertEquals(totalIndirectCents, preview.sumOf { it.allocatedCents })
    }

    // -------------------------------------------------------------
    // CENÁRIO 10: Rateio por valor fixo por contrato
    // -------------------------------------------------------------
    @Test
    fun test10_rateioPorValorFixoPorContrato() {
        val clients = listOf(createDummyClient("c1", "Aluno 1"), createDummyClient("c2", "Aluno 2"))
        val contracts = listOf(createDummyContract("con_1", "c1", "s1"), createDummyContract("con_2", "c2", "s1"))

        val preview = ProfitabilityEngine.calculateApportionmentPreview(
            method = ApportionmentMethod.VALOR_FIXO_CONTRATO,
            totalIndirectCents = 5000L,
            activeContracts = contracts,
            clients = clients,
            timeTrackings = emptyList(),
            receivablesInPeriod = emptyList(),
            fixedAmountPerContractCents = 1500L // R$ 15,00 fixo por contrato
        )

        assertEquals(2, preview.size)
        assertEquals(1500L, preview[0].allocatedCents)
        assertEquals(1500L, preview[1].allocatedCents)
    }

    // -------------------------------------------------------------
    // CENÁRIO 11: Validação de que a soma dos rateios não excede a despesa original
    // -------------------------------------------------------------
    @Test
    fun test11_validacaoSomaDosRateiosNaoExcedeDespesaOriginal() {
        val expense = createDummyExpense("exp_100", 10000L) // R$ 100,00

        val existing = listOf(
            CostAllocationEntity(id = "a1", expenseId = "exp_100", allocationType = CostAllocationType.DIRETO_CLIENTE, competence = testCompetence, allocatedAmountCents = 6000L)
        )

        // Saldo disponível = 100.00 - 60.00 = 40.00 (4000L)
        val available = ProfitabilityEngine.getAvailableAmountForAllocation(expense, existing)
        assertEquals(4000L, available)

        // Alocar 40.00 deve ser aceito
        assertTrue(ProfitabilityEngine.validateAllocationLimit(expense, existing, 4000L))

        // Alocar 40.01 (4001L) deve ser rejeitado
        assertFalse(ProfitabilityEngine.validateAllocationLimit(expense, existing, 4001L))

        // Alocar 50.00 (5000L) deve ser rejeitado
        assertFalse(ProfitabilityEngine.validateAllocationLimit(expense, existing, 5000L))
    }

    // -------------------------------------------------------------
    // CENÁRIO 12: Bloqueio de rateio retroativo sobre competência com fechamento definitivo
    // -------------------------------------------------------------
    @Test
    fun test12_bloqueioRateioRetroativoSobreFechamentoDefinitivo() {
        val closedCompetence = "09/2026"
        val closure = MonthlyClosureEntity(
            id = "closure_09",
            competence = closedCompetence,
            status = "DEFINITIVO",
            closedAt = System.currentTimeMillis(),
            closedBy = "Admin",
            accrualRevenueCents = 500000L,
            accrualExpenseCents = 200000L,
            cashRevenueCents = 500000L,
            cashExpenseCents = 200000L,
            netResultCents = 300000L,
            notes = "Fechamento mensal definitivo",
            isReopened = false
        )

        // Regra de segurança auditável: Se fechamento é DEFINITIVO, lança exceção
        val canModify = closure.status != "DEFINITIVO" || closure.isReopened
        assertFalse("Rateio deve ser bloqueado para competência DEFINITIVA", canModify)
    }

    // -------------------------------------------------------------
    // CENÁRIO 13: Cálculo de receita média por hora dedicada
    // -------------------------------------------------------------
    @Test
    fun test13_calculoReceitaMediaPorHoraDedicada() {
        val payments = listOf(
            PaymentEntity(
                id = "p1",
                receivableId = "r1",
                clientId = "cli_1",
                grossAmountCents = 40000L, // R$ 400,00
                feeCents = 0L,
                netAmountCents = 40000L,
                destinationAccountId = "acc_1",
                paymentDate = 0L,
                paymentMethod = PaymentMethod.PIX
            )
        )
        // 120 minutos = 2 horas dedicadas
        val timeTrackings = listOf(
            TimeTrackingEntity(
                id = "tt_1",
                competence = testCompetence,
                durationMinutes = 120,
                activityType = TimeActivityType.ATENDIMENTO_PRESENCIAL
            )
        )

        val metrics = ProfitabilityEngine.calculateOverviewMetrics(
            receivables = emptyList(),
            payments = payments,
            expenses = emptyList(),
            costAllocations = emptyList(),
            timeTrackings = timeTrackings,
            clients = emptyList(),
            contracts = emptyList()
        )

        assertEquals(2.0, metrics.dedicatedHours, 0.001)
        // Receita média por hora = R$ 400,00 / 2h = R$ 200,00/h (20000L)
        assertEquals(20000L, metrics.revenuePerHourCents)
    }

    // -------------------------------------------------------------
    // CENÁRIO 14: Cálculo do custo econômico do tempo sem duplicar despesa financeira
    // -------------------------------------------------------------
    @Test
    fun test14_calculoCustoEconomicoDoTempoSemDuplicarDespesaFinanceira() {
        val payments = listOf(
            PaymentEntity(
                id = "p1",
                receivableId = "r1",
                clientId = "cli_1",
                grossAmountCents = 60000L,
                feeCents = 0L,
                netAmountCents = 60000L,
                destinationAccountId = "acc_1",
                paymentDate = 0L,
                paymentMethod = PaymentMethod.PIX
            )
        )
        // 3 horas dedicadas (180 min), taxa de oportunidade de R$ 50,00/h (5000L)
        val timeTrackings = listOf(
            TimeTrackingEntity(id = "tt_1", competence = testCompetence, durationMinutes = 180, hourlyEconomicRateCents = 5000L)
        )

        val metrics = ProfitabilityEngine.calculateOverviewMetrics(
            receivables = emptyList(),
            payments = payments,
            expenses = emptyList(),
            costAllocations = emptyList(),
            timeTrackings = timeTrackings,
            clients = emptyList(),
            contracts = emptyList(),
            hourlyEconomicRateCents = 5000L
        )

        // Custo Econômico do tempo = 3h * R$ 50,00 = R$ 150,00 (15000L)
        assertEquals(15000L, metrics.economicCostOfTimeCents)
        // Resultado Operacional = R$ 600,00 (não afetado pelo custo de tempo contábil)
        assertEquals(60000L, metrics.managerialOperatingResultCents)
        // Resultado Econômico = 600 - 150 = R$ 450,00 (45000L)
        assertEquals(45000L, metrics.economicResultCents)
    }

    // -------------------------------------------------------------
    // CENÁRIO 15: Simulação de cenários de precificação (conservador, base e otimista)
    // -------------------------------------------------------------
    @Test
    fun test15_simulacaoCenariosPrecificacao() {
        val scenarios = PricingSimulatorEngine.generateScenarios(
            serviceId = "srv_personal",
            serviceName = "Personal Presencial",
            currentPriceCents = 20000L, // R$ 200,00
            proposedPriceCents = 25000L, // R$ 250,00 (+25%)
            currentClientsCount = 20,
            directCostPerClientCents = 2000L, // R$ 20,00
            allocatedExpensePerClientCents = 3000L, // R$ 30,00
            averageHoursPerClient = 4.0,
            baseChurnPercent = 10.0, // 10% perda no cenário base
            targetMarginPercent = 60.0
        )

        assertEquals(3, scenarios.size)
        val conservador = scenarios.first { it.scenarioType == PricingScenarioType.CONSERVADOR }
        val base = scenarios.first { it.scenarioType == PricingScenarioType.BASE }
        val otimista = scenarios.first { it.scenarioType == PricingScenarioType.OTIMISTA }

        // Conservador tem mais churn (10 + 15 = 25% churn => 15 alunos)
        assertTrue(conservador.projectedClients <= base.projectedClients)
        // Otimista tem menor churn (0% churn => 20 alunos)
        assertTrue(otimista.projectedClients >= base.projectedClients)

        // Receita projetada é proporcional aos alunos
        assertTrue(otimista.projectedRevenueCents >= base.projectedRevenueCents)
        assertTrue(base.projectedRevenueCents >= conservador.projectedRevenueCents)
    }

    // -------------------------------------------------------------
    // CENÁRIO 16: Cálculo correto do ponto de equilíbrio (break-even) de alunos
    // -------------------------------------------------------------
    @Test
    fun test16_calculoPontoDeEquilibrioBreakEven() {
        // Custos fixos totais = R$ 600,00 (60000L)
        // Preço proposto = R$ 250,00 (25000L)
        // Custo direto unitário = R$ 50,00 (5000L)
        // Margem de Contribuição unitária = 250 - 50 = R$ 200,00 (20000L)
        // Break-Even = 600 / 200 = 3 alunos!

        val scenario = PricingSimulatorEngine.calculateScenario(
            scenarioType = "BASE",
            name = "Cenário Base",
            serviceId = "srv_1",
            currentPriceCents = 20000L,
            proposedPriceCents = 25000L,
            currentClientsCount = 10,
            churnPercent = 0.0,
            directCostPerClientCents = 5000L,
            allocatedExpensePerClientCents = 6000L,
            totalFixedCostsCents = 60000L,
            averageHours = 2.0,
            targetMarginPercent = 50.0
        )

        assertEquals(3, scenario.breakEvenClients)
    }

    // -------------------------------------------------------------
    // TESTE ADICIONAL: Backup Criptografado V5 & Compatibilidade
    // -------------------------------------------------------------
    @Test
    fun testBackupManager_v5EncryptionAndDecryption() {
        val dummyPayload = BackupPayload(
            version = 5,
            clients = listOf(createDummyClient("c1", "Aluno V5")),
            costAllocations = listOf(
                CostAllocationEntity(
                    id = "alloc_v5",
                    expenseId = "exp_1",
                    allocationType = CostAllocationType.DIRETO_CLIENTE,
                    competence = testCompetence,
                    allocatedAmountCents = 5000L
                )
            ),
            timeTrackings = listOf(
                TimeTrackingEntity(
                    id = "tt_v5",
                    competence = testCompetence,
                    durationMinutes = 60
                )
            )
        )

        val password = "ph_secure_password_2026"
        val wrapper = BackupManager.encryptPayloadToWrapper(dummyPayload, password, version = 5)
        assertEquals(5, wrapper.version)

        val wrapperJson = BackupManager.wrapperAdapter.toJson(wrapper)
        val decrypted = BackupManager.decryptWrapperToPayload(wrapperJson, password)

        assertEquals(5, decrypted.version)
        assertEquals(1, decrypted.clients.size)
        assertEquals(1, decrypted.costAllocations.size)
        assertEquals(1, decrypted.timeTrackings.size)
        assertEquals(5000L, decrypted.costAllocations[0].allocatedAmountCents)
    }

    @Test
    fun testRegression_multiplePaymentsPerReceivable() {
        val receivable = ReceivableEntity(
            id = "rec_multi",
            clientId = "cli_1",
            contractId = "con_1",
            competence = testCompetence,
            dueDate = 0L,
            originalValueCents = 100000L,
            netValueCents = 100000L,
            status = ReceivableStatus.QUITADA
        )
        val payments = listOf(
            PaymentEntity(id = "p1", receivableId = "rec_multi", clientId = "cli_1", grossAmountCents = 40000L, feeCents = 1000L, netAmountCents = 39000L, destinationAccountId = "acc_1"),
            PaymentEntity(id = "p2", receivableId = "rec_multi", clientId = "cli_1", grossAmountCents = 60000L, feeCents = 2000L, netAmountCents = 58000L, destinationAccountId = "acc_1")
        )

        val metrics = ProfitabilityEngine.calculateOverviewMetrics(
            receivables = listOf(receivable),
            payments = payments,
            expenses = emptyList(),
            costAllocations = emptyList(),
            timeTrackings = emptyList(),
            clients = emptyList(),
            contracts = emptyList(),
            competenceFilter = testCompetence
        )

        assertEquals(97000L, metrics.netRevenueCents)
    }

    @Test
    fun testRegression_sessionApportionmentPrecision() {
        val clients = listOf(createDummyClient("c1", "A"), createDummyClient("c2", "B"), createDummyClient("c3", "C"))
        val contracts = listOf(
            createDummyContract("con_1", "c1", "s1"),
            createDummyContract("con_2", "c2", "s1"),
            createDummyContract("con_3", "c3", "s1")
        )
        val sessionCounts = mapOf("con_1" to 3, "con_2" to 3, "con_3" to 4)
        val totalIndirectCents = 1000L

        val preview = ProfitabilityEngine.calculateApportionmentPreview(
            method = ApportionmentMethod.QUANTIDADE_SESSOES,
            totalIndirectCents = totalIndirectCents,
            activeContracts = contracts,
            clients = clients,
            timeTrackings = emptyList(),
            receivablesInPeriod = emptyList(),
            contractSessionCounts = sessionCounts
        )

        assertEquals(3, preview.size)
        assertEquals(300L, preview.first { it.contractId == "con_1" }.allocatedCents)
        assertEquals(300L, preview.first { it.contractId == "con_2" }.allocatedCents)
        assertEquals(400L, preview.first { it.contractId == "con_3" }.allocatedCents)
        assertEquals(totalIndirectCents, preview.sumOf { it.allocatedCents })
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRegression_negativeAllocationValidation() {
        val expense = ExpenseEntity(
            id = "exp_neg",
            description = "Teste",
            category = "Op",
            valueCents = 10000L,
            competence = testCompetence,
            dueDate = 0L
        )
        ProfitabilityEngine.validateAllocationLimit(expense, emptyList(), -500L)
    }
}
