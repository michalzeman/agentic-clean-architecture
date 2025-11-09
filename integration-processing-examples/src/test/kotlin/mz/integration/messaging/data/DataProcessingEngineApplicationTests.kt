package mz.integration.messaging.data

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import mz.integration.messaging.data.connector.redis.RedisStreamPublisher
import mz.integration.messaging.data.domain.JobDocument
import mz.integration.messaging.data.domain.JobInfo
import mz.integration.messaging.mz.integration.messaging.data.connector.redis.json.RedisMapRecordJsonSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.channel.QueueChannel
import org.springframework.messaging.support.GenericMessage
import org.springframework.messaging.support.MessageBuilder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

const val TEST_DATA_PROCESSING_STREAM = "test-data-processing-stream"

@Tag("systemChecks")
@SpringBootTest(
    classes = [
        DataProcessingConfiguration::class,
        IntegrationFlowsConfiguration::class,
        DataProcessingTestConfiguration::class,
    ],
)
class DataProcessingEngineApplicationTests {
    @Autowired(required = true)
    lateinit var reactiveRedisTemplate: ReactiveRedisTemplate<String, *>

    @Autowired(required = true)
    lateinit var outboundRedisStreamChannel: PublishSubscribeChannel

    @Autowired(required = true)
    lateinit var objectMapper: ObjectMapper

    @Autowired(required = true)
    lateinit var jobDocumentInboundChannelTest: QueueChannel

    @Autowired(required = true)
    lateinit var jobModelProtoTestChannel: QueueChannel

    @Autowired(required = true)
    lateinit var jobDocumentStorageChannelTest: QueueChannel

    @Test
    fun serializeDeserialize() {
        val jobDocument =
            JobDocument(UUID.randomUUID().toString(), "Test Job document", listOf(JobInfo("STARTED", true)))

        val dataProcessingJsonSerializer = RedisMapRecordJsonSerializer(objectMapper)
        val serialized = dataProcessingJsonSerializer.serialize(GenericMessage(jobDocument))

        val deserialize = dataProcessingJsonSerializer.deserialize(serialized, JobDocument::class.java)

        assertThat(serialized).isNotEmpty
        assertThat(deserialize.payload).isEqualTo(jobDocument)
    }

    @Test
    fun outboundChannelToRedis() {
        val jobDocument =
            JobDocument(
                UUID.randomUUID().toString(),
                "Test Job document from Channel",
                listOf(JobInfo("Failed", false)),
            )

        val atomicReference = AtomicBoolean(false)
        outboundRedisStreamChannel.subscribe { atomicReference.set(true) }
        assertThat(outboundRedisStreamChannel.send(GenericMessage(jobDocument))).isTrue

        Thread.sleep(5000)

        assertThat(atomicReference.get()).isTrue
    }

    @Test
    fun `publish to redis stream`(): Unit =
        runBlocking {
            val jobDocument =
                JobDocument(
                    UUID.randomUUID().toString(),
                    "Test Job document from Channel",
                    listOf(JobInfo("Failed", false)),
                )

            val redisMapRecordJsonSerializer = RedisMapRecordJsonSerializer(objectMapper)

            val cut =
                RedisStreamPublisher<JobDocument>(
                    TEST_DATA_PROCESSING_STREAM,
                    reactiveRedisTemplate,
                    redisMapRecordJsonSerializer,
                )

            cut.publish(MessageBuilder.withPayload(jobDocument).build())

            val receive =
                jobDocumentInboundChannelTest.receive(5000)?.let {
                    it.payload as? JobDocument
                }
            assertThat(receive).isNotNull
            assertThat(receive!!.jobInfos).isEqualTo(jobDocument.jobInfos)
            assertThat(receive.name).isEqualTo(jobDocument.name)

            cut.publish(MessageBuilder.withPayload(jobDocument).build())
            val receive2 =
                jobDocumentInboundChannelTest.receive(5000)?.let {
                    it.payload as? JobDocument
                }
            assertThat(receive2).isNotNull

            cut.publish(MessageBuilder.withPayload(jobDocument).build())
            val receive3 =
                jobDocumentInboundChannelTest.receive(5000)?.let {
                    it.payload as? JobDocument
                }
            assertThat(receive3).isNotNull
        }

    @Test
    fun `json message storage backed channel`() {
        val jobDocument =
            JobDocument(
                UUID.randomUUID().toString(),
                "Test Job document from Channel",
                listOf(JobInfo("Failed", false)),
            )

        val wasSend = jobDocumentStorageChannelTest.send(MessageBuilder.withPayload(jobDocument).build())
        assertThat(wasSend).isTrue

        val received = jobDocumentStorageChannelTest.receive(1000)
        assertThat(received).isNotNull
        assertThat(received!!.payload).isInstanceOf(JobDocument::class.java)
    }
}
