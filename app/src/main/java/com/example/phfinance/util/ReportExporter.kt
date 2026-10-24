package com.example.phfinance.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.phfinance.data.local.BankTransactionEntity
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.local.ReconciliationEntity
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import java.io.File
import java.io.FileOutputStream

object ReportExporter {

    fun exportReceivablesCsv(
        context: Context,
        receivables: List<ReceivableEntity>,
        clients: List<ClientEntity>
    ) {
        val clientMap = clients.associateBy { it.id }
        val csvHeader = "Competencia,Aluno,Valor Original,Desconto,Valor Liquido,Valor Recebido,Status,Vencimento\n"
        val csvBody = receivables.joinToString("\n") { r ->
            val clientName = clientMap[r.clientId]?.fullName ?: "Aluno"
            val status = r.status.label
            val orig = CurrencyUtils.formatCents(r.originalValueCents)
            val desc = CurrencyUtils.formatCents(r.discountCents)
            val net = CurrencyUtils.formatCents(r.netValueCents)
            val rec = CurrencyUtils.formatCents(r.receivedValueCents)
            val due = DateUtils.formatDate(r.dueDate)
            "\"${r.competence}\",\"$clientName\",\"$orig\",\"$desc\",\"$net\",\"$rec\",\"$status\",\"$due\""
        }

        val fileContent = csvHeader + csvBody
        shareTextFile(context, fileContent, "relatorio_contas_a_receber.csv", "text/csv")
    }

    fun exportExpensesCsv(
        context: Context,
        expenses: List<ExpenseEntity>
    ) {
        val csvHeader = "Competencia,Descricao,Categoria,Fornecedor,Valor,Status,Tipo,Vencimento\n"
        val csvBody = expenses.joinToString("\n") { e ->
            val valStr = CurrencyUtils.formatCents(e.valueCents)
            val typeStr = if (e.isOwnerPersonalWithdrawal) "Retirada Pessoal" else "Despesa Operacional"
            val due = DateUtils.formatDate(e.dueDate)
            "\"${e.competence}\",\"${e.description}\",\"${e.category}\",\"${e.supplier}\",\"$valStr\",\"${e.status.label}\",\"$typeStr\",\"$due\""
        }

        val fileContent = csvHeader + csvBody
        shareTextFile(context, fileContent, "relatorio_despesas.csv", "text/csv")
    }

    fun exportInadimplenciaCsv(
        context: Context,
        receivables: List<ReceivableEntity>,
        clients: List<ClientEntity>
    ) {
        val clientMap = clients.associateBy { it.id }
        val now = System.currentTimeMillis()
        val overdueReceivables = receivables.filter {
            (it.status == com.example.phfinance.data.model.ReceivableStatus.PENDENTE || it.status == com.example.phfinance.data.model.ReceivableStatus.PARCIALMENTE_PAGA) &&
            it.dueDate < now
        }

        val csvHeader = "Aluno,Telefone,Competencia,Vencimento,Dias de Atraso,Valor Liquido,Valor Recebido,Saldo Devedor\n"
        val csvBody = overdueReceivables.joinToString("\n") { r ->
            val client = clientMap[r.clientId]
            val clientName = client?.fullName ?: "Aluno"
            val phone = client?.whatsapp?.ifBlank { client.phone } ?: "-"
            val due = DateUtils.formatDate(r.dueDate)
            val daysOverdue = ((now - r.dueDate) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
            val net = CurrencyUtils.formatCents(r.netValueCents)
            val rec = CurrencyUtils.formatCents(r.receivedValueCents)
            val pending = CurrencyUtils.formatCents(r.netValueCents - r.receivedValueCents)
            "\"$clientName\",\"$phone\",\"${r.competence}\",\"$due\",\"$daysOverdue\",\"$net\",\"$rec\",\"$pending\""
        }

        val fileContent = csvHeader + csvBody
        shareTextFile(context, fileContent, "relatorio_inadimplencia.csv", "text/csv")
    }

    fun exportMonthlyDREPdf(
        context: Context,
        dreResult: MonthlyDREResult,
        settings: com.example.phfinance.data.local.BusinessSettingsEntity?
    ) {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 16f
            isFakeBoldText = true
        }
        val subtitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 11f
        }
        val bodyPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
        }
        val boldBodyPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
            isFakeBoldText = true
        }
        val warningPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            textSize = 8f
        }

        var y = 40f
        canvas.drawText("PH PERSONAL - DEMONSTRATIVO FINANCEIRO GERENCIAL", 30f, y, titlePaint)
        y += 20f
        canvas.drawText("Razão Social: ${settings?.businessName ?: "PH Personal"} | Competência: ${dreResult.competence}", 30f, y, subtitlePaint)
        y += 15f
        canvas.drawText("Emissão: ${DateUtils.formatDate(System.currentTimeMillis())} | Moeda: BRL (R$)", 30f, y, subtitlePaint)
        y += 20f

        canvas.drawLine(30f, y, 565f, y, titlePaint)
        y += 20f

        // REGIME DE COMPETÊNCIA
        canvas.drawText("1. REGIME DE COMPETÊNCIA (ACCRUAL BASIS)", 30f, y, boldBodyPaint)
        y += 15f
        canvas.drawText("Receita Bruta Faturada: ${CurrencyUtils.formatCents(dreResult.accrualGrossRevenueCents)}", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("(-) Descontos Concedidos: ${CurrencyUtils.formatCents(dreResult.accrualDiscountsCents)}", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("(=) Receita Líquida por Competência: ${CurrencyUtils.formatCents(dreResult.accrualNetRevenueCents)}", 40f, y, boldBodyPaint)
        y += 15f
        canvas.drawText("(-) Despesas Incorridas no Mês: ${CurrencyUtils.formatCents(dreResult.accrualExpensesCents)}", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("(=) Resultado Operacional por Competência: ${CurrencyUtils.formatCents(dreResult.accrualOperatingResultCents)}", 40f, y, boldBodyPaint)
        y += 25f

        // REGIME DE CAIXA
        canvas.drawText("2. REGIME DE CAIXA (CASH BASIS - ENTRADAS E SAÍDAS REALIZADAS)", 30f, y, boldBodyPaint)
        y += 15f
        canvas.drawText("Receita Bruta Recebida em Conta: ${CurrencyUtils.formatCents(dreResult.cashGrossRevenueCents)}", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("(-) Taxas de Meios de Pagamento: ${CurrencyUtils.formatCents(dreResult.cashPaymentFeesCents)}", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("(=) Receita Líquida Recebida: ${CurrencyUtils.formatCents(dreResult.cashNetRevenueCents)}", 40f, y, boldBodyPaint)
        y += 15f
        canvas.drawText("(-) Despesas Operacionais Pagas: ${CurrencyUtils.formatCents(dreResult.cashOperationalExpensesCents)}", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("(=) Resultado Operacional de Caixa: ${CurrencyUtils.formatCents(dreResult.cashOperatingResultCents)}", 40f, y, boldBodyPaint)
        y += 15f
        canvas.drawText("(-) Retiradas / Pró-Labore do Proprietário: ${CurrencyUtils.formatCents(dreResult.cashOwnerWithdrawalsCents)}", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("(=) Variação Líquida de Caixa no Período: ${CurrencyUtils.formatCents(dreResult.cashNetPeriodVariationCents)}", 40f, y, boldBodyPaint)
        y += 30f

        // AVISO LEGAL
        canvas.drawLine(30f, y, 565f, y, subtitlePaint)
        y += 18f
        canvas.drawText("AVISO LEGAL DE RESPONSABILIDADE:", 30f, y, boldBodyPaint)
        y += 14f
        canvas.drawText("Relatório gerencial interno para tomada de decisão da consultoria PH Personal - Não substitui a escrituração contábil formal.", 30f, y, warningPaint)

        pdfDocument.finishPage(page)

        try {
            val cacheDir = File(context.cacheDir, "reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val fileName = "DRE_PHPersonal_${dreResult.competence.replace("/", "_")}.pdf"
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Compartilhar Demonstrativo DRE (PDF)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sharePaymentReceiptText(
        context: Context,
        payment: PaymentEntity,
        client: ClientEntity?,
        receivable: ReceivableEntity?
    ) {
        val clientName = client?.fullName ?: "Aluno"
        val valStr = CurrencyUtils.formatCents(payment.grossAmountCents)
        val dateStr = DateUtils.formatDate(payment.paymentDate)
        val competenceStr = receivable?.competence ?: "-"

        val receiptText = """
            ========================================
                     RECIBO DE PAGAMENTO PARTICULAR
                            PH PERSONAL
            ========================================
            Data: $dateStr
            Competência: $competenceStr
            
            Recebemos de: $clientName
            A quantia de: $valStr
            Forma de Pagamento: ${payment.paymentMethod.label}
            Ref. Transação: ${payment.transactionRef.ifBlank { "N/A" }}
            
            Descrição: Quitação de mensalidade / serviços prestados de treinamento e consultoria física.
            
            ========================================
            Documento emitido sem fins de nota fiscal.
                     PH Personal Finance
            ========================================
        """.trimIndent()

        shareTextFile(context, receiptText, "recibo_${payment.id.take(8)}.txt", "text/plain")
    }

    fun exportReconciliationCsv(
        context: Context,
        accountName: String,
        transactions: List<BankTransactionEntity>,
        reconciliations: List<ReconciliationEntity>
    ) {
        val recMap = reconciliations.associateBy { it.bankTransactionId }
        val csvHeader = "Data,Conta,Descricao Extrato,Tipo,Valor,Situacao,Tipo Conciliacao,Valor Conciliado,Taxa Retida\n"
        val csvBody = transactions.joinToString("\n") { tx ->
            val dateStr = DateUtils.formatDate(tx.transactionDate)
            val valStr = CurrencyUtils.formatCents(tx.amountCents)
            val rec = recMap[tx.id]
            val recTypeStr = rec?.reconciliationType?.label ?: "-"
            val recValStr = rec?.let { CurrencyUtils.formatCents(it.reconciledAmountCents) } ?: "-"
            val feeStr = rec?.let { CurrencyUtils.formatCents(it.feeCents) } ?: "-"

            "\"$dateStr\",\"$accountName\",\"${tx.memo.replace("\"", "'")}\",\"${tx.movementType.label}\",\"$valStr\",\"${tx.status.label}\",\"$recTypeStr\",\"$recValStr\",\"$feeStr\""
        }

        val fileContent = csvHeader + csvBody
        val fileName = "conciliacao_${accountName.replace(" ", "_").lowercase()}.csv"
        shareTextFile(context, fileContent, fileName, "text/csv")
    }

    fun exportReconciliationPdf(
        context: Context,
        accountName: String,
        transactions: List<BankTransactionEntity>,
        reconciliations: List<ReconciliationEntity>
    ) {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 15f
            isFakeBoldText = true
        }
        val subtitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 10f
        }
        val boldBodyPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 9f
            isFakeBoldText = true
        }
        val bodyPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 8.5f
        }

        var y = 35f
        canvas.drawText("PH PERSONAL - RELATÓRIO DE CONCILIAÇÃO BANCÁRIA", 30f, y, titlePaint)
        y += 18f
        canvas.drawText("Conta Financeira: $accountName | Emissão: ${DateUtils.formatDate(System.currentTimeMillis())}", 30f, y, subtitlePaint)
        y += 15f

        val totalImported = transactions.size
        val reconciledCount = transactions.count { it.status == com.example.phfinance.data.model.BankTransactionStatus.CONCILIADA }
        val pendingCount = transactions.count { it.status == com.example.phfinance.data.model.BankTransactionStatus.PENDENTE }
        val duplicateCount = transactions.count { it.status == com.example.phfinance.data.model.BankTransactionStatus.SUSPEITA_DUPLICIDADE }

        canvas.drawText("Resumo: $totalImported transações ($reconciledCount conciliadas, $pendingCount pendentes, $duplicateCount suspeitas de duplicidade)", 30f, y, subtitlePaint)
        y += 18f
        canvas.drawLine(30f, y, 565f, y, titlePaint)
        y += 15f

        canvas.drawText("DATA", 30f, y, boldBodyPaint)
        canvas.drawText("HISTÓRICO / MEMO", 90f, y, boldBodyPaint)
        canvas.drawText("VALOR", 350f, y, boldBodyPaint)
        canvas.drawText("SITUAÇÃO", 440f, y, boldBodyPaint)
        y += 12f
        canvas.drawLine(30f, y, 565f, y, subtitlePaint)
        y += 12f

        for (tx in transactions.take(35)) {
            val dateStr = DateUtils.formatDate(tx.transactionDate)
            val memoShort = if (tx.memo.length > 38) tx.memo.take(35) + "..." else tx.memo
            val valStr = CurrencyUtils.formatCents(tx.amountCents)
            val statusStr = tx.status.label

            canvas.drawText(dateStr, 30f, y, bodyPaint)
            canvas.drawText(memoShort, 90f, y, bodyPaint)
            canvas.drawText(valStr, 350f, y, bodyPaint)
            canvas.drawText(statusStr, 440f, y, bodyPaint)
            y += 14f

            if (y > 800f) break
        }

        pdfDocument.finishPage(page)

        try {
            val cacheDir = File(context.cacheDir, "reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val fileName = "relatorio_conciliacao_${System.currentTimeMillis()}.pdf"
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Compartilhar Relatório de Conciliação (PDF)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shareTextFile(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String
    ) {
        try {
            val cacheDir = File(context.cacheDir, "reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray())
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Exportar Relatório / Recibo")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

