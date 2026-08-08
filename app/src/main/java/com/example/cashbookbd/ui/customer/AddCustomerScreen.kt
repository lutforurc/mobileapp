package com.example.cashbookbd.ui.customer

import com.example.cashbookbd.ui.theme.muted
import com.example.cashbookbd.ui.theme.appColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.AppSelectDropdown
import com.example.cashbookbd.ui.components.AppTextField
import com.example.cashbookbd.ui.components.PrimaryButton

/**
 * Adds a customer/supplier contact — the essential fields of the web's
 * AddCustomerSupplier form (Type, Name, Address, Mobile, Ledger Page, National ID).
 */
@Composable
fun AddCustomerScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddCustomerViewModel = viewModel(
        factory = AddCustomerViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
    // Saved: hand the confirmation to the list, which reloads and shows it.
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.CREATED_MESSAGE, message)
        navController.popBackStack()
    }

    AuthenticatedShell(
        title = "Add Customer",
        currentRoute = Routes.CUSTOMERS,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Shown only when the branch collects customer areas; picking one
                // makes the server compose the address from area/thana/district.
                if (state.showArea) {
                    AppSelectDropdown(
                        label = "Select Area",
                        options = state.areas,
                        selected = state.area,
                        onSelected = viewModel::onArea,
                        placeholder = if (state.isAreasLoading) "Loading areas…" else "Select Area",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppSelectDropdown(
                    label = "Type",
                    options = CUSTOMER_TYPES,
                    selected = state.type,
                    onSelected = viewModel::onType,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.name,
                    onValueChange = viewModel::onName,
                    label = "Enter name",
                    caption = "Name",
                    modifier = Modifier.fillMaxWidth(),
                )
                // The branch keeps its ledgers in Bangla, so the name is written
                // twice — the English one for the software, this one for what is
                // read out at the counter.
                if (state.showBangla) {
                    AppTextField(
                        value = state.bangla,
                        onValueChange = viewModel::onBangla,
                        label = "Enter name in Bangla",
                        caption = "Name (Bangla)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.showRelation) {
                    AppSelectDropdown(
                        label = "Relation",
                        options = RELATION_TYPES,
                        selected = state.relation,
                        onSelected = viewModel::onRelation,
                        placeholder = "Select Relation",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        value = state.father,
                        onValueChange = viewModel::onFather,
                        label = "Enter relation's name",
                        caption = "Relation's Name",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.showMotherName) {
                    AppTextField(
                        value = state.motherName,
                        onValueChange = viewModel::onMotherName,
                        label = "Enter mother's name",
                        caption = "Mother's Name",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Shown only when the branch collects customer sex.
                if (state.showSex) {
                    AppSelectDropdown(
                        label = "Sex",
                        options = CUSTOMER_SEX_OPTIONS,
                        selected = state.sex,
                        onSelected = viewModel::onSex,
                        placeholder = "Select Sex",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.showDateOfBirth) {
                    val context = LocalContext.current
                    AppTextField(
                        value = state.dateOfBirth,
                        onValueChange = {},
                        label = "Date of Birth",
                        enabled = false,
                        trailingIcon = {
                            androidx.compose.material3.IconButton(onClick = {
                                pickCustomerDate(context, state.dateOfBirth, viewModel::onDateOfBirth)
                            }) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Filled.DateRange,
                                    contentDescription = "Pick date of birth",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.showOccupation) {
                    AppTextField(
                        value = state.occupation,
                        onValueChange = viewModel::onOccupation,
                        label = "Enter occupation",
                        caption = "Occupation",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.showContactPerson) {
                    AppTextField(
                        value = state.contactPerson,
                        onValueChange = viewModel::onContactPerson,
                        label = "Enter contact person",
                        caption = "Contact Person",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        value = state.contactNumber,
                        onValueChange = viewModel::onContactNumber,
                        label = "Enter contact number",
                        caption = "Contact Number",
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppTextField(
                    value = state.nationalId,
                    onValueChange = viewModel::onNationalId,
                    label = "Enter national ID",
                    caption = "National ID (optional)",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.mobile,
                    onValueChange = viewModel::onMobile,
                    label = "Enter mobile number",
                    // The web's on-blur duplicate warning — informational only.
                    caption = state.mobileWarning ?: "Mobile Number",
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = state.address,
                    onValueChange = viewModel::onAddress,
                    label = "Enter Present Address",
                    caption = "Present Address",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.showPermanentAddress) {
                    AppTextField(
                        value = state.permanentAddress,
                        onValueChange = viewModel::onPermanentAddress,
                        label = "Enter Permanent Address",
                        caption = "Permanent Address",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppTextField(
                    value = state.ledgerPage,
                    onValueChange = viewModel::onLedgerPage,
                    label = "Enter ledger page",
                    caption = "Ledger Page (optional)",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.showOpening) {
                    AppTextField(
                        value = state.openingBalance,
                        onValueChange = viewModel::onOpeningBalance,
                        label = "Enter opening balance",
                        caption = "Opening Balance (optional)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Entered once. Afterwards it can only be changed by " +
                            "clearing the branch's opening.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.showIdfrCode) {
                    AppTextField(
                        value = state.idfrCode,
                        onValueChange = viewModel::onIdfrCode,
                        label = "Enter customer number",
                        caption = "Customer Number",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Access Customer Login",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Lets this customer sign in to the portal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textOnScreenMuted,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = state.customerLogin,
                        onCheckedChange = viewModel::onCustomerLogin,
                    )
                }
                if (state.customerLogin) {
                    AppTextField(
                        value = state.password,
                        onValueChange = viewModel::onPassword,
                        label = "Portal Password",
                        caption = "Min 8 characters (optional — can be set later).",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.showPhoto) {
                    val context = LocalContext.current
                    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uri -> uri?.let { viewModel.onPhotoPicked(context, it) } }
                    CustomerPhotoField(
                        photo = state.photo,
                        existingUrl = null,
                        onPick = { pickPhoto.launch("image/*") },
                        onClear = viewModel::onPhotoCleared,
                    )
                }
                if (!state.canSave) {
                    Text(
                        text = "Type, Name, Present Address and Mobile are required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textOnScreenMuted,
                    )
                }
                PrimaryButton(
                    text = "Save Customer",
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    isLoading = state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
