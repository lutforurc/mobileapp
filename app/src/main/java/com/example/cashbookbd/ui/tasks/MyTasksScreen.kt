package com.example.cashbookbd.ui.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.TodoBoard
import com.example.cashbookbd.data.repository.TodoItem
import com.example.cashbookbd.data.repository.TodoPerson
import com.example.cashbookbd.data.repository.UserTodoRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.PickerField
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// ---------------------------------------------------------------------------
// Vocabulary (the web's own — MyTasks.tsx)
// ---------------------------------------------------------------------------

/** The six sticky-note colours the web offers, verbatim. */
private val NOTE_COLORS = listOf("#FFE5B4", "#B4E5FF", "#FFB4E5", "#E5FFB4", "#FFE5D9", "#D9E5FF")

/** Which slice of the board is on show — a private scratchpad and a queue of
 *  work other people put there are different things to look at. */
private val FILTERS = listOf(
    "all" to "All",
    "mine" to "My own",
    "to_me" to "Assigned to me",
    "by_me" to "I assigned",
)

/** pending → in_progress → done → pending; one button walks a task forward. */
private fun nextStatus(status: String): String = when (status) {
    "pending" -> "in_progress"
    "in_progress" -> "done"
    else -> "pending"
}

private fun statusLabel(status: String): String = when (status) {
    "in_progress" -> "In Progress"
    "done" -> "Done"
    else -> "Pending"
}

/** The note's own colour; a bad hex falls back to the first swatch. */
private fun noteColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFFFFE5B4)
}

/** Ink on a pastel note is dark in BOTH themes — the paper does not darken. */
private val NoteInk = Color(0xFF1F2935)

/** Two letters standing in for a face the app does not have. */
private fun initials(name: String): String = name.trim()
    .split(Regex("""\s+"""))
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifBlank { "?" }

private fun today(): SimpleDate {
    val c = Calendar.getInstance()
    return SimpleDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

/** The form dialog's fields — the web's five, seeded from the row on edit. */
data class TodoFormState(
    /** The note being changed; null writes a new one. */
    val editing: TodoItem? = null,
    val title: String = "",
    val description: String = "",
    val dueDate: SimpleDate = today(),
    /** "HH:mm", or blank for no reminder — the web's midnight-means-none rule. */
    val reminderTime: String = "",
    val color: String = NOTE_COLORS.first(),
    /** "" = personal note; otherwise the assignee's id. */
    val assignedTo: String = "",
) {
    val canSave: Boolean get() = title.isNotBlank()
}

data class MyTasksUiState(
    val board: TodoBoard = TodoBoard(emptyList(), emptyList(), emptyList()),
    val people: List<TodoPerson> = emptyList(),
    val filter: String = "all",
    val fromDate: SimpleDate? = null,
    val toDate: SimpleDate? = null,
    /** True while a date-range search's answer is on screen. */
    val searching: Boolean = false,

    val isLoading: Boolean = true,
    /** A quieter load: the cards stay put while the new list arrives. */
    val isRefreshing: Boolean = false,
    /** The one note a request is in flight for — only its buttons go quiet. */
    val busyId: Long? = null,

    val form: TodoFormState? = null,
    val isSaving: Boolean = false,
    val pendingDelete: TodoItem? = null,
    val isDeleting: Boolean = false,

    val actionMessage: String? = null,
    val sessionExpired: Boolean = false,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * The Daily Todo List — the web's My Tasks board (db94f9f..9234d4e): sticky
 * notes bucketed Today/Upcoming (yesterday's leftovers are found by searching
 * a date range, not dragged onto the board every morning), a one-button status
 * walk, pinning, and handing a task to somebody else in the company. The
 * author owns what a task says; the assignee owns how far along it is — the
 * server enforces that split, this screen just avoids offering buttons that
 * would 422.
 */
class MyTasksViewModel(
    private val repository: UserTodoRepository,
    val meId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyTasksUiState())
    val uiState: StateFlow<MyTasksUiState> = _uiState.asStateFlow()

    init {
        load(silent = false)
        viewModelScope.launch {
            (repository.assignees() as? Resource.Success)?.let { result ->
                _uiState.update { it.copy(people = result.data) }
            }
        }
    }

    /** The full-screen loader belongs to the first load only; every load after
     *  it keeps the cards on screen and swaps them when the answer arrives. */
    fun load(silent: Boolean = true) {
        val state = _uiState.value
        _uiState.update {
            if (silent) it.copy(isRefreshing = true) else it.copy(isLoading = true)
        }
        viewModelScope.launch {
            val result = repository.fetchBoard(
                filter = state.filter,
                dateFrom = if (state.searching) state.fromDate?.toApi() else null,
                dateTo = if (state.searching) state.toDate?.toApi() else null,
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, board = result.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onFilter(key: String) {
        if (key == _uiState.value.filter) return
        _uiState.update { it.copy(filter = key) }
        load()
    }

    fun onFromDate(date: SimpleDate) = _uiState.update { it.copy(fromDate = date) }
    fun onToDate(date: SimpleDate) = _uiState.update { it.copy(toDate = date) }

    /** Either end on its own is a sensible question; both empty means "no
     *  search". Dates the wrong way round are swapped rather than refused. */
    fun runSearch() {
        val state = _uiState.value
        if (state.fromDate == null && state.toDate == null) return
        val from = state.fromDate
        val to = state.toDate
        val flipped = from != null && to != null && from.toApi() > to.toApi()
        _uiState.update {
            it.copy(
                searching = true,
                fromDate = if (flipped) to else from,
                toDate = if (flipped) from else to,
            )
        }
        load()
    }

    fun clearSearch() {
        _uiState.update { it.copy(searching = false, fromDate = null, toDate = null) }
        load()
    }

    // ---- The form dialog ----

    fun openNew() = _uiState.update { it.copy(form = TodoFormState()) }

    fun openEdit(todo: TodoItem) = _uiState.update {
        it.copy(
            form = TodoFormState(
                editing = todo,
                title = todo.title,
                description = todo.description,
                dueDate = parseDate(todo.dueDate) ?: today(),
                reminderTime = todo.reminderTime,
                color = todo.color,
                assignedTo = todo.assignedTo?.toString().orEmpty(),
            ),
        )
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun onFormChange(transform: (TodoFormState) -> TodoFormState) =
        _uiState.update { state -> state.copy(form = state.form?.let(transform)) }

    /** One save for both: closes only once the row is written — a failure
     *  leaves the dialog open with the typing still in it. */
    fun submitForm() {
        val state = _uiState.value
        val form = state.form ?: return
        if (!form.canSave || state.isSaving) return

        val body = JsonObject().apply {
            addProperty("title", form.title.trim())
            form.description.trim().let {
                if (it.isEmpty()) add("description", JsonNull.INSTANCE) else addProperty("description", it)
            }
            addProperty("due_date", form.dueDate.toApi())
            addProperty("color", form.color)
            // The reminder rides the due day; blank time means none at all.
            if (form.reminderTime.isBlank()) {
                add("reminder_time", JsonNull.INSTANCE)
            } else {
                addProperty("reminder_time", "${form.dueDate.toApi()} ${form.reminderTime}:00")
            }
            form.assignedTo.toLongOrNull()
                ?.let { addProperty("assigned_to", it) }
                ?: add("assigned_to", JsonNull.INSTANCE)
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val editing = form.editing
            val result = if (editing == null) repository.create(body) else repository.update(editing.id, body)
            when (result) {
                is Resource.Success -> {
                    val assignedName = form.assignedTo.toLongOrNull()
                        ?.let { id -> _uiState.value.people.firstOrNull { it.id == id }?.name }
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            form = null,
                            actionMessage = when {
                                assignedName != null && editing == null -> "Task assigned to $assignedName."
                                editing == null -> "Task added."
                                else -> "Task updated."
                            },
                        )
                    }
                    load()
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    // ---- Per-card actions ----

    /** A note keeps its section until the next load: completing an overdue
     *  task ticks it in place rather than yanking it out from under the
     *  finger. The saved row replaces it where it stands. */
    private fun patch(todo: TodoItem, changes: JsonObject) {
        if (_uiState.value.busyId != null) return
        _uiState.update { it.copy(busyId = todo.id) }
        viewModelScope.launch {
            when (val result = repository.update(todo.id, changes)) {
                is Resource.Success -> _uiState.update { state ->
                    val saved = result.data
                    fun swap(list: List<TodoItem>) = list.map { if (it.id == saved.id) saved else it }
                    state.copy(
                        busyId = null,
                        board = TodoBoard(
                            today = swap(state.board.today),
                            upcoming = swap(state.board.upcoming),
                            results = swap(state.board.results),
                        ),
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        busyId = null,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun advanceStatus(todo: TodoItem) {
        val next = nextStatus(todo.status)
        patch(todo, JsonObject().apply {
            addProperty("status", next)
            addProperty("is_completed", next == "done")
        })
    }

    fun togglePin(todo: TodoItem) =
        patch(todo, JsonObject().apply { addProperty("is_pinned", !todo.isPinned) })

    fun askDelete(todo: TodoItem) = _uiState.update { it.copy(pendingDelete = todo) }
    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val todo = _uiState.value.pendingDelete ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = repository.delete(todo.id)) {
                is Resource.Success -> _uiState.update { state ->
                    // Only the deleted note leaves; the rest stay where they are.
                    fun drop(list: List<TodoItem>) = list.filterNot { it.id == todo.id }
                    state.copy(
                        isDeleting = false,
                        pendingDelete = null,
                        board = TodoBoard(
                            today = drop(state.board.today),
                            upcoming = drop(state.board.upcoming),
                            results = drop(state.board.results),
                        ),
                        actionMessage = "Task deleted.",
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        pendingDelete = null,
                        actionMessage = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onActionMessageShown() = _uiState.update { it.copy(actionMessage = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    private fun parseDate(value: String): SimpleDate? =
        Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(value)?.let { m ->
            val (y, mo, d) = m.destructured
            SimpleDate(y.toInt(), mo.toInt(), d.toInt())
        }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val session = ServiceLocator.provideSessionManager(appContext).state.value
                MyTasksViewModel(
                    repository = ServiceLocator.provideUserTodoRepository(appContext),
                    meId = session.settings?.userId ?: 0L,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun MyTasksScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: MyTasksViewModel = viewModel(factory = MyTasksViewModel.provideFactory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (state.sessionExpired) {
        LaunchedEffect(Unit) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onActionMessageShown()
    }

    AuthenticatedShell(
        title = "My Tasks",
        currentRoute = Routes.MY_TASKS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }
                else -> TasksBoard(state = state, viewModel = viewModel, context = context)
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.form?.let { form ->
        TodoFormDialog(form = form, state = state, viewModel = viewModel, context = context)
    }

    state.pendingDelete?.let { todo ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete this task?") },
            text = { Text("\"${todo.title}\" will be deleted. This cannot be undone.") },
            confirmButton = {
                PrimaryButton(
                    text = "Delete",
                    onClick = viewModel::confirmDelete,
                    enabled = !state.isDeleting,
                    isLoading = state.isDeleting,
                    compact = true,
                )
            },
            dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::cancelDelete) },
        )
    }
}

@Composable
private fun TasksBoard(state: MyTasksUiState, viewModel: MyTasksViewModel, context: Context) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            // New Task first, then whose work is whose.
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(text = "New Task", onClick = viewModel::openNew, compact = true)
                if (state.isRefreshing) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FILTERS.forEach { (key, label) ->
                    FilterChip(
                        selected = state.filter == key,
                        onClick = { viewModel.onFilter(key) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // The board is today and ahead only — a past task is found by
            // asking for its dates.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                TaskDateField(
                    label = "From",
                    value = state.fromDate,
                    context = context,
                    onSelected = viewModel::onFromDate,
                    modifier = Modifier.weight(1f),
                )
                TaskDateField(
                    label = "To",
                    value = state.toDate,
                    context = context,
                    onSelected = viewModel::onToDate,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(text = "Search", onClick = viewModel::runSearch, compact = true)
            }
            if (state.searching) {
                Spacer(Modifier.height(6.dp))
                LinkButton(text = "Clear search — back to the board", onClick = viewModel::clearSearch)
            }
            Spacer(Modifier.height(16.dp))
        }

        val sections = if (state.searching) {
            listOf("Search Results" to state.board.results)
        } else {
            listOf("Today" to state.board.today, "Upcoming" to state.board.upcoming)
        }
        val allEmpty = sections.all { it.second.isEmpty() }

        if (allEmpty) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.searching) {
                            "Nothing due in that range."
                        } else {
                            "The board is clear. Tap New Task to write one."
                        },
                        color = MaterialTheme.appColors.textOnScreenMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        sections.forEach { (title, items) ->
            if (items.isEmpty()) return@forEach
            item(key = "header-$title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title.uppercase(Locale.US),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = AppFontWeight.SemiBold,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.appColors.primaryTint,
                    ) {
                        Text(
                            text = items.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            items.forEach { todo ->
                item(key = todo.id) {
                    TodoCard(
                        todo = todo,
                        busy = state.busyId == todo.id,
                        isAuthor = todo.userId == viewModel.meId,
                        onAdvance = { viewModel.advanceStatus(todo) },
                        onPin = { viewModel.togglePin(todo) },
                        onEdit = { viewModel.openEdit(todo) },
                        onDelete = { viewModel.askDelete(todo) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            item(key = "gap-$title") { Spacer(Modifier.height(10.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// One note
// ---------------------------------------------------------------------------

@Composable
private fun TodoCard(
    todo: TodoItem,
    busy: Boolean,
    /** Whether this user wrote the task, which decides what they may change. */
    isAuthor: Boolean,
    onAdvance: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDone = todo.status == "done"
    val isOverdue = !isDone && todo.dueDate.isNotBlank() && todo.dueDate < today().toApi()
    val paper = noteColor(todo.color)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = paper,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // The note's own colour, deepened — except a task somebody else is
            // involved in, which wears the brand blue so shared work is told
            // apart from a private note at a glance.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        if (todo.isAssigned) MaterialTheme.colorScheme.primary
                        else NoteInk.copy(alpha = 0.15f),
                    ),
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = AppFontWeight.SemiBold,
                        color = if (isDone) NoteInk.copy(alpha = 0.55f) else NoteInk,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (todo.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.appColors.warning,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (todo.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = todo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = NoteInk.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Whose card this is, said once: work you gave away names the
                // person who has it, work given to you names the person who asked.
                val person = if (isAuthor) todo.assignee else todo.assigner
                val prefix = if (isAuthor) "Assigned to" else "From"
                if (person != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = NoteInk.copy(alpha = 0.18f)) {
                            Text(
                                text = initials(person.name),
                                style = MaterialTheme.typography.labelSmall,
                                color = NoteInk,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "$prefix ${person.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NoteInk.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The quiet chips: the day and the hour. Overdue is the loud
                    // exception — filled danger, white ink.
                    NoteChip(
                        text = todo.dueDate.toDisplayDate(),
                        loud = isOverdue,
                        loudColor = MaterialTheme.appColors.danger,
                    )
                    if (todo.reminderTime.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        NoteChip(text = todo.reminderTime, loud = false, loudColor = Color.Unspecified)
                    }
                    // Pending is the resting state and says nothing worth the room.
                    if (todo.status != "pending") {
                        Spacer(Modifier.width(6.dp))
                        NoteChip(
                            text = statusLabel(todo.status),
                            loud = true,
                            loudColor = if (isDone) MaterialTheme.appColors.success else MaterialTheme.appColors.info,
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    // One button walks the task forward: start it, finish it,
                    // reopen it.
                    NoteAction(
                        icon = if (todo.status == "pending") Icons.Filled.PlayArrow else Icons.Filled.Check,
                        description = when (todo.status) {
                            "pending" -> "Start working"
                            "in_progress" -> "Mark done"
                            else -> "Reopen"
                        },
                        tint = when (todo.status) {
                            "in_progress" -> MaterialTheme.appColors.info
                            "done" -> MaterialTheme.appColors.success
                            else -> MaterialTheme.appColors.success
                        },
                        enabled = !busy,
                        onClick = onAdvance,
                    )
                    NoteAction(
                        icon = Icons.Filled.Star,
                        description = if (todo.isPinned) "Unpin" else "Pin",
                        tint = MaterialTheme.appColors.warning,
                        enabled = !busy,
                        onClick = onPin,
                    )
                    // What the task says belongs to whoever wrote it — a button
                    // that 422s on tap is worse than no button.
                    if (isAuthor) {
                        NoteAction(
                            icon = Icons.Filled.Edit,
                            description = "Edit",
                            tint = MaterialTheme.appColors.info,
                            enabled = !busy,
                            onClick = onEdit,
                        )
                        NoteAction(
                            icon = Icons.Filled.Delete,
                            description = "Delete",
                            tint = MaterialTheme.appColors.danger,
                            enabled = !busy,
                            onClick = onDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteChip(text: String, loud: Boolean, loudColor: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (loud) loudColor else NoteInk.copy(alpha = 0.10f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (loud) Color.White else NoteInk,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun NoteAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TaskDateField(
    label: String,
    value: SimpleDate?,
    context: Context,
    onSelected: (SimpleDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerField(
        label = label,
        value = value?.toDisplay().orEmpty(),
        placeholder = "dd/mm/yyyy",
        trailingIcon = Icons.Filled.DateRange,
        modifier = modifier,
        onClick = {
            val seed = value ?: today()
            DatePickerDialog(
                context,
                { _, y, m, d -> onSelected(SimpleDate(y, m + 1, d)) },
                seed.year,
                seed.month - 1,
                seed.day,
            ).show()
        },
    )
}

/** "2026-08-17" → "17/08/2026" for the date chip; anything else verbatim. */
private fun String.toDisplayDate(): String =
    Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(this)
        ?.let { m -> "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}" }
        ?: this

// ---------------------------------------------------------------------------
// The form dialog — one for both writing a note and changing one
// ---------------------------------------------------------------------------

@Composable
private fun TodoFormDialog(
    form: TodoFormState,
    state: MyTasksUiState,
    viewModel: MyTasksViewModel,
    context: Context,
) {
    val isNew = form.editing == null
    AlertDialog(
        onDismissRequest = viewModel::closeForm,
        title = { Text(if (isNew) "New Task" else "Edit Task") },
        text = {
            // The body can outgrow small screens; scroll rather than clip.
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                AppTextField(
                    value = form.title,
                    onValueChange = { value -> viewModel.onFormChange { it.copy(title = value) } },
                    label = "What do you want to do?",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = form.description,
                    onValueChange = { value -> viewModel.onFormChange { it.copy(description = value) } },
                    label = "Anything worth remembering (optional)",
                    multiline = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        label = "Due Date",
                        value = form.dueDate.toDisplay(),
                        placeholder = "dd/mm/yyyy",
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    viewModel.onFormChange { it.copy(dueDate = SimpleDate(y, m + 1, d)) }
                                },
                                form.dueDate.year,
                                form.dueDate.month - 1,
                                form.dueDate.day,
                            ).show()
                        },
                    )
                    PickerField(
                        label = "Reminder",
                        value = form.reminderTime,
                        placeholder = "None",
                        trailingIcon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val parts = form.reminderTime.split(":")
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    viewModel.onFormChange {
                                        it.copy(
                                            reminderTime = String.format(Locale.US, "%02d:%02d", hour, minute),
                                        )
                                    }
                                },
                                parts.getOrNull(0)?.toIntOrNull() ?: 9,
                                parts.getOrNull(1)?.toIntOrNull() ?: 0,
                                true,
                            ).show()
                        },
                    )
                }
                if (form.reminderTime.isNotBlank()) {
                    LinkButton(
                        text = "Remove reminder",
                        onClick = { viewModel.onFormChange { it.copy(reminderTime = "") } },
                    )
                }
                Spacer(Modifier.height(10.dp))
                AppSelectDropdown(
                    label = "Assign to",
                    options = listOf(SelectorOption("", "Myself (personal note)")) +
                        state.people.map { SelectorOption(it.id.toString(), it.name) },
                    selected = state.people.firstOrNull { it.id.toString() == form.assignedTo }
                        ?.let { SelectorOption(it.id.toString(), it.name) }
                        ?: SelectorOption("", "Myself (personal note)"),
                    onSelected = { option -> viewModel.onFormChange { it.copy(assignedTo = option.id) } },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Handing this to somebody else puts it on their board too.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Colour",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NOTE_COLORS.forEach { swatch ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = noteColor(swatch),
                            border = if (form.color == swatch) {
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { viewModel.onFormChange { it.copy(color = swatch) } },
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = if (isNew) "Add Task" else "Save",
                onClick = viewModel::submitForm,
                enabled = form.canSave && !state.isSaving,
                isLoading = state.isSaving,
                compact = true,
            )
        },
        dismissButton = { LinkButton(text = "Cancel", onClick = viewModel::closeForm) },
    )
}

