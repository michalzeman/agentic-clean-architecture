package mz.bank.transaction.adapter.redis.stream.wiring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.support.json.JacksonJsonUtils

@Configuration
class RedisStreamConfiguration {
    @Bean("protobufObjectMapper")
    fun protobufObjectMapper(): ObjectMapper {
        val objectMapper =
            JacksonJsonUtils.messagingAwareMapper(
                "mz",
                "java.math",
                "org.springframework.data.redis.connection.stream",
                "kotlin.collections",
            )
        objectMapper
            .registerModule(KotlinModule.Builder().build())
            .registerModule(ProtobufModule())
        return objectMapper
    }
}
