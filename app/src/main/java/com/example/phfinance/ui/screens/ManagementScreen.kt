package com.example.phfinance.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.WhatsappTemplateType
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel

@Composable
fun ManagementScreen(viewModel: PhFinanceViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Serviços, 1: Metas & Simulador, 2: WhatsApp, 3: Configurações

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Serviços") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Simulador") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("WhatsApp") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Config") })
        }

        when (selectedTab) {
            0 -> ServicesManagementTab(viewModel)
            1 -> GoalsAndSimulatorTab(viewModel)
            2 -> WhatsappTemplatesTab(viewModel)
            3 -> BusinessSettingsTab(viewModel)
        }
    }
}

@Composable
fun ServicesManagementTab(viewModel: PhFinanceViewModel) {
    val services by viewModel.services.collectAsState()
    var showAddServiceDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                Text("Catálogo de Serviços PH", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { showAddServiceDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Novo Serviço")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(services) { srv ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(srv.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(srv.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(CurrencyUtils.formatCents(srv.defaultPriceCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showAddServiceDialog) {
        ServiceDialog(
            onDismiss = { showAddServiceDialog = false },
            onSave = { name, desc, priceCents, sessions ->
                viewModel.saveService(null, name, desc, priceCents, sessions)
                showAddServiceDialog = false
            }
        )
    }
}

@Composable
fun GoalsAndSimulatorTab(viewModel: PhFinanceViewModel) {
    val goal by viewModel.goal.collectAsState()

    var simulatedClientsCount by remember { mutableFloatStateOf(25f) }
    var simulatedTicketPrice by remember { mutableFloatStateOf(250f) }

    val simulatedRevenue = (simulatedClientsCount * simulatedTicketPrice * 100).toLong()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Simulador de Cenários Financeiros", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Projete o faturamento com base no número de alunos e ticket médio.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Quantidade de Alunos Simulação: ${simulatedClientsCount.toInt()}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = simulatedClientsCount,
                    onValueChange = { simulatedClientsCount = it },
                    valueRange = 5f..100f
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Ticket Médio Simulação: R$ ${simulatedTicketPrice.toInt()}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = simulatedTicketPrice,
                    onValueChange = { simulatedTicketPrice = it },
                    valueRange = 100f..1000f
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Projeção de Receita Mensal Simulada", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = CurrencyUtils.formatCents(simulatedRevenue),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsappTemplatesTab(viewModel: PhFinanceViewModel) {
    val context = LocalContext.current
    val clients by viewModel.allClients.collectAsState()

    var selectedTemplate by remember { mutableStateOf(WhatsappTemplateType.LEMBRETE_ANTES) }
    var selectedClientId by remember { mutableStateOf(clients.firstOrNull()?.id ?: "") }

    val client = clients.find { it.id == selectedClientId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Cobranças e Mensagens WhatsApp", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Envie mensagens preparadas diretamente ao WhatsApp do aluno.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        WhatsappTemplateType.values().forEach { template ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedTemplate == template) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            template.defaultText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(onClick = {
                        client?.let { cli ->
                            val cleanPhone = cli.whatsapp.replace("[^0-9]".toRegex(), "")
                            val msg = template.defaultText
                                .replace("{nome}", cli.fullName)
                                .replace("{valor}", CurrencyUtils.formatCents(cli.agreedValueCents))
                                .replace("{vencimento}", "Dia ${cli.dueDay}")

                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(msg)}"))
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessSettingsTab(viewModel: PhFinanceViewModel) {
    val settings by viewModel.settings.collectAsState()

    var businessName by remember { mutableStateOf(settings?.businessName ?: "PH Personal") }
    var tradeName by remember { mutableStateOf(settings?.tradeName ?: "PH Consultoria") }
    var pixKey by remember { mutableStateOf(settings?.pixKey ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Configurações da Consultoria", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = businessName,
            onValueChange = { businessName = it },
            label = { Text("Nome da Empresa / Profissional") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = tradeName,
            onValueChange = { tradeName = it },
            label = { Text("Nome Fantasia") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = pixKey,
            onValueChange = { pixKey = it },
            label = { Text("Chave Pix Oficial") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { viewModel.saveSettings(businessName, tradeName, pixKey) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Salvar Configurações")
        }

        Spacer(modifier = Modifier.height(12.dp))

        val context = LocalContext.current
        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordInput by remember { mutableStateOf("") }

        OutlinedButton(
            onClick = { showPasswordDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Build, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Exportar Backup Criptografado (AES-256)")
        }

        if (showPasswordDialog) {
            Dialog(onDismissRequest = { showPasswordDialog = false }) {
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
                        Text("Senha de Criptografia do Backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("A senha é necessária para proteger seus dados financeiros e NÃO é salva no app.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Senha do Backup (mínimo 6 dígitos)") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = { showPasswordDialog = false }) { Text("Cancelar") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (passwordInput.length >= 6) {
                                        viewModel.createBackup(context, passwordInput)
                                        showPasswordDialog = false
                                        passwordInput = ""
                                    }
                                }
                            ) {
                                Text("Gerar Backup")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var sessionsText by remember { mutableStateOf("0") }

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
                Text("Cadastrar Serviço", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Serviço") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Preço Padrão (R$)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val valCents = com.example.phfinance.data.model.CurrencyUtils.parseToCents(priceText)
                        val sess = sessionsText.toIntOrNull() ?: 0
                        if (name.isNotBlank() && valCents > 0) {
                            onSave(name, description, valCents, sess)
                        }
                    }) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}
