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
        MessageTemplateEntity::class,
        BankImportEntity::class,
        BankTransactionEntity::class,
        ReconciliationEntity::class,
        ReconciliationAuditEntity::class,
        CostAllocationEntity::class,
        ApportionmentPolicyEntity::class,
        TimeTrackingEntity::class,
        PricingSimulationScenarioEntity::class
    ],
    version = 5,
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bank_imports` (
                        `id` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `importTimestamp` INTEGER NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `endDate` INTEGER NOT NULL,
                        `totalTransactions` INTEGER NOT NULL,
                        `totalCreditsCents` INTEGER NOT NULL,
                        `totalDebitsCents` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`accountId`) REFERENCES `financial_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_imports_accountId` ON `bank_imports` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_imports_importTimestamp` ON `bank_imports` (`importTimestamp`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bank_transactions` (
                        `id` TEXT NOT NULL,
                        `importId` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `fitId` TEXT NOT NULL,
                        `transactionDate` INTEGER NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `movementType` TEXT NOT NULL,
                        `memo` TEXT NOT NULL,
                        `payee` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `reconciliationId` TEXT,
                        `rawHash` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`importId`) REFERENCES `bank_imports`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`accountId`) REFERENCES `financial_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_transactions_importId` ON `bank_transactions` (`importId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_transactions_accountId` ON `bank_transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_transactions_transactionDate` ON `bank_transactions` (`transactionDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_transactions_status` ON `bank_transactions` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_transactions_accountId_fitId` ON `bank_transactions` (`accountId`, `fitId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reconciliations` (
                        `id` TEXT NOT NULL,
                        `bankTransactionId` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `reconciliationType` TEXT NOT NULL,
                        `paymentId` TEXT,
                        `receivableId` TEXT,
                        `expenseId` TEXT,
                        `reconciledAmountCents` INTEGER NOT NULL,
                        `feeCents` INTEGER NOT NULL,
                        `grossAmountCents` INTEGER NOT NULL,
                        `reconciledAt` INTEGER NOT NULL,
                        `reconciledBy` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `revertReason` TEXT,
                        `revertedAt` INTEGER,
                        `revertedBy` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`bankTransactionId`) REFERENCES `bank_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`accountId`) REFERENCES `financial_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliations_bankTransactionId` ON `reconciliations` (`bankTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliations_accountId` ON `reconciliations` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliations_paymentId` ON `reconciliations` (`paymentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliations_receivableId` ON `reconciliations` (`receivableId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliations_expenseId` ON `reconciliations` (`expenseId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reconciliation_audits` (
                        `id` TEXT NOT NULL,
                        `reconciliationId` TEXT NOT NULL,
                        `bankTransactionId` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `user` TEXT NOT NULL,
                        `detailsJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`reconciliationId`) REFERENCES `reconciliations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliation_audits_reconciliationId` ON `reconciliation_audits` (`reconciliationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reconciliation_audits_timestamp` ON `reconciliation_audits` (`timestamp`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cost_allocations` (
                        `id` TEXT NOT NULL,
                        `expenseId` TEXT NOT NULL,
                        `allocationType` TEXT NOT NULL,
                        `contractId` TEXT,
                        `clientId` TEXT,
                        `serviceId` TEXT,
                        `competence` TEXT NOT NULL,
                        `allocatedAmountCents` INTEGER NOT NULL,
                        `allocationPercentage` REAL NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cost_allocations_expenseId` ON `cost_allocations` (`expenseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cost_allocations_contractId` ON `cost_allocations` (`contractId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cost_allocations_clientId` ON `cost_allocations` (`clientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cost_allocations_serviceId` ON `cost_allocations` (`serviceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cost_allocations_competence` ON `cost_allocations` (`competence`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `apportionment_policies` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `policyType` TEXT NOT NULL,
                        `validFrom` TEXT NOT NULL,
                        `validTo` TEXT,
                        `targetCategory` TEXT,
                        `notes` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `time_trackings` (
                        `id` TEXT NOT NULL,
                        `clientId` TEXT,
                        `contractId` TEXT,
                        `serviceId` TEXT,
                        `date` INTEGER NOT NULL,
                        `competence` TEXT NOT NULL,
                        `durationMinutes` INTEGER NOT NULL,
                        `activityType` TEXT NOT NULL,
                        `hourlyEconomicRateCents` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_trackings_clientId` ON `time_trackings` (`clientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_trackings_contractId` ON `time_trackings` (`contractId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_trackings_serviceId` ON `time_trackings` (`serviceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_trackings_competence` ON `time_trackings` (`competence`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_trackings_date` ON `time_trackings` (`date`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pricing_simulations` (
                        `id` TEXT NOT NULL,
                        `serviceId` TEXT NOT NULL,
                        `scenarioName` TEXT NOT NULL,
                        `scenarioType` TEXT NOT NULL,
                        `currentPriceCents` INTEGER NOT NULL,
                        `proposedPriceCents` INTEGER NOT NULL,
                        `currentClientsCount` INTEGER NOT NULL,
                        `directCostPerClientCents` INTEGER NOT NULL,
                        `allocatedExpensePerClientCents` INTEGER NOT NULL,
                        `averageDedicatedHoursPerClient` REAL NOT NULL,
                        `hypotheticalChurnPercent` REAL NOT NULL,
                        `targetMarginPercent` REAL NOT NULL,
                        `projectedRevenueCents` INTEGER NOT NULL,
                        `projectedContributionMarginCents` INTEGER NOT NULL,
                        `projectedOperatingResultCents` INTEGER NOT NULL,
                        `breakEvenClientsCount` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pricing_simulations_serviceId` ON `pricing_simulations` (`serviceId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ph_finance_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

