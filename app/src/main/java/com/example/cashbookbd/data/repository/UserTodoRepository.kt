package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

/** Somebody a task involves: its author, assignee or assigner. */
data class TodoPerson(
    val id: Long,
    val name: String,
)

/**
 * One note on the board, as `user-todos` returns it. Two people can have a
 * claim on a row — the author (`user_id`) and whoever it was handed to
 * (`assigned_to`); the author owns what the task *says*, the assignee owns how
 * far along it *is*, and the server refuses anything else with a 422.
 */
data class TodoItem(
    val id: Long,
    val userId: Long,
    val title: String,
    val description: String,
    /** yyyy-MM-dd — a DATE column server-side. */
    val dueDate: String,
    /** The sticky note's own colour, as a hex string. */
    val color: String,
    val isPinned: Boolean,
    /** pending | in_progress | done. */
    val status: String,
    /** "HH:mm" cut from the reminder timestamp, or blank for no reminder. */
    val reminderTime: String,
    val assignedTo: Long?,
    /** True once the task belongs to somebody other than its author. */
    val isAssigned: Boolean,
    val assignee: TodoPerson?,
    val assigner: TodoPerson?,
)

/** What the account-menu badge counts (`user-todos/summary`). */
data class TodoSummary(
    /** Tasks handed to this user that they have not opened the board for yet. */
    val assignedNew: Int,
    /** Every open task somebody else put on their board. */
    val assignedOpen: Int,
)

/** The board as the server buckets it; results is a date-range search's list. */
data class TodoBoard(
    val today: List<TodoItem>,
    val upcoming: List<TodoItem>,
    val results: List<TodoItem>,
)

/**
 * The Daily Todo List ("My Tasks", api 9c18e8e8..5c12e239). Personal — no
 * permission gates any of it; the server scopes every read and write to the
 * rows this user wrote or was handed.
 */
class UserTodoRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * The board. With a date range the server answers `results` alone (newest
     * first) and leaves today/upcoming empty; without one it answers the
     * today/upcoming split and an empty `results` — exactly the web's shape.
     */
    suspend fun fetchBoard(
        filter: String,
        dateFrom: String?,
        dateTo: String?,
    ): Resource<TodoBoard> = withContext(ioDispatcher) {
        guard {
            val params = buildMap {
                put("filter", filter)
                dateFrom?.takeIf { it.isNotBlank() }?.let { put("date_from", it) }
                dateTo?.takeIf { it.isNotBlank() }?.let { put("date_to", it) }
            }
            val response = api.get("user-todos", params)
            checkHttp(response)?.let { return@guard it }
            val data = response.body().payloadObject()
            Resource.Success(
                TodoBoard(
                    today = data.itemsAt("today"),
                    upcoming = data.itemsAt("upcoming"),
                    results = data.itemsAt("results"),
                ),
            )
        }
    }

    /**
     * The badge counts — work handed over is worthless if nobody notices it
     * arrive, and nobody keeps a todo screen open all day. Opening the board
     * is what marks them seen (the server stamps seen_at on the index read).
     */
    suspend fun summary(): Resource<TodoSummary> = withContext(ioDispatcher) {
        guard {
            val response = api.get("user-todos/summary", emptyMap())
            checkHttp(response)?.let { return@guard it }
            val data = response.body().payloadObject()
            Resource.Success(
                TodoSummary(
                    assignedNew = data?.longOr("assigned_new")?.toInt() ?: 0,
                    assignedOpen = data?.longOr("assigned_open")?.toInt() ?: 0,
                ),
            )
        }
    }

    /** Everyone a task may be handed to: the company, minus this user. */
    suspend fun assignees(): Resource<List<TodoPerson>> = withContext(ioDispatcher) {
        guard {
            val response = api.get("user-todos/assignees", emptyMap())
            checkHttp(response)?.let { return@guard it }
            val rows = response.body().payloadArrayItems()
            Resource.Success(rows)
        }
    }

    /** Writes a new note; the server hands the saved row back. */
    suspend fun create(body: JsonObject): Resource<TodoItem> = withContext(ioDispatcher) {
        guard {
            val response = api.postObjectRaw("user-todos", body)
            checkHttp(response)?.let { return@guard it }
            response.body().payloadObject()?.toTodo()
                ?.let { Resource.Success(it) }
                ?: Resource.Error("The task did not save. Please try again.")
        }
    }

    /**
     * Patches one note — a pin flip, a status step, or the author's full edit.
     * A 422 carries the server's own refusal ("Only the person who created
     * this task can change its details."), surfaced verbatim.
     */
    suspend fun update(id: Long, changes: JsonObject): Resource<TodoItem> = withContext(ioDispatcher) {
        guard {
            val response = api.patchObjectRaw("user-todos/$id", changes)
            checkHttp(response)?.let { return@guard it }
            response.body().payloadObject()?.toTodo()
                ?.let { Resource.Success(it) }
                ?: Resource.Error("The change did not save. Please try again.")
        }
    }

    /** Only the author may delete; the server 403s anybody else with a reason. */
    suspend fun delete(id: Long): Resource<Unit> = withContext(ioDispatcher) {
        guard {
            val response = api.delete("user-todos/$id")
            checkHttp(response)?.let { return@guard it }
            Resource.Success(Unit)
        }
    }

    // -----------------------------------------------------------------------
    // Plumbing
    // -----------------------------------------------------------------------

    private inline fun <T> guard(block: () -> Resource<T>): Resource<T> = try {
        block()
    } catch (e: IOException) {
        Resource.Error("No internet connection. Please check your network and try again.")
    } catch (e: HttpException) {
        if (e.code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        }
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }

    /**
     * Maps a bad status to an error, reading the server's own message out of
     * the error body first — a Laravel 422 ({message, errors}) or the delete's
     * 403 both say exactly why. Null when the response is fine.
     */
    private fun checkHttp(response: Response<JsonElement>): Resource.Error? {
        if (response.code() == HTTP_UNAUTHORIZED) {
            return Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        }
        if (response.isSuccessful) return null
        val message = try {
            response.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { raw ->
                com.google.gson.JsonParser.parseString(raw)
                    .takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("message")?.takeUnless { it.isJsonNull }?.asString
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
        if (message != null) return Resource.Error(message)
        return if (response.code() == HTTP_FORBIDDEN) {
            Resource.Error("You do not have permission for this action.")
        } else {
            Resource.Error("Server error (${response.code()}). Please try again later.")
        }
    }

    /** foundData wraps twice: the payload sits at data.data. */
    private fun JsonElement?.payloadObject(): JsonObject? = this
        ?.takeIf { it.isJsonObject }?.asJsonObject
        ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement?.payloadArrayItems(): List<TodoPerson> = this
        ?.takeIf { it.isJsonObject }?.asJsonObject
        ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        ?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            TodoPerson(
                id = o.longOr("id") ?: return@mapNotNull null,
                name = o.text("name").orEmpty(),
            )
        }
        .orEmpty()

    private fun JsonObject?.itemsAt(key: String): List<TodoItem> = this
        ?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.toTodo() }
        .orEmpty()

    private fun JsonObject.toTodo(): TodoItem? {
        val id = longOr("id") ?: return null
        return TodoItem(
            id = id,
            userId = longOr("user_id") ?: 0L,
            title = text("title").orEmpty(),
            description = text("description").orEmpty(),
            dueDate = text("due_date").orEmpty().take(10),
            color = text("color").orEmpty().ifBlank { "#FFE5B4" },
            isPinned = boolOr("is_pinned"),
            status = text("status").orEmpty().ifBlank {
                if (boolOr("is_completed")) "done" else "pending"
            },
            // "2026-08-17 14:30:00" or ISO — the clock face is all a card shows.
            reminderTime = text("reminder_time").orEmpty()
                .let { Regex("""[T ](\d{2}:\d{2})""").find(it)?.groupValues?.get(1).orEmpty() },
            assignedTo = longOr("assigned_to"),
            isAssigned = boolOr("is_assigned"),
            assignee = personAt("assignee"),
            assigner = personAt("assigner"),
        )
    }

    private fun JsonObject.personAt(key: String): TodoPerson? {
        val o = get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return TodoPerson(
            id = o.longOr("id") ?: return null,
            name = o.text("name").orEmpty(),
        )
    }

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.longOr(key: String): Long? =
        text(key)?.toDoubleOrNull()?.toLong()

    /** Reads 1/0, "1"/"0" or true/false alike. */
    private fun JsonObject.boolOr(key: String): Boolean {
        val raw = text(key) ?: return false
        return raw.equals("true", ignoreCase = true) || raw.toDoubleOrNull()?.let { it != 0.0 } == true
    }
}
