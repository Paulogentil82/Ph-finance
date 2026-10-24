package com.example.phfinance.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.theme.StatusRed
import com.example.phfinance.ui.theme.StatusYellow
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import com.example.phfinance.util.DashboardMetrics
import com.example.phfinance.util.PhInsight

@Composable
fun DashboardScreen(viewModel: PhFinanceViewModel) {
    val metrics by viewModel.dashboardMetrics.collectAsState()
    val competence by viewModel.selectedCompetence.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val sixMonthsHistory by viewModel.sixMonthsHistory.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Competence Selector Header
        item {
            CompetenceSelectorBar(
                currentCompetence = competence,
                onCompetenceChanged = { viewModel.setCompetence(it) }
            )
        }

        // Available Liquid Balance Card (Hero Card)
        item {
            HeroBalanceCard(metrics)
        }

        // Core Financial Grid (2x2)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Receita Recebida",
                    value = CurrencyUtils.formatCents(metrics.monthReceivedNetCents),
                    icon = Icons.Default.ArrowUpward,
                    iconTint = EmeraldPrimary,
                    subtitle = "Líquida no mês"
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Despesas Pagas",
                    value = CurrencyUtils.formatCents(metrics.monthPaidOperationalExpensesCents),
                    icon = Icons.Default.ArrowDownward,
                    iconTint = StatusRed,
                    subtitle = "Operacionais"
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "A Receber",
                    value = CurrencyUtils.formatCents(metrics.totalPendingToReceiveCents),
                    icon = Icons.Default.AttachMoney,
                    iconTint = StatusYellow,
                    subtitle = "Pendente no mês"
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Resultado Op.",
                    value = CurrencyUtils.formatCents(metrics.operatingResultCents),
                    icon = if (metrics.operatingResultCents >= 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                    iconTint = if (metrics.operatingResultCents >= 0) EmeraldPrimary else StatusRed,
                    subtitle = "Receita - Despesa"
                )
            }
        }

        // Owner Withdrawal Distinction Card
        item {
            OwnerWithdrawalCard(metrics.monthOwnerWithdrawalsCents)
        }

        // Goal & KPI Progress
        item {
            GoalProgressCard(metrics)
        }

        // Key Business Indicators (MRR, Active Clients, Ticket Médio)
        item {
            BusinessIndicatorsRow(metrics)
        }

        // Visual Financial Chart (Receita x Despesa)
        item {
            FinancialBarChartCard(history = sixMonthsHistory)
        }

        // Insights PH Section
        if (insights.isNotEmpty()) {
            item {
                Text(
                    text = "Insights PH Personal",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            items(insights.size) { index ->
                InsightCard(insights[index])
            }
        }
    }
}

@Composable
fun CompetenceSelectorBar(
    currentCompetence: String,
    onCompetenceChanged: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                val parts = currentCompetence.split("/")
                if (parts.size == 2) {
                    var m = parts[0].toInt() - 1
                    var y = parts[1].toInt()
                    if (m < 1) {
                        m = 12
                        y -= 1
                    }
                    onCompetenceChanged(String.format("%02d/%d", m, y))
                }
            }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Mês Anterior")
            }

            Text(
                text = "Competência: $currentCompetence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(onClick = {
                val parts = currentCompetence.split("/")
                if (parts.size == 2) {
                    var m = parts[0].toInt() + 1
                    var y = parts[1].toInt()
                    if (m > 12) {
                        m = 1
                        y += 1
                    }
                    onCompetenceChanged(String.format("%02d/%d", m, y))
                }
            }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Próximo Mês")
            }
        }
    }
}

@Composable
fun HeroBalanceCard(metrics: DashboardMetrics) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Saldo em Contas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (metrics.totalOverdueCents > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusRed.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Vencidos: ${CurrencyUtils.formatCents(metrics.totalOverdueCents)}",
                            color = StatusRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = CurrencyUtils.formatCents(metrics.liquidBalanceCents),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Receita Contratada: ${CurrencyUtils.formatCents(metrics.monthContractedRevenueCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun MetricTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    subtitle: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun OwnerWithdrawalCard(withdrawalsCents: Long) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MoneyOff,
                    contentDescription = null,
                    tint = StatusYellow,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Retirada Pessoal do Proprietário",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Mantido separado das despesas operacionais",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = CurrencyUtils.formatCents(withdrawalsCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = StatusYellow
            )
        }
    }
}

@Composable
fun GoalProgressCard(metrics: DashboardMetrics) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Meta de Faturamento Mensal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "${String.format("%.1f", metrics.goalReachedPercent)}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (metrics.goalReachedPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = EmeraldPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Atingido: ${CurrencyUtils.formatCents(metrics.monthReceivedNetCents)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Meta: ${CurrencyUtils.formatCents(metrics.monthlyRevenueGoalCents)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BusinessIndicatorsRow(metrics: DashboardMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.People, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Alunos Ativos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${metrics.activeClientsCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Ticket Médio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyUtils.formatCents(metrics.ticketMedioCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("MRR Recorrente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyUtils.formatCents(metrics.mrrCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FinancialBarChartCard(history: List<com.example.phfinance.util.MonthRevenueExpensePair>) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Evolução Financeira (Últimos Meses)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Bar Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val barWidth = 20.dp.toPx()
                val spacing = 12.dp.toPx()
                val maxCents = (history.flatMap { listOf(it.revenueCents, it.expenseCents) }.maxOrNull() ?: 100000L).coerceAtLeast(100000L).toFloat()

                var xOffset = 12.dp.toPx()

                history.forEach { item ->
                    val revHeight = (item.revenueCents.toFloat() / maxCents) * size.height
                    val expHeight = (item.expenseCents.toFloat() / maxCents) * size.height

                    // Revenue Bar
                    drawRoundRect(
                        color = Color(0xFF10B981),
                        topLeft = Offset(xOffset, size.height - revHeight),
                        size = Size(barWidth, revHeight.coerceAtLeast(4f)),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )

                    // Expense Bar
                    drawRoundRect(
                        color = Color(0xFFEF4444),
                        topLeft = Offset(xOffset + barWidth + 2.dp.toPx(), size.height - expHeight),
                        size = Size(barWidth / 2, expHeight.coerceAtLeast(4f)),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )

                    xOffset += barWidth + (barWidth / 2) + spacing
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.size(10.dp).background(EmeraldPrimary, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Receita Líquida", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.size(10.dp).background(StatusRed, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Despesas Pagas", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun InsightCard(insight: PhInsight) {
    val containerColor = when (insight.level) {
        com.example.phfinance.util.InsightLevel.WARNING -> StatusYellow.copy(alpha = 0.15f)
        com.example.phfinance.util.InsightLevel.SUCCESS -> EmeraldPrimary.copy(alpha = 0.15f)
        com.example.phfinance.util.InsightLevel.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }

    val iconTint = when (insight.level) {
        com.example.phfinance.util.InsightLevel.WARNING -> StatusYellow
        com.example.phfinance.util.InsightLevel.SUCCESS -> EmeraldPrimary
        com.example.phfinance.util.InsightLevel.INFO -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = insight.calculationDetail,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = iconTint
                )
            }
        }
    }
}
