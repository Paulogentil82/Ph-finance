package com.example.phfinance.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.data.model.WhatsappTemplateType
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.LaunchedEffect
import com.example.phfinance.ui.screens.CustomReceivableDialog
import com.example.phfinance.ui.screens.PaymentSettlementDialog
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.theme.StatusRed
import com.example.phfinance.ui.theme.StatusYellow
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import com.example.phfinance.util.ReportExporter

@Composable
fun BillingCenterComponent(viewModel: PhFinanceViewModel) {
    val context = LocalContext.current
    val receivables by viewModel.receivables.collectAsState()
    val clients by viewModel.allClients.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val competence by viewModel.selectedCompetence.collectAsState()

    val clientMap = clients.associateBy { it.id }

    var filterStatus by remember { mutableStateOf("TODOS") }
    var selectedReceivableForPayment by remember { mutableStateOf<ReceivableEntity?>(null) }
    var selectedReceivableForWhatsapp by remember { mutableStateOf<ReceivableEntity?>(null) }
    var showAddReceivableDialog by remember { mutableStateOf(false) }
    var showSelectReceivableForPaymentDialog by remember { mutableStateOf(false) }

    val competenceReceivables = receivables.filter { it.competence == competence && it.status != ReceivableStatus.CANCELADA }

    val triggerReceivable by viewModel.triggerOpenAddReceivable.collectAsState()
    val triggerPayment by viewModel.triggerOpenAddPayment.collectAsState()
    val userFeedback by viewModel.userFeedbackMessage.collectAsState()

    LaunchedEffect(triggerReceivable) {
        if (triggerReceivable) {
            showAddReceivableDialog = true
            viewModel.consumeAddReceivableFormTrigger()
        }
    }

    LaunchedEffect(triggerPayment) {
        if (triggerPayment) {
            val pendingList = competenceReceivables.filter { it.status != ReceivableStatus.QUITADA }
            if (pendingList.isNotEmpty()) {
                showSelectReceivableForPaymentDialog = true
            } else {
                viewModel.setUserFeedback("Nenhuma cobrança pendente na competência $competence para registrar recebimento.")
            }
            viewModel.consumeAddPaymentFormTrigger()
        }
    }

    val now = System.currentTimeMillis()
    val sevenDaysFromNow = now + 7 * 86400000L

    val totalToReceiveCents = competenceReceivables
        .filter { it.status == ReceivableStatus.PENDENTE || it.status == ReceivableStatus.PARCIALMENTE_PAGA || it.status == ReceivableStatus.VENCIDA }
        .sumOf { it.netValueCents - it.receivedValueCents }

    val dueTodayReceivables = competenceReceivables.filter {
        (it.status == ReceivableStatus.PENDENTE || it.status == ReceivableStatus.PARCIALMENTE_PAGA) &&
                DateUtils.formatDate(it.dueDate) == DateUtils.formatDate(now)
    }
    val dueTodayCents = dueTodayReceivables.sumOf { it.netValueCents - it.receivedValueCents }

    val next7DaysReceivables = competenceReceivables.filter {
        (it.status == ReceivableStatus.PENDENTE || it.status == ReceivableStatus.PARCIALMENTE_PAGA) &&
                it.dueDate in now..sevenDaysFromNow
    }
    val next7DaysCents = next7DaysReceivables.sumOf { it.netValueCents - it.receivedValueCents }

    val overdueReceivables = competenceReceivables.filter {
        (it.status == ReceivableStatus.PENDENTE || it.status == ReceivableStatus.PARCIALMENTE_PAGA || it.status == ReceivableStatus.VENCIDA) &&
                it.dueDate < now && DateUtils.formatDate(it.dueDate) != DateUtils.formatDate(now)
    }
    val overdueCents = overdueReceivables.sumOf { it.netValueCents - it.receivedValueCents }
    val overdueClientsCount = overdueReceivables.map { it.clientId }.distinct().size

    val partiallyPaidCount = competenceReceivables.count { it.status == ReceivableStatus.PARCIALMENTE_PAGA }
    val fullyPaidCount = competenceReceivables.count { it.status == ReceivableStatus.QUITADA }
    val mrrCents = competenceReceivables.sumOf { it.netValueCents }

    val filteredList = competenceReceivables.filter { r ->
        when (filterStatus) {
            "PENDENTE" -> r.status == ReceivableStatus.PENDENTE || r.status == ReceivableStatus.PARCIALMENTE_PAGA
            "PARCIAL" -> r.status == ReceivableStatus.PARCIALMENTE_PAGA
            "QUITADA" -> r.status == ReceivableStatus.QUITADA
            "VENCIDA" -> r.dueDate < now && r.status != ReceivableStatus.QUITADA
            else -> true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            userFeedback?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearUserFeedback() }) {
                            Icon(Icons.Default.Cancel, contentDescription = "Fechar")
                        }
                    }
                }
            }

            // TOP METRICS SUMMARY CARDS GRID
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricMiniCard(
                        modifier = Modifier.weight(1f),
                        label = "A Receber no Mês",
                        value = CurrencyUtils.formatCents(totalToReceiveCents),
                        valueColor = EmeraldPrimary
                    )
                    MetricMiniCard(
                        modifier = Modifier.weight(1f),
                        label = "Vencendo Hoje",
                        value = CurrencyUtils.formatCents(dueTodayCents),
                        valueColor = StatusYellow
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricMiniCard(
                        modifier = Modifier.weight(1f),
                        label = "Próximos 7 Dias",
                        value = CurrencyUtils.formatCents(next7DaysCents),
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    MetricMiniCard(
                        modifier = Modifier.weight(1f),
                        label = "Valores Vencidos",
                        value = CurrencyUtils.formatCents(overdueCents),
                        valueColor = StatusRed,
                        subtitle = "$overdueClientsCount alunos inadimplentes"
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricMiniCard(
                        modifier = Modifier.weight(1f),
                        label = "Parciais / Quitadas",
                        value = "$partiallyPaidCount parciais / $fullyPaidCount quitadas",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    MetricMiniCard(
                        modifier = Modifier.weight(1f),
                        label = "MRR Contratado",
                        value = CurrencyUtils.formatCents(mrrCents),
                        valueColor = EmeraldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions & Filters Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { viewModel.generateMonthlyDues() }) {
                    Icon(Icons.Default.Receipt, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gerar Mensalidades")
                }

                IconButton(onClick = {
                    ReportExporter.exportReceivablesCsv(context, filteredList, clients)
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Exportar CSV")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("TODOS", "PENDENTE", "PARCIAL", "VENCIDA", "QUITADA").forEach { tag ->
                    FilterChip(
                        selected = filterStatus == tag,
                        onClick = { filterStatus = tag },
                        label = { Text(tag.lowercase().capitalize(), fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhuma cobrança encontrada com o filtro selecionado.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList) { receivable ->
                        val client = clientMap[receivable.clientId]
                        val clientName = client?.fullName ?: "Aluno"

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
                                        Text(clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Vencimento: ${DateUtils.formatDate(receivable.dueDate)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    StatusBadge(status = receivable.status, isOverdue = receivable.dueDate < now && receivable.status != ReceivableStatus.QUITADA)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Líquido: ${CurrencyUtils.formatCents(receivable.netValueCents)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (receivable.receivedValueCents > 0) {
                                            Text("Recebido: ${CurrencyUtils.formatCents(receivable.receivedValueCents)}", style = MaterialTheme.typography.labelMedium, color = EmeraldPrimary)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (client != null) {
                                            OutlinedButton(
                                                onClick = { selectedReceivableForWhatsapp = receivable },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
                                            ) {
                                                Icon(Icons.Default.Message, contentDescription = "WhatsApp", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("WhatsApp", fontSize = 12.sp)
                                            }
                                        }

                                        if (receivable.status != ReceivableStatus.QUITADA) {
                                            Button(onClick = { selectedReceivableForPayment = receivable }) {
                                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Baixar", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB to Add Custom Receivable
        FloatingActionButton(
            onClick = { showAddReceivableDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = EmeraldPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nova Cobrança Avulsa", tint = MaterialTheme.colorScheme.surface)
        }
    }

    if (showAddReceivableDialog) {
        CustomReceivableDialog(
            clients = clients,
            onDismiss = { showAddReceivableDialog = false },
            onSave = { clientId, description, valueCents, dueDate, onSuccess, onError ->
                viewModel.addCustomReceivable(
                    clientId = clientId,
                    description = description,
                    valueCents = valueCents,
                    dueDate = dueDate,
                    onSuccess = {
                        showAddReceivableDialog = false
                        onSuccess()
                    },
                    onError = onError
                )
            }
        )
    }

    if (showSelectReceivableForPaymentDialog) {
        val pendingList = competenceReceivables.filter { it.status != ReceivableStatus.QUITADA }
        Dialog(onDismissRequest = { showSelectReceivableForPaymentDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp)
                ) {
                    Text("Selecione a Cobrança para Baixa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pendingList) { rec ->
                            val clientName = clientMap[rec.clientId]?.fullName ?: "Aluno"
                            val remaining = (rec.netValueCents - rec.receivedValueCents).coerceAtLeast(0L)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedReceivableForPayment = rec
                                        showSelectReceivableForPaymentDialog = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(clientName, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Vencimento: ${DateUtils.formatDate(rec.dueDate)} • Saldo: ${CurrencyUtils.formatCents(remaining)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showSelectReceivableForPaymentDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }

    // Payment Settlement Dialog
    selectedReceivableForPayment?.let { receivable ->
        PaymentSettlementDialog(
            receivable = receivable,
            accounts = accounts,
            onDismiss = { selectedReceivableForPayment = null },
            onConfirmPayment = { amountCents, feeCents, accountId, method, ref, onSuccess, onError ->
                viewModel.registerPayment(
                    receivableId = receivable.id,
                    amountReceivedCents = amountCents,
                    feeCents = feeCents,
                    destinationAccountId = accountId,
                    paymentMethod = method,
                    transactionRef = ref,
                    onSuccess = {
                        selectedReceivableForPayment = null
                        onSuccess()
                    },
                    onError = onError
                )
            }
        )
    }

    // WhatsApp Template Choice Modal
    selectedReceivableForWhatsapp?.let { receivable ->
        val client = clientMap[receivable.clientId]
        if (client != null) {
            WhatsappTemplateSelectionModal(
                client = client,
                receivable = receivable,
                onDismiss = { selectedReceivableForWhatsapp = null },
                onSelectTemplate = { tType ->
                    viewModel.sendWhatsappMessage(context, client, receivable, tType)
                    selectedReceivableForWhatsapp = null
                }
            )
        }
    }
}

@Composable
fun MetricMiniCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    subtitle: String = ""
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 10.sp, color = StatusRed)
            }
        }
    }
}

@Composable
fun StatusBadge(status: ReceivableStatus, isOverdue: Boolean) {
    val displayStatus = if (isOverdue) ReceivableStatus.VENCIDA else status
    val color = when (displayStatus) {
        ReceivableStatus.QUITADA -> EmeraldPrimary
        ReceivableStatus.PARCIALMENTE_PAGA -> StatusYellow
        ReceivableStatus.VENCIDA -> StatusRed
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(displayStatus.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun WhatsappTemplateSelectionModal(
    client: ClientEntity,
    receivable: ReceivableEntity,
    onDismiss: () -> Unit,
    onSelectTemplate: (WhatsappTemplateType) -> Unit
) {
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
                Text("Enviar Mensagem WhatsApp", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Aluno: ${client.fullName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(14.dp))

                WhatsappTemplateType.values().forEach { tType ->
                    OutlinedButton(
                        onClick = { onSelectTemplate(tType) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(tType.title, modifier = Modifier.fillMaxWidth())
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancelar")
                }
            }
        }
    }
}
