package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** One row of the Customers list (`contact/details`). */
data class CustomerRow(
    /** Raw PartyInfo id — the update endpoint resolves it. */
    val id: String,
    val name: String,
    val opening: String,
    val address: String,
    val ledgerPage: String,
    val mobile: String,
    val nationalId: String,
    /** The party's ledger account — what the opening voucher links into. */
    val coa4Id: String = "",
    /** The journal voucher carrying the opening balance, when one is live. */
    val openingVrNo: String = "",
) {
    /** True once a non-zero opening is stored (used only to pre-fill the edit). */
    val isOpeningSet: Boolean get() = (opening.toDoubleOrNull() ?: 0.0) != 0.0
}

/** A page of [CustomerRow]s plus the paginator meta. */
data class CustomerPage(
    val rows: List<CustomerRow>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)

/** The fields the (essential) Add Customer form collects. */
data class NewCustomer(
    /** Party type: 1 Customer, 2 Supplier, 3 Supplier & Customer, 4 Advance. */
    val typeId: String,
    val name: String,
    /** Bangla name, or blank — shown only when the branch's `use_bangla` is on. */
    val bangla: String = "",
    val address: String,
    val mobile: String,
    val ledgerPage: String,
    val nationalId: String,
    /** "male"/"female"/"other", or blank — shown only when the branch needs it. */
    val sex: String = "",
    /** Area id, or blank — shown only when the branch needs it. Choosing an
     *  area makes the server compose the address from area/thana/district. */
    val areaId: String = "",
    /**
     * Opening balance, or blank — shown only while the branch is keying openings.
     *
     * Sent, the server both stores the figure and raises the journal voucher for
     * it, exactly as entering one on the customer list does. Refused, it rolls
     * the whole creation back, so there is no customer left carrying a balance
     * the ledger never heard of.
     */
    val openingBalance: String = "",
    // The branch-gated extras the web form carries; blank when ungated.
    val relationId: String = "",
    val father: String = "",
    val motherName: String = "",
    /** yyyy-MM-dd, or blank. */
    val dateOfBirth: String = "",
    val occupation: String = "",
    val contactPerson: String = "",
    val contactNumber: String = "",
    val permanentAddress: String = "",
    /** Customer Number (idfr_code), gated on the branch's have_customer_sl. */
    val idfrCode: String = "",
    /** "Access Customer Login" — with [password] (min 8) when on. */
    val customerLogin: Boolean = false,
    val password: String = "",
    /** A `data:image/…;base64,` URI, or blank for none (key then omitted). */
    val photo: String = "",
    /** Branch-gated panels; empty lists leave the keys out of the body. */
    val guarantors: List<GuarantorRow> = emptyList(),
    val nominees: List<NomineeRow> = emptyList(),
)

/** One customer area from `area/ddl-list` — the Select Area options. */
data class CustomerArea(
    val id: String,
    val name: String,
    val thana: String,
    val district: String,
)

/**
 * The full customer as `contact/edit/{id}` answers it — every editable field
 * plus the guarantor/nominee arrays kept raw (echoed back on update so the
 * server's delete-and-recreate never wipes them).
 */
data class CustomerDetail(
    val id: String,
    val partyTypeId: String,
    val areaId: String,
    val name: String,
    val bangla: String,
    val relationId: String,
    val father: String,
    val motherName: String,
    val occupation: String,
    val sex: String,
    val dateOfBirth: String,
    val contactPerson: String,
    val contactNumber: String,
    val mobile: String,
    val email: String,
    val nationalId: String,
    val manualAddress: String,
    val permanentAddress: String,
    val idfrCode: String,
    val ledgerPage: String,
    /** Blank when unset/zero (unlocked); non-blank means one-time-set already. */
    val openingBalance: String,
    /** Stored relative path ("images/customer_photo/…"), or blank. */
    val photo: String,
    val customerLogin: Boolean,
    val guarantorsRaw: JsonArray,
    val nomineesRaw: JsonArray,
) {
    /** The web's rule: any non-zero stored opening locks the field. */
    val openingLocked: Boolean get() = openingBalance.isNotBlank()
}

/** The scalar fields the full Edit form posts to `contact/update/{id}`. */
data class CustomerForm(
    val partyTypeId: String,
    val areaId: String,
    val name: String,
    val bangla: String,
    val relationId: String,
    val father: String,
    val motherName: String,
    val occupation: String,
    val sex: String,
    val dateOfBirth: String,
    val contactPerson: String,
    val contactNumber: String,
    val mobile: String,
    val nationalId: String,
    val manualAddress: String,
    val permanentAddress: String,
    val idfrCode: String,
    val ledgerPage: String,
    val customerLogin: Boolean,
)

/** The duplicate-mobile warning (`contact/mobile-check`). */
data class MobileCheck(val exists: Boolean, val ownerName: String?)

/** One guarantor row — create-only server-side (update recreates the set). */
data class GuarantorRow(
    val name: String = "",
    val fatherName: String = "",
    val mobile: String = "",
    val nationalId: String = "",
    val address: String = "",
)

/**
 * One nominee row. [id] is kept on edit so the server upserts instead of
 * replacing; [photo] may hold the stored path (untouched), a fresh data URI,
 * or blank. Rows left out of an update are deleted server-side.
 */
data class NomineeRow(
    val id: String = "",
    val name: String = "",
    val relation: String = "",
    val occupation: String = "",
    val dateOfBirth: String = "",
    val motherName: String = "",
    val mobile: String = "",
    val presentAddress: String = "",
    val permanentAddress: String = "",
    val nationalId: String = "",
    val sharePercentage: String = "",
    val priorityOrder: String = "",
    val guardianName: String = "",
    val guardianMobile: String = "",
    val status: String = "active",
    val photo: String = "",
    val remarks: String = "",
)

/** The web rows' JSON shape, key for key. */
fun List<GuarantorRow>.toGuarantorsJson(): JsonArray = JsonArray().apply {
    this@toGuarantorsJson.forEach { row ->
        add(JsonObject().apply {
            addProperty("name", row.name.trim())
            addProperty("father_name", row.fatherName.trim())
            addProperty("mobile", row.mobile.trim())
            addProperty("national_id", row.nationalId.trim())
            addProperty("address", row.address.trim())
        })
    }
}

fun List<NomineeRow>.toNomineesJson(): JsonArray = JsonArray().apply {
    this@toNomineesJson.forEach { row ->
        add(JsonObject().apply {
            // The id only when it exists — the server upserts owned ids.
            row.id.takeIf { it.isNotBlank() }?.let { addProperty("id", it) }
            addProperty("name", row.name.trim())
            addProperty("relation", row.relation)
            addProperty("occupation", row.occupation.trim())
            addProperty("date_of_birth", row.dateOfBirth)
            addProperty("mother_name", row.motherName.trim())
            addProperty("mobile", row.mobile.trim())
            addProperty("present_address", row.presentAddress.trim())
            addProperty("permanent_address", row.permanentAddress.trim())
            addProperty("national_id", row.nationalId.trim())
            addProperty("share_percentage", row.sharePercentage.trim())
            addProperty("priority_order", row.priorityOrder.trim())
            addProperty("guardian_name", row.guardianName.trim())
            addProperty("guardian_mobile", row.guardianMobile.trim())
            addProperty("status", row.status.ifBlank { "active" })
            addProperty("photo", row.photo)
            addProperty("remarks", row.remarks.trim())
        })
    }
}

fun JsonArray.toGuarantorRows(): List<GuarantorRow> = mapNotNull { el ->
    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
    GuarantorRow(
        name = o.rowStr("name"),
        fatherName = o.rowStr("father_name"),
        mobile = o.rowStr("mobile"),
        nationalId = o.rowStr("national_id"),
        address = o.rowStr("address"),
    )
}

fun JsonArray.toNomineeRows(): List<NomineeRow> = mapNotNull { el ->
    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
    NomineeRow(
        id = o.rowStr("id"),
        name = o.rowStr("name"),
        relation = o.rowStr("relation"),
        occupation = o.rowStr("occupation"),
        dateOfBirth = o.rowStr("date_of_birth"),
        motherName = o.rowStr("mother_name"),
        mobile = o.rowStr("mobile"),
        presentAddress = o.rowStr("present_address"),
        permanentAddress = o.rowStr("permanent_address"),
        nationalId = o.rowStr("national_id"),
        sharePercentage = o.rowStr("share_percentage"),
        priorityOrder = o.rowStr("priority_order"),
        guardianName = o.rowStr("guardian_name"),
        guardianMobile = o.rowStr("guardian_mobile"),
        status = o.rowStr("status").ifBlank { "active" },
        photo = o.rowStr("photo"),
        remarks = o.rowStr("remarks"),
    )
}

private fun JsonObject.rowStr(key: String): String =
    get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

/**
 * Creates a customer/supplier contact (`contact/store`), a port of the web's
 * AddCustomerSupplier save. Only the essential fields are sent — the advanced
 * sections (area, guarantors, nominees, portal login) are omitted.
 */
class CustomerRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
    }

    /** A page of the Customers list (`contact/details`, paginated). */
    suspend fun loadCustomers(page: Int, perPage: Int, search: String): Resource<CustomerPage> = withContext(ioDispatcher) {
        try {
            val response = api.post(
                "contact/details",
                mapOf("page" to page.toString(), "per_page" to perPage.toString(), "search" to search.trim()),
            )
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            if (!response.isSuccessful && response.code() != 201) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val json = response.jsonBody()
            // notFound() ("No data found!") arrives as success:false at 201.
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Success(CustomerPage(emptyList(), 1, 1, 0))
            }
            val paginator = json?.getAsJsonObject("data")
                ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val rows = paginator?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.toCustomerRow() }
                .orEmpty()
            Resource.Success(
                CustomerPage(
                    rows = rows,
                    currentPage = paginator.intOr("current_page", 1),
                    lastPage = paginator.intOr("last_page", 1),
                    total = paginator.intOr("total", rows.size),
                )
            )
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Customer areas for the branch-gated "Select Area" field — the web's
     * `POST area/ddl-list` (`{searchName: ""}` loads the whole list). Each row:
     * `{id, name, thana_name, district_name}`; rows found defensively at
     * `data.data`, `data`, or the root array.
     */
    suspend fun fetchAreas(): Resource<List<CustomerArea>> = withContext(ioDispatcher) {
        try {
            val response = api.post("area/ddl-list", mapOf("searchName" to ""))
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Success(emptyList())
            }
            val data = json?.get("data")?.takeUnless { it.isJsonNull }
            val rows = when {
                data == null -> null
                data.isJsonArray -> data.asJsonArray
                data.isJsonObject -> data.asJsonObject.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                else -> null
            }
            Resource.Success(
                rows?.mapNotNull { element ->
                    val obj = element?.takeIf { it.isJsonObject }?.asJsonObject
                        ?: return@mapNotNull null
                    fun str(key: String): String =
                        obj.get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                    val id = str("id")
                    if (id.isBlank()) null else CustomerArea(
                        id = id,
                        name = str("name"),
                        thana = str("thana_name"),
                        district = str("district_name"),
                    )
                }.orEmpty()
            )
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    suspend fun storeCustomer(customer: NewCustomer): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            "type_id" to customer.typeId,
            "name" to customer.name.trim(),
            "bangla" to customer.bangla.trim(),
            "manual_address" to customer.address.trim(),
            "mobile" to customer.mobile.trim(),
            "ledger_page" to customer.ledgerPage.trim(),
            "national_id" to customer.nationalId.trim(),
            // Branch-gated extras; blank when the branch doesn't collect them,
            // exactly as the web sends the unused fields.
            "sex" to customer.sex,
            "area_id" to customer.areaId,
            "relation_id" to customer.relationId,
            "father" to customer.father.trim(),
            "mother_name" to customer.motherName.trim(),
            "date_of_birth" to customer.dateOfBirth,
            "occupation" to customer.occupation.trim(),
            "contact_person" to customer.contactPerson.trim(),
            "contact_number" to customer.contactNumber.trim(),
            "permanent_address" to customer.permanentAddress.trim(),
            "idfr_code" to customer.idfrCode.trim(),
            "customerLogin" to if (customer.customerLogin) "1" else "0",
        ) + buildMap {
            // Left out entirely when blank: the server acts on the key's
            // presence, and an empty one would be a figure nobody entered.
            customer.openingBalance.trim().takeIf { it.isNotEmpty() }
                ?.let { put("openingbalance", it) }
            // Applied only when filled (server rule) — and only with login on.
            customer.password.takeIf { customer.customerLogin && it.isNotBlank() }
                ?.let { put("password", it) }
            // The web's base64 data URI; absent is safely "no photo" on store.
            customer.photo.takeIf { it.isNotBlank() }?.let { put("photo", it) }
        }
        // The panels are arrays, which the flat form post can't carry — the
        // whole body travels as one JSON object instead.
        val jsonBody = JsonObject().apply {
            body.forEach { (key, value) -> addProperty(key, value) }
            if (customer.guarantors.isNotEmpty()) {
                add("guarantors", customer.guarantors.toGuarantorsJson())
            }
            if (customer.nominees.isNotEmpty()) {
                add("nominees", customer.nominees.toNomineesJson())
            }
        }
        try {
            val response = api.postObjectRaw("contact/store", jsonBody)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Customer saved successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Sets a customer's opening balance and/or ledger page from the list
     * (`contact/customer/update/ui/{id}`). Only non-blank fields are sent, so a
     * blank input never clears an existing value. The opening balance may be
     * re-saved: the server rewrites its journal voucher in place, refusing only
     * when the voucher is approved or belongs to another branch.
     */
    suspend fun updateOpeningLedger(
        id: String,
        opening: String?,
        ledgerPage: String?,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = buildMap {
            opening?.trim()?.takeIf { it.isNotEmpty() }?.let { put("openingbalance", it) }
            ledgerPage?.trim()?.takeIf { it.isNotEmpty() }?.let { put("ledger_page", it) }
        }
        if (body.isEmpty()) {
            return@withContext Resource.Error("Enter an opening balance or a ledger page to save.")
        }
        try {
            val response = api.post("contact/customer/update/ui/$id", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Customer updated successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Removes a customer's opening balance and sends its journal voucher to the
     * trash (`contact/customer/opening-balance/delete/{id}`, raw numeric id).
     * The customer stays. Refusals — an approved voucher, another branch —
     * arrive as notFound() (success:false at 2xx) with the reason in `message`.
     */
    suspend fun deleteOpeningBalance(id: String): Resource<String> = withContext(ioDispatcher) {
        try {
            val response = api.post("contact/customer/opening-balance/delete/$id", emptyMap())
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Opening balance could not be deleted."
                )
            }
            Resource.Success(
                json?.message()?.takeIf { it.isNotBlank() } ?: "Opening balance deleted"
            )
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * The full customer for editing (`GET contact/edit/{id}`, raw numeric id):
     * every form field plus the guarantor/nominee arrays kept RAW so the
     * update can echo them back verbatim — the server delete-and-recreates
     * whatever arrives, and an omitted array must never wipe the panels.
     */
    suspend fun fetchCustomerDetail(id: String): Resource<CustomerDetail> = withContext(ioDispatcher) {
        try {
            val response = api.get("contact/edit/$id", emptyMap())
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Error(json.message() ?: "Contact not found")
            }
            // The slice reads data.data; tolerate a single-nested data too.
            val data = json?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val party = data?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: data
                ?: return@withContext Resource.Error("Contact not found")
            Resource.Success(
                CustomerDetail(
                    id = party.str("id") ?: id,
                    partyTypeId = party.str("party_type_id").orEmpty(),
                    areaId = party.str("area_id").orEmpty(),
                    name = party.str("name").orEmpty(),
                    bangla = party.str("bangla").orEmpty(),
                    relationId = party.str("relation_id").orEmpty(),
                    father = party.str("father").orEmpty(),
                    motherName = party.str("mother_name").orEmpty(),
                    occupation = party.str("occupation").orEmpty(),
                    sex = party.str("sex").orEmpty(),
                    dateOfBirth = party.str("date_of_birth").orEmpty(),
                    contactPerson = party.str("contact_person").orEmpty(),
                    contactNumber = party.str("contact_number").orEmpty(),
                    mobile = party.str("mobile").orEmpty(),
                    email = party.str("email").orEmpty(),
                    nationalId = party.str("national_id").orEmpty(),
                    manualAddress = party.str("manual_address").orEmpty(),
                    permanentAddress = party.str("permanent_address").orEmpty(),
                    idfrCode = party.str("idfr_code").orEmpty(),
                    ledgerPage = party.str("ledger_page").orEmpty(),
                    // The web's prefill rule: null/0 reads as blank (unlocked).
                    openingBalance = party.str("openingbalance")
                        ?.takeIf { it.toDoubleOrNull() != 0.0 }.orEmpty(),
                    photo = party.str("photo").orEmpty(),
                    customerLogin = party.str("customer_login")?.trim()?.toDoubleOrNull() == 1.0,
                    guarantorsRaw = party.get("guarantors")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?: JsonArray(),
                    nomineesRaw = party.get("nominees")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?: JsonArray(),
                )
            )
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * The full customer update (`POST contact/update/{id}`). Never carries
     * `openingbalance` (this endpoint silently ignores it — an opening goes
     * through [updateOpeningLedger], the one path that raises the voucher) and
     * never carries `photo` (omitting the key preserves the stored one). The
     * guarantor/nominee arrays echo [CustomerDetail]'s raw copies until their
     * own screens land.
     */
    suspend fun updateCustomer(
        id: String,
        form: CustomerForm,
        echo: CustomerDetail,
        /** Null = leave the stored photo; "" = delete it; else a new data URI. */
        photo: String? = null,
        /** Null = echo the fetched arrays untouched; else the edited rows. */
        guarantors: List<GuarantorRow>? = null,
        nominees: List<NomineeRow>? = null,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("party_type_id", form.partyTypeId)
            addProperty("area_id", form.areaId)
            addProperty("name", form.name.trim())
            addProperty("bangla", form.bangla.trim())
            addProperty("relation_id", form.relationId)
            addProperty("father", form.father.trim())
            addProperty("mother_name", form.motherName.trim())
            addProperty("occupation", form.occupation.trim())
            addProperty("sex", form.sex)
            addProperty("date_of_birth", form.dateOfBirth)
            addProperty("contact_person", form.contactPerson.trim())
            addProperty("contact_number", form.contactNumber.trim())
            addProperty("idfr_code", form.idfrCode.trim())
            addProperty("mobile", form.mobile.trim())
            addProperty("email", echo.email)
            addProperty("national_id", form.nationalId.trim())
            addProperty("ledger_page", form.ledgerPage.trim())
            addProperty("manual_address", form.manualAddress.trim())
            addProperty("permanent_address", form.permanentAddress.trim())
            addProperty("customerLogin", if (form.customerLogin) "1" else "0")
            // The server only touches the photo column when the key is present.
            photo?.let { addProperty("photo", it) }
            // Edited rows when the panels' UI provided them; the fetched
            // arrays verbatim otherwise (update delete-and-recreates).
            add("guarantors", guarantors?.toGuarantorsJson() ?: echo.guarantorsRaw)
            add("nominees", nominees?.toNomineesJson() ?: echo.nomineesRaw)
        }
        try {
            val response = api.postObjectRaw("contact/update/$id", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Customer updated successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Sets (or, with a blank password, revokes) the customer's portal access —
     * `POST contact/customer/set-password/{id}`. The server wants min 8 chars;
     * a blank body nulls the password and kills the portal tokens.
     */
    suspend fun setPortalPassword(id: String, password: String): Resource<String> =
        withContext(ioDispatcher) {
            try {
                val response = api.post(
                    "contact/customer/set-password/$id",
                    mapOf("password" to password),
                )
                if (response.code() == HTTP_UNAUTHORIZED) {
                    return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
                }
                val json = response.jsonBody()
                val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                    (!response.isSuccessful && response.code() != 201)
                if (rejected) {
                    return@withContext Resource.Error(
                        json?.message() ?: "Couldn't save the portal password."
                    )
                }
                Resource.Success(
                    json?.message()?.takeIf { it.isNotBlank() }
                        ?: if (password.isBlank()) "Portal access revoked." else "Portal password saved."
                )
            } catch (e: IOException) {
                Resource.Error(NO_NETWORK)
            } catch (e: HttpException) {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    /**
     * The web's on-blur duplicate check (`POST contact/mobile-check`). Null on
     * any failure — this is a warning, never a gate; the save stays allowed.
     */
    suspend fun checkMobile(mobile: String): MobileCheck? = withContext(ioDispatcher) {
        try {
            val json = api.post("contact/mobile-check", mapOf("mobile" to mobile.trim()))
                .takeIf { it.isSuccessful }?.body()
                ?.takeIf { it.isJsonObject }?.asJsonObject ?: return@withContext null
            val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val payload = data?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: data
                ?: return@withContext null
            val exists = payload.get("exists")?.takeUnless { it.isJsonNull }?.asBoolean == true
            val owner = payload.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.str("name")
            MobileCheck(exists = exists, ownerName = owner)
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.toCustomerRow(): CustomerRow? {
        val id = str("id") ?: return null
        return CustomerRow(
            id = id,
            name = str("name").orEmpty(),
            opening = str("openingbalance") ?: "0",
            address = str("manual_address").orEmpty(),
            ledgerPage = str("ledger_page").orEmpty(),
            mobile = str("mobile").orEmpty(),
            nationalId = str("national_id").orEmpty(),
            coa4Id = str("coa4_id").orEmpty(),
            // Joined on status = 1 server-side, so a trashed voucher shows blank.
            openingVrNo = str("opening_vr_no").orEmpty(),
        )
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject?.intOr(key: String, default: Int): Int =
        this?.get(key)?.takeUnless { it.isJsonNull }?.asString?.toDoubleOrNull()?.toInt() ?: default

    /** The response JSON, from body() or a non-2xx errorBody(). */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** The reason a write was rejected: `message`, `error.message`, or a field error. */
    private fun JsonObject.message(): String? =
        get("message")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            ?: getAsJsonObject("error")?.get("message")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
            ?: firstFieldError()

    /** Reads `errors: {field: ["reason", …]}` — Laravel's per-field validation. */
    private fun JsonObject.firstFieldError(): String? {
        val errors = getAsJsonObject("errors") ?: return null
        return errors.keySet()
            .asSequence()
            .mapNotNull { key -> errors.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            .mapNotNull { array -> array.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString }
            .firstOrNull { it.isNotBlank() }
    }
}
