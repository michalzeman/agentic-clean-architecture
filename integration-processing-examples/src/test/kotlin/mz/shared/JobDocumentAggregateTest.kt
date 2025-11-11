package mz.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mz.shared.connector.redis.DataPlatformRedisConfiguration
import mz.shared.domain.JobDocument
import mz.shared.domain.JobDocumentAggregate
import mz.shared.domain.JobInfo
import mz.shared.domain.finished
import mz.shared.domain.toJobDocumentAggregate
import org.apache.commons.logging.LogFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.integration.aggregator.AggregatingMessageHandler
import org.springframework.integration.aggregator.CorrelationStrategy
import org.springframework.integration.aggregator.MessageGroupProcessor
import org.springframework.integration.aggregator.ReleaseStrategy
import org.springframework.integration.channel.QueueChannel
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.integration.redis.store.RedisMessageStore
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import java.time.Duration

private const val JOB_ID = "jobId"

private val logger = LogFactory.getLog(JobDocumentAggregateTest::class.java)

@Tag("systemChecks")
@SpringBootTest(
    classes = [
        DataPlatformRedisConfiguration::class,
        IntegrationFlowsConfiguration::class,
        DataProcessingTestConfiguration::class,
        JobDocumentAggregateTestConfiguration::class,
    ],
)
class JobDocumentAggregateTest {
    @Autowired(required = true)
    lateinit var jobDocumentAggregateChannelTest: MessageChannel

    @Autowired(required = true)
    lateinit var jobAggregatorChannelTest: QueueChannel

    @Autowired(required = true)
    lateinit var redisMessageStoreTest: RedisMessageStore

    @Autowired(required = true)
    lateinit var jobAggregateInstanceTest: AggregatingMessageHandler

    @BeforeEach
    fun setup() {
        jobAggregateInstanceTest.purgeOrphanedGroups()
    }

    @Test
    fun `test JobAggregator`() {
        val job1 = "Job 1"
        val jobDocument1 = JobDocument("1", job1, listOf(JobInfo("STARTED", true)))
        logger.info("Sending job document 1: $jobDocument1")
        jobDocumentAggregateChannelTest.send(
            MessageBuilder.withPayload(jobDocument1).setHeader(JOB_ID, jobDocument1.id).build(),
            1000,
        )

        val jobDocument2 = JobDocument("1", job1, listOf(JobInfo("RUNNING", true)))
        logger.info("Sending job document 2: $jobDocument2")
        jobDocumentAggregateChannelTest.send(
            MessageBuilder.withPayload(jobDocument2).setHeader(JOB_ID, jobDocument2.id).build(),
            1000,
        )
        logger.info("Waiting for 5 seconds before sending the last message...")
        runBlocking {
            delay(5000)
        }

        val receiveUncompleted = jobAggregatorChannelTest.receive(5000)!!

        assertThat(receiveUncompleted.payload).isEqualTo(JobDocumentAggregate(listOf(jobDocument1, jobDocument2)))

        val jobDocument3 = JobDocument("1", job1, listOf(JobInfo("STOPPED", true)))
        logger.info("Sending job document 3: $jobDocument3")
        jobDocumentAggregateChannelTest.send(
            MessageBuilder.withPayload(jobDocument3).setHeader(JOB_ID, jobDocument3.id).build(),
            1000,
        )

        val jobDocumentDifferentGroup = JobDocument("3", "Job 3", listOf(JobInfo("RUNNING", true)))
        jobDocumentAggregateChannelTest.send(
            MessageBuilder
                .withPayload(jobDocumentDifferentGroup)
                .setHeader(JOB_ID, jobDocumentDifferentGroup.id)
                .build(),
            1000,
        )

        val receiveAfterExpiration = jobAggregatorChannelTest.receive(5000)!!

        assertThat(receiveAfterExpiration.payload).isInstanceOf(JobDocumentAggregate::class.java)
        assertThat(receiveAfterExpiration.payload).isEqualTo(JobDocumentAggregate(listOf(jobDocument3)))
    }
}

@TestConfiguration
@ComponentScan("mz.integration.messaging.**")
class JobDocumentAggregateTestConfiguration(
    val redisConnectionFactory: LettuceConnectionFactory,
    val genericJackson2JsonRedisSerializer: GenericJackson2JsonRedisSerializer,
) {
    @Bean
    fun redisMessageStoreTest(): RedisMessageStore =
        RedisMessageStore(redisConnectionFactory, "JOB_DOC").apply {
            setValueSerializer(genericJackson2JsonRedisSerializer)
            isTimeoutOnIdle = true
        }

    @Bean
    fun jobDocumentAggregateChannelTest(redisMessageJsonStore: RedisChannelMessageStore) = QueueChannel()

    @Bean
    fun jobAggregatorChannelTest(redisMessageJsonStore: RedisChannelMessageStore) =
//        QueueChannel()
        MessageChannels
            .queue(
                "job-aggregator-storage-test-channel",
                redisMessageJsonStore,
                "job-aggregator-storage-test-storage",
            ).apply {
                datatype(JobDocumentAggregate::class.java)
            }

    @Bean
    fun jobAggregateInstanceTest(redisMessageStoreTest: RedisMessageStore): AggregatingMessageHandler {
        val correlation: CorrelationStrategy =
            CorrelationStrategy {
                it.headers[JOB_ID]
            }

        val releaseStrategy: ReleaseStrategy =
            ReleaseStrategy {
                it.toJobDocumentAggregate().finished()
            }

        val messageGroupProcessor: MessageGroupProcessor =
            MessageGroupProcessor {
                it.toJobDocumentAggregate()
            }

        return AggregatingMessageHandler(
            messageGroupProcessor,
            redisMessageStoreTest,
            correlation,
            releaseStrategy,
        ).apply {
            setExpireGroupsUponCompletion(true)
            setExpireDuration(Duration.ofMillis(50))
            setExpireTimeout(100L)
            this.setSendPartialResultOnExpiry(true)
            setAsync(true)
        }
    }

    @Bean
    fun jobDocumentAggregateFlow(
        jobAggregatorChannelTest: QueueChannel,
        jobAggregateInstanceTest: AggregatingMessageHandler,
    ): IntegrationFlow =
        integrationFlow("jobDocumentAggregateChannelTest") {
            log("Boom! Zemo ->")
            split()
            // there it doesn't work for the sendPartialResultOnExpiry(true), it will discard messages !!!!
//            aggregate {
//                sendPartialResultOnExpiry(true)
//                correlationStrategy { m -> m.headers[JOB_ID] }
//                releaseStrategy { it.toJobDocumentAggregate().finished() }
//                outputProcessor { it.toJobDocumentAggregate() }
//                messageStore(redisMessageStoreTest())
//                async(true)
//                expireGroupsUponCompletion(true)
//                // timeout definition and how often is checking in background
//                expireTimeout(100)
//                expireDuration(Duration.ofMillis(50))
// //                groupTimeout { it.size() * 200L }
//            }
            handle(jobAggregateInstanceTest)
            log("After aggregate ->>>>")
            channel(jobAggregatorChannelTest)
        }
}
