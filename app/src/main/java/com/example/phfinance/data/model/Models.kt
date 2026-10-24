package com.example.phfinance.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- ENUMS ---

enum class ClientType(val label: String) {
    PROSPECTO("Prospecto"),
    ATIVO("Ativo"),
    PAUSADO("Pausado"),
    INATIVO("Inativo")
}

enum class BillingPeriod(val label: String, val months: Int) {
    MENSAL("Mensal", 1),
    TRIMESTRAL("Trimestral", 3),
    SEMESTRAL("Semestral", 6),
    ANUAL("Anual", 12),
    AVULSO("Avulso/Sessão", 0)
}

enum class ServiceCategory(val label: String) {
    CONSULTORIA_ONLINE("Consultoria Online"),
    SERVICOS_COMBINADOS("Serviços Combinados"),
    AVALIACAO_FISICA("Avaliação Física"),
    TREINO_PRESENCIAL_SESSAO("Treino Presencial (Sessão)"),
    PACOTE_SESSOES("Pacote de Sessões"),
    OUTRO("Serviço Personalizado")
}

enum class ContractStatus(val label: String) {
    ATIVO("Ativo"),
    PAUSADO("Pausado"),
    ENCERRADO("Encerrado"),
    CANCELADO("Cancelado")
}

enum class ReceivableStatus(val label: String) {
    PENDENTE("Pendente"),
    PARCIALMENTE_PAGA("Parcialmente Paga"),
    QUITADA("Quitada"),
    VENCIDA("Vencida"),
    CANCELADA("Cancelada"),
    ESTORNADA("Estornada")
}

enum class PaymentMethod(val label: String) {
    PIX("Pix"),
    DINHEIRO("Dinheiro"),
    TRANSFERENCIA("Transferência Bancária"),
    CARTAO_DEBITO("Cartão de Débito"),
    CARTAO_CREDITO("Cartão de Crédito"),
    BOLETO("Boleto"),
    OUTRO("Outro")
}

enum class ExpenseStatus(val label: String) {
    PENDENTE("Pendente"),
    PAGO("Pago"),
    CANCELADO("Cancelado")
}

enum class ExpenseType(val label: String) {
    DESPESA_OPERACIONAL("Despesa Operacional"),
    PRO_LABORE("Pró-Labore (Remuneração)"),
    DISTRIBUICAO_LUCROS("Distribuição de Lucros"),
    DESPESA_PESSOAL("Despesa Pessoal")
}

enum class AccountType(val label: String) {
    BANCO_EMPRESARIAL("Banco Empresarial"),
    BANCO_PESSOAL("Banco Pessoal (Uso Negócio)"),
    DINHEIRO_ESPECIE("Dinheiro em Espécie"),
    CARTEIRA_DIGITAL("Carteira Digital"),
    OUTRA("Outra")
}

enum class MovementType(val label: String) {
    ENTRADA("Entrada"),
    SAIDA("Saída"),
    TRANSFERENCIA("Transferência")
}

enum class WhatsappTemplateType(val title: String, val defaultText: String) {
    LEMBRETE_ANTES("Lembrete Antes do Vencimento", "Olá {nome}! Tudo bem? Lembrando que sua mensalidade no valor de {valor} vence no dia {vencimento}. Qualquer dúvida estou à disposição! Att, PH Personal."),
    VENCENDO_HOJE("Vencendo Hoje", "Olá {nome}! Sua mensalidade no valor de {valor} vence hoje ({vencimento}). Chave Pix para pagamento: {pix}. Abraços!"),
    ATRASADO("Mensalidade em Atraso", "Olá {nome}! Identificamos que a mensalidade de {valor} referente a {competencia} venceu em {vencimento} e encontra-se pendente. Entre em contato para regularizar."),
    CONFIRMACAO("Confirmação de Pagamento", "Olá {nome}! Confirmamos o recebimento do pagamento de {valor} referente a {competencia}. Obrigado pela parceria! PH Personal."),
    RENOVACAO("Renovação de Contrato", "Olá {nome}! Seu contrato referente ao serviço {servico} está próximo do fim. Vamos renovar para manter seu foco nos treinos?"),
    AGRADECIMENTO("Agradecimento", "Olá {nome}! Obrigado por confiar no trabalho da PH Personal. Tamo junto!"),
    REAJUSTE("Aviso de Reajuste", "Olá {nome}! Comunicamos que a partir do próximo ciclo haverá reajuste no valor do serviço {servico}. Novo valor: {valor}.")
}

// --- MONETARY & DATE HELPERS ---

object CurrencyUtils {
    private val localeBR = Locale("pt", "BR")
    private val currencyFormat = NumberFormat.getCurrencyInstance(localeBR)

    fun formatCents(cents: Long): String {
        val bigDecimal = BigDecimal(cents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        return currencyFormat.format(bigDecimal)
    }

    /**
     * Parses formatted user input into exact integer centavos (Long).
     * Supports:
     * - "R$ 1.234,56" -> 123456L
     * - "150,50" -> 15050L
     * - "0,99" -> 99L
     * - "1234.56" -> 123456L
     * - "200" -> 20000L
     */
    fun parseToCents(amountText: String): Long {
        if (amountText.isBlank()) return 0L
        val clean = amountText
            .replace("R$", "", ignoreCase = true)
            .replace("\u00A0", "")
            .trim()

        if (clean.isBlank()) return 0L

        return try {
            val sanitized = if (clean.contains(",")) {
                clean.replace(".", "").replace(",", ".")
            } else {
                clean
            }
            val bigDecimal = BigDecimal(sanitized).setScale(2, RoundingMode.HALF_UP)
            bigDecimal.multiply(BigDecimal(100)).longValueExact()
        } catch (e: Exception) {
            0L
        }
    }

    fun parseToCentsStrict(amountText: String): Long {
        if (amountText.isBlank()) throw IllegalArgumentException("Valor monetário não pode ser vazio.")
        if (amountText.contains("-")) throw IllegalArgumentException("Valores monetários negativos não são permitidos.")

        val clean = amountText
            .replace("R$", "", ignoreCase = true)
            .replace("\u00A0", "")
            .trim()

        if (clean.isBlank()) throw IllegalArgumentException("Valor monetário em branco.")

        if (clean.count { it == ',' } > 1) {
            throw IllegalArgumentException("Separadores monetários inválidos.")
        }

        val sanitized = if (clean.contains(",")) {
            clean.replace(".", "").replace(",", ".")
        } else {
            clean
        }

        val bigDecimal = try {
            BigDecimal(sanitized)
        } catch (e: Exception) {
            throw IllegalArgumentException("Entrada monetária inválida: $amountText")
        }

        if (bigDecimal < BigDecimal.ZERO) throw IllegalArgumentException("Valores negativos não são permitidos.")

        return bigDecimal.setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .longValueExact()
    }

    fun centsToBigDecimal(cents: Long): BigDecimal {
        return BigDecimal(cents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    }

    fun bigDecimalToCents(amount: BigDecimal): Long {
        return amount.setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .longValueExact()
    }
}

object DateUtils {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val competenceFormat = SimpleDateFormat("MM/yyyy", Locale("pt", "BR"))

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        return dateFormat.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        return dateTimeFormat.format(Date(timestamp))
    }

    fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun formatCompetence(timestamp: Long): String {
        return competenceFormat.format(Date(timestamp))
    }

    fun getCurrentCompetence(): String {
        return competenceFormat.format(Date())
    }

    fun getAdjustedDueDate(year: Int, monthZeroIndexed: Int, preferredDay: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, monthZeroIndexed)
        val maxDayInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val actualDay = if (preferredDay > maxDayInMonth) maxDayInMonth else preferredDay
        calendar.set(Calendar.DAY_OF_MONTH, actualDay)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
