package com.example.cashbookbd.data.repository

import com.google.gson.JsonObject

/** One short line: what is on hand against what the voucher asks for. */
data class StockShortageRow(
    val name: String,
    val available: Double,
    val requested: Double,
)

/**
 * The question a voucher is stopped with before it overdraws the stock.
 *
 * Not a refusal (unless [blocked]). Goods routinely arrive before the
 * supplier's invoice does, so a branch can genuinely hold what its books have
 * not caught up with — and the same screen is used by someone who has simply
 * picked the wrong line. Only the person holding the phone can tell those
 * apart, so they are shown both figures and asked.
 *
 * Shaped like the server's answer (`stock_shortage` / `shortage_rows` /
 * `shortages` / `stock_blocked`), which is the same for a sale and a branch
 * transfer — so every screen asks the same way, as on the web.
 */
data class StockShortageWarning(
    val rows: List<StockShortageRow>,
    /** The rows already worded ("Product - Available X, Requested Y"). */
    val shortages: List<String>,
    val message: String,
    /** The branch refuses such a voucher: shown, but with nothing to continue to. */
    val blocked: Boolean = false,
)

/**
 * Reads the guard's answer out of a store response, or null when the body is
 * not a stock-shortage question. Shared by the sales and branch-transfer
 * repositories — the server words both with the same keys on purpose.
 */
fun parseStockShortage(obj: JsonObject): StockShortageWarning? {
    val isShortage = obj.get("stock_shortage")?.takeUnless { it.isJsonNull }
        ?.takeIf { it.isJsonPrimitive }?.asBoolean == true
    if (!isShortage) return null

    val rows = obj.get("shortage_rows")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = o.get("name")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifBlank { null }
                ?: return@mapNotNull null
            fun num(key: String): Double = o.get(key)?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString?.replace(",", "")?.trim()
                ?.toDoubleOrNull() ?: 0.0
            StockShortageRow(name = name, available = num("available"), requested = num("requested"))
        }
        .orEmpty()
    val shortages = obj.get("shortages")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifBlank { null } }
        .orEmpty()
    val message = obj.get("message")?.takeUnless { it.isJsonNull }
        ?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifBlank { null }
        ?: "Not enough stock."
    val blocked = obj.get("stock_blocked")?.takeUnless { it.isJsonNull }
        ?.takeIf { it.isJsonPrimitive }?.asBoolean == true

    return StockShortageWarning(rows = rows, shortages = shortages, message = message, blocked = blocked)
}
