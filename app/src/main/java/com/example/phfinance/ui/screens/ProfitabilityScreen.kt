package com.example.phfinance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.phfinance.data.local.ApportionmentPolicyEntity
import com.example.phfinance.data.local.CostAllocationEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.PricingSimulationScenarioEntity
import com.example.phfinance.data.local.TimeTrackingEntity
import com.example.phfinance.data.model.ApportionmentMethod
import com.example.phfinance.data.model.ApportionmentPreviewItem
import com.example.phfinance.data.model.CostAllocationType
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.PricingScenarioResult
import com.example.phfinance.data.model.ServiceProfitabilitySummary
import com.example.phfinance.data.model.StudentProfitabilitySummary
import com.example.phfinance.data.model.TimeActivityType
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.theme.StatusRed
import com.example.phfinance.ui.theme.StatusYellow
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import com.example.phfinance.util.ProfitabilityEngine
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun ProfitabilityScreen(viewModel: PhFinanceViewModel) {
    val context = LocalContext.current
    val competence by viewModel.selectedCompetence.collectAsState()
    val overview by viewModel.profitabilityMetrics.collectAsState()
    val serviceSummaries by viewModel.serviceProfitabilityList.collectAsState()
    val studentSummaries by viewModel.studentProfitabilityList.collectAsState()
    val costAllocations by viewModel.costAllocations.collectAsState()
    val policies by viewModel.apportionmentPolicies.collectAsState()
    val timeTrackings by viewModel.timeTrackings.collectAsState()
    val pricingSimulations by viewModel.pricingSimulations.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val clients by viewModel.allClients.collectAsState()
    val services by viewModel.services.collectAsState()
    val contracts by viewModel.contracts.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDirectCostDialog by remember { mutableStateOf(false) }
    var showApportionmentDialog by remember { mutableStateOf(false) }
    var showTimeTrackingDialog by remember { mutableStateOf(false) }
    var showPricingSimulatorDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profitability_screen")
    ) {
        // Header com competência e ações de exportação
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Central de Rentabilidade",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Competência: $competence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.exportProfitabilityPdf(context) },
                    modifier = Modifier.testTag("export_pdf_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF")
                }
                OutlinedButton(
                    onClick = {
                        if (selectedTab == 1) {
                            viewModel.exportServiceProfitabilityCsv(context)
                        } else {
                            viewModel.exportStudentProfitabilityCsv(context)
                        }
                    },
                    modifier = Modifier.testTag("export_csv_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CSV")
                }
            }
        }

        // Subtabs de navegação interna
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Visão Geral") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Por Serviço") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Por Aluno") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Rateio de Custos") })
            Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Tempo Dedicado") })
            Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }, text = { Text("Simulador de Preço") })
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> ProfitabilityOverviewTab(
                    overview = overview,
                    onNavigateToRateio = { selectedTab = 3 },
                    onNavigateToTempo = { selectedTab = 4 }
                )
                1 -> ServicesProfitabilityTab(summaries = serviceSummaries)
                2 -> StudentsProfitabilityTab(summaries = studentSummaries)
                3 -> CostAllocationTab(
                    allocations = costAllocations,
                    policies = policies,
                    expenses = expenses,
                    clients = clients,
                    contracts = contracts,
                    services = services,
                    competence = competence,
                    onAddDirectCost = { showDirectCostDialog = true },
                    onAddApportionment = { showApportionmentDialog = true },
                    onDeleteAllocation = { id ->
                        viewModel.deleteCostAllocation(id) { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                4 -> TimeTrackingTab(
                    trackings = timeTrackings,
                    clients = clients,
                    services = services,
                    onAddTimeTracking = { showTimeTrackingDialog = true },
                    onDeleteTracking = { viewModel.deleteTimeTracking(it) }
                )
                5 -> PricingSimulatorTab(
                    services = services,
                    existingSimulations = pricingSimulations,
                    onOpenSimulator = { showPricingSimulatorDialog = true },
                    onDeleteSimulation = { viewModel.deletePricingSimulation(it) },
                    onExportCsv = { sName, scenarios ->
                        viewModel.exportPricingSimulationCsv(context, sName, scenarios)
                    }
                )
            }
        }
    }

    if (showDirectCostDialog) {
        DirectCostAllocationDialog(
            expenses = expenses,
            clients = clients,
            contracts = contracts,
            services = services,
            competence = competence,
            existingAllocations = costAllocations,
            onDismiss = { showDirectCostDialog = false },
            onSave = { allocation ->
                viewModel.saveCostAllocation(allocation) { ok, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (ok) showDirectCostDialog = false
                }
            }
        )
    }

    if (showApportionmentDialog) {
        ApportionmentDialog(
            expenses = expenses,
            clients = clients,
            contracts = contracts,
            services = services,
            competence = competence,
            existingAllocations = costAllocations,
            timeTrackings = timeTrackings,
            receivables = viewModel.receivables.collectAsState().value,
            onDismiss = { showApportionmentDialog = false },
            onApply = { policy, expId, previewItems, notes ->
                viewModel.applyApportionment(policy, expId, competence, previewItems, notes) { ok, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (ok) showApportionmentDialog = false
                }
            }
        )
    }

    if (showTimeTrackingDialog) {
        TimeTrackingDialog(
            clients = clients,
            services = services,
            contracts = contracts,
            competence = competence,
            onDismiss = { showTimeTrackingDialog = false },
            onSave = { entry ->
                viewModel.saveTimeTracking(entry) { ok, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (ok) showTimeTrackingDialog = false
                }
            }
        )
    }

    if (showPricingSimulatorDialog) {
        PricingSimulatorDialog(
            services = services,
            contracts = contracts,
            costAllocations = costAllocations,
            timeTrackings = timeTrackings,
            onDismiss = { showPricingSimulatorDialog = false },
            onCalculateScenarios = { sId, sName, curPrice, propPrice, curClients, dirCost, indCost, avgHours, churn, margin ->
                viewModel.simulatePricingScenarios(sId, sName, curPrice, propPrice, curClients, dirCost, indCost, avgHours, churn, margin)
            },
            onSaveScenario = { scenarioEntity ->
                viewModel.savePricingSimulation(scenarioEntity)
                Toast.makeText(context, "Cenário de precificação salvo com sucesso.", Toast.LENGTH_SHORT).show()
                showPricingSimulatorDialog = false
            }
        )
    }
}

// =========================================================================
// 1. VISÃO GERAL TAB
// =========================================================================
@Composable
fun ProfitabilityOverviewTab(
    overview: com.example.phfinance.data.model.ProfitabilityMetricResult,
    onNavigateToRateio: () -> Unit,
    onNavigateToTempo: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Demonstrativo Gerencial da Operação", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Margem de Contribuição e Resultado Líquido Operacional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))

                    MetricRow("Receita Líquida Recebida", CurrencyUtils.formatCents(overview.netRevenueCents), EmeraldPrimary, bold = true)
                    MetricRow("(-) Custos Diretos Atribuídos", CurrencyUtils.formatCents(overview.directCostsCents), StatusRed)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(modifier = Modifier.height(6.dp))
                    MetricRow("(=) Margem de Contribuição (MC)", CurrencyUtils.formatCents(overview.contributionMarginCents), EmeraldPrimary, bold = true)
                    Text(
                        text = "Percentual de Margem: ${String.format("%.1f%%", overview.contributionMarginPercent)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    MetricRow("(-) Custos Indiretos Alocados", CurrencyUtils.formatCents(overview.allocatedIndirectCostsCents), StatusYellow)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(modifier = Modifier.height(6.dp))
                    MetricRow("(=) Resultado Gerencial Operacional", CurrencyUtils.formatCents(overview.managerialOperatingResultCents), if (overview.managerialOperatingResultCents >= 0) EmeraldPrimary else StatusRed, bold = true)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Produtividade & Custo Econômico do Tempo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Custo de oportunidade das horas dedicadas pelo profissional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Horas Dedicadas", style = MaterialTheme.typography.labelSmall)
                            Text("${String.format("%.1f", overview.dedicatedHours)}h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Receita Média / Hora", style = MaterialTheme.typography.labelSmall)
                            Text(CurrencyUtils.formatCents(overview.revenuePerHourCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        }
                        Column {
                            Text("Custo Oportunidade", style = MaterialTheme.typography.labelSmall)
                            Text(CurrencyUtils.formatCents(overview.economicCostOfTimeCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusYellow)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricRow("(=) Resultado Econômico do Negócio", CurrencyUtils.formatCents(overview.economicResultCents), if (overview.economicResultCents >= 0) EmeraldPrimary else StatusRed, bold = true)
                    Text(
                        text = "* Custo econômico do tempo não representa saída de caixa e não se confunde com pró-labore contábil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigateToRateio,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configurar Rateio")
                }
                OutlinedButton(
                    onClick = onNavigateToTempo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.HourglassBottom, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Registrar Horas")
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = if (bold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium)
        Text(text = value, style = if (bold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

// =========================================================================
// 2. POR SERVIÇO TAB
// =========================================================================
@Composable
fun ServicesProfitabilityTab(summaries: List<ServiceProfitabilitySummary>) {
    if (summaries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum serviço ou contrato cadastrado.")
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(summaries) { s ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.serviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${s.category} • ${s.activeContractsCount} contratos ativos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (s.contributionMarginPercent >= 50.0) EmeraldPrimary.copy(alpha = 0.15f) else StatusYellow.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = String.format("%.1f%% MC", s.contributionMarginPercent),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (s.contributionMarginPercent >= 50.0) EmeraldPrimary else StatusYellow,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Receita Líquida", style = MaterialTheme.typography.labelSmall)
                                Text(CurrencyUtils.formatCents(s.netRevenueCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Custos Diretos", style = MaterialTheme.typography.labelSmall)
                                Text(CurrencyUtils.formatCents(s.directCostsCents), style = MaterialTheme.typography.bodyMedium, color = StatusRed)
                            }
                            Column {
                                Text("Margem Contrib.", style = MaterialTheme.typography.labelSmall)
                                Text(CurrencyUtils.formatCents(s.contributionMarginCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Rateio Indireto: ${CurrencyUtils.formatCents(s.allocatedIndirectCostsCents)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Resultado Gerencial: ${CurrencyUtils.formatCents(s.managerialOperatingResultCents)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Horas: ${String.format("%.1fh", s.dedicatedHours)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Receita/Hora: ${CurrencyUtils.formatCents(s.revenuePerHourCents)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. POR ALUNO TAB
// =========================================================================
@Composable
fun StudentsProfitabilityTab(summaries: List<StudentProfitabilitySummary>) {
    if (summaries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum aluno cadastrado.")
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(summaries) { s ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${s.activeContractsCount} contratos ativos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.15f))
                            ) {
                                Text(
                                    text = String.format("%.1f%% MC", s.contributionMarginPercent),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Recebido", style = MaterialTheme.typography.labelSmall)
                                Text(CurrencyUtils.formatCents(s.receivedRevenueCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Custos Diretos", style = MaterialTheme.typography.labelSmall)
                                Text(CurrencyUtils.formatCents(s.directCostsCents), style = MaterialTheme.typography.bodyMedium, color = StatusRed)
                            }
                            Column {
                                Text("Margem Contrib.", style = MaterialTheme.typography.labelSmall)
                                Text(CurrencyUtils.formatCents(s.contributionMarginCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Rateio Indireto: ${CurrencyUtils.formatCents(s.allocatedIndirectCostsCents)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Resultado Gerencial: ${CurrencyUtils.formatCents(s.managerialOperatingResultCents)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Horas Dedicadas: ${String.format("%.1fh", s.dedicatedHours)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Receita/Hora: ${CurrencyUtils.formatCents(s.revenuePerHourCents)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 4. ATRIBUIÇÃO & RATEIO TAB
// =========================================================================
@Composable
fun CostAllocationTab(
    allocations: List<CostAllocationEntity>,
    policies: List<ApportionmentPolicyEntity>,
    expenses: List<ExpenseEntity>,
    clients: List<com.example.phfinance.data.local.ClientEntity>,
    contracts: List<com.example.phfinance.data.local.ContractEntity>,
    services: List<com.example.phfinance.data.local.ServiceEntity>,
    competence: String,
    onAddDirectCost: () -> Unit,
    onAddApportionment: () -> Unit,
    onDeleteAllocation: (String) -> Unit
) {
    val clientMap = clients.associateBy { it.id }
    val expenseMap = expenses.associateBy { it.id }
    val serviceMap = services.associateBy { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddDirectCost,
                modifier = Modifier.weight(1f).testTag("add_direct_cost_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Custo Direto")
            }
            OutlinedButton(
                onClick = onAddApportionment,
                modifier = Modifier.weight(1f).testTag("apply_apportionment_button")
            ) {
                Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Executar Rateio")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Custos Atribuídos na Competência ($competence)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (allocations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma atribuição de custo cadastrada.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allocations) { a ->
                    val exp = expenseMap[a.expenseId]
                    val cli = a.clientId?.let { clientMap[it] }
                    val srv = a.serviceId?.let { serviceMap[it] }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exp?.description ?: "Despesa Original",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when (a.allocationType) {
                                        CostAllocationType.DIRETO_CLIENTE -> "Direto Aluno: ${cli?.fullName ?: "Aluno"}"
                                        CostAllocationType.DIRETO_CONTRATO -> "Direto Contrato: ${cli?.fullName ?: "Aluno"}"
                                        CostAllocationType.DIRETO_SERVICO -> "Direto Serviço: ${srv?.name ?: "Serviço"}"
                                        CostAllocationType.INDIRETO_RATEADO -> "Rateio Indireto • ${cli?.fullName ?: "Aluno"}"
                                        else -> a.allocationType.label
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (a.notes.isNotBlank()) {
                                    Text(
                                        text = a.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyUtils.formatCents(a.allocatedAmountCents),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusRed
                                )
                                IconButton(onClick = { onDeleteAllocation(a.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = StatusRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. CONTROLE DE TEMPO TAB
// =========================================================================
@Composable
fun TimeTrackingTab(
    trackings: List<TimeTrackingEntity>,
    clients: List<com.example.phfinance.data.local.ClientEntity>,
    services: List<com.example.phfinance.data.local.ServiceEntity>,
    onAddTimeTracking: () -> Unit,
    onDeleteTracking: (String) -> Unit
) {
    val clientMap = clients.associateBy { it.id }
    val serviceMap = services.associateBy { it.id }

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
            Column {
                Text("Registro de Tempo Dedicado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Controle manual e cronômetro de atendimento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAddTimeTracking, modifier = Modifier.testTag("add_time_entry_button")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Registrar")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (trackings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum registro de tempo cadastrado.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trackings) { t ->
                    val cli = t.clientId?.let { clientMap[it] }
                    val srv = t.serviceId?.let { serviceMap[it] }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = t.activityType.name.replace("_", " "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Destino: ${cli?.fullName ?: srv?.name ?: "Atendimento Geral"} • ${DateUtils.formatDate(t.date)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (t.notes.isNotBlank()) {
                                    Text(
                                        text = t.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${t.durationMinutes} min (${String.format("%.1f", t.durationMinutes / 60.0)}h)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                                IconButton(onClick = { onDeleteTracking(t.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = StatusRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 6. SIMULADOR DE PREÇOS TAB
// =========================================================================
@Composable
fun PricingSimulatorTab(
    services: List<com.example.phfinance.data.local.ServiceEntity>,
    existingSimulations: List<PricingSimulationScenarioEntity>,
    onOpenSimulator: () -> Unit,
    onDeleteSimulation: (String) -> Unit,
    onExportCsv: (String, List<PricingScenarioResult>) -> Unit
) {
    val serviceMap = services.associateBy { it.id }

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
            Column {
                Text("Simulador de Precificação & Elasticidade", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Cenários conservador, base e otimista com break-even", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onOpenSimulator, modifier = Modifier.testTag("open_pricing_simulator_button")) {
                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Simular")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (existingSimulations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma simulação salva. Toque em 'Simular' para projetar reajustes.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(existingSimulations) { sim ->
                    val srv = serviceMap[sim.serviceId]
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(sim.scenarioName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Serviço: ${srv?.name ?: "Serviço"} • Tipo: ${sim.scenarioType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteSimulation(sim.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = StatusRed)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Preço Proposto", style = MaterialTheme.typography.labelSmall)
                                    Text(CurrencyUtils.formatCents(sim.proposedPriceCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Receita Projetada", style = MaterialTheme.typography.labelSmall)
                                    Text(CurrencyUtils.formatCents(sim.projectedRevenueCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                }
                                Column {
                                    Text("MC Projetada", style = MaterialTheme.typography.labelSmall)
                                    Text(CurrencyUtils.formatCents(sim.projectedContributionMarginCents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Resultado Operacional: ${CurrencyUtils.formatCents(sim.projectedOperatingResultCents)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("Break-Even: ${sim.breakEvenClientsCount} alunos", style = MaterialTheme.typography.bodySmall, color = StatusYellow)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// DIALOGS: ATRIBUIÇÃO DIRETA, RATEIO, TEMPO, SIMULADOR
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectCostAllocationDialog(
    expenses: List<ExpenseEntity>,
    clients: List<com.example.phfinance.data.local.ClientEntity>,
    contracts: List<com.example.phfinance.data.local.ContractEntity>,
    services: List<com.example.phfinance.data.local.ServiceEntity>,
    competence: String,
    existingAllocations: List<CostAllocationEntity>,
    onDismiss: () -> Unit,
    onSave: (CostAllocationEntity) -> Unit
) {
    var selectedExpense by remember { mutableStateOf(expenses.firstOrNull()) }
    var allocationType by remember { mutableStateOf<CostAllocationType>(CostAllocationType.DIRETO_CLIENTE) }
    var selectedClient by remember { mutableStateOf(clients.firstOrNull()) }
    var selectedService by remember { mutableStateOf(services.firstOrNull()) }
    var valueText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val availableCents = selectedExpense?.let {
        ProfitabilityEngine.getAvailableAmountForAllocation(it, existingAllocations)
    } ?: 0L

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("Atribuir Custo Direto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Vincule uma despesa a um cliente ou serviço", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(14.dp))

                // Seleção de Despesa
                Text("Despesa Financeira", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.height(110.dp)) {
                    items(expenses) { exp ->
                        val avail = ProfitabilityEngine.getAvailableAmountForAllocation(exp, existingAllocations)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedExpense = exp
                                    valueText = (avail / 100).toString()
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = exp.description,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selectedExpense?.id == exp.id) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "Disp: ${CurrencyUtils.formatCents(avail)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedExpense?.id == exp.id) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tipo de Atribuição
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = allocationType == CostAllocationType.DIRETO_CLIENTE,
                        onClick = { allocationType = CostAllocationType.DIRETO_CLIENTE },
                        label = { Text("Por Aluno") }
                    )
                    FilterChip(
                        selected = allocationType == CostAllocationType.DIRETO_SERVICO,
                        onClick = { allocationType = CostAllocationType.DIRETO_SERVICO },
                        label = { Text("Por Serviço") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (allocationType == CostAllocationType.DIRETO_CLIENTE) {
                    Text("Aluno Destino: ${selectedClient?.fullName ?: "Nenhum"}", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(modifier = Modifier.height(70.dp)) {
                        items(clients) { c ->
                            Text(
                                text = c.fullName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedClient = c }
                                    .padding(vertical = 2.dp),
                                color = if (selectedClient?.id == c.id) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Text("Serviço Destino: ${selectedService?.name ?: "Nenhum"}", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(modifier = Modifier.height(70.dp)) {
                        items(services) { s ->
                            Text(
                                text = s.name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedService = s }
                                    .padding(vertical = 2.dp),
                                color = if (selectedService?.id == s.id) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Valor a Atribuir (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observação / Justificativa") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val exp = selectedExpense
                            val valCents = CurrencyUtils.parseToCents(valueText)
                            if (exp != null && valCents > 0) {
                                val entity = CostAllocationEntity(
                                    id = UUID.randomUUID().toString(),
                                    expenseId = exp.id,
                                    allocationType = allocationType,
                                    clientId = if (allocationType == CostAllocationType.DIRETO_CLIENTE) selectedClient?.id else null,
                                    serviceId = if (allocationType == CostAllocationType.DIRETO_SERVICO) selectedService?.id else null,
                                    competence = competence,
                                    allocatedAmountCents = valCents,
                                    allocationPercentage = if (exp.valueCents > 0) (valCents.toDouble() / exp.valueCents) * 100.0 else 0.0,
                                    notes = notes
                                )
                                onSave(entity)
                            }
                        }
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
fun ApportionmentDialog(
    expenses: List<ExpenseEntity>,
    clients: List<com.example.phfinance.data.local.ClientEntity>,
    contracts: List<com.example.phfinance.data.local.ContractEntity>,
    services: List<com.example.phfinance.data.local.ServiceEntity>,
    competence: String,
    existingAllocations: List<CostAllocationEntity>,
    timeTrackings: List<TimeTrackingEntity>,
    receivables: List<com.example.phfinance.data.local.ReceivableEntity>,
    onDismiss: () -> Unit,
    onApply: (ApportionmentPolicyEntity, String, List<ApportionmentPreviewItem>, String) -> Unit
) {
    var selectedExpense by remember { mutableStateOf(expenses.firstOrNull()) }
    var selectedMethod by remember { mutableStateOf(ApportionmentMethod.PROPORCIONAL_RECEITA) }
    var previewItems by remember { mutableStateOf<List<ApportionmentPreviewItem>>(emptyList()) }
    var notes by remember { mutableStateOf("") }

    val activeContracts = contracts.filter { it.status.name == "ATIVO" }
    val availableCents = selectedExpense?.let {
        ProfitabilityEngine.getAvailableAmountForAllocation(it, existingAllocations)
    } ?: 0L

    // Atualiza preview ao alterar método ou despesa
    LaunchedEffect(selectedExpense, selectedMethod) {
        if (selectedExpense != null && availableCents > 0) {
            previewItems = ProfitabilityEngine.calculateApportionmentPreview(
                method = selectedMethod,
                totalIndirectCents = availableCents,
                activeContracts = activeContracts,
                clients = clients,
                timeTrackings = timeTrackings,
                receivablesInPeriod = receivables.filter { it.competence == competence }
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("Executar Rateio de Despesa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Distribui custos operacionais entre contratos ativos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(12.dp))

                Text("Selecione a Despesa Indireta", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.height(90.dp)) {
                    items(expenses) { exp ->
                        val avail = ProfitabilityEngine.getAvailableAmountForAllocation(exp, existingAllocations)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedExpense = exp }
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                exp.description,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selectedExpense?.id == exp.id) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                CurrencyUtils.formatCents(avail),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedExpense?.id == exp.id) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Método de Rateio", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedMethod == ApportionmentMethod.PROPORCIONAL_RECEITA,
                        onClick = { selectedMethod = ApportionmentMethod.PROPORCIONAL_RECEITA },
                        label = { Text("Por Receita") }
                    )
                    FilterChip(
                        selected = selectedMethod == ApportionmentMethod.PROPORCIONAL_TEMPO,
                        onClick = { selectedMethod = ApportionmentMethod.PROPORCIONAL_TEMPO },
                        label = { Text("Por Tempo") }
                    )
                    FilterChip(
                        selected = selectedMethod == ApportionmentMethod.QUANTIDADE_CLIENTES,
                        onClick = { selectedMethod = ApportionmentMethod.QUANTIDADE_CLIENTES },
                        label = { Text("Igualitário") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Pré-visualização do Impacto (${previewItems.size} contratos)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.height(110.dp)) {
                    items(previewItems) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.clientName, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${String.format("%.1f%%", item.percentage)} • ${CurrencyUtils.formatCents(item.allocatedCents)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val exp = selectedExpense
                            if (exp != null && previewItems.isNotEmpty()) {
                                val policy = ApportionmentPolicyEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = "Rateio ${selectedMethod.name}",
                                    policyType = selectedMethod,
                                    validFrom = competence,
                                    notes = notes
                                )
                                onApply(policy, exp.id, previewItems, notes)
                            }
                        }
                    ) {
                        Text("Confirmar Rateio")
                    }
                }
            }
        }
    }
}

@Composable
fun TimeTrackingDialog(
    clients: List<com.example.phfinance.data.local.ClientEntity>,
    services: List<com.example.phfinance.data.local.ServiceEntity>,
    contracts: List<com.example.phfinance.data.local.ContractEntity>,
    competence: String,
    onDismiss: () -> Unit,
    onSave: (TimeTrackingEntity) -> Unit
) {
    var selectedClient by remember { mutableStateOf(clients.firstOrNull()) }
    var selectedActivity by remember { mutableStateOf(TimeActivityType.ATENDIMENTO_PRESENCIAL) }
    var durationMinutesText by remember { mutableStateOf("60") }
    var notes by remember { mutableStateOf("") }

    // Stopwatch opcional
    var isStopwatchRunning by remember { mutableStateOf(false) }
    var stopwatchSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isStopwatchRunning) {
        while (isStopwatchRunning) {
            delay(1000)
            stopwatchSeconds++
            durationMinutesText = ((stopwatchSeconds + 59) / 60).toString()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text("Registrar Atendimento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(10.dp))

                // Cronômetro card
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cronômetro Opcional", style = MaterialTheme.typography.labelSmall)
                            val m = stopwatchSeconds / 60
                            val s = stopwatchSeconds % 60
                            Text(String.format("%02d:%02d", m, s), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { isStopwatchRunning = !isStopwatchRunning },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isStopwatchRunning) StatusRed else EmeraldPrimary)
                        ) {
                            Icon(if (isStopwatchRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isStopwatchRunning) "Pausar" else "Iniciar")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Aluno: ${selectedClient?.fullName ?: "Nenhum"}", style = MaterialTheme.typography.labelSmall)
                LazyColumn(modifier = Modifier.height(70.dp)) {
                    items(clients) { c ->
                        Text(
                            text = c.fullName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().clickable { selectedClient = c }.padding(vertical = 2.dp),
                            color = if (selectedClient?.id == c.id) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = durationMinutesText,
                    onValueChange = { durationMinutesText = it },
                    label = { Text("Duração (Minutos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val mins = durationMinutesText.toIntOrNull() ?: 60
                            val entity = TimeTrackingEntity(
                                id = UUID.randomUUID().toString(),
                                clientId = selectedClient?.id,
                                competence = competence,
                                durationMinutes = mins,
                                activityType = selectedActivity,
                                notes = notes
                            )
                            onSave(entity)
                        }
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
fun PricingSimulatorDialog(
    services: List<com.example.phfinance.data.local.ServiceEntity>,
    contracts: List<com.example.phfinance.data.local.ContractEntity>,
    costAllocations: List<CostAllocationEntity>,
    timeTrackings: List<TimeTrackingEntity>,
    onDismiss: () -> Unit,
    onCalculateScenarios: (String, String, Long, Long, Int, Long, Long, Double, Double, Double) -> List<PricingScenarioResult>,
    onSaveScenario: (PricingSimulationScenarioEntity) -> Unit
) {
    var selectedService by remember { mutableStateOf(services.firstOrNull()) }
    var proposedPriceText by remember { mutableStateOf("250") }
    var churnPercentText by remember { mutableStateOf("10") }
    var targetMarginText by remember { mutableStateOf("60") }
    var generatedScenarios by remember { mutableStateOf<List<PricingScenarioResult>>(emptyList()) }

    val activeCount = contracts.count { it.serviceId == selectedService?.id && it.status.name == "ATIVO" }
    val currentPriceCents = selectedService?.defaultPriceCents ?: 20000L

    LaunchedEffect(selectedService, proposedPriceText, churnPercentText, targetMarginText) {
        val s = selectedService
        if (s != null) {
            val propCents = CurrencyUtils.parseToCents(proposedPriceText)
            val churn = churnPercentText.toDoubleOrNull() ?: 10.0
            val targetMargin = targetMarginText.toDoubleOrNull() ?: 60.0

            generatedScenarios = onCalculateScenarios(
                s.id,
                s.name,
                currentPriceCents,
                propCents,
                activeCount.coerceAtLeast(1),
                1000L, // R$ 10,00 custo direto estimado por cliente
                2500L, // R$ 25,00 despesa indireta rateada estimada
                4.0,   // 4h dedicadas estimadas
                churn,
                targetMargin
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text("Simulador de Precificação", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Avalie cenários antes de alterar preços em contratos reais", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(10.dp))

                Text("Serviço Selecionado: ${selectedService?.name ?: "Nenhum"}", style = MaterialTheme.typography.labelSmall)
                LazyColumn(modifier = Modifier.height(60.dp)) {
                    items(services) { s ->
                        Text(
                            text = "${s.name} (Atual: ${CurrencyUtils.formatCents(s.defaultPriceCents)})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().clickable { selectedService = s }.padding(vertical = 2.dp),
                            color = if (selectedService?.id == s.id) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = proposedPriceText,
                        onValueChange = { proposedPriceText = it },
                        label = { Text("Preço Proposto (R$)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = churnPercentText,
                        onValueChange = { churnPercentText = it },
                        label = { Text("Perda Alunos (%)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Comparativo de Cenários", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.height(130.dp)) {
                    items(generatedScenarios) { scen ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(scen.scenarioName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Alunos: ${scen.projectedClients}", style = MaterialTheme.typography.bodySmall)
                                    Text("Receita: ${CurrencyUtils.formatCents(scen.projectedRevenueCents)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("MC: ${CurrencyUtils.formatCents(scen.projectedContributionMarginCents)} (${String.format("%.1f%%", scen.projectedMarginPercent)})", style = MaterialTheme.typography.bodySmall)
                                    Text("Break-Even: ${scen.breakEvenClients} alunos", style = MaterialTheme.typography.bodySmall, color = StatusYellow)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Fechar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val baseScen = generatedScenarios.firstOrNull { it.scenarioType == com.example.phfinance.data.model.PricingScenarioType.BASE } ?: generatedScenarios.firstOrNull()
                            val s = selectedService
                            if (baseScen != null && s != null) {
                                val entity = com.example.phfinance.util.PricingSimulatorEngine.toEntity(
                                    serviceId = s.id,
                                    scenario = baseScen,
                                    currentPriceCents = currentPriceCents,
                                    proposedPriceCents = CurrencyUtils.parseToCents(proposedPriceText),
                                    currentClientsCount = activeCount.coerceAtLeast(1),
                                    directCostPerClientCents = 1000L,
                                    allocatedExpensePerClientCents = 2500L,
                                    averageHours = 4.0,
                                    churnPercent = churnPercentText.toDoubleOrNull() ?: 10.0,
                                    targetMarginPercent = targetMarginText.toDoubleOrNull() ?: 60.0,
                                    notes = "Simulação gerada no módulo de Precificação"
                                )
                                onSaveScenario(entity)
                            }
                        }
                    ) {
                        Text("Salvar Cenário Base")
                    }
                }
            }
        }
    }
}
