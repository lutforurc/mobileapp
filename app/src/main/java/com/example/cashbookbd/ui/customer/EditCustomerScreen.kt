package com.example.cashbookbd.ui.customer

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.CustomerArea
import com.example.cashbookbd.data.repository.CustomerDetail
import com.example.cashbookbd.data.repository.CustomerForm
import com.example.cashbookbd.data.repository.CustomerRepository
import com.example.cashbookbd.data.repository.toGuarantorRows
import com.example.cashbookbd.data.repository.toNomineeRows
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.session.Settings
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.appColors
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The web's full Edit Customer form: prefilled from `contact/edit/{id}`, every
 * field the web shows under the same branch-setting gates, saved to
 * `contact/update/{id}`. An opening entered here goes through the ui endpoint
 * (the only path that raises the voucher); the portal password has its own
 * Save; the guarantor/nominee arrays are preserved verbatim until their
 * screens land (their data survives every save).
 */
data class EditCustomerUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val detail: CustomerDetail? = null,

    // The editable fields (prefilled once the detail lands).
    val typeId: String = "",
    val areaId: String = "",
    val name: String = "",
    val bangla: String = "",
    val relationId: String = "",
    val father: String = "",
    val motherName: String = "",
    val occupation: String = "",
    val sex: String = "",
    /** yyyy-MM-dd, or blank. */
    val dateOfBirth: String = "",
    val contactPerson: String = "",
    val contactNumber: String = "",
    val mobile: String = "",
    val nationalId: String = "",
    val presentAddress: String = "",
    val permanentAddress: String = "",
    val idfrCode: String = "",
    val ledgerPage: String = "",
    val opening: String = "",
    val customerLogin: Boolean = false,
    val portalPassword: String = "",
    /**
     * The photo change: null = untouched (key omitted, stored photo kept);
     * "" = delete; else a freshly encoded data URI.
     */
    val photoAction: String? = null,
    /** The editable guarantor/nominee rows, prefilled from the detail. */
    val guarantors: List<com.example.cashbookbd.data.repository.GuarantorRow> = emptyList(),
    val nominees: List<com.example.cashbookbd.data.repository.NomineeRow> = emptyList(),

    val areas: List<CustomerArea> = emptyList(),

    val isSaving: Boolean = false,
    val isSavingPassword: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
    /** One-shot snackbar for the portal-password save. */
    val passwordMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** The web's client-side requireds: type, name, mobile, present address. */
    val canSave: Boolean
        get() = !isSaving && detail != null && typeId.isNotBlank() &&
            name.isNotBlank() && mobile.isNotBlank() && presentAddress.isNotBlank()

    /** Min 8 like the server; blank is allowed — it revokes portal access. */
    val canSavePassword: Boolean
        get() = !isSavingPassword && detail != null &&
            (portalPassword.isBlank() || portalPassword.length >= 8)
}

class EditCustomerViewModel(
    private val customerId: String,
    private val repository: CustomerRepository,
    val settings: Settings?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditCustomerUiState())
    val uiState: StateFlow<EditCustomerUiState> = _uiState.asStateFlow()

    init {
        load()
        if (settings?.needCustomerArea == true) loadAreas()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.fetchCustomerDetail(customerId)) {
                is Resource.Success -> {
                    val d = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = d,
                            typeId = d.partyTypeId,
                            areaId = d.areaId,
                            name = d.name,
                            bangla = d.bangla,
                            relationId = d.relationId,
                            father = d.father,
                            motherName = d.motherName,
                            occupation = d.occupation,
                            sex = d.sex,
                            dateOfBirth = d.dateOfBirth,
                            contactPerson = d.contactPerson,
                            contactNumber = d.contactNumber,
                            mobile = d.mobile,
                            nationalId = d.nationalId,
                            presentAddress = d.manualAddress,
                            permanentAddress = d.permanentAddress,
                            idfrCode = d.idfrCode,
                            ledgerPage = d.ledgerPage,
                            opening = d.openingBalance,
                            customerLogin = d.customerLogin,
                            guarantors = d.guarantorsRaw.toGuarantorRows(),
                            nominees = d.nomineesRaw.toNomineeRows(),
                        )
                    }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun loadAreas() {
        viewModelScope.launch {
            (repository.fetchAreas() as? Resource.Success)?.let { result ->
                _uiState.update { it.copy(areas = result.data) }
            }
        }
    }

    // Field setters.
    fun onType(v: String) = _uiState.update { it.copy(typeId = v) }
    fun onArea(v: String) = _uiState.update { it.copy(areaId = v) }
    fun onName(v: String) = _uiState.update { it.copy(name = v) }
    fun onBangla(v: String) = _uiState.update { it.copy(bangla = v) }
    fun onRelation(v: String) = _uiState.update { it.copy(relationId = v) }
    fun onFather(v: String) = _uiState.update { it.copy(father = v) }
    fun onMotherName(v: String) = _uiState.update { it.copy(motherName = v) }
    fun onOccupation(v: String) = _uiState.update { it.copy(occupation = v) }
    fun onSex(v: String) = _uiState.update { it.copy(sex = v) }
    fun onDateOfBirth(v: String) = _uiState.update { it.copy(dateOfBirth = v) }
    fun onContactPerson(v: String) = _uiState.update { it.copy(contactPerson = v) }
    fun onContactNumber(v: String) = _uiState.update { it.copy(contactNumber = v) }
    fun onMobile(v: String) = _uiState.update { it.copy(mobile = v) }
    fun onNationalId(v: String) = _uiState.update { it.copy(nationalId = v) }
    fun onPresentAddress(v: String) = _uiState.update { it.copy(presentAddress = v) }
    fun onPermanentAddress(v: String) = _uiState.update { it.copy(permanentAddress = v) }
    fun onIdfrCode(v: String) = _uiState.update { it.copy(idfrCode = v) }
    fun onLedgerPage(v: String) = _uiState.update { it.copy(ledgerPage = v) }
    fun onOpening(v: String) = _uiState.update { it.copy(opening = v) }
    fun onCustomerLogin(v: Boolean) = _uiState.update { it.copy(customerLogin = v) }
    fun onPortalPassword(v: String) = _uiState.update { it.copy(portalPassword = v) }

    /** Encodes the picked image to the web's ≤150 KB data URI, off the UI thread. */
    fun onPhotoPicked(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val encoded = encodeCustomerPhoto(context.applicationContext, uri)
            _uiState.update {
                if (encoded == null) {
                    it.copy(error = "The photo could not be read, or won't fit under 150 KB.")
                } else {
                    it.copy(photoAction = encoded)
                }
            }
        }
    }

    /** A pick is undone back to "untouched"; a stored photo is marked deleted. */
    fun onPhotoCleared() = _uiState.update {
        it.copy(photoAction = if (it.photoAction?.isNotBlank() == true) null else "")
    }

    // ---- Guarantor / Nominee rows ----

    fun onGuarantorAdd() = _uiState.update {
        it.copy(guarantors = it.guarantors + com.example.cashbookbd.data.repository.GuarantorRow())
    }

    fun onGuarantorChange(index: Int, row: com.example.cashbookbd.data.repository.GuarantorRow) =
        _uiState.update {
            it.copy(guarantors = it.guarantors.mapIndexed { i, g -> if (i == index) row else g })
        }

    fun onGuarantorRemove(index: Int) = _uiState.update {
        it.copy(guarantors = it.guarantors.filterIndexed { i, _ -> i != index })
    }

    fun onNomineeAdd() = _uiState.update {
        it.copy(nominees = it.nominees + com.example.cashbookbd.data.repository.NomineeRow())
    }

    fun onNomineeChange(index: Int, row: com.example.cashbookbd.data.repository.NomineeRow) =
        _uiState.update {
            it.copy(nominees = it.nominees.mapIndexed { i, n -> if (i == index) row else n })
        }

    fun onNomineeRemove(index: Int) = _uiState.update {
        it.copy(nominees = it.nominees.filterIndexed { i, _ -> i != index })
    }

    fun onNomineePhotoPicked(context: Context, index: Int, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val encoded = encodeCustomerPhoto(context.applicationContext, uri)
            _uiState.update { state ->
                if (encoded == null) {
                    state.copy(error = "The photo could not be read, or won't fit under 150 KB.")
                } else {
                    state.copy(
                        nominees = state.nominees.mapIndexed { i, n ->
                            if (i == index) n.copy(photo = encoded) else n
                        }
                    )
                }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val detail = state.detail ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.updateCustomer(
                id = customerId,
                form = CustomerForm(
                    partyTypeId = state.typeId,
                    areaId = state.areaId,
                    name = state.name,
                    bangla = state.bangla,
                    relationId = state.relationId,
                    father = state.father,
                    motherName = state.motherName,
                    occupation = state.occupation,
                    sex = state.sex,
                    dateOfBirth = state.dateOfBirth,
                    contactPerson = state.contactPerson,
                    contactNumber = state.contactNumber,
                    mobile = state.mobile,
                    nationalId = state.nationalId,
                    manualAddress = state.presentAddress,
                    permanentAddress = state.permanentAddress,
                    idfrCode = state.idfrCode,
                    ledgerPage = state.ledgerPage,
                    customerLogin = state.customerLogin,
                ),
                echo = detail,
                photo = if (settings?.needCustomerPhoto == true) state.photoAction else null,
                // The edited rows when the branch shows the panels; null keeps
                // the fetched arrays untouched otherwise.
                guarantors = if (settings?.haveIsGuaranter == true) {
                    state.guarantors.filter { it.name.isNotBlank() }
                } else {
                    null
                },
                nominees = if (settings?.haveCustomerNominee == true) {
                    state.nominees.filter { it.name.isNotBlank() }
                } else {
                    null
                },
            )
            when (result) {
                is Resource.Success -> {
                    // An opening typed here goes through the ui endpoint — the
                    // only path that stores it and raises the voucher. Enabled
                    // branch + unlocked + non-blank, the web's own strip rule.
                    val opening = state.opening.trim()
                    val openingResult =
                        if (settings?.openingOngoing == true && !detail.openingLocked &&
                            opening.isNotEmpty()
                        ) {
                            repository.updateOpeningLedger(customerId, opening, null)
                        } else {
                            null
                        }
                    val message = when (openingResult) {
                        is Resource.Error ->
                            "${result.data} — but the opening failed: ${openingResult.message}"
                        else -> result.data
                    }
                    _uiState.update { it.copy(isSaving = false, savedMessage = message) }
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** The portal password's own Save — blank revokes access, like the web. */
    fun savePortalPassword() {
        val state = _uiState.value
        if (!state.canSavePassword) return
        _uiState.update { it.copy(isSavingPassword = true) }
        viewModelScope.launch {
            val result = repository.setPortalPassword(customerId, state.portalPassword)
            _uiState.update {
                it.copy(
                    isSavingPassword = false,
                    passwordMessage = when (result) {
                        is Resource.Success -> result.data
                        is Resource.Error -> result.message
                        Resource.Loading -> null
                    },
                    portalPassword = if (result is Resource.Success) "" else it.portalPassword,
                    sessionExpired = it.sessionExpired ||
                        (result as? Resource.Error)?.isUnauthorized == true,
                )
            }
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onPasswordMessageShown() = _uiState.update { it.copy(passwordMessage = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context, customerId: String) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                EditCustomerViewModel(
                    customerId = customerId,
                    repository = ServiceLocator.provideCustomerRepository(appContext),
                    settings = ServiceLocator.provideSessionManager(appContext).state.value.settings,
                )
            }
        }
    }
}

@Composable
fun EditCustomerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    customerId: String? = null,
) {
    if (customerId.isNullOrBlank()) {
        AuthenticatedShell(
            title = "Edit Customer",
            currentRoute = Routes.CUSTOMERS,
            navController = navController,
            onLogout = onLogout,
            modifier = modifier,
        ) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Customer not found.", color = MaterialTheme.colorScheme.onBackground)
            }
        }
        return
    }

    val viewModel: EditCustomerViewModel = viewModel(
        key = customerId,
        factory = EditCustomerViewModel.provideFactory(LocalContext.current, customerId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val settings = viewModel.settings

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }
    LaunchedEffect(state.passwordMessage) {
        val message = state.passwordMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onPasswordMessageShown()
    }
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Edit Customer",
        currentRoute = Routes.CUSTOMERS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.loadError != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        state.loadError!!,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                else -> EditCustomerForm(
                    state = state,
                    settings = settings,
                    context = context,
                    viewModel = viewModel,
                )
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun EditCustomerForm(
    state: EditCustomerUiState,
    settings: Settings?,
    context: Context,
    viewModel: EditCustomerViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (settings?.needCustomerArea == true) {
            AppSelectDropdown(
                label = "Select Area",
                options = state.areas.map {
                    SelectorOption(it.id, it.name, listOf(it.thana, it.district).filter { s -> s.isNotBlank() }.joinToString(", "))
                },
                selected = state.areas.firstOrNull { it.id == state.areaId }
                    ?.let { SelectorOption(it.id, it.name) },
                onSelected = { viewModel.onArea(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppSelectDropdown(
            label = "Select Type *",
            options = CLIENT_TYPES,
            selected = CLIENT_TYPES.firstOrNull { it.id == state.typeId },
            onSelected = { viewModel.onType(it.id) },
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.name,
            onValueChange = viewModel::onName,
            label = "Name *",
            modifier = Modifier.fillMaxWidth(),
        )
        if (settings?.useBangla == true) {
            AppTextField(
                value = state.bangla,
                onValueChange = viewModel::onBangla,
                label = "Name (Bangla)",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (settings?.needRelationInfo == true) {
            AppSelectDropdown(
                label = "Relation",
                options = RELATION_TYPES,
                selected = RELATION_TYPES.firstOrNull { it.id == state.relationId },
                onSelected = { viewModel.onRelation(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = state.father,
                onValueChange = viewModel::onFather,
                label = "Relation's Name",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (settings?.needCustomerMotherName == true) {
            AppTextField(
                value = state.motherName,
                onValueChange = viewModel::onMotherName,
                label = "Mother's Name",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (settings?.needCustomerSex == true) {
            AppSelectDropdown(
                label = "Sex",
                options = SEX_TYPES,
                selected = SEX_TYPES.firstOrNull { it.id == state.sex },
                onSelected = { viewModel.onSex(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (settings?.needCustomerDateOfBirth == true) {
            AppTextField(
                value = state.dateOfBirth,
                onValueChange = {},
                label = "Date of Birth",
                enabled = false,
                trailingIcon = {
                    androidx.compose.material3.IconButton(onClick = {
                        pickIsoDate(context, state.dateOfBirth, viewModel::onDateOfBirth)
                    }) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.DateRange,
                            contentDescription = "Pick date of birth",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (settings?.needCustomerOccupation == true) {
            AppTextField(
                value = state.occupation,
                onValueChange = viewModel::onOccupation,
                label = "Occupation",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (settings?.needCustomerContactPerson == true) {
            AppTextField(
                value = state.contactPerson,
                onValueChange = viewModel::onContactPerson,
                label = "Contact Person",
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = state.contactNumber,
                onValueChange = viewModel::onContactNumber,
                label = "Contact Number",
                keyboardType = KeyboardType.Phone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppTextField(
            value = state.nationalId,
            onValueChange = viewModel::onNationalId,
            label = "National ID",
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.mobile,
            onValueChange = viewModel::onMobile,
            label = "Mobile Number *",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.presentAddress,
            onValueChange = viewModel::onPresentAddress,
            label = "Present Address *",
            modifier = Modifier.fillMaxWidth(),
        )
        if (settings?.needCustomerPermanentAddress == true) {
            AppTextField(
                value = state.permanentAddress,
                onValueChange = viewModel::onPermanentAddress,
                label = "Permanent Address",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppTextField(
            value = state.ledgerPage,
            onValueChange = viewModel::onLedgerPage,
            label = "Ledger Page",
            modifier = Modifier.fillMaxWidth(),
        )
        if (settings?.openingOngoing == true) {
            val locked = state.detail?.openingLocked == true
            AppTextField(
                value = state.opening,
                onValueChange = viewModel::onOpening,
                label = "Opening Balance (Optional)",
                // Set once, it can only be changed by clearing the branch's opening.
                enabled = !locked,
                caption = if (locked) {
                    "Already entered. It can only be changed by clearing the branch's opening."
                } else {
                    "Entered once. Afterwards it can only be changed by clearing the branch's opening."
                },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (settings?.haveCustomerSl == true) {
            AppTextField(
                value = state.idfrCode,
                onValueChange = viewModel::onIdfrCode,
                label = "Customer Number",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (settings?.needCustomerPhoto == true) {
            val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri -> uri?.let { viewModel.onPhotoPicked(context, it) } }
            CustomerPhotoField(
                photo = state.photoAction?.takeIf { it.isNotBlank() }.orEmpty(),
                existingUrl = if (state.photoAction == "") {
                    null // marked deleted
                } else {
                    state.detail?.photo?.let {
                        customerPhotoUrl(it, settings.isLocalEnv)
                    }
                },
                onPick = { pickPhoto.launch("image/*") },
                onClear = viewModel::onPhotoCleared,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Access Customer Login",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = AppFontWeight.SemiBold,
                )
                Text(
                    text = "Lets this customer sign in to the portal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
            }
            Switch(checked = state.customerLogin, onCheckedChange = viewModel::onCustomerLogin)
        }
        if (state.customerLogin) {
            AppTextField(
                value = state.portalPassword,
                onValueChange = viewModel::onPortalPassword,
                label = "Portal Password",
                caption = "Min 8 characters. Saving it blank revokes portal access.",
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = "Save Password",
                onClick = viewModel::savePortalPassword,
                enabled = state.canSavePassword,
                isLoading = state.isSavingPassword,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (settings?.haveIsGuaranter == true) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            GuarantorPanel(
                rows = state.guarantors,
                onChange = viewModel::onGuarantorChange,
                onAdd = viewModel::onGuarantorAdd,
                onRemove = viewModel::onGuarantorRemove,
            )
        }
        if (settings?.haveCustomerNominee == true) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            var photoRowIndex by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(-1)
            }
            val pickNomineePhoto = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri ->
                val index = photoRowIndex
                photoRowIndex = -1
                if (uri != null && index >= 0) viewModel.onNomineePhotoPicked(context, index, uri)
            }
            NomineePanel(
                rows = state.nominees,
                onChange = viewModel::onNomineeChange,
                onAdd = viewModel::onNomineeAdd,
                onRemove = viewModel::onNomineeRemove,
                showPhoto = settings.needNomineePhoto,
                onPickPhoto = { index ->
                    photoRowIndex = index
                    pickNomineePhoto.launch("image/*")
                },
            )
        }

        PrimaryButton(
            text = "Update",
            onClick = viewModel::save,
            enabled = state.canSave,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Picks a date and hands back yyyy-MM-dd (the key the server stores). */
private fun pickIsoDate(context: Context, current: String, onPicked: (String) -> Unit) {
    val parts = current.split("-").mapNotNull { it.toIntOrNull() }
    val initial = if (parts.size == 3) SimpleDate(parts[0], parts[1], parts[2]) else SimpleDate.today()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth))
        },
        initial.year,
        initial.month - 1,
        initial.day,
    ).show()
}
