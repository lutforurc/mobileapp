package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.session.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Loads the current user's app settings (and, crucially, their permission list)
 * from `POST /settings/get-settings`. Maps every outcome to a [Resource] and
 * flags a 401 via [Resource.Error.isUnauthorized] so callers can force re-login.
 */
class SettingsRepository(
    private val api: ApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    suspend fun getSettings(): Resource<Settings> = withContext(ioDispatcher) {
        try {
            val response = api.getSettings(emptyMap())

            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    "Server error (${response.code()}). Please try again later."
                )
            }

            val body = response.body()
                ?: return@withContext Resource.Error("Invalid response from server.")
            if (!body.success) {
                return@withContext Resource.Error(
                    body.error?.message ?: body.message?.ifBlank { null } ?: "Settings load failed."
                )
            }

            val payload = body.data?.payload
            val permissions = payload?.permissions
                .orEmpty()
                .mapNotNull { it.toPermission() }
            Resource.Success(
                Settings(
                    permissions = permissions,
                    userId = payload?.user?.id,
                    businessTypeId = payload?.branch?.businessTypeId,
                    inventorySystemId = payload?.branch?.inventorySystemId,
                    branchId = payload?.branch?.id,
                    branchTypesId = payload?.branch?.branchTypesId,
                    combinedInvoiceNote = payload?.branch?.combinedInvoiceNote?.trim()
                        .let { !it.isNullOrEmpty() && it != "0" },
                    userName = payload?.user?.name?.takeIf { it.isNotBlank() },
                    userEmail = payload?.user?.email?.takeIf { it.isNotBlank() },
                    userPhotoUrl = payload?.user?.profilePhoto?.takeIf { it.isNotBlank() },
                    transactionDate = payload?.trxDt?.takeIf { it.isNotBlank() },
                    decimalPlaces = payload?.branch?.decimalPlaces?.trim()?.toIntOrNull(),
                    // The web gates the Product List's opening columns on == 1.
                    openingOngoing = payload?.branch?.isOpening?.trim()?.toDoubleOrNull() == 1.0,
                    // A branch column, not a meta — arrives as a number. The web
                    // reads it loosely (String(x) === '1'), so parse numerically.
                    useBangla = payload?.branch?.useBangla?.trim()?.toDoubleOrNull() == 1.0,
                    stockReportTypeGrouped = payload?.branch?.stockReportType?.trim()
                        ?.toDoubleOrNull() == 1.0,
                    needDemoTutorial = payload?.branch?.needDemoTutorial?.trim() == "1",
                    // The web checks String(x) === '1' on each of these metas.
                    needCustomerArea = payload?.branch?.needCustomerArea?.trim() == "1",
                    needCustomerDateOfBirth = payload?.branch?.needCustomerDateOfBirth?.trim() == "1",
                    needCustomerOccupation = payload?.branch?.needCustomerOccupation?.trim() == "1",
                    needCustomerPermanentAddress = payload?.branch?.needCustomerPermanentAddress?.trim() == "1",
                    needCustomerPhoto = payload?.branch?.needCustomerPhoto?.trim() == "1",
                    needNomineePhoto = payload?.branch?.needNomineePhoto?.trim() == "1",
                    needCustomerMotherName = payload?.branch?.needCustomerMotherName?.trim() == "1",
                    needCustomerContactPerson = payload?.branch?.needCustomerContactPerson?.trim() == "1",
                    needRelationInfo = payload?.branch?.needRelationInfo?.trim() == "1",
                    haveIsGuaranter = payload?.branch?.haveIsGuaranter?.trim() == "1",
                    haveCustomerNominee = payload?.branch?.haveCustomerNominee?.trim() == "1",
                    // A branch column like use_bangla — arrives numeric ("1"/"1.0").
                    haveCustomerSl = payload?.branch?.haveCustomerSl?.trim()?.toDoubleOrNull() == 1.0,
                    needCustomerSex = payload?.branch?.needCustomerSex?.trim() == "1",
                    multiProductOrder = payload?.branch?.multiProductOrder?.trim() == "1",
                    showVoucherImage = payload?.branch?.showVoucherImage?.trim() == "1",
                    isLocalEnv = payload?.env?.trim().equals("local", ignoreCase = true),
                    // Text metas arrive as the string, or boolean false when the
                    // branch never wrote them (Gson reads that into "false").
                    letterRefPrefix = payload?.branch?.letterRefPrefix?.trim()
                        ?.takeUnless { it.isEmpty() || it == "false" },
                    letterRefDate = payload?.branch?.letterRefDate?.trim()
                        ?.takeUnless { it.isEmpty() || it == "false" }
                        // A native date field only holds yyyy-MM-dd; cut like the web.
                        ?.take(10),
                )
            )
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
}