package mz.bank.transaction.adapter.rest

import mz.bank.transaction.application.transaction.BankTransactionCommandHandler
import mz.bank.transaction.application.transaction.BankTransactionRepository
import mz.bank.transaction.domain.BankTransactionCommand
import mz.shared.domain.AggregateId
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for BankTransaction operations.
 * Provides endpoints for all BankTransaction commands.
 */
@RestController
@RequestMapping("/api/v1/transactions")
class BankTransactionController(
    private val commandHandler: BankTransactionCommandHandler,
    private val bankTransactionRepository: BankTransactionRepository,
) {
    private val logger = LoggerFactory.getLogger(BankTransactionController::class.java)

    /**
     * Get transaction by ID.
     */
    @GetMapping("/{transactionId}")
    suspend fun getBankTransaction(
        @PathVariable transactionId: String,
    ): ResponseEntity<BankTransactionResponse> {
        logger.info("GET /api/v1/transactions/{} called", transactionId)
        val bankTransaction =
            bankTransactionRepository.findById(AggregateId(transactionId))
                ?: return ResponseEntity.notFound().build()
        logger.info("GET /api/v1/transactions/{} - transaction found", transactionId)
        return ResponseEntity.ok(bankTransaction.toResponse())
    }

    /**
     * Create a new transaction.
     */
    @PostMapping
    suspend fun createBankTransaction(
        @RequestBody request: CreateBankTransactionRequest,
    ): ResponseEntity<BankTransactionResponse> {
        logger.info("POST /api/v1/transactions called with request: {}", request)
        val command =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = UUID.randomUUID().toString(),
                fromAccountId = AggregateId(request.fromAccountId),
                toAccountId = AggregateId(request.toAccountId),
                amount = request.amount,
            )
        logger.info("POST /api/v1/transactions - command created: {}", command)
        val bankTransaction = commandHandler.handle(command)
        logger.info("POST /api/v1/transactions - transaction created: {}", bankTransaction.aggregateId.value)
        return ResponseEntity.status(HttpStatus.CREATED).body(bankTransaction.toResponse())
    }

    /**
     * Validate money withdrawal for a transaction.
     */
    @PostMapping("/{transactionId}/validate-withdraw")
    suspend fun validateWithdraw(
        @PathVariable transactionId: String,
        @RequestBody request: ValidateWithdrawRequest,
    ): ResponseEntity<BankTransactionResponse> {
        val command =
            BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
                aggregateId = AggregateId(transactionId),
                accountId = AggregateId(request.accountId),
                correlationId = request.transactionId,
            )
        val bankTransaction = commandHandler.handle(command)
        return ResponseEntity.ok(bankTransaction.toResponse())
    }

    /**
     * Validate money deposit for a transaction.
     */
    @PostMapping("/{transactionId}/validate-deposit")
    suspend fun validateDeposit(
        @PathVariable transactionId: String,
        @RequestBody request: ValidateDepositRequest,
    ): ResponseEntity<BankTransactionResponse> {
        val command =
            BankTransactionCommand.ValidateBankTransactionMoneyDeposit(
                aggregateId = AggregateId(transactionId),
                accountId = AggregateId(request.accountId),
                correlationId = request.transactionId,
            )
        val bankTransaction = commandHandler.handle(command)
        return ResponseEntity.ok(bankTransaction.toResponse())
    }

    /**
     * Finish a transaction.
     */
    @PostMapping("/{transactionId}/finish")
    suspend fun finishBankTransaction(
        @PathVariable transactionId: String,
        @RequestBody request: FinishBankTransactionRequest,
    ): ResponseEntity<BankTransactionResponse> {
        val command =
            BankTransactionCommand.FinishBankTransaction(
                aggregateId = AggregateId(transactionId),
                correlationId = UUID.randomUUID().toString(),
                fromAccountId = AggregateId(request.fromAccountId),
                toAccountId = AggregateId(request.toAccountId),
            )
        val bankTransaction = commandHandler.handle(command)
        return ResponseEntity.ok(bankTransaction.toResponse())
    }

    /**
     * Cancel a transaction.
     */
    @PostMapping("/{transactionId}/cancel")
    suspend fun cancelBankTransaction(
        @PathVariable transactionId: String,
        @RequestBody request: CancelBankTransactionRequest,
    ): ResponseEntity<BankTransactionResponse> {
        val command =
            BankTransactionCommand.CancelBankTransaction(
                aggregateId = AggregateId(transactionId),
                correlationId = UUID.randomUUID().toString(),
                fromAccountId = AggregateId(request.fromAccountId),
                toAccountId = AggregateId(request.toAccountId),
                amount = request.amount,
            )
        val bankTransaction = commandHandler.handle(command)
        return ResponseEntity.ok(bankTransaction.toResponse())
    }

    /**
     * Handle IllegalArgumentException errors.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.error("IllegalArgumentException caught in controller: {}", ex.message, ex)
        val error =
            ErrorResponse(
                error = "Bad Request",
                message = ex.message ?: "Invalid request",
            )
        return ResponseEntity.badRequest().body(error)
    }

    /**
     * Handle IllegalStateException errors.
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        logger.error("IllegalStateException caught in controller: {}", ex.message, ex)
        val error =
            ErrorResponse(
                error = "Conflict",
                message = ex.message ?: "Operation not allowed in current state",
            )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error)
    }
}
