package mz.bank.system.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal

/**
 * Builds a JSON request for creating a bank account.
 *
 * @param email The email address for the account owner
 * @param initialBalance The initial balance for the account
 * @return JsonNode representing the account creation request
 */
fun ObjectMapper.buildAccountRequest(
    email: String,
    initialBalance: BigDecimal,
): JsonNode =
    createObjectNode().apply {
        put("email", email)
        put("initialBalance", initialBalance)
    }

/**
 * Builds a JSON request for creating a transaction.
 *
 * @param fromAccountId The source account ID
 * @param toAccountId The destination account ID
 * @param amount The amount to transfer
 * @return JsonNode representing the transaction request
 */
fun ObjectMapper.buildTransactionRequest(
    fromAccountId: String,
    toAccountId: String,
    amount: BigDecimal,
): JsonNode =
    createObjectNode().apply {
        put("fromAccountId", fromAccountId)
        put("toAccountId", toAccountId)
        put("amount", amount)
    }
