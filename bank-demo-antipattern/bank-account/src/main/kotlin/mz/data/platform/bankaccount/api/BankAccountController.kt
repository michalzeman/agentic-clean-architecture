package mz.data.platform.bankaccount.api

import jakarta.validation.Valid
import mz.data.platform.bankaccount.api.dto.AccountResponse
import mz.data.platform.bankaccount.api.dto.CreateAccountRequest
import mz.data.platform.bankaccount.api.dto.DepositRequest
import mz.data.platform.bankaccount.api.dto.TransferRequest
import mz.data.platform.bankaccount.api.dto.WithdrawRequest
import mz.data.platform.bankaccount.service.BankAccountService
import mz.data.platform.bankaccount.service.exceptions.AccountNotFoundException
import mz.data.platform.bankaccount.service.exceptions.InsufficientFundsException
import mz.data.platform.bankaccount.service.exceptions.NegativeBalanceException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/accounts")
class BankAccountController(
    private val bankAccountService: BankAccountService,
) {
    @PostMapping
    fun createAccount(
        @Valid @RequestBody request: CreateAccountRequest,
    ): ResponseEntity<AccountResponse> {
        val account = bankAccountService.createAccount(request.initialBalance)
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account))
    }

    @GetMapping("/{id}")
    fun getAccount(
        @PathVariable id: String,
    ): ResponseEntity<AccountResponse> {
        val account = bankAccountService.getAccount(id)
        return ResponseEntity.ok(AccountResponse.from(account))
    }

    @PostMapping("/{id}/withdraw")
    fun withdraw(
        @PathVariable id: String,
        @Valid @RequestBody request: WithdrawRequest,
    ): ResponseEntity<AccountResponse> {
        val account = bankAccountService.withdraw(id, request.amount)
        return ResponseEntity.ok(AccountResponse.from(account))
    }

    @PostMapping("/{id}/deposit")
    fun deposit(
        @PathVariable id: String,
        @Valid @RequestBody request: DepositRequest,
    ): ResponseEntity<AccountResponse> {
        val account = bankAccountService.deposit(id, request.amount)
        return ResponseEntity.ok(AccountResponse.from(account))
    }

    @PostMapping("/{id}/transfer")
    fun transfer(
        @PathVariable id: String,
        @Valid @RequestBody request: TransferRequest,
    ): ResponseEntity<AccountResponse> {
        val account = bankAccountService.transfer(id, request.toAccountId, request.amount)
        return ResponseEntity.ok(AccountResponse.from(account))
    }

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFound(ex: AccountNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message ?: "Account not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(ex.message ?: "Insufficient funds"))

    @ExceptionHandler(NegativeBalanceException::class)
    fun handleNegativeBalance(ex: NegativeBalanceException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message ?: "Negative balance not allowed"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message ?: "Invalid request"))

    data class ErrorResponse(
        val message: String,
    )
}
