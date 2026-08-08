package com.example.cashbookbd.ui.customer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.CustomerRepository
import com.example.cashbookbd.data.repository.NewCustomer
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.reports.model.SelectorOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Backs the (essential) Add Customer form: collects the fields and saves them. */
class AddCustomerViewModel(
    private val repository: CustomerRepository,
    settings: com.example.cashbookbd.session.Settings?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddCustomerUiState(
            showBangla = settings?.useBangla == true,
            showSex = settings?.needCustomerSex == true,
            showArea = settings?.needCustomerArea == true,
            showOpening = settings?.openingOngoing == true,
            showRelation = settings?.needRelationInfo == true,
            showMotherName = settings?.needCustomerMotherName == true,
            showDateOfBirth = settings?.needCustomerDateOfBirth == true,
            showOccupation = settings?.needCustomerOccupation == true,
            showContactPerson = settings?.needCustomerContactPerson == true,
            showPermanentAddress = settings?.needCustomerPermanentAddress == true,
            showIdfrCode = settings?.haveCustomerSl == true,
            showPhoto = settings?.needCustomerPhoto == true,
        )
    )
    val uiState: StateFlow<AddCustomerUiState> = _uiState.asStateFlow()

    private var mobileCheckJob: kotlinx.coroutines.Job? = null

    init {
        if (_uiState.value.showArea) loadAreas()
    }

    private fun loadAreas() {
        _uiState.update { it.copy(isAreasLoading = true) }
        viewModelScope.launch {
            when (val result = repository.fetchAreas()) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        isAreasLoading = false,
                        areas = result.data.map { area ->
                            SelectorOption(
                                id = area.id,
                                label = area.name,
                                sublabel = listOf(area.thana, area.district)
                                    .filter { it.isNotBlank() }
                                    .joinToString(", ")
                                    .ifBlank { null },
                            )
                        },
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isAreasLoading = false,
                        error = it.error ?: result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun onType(option: SelectorOption) = _uiState.update { it.copy(type = option) }
    fun onSex(option: SelectorOption) = _uiState.update { it.copy(sex = option) }
    fun onArea(option: SelectorOption) = _uiState.update { it.copy(area = option) }
    fun onName(value: String) = _uiState.update { it.copy(name = value) }
    fun onBangla(value: String) = _uiState.update { it.copy(bangla = value) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value) }
    fun onMobile(value: String) {
        _uiState.update { it.copy(mobile = value, mobileWarning = null) }
        // The web checks on blur; here a short pause after typing does the same
        // job. A warning only — the save stays allowed, exactly like the web.
        mobileCheckJob?.cancel()
        val mobile = value.trim()
        if (mobile.length < 6) return
        mobileCheckJob = viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            val check = repository.checkMobile(mobile) ?: return@launch
            if (check.exists) {
                _uiState.update {
                    it.copy(
                        mobileWarning = "This mobile is already used" +
                            (check.ownerName?.let { name -> " by $name" } ?: "") + "."
                    )
                }
            }
        }
    }

    fun onRelation(option: SelectorOption) = _uiState.update { it.copy(relation = option) }
    fun onFather(value: String) = _uiState.update { it.copy(father = value) }
    fun onMotherName(value: String) = _uiState.update { it.copy(motherName = value) }
    fun onDateOfBirth(value: String) = _uiState.update { it.copy(dateOfBirth = value) }
    fun onOccupation(value: String) = _uiState.update { it.copy(occupation = value) }
    fun onContactPerson(value: String) = _uiState.update { it.copy(contactPerson = value) }
    fun onContactNumber(value: String) = _uiState.update { it.copy(contactNumber = value) }
    fun onPermanentAddress(value: String) = _uiState.update { it.copy(permanentAddress = value) }
    fun onIdfrCode(value: String) = _uiState.update { it.copy(idfrCode = value) }
    fun onCustomerLogin(value: Boolean) = _uiState.update { it.copy(customerLogin = value) }
    fun onPassword(value: String) = _uiState.update { it.copy(password = value) }

    /** Encodes the picked image to the web's ≤150 KB data URI, off the UI thread. */
    fun onPhotoPicked(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val encoded = encodeCustomerPhoto(context.applicationContext, uri)
            _uiState.update {
                if (encoded == null) {
                    it.copy(error = "The photo could not be read, or won't fit under 150 KB.")
                } else {
                    it.copy(photo = encoded)
                }
            }
        }
    }

    fun onPhotoCleared() = _uiState.update { it.copy(photo = "") }
    fun onLedgerPage(value: String) = _uiState.update { it.copy(ledgerPage = value) }
    fun onNationalId(value: String) = _uiState.update { it.copy(nationalId = value) }
    fun onOpeningBalance(value: String) = _uiState.update { it.copy(openingBalance = value) }

    fun save() {
        val state = _uiState.value
        val type = state.type ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.storeCustomer(
                NewCustomer(
                    typeId = type.id,
                    name = state.name,
                    bangla = if (state.showBangla) state.bangla else "",
                    address = state.address,
                    mobile = state.mobile,
                    ledgerPage = state.ledgerPage,
                    nationalId = state.nationalId,
                    sex = state.sex?.id.orEmpty(),
                    areaId = state.area?.id.orEmpty(),
                    // Never sent from a branch that is not keying openings, even
                    // if something had left a value behind in the field.
                    openingBalance = if (state.showOpening) state.openingBalance else "",
                    relationId = if (state.showRelation) state.relation?.id.orEmpty() else "",
                    father = if (state.showRelation) state.father else "",
                    motherName = if (state.showMotherName) state.motherName else "",
                    dateOfBirth = if (state.showDateOfBirth) state.dateOfBirth else "",
                    occupation = if (state.showOccupation) state.occupation else "",
                    contactPerson = if (state.showContactPerson) state.contactPerson else "",
                    contactNumber = if (state.showContactPerson) state.contactNumber else "",
                    permanentAddress = if (state.showPermanentAddress) state.permanentAddress else "",
                    idfrCode = if (state.showIdfrCode) state.idfrCode else "",
                    customerLogin = state.customerLogin,
                    password = state.password,
                    photo = if (state.showPhoto) state.photo else "",
                )
            )
            when (result) {
                is Resource.Success -> _uiState.update {
                    it.copy(isSaving = false, savedMessage = result.data)
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

    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                AddCustomerViewModel(
                    repository = ServiceLocator.provideCustomerRepository(appContext),
                    settings = ServiceLocator.provideSessionManager(appContext).state.value.settings,
                )
            }
        }
    }
}
