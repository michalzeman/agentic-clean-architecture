package mz.integration.messaging.data

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DataProcessingEngineApplication

fun main(args: Array<String>) {
    runApplication<DataProcessingEngineApplication>(*args)
}
