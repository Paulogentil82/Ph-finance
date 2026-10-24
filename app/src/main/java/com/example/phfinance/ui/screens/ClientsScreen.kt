package com.example.phfinance.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.theme.StatusRed
import com.example.phfinance.ui.theme.StatusYellow
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(viewModel: PhFinanceViewModel) {
    val context = LocalContext.current
    val allClients by viewModel.allClients.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ATIVO") } // ATIVO, PAUSADO, INATIVO, ARQUIVADO, TODOS

    var showAddDialog by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<ClientEntity?>(null) }
    var selectedClientDetail by remember { mutableStateOf<ClientEntity?>(null) }

    val triggerAddClient by viewModel.triggerOpenAddClient.collectAsState()

    LaunchedEffect(triggerAddClient) {
        if (triggerAddClient) {
            editingClient = null
            showAddDialog = true
            viewModel.consumeAddClientFormTrigger()
        }
    }

    val filteredClients = allClients.filter { client ->
        val matchesQuery = client.fullName.contains(searchQuery, ignoreCase = true) ||
                client.phone.contains(searchQuery) ||
                client.nickname.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "ATIVO" -> client.clientType.name == "ATIVO" && !client.isArchived
            "PAUSADO" -> client.clientType.name == "PAUSADO" && !client.isArchived
            "INATIVO" -> client.clientType.name == "INATIVO" && !client.isArchived
            "ARQUIVADO" -> client.isArchived
            else -> true
        }
        matchesQuery && matchesFilter
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar aluno por nome, whatsapp ou apelido...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ATIVO", "PAUSADO", "INATIVO", "ARQUIVADO", "TODOS").forEach { filterTag ->
                    FilterChip(
                        selected = selectedFilter == filterTag,
                        onClick = { selectedFilter = filterTag },
                        label = { Text(filterTag.lowercase().capitalize()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Client List
            if (filteredClients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum aluno encontrado.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredClients) { client ->
                        ClientListItemCard(
                            client = client,
                            onClick = { selectedClientDetail = client },
                            onWhatsAppClick = {
                                val cleanPhone = client.whatsapp.replace("[^0-9]".toRegex(), "")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }

        // FAB to Add Client
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = EmeraldPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Cadastrar Aluno", tint = MaterialTheme.colorScheme.surface)
        }
    }

    // Add / Edit Dialog
    if (showAddDialog || editingClient != null) {
        ClientFormDialog(
            client = editingClient,
            onDismiss = {
                showAddDialog = false
                editingClient = null
            },
            onSave = { id, fullName, nickname, phone, whatsapp, email, valueCents, dueDay, notes, onSuccess, onError ->
                viewModel.saveClient(
                    id = id,
                    fullName = fullName,
                    nickname = nickname,
                    phone = phone,
                    whatsapp = whatsapp,
                    email = email,
                    agreedValueCents = valueCents,
                    dueDay = dueDay,
                    notes = notes,
                    onSuccess = {
                        showAddDialog = false
                        editingClient = null
                        onSuccess()
                    },
                    onError = onError
                )
            }
        )
    }

    // Client Detail Sheet
    selectedClientDetail?.let { client ->
        ModalBottomSheet(
            onDismissRequest = { selectedClientDetail = null }
        ) {
            ClientDetailSheetContent(
                client = client,
                viewModel = viewModel,
                onEdit = {
                    editingClient = client
                    selectedClientDetail = null
                },
                onArchiveToggle = {
                    if (client.isArchived) {
                        viewModel.unarchiveClient(client.id)
                    } else {
                        viewModel.archiveClient(client.id)
                    }
                    selectedClientDetail = null
                },
                onWhatsAppClick = {
                    val cleanPhone = client.whatsapp.replace("[^0-9]".toRegex(), "")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone"))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun ClientListItemCard(
    client: ClientEntity,
    onClick: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = client.fullName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = client.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (client.clientType.name) {
                                    "ATIVO" -> EmeraldPrimary.copy(alpha = 0.2f)
                                    "PAUSADO" -> StatusYellow.copy(alpha = 0.2f)
                                    else -> StatusRed.copy(alpha = 0.2f)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = client.clientType.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (client.clientType.name) {
                                "ATIVO" -> EmeraldPrimary
                                "PAUSADO" -> StatusYellow
                                else -> StatusRed
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Vencimento: Dia ${client.dueDay} • ${CurrencyUtils.formatCents(client.agreedValueCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onWhatsAppClick) {
                Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = EmeraldPrimary)
            }
        }
    }
}

@Composable
fun ClientFormDialog(
    client: ClientEntity?,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        fullName: String,
        nickname: String,
        phone: String,
        whatsapp: String,
        email: String,
        agreedValueCents: Long,
        dueDay: Int,
        notes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    var fullName by remember { mutableStateOf(client?.fullName ?: "") }
    var nickname by remember { mutableStateOf(client?.nickname ?: "") }
    var phone by remember { mutableStateOf(client?.phone ?: "") }
    var whatsapp by remember { mutableStateOf(client?.whatsapp ?: "") }
    var email by remember { mutableStateOf(client?.email ?: "") }
    var valueText by remember { mutableStateOf(if (client != null) (client.agreedValueCents / 100).toString() else "200") }
    var dueDayText by remember { mutableStateOf(client?.dueDay?.toString() ?: "10") }
    var notes by remember { mutableStateOf(client?.notes ?: "") }

    var validationError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (client == null) "Cadastrar Aluno" else "Editar Aluno",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        validationError = null
                    },
                    label = { Text("Nome Completo *") },
                    isError = validationError != null && fullName.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Apelido") },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            if (whatsapp.isBlank()) whatsapp = it
                        },
                        label = { Text("Telefone/DDD") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        enabled = !isSaving
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = {
                            valueText = it
                            validationError = null
                        },
                        label = { Text("Valor Mensal (R$) *") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isSaving
                    )
                    OutlinedTextField(
                        value = dueDayText,
                        onValueChange = {
                            dueDayText = it
                            validationError = null
                        },
                        label = { Text("Dia Vencimento *") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isSaving
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações Administrativas") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                )

                validationError?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isSaving,
                        onClick = {
                            validationError = null
                            val trimmedName = fullName.trim()
                            if (trimmedName.isBlank()) {
                                validationError = "O nome completo do aluno é obrigatório."
                                return@Button
                            }

                            val valCents = try {
                                val clean = valueText.replace(",", ".").trim()
                                if (clean.isBlank()) 0L else (clean.toDouble() * 100).toLong()
                            } catch (e: Exception) {
                                -1L
                            }

                            if (valCents < 0L) {
                                validationError = "Informe um valor mensal válido e não negativo."
                                return@Button
                            }

                            val dueDay = dueDayText.trim().toIntOrNull()
                            if (dueDay == null || dueDay !in 1..31) {
                                validationError = "O dia de vencimento deve estar entre 1 e 31."
                                return@Button
                            }

                            isSaving = true
                            onSave(
                                client?.id,
                                trimmedName,
                                nickname.trim(),
                                phone.trim(),
                                whatsapp.trim(),
                                email.trim(),
                                valCents,
                                dueDay,
                                notes.trim(),
                                /* onSuccess */ {
                                    isSaving = false
                                    onDismiss()
                                },
                                /* onError */ { err ->
                                    isSaving = false
                                    validationError = err
                                }
                            )
                        }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvando...")
                        } else {
                            Text("Salvar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClientDetailSheetContent(
    client: ClientEntity,
    viewModel: PhFinanceViewModel,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    var sheetTab by remember { mutableStateOf(0) } // 0: Cadastro, 1: Análise Financeira
    var isAccumulated by remember { mutableStateOf(false) }

    val competence by viewModel.selectedCompetence.collectAsState()
    val receivables by viewModel.receivables.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val contracts by viewModel.contracts.collectAsState()
    val services by viewModel.services.collectAsState()
    val costAllocations by viewModel.costAllocations.collectAsState()
    val timeTrackings by viewModel.timeTrackings.collectAsState()

    val clientContracts = contracts.filter { it.clientId == client.id }
    val contractIds = clientContracts.map { it.id }.toSet()
    val serviceMap = services.associateBy { it.id }

    val allClientReceivables = receivables.filter { it.clientId == client.id || it.contractId in contractIds }
    val filteredReceivables = if (isAccumulated) {
        allClientReceivables
    } else {
        allClientReceivables.filter { it.competence == competence }
    }

    val paymentMap = payments.associateBy { it.receivableId }

    val contractedRevenue = filteredReceivables.sumOf { it.originalValueCents }
    val receivedRevenue = filteredReceivables.sumOf { rec ->
        paymentMap[rec.id]?.grossAmountCents ?: if (rec.status == ReceivableStatus.QUITADA || rec.status.name == "RECEBIDO") rec.receivedValueCents.takeIf { it > 0 } ?: rec.originalValueCents else 0L
    }
    val pendingRevenue = filteredReceivables.filter { it.status.name == "PENDENTE" || it.status.name == "ATRASADO" }.sumOf { it.netValueCents }
    val discounts = filteredReceivables.sumOf { it.discountCents }
    val fees = filteredReceivables.sumOf { rec -> paymentMap[rec.id]?.feeCents ?: 0L }

    val clientAllocations = costAllocations.filter {
        (it.clientId == client.id || it.contractId in contractIds) &&
                (isAccumulated || it.competence == competence)
    }
    val directCosts = clientAllocations
        .filter { it.allocationType != com.example.phfinance.data.model.CostAllocationType.INDIRETO_RATEADO }
        .sumOf { it.allocatedAmountCents }
    val indirectCosts = clientAllocations
        .filter { it.allocationType == com.example.phfinance.data.model.CostAllocationType.INDIRETO_RATEADO }
        .sumOf { it.allocatedAmountCents }

    val netEffectiveRevenue = receivedRevenue - fees
    val contributionMargin = netEffectiveRevenue - directCosts
    val marginPercent = if (netEffectiveRevenue > 0) (contributionMargin.toDouble() / netEffectiveRevenue.toDouble()) * 100.0 else 0.0
    val managerialResult = contributionMargin - indirectCosts

    val clientTimeTrackings = timeTrackings.filter {
        (it.clientId == client.id || it.contractId in contractIds) &&
                (isAccumulated || it.competence == competence)
    }
    val dedicatedMinutes = clientTimeTrackings.sumOf { it.durationMinutes }
    val dedicatedHours = dedicatedMinutes / 60.0
    val revenuePerHour = if (dedicatedHours > 0.0) (netEffectiveRevenue / dedicatedHours).toLong() else 0L
    val economicCostOfTime = (dedicatedHours * 5000L).toLong()
    val economicResult = managerialResult - economicCostOfTime

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = client.fullName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "WhatsApp: ${client.whatsapp}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onWhatsAppClick) {
                Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = EmeraldPrimary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TabRow(selectedTabIndex = sheetTab) {
            Tab(selected = sheetTab == 0, onClick = { sheetTab = 0 }, text = { Text("Dados do Aluno") })
            Tab(selected = sheetTab == 1, onClick = { sheetTab = 1 }, text = { Text("Análise Financeira") })
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (sheetTab == 0) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resumo Financeiro Contratado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyUtils.formatCents(client.agreedValueCents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Vencimento no dia ${client.dueDay} de cada mês", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Editar")
                }

                Button(
                    onClick = onArchiveToggle,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (client.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (client.isArchived) "Reativar" else "Arquivar")
                }
            }
        } else {
            // ABA: ANÁLISE FINANCEIRA INDIVIDUAL DO ALUNO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isAccumulated) "Período: Acumulado Geral" else "Período: Competência Atual ($competence)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !isAccumulated,
                        onClick = { isAccumulated = false },
                        label = { Text("Mensal") }
                    )
                    FilterChip(
                        selected = isAccumulated,
                        onClick = { isAccumulated = true },
                        label = { Text("Acumulado") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Retorno & Margem de Contribuição", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Receita Contratada:", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyUtils.formatCents(contractedRevenue), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Receita Efetivamente Recebida:", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyUtils.formatCents(receivedRevenue), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            }
                            if (pendingRevenue > 0L) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Valores Pendentes:", style = MaterialTheme.typography.bodySmall)
                                    Text(CurrencyUtils.formatCents(pendingRevenue), style = MaterialTheme.typography.bodySmall, color = StatusYellow)
                                }
                            }
                            if (discounts > 0L) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Descontos Concedidos:", style = MaterialTheme.typography.bodySmall)
                                    Text(CurrencyUtils.formatCents(discounts), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (fees > 0L) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Taxas Financeiras:", style = MaterialTheme.typography.bodySmall)
                                    Text(CurrencyUtils.formatCents(fees), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("(-) Custos Diretos Associados:", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyUtils.formatCents(directCosts), style = MaterialTheme.typography.bodySmall, color = StatusRed)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("(=) Margem de Contribuição:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("${CurrencyUtils.formatCents(contributionMargin)} (${String.format("%.1f%%", marginPercent)})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("(-) Custos Indiretos Alocados:", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyUtils.formatCents(indirectCosts), style = MaterialTheme.typography.bodySmall, color = StatusYellow)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("(=) Resultado Gerencial:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(CurrencyUtils.formatCents(managerialResult), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (managerialResult >= 0) EmeraldPrimary else StatusRed)
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Produtividade do Atendimento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tempo Dedicado:", style = MaterialTheme.typography.bodySmall)
                                Text("${dedicatedMinutes} min (${String.format("%.1fh", dedicatedHours)})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Receita Média / Hora:", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyUtils.formatCents(revenuePerHour), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Custo Econômico do Tempo (Oportunidade):", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyUtils.formatCents(economicCostOfTime), style = MaterialTheme.typography.bodySmall, color = StatusYellow)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Resultado Econômico:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(CurrencyUtils.formatCents(economicResult), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Diretriz de Decisão Administrativa", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Informações objetivas de retorno contratual. A margem de contribuição expressa o valor residual gerado para cobertura dos custos fixos. Utilize para calibrar a grade de horários, reajustes e estratégias de retenção.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
