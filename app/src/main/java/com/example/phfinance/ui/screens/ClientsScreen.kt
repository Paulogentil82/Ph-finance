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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.model.CurrencyUtils
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
            onSave = { id, fullName, nickname, phone, whatsapp, email, valueCents, dueDay, notes ->
                viewModel.saveClient(id, fullName, nickname, phone, whatsapp, email, valueCents, dueDay, notes)
                showAddDialog = false
                editingClient = null
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
    onSave: (String?, String, String, String, String, String, Long, Int, String) -> Unit
) {
    var fullName by remember { mutableStateOf(client?.fullName ?: "") }
    var nickname by remember { mutableStateOf(client?.nickname ?: "") }
    var phone by remember { mutableStateOf(client?.phone ?: "") }
    var whatsapp by remember { mutableStateOf(client?.whatsapp ?: "") }
    var email by remember { mutableStateOf(client?.email ?: "") }
    var valueText by remember { mutableStateOf(if (client != null) (client.agreedValueCents / 100).toString() else "200") }
    var dueDayText by remember { mutableStateOf(client?.dueDay?.toString() ?: "10") }
    var notes by remember { mutableStateOf(client?.notes ?: "") }

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
                Text(
                    text = if (client == null) "Cadastrar Aluno" else "Editar Aluno",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nome Completo") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Apelido") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            if (whatsapp.isBlank()) whatsapp = it
                        },
                        label = { Text("Telefone/DDD") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text("Valor Mensal (R$)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = dueDayText,
                        onValueChange = { dueDayText = it },
                        label = { Text("Dia Vencimento") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações Administrativas") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (fullName.isNotBlank()) {
                                val valCents = CurrencyUtils.parseToCents(valueText)
                                val dueDay = dueDayText.toIntOrNull() ?: 10
                                onSave(client?.id, fullName, nickname, phone, whatsapp, email, valCents, dueDay, notes)
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
fun ClientDetailSheetContent(
    client: ClientEntity,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
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
                Icon(Icons.Default.Send, contentDescription = null, tint = EmeraldPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
    }
}
