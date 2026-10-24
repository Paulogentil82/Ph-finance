package com.example.phfinance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.theme.StatusRed
import com.example.phfinance.ui.theme.StatusYellow
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import com.example.phfinance.util.ReportExporter

@Composable
fun MonthlyClosureComponent(viewModel: PhFinanceViewModel) {
    val context = LocalContext.current
    val competence by viewModel.selectedCompetence.collectAsState()
    val dreResult by viewModel.monthlyDRE.collectAsState()
    val closure by viewModel.currentClosure.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showReopenDialog by remember { mutableStateOf(false) }
    var closureNotes by remember { mutableStateOf("") }

    val isLocked = closure?.status == "DEFINITIVO"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Status & Title Bar
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Fechamento Mensal - DRE ($competence)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isLocked) "Fechado Definitivamente" else "Em Rascunho / Aberto",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLocked) EmeraldPrimary else StatusYellow
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLocked) EmeraldPrimary.copy(alpha = 0.2f) else StatusYellow.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isLocked) EmeraldPrimary else StatusYellow,
                            modifier = Modifier.height(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLocked) "DEFINITIVO" else "RASCUNHO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isLocked) EmeraldPrimary else StatusYellow
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ACCRUAL VS CASH COMPARISON CARDS
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Regime de Competência", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Faturado Líquido: ${CurrencyUtils.formatCents(dreResult.accrualNetRevenueCents)}", fontSize = 11.sp)
                    Text("Despesas Incorridas: ${CurrencyUtils.formatCents(dreResult.accrualExpensesCents)}", fontSize = 11.sp, color = StatusRed)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Resultado: ${CurrencyUtils.formatCents(dreResult.accrualOperatingResultCents)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Regime de Caixa", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Recebido Líquido: ${CurrencyUtils.formatCents(dreResult.cashNetRevenueCents)}", fontSize = 11.sp)
                    Text("Despesas Pagas: ${CurrencyUtils.formatCents(dreResult.cashOperationalExpensesCents)}", fontSize = 11.sp, color = StatusRed)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Resultado Caixa: ${CurrencyUtils.formatCents(dreResult.cashOperatingResultCents)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // DETAILED DRE BREAKDOWN TABLE
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Demonstrativo do Resultado (DRE) - $competence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                DreRowItem("Receita Bruta Recebida", CurrencyUtils.formatCents(dreResult.cashGrossRevenueCents), false)
                DreRowItem("(-) Taxas de Operadora/Meios", CurrencyUtils.formatCents(-dreResult.cashPaymentFeesCents), false)
                DreRowItem("(=) Receita Líquida Recebida", CurrencyUtils.formatCents(dreResult.cashNetRevenueCents), true)
                Spacer(modifier = Modifier.height(6.dp))
                DreRowItem("(-) Despesas Operacionais Pagas", CurrencyUtils.formatCents(-dreResult.cashOperationalExpensesCents), false, isNegative = true)
                DreRowItem("(=) Resultado Operacional", CurrencyUtils.formatCents(dreResult.cashOperatingResultCents), true)
                Spacer(modifier = Modifier.height(6.dp))
                DreRowItem("(-) Retiradas Pró-Labore/Proprietário", CurrencyUtils.formatCents(-dreResult.cashOwnerWithdrawalsCents), false, isNegative = true)
                DreRowItem("(=) Variação Líquida de Caixa", CurrencyUtils.formatCents(dreResult.cashNetPeriodVariationCents), true)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ACTIONS BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { ReportExporter.exportMonthlyDREPdf(context, dreResult, settings) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Exportar PDF", fontSize = 12.sp)
            }

            if (!isLocked) {
                Button(
                    onClick = { viewModel.saveMonthlyClosure("DEFINITIVO", closureNotes) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Travar Mês", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = { showReopenDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusYellow)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reabrir Mês", fontSize = 12.sp)
                }
            }
        }
    }

    if (showReopenDialog) {
        var reasonText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showReopenDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Justificativa de Reabertura", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Motivo obrigatório da reabertura") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { showReopenDialog = false }) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (reasonText.isNotBlank()) {
                                viewModel.reopenMonthlyClosure(reasonText)
                                showReopenDialog = false
                            }
                        }) {
                            Text("Confirmar Reabertura")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DreRowItem(
    label: String,
    valueStr: String,
    isHeader: Boolean,
    isNegative: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = if (isHeader) 13.sp else 12.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = valueStr,
            fontSize = if (isHeader) 13.sp else 12.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            color = if (isNegative) StatusRed else if (isHeader) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}
