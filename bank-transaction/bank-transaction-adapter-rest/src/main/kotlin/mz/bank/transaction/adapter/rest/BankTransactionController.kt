package mz.bank.transaction.adapter.rest

import mz.bank.transaction.application.BankTransactionCommandHandler
import mz.bank.transaction.application.BankTransactionRepository
import mz.bank.transaction.domain.BankTransactionCommand
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
 * REST controller for BankTransaction operations.
 * Provides endpoints for all BankTransaction commands.
 */
@RestController
@RequestMapping("/api/v1/transactions")
class BankTransactionController(
    private val commandHandler: BankTransactionCommandHandler,
    private val bankTransactionRepository: BankTransactionRepository,
) {
    /**
     * Get transaction by ID.
     */
    @GetMapping("/{transactionId}")
    suspend fun getBankTransaction(
        @PathVariable transactionId: String,
    ): ResponseEntity<BankTransactionResponse> {
        val bankTransaction =
            bankTransactionRepository.findById(AggregateId(transactionId))
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(bankTransaction.toResponse())
    }

    /**
     * Create a new transaction.
     */
    @PostMapping
    suspend fun createBankTransaction(
        @RequestBody request: CreateBankTransactionRequest,
    ): ResponseEntity<BankTransactionResponse> {
        val command =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = UUID.randomUUID().toString(),
                fromAccountId = AggregateId(request.fromAccountId),
                toAccountId = AggregateId(request.toAccountId),
                amount = request.amount,
            )
        val bankTransaction = commandHandler.handle(command)
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
