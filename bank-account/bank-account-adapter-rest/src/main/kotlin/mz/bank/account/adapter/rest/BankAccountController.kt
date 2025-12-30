package mz.bank.account.adapter.rest

import mz.bank.account.application.BankAccountCommandHandler
import mz.bank.account.application.BankAccountRepository
import mz.bank.account.domain.BankAccountCommand
import mz.bank.account.domain.Email
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

/**
 * REST controller for BankAccount operations.
 * Provides endpoints for all BankAccount commands.
 */
@RestController
@RequestMapping("/api/v1/accounts")
class BankAccountController(
    private val commandHandler: BankAccountCommandHandler,
    private val bankAccountRepository: BankAccountRepository,
) {
    /**
     * Get account by ID.
     */
    @GetMapping("/{accountId}")
    suspend fun getAccount(
        @PathVariable accountId: String,
    ): ResponseEntity<BankAccountResponse> {
        val account =
            bankAccountRepository.findById(AggregateId(accountId))
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(account.toResponse())
    }

    /**
     * Create a new bank account.
     */
    @PostMapping
    suspend fun createAccount(
        @RequestBody request: CreateAccountRequest,
    ): ResponseEntity<BankAccountResponse> {
        val command =
            BankAccountCommand.CreateAccount(
                email = Email(request.email),
                initialBalance = request.initialBalance,
            )
        return commandHandler.handle(command)?.let {
            ResponseEntity.status(HttpStatus.CREATED).body(it.toResponse())
        } ?: ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
    }

    /**
     * Deposit money into an account.
     */
    @PostMapping("/{accountId}/deposit")
    suspend fun depositMoney(
        @PathVariable accountId: String,
        @RequestBody request: DepositMoneyRequest,
    ): ResponseEntity<BankAccountResponse> {
        val command =
            BankAccountCommand.DepositMoney(
                aggregateId = AggregateId(accountId),
                amount = request.amount,
            )
        return commandHandler.handle(command)?.let {
            ResponseEntity.ok(it.toResponse())
        } ?: ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
    }

    /**
     * Withdraw money from an account.
     */
    @PostMapping("/{accountId}/withdraw")
    suspend fun withdrawMoney(
        @PathVariable accountId: String,
        @RequestBody request: WithdrawMoneyRequest,
    ): ResponseEntity<BankAccountResponse> {
        val command =
            BankAccountCommand.WithdrawMoney(
                aggregateId = AggregateId(accountId),
                amount = request.amount,
            )
        return commandHandler.handle(command)?.let {
            ResponseEntity.ok(it.toResponse())
        } ?: ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
    }

    /**
     * Withdraw money for a transfer transaction.
     */
    @PostMapping("/{accountId}/transfer/withdraw")
    suspend fun withdrawForTransfer(
        @PathVariable accountId: String,
        @RequestBody request: TransferWithdrawRequest,
    ): ResponseEntity<BankAccountResponse> {
        val command =
            BankAccountCommand.WithdrawForTransfer(
                aggregateId = AggregateId(accountId),
                transactionId = request.transactionId,
                amount = request.amount,
            )
        return commandHandler.handle(command)?.let {
            ResponseEntity.ok(it.toResponse())
        } ?: ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
    }

    /**
     * Deposit money from a transfer transaction.
     */
    @PostMapping("/{accountId}/transfer/deposit")
    suspend fun depositFromTransfer(
        @PathVariable accountId: String,
        @RequestBody request: TransferDepositRequest,
    ): ResponseEntity<BankAccountResponse> {
        val command =
            BankAccountCommand.DepositFromTransfer(
                aggregateId = AggregateId(accountId),
                transactionId = request.transactionId,
                amount = request.amount,
            )
        return commandHandler.handle(command)?.let {
            ResponseEntity.ok(it.toResponse())
        } ?: ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
    }

    /**
     * Finish a transaction.
     */
    @PostMapping("/{accountId}/transfer/finish")
    suspend fun finishTransaction(
        @PathVariable accountId: String,
        @RequestBody request: FinishTransactionRequest,
    ): ResponseEntity<BankAccountResponse> {
        val command =
            BankAccountCommand.FinishTransaction(
                aggregateId = AggregateId(accountId),
                transactionId = request.transactionId,
            )
        return commandHandler.handle(command)?.let {
            ResponseEntity.ok(it.toResponse())
        } ?: ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
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
