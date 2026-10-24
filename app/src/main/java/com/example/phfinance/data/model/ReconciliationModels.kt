package com.example.phfinance.data.model

enum class BankTransactionStatus(val label: String) {
    IMPORTADA("Importada"),
    PENDENTE("Pendente"),
    CONCILIADA("Conciliada"),
    IGNORADA("Ignorada"),
    SUSPEITA_DUPLICIDADE("Suspeita de Duplicidade")
}

enum class ReconciliationType(val label: String) {
    VINCULO_PAGAMENTO_EXISTENTE("Vínculo a Pagamento Existente"),
    NOVO_RECEBIMENTO("Baixa de Recebimento"),
    NOVA_DESPESA("Lançamento de Despesa")
}

enum class MatchConfidence(val label: String) {
    FORTE("Correspondência Forte"),
    POSSIVEL("Possível Correspondência"),
    INSUFICIENTE("Insuficiente / Não Encontrada")
}

data class ReconciliationMatchSuggestion(
    val bankTransactionId: String,
    val confidence: MatchConfidence,
    val justification: String,
    val matchedPayment: Any? = null,
    val matchedPaymentId: String? = null,
    val matchedReceivable: Any? = null,
    val matchedReceivableId: String? = null,
    val matchedExpense: Any? = null,
    val matchedExpenseId: String? = null,
    val suggestedGrossCents: Long = 0L,
    val suggestedFeeCents: Long = 0L,
    val suggestedNetCents: Long = 0L
)

data class CsvConfig(
    val delimiter: Char = ';',
    val decimalSeparator: Char = ',',
    val dateFormat: String = "dd/MM/yyyy",
    val dateCol: Int = 0,
    val descCol: Int = 1,
    val amountCol: Int = 2,
    val fitIdCol: Int = -1,
    val hasHeader: Boolean = true
)

data class ParsedBankTransaction(
    val fitId: String,
    val date: Long,
    val amountCents: Long, // positive = credit (entrada), negative = debit (saída)
    val movementType: MovementType,
    val memo: String,
    val payee: String = ""
)

data class ParsedBankStatement(
    val accountId: String,
    val fileName: String,
    val fileType: String, // "OFX" or "CSV"
    val fileSizeBytes: Long,
    val startDate: Long,
    val endDate: Long,
    val transactions: List<ParsedBankTransaction>
)
