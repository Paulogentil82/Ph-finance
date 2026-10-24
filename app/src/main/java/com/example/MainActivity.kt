package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.phfinance.ui.components.QuickActionModal
import com.example.phfinance.ui.screens.AuthScreen
import com.example.phfinance.ui.screens.ClientsScreen
import com.example.phfinance.ui.screens.DashboardScreen
import com.example.phfinance.ui.screens.FinancialScreen
import com.example.phfinance.ui.screens.ManagementScreen
import com.example.phfinance.ui.screens.ReportsScreen
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.theme.PHFinanceTheme
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {

    private val viewModel: PhFinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "FirebaseApp init handled: ${e.message}")
        }

        try {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.phfinance.util.NotificationWorker>(
                1, java.util.concurrent.TimeUnit.DAYS
            ).build()
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ph_finance_daily_notification",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "WorkManager schedule error: ${e.message}")
        }

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()

            PHFinanceTheme(darkTheme = isDark) {
                AppContent(viewModel)
            }
        }
    }
}

private fun getSafeUser(): FirebaseUser? {
    return try {
        Firebase.auth.currentUser
    } catch (e: Exception) {
        null
    }
}

@Composable
fun AppContent(viewModel: PhFinanceViewModel) {
    var isLocalAuthenticated by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf(getSafeUser()) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            try {
                currentUser = auth.currentUser
            } catch (e: Exception) {
                // Ignore
            }
        }
        val safeAuth = try { Firebase.auth } catch (e: Exception) { null }
        safeAuth?.addAuthStateListener(listener)
        onDispose {
            safeAuth?.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null && !isLocalAuthenticated) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = getSafeUser()
                isLocalAuthenticated = true
            }
        )
    } else {
        MainAppScaffold(
            viewModel = viewModel,
            onSignOut = {
                try { Firebase.auth.signOut() } catch (e: Exception) {}
                currentUser = null
                isLocalAuthenticated = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: PhFinanceViewModel,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showQuickActionModal by remember { mutableStateOf(false) }
    val isDark by viewModel.isDarkMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PH Finance",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleDarkMode() }) {
                        Icon(
                            if (isDark) Icons.Default.WbSunny else Icons.Default.Nightlight,
                            contentDescription = "Alternar Tema"
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Sair")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Início") },
                    label = { Text("Início") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.People, contentDescription = "Alunos") },
                    label = { Text("Alunos") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Payments, contentDescription = "Financeiro") },
                    label = { Text("Financeiro") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Relatórios") },
                    label = { Text("Relatórios") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "Gestão") },
                    label = { Text("Gestão") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickActionModal = true },
                containerColor = EmeraldPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ação Rápida", tint = MaterialTheme.colorScheme.surface)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(viewModel)
                1 -> ClientsScreen(viewModel)
                2 -> FinancialScreen(viewModel)
                3 -> ReportsScreen(viewModel)
                4 -> ManagementScreen(viewModel)
            }
        }
    }

    if (showQuickActionModal) {
        QuickActionModal(
            onDismiss = { showQuickActionModal = false },
            onAddClient = {
                showQuickActionModal = false
                viewModel.triggerAddClientForm()
                selectedTab = 1
            },
            onAddReceivable = {
                showQuickActionModal = false
                viewModel.triggerAddReceivableForm()
                selectedTab = 2
            },
            onAddExpense = {
                showQuickActionModal = false
                viewModel.triggerAddExpenseForm()
                selectedTab = 2
            },
            onAddPayment = {
                showQuickActionModal = false
                viewModel.triggerAddPaymentForm()
                selectedTab = 2
            }
        )
    }
}
