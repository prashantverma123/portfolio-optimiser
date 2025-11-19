package com.example.backend.processing

import com.example.backend.events.FileUploadedEvent
import com.example.backend.storage.UserRecord
import com.example.backend.storage.UserStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files

object CsvUserProcessor {
    private val logger = LoggerFactory.getLogger(CsvUserProcessor::class.java)

    suspend fun process(event: FileUploadedEvent) = withContext(Dispatchers.IO) {
        val users = mutableListOf<UserRecord>()
        val path = event.path
        logger.info("Processing {}", path)
        try {
            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreEmptyLines(true).withTrim()).use { parser ->
                    var row = 1
                    for (record in parser) {
                        row++
                        try {
                            val user = UserRecord(
                                id = record.get("id"),
                                firstName = record.get("firstName"),
                                lastName = record.get("lastName"),
                                email = record.get("email")
                            )
                            users.add(user)
                        } catch (ex: Exception) {
                            logger.warn("Invalid row {} in {}: {}", row, path.fileName, ex.message)
                        }
                    }
                }
            }
            if (users.isNotEmpty()) {
                UserStore.addAll(users)
                logger.info("Stored {} user records from {}", users.size, path.fileName)
            } else {
                logger.warn("No valid user records found in {}", path.fileName)
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
