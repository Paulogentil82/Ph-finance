package com.example.phfinance.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.phfinance.data.model.AccountType
import com.example.phfinance.data.model.BillingPeriod
import com.example.phfinance.data.model.ClientType
import com.example.phfinance.data.model.ContractStatus
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.ExpenseType
import com.example.phfinance.data.model.MovementType
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.data.model.ServiceCategory
import com.example.phfinance.data.model.WhatsappTemplateType

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val nickname: String,
    val phone: String,
    val whatsapp: String,
    val email: String,
    val birthDate: String = "",
    val cpf: String = "",
    val registrationDate: Long = System.currentTimeMillis(),
    val clientType: ClientType = ClientType.ATIVO,
    val preferredServiceId: String = "",
    val agreedValueCents: Long = 0L,
    val discountCents: Long = 0L,
    val dueDay: Int = 10,
    val startDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: ServiceCategory = ServiceCategory.CONSULTORIA_ONLINE,
    val defaultPriceCents: Long = 0L,
    val billingPeriod: BillingPeriod = BillingPeriod.MENSAL,
    val sessionCount: Int = 0,
    val contractDurationMonths: Int = 1,
    val isActive: Boolean = true,
    val revenueCategory: String = "Consultoria",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "contracts",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ServiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("clientId"), Index("serviceId")]
)
data class ContractEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val serviceId: String,
    val startDate: Long,
    val endDate: Long? = null,
    val contractedValueCents: Long,
    val discountCents: Long = 0L,
    val billingFrequency: BillingPeriod = BillingPeriod.MENSAL,
    val dueDay: Int = 10,
    val preferredPaymentMethod: PaymentMethod = PaymentMethod.PIX,
    val autoRenewal: Boolean = true,
    val status: ContractStatus = ContractStatus.ATIVO,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "contract_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ContractEntity::class,
            parentColumns = ["id"],
            childColumns = ["contractId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("contractId"), Index("clientId")]
)
data class ContractSessionEntity(
    @PrimaryKey val id: String,
    val contractId: String,
    val clientId: String,
    val totalSessions: Int,
    val completedSessions: Int = 0,
    val cancelledSessions: Int = 0,
    val remainingSessions: Int,
    val lastSessionDate: Long? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "receivables",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("clientId"), Index("contractId"), Index("competence")]
)
data class ReceivableEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val contractId: String = "",
    val competence: String, // MM/yyyy
    val issueDate: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val originalValueCents: Long,
    val discountCents: Long = 0L,
    val additionCents: Long = 0L,
    val netValueCents: Long,
    val receivedValueCents: Long = 0L,
    val status: ReceivableStatus = ReceivableStatus.PENDENTE,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = ReceivableEntity::class,
            parentColumns = ["id"],
            childColumns = ["receivableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receivableId"), Index("clientId"), Index("destinationAccountId")]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val receivableId: String,
    val clientId: String,
    val grossAmountCents: Long,
    val feeCents: Long = 0L,
    val netAmountCents: Long,
    val paymentDate: Long = System.currentTimeMillis(),
    val destinationAccountId: String,
    val paymentMethod: PaymentMethod = PaymentMethod.PIX,
    val transactionRef: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isSystemDefault: Boolean = false
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val description: String,
    val category: String,
    val supplier: String = "",
    val valueCents: Long,
    val competence: String,
    val dueDate: Long,
    val paymentDate: Long? = null,
    val accountId: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.PIX,
    val isRecurring: Boolean = false,
    val installmentInfo: String = "",
    val status: ExpenseStatus = ExpenseStatus.PENDENTE,
    val isOwnerPersonalWithdrawal: Boolean = false,
    val expenseType: ExpenseType = ExpenseType.DESPESA_OPERACIONAL,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "financial_accounts")
data class FinancialAccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val accountType: AccountType = AccountType.BANCO_EMPRESARIAL,
    val initialBalanceCents: Long = 0L,
    val currentBalanceCents: Long = 0L,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "account_movements",
    indices = [Index("accountId"), Index("date")]
)
data class AccountMovementEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: MovementType,
    val amountCents: Long,
    val date: Long = System.currentTimeMillis(),
    val description: String,
    val relatedEntityId: String = "", // Payment ID, Expense ID or Transfer ID
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "account_transfers")
data class AccountTransferEntity(
    @PrimaryKey val id: String,
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amountCents: Long,
    val date: Long = System.currentTimeMillis(),
    val description: String = "Transferência Interna",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String = "default_goal",
    val monthlyRevenueGoalCents: Long = 1000000L, // Default R$ 10.000,00
    val annualRevenueGoalCents: Long = 12000000L, // Default R$ 120.000,00
    val activeClientsGoal: Int = 30,
    val operatingResultGoalCents: Long = 700000L, // Default R$ 7.000,00
    val maxExpenseLimitCents: Long = 300000L, // Default R$ 3.000,00
    val desiredReserveCents: Long = 2000000L // Default R$ 20.000,00
)

@Entity(tableName = "business_settings")
data class BusinessSettingsEntity(
    @PrimaryKey val id: String = "default_settings",
    val businessName: String = "PH Personal",
    val tradeName: String = "PH Consultoria de Treinamento",
    val cpfCnpj: String = "",
    val phone: String = "(11) 99999-9999",
    val email: String = "contato@phpersonal.com.br",
    val address: String = "São Paulo - SP",
    val logoUri: String = "",
    val taxReservePercent: Double = 6.0,
    val isDarkMode: Boolean = true,
    val pixKey: String = "11999999999",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notification_logs",
    indices = [Index("clientId"), Index("receivableId")]
)
data class NotificationLogEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val receivableId: String = "",
    val templateType: WhatsappTemplateType,
    val messageText: String,
    val sentAt: Long = System.currentTimeMillis(),
    val isConfirmedSent: Boolean = false
)

@Entity(tableName = "monthly_closures")
data class MonthlyClosureEntity(
    @PrimaryKey val id: String, // e.g., "closure_10_2026"
    val competence: String, // MM/yyyy
    val status: String = "RASCUNHO", // "RASCUNHO" or "DEFINITIVO"
    val closedAt: Long = System.currentTimeMillis(),
    val closedBy: String = "Proprietário",
    val accrualRevenueCents: Long = 0L,
    val accrualExpenseCents: Long = 0L,
    val cashRevenueCents: Long = 0L,
    val cashExpenseCents: Long = 0L,
    val netResultCents: Long = 0L,
    val notes: String = "",
    val isReopened: Boolean = false,
    val reopenedAt: Long? = null,
    val reopenReason: String = ""
)

@Entity(tableName = "closure_histories")
data class ClosureHistoryEntity(
    @PrimaryKey val id: String,
    val closureId: String,
    val competence: String,
    val actionType: String, // "CRIADO_RASCUNHO", "FECHADO_DEFINITIVO", "REABERTO"
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "",
    val user: String = "Proprietário"
)

@Entity(tableName = "message_templates")
data class MessageTemplateEntity(
    @PrimaryKey val id: String, // WhatsappTemplateType name
    val templateType: WhatsappTemplateType,
    val title: String,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis()
)

