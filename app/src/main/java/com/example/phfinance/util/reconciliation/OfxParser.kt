package com.example.phfinance.util.reconciliation

import com.example.phfinance.data.model.MovementType
import com.example.phfinance.data.model.ParsedBankStatement
import com.example.phfinance.data.model.ParsedBankTransaction
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

object OfxParser {

    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    private const val MAX_TRANSACTIONS = 5000

    /**
     * Parses an OFX statement from input stream or string content.
     * Handles both SGML and XML style OFX files produced by Brazilian banks (Itaú, Bradesco, BB, Nubank, Inter, Santander, etc.).
     */
    fun parse(
        inputStream: InputStream,
        accountId: String,
        fileName: String,
        fileSizeBytes: Long
    ): ParsedBankStatement {
        require(fileSizeBytes <= MAX_FILE_SIZE_BYTES) {
            "O arquivo excede o limite de tamanho seguro (máximo 10MB)."
        }

        val rawBytes = inputStream.readBytes()
        val content = detectCharsetAndDecode(rawBytes)
        return parseString(content, accountId, fileName, fileSizeBytes)
    }

    fun parseString(
        content: String,
        accountId: String,
        fileName: String,
        fileSizeBytes: Long = content.toByteArray().size.toLong()
    ): ParsedBankStatement {
        if (!content.contains("<OFX>", ignoreCase = true) && !content.contains("OFXHEADER", ignoreCase = true)) {
            throw IllegalArgumentException("Arquivo não possui estrutura OFX válida.")
        }

        val transactions = mutableListOf<ParsedBankTransaction>()
        var minDate = Long.MAX_VALUE
        var maxDate = 0L

        // Find each STMTTRN block
        val regex = Regex("<STMTTRN>([\\s\\S]*?)(?=<STMTTRN>|</BANKTRANLIST>|$)", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(content).take(MAX_TRANSACTIONS)

        for (match in matches) {
            val block = match.groups[1]?.value ?: continue
            val trnType = extractTagValue(block, "TRNTYPE")?.uppercase() ?: "OTHER"
            val dtPostedRaw = extractTagValue(block, "DTPOSTED")
            val trnAmtRaw = extractTagValue(block, "TRNAMT")
            val fitId = extractTagValue(block, "FITID")?.trim() ?: UUID.randomUUID().toString()
            val name = extractTagValue(block, "NAME")?.trim() ?: ""
            val memo = extractTagValue(block, "MEMO")?.trim() ?: ""

            if (trnAmtRaw == null || dtPostedRaw == null) continue

            val date = parseOfxDate(dtPostedRaw)
            val amountCents = parseOfxAmount(trnAmtRaw)
            if (date < minDate) minDate = date
            if (date > maxDate) maxDate = date

            val movementType = if (amountCents >= 0) MovementType.ENTRADA else MovementType.SAIDA
            val description = when {
                memo.isNotBlank() && name.isNotBlank() && memo != name -> "$name - $memo"
                memo.isNotBlank() -> memo
                name.isNotBlank() -> name
                else -> "Transação Bancária"
            }

            transactions.add(
                ParsedBankTransaction(
                    fitId = fitId,
                    date = date,
                    amountCents = amountCents,
                    movementType = movementType,
                    memo = description,
                    payee = name
                )
            )
        }

        if (transactions.isEmpty()) {
            throw IllegalArgumentException("Nenhuma transação financeira válida encontrada no extrato OFX.")
        }

        val startDate = if (minDate == Long.MAX_VALUE) System.currentTimeMillis() else minDate
        val endDate = if (maxDate == 0L) System.currentTimeMillis() else maxDate

        return ParsedBankStatement(
            accountId = accountId,
            fileName = fileName,
            fileType = "OFX",
            fileSizeBytes = fileSizeBytes,
            startDate = startDate,
            endDate = endDate,
            transactions = transactions
        )
    }

    private fun extractTagValue(block: String, tagName: String): String? {
        // Handle XML <TAG>value</TAG> or SGML <TAG>value\n
        val pattern = Regex("<$tagName>([^<\r\n]+)(?:</$tagName>)?", RegexOption.IGNORE_CASE)
        val match = pattern.find(block)
        return match?.groups?.get(1)?.value?.trim()
    }

    fun parseOfxDate(rawDate: String): Long {
        val clean = rawDate.trim()
        val datePart = if (clean.length >= 8) clean.substring(0, 8) else clean
        val formats = listOf(
            "yyyyMMddHHmmss",
            "yyyyMMdd",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val parsed = sdf.parse(clean.take(pattern.length))
                if (parsed != null) return parsed.time
            } catch (_: Exception) { }
        }
        return System.currentTimeMillis()
    }

    fun parseOfxAmount(rawAmount: String): Long {
        val clean = rawAmount.replace(",", ".").trim()
        val bd = BigDecimal(clean).setScale(2, RoundingMode.HALF_UP)
        return bd.multiply(BigDecimal(100)).longValueExact()
    }

    private fun detectCharsetAndDecode(bytes: ByteArray): String {
        // Try UTF-8 first, fallback to ISO-8859-1 (common in Brazilian banks)
        return try {
            val utf8 = String(bytes, Charsets.UTF_8)
            if (utf8.contains("<OFX", ignoreCase = true) || utf8.contains("OFXHEADER", ignoreCase = true)) {
                utf8
            } else {
                String(bytes, Charset.forName("ISO-8859-1"))
            }
        } catch (_: Exception) {
            String(bytes, Charset.forName("ISO-8859-1"))
        }
    }
}
