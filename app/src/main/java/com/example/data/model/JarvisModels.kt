package com.example.data.model

import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class WorkType(val label: String, val defaultPriceUsd: Int) {
    VIDEO_EDITING("Video Editing", 30),
    DESIGNING("Designing", 20),
    WEBSITE("Website", 80);

    companion object {
        fun fromRaw(raw: String?): WorkType =
            entries.firstOrNull { it.name == raw } ?: WEBSITE
    }
}

enum class ClientStatus(val label: String, val romanLabel: String) {
    HUNTED("Hunted", "Naya Lead"),
    PITCHED("Pitched", "Message Bheja"),
    IN_DISCUSSION("In Discussion", "Baat Chal Rahi"),
    AWAITING_APPROVAL("Awaiting Approval", "Haan Ka Intezar"),
    CLOSED_WON("Closed Won", "Deal Final"),
    COMPLETED("Completed", "Kaam Mukammal");

    companion object {
        fun fromRaw(raw: String?): ClientStatus =
            entries.firstOrNull { it.name == raw } ?: HUNTED
    }
}

enum class MemoryCategory(val label: String) {
    RULE("Rule"),
    PREFERENCE("Preference"),
    CLIENT_NOTE("Client Note"),
    SKILL("Skill");

    companion object {
        fun fromRaw(raw: String?): MemoryCategory =
            entries.firstOrNull { it.name == raw } ?: RULE
    }
}

enum class ChatRole {
    USER,
    ASSISTANT;

    companion object {
        fun fromRaw(raw: String?): ChatRole =
            entries.firstOrNull { it.name == raw } ?: ASSISTANT
    }
}

@Keep
@IgnoreExtraProperties
data class ClientLead(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val workType: String = WorkType.WEBSITE.name,
    val priceUsd: Long = 80L,
    val platform: String = "Google",
    val contactHandle: String = "",
    val status: String = ClientStatus.HUNTED.name,
    val outreachMessage: String = "",
    val lastClientMessage: String = "",
    val draftedReply: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun workTypeEnum(): WorkType = WorkType.fromRaw(workType)
    fun statusEnum(): ClientStatus = ClientStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class MemoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = MemoryCategory.RULE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): MemoryCategory = MemoryCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = ChatRole.USER.name,
    val text: String = "",
    val brainUsed: String = "",
    val voiceInput: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun roleEnum(): ChatRole = ChatRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class DailyStat(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val huntedCount: Long = 0L,
    val repliesHandled: Long = 0L,
    val dealsClosed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class HuntedLeadCandidate(
    val clientName: String,
    val platform: String,
    val contactHandle: String,
    val workType: WorkType,
    val priceUsd: Int,
    val whyGoodFit: String,
    val personalizedEnglishPitch: String
)

data class ClientReplyAnalysis(
    val detectedWorkType: WorkType,
    val quotedPriceUsd: Int,
    val clientAgreed: Boolean,
    val romanUrduSummary: String,
    val englishReply: String
)

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}
