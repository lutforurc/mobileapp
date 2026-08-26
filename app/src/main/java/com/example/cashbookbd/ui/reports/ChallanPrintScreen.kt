package com.example.cashbookbd.ui.reports

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton

/**
 * The delivery challan, rendered and handed to Android's print service.
 *
 * The paper arrives as ready-made HTML from `sales/challan/{id}` — the
 * token-authenticated door the API added for exactly this client (the web's
 * cookie route would answer with the login page). A WebView draws it and
 * `createPrintDocumentAdapter` turns the drawn page into the system print
 * dialog's document, so what prints is what is on screen — the same page the
 * Blade route has always printed.
 *
 * The web app draws its challan through the branch's own designed layout
 * instead; that print-designer engine stays web-only, and this screen prints
 * the built-in paper.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChallanPrintScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    voucherId: Long,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.provideTradeLedgerRepository(context) }

    var html by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(voucherId) {
        when (val result = repository.fetchChallanHtml(voucherId)) {
            is Resource.Success -> html = result.data
            is Resource.Error -> {
                if (result.isUnauthorized) onLogout() else error = result.message
            }
            Resource.Loading -> Unit
        }
    }

    AuthenticatedShell(
        title = "Delivery Challan",
        currentRoute = Routes.TRADE_LEDGER_PATTERN,
        navController = navController,
        onLogout = onLogout,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            when {
                error != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

                html == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                else -> {
                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    // The paper is wider than a phone; start
                                    // zoomed-out and let fingers do the rest.
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    webViewClient = WebViewClient()
                                    webView = this
                                }
                            },
                            update = { view ->
                                // The host root, so the page's absolute links
                                // (its CDN stylesheet, the letterhead image)
                                // resolve exactly as they do on the web.
                                view.loadDataWithBaseURL(
                                    BuildConfig.BASE_URL.trimEnd('/').removeSuffix("/api"),
                                    html!!,
                                    "text/html",
                                    "utf-8",
                                    null,
                                )
                            },
                        )
                    }
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        SecondaryButton(
                            text = "Back",
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "Print",
                            onClick = {
                                val view = webView ?: return@PrimaryButton
                                val printManager =
                                    context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                printManager.print(
                                    "Delivery Challan",
                                    view.createPrintDocumentAdapter("Delivery Challan"),
                                    PrintAttributes.Builder().build(),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
