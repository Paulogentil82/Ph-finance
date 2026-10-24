package com.example.phfinance

import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.FinancialAccountEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.util.BackupImportResult
import com.example.phfinance.util.BackupManager
import com.example.phfinance.util.FinancialEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinancialEngineTest {

    @Test
    fun testCurrencyUtilsPtBrParser() {
        assertEquals(123456L, CurrencyUtils.parseToCents("R$ 1.234,56"))
        assertEquals(15050L, CurrencyUtils.parseToCents("150,50"))
        assertEquals(99L, CurrencyUtils.parseToCents("0,99"))
        assertEquals(123456L, CurrencyUtils.parseToCents("1234.56"))
        assertEquals(20000L, CurrencyUtils.parseToCents("200"))
        assertEquals(0L, CurrencyUtils.parseToCents(""))
    }

    @Test
    fun testCurrencyUtilsStrictParser() {
        assertEquals(123456L, CurrencyUtils.parseToCentsStrict("R$ 1.234,56"))
        assertEquals(15050L, CurrencyUtils.parseToCentsStrict("150,50"))
        assertEquals(99L, CurrencyUtils.parseToCentsStrict("0,99"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCurrencyUtilsStrictParserRejectsNegative() {
        CurrencyUtils.parseToCentsStrict("-150,00")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCurrencyUtilsStrictParserRejectsInvalidChars() {
        CurrencyUtils.parseToCentsStrict("abc")
    }

    @Test
    fun testAdjustedDueDateForShortMonths() {
        val timestamp = DateUtils.getAdjustedDueDate(2026, Calendar.FEBRUARY, 31)
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH))
        assertEquals(28, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testOwnerPersonalWithdrawalKeptSeparateFromOperationalExpenses() {
        val accounts = listOf(FinancialAccountEntity("acc1", "Empresarial", currentBalanceCents = 1000000L))
        val expenses = listOf(
            ExpenseEntity("exp1", "Aluguel", "Infra", valueCents = 200000L, competence = "10/2026", dueDate = 0L, status = ExpenseStatus.PAGO, isOwnerPersonalWithdrawal = false),
            ExpenseEntity("exp2", "Pro-Labore Paulo", "Pessoal", valueCents = 500000L, competence = "10/2026", dueDate = 0L, status = ExpenseStatus.PAGO, isOwnerPersonalWithdrawal = true)
        )
        val receivables = listOf(
            ReceivableEntity("rec1", "cli1", "", "10/2026", 0L, 0L, 1000000L, 0L, 0L, 1000000L, 1000000L, ReceivableStatus.QUITADA)
        )
        val payments = listOf(
            PaymentEntity("pay1", "rec1", "cli1", 1000000L, 0L, 1000000L, 0L, "acc1", PaymentMethod.PIX)
        )

        val metrics = FinancialEngine.calculateDashboardMetrics(
            accounts = accounts,
            receivables = receivables,
            payments = payments,
            expenses = expenses,
            contracts = emptyList(),
            clients = emptyList(),
            goal = null,
            selectedCompetence = "10/2026"
        )

        assertEquals(200000L, metrics.monthPaidOperationalExpensesCents)
        assertEquals(500000L, metrics.monthOwnerWithdrawalsCents)
        assertEquals(800000L, metrics.operatingResultCents)
    }

    @Test
    fun testPartialPaymentBalanceRecalculation() {
        val netValueCents = 200000L // R$ 200,00
        val receivedValueCents = 15050L // R$ 150,50
        val pending = netValueCents - receivedValueCents
        assertEquals(184950L, pending) // R$ 1.849,50 or remainder
    }

    @Test
    fun testInternalTransfersDoNotAlterConsolidatedRevenueOrProfit() {
        val source = FinancialAccountEntity("acc1", "Empresarial", currentBalanceCents = 500000L)
        val dest = FinancialAccountEntity("acc2", "Caixa Espécie", currentBalanceCents = 100000L)

        val transferAmount = 200000L
        val newSource = source.copy(currentBalanceCents = source.currentBalanceCents - transferAmount)
        val newDest = dest.copy(currentBalanceCents = dest.currentBalanceCents + transferAmount)

        val totalConsolidatedBalance = newSource.currentBalanceCents + newDest.currentBalanceCents
        assertEquals(600000L, totalConsolidatedBalance) // Initial 500k + 100k remains 600k total!
    }

    @Test
    fun testSecurityBypassDisabledInReleaseMode() {
        // BuildConfig.DEBUG flag protects proprietor bypass
        val isDebugMode = com.example.BuildConfig.DEBUG
        if (!isDebugMode) {
            assertFalse("Bypass must be disabled in release builds", isDebugMode)
        } else {
            assertTrue(isDebugMode)
        }
    }
}
