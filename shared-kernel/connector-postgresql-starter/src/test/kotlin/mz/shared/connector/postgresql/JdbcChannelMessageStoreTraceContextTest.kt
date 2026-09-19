package mz.shared.connector.postgresql

import org.assertj.core.api.Assertions.assertThat
import org.h2.Driver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore
import org.springframework.integration.jdbc.store.channel.H2ChannelMessageStoreQueryProvider
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TRACEPARENT_HEADER = "traceparent" // mirrors TraceParent.HEADER (connector-observability)
private const val HEADER_VALUE = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
private const val GROUP_KEY = "trace-test-group"
private const val REGION = "trace-test"

/**
 * JDBC hop of the W3C traceparent propagation: JdbcChannelMessageStore serializes the
 * whole Message (headers included) via Jackson, so the header must survive a store on
 * one thread and a poll on another thread. The store is built against H2 with the H2
 * query provider (the Postgres provider's SQL cannot run on H2) and the production
 * serializer wiring.
 */
class JdbcChannelMessageStoreTraceContextTest {
    private lateinit var dataSource: SimpleDriverDataSource
    private lateinit var store: JdbcChannelMessageStore
    private val storingExecutor = Executors.newSingleThreadExecutor()
    private val pollingExecutor = Executors.newSingleThreadExecutor()

    @BeforeEach
    fun prepareStore() {
        dataSource = SimpleDriverDataSource(Driver(), "jdbc:h2:mem:tracecontext;DB_CLOSE_DELAY=-1", "sa", "")
        DatabasePopulatorUtils.execute(
            ResourceDatabasePopulator(ClassPathResource("org/springframework/integration/jdbc/schema-drop-h2.sql")),
            dataSource,
        )
        DatabasePopulatorUtils.execute(
            ResourceDatabasePopulator(ClassPathResource("org/springframework/integration/jdbc/schema-h2.sql")),
            dataSource,
        )
        store =
            JdbcChannelMessageStore(dataSource).apply {
                setChannelMessageStoreQueryProvider(H2ChannelMessageStoreQueryProvider())
                setRegion(REGION)
                JsonChannelMessageStoreSerializers.configure(this, PostgresqlIntegrationProperties().trustedPackages)
            }
        store.afterPropertiesSet()
    }

    @AfterEach
    fun shutdownExecutors() {
        storingExecutor.shutdownNow()
        pollingExecutor.shutdownNow()
    }

    @Test
    fun `traceparent header survives the jdbc message store across threads`() {
        val message =
            MessageBuilder
                .withPayload("trace-context-payload")
                .setHeader(TRACEPARENT_HEADER, HEADER_VALUE)
                .build()

        storeOnOneThread(message)
        val polled = pollOnAnotherThread()

        assertThat(polled).isNotNull
        assertThat(polled!!.headers[TRACEPARENT_HEADER]).isEqualTo(HEADER_VALUE)
        assertThat(polled.payload).isEqualTo("trace-context-payload")
    }

    @Test
    fun `message without traceparent header stores and polls without failing`() {
        val message = MessageBuilder.withPayload("plain-payload").build()

        storeOnOneThread(message)
        val polled = pollOnAnotherThread()

        assertThat(polled).isNotNull
        assertThat(polled!!.headers).doesNotContainKey(TRACEPARENT_HEADER)
    }

    private fun storeOnOneThread(message: Message<*>) {
        CompletableFuture
            .runAsync({ store.addMessageToGroup(GROUP_KEY, message) }, storingExecutor)
            .get(5, TimeUnit.SECONDS)
    }

    private fun pollOnAnotherThread(): Message<*>? =
        CompletableFuture
            .supplyAsync({ store.pollMessageFromGroup(GROUP_KEY) }, pollingExecutor)
            .get(5, TimeUnit.SECONDS)
}
