package mz.integration.messaging.data.connector.redis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import mz.data.platform.shared.domain.AGGREGATE_ID
import mz.data.platform.shared.domain.LockProvider
import mz.integration.messaging.mz.integration.messaging.data.connector.redis.json.RedisMapRecordJsonSerializer
import org.apache.commons.logging.LogFactory
import org.springframework.data.redis.connection.stream.StringRecord
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.messaging.Message
import kotlin.concurrent.withLock

private val logger = LogFactory.getLog(RedisStreamPublisher::class.java)

class RedisStreamPublisher<V>(
    private val streamKey: String,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, *>,
    private val redisMapRecordJsonSerializer: RedisMapRecordJsonSerializer,
    private val lockRegistry: LockProvider? = null,
) {
    suspend fun publish(message: Message<V>): Unit =
        withContext(Dispatchers.IO) {
            lockRegistry?.let { lckRg ->
                message.headers[AGGREGATE_ID]?.let { lockKey ->
                    lckRg.withLock(lockKey.toString()) {
                        serializeAndPublish(message)
                    }
                }
            } ?: serializeAndPublish(message)
        }

    private suspend fun serializeAndPublish(message: Message<V>) {
        val recordToPublish = redisMapRecordJsonSerializer.serialize(message).withStreamKey(streamKey)
        logger.info("Publishing to the stream: $streamKey message: $recordToPublish")
        reactiveRedisTemplate
            .opsForStream<String, StringRecord>()
            .add(recordToPublish)
            .awaitSingle()
    }
}
