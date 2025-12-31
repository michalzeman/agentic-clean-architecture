package mz.bank.transaction.application.transaction

import mz.bank.transaction.domain.BankTransactionCommand
import org.springframework.integration.annotation.Gateway
import org.springframework.integration.annotation.MessagingGateway
import org.springframework.integration.support.MessageBuilder
import org.springframework.messaging.Message

/**
 * Messaging gateway for sending BankTransactionCommand to the command channel.
 *
 * This gateway provides a simple interface for publishing commands to the
 * bankTransactionCommandChannel, which are then processed asynchronously by
 * the BankTransactionCommandHandler.
 *
 * Usage:
 * ```
 * @Component
 * class SomeService(private val gateway: BankTransactionCommandGateway) {
 *     suspend fun doSomething() {
 *         val command = BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(...)
 *         gateway.send(command)
 *     }
 * }
 * ```
 */
@MessagingGateway
interface BankTransactionCommandGateway {
    /**
     * Sends a BankTransactionCommand to the command channel for asynchronous processing.
     *
     * @param command The command to be processed
     */
    suspend fun send(command: BankTransactionCommand) {
        val message = MessageBuilder.withPayload(command).build()
        send(message)
    }

    @Gateway(requestChannel = "bankTransactionCommandChannel")
    suspend fun send(message: Message<out BankTransactionCommand>)
}
