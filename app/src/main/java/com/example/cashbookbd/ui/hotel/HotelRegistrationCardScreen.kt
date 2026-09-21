package com.example.cashbookbd.ui.hotel

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.cashbookbd.core.AmountFormat
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.repository.HotelRegistrationCard
import com.example.cashbookbd.data.repository.HotelRegistrationCards
import com.example.cashbookbd.data.repository.HotelRepository
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton

/**
 * Every guest recorded on a booking, one card each, two to an A4 sheet — the
 * rate a guest signs for is the room's rate on the night they were let it,
 * never today's tariff (`bookings/allotment/{id}/card`). Printed the same way
 * [HotelBillPaperScreen] is: a WebView loaded with a built-in paper, handed to
 * Android's print service.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HotelRegistrationCardScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    bookingId: Long,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.provideHotelRepository(context) }

    var html by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(bookingId) {
        when (val result = repository.fetchRegistrationCards(bookingId)) {
            is Resource.Success -> html = registrationCardHtml(result.data)
            is Resource.Error -> if (result.isUnauthorized) onLogout() else error = result.message
            Resource.Loading -> Unit
        }
    }

    AuthenticatedShell(
        title = "Registration Cards",
        currentRoute = Routes.HOTEL,
        navController = navController,
        onLogout = onLogout,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            when {
                error != null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(error!!, color = MaterialTheme.colorScheme.error) }

                html == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                else -> {
                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    webViewClient = WebViewClient()
                                    webView = this
                                }
                            },
                            update = { view -> view.loadDataWithBaseURL(null, html!!, "text/html", "utf-8", null) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SecondaryButton(text = "Back", onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f))
                        PrimaryButton(
                            text = "Print",
                            onClick = {
                                val view = webView ?: return@PrimaryButton
                                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                printManager.print(
                                    "Registration Cards",
                                    view.createPrintDocumentAdapter("Registration Cards"),
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

private fun esc(text: String): String = buildString(text.length) {
    text.forEach { c ->
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}

private const val CARD_CSS = """
  @page { size: A4; margin: 10mm; }
  * { box-sizing: border-box; }
  body { font-family: Arial, Helvetica, sans-serif; font-size: 12px; color: #111; margin: 0; padding: 0; background: #fff; }
  .sheet { display: flex; flex-direction: column; gap: 10mm; }
  .card { border: 1px solid #333; padding: 10px 12px; page-break-inside: avoid; }
  .card h1 { font-size: 15px; margin: 0 0 2px 0; }
  .card .sub { color: #444; font-size: 10px; margin-bottom: 6px; }
  .card table { border-collapse: collapse; width: 100%; }
  .card td { padding: 2px 6px 2px 0; vertical-align: top; font-size: 11px; }
  .card td.k { color: #555; white-space: nowrap; width: 110px; }
  .primary-tag { font-size: 10px; color: #555; }
  .sign { display: flex; justify-content: space-between; margin-top: 22px; font-size: 10px; }
  .sign div { border-top: 1px solid #333; padding-top: 4px; width: 42%; text-align: center; }
  .terms { margin-top: 8px; font-size: 9px; color: #444; white-space: pre-wrap; }
"""

private fun oneCard(data: HotelRegistrationCards, card: HotelRegistrationCard): String = buildString {
    append("<div class=\"card\">")
    append("<h1>").append(esc(data.branchName.ifBlank { "Registration Card" })).append("</h1>")
    val subLine = listOf(data.branchAddress, data.branchPhone.takeIf { it.isNotBlank() }?.let { "Phone: $it" })
        .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
    if (subLine.isNotBlank()) append("<div class=\"sub\">").append(esc(subLine)).append("</div>")
    append("<table>")
    append("<tr><td class=\"k\">Booking No</td><td>").append(esc(data.bookingNo)).append("</td></tr>")
    append("<tr><td class=\"k\">Room</td><td>").append(esc(card.room)).append("</td></tr>")
    append("<tr><td class=\"k\">Stay</td><td>")
        .append(esc(hotelDate(data.checkInDate))).append(" → ").append(esc(hotelDate(data.checkOutDate)))
        .append(if (data.nights > 0) " · ${data.nights} night${if (data.nights == 1) "" else "s"}" else "")
        .append("</td></tr>")
    append("<tr><td class=\"k\">Rate</td><td>").append(card.rate?.let { AmountFormat.format(it) } ?: "—").append("</td></tr>")
    append("<tr><td class=\"k\">Guest</td><td>").append(esc(card.guestName.ifBlank { "—" }))
    if (card.isPrimary) append(" <span class=\"primary-tag\">(primary)</span>")
    append("</td></tr>")
    if (card.guestMobile.isNotBlank()) append("<tr><td class=\"k\">Mobile</td><td>").append(esc(card.guestMobile)).append("</td></tr>")
    if (card.guestNationalId.isNotBlank()) {
        append("<tr><td class=\"k\">NID / Passport</td><td>").append(esc(card.guestNationalId)).append("</td></tr>")
    }
    if (card.guestAddress.isNotBlank()) append("<tr><td class=\"k\">Address</td><td>").append(esc(card.guestAddress)).append("</td></tr>")
    val personal = listOfNotNull(
        card.guestGender.takeIf { it.isNotBlank() },
        card.guestAge.takeIf { it.isNotBlank() }?.let { "$it yrs" },
        if (card.isChild) "child" else null,
    ).joinToString(", ")
    if (personal.isNotBlank()) append("<tr><td class=\"k\">Details</td><td>").append(esc(personal)).append("</td></tr>")
    append("</table>")
    if (data.terms.isNotBlank()) append("<div class=\"terms\">").append(esc(data.terms)).append("</div>")
    append("<div class=\"sign\"><div>Guest signature</div><div>Front desk</div></div>")
    append("</div>")
}

/** Two cards to an A4 sheet — the sheet simply stacks every card in order. */
internal fun registrationCardHtml(data: HotelRegistrationCards): String = buildString {
    append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=800\">")
    append("<style>").append(CARD_CSS).append("</style></head><body><div class=\"sheet\">")
    if (data.cards.isEmpty()) {
        append("<div class=\"card\">Nobody has been checked in yet.</div>")
    } else {
        data.cards.forEach { append(oneCard(data, it)) }
    }
    append("</div></body></html>")
}
