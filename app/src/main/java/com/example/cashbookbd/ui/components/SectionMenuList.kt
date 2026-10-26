package com.example.cashbookbd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.navigation.MenuGroups
import com.example.cashbookbd.ui.theme.AppFontWeight

/**
 * A menu group's heading inside a section-home screen — the web sidebar's
 * `divider:<key>:<Label>` drawn as a small uppercase label over a hairline rule.
 */
@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = AppFontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The one body every section-home screen draws: its already-permission-filtered
 * [items] under the web's [MenuGroups] headings, or — once the user has
 * arranged the menu ([arranged]) — in their saved flat order, exactly as the
 * web switches between its `SIDEBAR_SUB_DEFAULTS` and the user's own order.
 */
@Composable
fun <T> SectionMenuList(
    menuId: String,
    items: List<T>,
    keyOf: (T) -> String,
    arranged: Boolean,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val rows: List<MenuGroups.Row<T>> = remember(menuId, items, arranged) {
        if (arranged) items.map { MenuGroups.Row.Entry(it) }
        else MenuGroups.rows(menuId, items, keyOf)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEachIndexed { index, row ->
            when (row) {
                is MenuGroups.Row.Heading -> item(key = "h$index") { SectionHeading(row.title) }
                is MenuGroups.Row.Entry -> item(key = "i$index") { itemContent(row.item) }
            }
        }
    }
}
