package mz.data.platform.bankaccount.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed
import java.math.BigDecimal
import java.time.LocalDateTime

@RedisHash("bank-account")
data class BankAccount(
    @Id
    val id: String,
    @Indexed
    val accountNumber: String,
    var balance: BigDecimal,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
    @Version
    var version: Long? = null,
)
