package com.example.cashbookbd.ui.invoice

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jAsArr
import com.example.cashbookbd.core.jAsObj
import com.example.cashbookbd.core.jObjects
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.LinkButton
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

/*
 * What the Tiles and Sanitary screens share: the branch facts that pick them
 * (`user/current-branch`'s is_tiles_and_sanitary — the web's SalesIndex reads
 * the flag, not the inventory system, since such a branch runs Trading's id 4),
 * the reference picker, and the "Total Tk." working under the buttons.
 */

/** The current branch as `user/current-branch` describes it. */
data class CurrentBranchInfo(
    val id: Long?,
    val isTilesAndSanitary: Boolean,
    val haveWarehouse: Boolean,
    val deductStockOnDelivery: Boolean,
    /** The branch's own names for its four handwritten figures (blank = standard). */
    val labels: Map<String, String>,
    /** The trade's name ("Tiles and Sanitary"), from `business_type` or the branch name. */
    val businessType: String = "",
    val name: String = "",
) {
    fun label(key: String, fallback: String) = labels[key]?.takeIf { it.isNotBlank() } ?: fallback

    /** The flag, or — failing that — the trade's own name. */
    val isTilesBranch: Boolean
        get() = isTilesAndSanitary || TilesBranch.matches(businessType) || TilesBranch.matches(name)
}

/**
 * Whether the branch is a Tiles and Sanitary shop — asked of EVERY signal the
 * app holds, so a Tiles branch is never mistaken for a Trading one.
 *
 * The web reads a single flag off `user/current-branch`; the phone kept the same
 * one signal, and if that one read was missing the four Tiles screens (Cash
 * Received/Payment, Sales/Purchase Invoice) silently fell back to the general
 * forms. get-settings carries the same `Branch::find()` row, so the flag and the
 * trade's NAME are already in hand without another request — and the server
 * itself recognises the trade by name, never by an auto-increment business type.
 */
object TilesBranch {
    /** The trade's name, however the server spells it ("Tiles and Sanitary"). */
    fun matches(name: String?): Boolean {
        val n = name?.lowercase().orEmpty()
        return n.contains("tiles") && n.contains("sanitary")
    }

    /** True when any signal says this is a Tiles and Sanitary branch. */
    fun isTiles(settings: com.example.cashbookbd.session.Settings?, branch: CurrentBranchInfo?): Boolean =
        settings?.isTilesAndSanitary == true ||
            branch?.isTilesAndSanitary == true ||
            matches(settings?.branchBusinessType) ||
            branch?.isTilesBranch == true
}

private fun JsonObject.flag(key: String): Boolean {
    val v = get(key) ?: return false
    if (v.isJsonNull) return false
    if (v.isJsonPrimitive && v.asJsonPrimitive.isBoolean) return v.asBoolean
    val s = v.asString.trim().lowercase()
    return s == "true" || s.toDoubleOrNull() == 1.0
}

/** A field that may arrive as text or as an object (its `name`), like `business_type`. */
private fun JsonObject.nameOrText(key: String): String {
    val el = get(key) ?: return ""
    return when {
        el.isJsonPrimitive -> el.asString.trim()
        el.isJsonObject -> el.asJsonObject.jStr("name")
        else -> ""
    }
}

/** One read per session; the branch switch clears the dashboard cache, this too. */
object CurrentBranchCache {
    @Volatile
    var value: CurrentBranchInfo? = null

    /**
     * Drop the cached branch. Called on logout and on a working-branch switch --
     * the next company (or the login screen) must not inherit the branch the
     * last session was reading from.
     */
    fun clear() {
        value = null
    }

    suspend fun load(api: WebApi, force: Boolean = false): CurrentBranchInfo? {
        if (!force) value?.let { return it }
        val body = (api.get("user/current-branch") as? Resource.Success)?.data ?: return value
        val o = body.jPayload()
        // Kept for the next occurrence of a mis-detected branch: this is the
        // payload the four Tiles screens are chosen from.
        if (BuildConfig.DEBUG) Log.d("CurrentBranch", "raw: $body")
        val info = CurrentBranchInfo(
            id = o.jStr("id").toLongOrNull(),
            isTilesAndSanitary = o.flag("is_tiles_and_sanitary"),
            haveWarehouse = o.flag("have_warehouse"),
            deductStockOnDelivery = o.flag("deduct_stock_on_delivery"),
            labels = listOf(
                "manual_voucher_no_label", "manual_voucher_date_label",
                "manual_challan_no_label", "manual_challan_date_label",
            ).associateWith { o.jStr(it) },
            businessType = o.nameOrText("business_type"),
            name = o.jStr("name"),
        )
        value = info
        return info
    }
}

/** A warehouse with the facts the single-godown rule needs. */
data class TilesWarehouse(val id: String, val name: String, val branchId: Long?, val isDefault: Boolean)

/**
 * The web's useTilesWarehouseField: a Tiles branch without "Have Warehouse"
 * keeps one godown, so the picker hides and every line goes to the branch's
 * own default (or first) godown.
 */
suspend fun loadTilesWarehouses(api: WebApi): List<TilesWarehouse> {
    val body = (api.get("active/warehouse") as? Resource.Success)?.data ?: return emptyList()
    val arr = body.get("data").jAsArr() ?: body.get("data").jAsObj()?.get("data").jAsArr()
    return arr?.jObjects().orEmpty().map {
        TilesWarehouse(
            id = it.jStr("id"),
            name = it.jStr("name"),
            branchId = it.jStr("branch_id").toLongOrNull(),
            isDefault = it.jStr("is_default") == "1",
        )
    }
}

/** The four terms and the answer, one to a line, every term shown (nought too). */
@Composable
fun TilesTotalPanel(terms: List<Pair<String, Double>>, total: Double) {
    fun money(v: Double) = AmountFormat.format(v).let { if (it == "-") "0" else it }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        terms.forEach { (label, amount) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = AppFontWeight.SemiBold)
                Text(money(amount), style = MaterialTheme.typography.bodySmall, fontWeight = AppFontWeight.SemiBold)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total Tk.", fontWeight = AppFontWeight.Bold)
            Text(money(total), fontWeight = AppFontWeight.Bold)
        }
    }
}

/**
 * Who recommended this sale (web ReferrerPickerModal): pick from `referrers`,
 * or add a new one and pick it at once. Remove takes the reference off.
 */
@Composable
fun ReferrerPickerDialog(selectedId: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val api = remember { ServiceLocator.provideWebApi(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<SelectorOption>>(emptyList()) }
    var picked by remember { mutableStateOf(selectedId) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    suspend fun load() {
        val body = (api.get("referrers") as? Resource.Success)?.data
        val list = body?.jPayload()?.get("rows").jAsArr() ?: body?.get("data").jAsObj()?.get("rows").jAsArr()
        // Retired referrers stay in the history but are not offered at the counter.
        rows = list?.jObjects().orEmpty().filter { it.jStr("status") == "1" }.map {
            val m = it.jStr("mobile")
            SelectorOption(it.jStr("id"), if (m.isNotBlank()) "${it.jStr("name")} — $m" else it.jStr("name"))
        }
    }
    LaunchedEffect(Unit) { load() }

    val options = listOf(SelectorOption("", "— none —")) + rows
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reference") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSelectDropdown("Select Reference", options, options.firstOrNull { it.id == picked }, { picked = it.id })
                if (rows.isEmpty()) Text("Nobody on the list yet.", style = MaterialTheme.typography.bodySmall)
                LinkButton(text = if (adding) "Cancel" else "+ New reference", onClick = { adding = !adding })
                if (adding) {
                    AppTextField(name, { name = it }, label = "Name", modifier = Modifier.fillMaxWidth())
                    AppTextField(mobile, { mobile = it }, label = "Mobile", modifier = Modifier.fillMaxWidth())
                    AppTextField(address, { address = it }, label = "Address", modifier = Modifier.fillMaxWidth())
                    PrimaryButton(
                        text = "Add", isLoading = saving, compact = true,
                        onClick = {
                            if (name.isBlank()) {
                                note = "A name is required."
                                return@PrimaryButton
                            }
                            saving = true
                            scope.launch {
                                val body = JsonObject().apply {
                                    addProperty("name", name.trim())
                                    addProperty("mobile", mobile.trim())
                                    addProperty("address", address.trim())
                                }
                                when (val r = api.post("referrers", body)) {
                                    is Resource.Success -> {
                                        r.data.jPayload().jStr("id").takeIf { it.isNotBlank() }?.let { picked = it }
                                        note = r.data.jStr("message").ifBlank { "Referrer added." }
                                        name = ""; mobile = ""; address = ""; adding = false
                                        load()
                                    }
                                    is Resource.Error -> note = r.message.ifBlank { "Could not add the referrer." }
                                    Resource.Loading -> Unit
                                }
                                saving = false
                            }
                        },
                    )
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { PrimaryButton(text = "Save", compact = true, onClick = { onPick(picked); onDismiss() }) },
        dismissButton = { LinkButton(text = "Remove", onClick = { onPick(""); onDismiss() }) },
    )
}
