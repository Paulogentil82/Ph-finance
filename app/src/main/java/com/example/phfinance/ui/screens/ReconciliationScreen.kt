package com.example.phfinance.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.phfinance.data.local.BankImportEntity
import com.example.phfinance.data.local.BankTransactionEntity
import com.example.phfinance.data.local.FinancialAccountEntity
import com.example.phfinance.data.local.ReconciliationEntity
import com.example.phfinance.data.model.BankTransactionStatus
import com.example.phfinance.data.model.CsvConfig
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.MatchConfidence
import com.example.phfinance.data.model.MovementType
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReconciliationType
import com.example.phfinance.ui.theme.EmeraldContainer
import com.example.phfinance.ui.theme.EmeraldPrimary
import com.example.phfinance.ui.theme.OnEmeraldContainer
import com.example.phfinance.ui.theme.StatusBlue
import com.example.phfinance.ui.theme.StatusGreen
import com.example.phfinance.ui.theme.StatusRed
import com.example.phfinance.ui.theme.StatusYellow
import com.example.phfinance.ui.viewmodel.PhFinanceViewModel
import com.example.phfinance.util.ReportExporter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationScreen(viewModel: PhFinanceViewModel) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsState()
    val allTransactions by viewModel.bankTransactions.collectAsState()
    val allImports by viewModel.bankImports.collectAsState()
    val allReconciliations by viewModel.reconciliations.collectAsState()
    val filterStatus by viewModel.reconciliationFilterStatus.collectAsState()
    val isProcessing by viewModel.isReconciling.collectAsState()
    val selectedAccountId by viewModel.selectedReconciliationAccountId.collectAsState()

    // Determine current active account
    val activeAccount = accounts.find { it.id == selectedAccountId }
        ?: accounts.find { it.isDefault }
        ?: accounts.firstOrNull()

    var showAccountDropdown by remember { mutableStateOf(false) }
    var showCsvConfigDialog by remember { mutableStateOf(false) }
    var showImportHistoryDialog by remember { mutableStateOf(false) }
    var showUndoDialogFor by remember { mutableStateOf<ReconciliationEntity?>(null) }
    var csvConfig by remember { mutableStateOf(CsvConfig()) }

    val selectedTxForMatch by viewModel.selectedTransactionForMatch.collectAsState()
    val matchSuggestion by viewModel.currentMatchSuggestion.collectAsState()

    // Filter transactions by selected account
    val accountTransactions = remember(allTransactions, activeAccount) {
        if (activeAccount == null) emptyList()
        else allTransactions.filter { it.accountId == activeAccount.id }
    }

    val filteredTransactions = remember(accountTransactions, filterStatus) {
        when (filterStatus) {
            "PENDENTES" -> accountTransactions.filter { it.status == BankTransactionStatus.PENDENTE || it.status == BankTransactionStatus.IMPORTADA }
            "CONCILIADAS" -> accountTransactions.filter { it.status == BankTransactionStatus.CONCILIADA }
            "DUPLICIDADES" -> accountTransactions.filter { it.status == BankTransactionStatus.SUSPEITA_DUPLICIDADE }
            "IGNORADAS" -> accountTransactions.filter { it.status == BankTransactionStatus.IGNORADA }
            else -> accountTransactions
        }
    }

    // Native file picker for OFX
    val ofxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && activeAccount != null) {
            val contentResolver = context.contentResolver
            val fileName = getFileNameFromUri(context, uri) ?: "extrato.ofx"
            try {
                val stream = contentResolver.openInputStream(uri)
                if (stream != null) {
                    val size = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 1024L
                    viewModel.importOfxStream(stream, activeAccount.id, fileName, size) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao abrir arquivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Native file picker for CSV
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && activeAccount != null) {
            val contentResolver = context.contentResolver
            val fileName = getFileNameFromUri(context, uri) ?: "extrato.csv"
            try {
                val stream = contentResolver.openInputStream(uri)
                if (stream != null) {
                    val size = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 1024L
                    viewModel.importCsvStream(stream, activeAccount.id, fileName, size, csvConfig) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao abrir arquivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("reconciliation_screen")
    ) {
        // --- TOP BAR: ACCOUNT SELECTOR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Conciliação Bancária",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Importação e auditoria de extratos OFX e CSV",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Export Actions
            Row {
                IconButton(
                    onClick = {
                        if (activeAccount != null) {
                            ReportExporter.exportReconciliationCsv(
                                context = context,
                                accountName = activeAccount.name,
                                transactions = accountTransactions,
                                reconciliations = allReconciliations
                            )
                        }
                    },
                    modifier = Modifier.testTag("export_reconciliation_csv_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Exportar CSV", tint = EmeraldPrimary)
                }

                IconButton(
                    onClick = {
                        if (activeAccount != null) {
                            ReportExporter.exportReconciliationPdf(
                                context = context,
                                accountName = activeAccount.name,
                                transactions = accountTransactions,
                                reconciliations = allReconciliations
                            )
                        }
                    },
                    modifier = Modifier.testTag("export_reconciliation_pdf_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Exportar PDF", tint = EmeraldPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- ACCOUNT PICKER CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))

                ExposedDropdownMenuBox(
                    expanded = showAccountDropdown,
                    onExpandedChange = { showAccountDropdown = !showAccountDropdown },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = activeAccount?.let { "${it.name} (${CurrencyUtils.formatCents(it.currentBalanceCents)})" } ?: "Selecione uma Conta",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Conta Bancária Ativa") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showAccountDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("account_selector_field")
                    )
                    ExposedDropdownMenu(
                        expanded = showAccountDropdown,
                        onDismissRequest = { showAccountDropdown = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.name} — Saldo: ${CurrencyUtils.formatCents(acc.currentBalanceCents)}") },
                                onClick = {
                                    viewModel.setReconciliationAccount(acc.id)
                                    showAccountDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- METRICS SUMMARY CARDS ---
        val totalTrans = accountTransactions.size
        val reconciledCount = accountTransactions.count { it.status == BankTransactionStatus.CONCILIADA }
        val pendingCount = accountTransactions.count { it.status == BankTransactionStatus.PENDENTE || it.status == BankTransactionStatus.IMPORTADA }
        val duplicateCount = accountTransactions.count { it.status == BankTransactionStatus.SUSPEITA_DUPLICIDADE }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryMetricCard("Total", "$totalTrans", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            SummaryMetricCard("Conciliadas", "$reconciledCount", StatusGreen, Modifier.weight(1f))
            SummaryMetricCard("Pendentes", "$pendingCount", StatusYellow, Modifier.weight(1f))
            SummaryMetricCard("Duplicidades", "$duplicateCount", if (duplicateCount > 0) StatusRed else MaterialTheme.colorScheme.outline, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- IMPORT ACTION BUTTONS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    ofxPickerLauncher.launch("*/*")
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_ofx_button"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("OFX", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    showCsvConfigDialog = true
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_csv_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("CSV", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showImportHistoryDialog = true },
                modifier = Modifier.testTag("history_button")
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Histórico")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Sample Data Demo helper (for testing in emulator)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "⚡ Injetar Amostra Demonstrativa OFX",
                color = EmeraldPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable {
                        if (activeAccount != null) {
                            val sampleOfx = buildSampleOfxContent()
                            viewModel.importOfxStream(
                                inputStream = sampleOfx.byteInputStream(),
                                accountId = activeAccount.id,
                                fileName = "extrato_demo_nubank.ofx",
                                fileSize = sampleOfx.toByteArray().size.toLong()
                            ) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
                    .testTag("sample_ofx_injection_button")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- FILTER CHIPS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf("TODAS", "PENDENTES", "CONCILIADAS", "DUPLICIDADES", "IGNORADAS")
            filters.forEach { f ->
                val isSelected = filterStatus == f
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setReconciliationFilter(f) },
                    label = { Text(f, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = EmeraldPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isProcessing) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
        }

        // --- TRANSACTIONS LIST ---
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nenhuma transação encontrada para este filtro.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Importe um arquivo OFX ou CSV do seu banco empresarial.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("transactions_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { tx ->
                    BankTransactionCard(
                        transaction = tx,
                        reconciliation = allReconciliations.find { it.bankTransactionId == tx.id && it.status == "ATIVA" },
                        onReconcileClick = {
                            viewModel.loadMatchSuggestion(tx)
                        },
                        onUndoClick = { rec ->
                            showUndoDialogFor = rec
                        },
                        onIgnoreClick = {
                            viewModel.ignoreBankTransaction(tx.id)
                        },
                        onRestoreClick = {
                            viewModel.restoreBankTransaction(tx.id)
                        }
                    )
                }
            }
        }
    }

    // --- CSV CONFIG DIALOG ---
    if (showCsvConfigDialog && activeAccount != null) {
        ImportCsvConfigDialog(
            currentConfig = csvConfig,
            onDismiss = { showCsvConfigDialog = false },
            onConfirm = { updatedConfig ->
                csvConfig = updatedConfig
                showCsvConfigDialog = false
                csvPickerLauncher.launch("*/*")
            }
        )
    }

    // --- MATCH SUGGESTION & RECONCILIATION MODAL ---
    if (selectedTxForMatch != null && matchSuggestion != null) {
        ReconciliationMatchDialog(
            transaction = selectedTxForMatch!!,
            suggestion = matchSuggestion!!,
            viewModel = viewModel,
            onDismiss = { viewModel.clearMatchSuggestion() }
        )
    }

    // --- UNDO RECONCILIATION DIALOG ---
    if (showUndoDialogFor != null) {
        UndoReconciliationDialog(
            reconciliation = showUndoDialogFor!!,
            onDismiss = { showUndoDialogFor = null },
            onConfirm = { reason ->
                viewModel.undoReconciliation(showUndoDialogFor!!.id, reason) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                showUndoDialogFor = null
            }
        )
    }

    // --- IMPORT HISTORY DIALOG ---
    if (showImportHistoryDialog) {
        ImportHistoryDialog(
            imports = allImports,
            onDismiss = { showImportHistoryDialog = false }
        )
    }
}

@Composable
fun SummaryMetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        }
    }
}

@Composable
fun BankTransactionCard(
    transaction: BankTransactionEntity,
    reconciliation: ReconciliationEntity?,
    onReconcileClick: () -> Unit,
    onUndoClick: (ReconciliationEntity) -> Unit,
    onIgnoreClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val isCredit = transaction.amountCents >= 0
    val amountColor = if (isCredit) StatusGreen else StatusRed

    val statusColor = when (transaction.status) {
        BankTransactionStatus.CONCILIADA -> StatusGreen
        BankTransactionStatus.SUSPEITA_DUPLICIDADE -> StatusRed
        BankTransactionStatus.PENDENTE -> StatusYellow
        BankTransactionStatus.IGNORADA -> MaterialTheme.colorScheme.outline
        BankTransactionStatus.IMPORTADA -> StatusBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tx_card_${transaction.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateUtils.formatDate(transaction.transactionDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = transaction.status.label,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.memo,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    if (transaction.fitId.isNotBlank()) {
                        Text(
                            text = "Doc/Ref: ${transaction.fitId.take(16)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = (if (isCredit) "+ " else "") + CurrencyUtils.formatCents(transaction.amountCents),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = amountColor
                )
            }

            // Reconciliation details if present
            if (reconciliation != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(EmeraldContainer.copy(alpha = 0.3f))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${reconciliation.reconciliationType.label} — ${CurrencyUtils.formatCents(reconciliation.reconciledAmountCents)}",
                            fontSize = 12.sp,
                            color = OnEmeraldContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (transaction.status) {
                    BankTransactionStatus.PENDENTE, BankTransactionStatus.IMPORTADA, BankTransactionStatus.SUSPEITA_DUPLICIDADE -> {
                        OutlinedButton(
                            onClick = onIgnoreClick,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text("Ignorar", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onReconcileClick,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier.testTag("btn_conciliar_${transaction.id}")
                        ) {
                            Text("Conciliar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    BankTransactionStatus.CONCILIADA -> {
                        if (reconciliation != null) {
                            OutlinedButton(
                                onClick = { onUndoClick(reconciliation) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusYellow),
                                modifier = Modifier.testTag("btn_desfazer_${transaction.id}")
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Desfazer", fontSize = 12.sp)
                            }
                        }
                    }
                    BankTransactionStatus.IGNORADA -> {
                        OutlinedButton(onClick = onRestoreClick) {
                            Text("Restaurar", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReconciliationMatchDialog(
    transaction: BankTransactionEntity,
    suggestion: com.example.phfinance.data.model.ReconciliationMatchSuggestion,
    viewModel: PhFinanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val receivables by viewModel.receivables.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val clients by viewModel.allClients.collectAsState()
    val clientMap = clients.associateBy { it.id }

    var enteredFeeText by remember { mutableStateOf(if (suggestion.suggestedFeeCents > 0) CurrencyUtils.formatCents(suggestion.suggestedFeeCents) else "R$ 0,00") }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.PIX) }

    val confidenceColor = when (suggestion.confidence) {
        MatchConfidence.FORTE -> StatusGreen
        MatchConfidence.POSSIVEL -> StatusYellow
        MatchConfidence.INSUFICIENTE -> MaterialTheme.colorScheme.error
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("match_suggestion_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Conciliação Determinística",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Transaction Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(transaction.memo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Data: ${DateUtils.formatDate(transaction.transactionDate)}", fontSize = 11.sp)
                        Text(
                            text = "Valor: ${CurrencyUtils.formatCents(transaction.amountCents)}",
                            fontWeight = FontWeight.Bold,
                            color = if (transaction.amountCents >= 0) StatusGreen else StatusRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Confidence Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(confidenceColor.copy(alpha = 0.15f))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (suggestion.confidence == MatchConfidence.FORTE) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = confidenceColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = suggestion.confidence.label,
                                fontWeight = FontWeight.Bold,
                                color = confidenceColor,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = suggestion.justification,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action execution based on suggestion
                if (suggestion.matchedPaymentId != null) {
                    // Fluxo A
                    val payment = payments.find { it.id == suggestion.matchedPaymentId }
                    val client = payment?.let { clientMap[it.clientId] }

                    Text("Fluxo A: Vincular ao pagamento já existente", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("O valor já foi lançado no caixa anteriormente. O sistema apenas conectará a transação sem duplicar saldo.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.confirmFluxoA(transaction.id, suggestion.matchedPaymentId) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_fluxo_a_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text("Confirmar Vínculo (Fluxo A)")
                    }
                } else if (suggestion.matchedReceivableId != null) {
                    // Fluxo B
                    val receivable = receivables.find { it.id == suggestion.matchedReceivableId }
                    val client = receivable?.let { clientMap[it.clientId] }

                    Text("Fluxo B: Baixa de Cobrança / Recebimento", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Cria o registro financeiro, atualiza a mensalidade para quitada/parcial e credita o valor na conta.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = enteredFeeText,
                        onValueChange = { enteredFeeText = it },
                        label = { Text("Taxa de Meio de Pagamento (se houver)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val feeCents = CurrencyUtils.parseToCents(enteredFeeText)
                            viewModel.confirmFluxoB(transaction.id, suggestion.matchedReceivableId, feeCents, selectedMethod) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_fluxo_b_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text("Confirmar Baixa e Quitação (Fluxo B)")
                    }
                } else if (suggestion.matchedExpenseId != null) {
                    val expense = expenses.find { it.id == suggestion.matchedExpenseId }
                    Text("Liquidação de Despesa Operacional", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Baixa a conta a pagar '${expense?.description}' e debita na conta bancária.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.confirmExpense(transaction.id, suggestion.matchedExpenseId) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_expense_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text("Confirmar Baixa da Despesa")
                    }
                } else {
                    // No automatic match: allow manual selection or ignoring
                    Text("Selecione um lançamento pendente manualmente ou ignore a transação:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.ignoreBankTransaction(transaction.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ignorar Transação")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
fun UndoReconciliationDialog(
    reconciliation: ReconciliationEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("undo_reconciliation_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Desfazer Conciliação",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Esta operação é auditável e reverterá qualquer movimentação de saldo e baixa de cobrança gerada por esta conciliação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Justificativa Obrigatória") },
                    placeholder = { Text("Ex: Conciliado com aluno incorreto") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("undo_reason_input")
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
                            if (reason.isBlank()) {
                                return@Button
                            }
                            onConfirm(reason)
                        },
                        enabled = reason.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                        modifier = Modifier.testTag("confirm_undo_button")
                    ) {
                        Text("Desfazer Conciliação")
                    }
                }
            }
        }
    }
}

@Composable
fun ImportCsvConfigDialog(
    currentConfig: CsvConfig,
    onDismiss: () -> Unit,
    onConfirm: (CsvConfig) -> Unit
) {
    var delimiter by remember { mutableStateOf(currentConfig.delimiter) }
    var decimalSep by remember { mutableStateOf(currentConfig.decimalSeparator) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Configuração do Extrato CSV",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Separador de Colunas:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = delimiter == ';',
                        onClick = { delimiter = ';' },
                        label = { Text("Ponto e Vírgula (;)") }
                    )
                    FilterChip(
                        selected = delimiter == ',',
                        onClick = { delimiter = ',' },
                        label = { Text("Vírgula (,)") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Separador Decimal de Valores:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = decimalSep == ',',
                        onClick = { decimalSep = ',' },
                        label = { Text("Vírgula (1.250,00)") }
                    )
                    FilterChip(
                        selected = decimalSep == '.',
                        onClick = { decimalSep = '.' },
                        label = { Text("Ponto (1250.00)") }
                    )
                }

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
                            onConfirm(currentConfig.copy(delimiter = delimiter, decimalSeparator = decimalSep))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Text("Selecionar Arquivo")
                    }
                }
            }
        }
    }
}

@Composable
fun ImportHistoryDialog(
    imports: List<BankImportEntity>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Histórico de Importações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (imports.isEmpty()) {
                    Text(
                        text = "Nenhum arquivo importado até o momento.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(imports) { imp ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(imp.fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(imp.fileType, fontSize = 11.sp, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Text("Data: ${DateUtils.formatDate(imp.importTimestamp)}", fontSize = 11.sp)
                                    Text("Transações: ${imp.totalTransactions} | Entradas: ${CurrencyUtils.formatCents(imp.totalCreditsCents)} | Saídas: ${CurrencyUtils.formatCents(imp.totalDebitsCents)}", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = it.getString(index)
            }
        }
    }
    return name ?: uri.path?.substringAfterLast('/')
}

private fun buildSampleOfxContent(): String {
    val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    return """
        OFXHEADER:100
        DATA:OFXSGML
        VERSION:102
        SECURITY:NONE
        ENCODING:USASCII
        CHARSET:1252
        COMPRESSION:NONE
        OLDFILEUID:NONE
        NEWFILEUID:NONE
        <OFX>
        <SIGNONMSGSRSV1>
        <SONRS>
        <STATUS>
        <CODE>0
        <SEVERITY>INFO
        </STATUS>
        <DTSERVER>${today}120000
        <LANGUAGE>POR
        </SONRS>
        </SIGNONMSGSRSV1>
        <BANKMSGSRSV1>
        <STMTTRNRS>
        <TRNUID>1001
        <STATUS>
        <CODE>0
        <SEVERITY>INFO
        </STATUS>
        <STMTRS>
        <CURDEF>BRL
        <BANKACCTFROM>
        <BANKID>260
        <ACCTID>0001998877
        <ACCTTYPE>CHECKING
        </BANKACCTFROM>
        <BANKTRANLIST>
        <DTSTART>${today}000000
        <DTEND>${today}235959
        <STMTTRN>
        <TRNTYPE>CREDIT
        <DTPOSTED>${today}100000
        <TRNAMT>200.00
        <FITID>NUB_PIX_RECEIVABLE_001
        <MEMO>PIX RECEBIDO - ALUNO CONSULTORIA
        </STMTTRN>
        <STMTTRN>
        <TRNTYPE>CREDIT
        <DTPOSTED>${today}113000
        <TRNAMT>540.00
        <FITID>NUB_PIX_TRIMESTRAL_002
        <MEMO>PIX RECEBIDO - PACOTE TRIMESTRAL
        </STMTTRN>
        <STMTTRN>
        <TRNTYPE>DEBIT
        <DTPOSTED>${today}150000
        <TRNAMT>-89.90
        <FITID>NUB_DEB_ASSINATURA_003
        <MEMO>ASSINATURA APLICATIVOS FITNESS
        </STMTTRN>
        </BANKTRANLIST>
        <LEDGERBAL>
        <BALAMT>3150.10
        <DTASOF>${today}235959
        </LEDGERBAL>
        </STMTRS>
        </STMTTRNRS>
        </BANKMSGSRSV1>
        </OFX>
    """.trimIndent()
}
