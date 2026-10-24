package com.example.phfinance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import com.example.phfinance.util.ReportExporter

data class ReportItem(
    val title: String,
    val category: String,
    val description: String
)

@Composable
fun ReportsScreen(viewModel: PhFinanceViewModel) {
    var selectedTab by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.TabRow(selectedTabIndex = selectedTab) {
            androidx.compose.material3.Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Relatórios & Extratos") }
            )
            androidx.compose.material3.Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Metas & Simulador") }
            )
        }

        when (selectedTab) {
            0 -> ReportsTab(viewModel)
            1 -> com.example.phfinance.ui.components.GoalSimulatorComponent(viewModel)
        }
    }
}

@Composable
fun ReportsTab(viewModel: PhFinanceViewModel) {
    val context = LocalContext.current
    val metrics by viewModel.dashboardMetrics.collectAsState()
    val receivables by viewModel.receivables.collectAsState()
    val clients by viewModel.allClients.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val dreResult by viewModel.monthlyDRE.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val reportList = listOf(
        ReportItem("Demonstrativo DRE (PDF)", "DRE Gerencial", "Demonstrativo gerencial completo em PDF comparando regime de caixa e competência."),
        ReportItem("Inadimplência e Atrasos (CSV)", "Risco", "Lista com dias de atraso e saldos devedores de alunos inadimplentes."),
        ReportItem("Contas a Receber e Cobranças (CSV)", "Financeiro", "Relatório completo de cobranças e faturamento por competência."),
        ReportItem("Despesas por Categoria (CSV)", "Operacional", "Distribuição das despesas operacionais e retiradas do proprietário."),
        ReportItem("Receita Recorrente (MRR)", "Métricas", "Análise de faturamento mensal fixo previsível da consultoria PH Personal.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Relatórios Gerenciais PH",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Button(onClick = {
                ReportExporter.exportMonthlyDREPdf(context, dreResult, settings)
            }) {
                Icon(Icons.Default.Assessment, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Baixar DRE PDF")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(reportList) { report ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Assessment,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.padding(end = 12.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(report.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(onClick = {
                            when {
                                report.title.contains("DRE") -> ReportExporter.exportMonthlyDREPdf(context, dreResult, settings)
                                report.title.contains("Inadimplência") -> ReportExporter.exportInadimplenciaCsv(context, receivables, clients)
                                report.title.contains("Despesas") -> ReportExporter.exportExpensesCsv(context, expenses)
                                else -> ReportExporter.exportReceivablesCsv(context, receivables, clients)
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartilhar")
                        }
                    }
                }
            }
        }
    }
}

