package mz.shared.connector.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import net.logstash.logback.encoder.LogstashEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.nio.charset.StandardCharsets

private const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
private const val SPAN_ID = "b7ad6b7169203331"

class LogbackConfigTest {
    @Test
    fun `test classpath uses console pattern encoder, not JSON`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        // logback 1.5.x has no getAppenders(); only iteratorForAppenders().
        // The encoder property lives on OutputStreamAppender, not on the Appender interface.
        val encoder =
            context
                .getLogger("ROOT")
                .iteratorForAppenders()
                .asSequence()
                .filterIsInstance<OutputStreamAppender<*>>()
                .firstNotNullOf { it.encoder }
        assertThat(encoder).isNotNull
        assertThat(encoder!!::class.simpleName).doesNotContain("Logstash")
    }

    @Test
    fun `main resources contain logback xml with LogstashEncoder`() {
        val resource = javaClass.classLoader.getResource("logback.xml")
        assertThat(resource).isNotNull
        // includeMdc is the mechanism that emits MDC keys (traceId/spanId among them)
        // as JSON fields; field presence itself is verified by the smoke run.
        assertThat(resource!!.readText()).contains("LogstashEncoder", "includeMdc")
    }

    @Test
    fun `runtime LogstashEncoder emits traceId and spanId MDC values as json fields`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger("LogbackConfigTest")
        val encoded: String =
            try {
                MDC.put("traceId", TRACE_ID)
                MDC.put("spanId", SPAN_ID)
                val encoder =
                    LogstashEncoder().apply {
                        isIncludeMdc = true
                        start()
                    }
                String(encoder.encode(loggingEvent(logger)), StandardCharsets.UTF_8)
            } finally {
                MDC.clear()
            }

        assertThat(encoded).contains("\"traceId\":\"$TRACE_ID\"", "\"spanId\":\"$SPAN_ID\"")
    }

    private fun loggingEvent(logger: org.slf4j.Logger): LoggingEvent =
        LoggingEvent("LogbackConfigTest", logger as ch.qos.logback.classic.Logger, Level.INFO, "traced json line", null, arrayOf())
}
