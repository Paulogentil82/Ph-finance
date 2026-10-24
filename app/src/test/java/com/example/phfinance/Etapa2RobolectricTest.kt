package com.example.phfinance

import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.util.FinancialEngine
import com.example.phfinance.util.GrowthSimulationParams
import com.example.phfinance.util.GrowthSimulatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Etapa2RobolectricTest {

    @Test
    fun testMonthlyDREAccrualVsCashBasis() {
        val receivables = listOf(
            ReceivableEntity("r1", "c1", "cnt1", "10/2026", 0L, 0L, 200000L, 20000L, 0L, 180000L, 180000L, ReceivableStatus.QUITADA),
            ReceivableEntity("r2", "c2", "cnt2", "10/2026", 0L, 0L, 150000L, 0L, 0L, 150000L, 0L, ReceivableStatus.PENDENTE)
        )

        val payments = listOf(
            PaymentEntity("p1", "r1", "c1", 180000L, 5000L, 175000L, 0L, "acc1", PaymentMethod.PIX)
        )

        val expenses = listOf(
            ExpenseEntity("e1", "Aluguel Studio", "Infraestrutura", "", 50000L, "10/2026", 0L, status = ExpenseStatus.PAGO, isOwnerPersonalWithdrawal = false),
            ExpenseEntity("e2", "Pro-Labore Paulo", "Pessoal", "", 80000L, "10/2026", 0L, status = ExpenseStatus.PAGO, isOwnerPersonalWithdrawal = true)
        )

        val dre = FinancialEngine.calculateMonthlyDRE(receivables, payments, expenses, "10/2026")

        // Accrual (Faturado)
        assertEquals(350000L, dre.accrualGrossRevenueCents) // 200k + 150k
        assertEquals(20000L, dre.accrualDiscountsCents)
        assertEquals(330000L, dre.accrualNetRevenueCents)
        assertEquals(50000L, dre.accrualExpensesCents)
        assertEquals(280000L, dre.accrualOperatingResultCents)

        // Cash (Recebido em Conta)
        assertEquals(180000L, dre.cashGrossRevenueCents)
        assertEquals(5000L, dre.cashPaymentFeesCents) // 5k fee
        assertEquals(175000L, dre.cashNetRevenueCents)

        assertEquals(50000L, dre.cashOperationalExpensesCents)
        assertEquals(125000L, dre.cashOperatingResultCents) // 175k - 50k
        assertEquals(80000L, dre.cashOwnerWithdrawalsCents)
        assertEquals(45000L, dre.cashNetPeriodVariationCents) // 125k - 80k
    }

    @Test
    fun testGrowthSimulatorDeterministicCompounding() {
        val params = GrowthSimulationParams(
            currentActiveClients = 10,
            monthlyNewClients = 2,
            monthlyChurnPercent = 10.0, // 1 churn per month
            ticketMedioCents = 20000L, // R$ 200,00
            fixedExpensesCents = 100000L, // R$ 1.000,00
            variableExpensePercent = 5.0,
            estimatedInadimplenciaPercent = 0.0
        )

        val projections = GrowthSimulatorEngine.calculateProjections(params)
        assertEquals(4, projections.size) // Months 1, 3, 6, 12

        val m1 = projections.find { it.monthIndex == 1 }
        assertNotNull(m1)
        assertEquals(11, m1!!.projectedClients) // 10 - 1 + 2 = 11
        assertEquals(220000L, m1.mrrCents) // 11 * 200k = 2.200,00
        assertTrue(m1.operatingResultCents > 0L)
    }
}
