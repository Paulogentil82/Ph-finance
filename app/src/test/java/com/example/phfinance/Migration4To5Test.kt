package com.example.phfinance

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phfinance.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    @Test
    fun testMigration4To5CompleteSchema() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = context.getDatabasePath("test-mig-v4-complete.db")
        if (dbFile.exists()) dbFile.delete()

        val sqliteDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // Setup version 4 representative schema tables
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS clients (id TEXT PRIMARY KEY NOT NULL, fullName TEXT NOT NULL, nickname TEXT NOT NULL, phone TEXT NOT NULL, whatsapp TEXT NOT NULL, email TEXT NOT NULL, agreedValueCents INTEGER NOT NULL DEFAULT 0, dueDay INTEGER NOT NULL DEFAULT 10, notes TEXT NOT NULL DEFAULT '', clientType TEXT NOT NULL DEFAULT 'ATIVO', createdAt INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0)")
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS services (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, defaultPriceCents INTEGER NOT NULL, billingPeriod TEXT NOT NULL, sessionCount INTEGER NOT NULL, contractDurationMonths INTEGER NOT NULL, revenueCategory TEXT NOT NULL, createdAt INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0)")
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS contracts (id TEXT PRIMARY KEY NOT NULL, clientId TEXT NOT NULL, serviceId TEXT NOT NULL, startDate INTEGER NOT NULL, endDate INTEGER, contractedValueCents INTEGER NOT NULL, dueDay INTEGER NOT NULL, discountCents INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'ATIVO', createdAt INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0)")
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS receivables (id TEXT PRIMARY KEY NOT NULL, clientId TEXT NOT NULL, contractId TEXT NOT NULL, competence TEXT NOT NULL, issueDate INTEGER NOT NULL, dueDate INTEGER NOT NULL, originalValueCents INTEGER NOT NULL, discountCents INTEGER NOT NULL, additionCents INTEGER NOT NULL, netValueCents INTEGER NOT NULL, receivedValueCents INTEGER NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, updatedAt INTEGER NOT NULL DEFAULT 0)")
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS payments (id TEXT PRIMARY KEY NOT NULL, receivableId TEXT NOT NULL, clientId TEXT NOT NULL, grossAmountCents INTEGER NOT NULL, feeCents INTEGER NOT NULL, netAmountCents INTEGER NOT NULL, paymentDate INTEGER NOT NULL, destinationAccountId TEXT NOT NULL, paymentMethod TEXT NOT NULL, transactionRef TEXT NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL DEFAULT 0)")
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS expenses (id TEXT PRIMARY KEY NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, supplier TEXT NOT NULL DEFAULT '', valueCents INTEGER NOT NULL, competence TEXT NOT NULL, dueDate INTEGER NOT NULL, paymentDate INTEGER, accountId TEXT, paymentMethod TEXT, status TEXT NOT NULL, isOwnerPersonalWithdrawal INTEGER NOT NULL DEFAULT 0, expenseType TEXT NOT NULL DEFAULT 'DESPESA_OPERACIONAL', createdAt INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0)")
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS financial_accounts (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, accountType TEXT NOT NULL, initialBalanceCents INTEGER NOT NULL, currentBalanceCents INTEGER NOT NULL, isDefault INTEGER NOT NULL, createdAt INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0)")

        // Insert sample data in V4
        sqliteDb.execSQL("INSERT INTO clients (id, fullName, nickname, phone, whatsapp, email) VALUES ('c1', 'Ana Silva', 'Ana', '11988888888', '11988888888', 'ana@mail.com')")
        sqliteDb.execSQL("INSERT INTO services (id, name, description, category, defaultPriceCents, billingPeriod, sessionCount, contractDurationMonths, revenueCategory) VALUES ('s1', 'Personal', 'Treino', 'CONSULTORIA_ONLINE', 20000, 'MENSAL', 0, 1, 'Consultoria')")
        sqliteDb.execSQL("INSERT INTO contracts (id, clientId, serviceId, startDate, contractedValueCents, dueDay) VALUES ('con1', 'c1', 's1', 100000, 20000, 10)")
        sqliteDb.execSQL("INSERT INTO receivables (id, clientId, contractId, competence, issueDate, dueDate, originalValueCents, discountCents, additionCents, netValueCents, receivedValueCents, status, notes) VALUES ('r1', 'c1', 'con1', '10/2026', 100000, 100000, 20000, 0, 0, 20000, 0, 'PENDENTE', 'Teste')")
        sqliteDb.execSQL("INSERT INTO expenses (id, description, category, valueCents, competence, dueDate, status) VALUES ('e1', 'Aluguel', 'Operacional', 100000, '10/2026', 100000, 'PENDENTE')")
        sqliteDb.close()

        val supportDb = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbFile.absolutePath)
                    .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                        override fun onCreate(db: SupportSQLiteDatabase) {}
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )
            .writableDatabase

        // Execute migration V4 -> V5
        AppDatabase.MIGRATION_4_5.migrate(supportDb)

        // Verify data preservation
        val cCursor = supportDb.query("SELECT * FROM clients WHERE id = 'c1'")
        assertTrue(cCursor.moveToFirst())
        assertEquals("Ana Silva", cCursor.getString(cCursor.getColumnIndexOrThrow("fullName")))
        cCursor.close()

        val sCursor = supportDb.query("SELECT * FROM services WHERE id = 's1'")
        assertTrue(sCursor.moveToFirst())
        assertEquals("Personal", sCursor.getString(sCursor.getColumnIndexOrThrow("name")))
        sCursor.close()

        // Verify new V5 tables created
        val tableCheck = supportDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cost_allocations'")
        assertTrue("Table cost_allocations must exist in V5", tableCheck.moveToFirst())
        tableCheck.close()

        val policyCheck = supportDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='apportionment_policies'")
        assertTrue("Table apportionment_policies must exist in V5", policyCheck.moveToFirst())
        policyCheck.close()

        supportDb.close()
    }
}
