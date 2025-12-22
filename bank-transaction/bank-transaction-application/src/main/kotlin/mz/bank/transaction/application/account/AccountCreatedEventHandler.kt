package mz.bank.transaction.application.account

import mz.bank.transaction.application.integration.AccountCreatedEvent
import mz.bank.transaction.domain.account.AccountView
import org.slf4j.LoggerFactory
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(AccountCreatedEventHandler::class.java)

/**
 * Handler for AccountCreatedEvent from the bank-account service.
 * This handler maintains a local account view (read model) for the bank-transaction bounded context.
 * It does not emit any events as this is purely a local view update.
 */
@Component
class AccountCreatedEventHandler(
    private val accountViewRepository: AccountViewRepository,
) {
    /**
     * Handles incoming AccountCreatedEvent by storing the account ID in the local view.
     */
    @ServiceActivator(inputChannel = "inboundBankAccountEventsChannel")
    suspend fun handle(event: AccountCreatedEvent) {
        logger.info("Handling AccountCreatedEvent for accountId=${event.accountId}")

        val accountView = AccountView(accountId = event.accountId)
        accountViewRepository.upsert(accountView)

        logger.info("AccountView created for accountId=${event.accountId}")
    }
}
