package mz.bank.transaction

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BankTransactionApplication

fun main(args: Array<String>) {
    runApplication<BankTransactionApplication>(*args)
}
