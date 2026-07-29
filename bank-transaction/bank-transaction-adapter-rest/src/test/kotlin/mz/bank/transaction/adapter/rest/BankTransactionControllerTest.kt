package mz.bank.transaction.adapter.rest

import mz.bank.transaction.application.transaction.BankTransactionCommandHandler
import mz.bank.transaction.application.transaction.BankTransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class BankTransactionControllerTest {
    private val controller =
        BankTransactionController(
            commandHandler = mock<BankTransactionCommandHandler>(),
            bankTransactionRepository = mock<BankTransactionRepository>(),
        )

    @Test
    fun `should return 400 Bad Request when IllegalArgumentException is thrown`() {
        // Given
        val exception = IllegalArgumentException("Invalid account ID format")

        // When
        val response = controller.handleIllegalArgument(exception)

        // Then
        assertThat(response.statusCode.value()).isEqualTo(400)
        val body = response.body ?: fail("body should not be null")
        assertThat(body.error).isEqualTo("Bad Request")
        assertThat(body.message).isEqualTo("Invalid account ID format")
    }

    @Test
    fun `should return 400 Bad Request with default message when IllegalArgumentException has null message`() {
        // Given
        val exception = IllegalArgumentException(null as String?)

        // When
        val response = controller.handleIllegalArgument(exception)

        // Then
        assertThat(response.statusCode.value()).isEqualTo(400)
        val body = response.body ?: fail("body should not be null")
        assertThat(body.error).isEqualTo("Bad Request")
        assertThat(body.message).isEqualTo("Invalid request")
    }

    @Test
    fun `should return 409 Conflict when IllegalStateException is thrown`() {
        // Given
        val exception = IllegalStateException("Transaction already completed")

        // When
        val response = controller.handleIllegalState(exception)

        // Then
        assertThat(response.statusCode.value()).isEqualTo(409)
        val body = response.body ?: fail("body should not be null")
        assertThat(body.error).isEqualTo("Conflict")
        assertThat(body.message).isEqualTo("Transaction already completed")
    }

    @Test
    fun `should return 409 Conflict with default message when IllegalStateException has null message`() {
        // Given
        val exception = IllegalStateException(null as String?)

        // When
        val response = controller.handleIllegalState(exception)

        // Then
        assertThat(response.statusCode.value()).isEqualTo(409)
        val body = response.body ?: fail("body should not be null")
        assertThat(body.error).isEqualTo("Conflict")
        assertThat(body.message).isEqualTo("Operation not allowed in current state")
    }
}
