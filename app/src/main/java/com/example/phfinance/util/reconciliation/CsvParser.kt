package com.example.phfinance.util.reconciliation

import com.example.phfinance.data.model.CsvConfig
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

object CsvParser {

    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    private const val MAX_TRANSACTIONS = 5000

    fun parse(
        inputStream: InputStream,
        accountId: String,
        fileName: String,
        fileSizeBytes: Long,
        config: CsvConfig? = null
    ): ParsedBankStatement {
        require(fileSizeBytes <= MAX_FILE_SIZE_BYTES) {
            "O arquivo excede o limite de tamanho seguro (máximo 10MB)."
        }

        val rawBytes = inputStream.readBytes()
        val content = detectCharsetAndDecode(rawBytes)
        return parseString(content, accountId, fileName, fileSizeBytes, config)
    }

    fun parseString(
        content: String,
        accountId: String,
        fileName: String,
        fileSizeBytes: Long = content.toByteArray().size.toLong(),
        userConfig: CsvConfig? = null
    ): ParsedBankStatement {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            throw IllegalArgumentException("Arquivo CSV vazio ou sem linhas legíveis.")
        }

        // Auto-detect delimiter if not forced
        val delimiter = userConfig?.delimiter ?: detectDelimiter(lines.take(5))
        val resolvedConfig = if (userConfig != null) {
            if (userConfig.hasHeader && userConfig.fitIdCol < 0) {
                val detected = autoDetectColumns(lines.first(), delimiter)
                userConfig.copy(fitIdCol = detected.fitIdCol)
            } else {
                userConfig
            }
        } else {
            autoDetectColumns(lines.first(), delimiter)
        }

        val transactions = mutableListOf<ParsedBankTransaction>()
        var minDate = Long.MAX_VALUE
        var maxDate = 0L

        val startIndex = if (resolvedConfig.hasHeader) 1 else 0

        for (i in startIndex until lines.size) {
            if (transactions.size >= MAX_TRANSACTIONS) break
            val line = lines[i].trim()
            if (line.isBlank()) continue

            val columns = parseCsvLine(line, delimiter)
            if (columns.size <= maxOf(resolvedConfig.dateCol, resolvedConfig.descCol, resolvedConfig.amountCol)) {
                continue
            }

            val dateStr = columns.getOrNull(resolvedConfig.dateCol)?.trim() ?: ""
            val descStr = columns.getOrNull(resolvedConfig.descCol)?.trim() ?: ""
            val amountStr = columns.getOrNull(resolvedConfig.amountCol)?.trim() ?: ""
            val fitId = if (resolvedConfig.fitIdCol >= 0 && resolvedConfig.fitIdCol < columns.size) {
                columns[resolvedConfig.fitIdCol].trim().ifBlank { UUID.randomUUID().toString() }
            } else {
                UUID.randomUUID().toString()
            }

            val parsedDate = parseDate(dateStr, resolvedConfig.dateFormat) ?: continue
            val parsedAmount = parseAmount(amountStr, resolvedConfig.decimalSeparator) ?: continue

            if (parsedDate < minDate) minDate = parsedDate
            if (parsedDate > maxDate) maxDate = parsedDate

            val movementType = if (parsedAmount >= 0) MovementType.ENTRADA else MovementType.SAIDA

            transactions.add(
                ParsedBankTransaction(
                    fitId = fitId,
                    date = parsedDate,
                    amountCents = parsedAmount,
                    movementType = movementType,
                    memo = descStr.ifBlank { "Lançamento em Conta" },
                    payee = ""
                )
            )
        }

        if (transactions.isEmpty()) {
            throw IllegalArgumentException("Nenhuma transação válida identificada com o padrão CSV configurado.")
        }

        val startDate = if (minDate == Long.MAX_VALUE) System.currentTimeMillis() else minDate
        val endDate = if (maxDate == 0L) System.currentTimeMillis() else maxDate

        return ParsedBankStatement(
            accountId = accountId,
            fileName = fileName,
            fileType = "CSV",
            fileSizeBytes = fileSizeBytes,
            startDate = startDate,
            endDate = endDate,
            transactions = transactions
        )
    }

    private fun detectDelimiter(sampleLines: List<String>): Char {
        var semicolonCount = 0
        var commaCount = 0
        for (line in sampleLines) {
            semicolonCount += line.count { it == ';' }
            commaCount += line.count { it == ',' }
        }
        return if (semicolonCount >= commaCount) ';' else ','
    }

    private fun autoDetectColumns(headerLine: String, delimiter: Char): CsvConfig {
        val cols = parseCsvLine(headerLine, delimiter).map { it.lowercase().trim() }
        var dateCol = 0
        var descCol = 1
        var amountCol = 2
        var fitIdCol = -1

        for ((idx, col) in cols.withIndex()) {
            when {
                col.contains("data") || col.contains("dt") || col.contains("date") -> dateCol = idx
                col.contains("desc") || col.contains("hist") || col.contains("memo") || col.contains("detalhe") -> descCol = idx
                col.contains("valor") || col.contains("amount") || col.contains("val") || col.contains("saldo") -> amountCol = idx
                col.contains("doc") || col.contains("id") || col.contains("fitid") || col.contains("ref") -> fitIdCol = idx
            }
        }

        val decimalSep = if (delimiter == ';') ',' else '.'

        return CsvConfig(
            delimiter = delimiter,
            decimalSeparator = decimalSep,
            dateCol = dateCol,
            descCol = descCol,
            amountCol = amountCol,
            fitIdCol = fitIdCol,
            hasHeader = true
        )
    }

    fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString().trim().removeSurrounding("\""))
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim().removeSurrounding("\""))
        return result
    }

    fun parseDate(dateStr: String, primaryFormat: String = "dd/MM/yyyy"): Long? {
        val clean = dateStr.trim().removeSurrounding("\"")
        val formats = listOf(
            primaryFormat,
            "dd/MM/yyyy",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "dd/MM/yy"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale("pt", "BR"))
                sdf.isLenient = false
                val parsed = sdf.parse(clean)
                if (parsed != null) return parsed.time
            } catch (_: Exception) { }
        }
        return null
    }

    fun parseAmount(amountStr: String, decimalSep: Char = ','): Long? {
        val clean = amountStr.trim()
            .replace("R$", "", ignoreCase = true)
            .replace("\u00A0", "")
            .replace(" ", "")
            .removeSurrounding("\"")

        if (clean.isBlank()) return null

        val isNegative = clean.startsWith("-") || clean.contains(" D") || clean.endsWith("-")
        val digitsOnly = clean.replace("-", "").replace("+", "").replace(" C", "").replace(" D", "").trim()

        return try {
            val normalized = if (decimalSep == ',') {
                digitsOnly.replace(".", "").replace(",", ".")
            } else {
                digitsOnly.replace(",", "")
            }
            val bd = BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP)
            val cents = bd.multiply(BigDecimal(100)).longValueExact()
            if (isNegative) -cents else cents
        } catch (_: Exception) {
            null
        }
    }

    private fun detectCharsetAndDecode(bytes: ByteArray): String {
        return try {
            val utf8 = String(bytes, Charsets.UTF_8)
            if (utf8.contains("\uFFFD")) {
                String(bytes, Charset.forName("ISO-8859-1"))
            } else {
                utf8
            }
        } catch (_: Exception) {
            String(bytes, Charset.forName("ISO-8859-1"))
        }
    }
}
