package com.example.phfinance

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.phfinance.data.local.AppDatabase
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.model.ClientType
import com.example.phfinance.data.repository.PhFinanceRepository
import com.example.phfinance.ui.screens.onGoogleSignInClicked
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ClientFlowAndAuthTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var database: AppDatabase
    private lateinit var repository: PhFinanceRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PhFinanceRepository(database.phFinanceDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testGoogleSignInWithoutValidClientIdFailsGracefully() = testScope.runTest {
        var errorReported: String? = null
        val credentialManager = CredentialManager.create(context)

        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onAuthError = { errorReported = it },
            scope = this
        )

        testScheduler.advanceUntilIdle()
        assertNotNull(errorReported)
    }

    @Test
    fun testQuickActionTriggerFlow() {
        val viewModel = PhFinanceViewModel(ApplicationProvider.getApplicationContext())
        assertFalse(viewModel.triggerOpenAddClient.value)

        viewModel.triggerAddClientForm()
        assertTrue(viewModel.triggerOpenAddClient.value)

        viewModel.consumeAddClientFormTrigger()
        assertFalse(viewModel.triggerOpenAddClient.value)
    }

    @Test
    fun testSaveClientPersistenceAndEditPreservesFields() = testScope.runTest {
        // 1. Insert original client directly in database
        val originalClient = ClientEntity(
            id = "client_1",
            fullName = "Carlos Alberto",
            nickname = "Carlinhos",
            phone = "11988887777",
            whatsapp = "11988887777",
            email = "carlos@test.com",
            birthDate = "1990-05-15",
            cpf = "123.456.789-00",
            clientType = ClientType.ATIVO,
            agreedValueCents = 25000L,
            dueDay = 15,
            notes = "Observação original"
        )
        repository.saveClient(originalClient)

        val retrieved = repository.getClientById("client_1")
        assertNotNull(retrieved)
        assertEquals("1990-05-15", retrieved!!.birthDate)
        assertEquals("123.456.789-00", retrieved.cpf)

        // 2. Edit client via Repository saveClient with updated fields
        val updatedClient = retrieved.copy(
            fullName = "Carlos Alberto Silva",
            agreedValueCents = 30000L,
            dueDay = 20,
            notes = "Observação atualizada"
        )
        repository.saveClient(updatedClient)

        val afterEdit = repository.getClientById("client_1")
        assertNotNull(afterEdit)
        assertEquals("Carlos Alberto Silva", afterEdit!!.fullName)
        assertEquals(30000L, afterEdit.agreedValueCents)
        assertEquals(20, afterEdit.dueDay)
        // Ensure CPF and BirthDate were preserved intact
        assertEquals("1990-05-15", afterEdit.birthDate)
        assertEquals("123.456.789-00", afterEdit.cpf)
    }
}
