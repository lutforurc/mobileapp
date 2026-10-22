package com.example.cashbookbd.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/*
 * Defensive readers for the Laravel payloads, shared by the screens added in
 * the 2026-10 parity pass. The server is loose about types — an id may arrive
 * as 12 or "12", a money figure as "1,250.00", a missing relation as null or
 * as `false` — so every reader takes what it can and falls back quietly.
 *
 * Prefixed `j…` so they never collide with the private helpers older
 * repositories declare under the plain names.
 */

/** This element as an object, or null. */
fun JsonElement?.jAsObj(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject

/** This element as an array, or null. */
fun JsonElement?.jAsArr(): JsonArray? = this?.takeIf { it.isJsonArray }?.asJsonArray

fun JsonObject.jObj(key: String): JsonObject? = get(key).jAsObj()

fun JsonObject.jArr(key: String): JsonArray = get(key).jAsArr() ?: JsonArray()

/** The objects in the array under [key]; non-objects are skipped. */
fun JsonObject.jObjs(key: String): List<JsonObject> = jArr(key).mapNotNull { it.jAsObj() }

/** The objects of an array element (top-level arrays). */
fun JsonArray.jObjects(): List<JsonObject> = mapNotNull { it.jAsObj() }

/** A scalar as text; "" when absent, null, or not a scalar. */
fun JsonObject.jStr(key: String): String =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

/** A number, tolerant of strings with thousands separators; 0 when absent. */
fun JsonObject.jNum(key: String): Double = jNumOrNull(key) ?: 0.0

fun JsonObject.jNumOrNull(key: String): Double? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString?.replace(",", "")?.trim()?.toDoubleOrNull()

fun JsonObject.jLong(key: String): Long? = jNumOrNull(key)?.toLong()

fun JsonObject.jInt(key: String): Int? = jNumOrNull(key)?.toInt()

/** "1", 1, true, "true" are on; everything else is off. */
fun JsonObject.jBool(key: String): Boolean {
    val el = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return false
    return when {
        el.isBoolean -> el.asBoolean
        else -> el.asString.trim().let { it == "1" || it.equals("true", ignoreCase = true) }
    }
}

/**
 * Walks a dot path ("product.unit.name"); numeric segments index arrays.
 * Returns the scalar's text, or "" when any step is missing.
 */
fun JsonObject.jPath(path: String): String {
    var cur: JsonElement? = this
    for (seg in path.split('.')) {
        cur = when {
            cur == null -> return ""
            cur.isJsonObject -> cur.asJsonObject.get(seg)
            cur.isJsonArray -> seg.toIntOrNull()?.let { i -> cur!!.asJsonArray.takeIf { i in 0 until it.size() }?.get(i) }
            else -> return ""
        }
    }
    return cur?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
}

/**
 * The row list of a `foundData()` answer wherever it sits: a top-level
 * array, `data` as an array, `data.data`, or a paginator's `data.data.data`.
 */
fun JsonObject.jRows(): List<JsonObject> {
    val data = get("data")
    data.jAsArr()?.let { return it.jObjects() }
    val inner = data.jAsObj()?.get("data")
    inner.jAsArr()?.let { return it.jObjects() }
    inner.jAsObj()?.get("data").jAsArr()?.let { return it.jObjects() }
    return emptyList()
}

/** The payload object of a `foundData()` answer: `data.data`, else `data`, else the body. */
fun JsonObject.jPayload(): JsonObject =
    jObj("data")?.let { d -> d.jObj("data") ?: d } ?: this
