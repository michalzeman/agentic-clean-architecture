package mz.bank.transaction.adapter.rest

import mz.shared.domain.LockAcquisitionException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test

class BankTransactionExceptionAdvisorTest {
    private val advisor = BankTransactionExceptionAdvisor()

    @Test
    fun `should return 503 when lock acquisition exception is handled`() {
        // Given
        val exception = LockAcquisitionException("Failed to acquire lock 'txn-123' after 3 attempts")

        // When
        val response = advisor.handleLockAcquisition(exception)

        // Then
        assertThat(response.statusCode.value()).isEqualTo(503)
        val body = response.body ?: fail("body should not be null")
        assertThat(body.error).isEqualTo("Service Unavailable")
        assertThat(body.message).contains("Could not acquire lock")
    }
}
