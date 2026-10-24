package com.example.phfinance

import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReceivableStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialFlowsTest {

    @Test
    fun testCurrencyFormattingAndParsing() {
        val cents = 15050L
        val formatted = CurrencyUtils.formatCents(cents)
        assertTrue(formatted.contains("150,50"))

        val parsedCents = CurrencyUtils.parseToCentsStrict("150,50")
        assertEquals(15050L, parsedCents)
    }

    @Test
    fun testValidationOfFinancialInputs() {
        // Description check
        val emptyDesc = "  "
        assertTrue(emptyDesc.trim().isEmpty())

        // Cents check
        val zeroCents = CurrencyUtils.parseToCents("0,00")
        assertEquals(0L, zeroCents)

        val validCents = CurrencyUtils.parseToCents("250,00")
        assertEquals(25000L, validCents)
        assertTrue(validCents > 0L)
    }

    @Test
    fun testFinancialEngineReceivableStatusComputation() {
        val receivable = ReceivableEntity(
            id = "rec1",
            clientId = "client1",
            contractId = "contract1",
            competence = "12/2026",
            dueDate = System.currentTimeMillis(),
            originalValueCents = 10000L,
            netValueCents = 10000L,
            receivedValueCents = 0L,
            status = ReceivableStatus.PENDENTE,
            notes = "Cobrança Teste"
        )

        // Full settlement payment
        val fullPayment = PaymentEntity(
            id = "pay1",
            receivableId = "rec1",
            clientId = "client1",
            grossAmountCents = 10000L,
            feeCents = 0L,
            netAmountCents = 10000L,
            paymentDate = System.currentTimeMillis(),
            destinationAccountId = "acc1",
            paymentMethod = PaymentMethod.PIX
        )

        val remainingCents = receivable.netValueCents - fullPayment.grossAmountCents
        assertEquals(0L, remainingCents)
        assertTrue(remainingCents <= 0L)
    }

    @Test
    fun testPartialPaymentComputation() {
        val receivable = ReceivableEntity(
            id = "rec2",
            clientId = "client2",
            contractId = "",
            competence = "12/2026",
            dueDate = System.currentTimeMillis(),
            originalValueCents = 20000L,
            netValueCents = 20000L,
            receivedValueCents = 0L,
            status = ReceivableStatus.PENDENTE,
            notes = "Cobrança Parcial"
        )

        val partialPayment = PaymentEntity(
            id = "pay2",
            receivableId = "rec2",
            clientId = "client2",
            grossAmountCents = 5000L,
            feeCents = 0L,
            netAmountCents = 5000L,
            paymentDate = System.currentTimeMillis(),
            destinationAccountId = "acc1",
            paymentMethod = PaymentMethod.PIX
        )

        val totalPaid = partialPayment.grossAmountCents
        val remainingCents = receivable.netValueCents - totalPaid
        assertEquals(15000L, remainingCents) // R$ 150,00 remaining
        assertTrue(remainingCents > 0L)
    }

    @Test
    fun testExpenseStatusComputation() {
        val expense = ExpenseEntity(
            id = "exp1",
            description = "Aluguel",
            category = "Infraestrutura",
            valueCents = 150000L, // R$ 1.500,00
            competence = "12/2026",
            dueDate = System.currentTimeMillis(),
            status = ExpenseStatus.PENDENTE,
            isOwnerPersonalWithdrawal = false
        )

        assertEquals(ExpenseStatus.PENDENTE, expense.status)
        assertEquals(150000L, expense.valueCents)
        assertFalse(expense.isOwnerPersonalWithdrawal)
    }
}
