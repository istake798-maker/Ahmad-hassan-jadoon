package com.example.data.repository

import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRole
import com.example.data.model.ClientLead
import com.example.data.model.ClientStatus
import com.example.data.model.DailyStat
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.OperationType
import com.example.data.model.WorkType
import com.example.data.model.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvisFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun observeClients(userId: String): Flow<List<ClientLead>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch." }
        val path = "users/$uid/clients"
        emitAll(
            db.collection("users").document(uid).collection("clients")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ClientLead::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLogFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMemories(userId: String): Flow<List<MemoryItem>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch." }
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MemoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLogFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMessages(userId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch." }
        val path = "users/$uid/messages"
        emitAll(
            db.collection("users").document(uid).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(60)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    unwrapAndLogFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeDailyStat(userId: String, dateKey: String = todayDateKey()): Flow<DailyStat> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session user mismatch." }
        val path = "users/$uid/dailyStats/$dateKey"
        emitAll(
            db.collection("users").document(uid).collection("dailyStats").document(dateKey)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            DailyStat::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: DailyStat(userId = uid, dateKey = dateKey)
                    } else {
                        DailyStat(userId = uid, dateKey = dateKey)
                    }
                }
                .catch { error ->
                    unwrapAndLogFlowError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun addClientLead(
        name: String,
        workType: WorkType,
        priceUsd: Int,
        platform: String,
        contactHandle: String,
        status: ClientStatus,
        outreachMessage: String = "",
        notes: String = "",
        countTowardsDailyHunt: Boolean = false
    ): Result<String> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        var docPath = "users/$currentUid/clients"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Client name is required." }
            val cleanPlatform = platform.trim().ifEmpty { "Google" }.take(40)
            val safePrice = priceUsd.coerceIn(1, 100_000)

            val dateKey = todayDateKey()
            val statRef = db.collection("users").document(uid).collection("dailyStats").document(dateKey)
            val clientRef = db.collection("users").document(uid).collection("clients").document()
            docPath = clientRef.path

            if (countTowardsDailyHunt) {
                val statSnap = statRef.get().await()
                val currentHunted = (statSnap.getLong("huntedCount") ?: 0L).toInt()
                if (currentHunted >= 10) {
                    return Result.failure(IllegalStateException("Aaj ke 10 clients ka daily hunt quota poora ho chuka hai."))
                }
            }

            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "workType" to workType.name,
                "priceUsd" to safePrice,
                "platform" to cleanPlatform,
                "contactHandle" to contactHandle.trim().take(120),
                "status" to status.name,
                "outreachMessage" to outreachMessage.trim().take(2000),
                "notes" to notes.trim().take(1000),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { value ->
                value !is String || value.isNotEmpty()
            }

            clientRef.set(payload).await()

            if (countTowardsDailyHunt) {
                incrementDailyCounter(uid, dateKey, huntedDelta = 1L)
            }

            Result.success(clientRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateClientHandled(
        clientId: String,
        workType: WorkType,
        priceUsd: Int,
        lastClientMessage: String,
        draftedReply: String,
        newStatus: ClientStatus,
        notes: String? = null
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/clients/$clientId"
        return try {
            val uid = requireUserId()
            require(clientId.isNotBlank()) { "Client ID is required." }
            require(newStatus != ClientStatus.CLOSED_WON) {
                "Deal close requires Hassan's explicit Haan approval."
            }
            val docRef = db.collection("users").document(uid).collection("clients").document(clientId)
            val updates = mutableMapOf<String, Any>(
                "workType" to workType.name,
                "priceUsd" to priceUsd.coerceIn(1, 100_000),
                "lastClientMessage" to lastClientMessage.trim().take(2000),
                "draftedReply" to draftedReply.trim().take(2000),
                "status" to newStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (notes != null) {
                updates["notes"] = notes.trim().take(1000)
            }
            docRef.update(updates).await()
            incrementDailyCounter(uid, todayDateKey(), repliesDelta = 1L)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateClientStatus(
        clientId: String,
        newStatus: ClientStatus,
        outreachMessage: String? = null
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/clients/$clientId"
        return try {
            val uid = requireUserId()
            require(clientId.isNotBlank()) { "Client ID is required." }
            require(newStatus != ClientStatus.CLOSED_WON) {
                "Use approveClientDeal for closing deals."
            }
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (outreachMessage != null) {
                updates["outreachMessage"] = outreachMessage.trim().take(2000)
            }
            db.collection("users").document(uid).collection("clients").document(clientId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun approveClientDeal(
        clientId: String,
        finalReply: String? = null
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/clients/$clientId"
        return try {
            val uid = requireUserId()
            require(clientId.isNotBlank()) { "Client ID is required." }
            val updates = mutableMapOf<String, Any>(
                "status" to ClientStatus.CLOSED_WON.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!finalReply.isNullOrBlank()) {
                updates["draftedReply"] = finalReply.trim().take(2000)
            }
            db.collection("users").document(uid).collection("clients").document(clientId)
                .update(updates)
                .await()
            incrementDailyCounter(uid, todayDateKey(), dealsDelta = 1L)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun completeClientDeal(clientId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/clients/$clientId"
        return try {
            val uid = requireUserId()
            require(clientId.isNotBlank()) { "Client ID is required." }
            db.collection("users").document(uid).collection("clients").document(clientId)
                .update(
                    mapOf(
                        "status" to ClientStatus.COMPLETED.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteClient(clientId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/clients/$clientId"
        return try {
            val uid = requireUserId()
            require(clientId.isNotBlank()) { "Client ID is required." }
            db.collection("users").document(uid).collection("clients").document(clientId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMemory(
        title: String,
        content: String,
        category: MemoryCategory = MemoryCategory.RULE
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        var docPath = "users/$currentUid/memories"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(100)
            val cleanContent = content.trim().take(1000)
            require(cleanTitle.isNotEmpty() && cleanContent.isNotEmpty()) {
                "Memory title and content are required."
            }
            val ref = db.collection("users").document(uid).collection("memories").document()
            docPath = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "category" to category.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$currentUid/memories/$memoryId"
        return try {
            val uid = requireUserId()
            require(memoryId.isNotBlank()) { "Memory ID is required." }
            db.collection("users").document(uid).collection("memories").document(memoryId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        role: ChatRole,
        text: String,
        brainUsed: String = "",
        voiceInput: Boolean = false
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        var docPath = "users/$currentUid/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(8000)
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }
            val ref = db.collection("users").document(uid).collection("messages").document()
            docPath = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "role" to role.name,
                    "text" to cleanText,
                    "brainUsed" to brainUsed.trim().take(60),
                    "voiceInput" to voiceInput,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$currentUid/messages"
        return try {
            val uid = requireUserId()
            val snap = db.collection("users").document(uid).collection("messages").get().await()
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun incrementDailyCounter(
        uid: String,
        dateKey: String,
        huntedDelta: Long = 0L,
        repliesDelta: Long = 0L,
        dealsDelta: Long = 0L
    ) {
        val statRef = db.collection("users").document(uid).collection("dailyStats").document(dateKey)
        try {
            val snap = statRef.get().await()
            if (snap.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (huntedDelta != 0L) {
                    val current = (snap.getLong("huntedCount") ?: 0L)
                    updates["huntedCount"] = (current + huntedDelta).coerceIn(0L, 10L)
                }
                if (repliesDelta != 0L) {
                    updates["repliesHandled"] = FieldValue.increment(repliesDelta)
                }
                if (dealsDelta != 0L) {
                    updates["dealsClosed"] = FieldValue.increment(dealsDelta)
                }
                statRef.update(updates).await()
            } else {
                statRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "huntedCount" to huntedDelta.coerceIn(0L, 10L),
                        "repliesHandled" to repliesDelta.coerceAtLeast(0L),
                        "dealsClosed" to dealsDelta.coerceAtLeast(0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, statRef.path)
        }
    }

    private fun unwrapAndLogFlowError(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) {
            throw error
        }
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        Log.w("JarvisFirestoreRepo", "Flow error on $path", error)
    }
}
