package mz.shared.connector.redis

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import mz.shared.connector.redis.json.RedisMapRecordJsonSerializer
import mz.shared.domain.AGGREGATE_ID
import mz.shared.domain.LockProvider
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.stream.StreamReceiver
import org.springframework.data.redis.stream.StreamReceiver.StreamReceiverOptions
import org.springframework.data.redis.stream.StreamReceiver.StreamReceiverOptions.builder
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.ErrorMessage
import org.springframework.messaging.support.MessageBuilder
import java.time.Duration

private val logger = LogFactory.getLog(RedisStreamConsumer::class.java)

class RedisStreamConsumer<T, O> internal constructor(
    val channel: MessageChannel,
    val serializer: RedisMapRecordJsonSerializer,
    val type: Class<T>,
    val recordStream: Flow<MapRecord<String, String, String>>,
    val errorChannel: MessageChannel? = null,
    val mapper: ((T) -> O)? = null,
    val reactiveRedisTemplate: ReactiveRedisTemplate<String, *>,
    val consumer: Consumer,
    private val lockProvider: LockProvider? = null,
) : DisposableBean {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch { startConsumer() }
    }

    private suspend fun startConsumer(): Unit =
        recordStream
            .flowOn(Dispatchers.IO)
            .flatMapConcat { record ->
                flow { emit(record) }
                    .map { serializer.deserialize(it, type) }
                    .map(this::mapRecordMessage)
                    .map { message ->
                        sendToChannel(message, record)
                        message
                    }.flatMapConcat {
                        reactiveRedisTemplate.opsForStream<String, Any>().acknowledge(consumer.group, record).asFlow()
                    }
            }.retryWhen { cause, _ ->
                doOnError(cause)
            }.collect()

    private suspend fun doOnError(cause: Throwable): Boolean {
        logger.error("Error during the consumption of the stream, retry: $cause")
        errorChannel?.send(ErrorMessage(cause))
        delay(1000)
        return true
    }

    private suspend fun sendToChannel(
        message: Message<out Any?>,
        record: MapRecord<String, String, String>,
    ) {
        if (lockProvider != null) {
            val lockKey = message.headers[AGGREGATE_ID]
            if (lockKey != null && lockKey is String) {
                lockProvider.withLock(lockKey) {
                    sendMessageToChannel(record, message)
                }
            } else {
                sendMessageToChannel(record, message)
            }
        } else {
            sendMessageToChannel(record, message)
        }
    }

    private fun mapRecordMessage(desRecord: Message<T>): Message<out Any?> =
        mapper
            ?.invoke(desRecord.payload)
            ?.let {
                MessageBuilder
                    .withPayload(it)
                    .copyHeaders(desRecord.headers)
                    .build()
            } ?: desRecord

    private fun sendMessageToChannel(
        record: MapRecord<String, String, String>,
        message: Message<out Any>,
    ) {
        logger.info("Received from the stream: ${record.stream} message: $message")
        if (!channel.send(message, 1000)) {
            logger.error("Error sending message to the channel timeout: $message")
            errorChannel?.send(ErrorMessage(RuntimeException("Sending to the channel timeout"), message))
            throw RuntimeException("Sending to the channel timeout")
        }
    }

    override fun destroy() {
        logger.info("Stopping Redis Stream Consumer")
        scope.cancel()
    }
}

class RedisSteamConsumerBuilder<T, O>(
    val streamKey: String,
    val channel: MessageChannel,
    val consumerGroup: String,
    val consumerName: String,
    val type: Class<T>,
    val redisConnectionFactory: LettuceConnectionFactory,
    val objectMapper: ObjectMapper,
) {
    private var errorChannel: MessageChannel? = null

    private var options: StreamReceiverOptions<String, MapRecord<String, String, String>>? = null

    private var mapper: ((T) -> O)? = null

    private var lockProvider: LockProvider? = null

    fun withOptions(options: StreamReceiverOptions<String, MapRecord<String, String, String>>): RedisSteamConsumerBuilder<T, O> {
        this.options = options
        return this
    }

    fun withErrorChannel(errorChannel: MessageChannel): RedisSteamConsumerBuilder<T, O> {
        this.errorChannel = errorChannel
        return this
    }

    fun withLockProvider(lockProvider: LockProvider?): RedisSteamConsumerBuilder<T, O> {
        this.lockProvider = lockProvider
        return this
    }

    fun withMapper(mapper: (T) -> O): RedisSteamConsumerBuilder<T, O> {
        this.mapper = mapper
        return this
    }

    fun build(): RedisStreamConsumer<T, O> {
        val consumerGroup = Consumer.from(consumerGroup, consumerName)

        val reactiveRedisTemplate: ReactiveRedisTemplate<String, *> =
            ReactiveRedisTemplate<String, String>(this.redisConnectionFactory, RedisSerializationContext.string())

        val opsForStream = reactiveRedisTemplate.opsForStream<String, Any>()
        val consumerGroupMono =
            opsForStream
                .createGroup(streamKey, consumerGroup.group)
                .onErrorReturn(consumerGroup.group)

        val options =
            this.options ?: builder()
                .pollTimeout(Duration.ofMillis(100))
                .build()
        val receiver = StreamReceiver.create<String, MapRecord<String, String, String>>(redisConnectionFactory, options)

        // val events = receiver.receiveAutoAck(consumerGroup, StreamOffset.create(streamKey, ReadOffset.lastConsumed()))
        val events = receiver.receive(consumerGroup, StreamOffset.create(streamKey, ReadOffset.lastConsumed()))

        val recordStream =
            consumerGroupMono
                .onErrorComplete()
                .thenMany(events)

        val consumer =
            RedisStreamConsumer<T, O>(
                channel,
                RedisMapRecordJsonSerializer(objectMapper),
                type,
                recordStream.asFlow(),
                errorChannel,
                mapper,
                reactiveRedisTemplate,
                consumerGroup,
                lockProvider,
            )

        return consumer
    }
}
