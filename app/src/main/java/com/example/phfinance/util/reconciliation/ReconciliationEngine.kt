package com.example.phfinance.util.reconciliation

import com.example.phfinance.data.local.BankTransactionEntity
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.model.BankTransactionStatus
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.MatchConfidence
import com.example.phfinance.data.model.MovementType
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.data.model.ReconciliationMatchSuggestion
import java.security.MessageDigest
import kotlin.math.abs

object ReconciliationEngine {

    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    private const val DATE_TOLERANCE_STRONG_MS = 3 * ONE_DAY_MS
    private const val DATE_TOLERANCE_POSSIBLE_MS = 7 * ONE_DAY_MS

    fun computeRawHash(accountId: String, date: Long, amountCents: Long, memo: String): String {
        val normalizedMemo = memo.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        // Round date to day boundary for rawHash stability across timezones
        val dayIndex = date / ONE_DAY_MS
        val input = "$accountId|$dayIndex|$amountCents|$normalizedMemo"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Deterministically evaluates candidate matches for a bank transaction against internal records.
     * Never auto-commits without explicit confirmation from the administrator.
     */
    fun findBestMatch(
        tx: BankTransactionEntity,
        existingPayments: List<PaymentEntity>,
        receivables: List<ReceivableEntity>,
        expenses: List<ExpenseEntity>,
        clients: List<ClientEntity>
    ): ReconciliationMatchSuggestion {
        val clientMap = clients.associateBy { it.id }

        if (tx.movementType == MovementType.ENTRADA || tx.amountCents > 0) {
            val creditAmount = abs(tx.amountCents)

            // 1. Check existing recorded payments (Fluxo A)
            val paymentMatch = matchExistingPayment(tx, creditAmount, existingPayments, clientMap)
            if (paymentMatch != null && paymentMatch.confidence == MatchConfidence.FORTE) {
                return paymentMatch
            }

            // 2. Check pending receivables (Fluxo B)
            val receivableMatch = matchPendingReceivable(tx, creditAmount, receivables, clientMap)
            if (receivableMatch != null && receivableMatch.confidence == MatchConfidence.FORTE) {
                return receivableMatch
            }

            // Fallback to possible payment match if found
            if (paymentMatch != null) return paymentMatch
            // Fallback to possible receivable match if found
            if (receivableMatch != null) return receivableMatch

        } else {
            // Outflow (Debits / Expenses)
            val debitAmount = abs(tx.amountCents)
            val expenseMatch = matchPendingExpense(tx, debitAmount, expenses)
            if (expenseMatch != null) return expenseMatch
        }

        return ReconciliationMatchSuggestion(
            bankTransactionId = tx.id,
            confidence = MatchConfidence.INSUFICIENTE,
            justification = "Nenhuma correspondência interna direta com valor e período. O usuário pode vincular manualmente ou registrar nova movimentação."
        )
    }

    private fun matchExistingPayment(
        tx: BankTransactionEntity,
        amountCents: Long,
        payments: List<PaymentEntity>,
        clientMap: Map<String, ClientEntity>
    ): ReconciliationMatchSuggestion? {
        val candidates = payments.filter { p ->
            val dateDiff = abs(p.paymentDate - tx.transactionDate)
            dateDiff <= DATE_TOLERANCE_POSSIBLE_MS &&
                    (p.netAmountCents == amountCents || p.grossAmountCents == amountCents)
        }

        if (candidates.isEmpty()) return null

        for (p in candidates) {
            val dateDiff = abs(p.paymentDate - tx.transactionDate)
            val client = clientMap[p.clientId]
            val clientName = client?.fullName ?: ""
            val nameMatch = containsClientNameToken(tx.memo, clientName)

            if (p.netAmountCents == amountCents && (dateDiff <= DATE_TOLERANCE_STRONG_MS || nameMatch)) {
                return ReconciliationMatchSuggestion(
                    bankTransactionId = tx.id,
                    confidence = MatchConfidence.FORTE,
                    justification = "Pagamento já registrado no sistema para $clientName em ${DateUtils.formatDate(p.paymentDate)} (${CurrencyUtils.formatCents(p.netAmountCents)}). Fluxo A: Apenas conciliar sem duplicar caixa.",
                    matchedPayment = p,
                    matchedPaymentId = p.id,
                    suggestedGrossCents = p.grossAmountCents,
                    suggestedFeeCents = p.feeCents,
                    suggestedNetCents = p.netAmountCents
                )
            }
        }

        // Possible match
        val first = candidates.first()
        val clientName = clientMap[first.clientId]?.fullName ?: "Aluno"
        return ReconciliationMatchSuggestion(
            bankTransactionId = tx.id,
            confidence = MatchConfidence.POSSIVEL,
            justification = "Possível correspondência com pagamento registrado de $clientName em ${DateUtils.formatDate(first.paymentDate)}.",
            matchedPayment = first,
            matchedPaymentId = first.id,
            suggestedGrossCents = first.grossAmountCents,
            suggestedFeeCents = first.feeCents,
            suggestedNetCents = first.netAmountCents
        )
    }

    private fun matchPendingReceivable(
        tx: BankTransactionEntity,
        amountCents: Long,
        receivables: List<ReceivableEntity>,
        clientMap: Map<String, ClientEntity>
    ): ReconciliationMatchSuggestion? {
        val openReceivables = receivables.filter { r ->
            r.status == ReceivableStatus.PENDENTE ||
                    r.status == ReceivableStatus.VENCIDA ||
                    r.status == ReceivableStatus.PARCIALMENTE_PAGA
        }

        for (r in openReceivables) {
            val remainingCents = r.netValueCents - r.receivedValueCents
            val client = clientMap[r.clientId]
            val clientName = client?.fullName ?: ""
            val nameMatch = containsClientNameToken(tx.memo, clientName)
            val dateDiff = abs(r.dueDate - tx.transactionDate)

            // Exact balance match: requires nameMatch to qualify as FORTE
            if (remainingCents == amountCents) {
                if (nameMatch && dateDiff <= DATE_TOLERANCE_POSSIBLE_MS) {
                    return ReconciliationMatchSuggestion(
                        bankTransactionId = tx.id,
                        confidence = MatchConfidence.FORTE,
                        justification = "Mensalidade pendente de $clientName (ref. ${r.competence}, vencimento ${DateUtils.formatDate(r.dueDate)}) com identificação e saldo confirmados. Fluxo B: Dar baixa e creditar na conta.",
                        matchedReceivable = r,
                        matchedReceivableId = r.id,
                        suggestedGrossCents = remainingCents,
                        suggestedFeeCents = 0L,
                        suggestedNetCents = amountCents
                    )
                }
            }

            // Fee discrepancy: amount received is slightly lower than remaining balance (e.g. 1% to 6% card/gateway fee)
            if (amountCents < remainingCents && (remainingCents - amountCents) <= (remainingCents * 0.08).toLong()) {
                val feeDiff = remainingCents - amountCents
                if (nameMatch) {
                    return ReconciliationMatchSuggestion(
                        bankTransactionId = tx.id,
                        confidence = MatchConfidence.POSSIVEL,
                        justification = "Cobrança de $clientName (${r.competence}) de ${CurrencyUtils.formatCents(remainingCents)} com provável retenção de taxa de ${CurrencyUtils.formatCents(feeDiff)}.",
                        matchedReceivable = r,
                        matchedReceivableId = r.id,
                        suggestedGrossCents = remainingCents,
                        suggestedFeeCents = feeDiff,
                        suggestedNetCents = amountCents
                    )
                }
            }
        }

        // Check for general possible match on amount alone
        val valueMatch = openReceivables.find { (it.netValueCents - it.receivedValueCents) == amountCents }
        if (valueMatch != null) {
            val clientName = clientMap[valueMatch.clientId]?.fullName ?: "Aluno"
            return ReconciliationMatchSuggestion(
                bankTransactionId = tx.id,
                confidence = MatchConfidence.POSSIVEL,
                justification = "Valor idêntico ao saldo pendente de $clientName (${valueMatch.competence}), vencimento ${DateUtils.formatDate(valueMatch.dueDate)}. Confirmar identidade do pagador.",
                matchedReceivable = valueMatch,
                matchedReceivableId = valueMatch.id,
                suggestedGrossCents = amountCents,
                suggestedFeeCents = 0L,
                suggestedNetCents = amountCents
            )
        }

        return null
    }

    private fun matchPendingExpense(
        tx: BankTransactionEntity,
        amountCents: Long,
        expenses: List<ExpenseEntity>
    ): ReconciliationMatchSuggestion? {
        val openExpenses = expenses.filter { it.status == com.example.phfinance.data.model.ExpenseStatus.PENDENTE }

        for (e in openExpenses) {
            val dateDiff = abs(e.dueDate - tx.transactionDate)
            if (e.valueCents == amountCents) {
                val descMatch = tx.memo.contains(e.description, ignoreCase = true) ||
                        (e.supplier.isNotBlank() && tx.memo.contains(e.supplier, ignoreCase = true))

                if (descMatch || dateDiff <= DATE_TOLERANCE_STRONG_MS) {
                    return ReconciliationMatchSuggestion(
                        bankTransactionId = tx.id,
                        confidence = MatchConfidence.FORTE,
                        justification = "Despesa pendente '${e.description}' (${e.competence}) com valor exato de ${CurrencyUtils.formatCents(e.valueCents)}. Fluxo: Dar baixa na despesa e atualizar saldo.",
                        matchedExpense = e,
                        matchedExpenseId = e.id,
                        suggestedGrossCents = amountCents,
                        suggestedFeeCents = 0L,
                        suggestedNetCents = amountCents
                    )
                }
            }
        }

        val possible = openExpenses.find { it.valueCents == amountCents }
        if (possible != null) {
            return ReconciliationMatchSuggestion(
                bankTransactionId = tx.id,
                confidence = MatchConfidence.POSSIVEL,
                justification = "Valor coincide com a despesa a pagar '${possible.description}' (${possible.competence}).",
                matchedExpense = possible,
                matchedExpenseId = possible.id,
                suggestedGrossCents = amountCents,
                suggestedFeeCents = 0L,
                suggestedNetCents = amountCents
            )
        }

        return null
    }

    private fun containsClientNameToken(memo: String, fullName: String): Boolean {
        if (fullName.isBlank()) return false
        val cleanMemo = memo.lowercase()
        val tokens = fullName.lowercase().split(" ").filter { it.length > 2 }
        return tokens.any { cleanMemo.contains(it) }
    }
}
