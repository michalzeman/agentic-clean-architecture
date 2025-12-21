package mz.bank.transaction.adapter.rest

import mz.bank.transaction.application.TransactionCommandHandler
import mz.bank.transaction.application.TransactionRepository
import mz.bank.transaction.domain.TransactionCommand
import mz.shared.domain.AggregateId
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
 * REST controller for Transaction operations.
 * Provides endpoints for all Transaction commands.
 */
@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController(
    private val commandHandler: TransactionCommandHandler,
    private val transactionRepository: TransactionRepository,
) {
    /**
     * Get transaction by ID.
     */
    @GetMapping("/{transactionId}")
    suspend fun getTransaction(
        @PathVariable transactionId: String,
    ): ResponseEntity<TransactionResponse> {
        val transaction =
            transactionRepository.findById(AggregateId(transactionId))
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(transaction.toResponse())
    }

    /**
     * Create a new transaction.
     */
    @PostMapping
    suspend fun createTransaction(
        @RequestBody request: CreateTransactionRequest,
    ): ResponseEntity<TransactionResponse> {
        val command =
            TransactionCommand.CreateTransaction(
                correlationId = UUID.randomUUID().toString(),
                fromAccountId = AggregateId(request.fromAccountId),
                toAccountId = AggregateId(request.toAccountId),
                amount = request.amount,
            )
        val transaction = commandHandler.handle(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction.toResponse())
    }

    /**
     * Validate money withdrawal for a transaction.
     */
    @PostMapping("/{transactionId}/validate-withdraw")
    suspend fun validateWithdraw(
        @PathVariable transactionId: String,
        @RequestBody request: ValidateWithdrawRequest,
    ): ResponseEntity<TransactionResponse> {
        val command =
            TransactionCommand.ValidateTransactionMoneyWithdraw(
                aggregateId = AggregateId(transactionId),
                correlationId = request.transactionId,
            )
        val transaction = commandHandler.handle(command)
        return ResponseEntity.ok(transaction.toResponse())
    }

    /**
     * Validate money deposit for a transaction.
     */
    @PostMapping("/{transactionId}/validate-deposit")
    suspend fun validateDeposit(
        @PathVariable transactionId: String,
        @RequestBody request: ValidateDepositRequest,
    ): ResponseEntity<TransactionResponse> {
        val command =
            TransactionCommand.ValidateTransactionMoneyDeposit(
                aggregateId = AggregateId(transactionId),
                correlationId = request.transactionId,
            )
        val transaction = commandHandler.handle(command)
        return ResponseEntity.ok(transaction.toResponse())
    }

    /**
     * Finish a transaction.
     */
    @PostMapping("/{transactionId}/finish")
    suspend fun finishTransaction(
        @PathVariable transactionId: String,
        @RequestBody request: FinishTransactionRequest,
    ): ResponseEntity<TransactionResponse> {
        val command =
            TransactionCommand.FinishTransaction(
                aggregateId = AggregateId(transactionId),
                correlationId = UUID.randomUUID().toString(),
                fromAccountId = AggregateId(request.fromAccountId),
                toAccountId = AggregateId(request.toAccountId),
            )
        val transaction = commandHandler.handle(command)
        return ResponseEntity.ok(transaction.toResponse())
    }

    /**
     * Cancel a transaction.
     */
    @PostMapping("/{transactionId}/cancel")
    suspend fun cancelTransaction(
        @PathVariable transactionId: String,
        @RequestBody request: CancelTransactionRequest,
    ): ResponseEntity<TransactionResponse> {
        val command =
            TransactionCommand.CancelTransaction(
                aggregateId = AggregateId(transactionId),
                correlationId = UUID.randomUUID().toString(),
                fromAccountId = AggregateId(request.fromAccountId),
                toAccountId = AggregateId(request.toAccountId),
                amount = request.amount,
            )
        val transaction = commandHandler.handle(command)
        return ResponseEntity.ok(transaction.toResponse())
    }

    /**
     * Handle IllegalArgumentException errors.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
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
        val error =
            ErrorResponse(
                error = "Conflict",
                message = ex.message ?: "Operation not allowed in current state",
            )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error)
    }
}
