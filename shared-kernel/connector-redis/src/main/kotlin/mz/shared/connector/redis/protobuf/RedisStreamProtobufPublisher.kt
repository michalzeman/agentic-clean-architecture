package mz.shared.connector.redis.protobuf

import com.google.protobuf.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import mz.shared.domain.AGGREGATE_ID
import mz.shared.domain.LockProvider
import org.apache.commons.logging.LogFactory
import org.springframework.data.redis.connection.stream.StringRecord
import org.springframework.data.redis.core.ReactiveRedisTemplate
import kotlin.concurrent.withLock
import org.springframework.messaging.Message as SpringMessage

private val logger = LogFactory.getLog(RedisStreamProtobufPublisher::class.java)

class RedisStreamProtobufPublisher<V : Message>(
    private val streamKey: String,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, *>,
    private val redisMapRecordProtobufSerializer: RedisMapRecordProtobufSerializer,
    private val lockRegistry: LockProvider? = null,
) {
    suspend fun publish(message: SpringMessage<V>): Unit =
        withContext(Dispatchers.IO) {
            lockRegistry?.let { lckRg ->
                message.headers[AGGREGATE_ID]?.let { lockKey ->
                    lckRg.withLock(lockKey.toString()) {
                        serializeAndPublish(message)
                    }
                }
            } ?: serializeAndPublish(message)
        }

    private suspend fun serializeAndPublish(message: SpringMessage<V>) {
        val recordToPublish = redisMapRecordProtobufSerializer.serialize(message).withStreamKey(streamKey)
        logger.info("Publishing protobuf message to stream: $streamKey")
        reactiveRedisTemplate
            .opsForStream<String, StringRecord>()
            .add(recordToPublish)
            .awaitSingle()
    }
}
