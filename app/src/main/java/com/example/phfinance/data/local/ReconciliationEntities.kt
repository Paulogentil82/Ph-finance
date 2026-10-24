package com.example.phfinance.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.phfinance.data.model.BankTransactionStatus
import com.example.phfinance.data.model.MovementType
import com.example.phfinance.data.model.ReconciliationType

@Entity(
    tableName = "bank_imports",
    foreignKeys = [
        ForeignKey(
            entity = FinancialAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("accountId"), Index("importTimestamp")]
)
data class BankImportEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val fileName: String,
    val fileType: String, // "OFX" or "CSV"
    val fileSizeBytes: Long,
    val importTimestamp: Long = System.currentTimeMillis(),
    val startDate: Long,
    val endDate: Long,
    val totalTransactions: Int,
    val totalCreditsCents: Long = 0L,
    val totalDebitsCents: Long = 0L,
    val status: String = "PROCESSADO" // "PROCESSADO" or "CANCELADO"
)

@Entity(
    tableName = "bank_transactions",
    foreignKeys = [
        ForeignKey(
            entity = BankImportEntity::class,
            parentColumns = ["id"],
            childColumns = ["importId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FinancialAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("importId"),
        Index("accountId"),
        Index("transactionDate"),
        Index("status"),
        Index(value = ["accountId", "fitId"])
    ]
)
data class BankTransactionEntity(
    @PrimaryKey val id: String,
    val importId: String,
    val accountId: String,
    val fitId: String, // Bank provided ID or generated deterministic hash
    val transactionDate: Long,
    val amountCents: Long, // Positive for inflow (credit), negative for outflow (debit)
    val movementType: MovementType,
    val memo: String,
    val payee: String = "",
    val status: BankTransactionStatus = BankTransactionStatus.IMPORTADA,
    val reconciliationId: String? = null,
    val rawHash: String, // Hash of accountId + date + amount + sanitized memo for duplicate prevention
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reconciliations",
    foreignKeys = [
        ForeignKey(
            entity = BankTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankTransactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FinancialAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("bankTransactionId"),
        Index("accountId"),
        Index("paymentId"),
        Index("receivableId"),
        Index("expenseId")
    ]
)
data class ReconciliationEntity(
    @PrimaryKey val id: String,
    val bankTransactionId: String,
    val accountId: String,
    val reconciliationType: ReconciliationType,
    val paymentId: String? = null,
    val receivableId: String? = null,
    val expenseId: String? = null,
    val reconciledAmountCents: Long,
    val feeCents: Long = 0L,
    val grossAmountCents: Long = 0L,
    val reconciledAt: Long = System.currentTimeMillis(),
    val reconciledBy: String = "Proprietário",
    val status: String = "ATIVA", // "ATIVA" or "DESFEITA"
    val notes: String = "",
    val revertReason: String? = null,
    val revertedAt: Long? = null,
    val revertedBy: String? = null
)

@Entity(
    tableName = "reconciliation_audits",
    foreignKeys = [
        ForeignKey(
            entity = ReconciliationEntity::class,
            parentColumns = ["id"],
            childColumns = ["reconciliationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reconciliationId"), Index("timestamp")]
)
data class ReconciliationAuditEntity(
    @PrimaryKey val id: String,
    val reconciliationId: String,
    val bankTransactionId: String,
    val actionType: String, // "CRIACAO_VINCULO", "CRIACAO_NOVO_RECEBIMENTO", "CRIACAO_NOVA_DESPESA", "DESFEITA", "IGNORADA", "RESTAURADA"
    val timestamp: Long = System.currentTimeMillis(),
    val user: String = "Proprietário",
    val detailsJson: String = ""
)
