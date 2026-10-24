package com.example.phfinance.ui.screens

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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.FinancialAccountEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.theme.StatusRed
import com.example.phfinance.ui.theme.StatusYellow
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import com.example.phfinance.util.ReportExporter

@Composable
fun FinancialScreen(viewModel: PhFinanceViewModel) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Cobranças") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("A Pagar") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Fechamento & DRE") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Contas & Caixa") }
            )
        }

        when (selectedTab) {
            0 -> com.example.phfinance.ui.components.BillingCenterComponent(viewModel)
            1 -> ExpensesTab(viewModel)
            2 -> com.example.phfinance.ui.components.MonthlyClosureComponent(viewModel)
            3 -> AccountsTab(viewModel)
        }
    }
}


@Composable
fun ReceivablesTab(viewModel: PhFinanceViewModel) {
    val context = LocalContext.current
    val receivables by viewModel.receivables.collectAsState()
    val clients by viewModel.allClients.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val competence by viewModel.selectedCompetence.collectAsState()

    val clientMap = clients.associateBy { it.id }

    var filterStatus by remember { mutableStateOf("TODOS") } // PENDENTE, QUITADA, VENCIDA, TODOS
    var selectedReceivableForPayment by remember { mutableStateOf<ReceivableEntity?>(null) }
    var showAddReceivableDialog by remember { mutableStateOf(false) }

    val filteredList = receivables.filter { r ->
        r.competence == competence && (
                when (filterStatus) {
                    "PENDENTE" -> r.status == ReceivableStatus.PENDENTE || r.status == ReceivableStatus.PARCIALMENTE_PAGA
                    "QUITADA" -> r.status == ReceivableStatus.QUITADA
                    "VENCIDA" -> r.status == ReceivableStatus.VENCIDA
                    else -> true
                }
                )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Recurrent Generator & Export Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { viewModel.generateMonthlyDues() }) {
                    Icon(Icons.Default.Receipt, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gerar Mensalidades do Mês")
                }

                IconButton(onClick = {
                    ReportExporter.exportReceivablesCsv(context, filteredList, clients)
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Exportar CSV")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TODOS", "PENDENTE", "QUITADA", "VENCIDA").forEach { tag ->
                    FilterChip(
                        selected = filterStatus == tag,
                        onClick = { filterStatus = tag },
                        label = { Text(tag.lowercase().capitalize()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma cobrança encontrada para a competência $competence.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList) { receivable ->
                        val clientName = clientMap[receivable.clientId]?.fullName ?: "Aluno"
                        ReceivableCard(
                            receivable = receivable,
                            clientName = clientName,
                            onPayClick = { selectedReceivableForPayment = receivable }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddReceivableDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = EmeraldPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Cobrança Avulsa", tint = MaterialTheme.colorScheme.surface)
        }
    }

    // Register Payment Dialog (Baixa Financeira)
    selectedReceivableForPayment?.let { receivable ->
        PaymentSettlementDialog(
            receivable = receivable,
            accounts = accounts,
            onDismiss = { selectedReceivableForPayment = null },
            onConfirmPayment = { amountCents, feeCents, accountId, method, ref ->
                viewModel.registerPayment(receivable.id, amountCents, feeCents, accountId, method, ref)
                selectedReceivableForPayment = null
            }
        )
    }

    if (showAddReceivableDialog) {
        CustomReceivableDialog(
            clients = clients,
            onDismiss = { showAddReceivableDialog = false },
            onSave = { clientId, desc, valCents, dueDate ->
                viewModel.addCustomReceivable(clientId, desc, valCents, dueDate)
                showAddReceivableDialog = false
            }
        )
    }
}

@Composable
fun ReceivableCard(
    receivable: ReceivableEntity,
    clientName: String,
    onPayClick: () -> Unit
) {
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

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (receivable.status) {
                                ReceivableStatus.QUITADA -> EmeraldPrimary.copy(alpha = 0.2f)
                                ReceivableStatus.PARCIALMENTE_PAGA -> StatusYellow.copy(alpha = 0.2f)
                                ReceivableStatus.VENCIDA -> StatusRed.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = receivable.status.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (receivable.status) {
                            ReceivableStatus.QUITADA -> EmeraldPrimary
                            ReceivableStatus.PARCIALMENTE_PAGA -> StatusYellow
                            ReceivableStatus.VENCIDA -> StatusRed
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Valor Devido: ${CurrencyUtils.formatCents(receivable.netValueCents)}", style = MaterialTheme.typography.bodyMedium)
                    if (receivable.receivedValueCents > 0) {
                        Text("Pago: ${CurrencyUtils.formatCents(receivable.receivedValueCents)}", style = MaterialTheme.typography.labelMedium, color = EmeraldPrimary)
                    }
                }

                if (receivable.status != ReceivableStatus.QUITADA) {
                    Button(onClick = onPayClick) {
                        Text("Baixar / Receber")
                    }
                }
            }
        }
    }
}

@Composable
fun ExpensesTab(viewModel: PhFinanceViewModel) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val competence by viewModel.selectedCompetence.collectAsState()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var payingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    val filteredList = expenses.filter { it.competence == competence }

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
                Text("Despesas & Contas a Pagar ($competence)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { ReportExporter.exportExpensesCsv(context, filteredList) }) {
                    Icon(Icons.Default.Share, contentDescription = "Exportar CSV")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma despesa registrada no mês $competence.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList) { expense ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(expense.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        if (expense.isOwnerPersonalWithdrawal) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(StatusYellow.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("Retirada Pessoal", fontSize = 10.sp, color = StatusYellow, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text("Categoria: ${expense.category} • Venc: ${DateUtils.formatDate(expense.dueDate)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyUtils.formatCents(expense.valueCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusRed)
                                }

                                if (expense.status == ExpenseStatus.PENDENTE) {
                                    Button(onClick = { payingExpense = expense }) {
                                        Text("Pagar")
                                    }
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Pago", tint = EmeraldPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddExpenseDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = StatusRed
        ) {
            Icon(Icons.Default.Add, contentDescription = "Adicionar Despesa", tint = MaterialTheme.colorScheme.surface)
        }
    }

    if (showAddExpenseDialog) {
        ExpenseDialog(
            onDismiss = { showAddExpenseDialog = false },
            onSave = { desc, cat, supp, valCents, due, isOwner ->
                viewModel.addExpense(desc, cat, supp, valCents, due, isOwner)
                showAddExpenseDialog = false
            }
        )
    }

    payingExpense?.let { exp ->
        PayExpenseDialog(
            expense = exp,
            accounts = accounts,
            onDismiss = { payingExpense = null },
            onConfirmPay = { accId, method ->
                viewModel.payExpense(exp.id, accId, method)
                payingExpense = null
            }
        )
    }
}

@Composable
fun AccountsTab(viewModel: PhFinanceViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    var showTransferDialog by remember { mutableStateOf(false) }

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
            Text("Contas Financeiras", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showTransferDialog = true }) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Transferência Interna")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(accounts) { acc ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(acc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(acc.accountType.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(CurrencyUtils.formatCents(acc.currentBalanceCents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    }
                }
            }
        }
    }

    if (showTransferDialog) {
        TransferDialog(
            accounts = accounts,
            onDismiss = { showTransferDialog = false },
            onConfirm = { srcId, destId, valCents, desc ->
                viewModel.transferAccounts(srcId, destId, valCents, desc)
                showTransferDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSettlementDialog(
    receivable: ReceivableEntity,
    accounts: List<FinancialAccountEntity>,
    onDismiss: () -> Unit,
    onConfirmPayment: (Long, Long, String, PaymentMethod, String) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", (receivable.netValueCents - receivable.receivedValueCents) / 100.0)) }
    var feeText by remember { mutableStateOf("0") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.PIX) }
    var transactionRef by remember { mutableStateOf("") }

    var expandedAccount by remember { mutableStateOf(false) }

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
                Text("Baixa Financeira de Pagamento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor Efetivamente Recebido (R$)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = feeText,
                    onValueChange = { feeText = it },
                    label = { Text("Taxa do Meio de Pagamento (R$)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Account Selector
                ExposedDropdownMenuBox(
                    expanded = expandedAccount,
                    onExpandedChange = { expandedAccount = !expandedAccount }
                ) {
                    OutlinedTextField(
                        value = accounts.find { it.id == selectedAccountId }?.name ?: "Selecione a Conta",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Conta de Destino") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccount) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAccount,
                        onDismissRequest = { expandedAccount = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    selectedAccountId = acc.id
                                    expandedAccount = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val valCents = CurrencyUtils.parseToCents(amountText)
                            val feeCents = CurrencyUtils.parseToCents(feeText)
                            if (valCents > 0 && selectedAccountId.isNotBlank()) {
                                onConfirmPayment(valCents, feeCents, selectedAccountId, selectedMethod, transactionRef)
                            }
                        }
                    ) {
                        Text("Confirmar Baixa")
                    }
                }
            }
        }
    }
}

@Composable
fun CustomReceivableDialog(
    clients: List<ClientEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Long) -> Unit
) {
    var selectedClientId by remember { mutableStateOf(clients.firstOrNull()?.id ?: "") }
    var description by remember { mutableStateOf("") }
    var valueText by remember { mutableStateOf("") }

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
                Text("Criar Cobrança Avulsa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição / Serviço") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Valor (R$)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val valCents = CurrencyUtils.parseToCents(valueText)
                        if (selectedClientId.isNotBlank() && valCents > 0) {
                            onSave(selectedClientId, description, valCents, System.currentTimeMillis() + 86400000L * 5)
                        }
                    }) {
                        Text("Criar Cobrança")
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long, Long, Boolean) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Assinaturas e Aplicativos") }
    var supplier by remember { mutableStateOf("") }
    var valueText by remember { mutableStateOf("") }
    var isOwnerWithdrawal by remember { mutableStateOf(false) }

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
                Text("Registrar Despesa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição da Despesa") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Valor (R$)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isOwnerWithdrawal,
                        onCheckedChange = { isOwnerWithdrawal = it }
                    )
                    Text("Retirada Pessoal do Proprietário (Pro-Labore)", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val valCents = CurrencyUtils.parseToCents(valueText)
                        if (description.isNotBlank() && valCents > 0) {
                            onSave(description, category, supplier, valCents, System.currentTimeMillis() + 86400000L * 3, isOwnerWithdrawal)
                        }
                    }) {
                        Text("Salvar Despesa")
                    }
                }
            }
        }
    }
}

@Composable
fun PayExpenseDialog(
    expense: ExpenseEntity,
    accounts: List<FinancialAccountEntity>,
    onDismiss: () -> Unit,
    onConfirmPay: (String, PaymentMethod) -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }

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
                Text("Confirmar Pagamento de Despesa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${expense.description} - ${CurrencyUtils.formatCents(expense.valueCents)}", style = MaterialTheme.typography.titleMedium, color = StatusRed)

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (selectedAccountId.isNotBlank()) {
                            onConfirmPay(selectedAccountId, PaymentMethod.PIX)
                        }
                    }) {
                        Text("Pagar Agora")
                    }
                }
            }
        }
    }
}

@Composable
fun TransferDialog(
    accounts: List<FinancialAccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long, String) -> Unit
) {
    var sourceId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var destId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: "") }
    var valueText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("Transferência entre contas internas") }

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
                Text("Transferência Interna", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Valor da Transferência (R$)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val valCents = CurrencyUtils.parseToCents(valueText)
                        if (sourceId.isNotBlank() && destId.isNotBlank() && valCents > 0) {
                            onConfirm(sourceId, destId, valCents, description)
                        }
                    }) {
                        Text("Transferir")
                    }
                }
            }
        }
    }
}
