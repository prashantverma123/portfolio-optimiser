package com.example.backend.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Path

data class FileUploadedEvent(val path: Path)

object FileUploadEventBus {
    private val logger = LoggerFactory.getLogger(FileUploadEventBus::class.java)
    private val channel = Channel<FileUploadedEvent>(capacity = Channel.UNLIMITED)

    fun startProcessing(scope: CoroutineScope, processor: suspend (FileUploadedEvent) -> Unit) {
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

    suspend fun publish(event: FileUploadedEvent) {
        channel.send(event)
    }
}
