package mz.shared.connector.redis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import mz.shared.connector.observability.TraceParent
import mz.shared.connector.redis.json.RedisMapRecordJsonSerializer
import mz.shared.domain.AGGREGATE_ID
import mz.shared.domain.LockProvider
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.StringRecord
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder

private val logger = LoggerFactory.getLogger(RedisStreamPublisher::class.java)

class RedisStreamPublisher<V : com.google.protobuf.GeneratedMessage>(
    private val streamKey: String,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, *>,
    private val redisMapRecordJsonSerializer: RedisMapRecordJsonSerializer,
    private val lockRegistry: LockProvider? = null,
    private val traceParent: TraceParent? = null,
) {
    suspend fun publish(message: Message<V>) {
        val stampedMessage = stampTraceContext(message)
        withContext(Dispatchers.IO) {
            lockRegistry?.let { lckRg ->
                stampedMessage.headers[AGGREGATE_ID]?.let { lockKey ->
                    lckRg.withLock(lockKey.toString()) {
                        serializeAndPublish(stampedMessage)
                    }
                }
            } ?: serializeAndPublish(stampedMessage)
        }
    }

    private suspend fun serializeAndPublish(message: Message<V>) {
        val recordToPublish = redisMapRecordJsonSerializer.serialize(message).withStreamKey(streamKey)
        logger.info("Publishing to the stream: $streamKey message: $recordToPublish")
        reactiveRedisTemplate
            .opsForStream<String, StringRecord>()
            .add(recordToPublish)
            .awaitSingle()
    }

    private fun stampTraceContext(message: Message<V>): Message<V> {
        val traceparent = traceParent?.format() ?: return message
        return MessageBuilder
            .fromMessage(message)
            .setHeader(TraceParent.HEADER, traceparent)
            .build()
    }
}
