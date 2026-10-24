package com.example.phfinance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.theme.StatusRed
import com.example.phfinance.ui.theme.StatusYellow
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import com.example.phfinance.util.GrowthSimulationParams

@Composable
fun GoalSimulatorComponent(viewModel: PhFinanceViewModel) {
    val goal by viewModel.goal.collectAsState()
    val metrics by viewModel.dashboardMetrics.collectAsState()
    val projections by viewModel.growthProjections.collectAsState()
    val currentParams by viewModel.growthParams.collectAsState()

    var newClientsText by remember { mutableStateOf(currentParams.monthlyNewClients.toString()) }
    var churnText by remember { mutableStateOf(currentParams.monthlyChurnPercent.toString()) }
    var fixedExpText by remember { mutableStateOf((currentParams.fixedExpensesCents / 100).toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // GOALS PROGRESS PANEL
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Painel de Metas do Negócio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                // Revenue Goal
                val targetRevenue = goal?.monthlyRevenueGoalCents ?: 1000000L
                val currentRevenue = metrics.monthReceivedNetCents
                val progress = if (targetRevenue > 0) (currentRevenue.toFloat() / targetRevenue.toFloat()).coerceIn(0f, 1f) else 0f

                Text("Meta Faturamento Mensal: ${CurrencyUtils.formatCents(currentRevenue)} / ${CurrencyUtils.formatCents(targetRevenue)}", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = EmeraldPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Active Clients Goal
                val targetClients = goal?.activeClientsGoal ?: 30
                val currentClients = metrics.activeClientsCount
                val clientProgress = if (targetClients > 0) (currentClients.toFloat() / targetClients.toFloat()).coerceIn(0f, 1f) else 0f

                Text("Meta Alunos Ativos: $currentClients / $targetClients alunos", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { clientProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = StatusYellow
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // DETERMINISTIC GROWTH SIMULATOR
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Simulador de Crescimento Financeiro Determinístico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Ajuste as premissas matemáticas e veja o resultado projetado:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newClientsText,
                        onValueChange = { newClientsText = it },
                        label = { Text("Novos/Mês") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = churnText,
                        onValueChange = { churnText = it },
                        label = { Text("Churn %") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = fixedExpText,
                        onValueChange = { fixedExpText = it },
                        label = { Text("Desp. Fixas (R$)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val newClients = newClientsText.toIntOrNull() ?: 3
                        val churn = churnText.toDoubleOrNull() ?: 5.0
                        val fixedExp = (fixedExpText.toLongOrNull() ?: 2000L) * 100L
                        viewModel.updateGrowthParams(
                            currentParams.copy(
                                monthlyNewClients = newClients,
                                monthlyChurnPercent = churn,
                                fixedExpensesCents = fixedExp
                            )
                        )
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Recalcular Projeções")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PROJECTIONS TABLE
        Text("Projeção Determinística de Crescimento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projections) { proj ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(proj.monthLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Alunos: ${proj.projectedClients}", fontSize = 12.sp)
                            Text("Receita Líquida: ${CurrencyUtils.formatCents(proj.netRevenueCents)}", fontSize = 12.sp)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Lucro: ${CurrencyUtils.formatCents(proj.operatingResultCents)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            Text("Margem: ${String.format("%.1f", proj.profitMarginPercent)}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
