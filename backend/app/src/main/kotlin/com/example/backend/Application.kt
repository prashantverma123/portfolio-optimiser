package com.example.backend

import com.example.backend.events.FileUploadEventBus
import com.example.backend.events.FileUploadedEvent
import com.example.backend.processing.CsvUserProcessor
import com.example.backend.storage.UserStore
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import io.ktor.utils.io.core.readBytes

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    io.ktor.server.engine.embeddedServer(io.ktor.server.netty.Netty, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        anyHost()
        allowNonSimpleContentTypes = true
    }

    val uploadDirectory = Paths.get(System.getenv("UPLOAD_DIR") ?: "uploads").toAbsolutePath()
    Files.createDirectories(uploadDirectory)

    val processorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    FileUploadEventBus.startProcessing(processorScope) { event ->
        CsvUserProcessor.process(event)
    }

    environment.monitor.subscribe(ApplicationStopped) {
        processorScope.cancel()
    }

    routing {
        get("/api/users") {
            call.respond(UserStore.getAll())
        }

        post("/api/uploads") {
            val multipart = call.receiveMultipart()
            var savedFile: Path? = null
            var validationError: String? = null

            while (true) {
                val part = multipart.readPart() ?: break
                when (part) {
                    is PartData.FileItem -> {
                        val originalFileName = part.originalFileName
                        val ext = originalFileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
                        if (ext != "csv") {
                            validationError = "Only CSV files are supported."
                        } else {
                            val timestamp = Instant.now().toEpochMilli()
                            val fileName = "upload-${timestamp}-${originalFileName}"
                            val filePath = uploadDirectory.resolve(fileName)
                            val packet = part.provider.invoke()
                            val bytes = packet.readBytes()
                            packet.close()
                            Files.write(filePath, bytes)
                            savedFile = filePath
                        }
                    }
                    else -> Unit
                }
                part.dispose()
                if (validationError != null) {
                    break
                }
            }

            validationError?.let {
                return@post call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = UploadResponse(it)
                )
            }

            val path = savedFile ?: return@post call.respondText(
                "No CSV file received",
                status = HttpStatusCode.BadRequest
            )

            FileUploadEventBus.publish(FileUploadedEvent(path))

            call.respond(
                status = HttpStatusCode.Accepted,
                message = UploadResponse("File uploaded successfully, processing started.")
            )
        }
    }
}

@Serializable
data class UploadResponse(val message: String)
