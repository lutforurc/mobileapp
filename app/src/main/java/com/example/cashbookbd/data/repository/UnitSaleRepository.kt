package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.example.cashbookbd.ui.components.LedgerDropdownItem
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Backs the Real Estate module: the Unit Sales entry form (unit/parking/charge
 * pickers + the sale POST) and the Building Layout viewer.
 *
 * ⚠️ [submitSale] posts a REAL sale voucher plus a cash-received voucher via
 * `real-estate/unit/sales` — never call it as a test. Success/failure is decided
 * by the body's `success` flag; a failure can arrive on HTTP 200/201 too.
 */

/**
 * One option of the unit/parking DDLs (`real-estate/units/ddl`,
 * `real-estate/parking/ddl`). The web posts the WHOLE option object back inside
 * the sale payload, so [raw] keeps the exact JSON the server sent.
 *
 * label   = unit_no, label_0 = size_sqft, label_1 = sale price per sqft,
 * label_2 = flat,    label_3 = building,  label_4 = area, label_5 = branch.
 */
data class RealEstateOption(
    val id: Int,
    val label: String,
    val sizeSqft: Double,
    val ratePerSqft: Double,
    val flatName: String?,
    val buildingName: String?,
    val areaName: String?,
    val branchName: String?,
    val raw: JsonObject,
) {
    /** The auto price of the locked line: size × per-sqft rate. */
    val autoAmount: Double get() = sizeSqft * ratePerSqft
}

/** A charge-type option (`real-estate/units/charge-types/ddl`): label_2 is the effect. */
data class ChargeTypeOption(
    val id: Int,
    val name: String,
    /** "+" adds to the total, "-" subtracts (e.g. a discount). */
    val effect: String,
)

/** One row of the pricing table — mirrors the web's PriceItem exactly. */
data class UnitSaleLine(
    /** Epoch millis at creation; the web uses Date.now() as the row id. */
    val id: Long,
    /** "UNIT_PRICE" | "PARKING" | "CUSTOM". */
    val type: String,
    val title: String,
    /** "+" | "-". */
    val effect: String,
    /** "unit" | "parking" | null. */
    val linkedKind: String?,
    val linkedLabel: String?,
    val linkedId: Int?,
    val amount: Double,
    val note: String?,
    /** "LOCKED" | "EDITABLE". */
    val editMode: String,
) {
    /** Signed contribution to the grand total. */
    val signedAmount: Double get() = if (effect == "-") -kotlin.math.abs(amount) else kotlin.math.abs(amount)
}

/** A unit chip in the layout viewer (`buildings/{id}/layout` → floors→flats→units). */
data class BuildingUnit(
    val id: Int,
    val unitNo: String,
    /** "unit" | "parking". */
    val unitType: String,
    val sizeSqft: Double,
    val salePrice: Double,
    /** 1 Available, 2 Under Dev, 3 Completed, 4 Sold. */
    val status: Int,
    val customerName: String?,
    val customerMobile: String?,
) {
    val isParking: Boolean get() = unitType.equals("parking", ignoreCase = true)

    /** size × per-sqft rate, or null when either side is unknown. */
    val totalPrice: Double? get() = if (sizeSqft > 0 && salePrice > 0) sizeSqft * salePrice else null
}

data class LayoutFlat(val flatName: String, val units: List<BuildingUnit>)

/** floor_no arrives as a STRING in the JSON — parsed to an Int here. */
data class LayoutFloor(val floorNo: Int, val flats: List<LayoutFlat>)

data class BuildingLayout(val building: String, val floors: List<LayoutFloor>)

class UnitSaleRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        /** The unit/parking/building DDLs search from 2 typed characters. */
        const val DDL_MIN_CHARS = 2
    }

    // ---- DDL pickers -------------------------------------------------------

    /** Unsold, active flat units (`real-estate/units/ddl?q=`). */
    suspend fun searchUnits(query: String): Resource<List<RealEstateOption>> =
        searchOptions("real-estate/units/ddl", query)

    /** Unsold, active parking units (`real-estate/parking/ddl?q=`). */
    suspend fun searchParking(query: String): Resource<List<RealEstateOption>> =
        searchOptions("real-estate/parking/ddl", query)

    private suspend fun searchOptions(path: String, query: String): Resource<List<RealEstateOption>> =
        withContext(ioDispatcher) {
            val q = query.trim()
            if (q.length < DDL_MIN_CHARS) return@withContext Resource.Success(emptyList())
            safeCall {
                val response = reportApi.get(path, mapOf("q" to q))
                response.unauthorizedOrNull()?.let { return@safeCall it }
                // The backend answers "no rows" with notFound(); treat it as empty.
                if (response.code() == 404 || response.code() == 201) {
                    return@safeCall Resource.Success(emptyList())
                }
                if (!response.isSuccessful) {
                    return@safeCall Resource.Error("Couldn't search (${response.code()}).")
                }
                Resource.Success(parseOptions(response.body()))
            }
        }

    /** All charge types (`real-estate/units/charge-types/ddl`) — loaded once, no query. */
    suspend fun getChargeTypes(): Resource<List<ChargeTypeOption>> = withContext(ioDispatcher) {
        safeCall {
            val response = reportApi.get("real-estate/units/charge-types/ddl", emptyMap())
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (response.code() == 404 || response.code() == 201) {
                return@safeCall Resource.Success(emptyList())
            }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error("Couldn't load charge types (${response.code()}).")
            }
            val array = locateArray(response.body()) ?: return@safeCall Resource.Success(emptyList())
            Resource.Success(
                array.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    ChargeTypeOption(
                        id = o.int("value") ?: return@mapNotNull null,
                        name = o.str("label") ?: return@mapNotNull null,
                        effect = if (o.str("label_2") == "-") "-" else "+",
                    )
                }
            )
        }
    }

    /** Buildings for the layout viewer (`real-estate/buildings/ddl?q=`). */
    suspend fun searchBuildings(query: String): Resource<List<RealEstateOption>> =
        searchOptions("real-estate/buildings/ddl", query)

    // ---- Building layout ---------------------------------------------------

    /**
     * `real-estate/buildings/{id}/layout` → the building's floors, flats and
     * unit chips. An empty building answers success:false ("No layout data
     * found…"), surfaced as an [Resource.Error] with that message.
     */
    suspend fun getBuildingLayout(buildingId: Int): Resource<BuildingLayout> = withContext(ioDispatcher) {
        safeCall {
            val response = reportApi.get("real-estate/buildings/$buildingId/layout", emptyMap())
            response.unauthorizedOrNull()?.let { return@safeCall it }
            val body = response.body() ?: response.errorBodyJson()
            val root = body?.takeIf { it.isJsonObject }?.asJsonObject
            val success = root?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            val message = root?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
            if (success == false || response.code() == 404) {
                return@safeCall Resource.Error(message ?: "No layout data found for the specified building.")
            }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error(message ?: "Server error (${response.code()}). Please try again later.")
            }
            val data = locateObject(body)
                ?: return@safeCall Resource.Error("Invalid response from server.")
            Resource.Success(parseLayout(data))
        }
    }

    // ---- Unit sale submit --------------------------------------------------

    /**
     * POSTs `real-estate/unit/sales` with the web UnitSalePage's exact payload.
     * The [unit]/[parking] options go back verbatim (their [RealEstateOption.raw]),
     * and the customer travels as the same `{value,label,label_2}` option shape
     * the web's coa4 dropdown produces.
     *
     * ⚠️ Creates a REAL sale + cash voucher. Success returns "Vr. No. X".
     */
    suspend fun submitSale(
        customer: LedgerDropdownItem,
        unit: RealEstateOption,
        parking: RealEstateOption?,
        paymentMode: String,
        receiptNo: String?,
        bookingAmt: Int,
        referenceNo: String?,
        bankName: String?,
        branchName: String?,
        items: List<UnitSaleLine>,
        total: Double,
        due: Double,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            add("customer", JsonObject().apply {
                addProperty("value", customer.id)
                addProperty("label", customer.name)
                if (customer.mobile.isNullOrBlank()) add("label_2", JsonNull.INSTANCE)
                else addProperty("label_2", customer.mobile)
            })
            add("unit", unit.raw.deepCopy())
            if (parking != null) add("parking", parking.raw.deepCopy()) else add("parking", JsonNull.INSTANCE)
            addProperty("payment_mode", paymentMode)
            addNullable("receipt_no", receiptNo)
            addProperty("booking_amt", bookingAmt)
            add("note", JsonNull.INSTANCE)
            addNullable("reference_no", referenceNo)
            addNullable("bank_name", bankName)
            addNullable("branch_name", branchName)
            add("items", JsonArray().apply { items.forEach { add(lineJson(it)) } })
            addProperty("total", total)
            addProperty("due", due)
        }
        try {
            val response = transactionApi.postObject("real-estate/unit/sales", body)
            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return@withContext Resource.Error(
                    "You do not have permission for this action."
                )
            }
            parseSaleResult(response)
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
    }

    private fun lineJson(line: UnitSaleLine): JsonObject = JsonObject().apply {
        addProperty("id", line.id)
        addProperty("type", line.type)
        addProperty("title", line.title)
        addProperty("effect", line.effect)
        if (line.linkedKind == null) {
            add("linkedTo", JsonNull.INSTANCE)
        } else {
            add("linkedTo", JsonObject().apply {
                addProperty("kind", line.linkedKind)
                addProperty("label", line.linkedLabel.orEmpty())
                // The web names the id after the kind: unitId or parkingId.
                if (line.linkedKind == "unit") addProperty("unitId", line.linkedId ?: 0)
                else addProperty("parkingId", line.linkedId ?: 0)
            })
        }
        addProperty("amount", line.amount)
        addNullable("note", line.note)
        addProperty("editMode", line.editMode)
    }

    /**
     * A save fails as success:false on 200/201 (unit already sold), as a 422
     * validation error (duplicate receipt no.), or as a plain 500 — all three
     * become an [Resource.Error] with the server's own message when it has one.
     */
    private fun parseSaleResult(response: Response<JsonElement>): Resource<String> {
        val root = (response.body() ?: response.errorBodyJson())
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return if (response.isSuccessful) {
                Resource.Error("Invalid response from server.")
            } else {
                Resource.Error("Server error (${response.code()}). Please try again later.")
            }

        val success = root.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        val message = root.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
        val errorMessage = root.getAsJsonObject("error")
            ?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }

        if (success == false || !response.isSuccessful) {
            return Resource.Error(errorMessage ?: message ?: "The unit sale could not be saved.")
        }

        val vrNo = root.getAsJsonObject("data")?.getAsJsonObject("data")
            ?.get("vr_no")?.takeUnless { it.isJsonNull }?.asString
        return Resource.Success(
            when {
                !vrNo.isNullOrBlank() -> "Vr. No. $vrNo"
                message != null -> message
                else -> "Unit sale transaction saved successfully."
            }
        )
    }

    // ---- Parsing helpers ---------------------------------------------------

    private fun parseOptions(root: JsonElement?): List<RealEstateOption> {
        val array = locateArray(root) ?: return emptyList()
        return array.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            RealEstateOption(
                id = o.int("value") ?: return@mapNotNull null,
                label = o.str("label") ?: return@mapNotNull null,
                sizeSqft = o.num("label_0"),
                ratePerSqft = o.num("label_1"),
                flatName = o.str("label_2"),
                buildingName = o.str("label_3"),
                areaName = o.str("label_4"),
                branchName = o.str("label_5"),
                raw = o,
            )
        }
    }

    private fun parseLayout(data: JsonObject): BuildingLayout = BuildingLayout(
        building = data.str("building") ?: "Building",
        floors = data.get("floors")?.takeIf { it.isJsonArray }?.asJsonArray.orEmpty().mapNotNull { fl ->
            val f = fl.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            LayoutFloor(
                // floor_no is a STRING key from the server's groupBy; "2" or 2.
                floorNo = f.num("floor_no").toInt(),
                flats = f.get("flats")?.takeIf { it.isJsonArray }?.asJsonArray.orEmpty().mapNotNull { flat ->
                    val ft = flat.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    LayoutFlat(
                        flatName = ft.str("flat_name").orEmpty(),
                        units = ft.get("units")?.takeIf { it.isJsonArray }?.asJsonArray.orEmpty()
                            .mapNotNull(::parseUnit),
                    )
                },
            )
        },
    )

    private fun parseUnit(el: JsonElement): BuildingUnit? {
        val u = el.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val customer = u.get("customer")?.takeIf { it.isJsonObject }?.asJsonObject
        return BuildingUnit(
            id = u.int("id") ?: return null,
            unitNo = u.str("unit_no") ?: "-",
            unitType = u.str("unit_type") ?: "unit",
            sizeSqft = u.num("size_sqft"),
            salePrice = u.num("sale_price"),
            status = u.int("status") ?: 0,
            customerName = customer?.str("name"),
            customerMobile = customer?.str("mobile"),
        )
    }

    /** Unwraps the `foundData` envelope (`data.data`) down to the row array. */
    private fun locateArray(root: JsonElement?): JsonArray? =
        unwrapData(root)?.takeIf { it.isJsonArray }?.asJsonArray

    /** Unwraps the `foundData` envelope (`data.data`) down to the payload object. */
    private fun locateObject(root: JsonElement?): JsonObject? =
        unwrapData(root)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun unwrapData(root: JsonElement?): JsonElement? {
        if (root == null) return null
        if (root.isJsonObject) {
            val success = root.asJsonObject.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) return null
        }
        var payload: JsonElement = root
        repeat(2) {
            val inner = payload.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        return payload
    }

    /** Non-2xx replies (404/422/500) carry their JSON in the error body. */
    private fun Response<JsonElement>.errorBodyJson(): JsonElement? = try {
        errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { JsonParser.parseString(it) }
    } catch (e: Exception) {
        null
    }

    private fun JsonObject.addNullable(key: String, value: String?) {
        if (value.isNullOrBlank()) add(key, JsonNull.INSTANCE) else addProperty(key, value)
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray()

    private fun JsonObject.str(key: String): String? {
        val el = get(key)?.takeUnless { it.isJsonNull } ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.trim().ifBlank { null }
    }

    /** Lenient number read — the API sends numerics as numbers or strings. */
    private fun JsonObject.num(key: String): Double =
        str(key)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    private fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()

    private fun Response<*>.unauthorizedOrNull(): Resource.Error? =
        if (code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            null
        }

    private inline fun <T> safeCall(block: () -> Resource<T>): Resource<T> = try {
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
}
