package com.example.backend.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPooled
import java.io.Closeable
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

data class FileUploadedEvent(val path: Path)

interface FileUploadEventBus : Closeable {
    fun startProcessing(scope: CoroutineScope, processor: suspend (FileUploadedEvent) -> Unit)
    suspend fun publish(event: FileUploadedEvent)
}

object FileUploadEventBusFactory {
    private val logger = LoggerFactory.getLogger(FileUploadEventBusFactory::class.java)

    fun create(): FileUploadEventBus {
        val backend = System.getenv("MESSAGE_BACKEND")?.lowercase()
        return if (backend == "redis") {
            val redisUri = System.getenv("REDIS_URI") ?: "redis://localhost:6379"
            logger.info("Using Redis queue backend at {}", redisUri)
            RedisFileUploadEventBus(redisUri)
        } else {
            if (backend != null) {
                logger.warn("Unrecognized MESSAGE_BACKEND '{}', defaulting to in-memory queue", backend)
            }
            InMemoryFileUploadEventBus
        }
    }
}

object InMemoryFileUploadEventBus : FileUploadEventBus {
    private val logger = LoggerFactory.getLogger(InMemoryFileUploadEventBus::class.java)
    private val channel = Channel<FileUploadedEvent>(capacity = Channel.UNLIMITED)

    override fun startProcessing(scope: CoroutineScope, processor: suspend (FileUploadedEvent) -> Unit) {
        scope.launch {
            for (event in channel) {
                try {
                    processor(event)
                } catch (exception: Exception) {
                    logger.error("Failed to process event for {}", event.path, exception)
                }
            }
        }
    }

    override suspend fun publish(event: FileUploadedEvent) {
        channel.send(event)
    }

    override fun close() {
        channel.close()
    }
}

class RedisFileUploadEventBus(redisUri: String) : FileUploadEventBus {
    private val logger = LoggerFactory.getLogger(RedisFileUploadEventBus::class.java)
    private val queueKey = System.getenv("REDIS_QUEUE_KEY") ?: "file-uploads"
    private val client = JedisPooled(URI(redisUri))

    override fun startProcessing(scope: CoroutineScope, processor: suspend (FileUploadedEvent) -> Unit) {
        scope.launch {
            while (scope.isActive) {
                try {
                    val result = client.brpop(5.seconds.inWholeSeconds.toInt(), queueKey)
                    val message = result?.getOrNull(1)
                    if (message != null) {
                        processor(FileUploadedEvent(Paths.get(message)))
                    }
                } catch (exception: Exception) {
                    logger.error("Failed to poll Redis for events", exception)
                }
            }
        }
    }

    override suspend fun publish(event: FileUploadedEvent) {
        client.lpush(queueKey, event.path.toString())
    }

    override fun close() {
        client.close()
    }
}
