package com.example.phfinance

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.MainAppScaffold
import com.example.phfinance.ui.theme.PHFinanceTheme
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real scaffold callbacks and the screens that consume their triggers.
 * Robolectric uses sandbox storage; these tests never authenticate or submit a form.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuickActionNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var viewModel: PhFinanceViewModel

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        viewModel = ViewModelProvider(
            store, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[PhFinanceViewModel::class.java]
        compose.setContent {
            PHFinanceTheme { MainAppScaffold(viewModel, onSignOut = {}) }
        }
    }

    @After
    fun tearDown() {
        compose.runOnIdle { store.clear() }
    }

    private fun choose(action: String) {
        compose.onNodeWithContentDescription("Ação Rápida").performClick()
        compose.onNodeWithText(action).performClick()
        compose.onNodeWithText("Ação Rápida PH Finance").assertDoesNotExist()
    }

    private fun assertFinancialTab(tab: String) {
        compose.onNodeWithText("Financeiro").assertIsSelected()
        compose.onNodeWithText(tab).assertIsSelected()
    }

    @Test
    fun receivableOpensFromDashboardAndAgainFromExpenses() {
        choose("Criar Cobrança Avulsa")
        assertFinancialTab("Cobranças")
        compose.onNodeWithText("Criar Cobrança Avulsa").assertIsDisplayed()
        compose.runOnIdle { assertFalse(viewModel.triggerOpenAddReceivable.value) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("A Pagar").performClick()
        choose("Criar Cobrança Avulsa")
        assertFinancialTab("Cobranças")
        compose.onNodeWithText("Criar Cobrança Avulsa").assertIsDisplayed()
    }

    @Test
    fun expenseOpensAndCanBeReopenedOnFinancialScreen() {
        choose("Registrar Despesa")
        assertFinancialTab("A Pagar")
        compose.onNodeWithText("Registrar Despesa").assertIsDisplayed()
        compose.runOnIdle { assertFalse(viewModel.triggerOpenAddExpense.value) }
        compose.onNodeWithText("Cancelar").performClick()
        choose("Registrar Despesa")
        assertFinancialTab("A Pagar")
        compose.onNodeWithText("Registrar Despesa").assertIsDisplayed()
    }

    @Test
    fun paymentWithoutReceivablesShowsFeedbackFromBillingCenter() {
        // Fresh Robolectric storage has no financial records. Open another subtab first.
        compose.onNodeWithText("Financeiro").performClick()
        compose.onNodeWithText("A Pagar").performClick()
        choose("Registrar Recebimento (Baixa)")
        assertFinancialTab("Cobranças")
        compose.runOnIdle {
            assertFalse(viewModel.triggerOpenAddPayment.value)
            assertTrue(viewModel.userFeedbackMessage.value.orEmpty()
                .startsWith("Nenhuma cobrança pendente"))
        }
    }

    @Test
    fun clientRegistrationStillOpensInClientsTab() {
        choose("Cadastrar Novo Aluno")
        compose.onNodeWithText("Alunos").assertIsSelected()
        compose.onNodeWithText("Cadastrar Aluno").assertIsDisplayed()
        compose.runOnIdle { assertFalse(viewModel.triggerOpenAddClient.value) }
    }
}
