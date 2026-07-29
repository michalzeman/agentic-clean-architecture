package mz.bank.transaction.adapter.rest

import mz.shared.domain.LockAcquisitionException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Maps [LockAcquisitionException] to 503 Service Unavailable so clients can retry.
 */
@RestControllerAdvice
class BankTransactionExceptionAdvisor {
    private val logger = LoggerFactory.getLogger(BankTransactionExceptionAdvisor::class.java)

    @ExceptionHandler(LockAcquisitionException::class)
    fun handleLockAcquisition(ex: LockAcquisitionException): ResponseEntity<ErrorResponse> {
        logger.error("Lock acquisition failed", ex)
        val error =
            ErrorResponse(
                error = "Service Unavailable",
                message = "Could not acquire lock for transaction, please retry",
            )
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error)
    }
}
