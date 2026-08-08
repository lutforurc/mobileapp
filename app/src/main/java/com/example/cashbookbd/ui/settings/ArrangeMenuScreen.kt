package com.example.cashbookbd.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.data.repository.MENU_DIVIDER_LABEL_MAX
import com.example.cashbookbd.data.repository.MenuPreferences
import com.example.cashbookbd.data.repository.MenuPreferencesRepository
import com.example.cashbookbd.data.repository.applyMenuOrder
import com.example.cashbookbd.data.repository.isMenuDivider
import com.example.cashbookbd.data.repository.makeMenuDividerId
import com.example.cashbookbd.data.repository.menuDividerKey
import com.example.cashbookbd.data.repository.menuDividerLabel
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.DrawerMenus
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.theme.AppFontWeight

/**
 * Where a user arranges their own drawer — the web's Arrange Menu page.
 *
 * Same rules: changes save as they are made (there is nothing to Save — the
 * arrangement is already stored, against the user, so it is the same on any
 * device they log in from, web included). Menus move with the arrows and hide
 * with the eye; a divider is named in place, and deleted rather than hidden.
 */
class ArrangeMenuViewModel(
    private val repository: MenuPreferencesRepository,
) : ViewModel() {

    val prefs = repository.state

    init {
        repository.refresh()
        repository.refreshSub()
    }

    /** Every arrangeable menu plus the user's dividers, in the saved order. */
    fun orderedIds(current: MenuPreferences): List<String> =
        applyMenuOrder(DrawerMenus.all.map { it.id }, current.order)

    fun move(id: String, up: Boolean) {
        val current = prefs.value
        val ids = orderedIds(current).toMutableList()
        val from = ids.indexOf(id)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to < 0 || to >= ids.size) return
        ids[from] = ids[to].also { ids[to] = ids[from] }
        repository.update(current.copy(order = ids))
    }

    fun toggleHidden(id: String) {
        val current = prefs.value
        val hidden = if (id in current.hidden) current.hidden - id else current.hidden + id
        repository.update(current.copy(hidden = hidden))
    }

    /** An empty name is allowed — a plain rule with no heading. Added on top. */
    fun addDivider(label: String) {
        val current = prefs.value
        repository.update(
            current.copy(order = listOf(makeMenuDividerId(label.trim())) + orderedIds(current))
        )
    }

    /** Renaming rewrites the id (the label lives inside it); the key survives. */
    fun renameDivider(id: String, label: String) {
        val next = makeMenuDividerId(label.trim(), menuDividerKey(id))
        if (next == id) return
        val current = prefs.value
        repository.update(
            MenuPreferences(
                order = orderedIds(current).map { if (it == id) next else it },
                hidden = current.hidden.map { if (it == id) next else it },
            )
        )
    }

    fun removeDivider(id: String) {
        val current = prefs.value
        repository.update(
            MenuPreferences(
                order = orderedIds(current).filter { it != id },
                hidden = current.hidden.filter { it != id },
            )
        )
    }

    fun reset() = repository.update(MenuPreferences())

    // ---- The second panel: entries INSIDE one menu (sidebar-sub) ----

    val subPrefs = repository.subState

    /** One menu's entries in the user's order, as (mobileKey, webId, title). */
    fun subEntries(menuId: String): List<Triple<String, String, String>> {
        val table = com.example.cashbookbd.navigation.WebMenuIds.forMenu(menuId)
        val titles = subTitles(menuId)
        val keys = com.example.cashbookbd.navigation.WebMenuIds.arrange(
            menuId, titles.keys.toList(), subPrefs.value,
        ) { it }
        return keys.mapNotNull { key ->
            val webId = table[key] ?: return@mapNotNull null
            Triple(key, webId, titles[key] ?: key)
        }
    }

    private fun subTitles(menuId: String): Map<String, String> = when (menuId) {
        "reports" -> com.example.cashbookbd.report.ReportMenu.all
            .filter { it.key in com.example.cashbookbd.navigation.WebMenuIds.forMenu("reports") }
            .associate { it.key to it.title }
        "transaction" -> com.example.cashbookbd.transaction.TransactionMenu.all
            .associate { it.key to it.title }
        "admin" -> com.example.cashbookbd.admin.AdminMenu.all
            .associate { it.key to it.title }
        else -> emptyMap()
    }

    fun subIsHidden(menuId: String, webId: String): Boolean =
        "$menuId/$webId" in subPrefs.value.hidden

    fun subToggleHidden(menuId: String, webId: String) {
        val id = "$menuId/$webId"
        val current = subPrefs.value
        val hidden = if (id in current.hidden) current.hidden - id else current.hidden + id
        repository.updateSub(current.copy(hidden = hidden))
    }

    /** Swaps within the menu and writes the whole menu's stretch back. */
    fun subMove(menuId: String, webId: String, up: Boolean) {
        val current = subPrefs.value
        val ids = subEntries(menuId).map { it.second }.toMutableList()
        val from = ids.indexOf(webId)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to < 0 || to >= ids.size) return
        ids[from] = ids[to].also { ids[to] = ids[from] }
        // The web's writeGroup: every other menu's ids stay untouched.
        val prefix = "$menuId/"
        val others = current.order.filter { !it.startsWith(prefix) }
        repository.updateSub(current.copy(order = others + ids.map { "$prefix$it" }))
    }

    fun subReset(menuId: String) {
        val prefix = "$menuId/"
        val current = subPrefs.value
        repository.updateSub(
            MenuPreferences(
                order = current.order.filter { !it.startsWith(prefix) },
                hidden = current.hidden.filter { !it.startsWith(prefix) },
            )
        )
    }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                ArrangeMenuViewModel(
                    repository = ServiceLocator.provideMenuPreferencesRepository(
                        context.applicationContext,
                    ),
                )
            }
        }
    }
}

@Composable
fun ArrangeMenuScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArrangeMenuViewModel = viewModel(
        factory = ArrangeMenuViewModel.provideFactory(LocalContext.current),
    ),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    var newDivider by rememberSaveable { mutableStateOf("") }

    val ordered = viewModel.orderedIds(prefs)

    AuthenticatedShell(
        title = "Arrange Menu",
        currentRoute = Routes.ADMIN,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Move a menu with the arrows, hide anything you never " +
                    "open. This is yours alone and follows you to the web too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AppTextField(
                value = newDivider,
                onValueChange = { newDivider = it.take(MENU_DIVIDER_LABEL_MAX) },
                label = "Divider name (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    // Named if you type a name, a plain rule if you do not.
                    text = if (newDivider.isBlank()) "Add line" else "Add divider",
                    onClick = {
                        viewModel.addDivider(newDivider)
                        newDivider = ""
                    },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Reset everything",
                    onClick = viewModel::reset,
                    modifier = Modifier.weight(1f),
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ordered.forEachIndexed { index, id ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        MenuRow(
                            id = id,
                            index = index,
                            lastIndex = ordered.lastIndex,
                            hidden = id in prefs.hidden,
                            viewModel = viewModel,
                        )
                    }
                }
            }

            // ---- The web page's second panel: inside one menu ----
            val subPrefs by viewModel.subPrefs.collectAsStateWithLifecycle()
            var selectedMenu by rememberSaveable { mutableStateOf("reports") }
            Text(
                text = "Inside a menu",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = AppFontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("reports" to "Reports", "transaction" to "Transaction", "admin" to "Admin")
                    .forEach { (id, label) ->
                        SecondaryButton(
                            text = if (selectedMenu == id) "$label ✓" else label,
                            onClick = { selectedMenu = id },
                            compact = true,
                        )
                    }
            }
            // subPrefs read keeps this block recomposing on every change.
            @Suppress("UNUSED_EXPRESSION") subPrefs
            val entries = viewModel.subEntries(selectedMenu)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    entries.forEachIndexed { index, (key, webId, title) ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        val hidden = viewModel.subIsHidden(selectedMenu, webId)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hidden) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.subMove(selectedMenu, webId, up = true) },
                                enabled = index > 0,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = { viewModel.subMove(selectedMenu, webId, up = false) },
                                enabled = index < entries.lastIndex,
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                            }
                            SecondaryButton(
                                text = if (hidden) "Show" else "Hide",
                                onClick = { viewModel.subToggleHidden(selectedMenu, webId) },
                                compact = true,
                            )
                        }
                    }
                }
            }
            SecondaryButton(
                text = "Reset this menu",
                onClick = { viewModel.subReset(selectedMenu) },
                compact = true,
            )
        }
    }
}

@Composable
private fun MenuRow(
    id: String,
    index: Int,
    lastIndex: Int,
    hidden: Boolean,
    viewModel: ArrangeMenuViewModel,
) {
    val divider = isMenuDivider(id)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Menu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
        )

        if (divider) {
            // A divider is edited in place — its name is all there is to change.
            // The draft commits when focus leaves, so a keystroke is not a save
            // (renaming rewrites the id).
            var draft by remember(id) { mutableStateOf(menuDividerLabel(id)) }
            AppTextField(
                value = draft,
                onValueChange = { draft = it.take(MENU_DIVIDER_LABEL_MAX) },
                label = "Divider",
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (!it.isFocused) viewModel.renameDivider(id, draft) },
            )
        } else {
            Text(
                text = DrawerMenus.titleOf(id) ?: id,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = AppFontWeight.Normal,
                color = if (hidden) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
        }

        IconButton(onClick = { viewModel.move(id, up = true) }, enabled = index > 0) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = { viewModel.move(id, up = false) }, enabled = index < lastIndex) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
        }
        if (divider) {
            // A divider in the way is deleted, not hidden — nothing is behind it.
            IconButton(onClick = { viewModel.removeDivider(id) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove divider",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            SecondaryButton(
                text = if (hidden) "Show" else "Hide",
                onClick = { viewModel.toggleHidden(id) },
                compact = true,
            )
        }
    }
}
