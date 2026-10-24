package com.example.phfinance

import com.example.phfinance.data.local.AccountMovementEntity
import com.example.phfinance.data.local.BankImportEntity
import com.example.phfinance.data.local.BankTransactionEntity
import com.example.phfinance.data.local.ClientEntity
import com.example.phfinance.data.local.ExpenseEntity
import com.example.phfinance.data.local.FinancialAccountEntity
import com.example.phfinance.data.local.MonthlyClosureEntity
import com.example.phfinance.data.local.PaymentEntity
import com.example.phfinance.data.local.ReceivableEntity
import com.example.phfinance.data.local.ReconciliationAuditEntity
import com.example.phfinance.data.local.ReconciliationEntity
import com.example.phfinance.data.model.AccountType
import com.example.phfinance.data.model.BankTransactionStatus
import com.example.phfinance.data.model.CsvConfig
import com.example.phfinance.data.model.CurrencyUtils
import com.example.phfinance.data.model.DateUtils
import com.example.phfinance.data.model.ExpenseStatus
import com.example.phfinance.data.model.MatchConfidence
import com.example.phfinance.data.model.MovementType
import com.example.phfinance.data.model.ParsedBankStatement
import com.example.phfinance.data.model.ParsedBankTransaction
import com.example.phfinance.data.model.PaymentMethod
import com.example.phfinance.data.model.ReceivableStatus
import com.example.phfinance.data.model.ReconciliationType
import com.example.phfinance.util.BackupManager
import com.example.phfinance.util.BackupPayload
import com.example.phfinance.util.reconciliation.CsvParser
import com.example.phfinance.util.reconciliation.OfxParser
import com.example.phfinance.util.reconciliation.ReconciliationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class BankReconciliationTest {

    // 1. OFX PARSING VALIDATION
    @Test
    fun testOfxParsing_validBrazilianStatement() {
        val ofxContent = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            SECURITY:NONE
            ENCODING:USASCII
            CHARSET:1252
            <OFX>
            <BANKMSGSRSV1>
            <STMTTRNRS>
            <STMTRS>
            <CURDEF>BRL
            <BANKACCTFROM>
            <BANKID>260
            <ACCTID>998877
            </BANKACCTFROM>
            <BANKTRANLIST>
            <DTSTART>20261001000000
            <DTEND>20261031235959
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20261010120000
            <TRNAMT>250.00
            <FITID>FIT_TEST_001
            <MEMO>PIX RECEBIDO - CARLOS SILVA
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20261012150000
            <TRNAMT>-89.90
            <FITID>FIT_TEST_002
            <MEMO>ASSINATURA SOFTWARE
            </STMTTRN>
            </BANKTRANLIST>
            </STMTRS>
            </STMTTRNRS>
            </BANKMSGSRSV1>
            </OFX>
        """.trimIndent()

        val parsed = OfxParser.parseString(ofxContent, "acc_main", "extrato_itau.ofx")
        assertEquals("OFX", parsed.fileType)
        assertEquals(2, parsed.transactions.size)

        val txCredit = parsed.transactions[0]
        assertEquals("FIT_TEST_001", txCredit.fitId)
        assertEquals(25000L, txCredit.amountCents)
        assertEquals(MovementType.ENTRADA, txCredit.movementType)
        assertEquals("PIX RECEBIDO - CARLOS SILVA", txCredit.memo)

        val txDebit = parsed.transactions[1]
        assertEquals("FIT_TEST_002", txDebit.fitId)
        assertEquals(-8990L, txDebit.amountCents)
        assertEquals(MovementType.SAIDA, txDebit.movementType)
    }

    // 2. OFX REJECTION OF MALFORMED CONTENT
    @Test
    fun testOfxParsing_rejectsMalformedContent() {
        val invalidContent = "Este é um arquivo de texto comum que não possui tags OFX."
        try {
            OfxParser.parseString(invalidContent, "acc_main", "invalido.txt")
            fail("Deveria lançar IllegalArgumentException para arquivo sem tags OFX.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("OFX"))
        }
    }

    // 3. CSV PARSING WITH BRAZILIAN DECIMAL COMMA AND SEMICOLONS
    @Test
    fun testCsvParsing_semicolonWithBrazilianCommaFormat() {
        val csvContent = """
            Data;Historico;Valor;Documento
            10/10/2026;Pix Recebido João Silva;1.250,50;DOC123
            15/10/2026;Pgto Equipamento Academia;-450,00;DOC124
        """.trimIndent()

        val parsed = CsvParser.parseString(
            content = csvContent,
            accountId = "acc_main",
            fileName = "extrato.csv",
            userConfig = CsvConfig(delimiter = ';', decimalSeparator = ',', hasHeader = true)
        )

        assertEquals(2, parsed.transactions.size)
        val credit = parsed.transactions[0]
        assertEquals(125050L, credit.amountCents)
        assertEquals(MovementType.ENTRADA, credit.movementType)
        assertEquals("Pix Recebido João Silva", credit.memo)
        assertEquals("DOC123", credit.fitId)

        val debit = parsed.transactions[1]
        assertEquals(-45000L, debit.amountCents)
        assertEquals(MovementType.SAIDA, debit.movementType)
    }

    // 4. DUPLICATE DETECTION AND IDEMPOTENCY
    @Test
    fun testDuplicateDetection_rawHashAndFitId() {
        val accountId = "acc_main"
        val date = DateUtils.parseDate("15/10/2026")
        val amount = 20000L
        val memo1 = "PIX RECEBIDO JOAO"
        val memo2 = "pix  recebido  joao "

        val hash1 = ReconciliationEngine.computeRawHash(accountId, date, amount, memo1)
        val hash2 = ReconciliationEngine.computeRawHash(accountId, date, amount, memo2)

        // Same normalized transaction produces identical rawHash
        assertEquals(hash1, hash2)

        // Different amount produces distinct rawHash
        val hashDifferentAmount = ReconciliationEngine.computeRawHash(accountId, date, 25000L, memo1)
        assertTrue(hash1 != hashDifferentAmount)
    }

    // 5. DETERMINISTIC RECONCILIATION ENGINE SCORING
    @Test
    fun testReconciliationEngine_strongAndPossibleMatches() {
        val dateNow = DateUtils.parseDate("15/10/2026")
        val client = ClientEntity(
            id = "cli_1",
            fullName = "Juliana Mendes",
            nickname = "Ju",
            phone = "11999999999",
            whatsapp = "11999999999",
            email = "ju@gmail.com"
        )
        val clients = listOf(client)

        // Pending Receivable
        val receivable = ReceivableEntity(
            id = "rec_1",
            clientId = client.id,
            competence = "10/2026",
            dueDate = dateNow,
            originalValueCents = 20000L,
            netValueCents = 20000L,
            receivedValueCents = 0L,
            status = ReceivableStatus.PENDENTE
        )

        // 5.1 Strong Match: exact amount + client name in memo
        val txStrong = BankTransactionEntity(
            id = "tx_1",
            importId = "imp_1",
            accountId = "acc_main",
            fitId = "FIT1",
            transactionDate = dateNow,
            amountCents = 20000L,
            movementType = MovementType.ENTRADA,
            memo = "PIX RECEBIDO DE JULIANA MENDES",
            status = BankTransactionStatus.PENDENTE,
            rawHash = "hash1"
        )

        val suggestionStrong = ReconciliationEngine.findBestMatch(
            tx = txStrong,
            existingPayments = emptyList(),
            receivables = listOf(receivable),
            expenses = emptyList(),
            clients = clients
        )

        assertEquals(MatchConfidence.FORTE, suggestionStrong.confidence)
        assertEquals(receivable.id, suggestionStrong.matchedReceivableId)
        assertTrue(suggestionStrong.justification.contains("Juliana Mendes"))

        // 5.2 Possible Match: amount match without client name in memo
        val txPossible = BankTransactionEntity(
            id = "tx_2",
            importId = "imp_1",
            accountId = "acc_main",
            fitId = "FIT2",
            transactionDate = dateNow,
            amountCents = 20000L,
            movementType = MovementType.ENTRADA,
            memo = "TRANSF BANCARIA RECEBIDA 8872",
            status = BankTransactionStatus.PENDENTE,
            rawHash = "hash2"
        )

        val suggestionPossible = ReconciliationEngine.findBestMatch(
            tx = txPossible,
            existingPayments = emptyList(),
            receivables = listOf(receivable),
            expenses = emptyList(),
            clients = clients
        )

        assertEquals(MatchConfidence.POSSIVEL, suggestionPossible.confidence)

        // 5.3 Insufficient Match: completely different amount
        val txInsufficient = BankTransactionEntity(
            id = "tx_3",
            importId = "imp_1",
            accountId = "acc_main",
            fitId = "FIT3",
            transactionDate = dateNow,
            amountCents = 99999L,
            movementType = MovementType.ENTRADA,
            memo = "CREDITO INDEFINIDO",
            status = BankTransactionStatus.PENDENTE,
            rawHash = "hash3"
        )

        val suggestionInsufficient = ReconciliationEngine.findBestMatch(
            tx = txInsufficient,
            existingPayments = emptyList(),
            receivables = listOf(receivable),
            expenses = emptyList(),
            clients = clients
        )

        assertEquals(MatchConfidence.INSUFICIENTE, suggestionInsufficient.confidence)
    }

    // 6. FLUXO A: LINKING TO EXISTING PAYMENT
    @Test
    fun testReconciliationEngine_fluxoA_existingPaymentMatch() {
        val dateNow = DateUtils.parseDate("15/10/2026")
        val client = ClientEntity(
            id = "cli_1",
            fullName = "Pedro Santos",
            nickname = "Pedro",
            phone = "11999999999",
            whatsapp = "11999999999",
            email = "pedro@gmail.com"
        )

        val payment = PaymentEntity(
            id = "pay_1",
            receivableId = "rec_1",
            clientId = client.id,
            grossAmountCents = 30000L,
            feeCents = 0L,
            netAmountCents = 30000L,
            paymentDate = dateNow,
            destinationAccountId = "acc_main",
            paymentMethod = PaymentMethod.PIX
        )

        val tx = BankTransactionEntity(
            id = "tx_pay",
            importId = "imp_1",
            accountId = "acc_main",
            fitId = "FIT_PAY",
            transactionDate = dateNow,
            amountCents = 30000L,
            movementType = MovementType.ENTRADA,
            memo = "PIX RECEBIDO - PEDRO SANTOS",
            status = BankTransactionStatus.PENDENTE,
            rawHash = "hash_pay"
        )

        val suggestion = ReconciliationEngine.findBestMatch(
            tx = tx,
            existingPayments = listOf(payment),
            receivables = emptyList(),
            expenses = emptyList(),
            clients = listOf(client)
        )

        assertEquals(MatchConfidence.FORTE, suggestion.confidence)
        assertEquals(payment.id, suggestion.matchedPaymentId)
        assertTrue(suggestion.justification.contains("Fluxo A"))
    }

    // 7. EXPENSE (DEBIT) MATCHING
    @Test
    fun testReconciliationEngine_expenseOutflowMatch() {
        val dateNow = DateUtils.parseDate("20/10/2026")
        val expense = ExpenseEntity(
            id = "exp_1",
            description = "Google Workspace Consultoria",
            category = "Assinaturas e Aplicativos",
            supplier = "Google Brasil",
            valueCents = 6000L,
            competence = "10/2026",
            dueDate = dateNow,
            status = ExpenseStatus.PENDENTE
        )

        val txDebit = BankTransactionEntity(
            id = "tx_deb",
            importId = "imp_1",
            accountId = "acc_main",
            fitId = "FIT_DEB",
            transactionDate = dateNow,
            amountCents = -6000L,
            movementType = MovementType.SAIDA,
            memo = "DEBITO AUTOMATICO GOOGLE WORKSPACE",
            status = BankTransactionStatus.PENDENTE,
            rawHash = "hash_deb"
        )

        val suggestion = ReconciliationEngine.findBestMatch(
            tx = txDebit,
            existingPayments = emptyList(),
            receivables = emptyList(),
            expenses = listOf(expense),
            clients = emptyList()
        )

        assertEquals(MatchConfidence.FORTE, suggestion.confidence)
        assertEquals(expense.id, suggestion.matchedExpenseId)
        assertTrue(suggestion.justification.contains("Google Workspace"))
    }

    // 8. BACKUP ENCRYPTION AND RESTORATION WITH RECONCILIATION ENTITIES (v4)
    @Test
    fun testBackupPayload_v4_reconciliationTablesIncluded() {
        val testImport = BankImportEntity(
            id = "imp_test",
            accountId = "acc_main",
            fileName = "extrato_outubro.ofx",
            fileType = "OFX",
            fileSizeBytes = 2048L,
            startDate = 1000L,
            endDate = 2000L,
            totalTransactions = 1,
            totalCreditsCents = 25000L,
            totalDebitsCents = 0L
        )

        val testTx = BankTransactionEntity(
            id = "tx_test",
            importId = "imp_test",
            accountId = "acc_main",
            fitId = "FIT_BKP",
            transactionDate = 1500L,
            amountCents = 25000L,
            movementType = MovementType.ENTRADA,
            memo = "PIX DE ALUNO",
            status = BankTransactionStatus.CONCILIADA,
            rawHash = "raw_hash_test"
        )

        val testRec = ReconciliationEntity(
            id = "rec_test",
            bankTransactionId = "tx_test",
            accountId = "acc_main",
            reconciliationType = ReconciliationType.NOVO_RECEBIMENTO,
            paymentId = "pay_test",
            receivableId = "rec_1",
            reconciledAmountCents = 25000L,
            feeCents = 0L,
            grossAmountCents = 25000L,
            reconciledAt = 1600L,
            status = "ATIVA"
        )

        val testAudit = ReconciliationAuditEntity(
            id = "audit_test",
            reconciliationId = "rec_test",
            bankTransactionId = "tx_test",
            actionType = "CRIACAO_NOVO_RECEBIMENTO",
            timestamp = 1600L,
            detailsJson = "Teste de auditoria"
        )

        val payload = BackupPayload(
            version = 4,
            timestamp = System.currentTimeMillis(),
            bankImports = listOf(testImport),
            bankTransactions = listOf(testTx),
            reconciliations = listOf(testRec),
            reconciliationAudits = listOf(testAudit)
        )

        // Encrypt with AES-256-GCM
        val password = "StrongPassword@123"
        val wrapper = BackupManager.encryptPayloadToWrapper(payload, password, version = 4)
        assertEquals(4, wrapper.version)
        assertNotNull(wrapper.cipherTextBase64)
        assertNotNull(wrapper.sha256Checksum)

        // Decrypt and verify integrity
        val wrapperJson = BackupManager.wrapperAdapter.toJson(wrapper)
        val decrypted = BackupManager.decryptWrapperToPayload(wrapperJson, password)

        assertEquals(4, decrypted.version)
        assertEquals(1, decrypted.bankImports.size)
        assertEquals(testImport.fileName, decrypted.bankImports[0].fileName)
        assertEquals(1, decrypted.bankTransactions.size)
        assertEquals(testTx.fitId, decrypted.bankTransactions[0].fitId)
        assertEquals(1, decrypted.reconciliations.size)
        assertEquals(testRec.id, decrypted.reconciliations[0].id)
        assertEquals(1, decrypted.reconciliationAudits.size)
        assertEquals(testAudit.id, decrypted.reconciliationAudits[0].id)
    }

    // 9. BACKWARD COMPATIBILITY: V3 BACKUP PARSING IN V4 ENGINE
    @Test
    fun testBackupBackwardCompatibility_v3PayloadDeserialization() {
        // A V3 JSON missing reconciliation fields should default them to emptyList() cleanly
        val v3Json = """
            {
               "version": 3,
               "timestamp": 1729000000,
               "clients": [],
               "services": [],
               "contracts": [],
               "contractSessions": [],
               "receivables": [],
               "payments": [],
               "expenseCategories": [],
               "expenses": [],
               "accounts": [],
               "accountMovements": [],
               "accountTransfers": [],
               "goals": [],
               "businessSettings": [],
               "notificationLogs": [],
               "monthlyClosures": [],
               "closureHistories": [],
               "messageTemplates": []
            }
        """.trimIndent()

        val parsedPayload = BackupManager.payloadAdapter.fromJson(v3Json)
        assertNotNull(parsedPayload)
        assertEquals(3, parsedPayload!!.version)
        assertTrue(parsedPayload.bankImports.isEmpty())
        assertTrue(parsedPayload.bankTransactions.isEmpty())
        assertTrue(parsedPayload.reconciliations.isEmpty())
        assertTrue(parsedPayload.reconciliationAudits.isEmpty())
    }
}
