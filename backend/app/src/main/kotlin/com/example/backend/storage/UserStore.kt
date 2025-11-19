package com.example.backend.storage

import kotlinx.serialization.Serializable
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class UserRecord(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String
)

object UserStore {
    private val users = CopyOnWriteArrayList<UserRecord>()

    fun addAll(newUsers: List<UserRecord>) {
        users.addAll(newUsers)
    }

    fun getAll(): List<UserRecord> = users.toList()

    fun clear() {
        users.clear()
    }
}
