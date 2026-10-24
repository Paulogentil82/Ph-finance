package com.example.phfinance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.phfinance.data.model.AccountType
import com.example.phfinance.data.model.BillingPeriod
import com.example.phfinance.data.model.ServiceCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        ClientEntity::class,
        ServiceEntity::class,
        ContractEntity::class,
        ContractSessionEntity::class,
        ReceivableEntity::class,
        PaymentEntity::class,
        ExpenseCategoryEntity::class,
        ExpenseEntity::class,
        FinancialAccountEntity::class,
        AccountMovementEntity::class,
        AccountTransferEntity::class,
        GoalEntity::class,
        BusinessSettingsEntity::class,
        NotificationLogEntity::class,
        MonthlyClosureEntity::class,
        ClosureHistoryEntity::class,
        MessageTemplateEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun phFinanceDao(): PhFinanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN expenseType TEXT NOT NULL DEFAULT 'DESPESA_OPERACIONAL'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `monthly_closures` (
                        `id` TEXT NOT NULL,
                        `competence` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `closedAt` INTEGER NOT NULL,
                        `closedBy` TEXT NOT NULL,
                        `accrualRevenueCents` INTEGER NOT NULL,
                        `accrualExpenseCents` INTEGER NOT NULL,
                        `cashRevenueCents` INTEGER NOT NULL,
                        `cashExpenseCents` INTEGER NOT NULL,
                        `netResultCents` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `isReopened` INTEGER NOT NULL,
                        `reopenedAt` INTEGER,
                        `reopenReason` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `closure_histories` (
                        `id` TEXT NOT NULL,
                        `closureId` TEXT NOT NULL,
                        `competence` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `user` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `message_templates` (
                        `id` TEXT NOT NULL,
                        `templateType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ph_finance_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = getDatabase(context).phFinanceDao()
                    populateInitialData(dao)
                }
            }

            private suspend fun populateInitialData(dao: PhFinanceDao) {
                // Default Expense Categories
                val categories = listOf(
                    "Assinaturas e Aplicativos",
                    "Marketing e Publicidade",
                    "Internet e Telefonia",
                    "Equipamentos",
                    "Materiais de Trabalho",
                    "Transporte e Deslocamento",
                    "Serviços Profissionais",
                    "Tarifas Bancárias",
                    "Impostos e Tributos",
                    "Educação e Cursos",
                    "Outras Despesas"
                )
                categories.forEach { catName ->
                    dao.insertExpenseCategory(
                        ExpenseCategoryEntity(
                            id = UUID.randomUUID().toString(),
                            name = catName,
                            isSystemDefault = true
                        )
                    )
                }

                // Default Financial Accounts
                dao.insertAccount(
                    FinancialAccountEntity(
                        id = "acc_main",
                        name = "Conta Empresarial Principal",
                        accountType = AccountType.BANCO_EMPRESARIAL,
                        initialBalanceCents = 250000L, // R$ 2.500,00
                        currentBalanceCents = 250000L,
                        isDefault = true
                    )
                )
                dao.insertAccount(
                    FinancialAccountEntity(
                        id = "acc_cash",
                        name = "Caixa em Espécie / Pix Pessoal",
                        accountType = AccountType.DINHEIRO_ESPECIE,
                        initialBalanceCents = 50000L, // R$ 500,00
                        currentBalanceCents = 50000L,
                        isDefault = false
                    )
                )

                // Default Services Catalogue
                val services = listOf(
                    ServiceEntity(
                        id = "srv_consultoria_online",
                        name = "Consultoria Online Mensal",
                        description = "Acompanhamento e suporte personalizado online via WhatsApp e app.",
                        category = ServiceCategory.CONSULTORIA_ONLINE,
                        defaultPriceCents = 20000L, // R$ 200,00
                        billingPeriod = BillingPeriod.MENSAL,
                        sessionCount = 0,
                        contractDurationMonths = 1,
                        revenueCategory = "Consultoria"
                    ),
                    ServiceEntity(
                        id = "srv_treino_presencial_pct",
                        name = "Pacote Presencial 12 Sessões",
                        description = "Treinamento presencial individual de 12 sessões mensais.",
                        category = ServiceCategory.PACOTE_SESSOES,
                        defaultPriceCents = 120000L, // R$ 1.200,00
                        billingPeriod = BillingPeriod.MENSAL,
                        sessionCount = 12,
                        contractDurationMonths = 1,
                        revenueCategory = "Presencial"
                    ),
                    ServiceEntity(
                        id = "srv_avaliacao_fisica",
                        name = "Avaliação Física Completa",
                        description = "Análise de dobras cutâneas, bioimpedância e postura.",
                        category = ServiceCategory.AVALIACAO_FISICA,
                        defaultPriceCents = 15000L, // R$ 150,00
                        billingPeriod = BillingPeriod.AVULSO,
                        sessionCount = 1,
                        contractDurationMonths = 0,
                        revenueCategory = "Avaliação"
                    ),
                    ServiceEntity(
                        id = "srv_consultoria_trimestral",
                        name = "Consultoria Trimestral com Desconto",
                        description = "Plano com fidelidade de 3 meses para acompanhamento contínuo.",
                        category = ServiceCategory.CONSULTORIA_ONLINE,
                        defaultPriceCents = 54000L, // R$ 540,00 (R$ 180/mês)
                        billingPeriod = BillingPeriod.TRIMESTRAL,
                        sessionCount = 0,
                        contractDurationMonths = 3,
                        revenueCategory = "Consultoria"
                    )
                )
                services.forEach { dao.insertService(it) }

                // Default Message Templates
                com.example.phfinance.data.model.WhatsappTemplateType.values().forEach { tType ->
                    dao.insertMessageTemplate(
                        MessageTemplateEntity(
                            id = tType.name,
                            templateType = tType,
                            title = tType.title,
                            text = tType.defaultText
                        )
                    )
                }

                // Default Goals & Business Settings
                dao.insertOrUpdateGoal(GoalEntity())
                dao.insertOrUpdateSettings(BusinessSettingsEntity())
            }
        }
    }
}

