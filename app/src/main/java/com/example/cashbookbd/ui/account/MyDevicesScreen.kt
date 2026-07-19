package com.example.cashbookbd.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.cashbookbd.data.repository.UserDevice
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton

/**
 * My Devices: every session signed in with this account, and a way to sign the
 * others out. Mirrors the web's `/my-devices`; the count is what the plan's
 * per-user device limit is enforced against at login.
 */
@Composable
fun MyDevicesScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyDevicesViewModel = viewModel(
        factory = MyDevicesViewModel.provideFactory(LocalContext.current)
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onLogout()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    AuthenticatedShell(
        title = "My Devices",
        currentRoute = Routes.MY_DEVICES,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.isLoading && state.devices.isEmpty() -> Center { CircularProgressIndicator() }

                    state.error != null && state.devices.isEmpty() -> Center {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(text = "Retry", onClick = viewModel::load)
                        }
                    }

                    state.devices.isEmpty() -> Center {
                        Text(
                            text = "No active devices found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> DeviceList(
                        devices = state.devices,
                        deviceLimit = state.deviceLimit,
                        revoking = state.revoking,
                        onRevoke = viewModel::revoke,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<UserDevice>,
    deviceLimit: Int?,
    revoking: Set<Long>,
    onRevoke: (UserDevice) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = deviceLimit?.let { "${devices.size} of $it devices in use" }
                    ?: "${devices.size} active devices",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(devices, key = { it.id }) { device ->
            DeviceCard(
                device = device,
                isRevoking = device.id in revoking,
                onRevoke = { onRevoke(device) },
            )
        }
    }
}

@Composable
private fun DeviceCard(device: UserDevice, isRevoking: Boolean, onRevoke: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (device.isCurrent) ThisDevicePill()
            }

            device.ip?.let { DetailRow("IP", it) }
            device.lastUsedAt?.let { DetailRow("Last used", it) }
            device.createdAt?.let { DetailRow("Signed in", it) }

            // The current device is never revocable — see MyDevicesViewModel.revoke.
            if (!device.isCurrent) {
                if (isRevoking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    SecondaryButton(text = "Sign out", onClick = onRevoke)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ThisDevicePill() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "This device",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}
