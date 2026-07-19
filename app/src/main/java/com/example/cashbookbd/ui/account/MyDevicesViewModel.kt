package com.example.cashbookbd.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.DeviceRepository
import com.example.cashbookbd.data.repository.UserDevice
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyDevicesUiState(
    val isLoading: Boolean = false,
    val devices: List<UserDevice> = emptyList(),
    val deviceLimit: Int? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    /** Device ids with a sign-out in flight — their row shows a spinner. */
    val revoking: Set<Long> = emptySet(),
    /** One-shot message for the snackbar. */
    val message: String? = null,
)

/** Drives the My Devices screen: list the user's sessions and sign them out. */
class MyDevicesViewModel(
    private val repository: DeviceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyDevicesUiState())
    val uiState: StateFlow<MyDevicesUiState> = _uiState.asStateFlow()

    fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val result = repository.getDevices()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        devices = result.data.devices,
                        deviceLimit = result.data.deviceLimit,
                        error = null,
                    )
                }

                is Resource.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message,
                        sessionExpired = it.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    /**
     * Signs [device] out. Refuses the current device: revoking our own token
     * would log this phone out from a screen that looks like it only affects
     * another device.
     */
    fun revoke(device: UserDevice) {
        if (device.isCurrent || device.id in _uiState.value.revoking) return

        _uiState.update { it.copy(revoking = it.revoking + device.id) }

        viewModelScope.launch {
            when (val result = repository.revokeDevice(device.id)) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(
                        revoking = state.revoking - device.id,
                        // Drop it locally; the freed slot is reflected on next load.
                        devices = state.devices.filterNot { it.id == device.id },
                        message = result.data,
                    )
                }

                is Resource.Error -> _uiState.update { state ->
                    state.copy(
                        revoking = state.revoking - device.id,
                        message = result.message,
                        sessionExpired = state.sessionExpired || result.isUnauthorized,
                    )
                }

                Resource.Loading -> Unit
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSessionExpiredHandled() = _uiState.update { it.copy(sessionExpired = false) }

    companion object {
        fun provideFactory(context: Context) = viewModelFactory {
            initializer {
                MyDevicesViewModel(
                    repository = ServiceLocator.provideDeviceRepository(context.applicationContext),
                )
            }
        }
    }
}
