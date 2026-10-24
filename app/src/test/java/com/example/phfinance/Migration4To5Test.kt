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
    fun testMigration4To5Directly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = context.getDatabasePath("test-mig.db")
        if (dbFile.exists()) dbFile.delete()

        val sqliteDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS clients (id TEXT PRIMARY KEY NOT NULL, fullName TEXT NOT NULL, nickname TEXT NOT NULL, phone TEXT NOT NULL, whatsapp TEXT NOT NULL, email TEXT NOT NULL)")
        sqliteDb.execSQL("INSERT INTO clients (id, fullName, nickname, phone, whatsapp, email) VALUES ('c1', 'Aluno Teste', 'Teste', '11999999999', '11999999999', 'aluno@test.com')")
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

        // Run MIGRATION_4_5
        AppDatabase.MIGRATION_4_5.migrate(supportDb)

        val cursor = supportDb.query("SELECT * FROM clients WHERE id = 'c1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("Aluno Teste", cursor.getString(cursor.getColumnIndexOrThrow("fullName")))
        cursor.close()
        supportDb.close()
    }
}
