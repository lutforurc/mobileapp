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
import com.example.cashbookbd.data.repository.HotelFolioRepository
import com.example.cashbookbd.data.repository.HotelPaper
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.navigation.Routes
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton

/**
 * The guest's bill, or one money receipt, drawn and handed to Android's print
 * service.
 *
 * The server sends FACTS, not a page: `folio/{id}/bill-paper` (or
 * `receipt/{paymentId}`) answers with the stay, the lines and the totals as
 * JSON, and the web draws them through the branch's own designed layout. That
 * print-designer engine stays web-only; this screen draws a built-in paper
 * from the same facts and prints it exactly as [com.example.cashbookbd.ui.reports.ChallanPrintScreen]
 * does — a WebView, and `createPrintDocumentAdapter` so what prints is what
 * is on screen.
 *
 * A receipt carries NO tax figure of any kind. VAT falls due on the BILL, and
 * a receipt with a VAT line on it becomes a VAT invoice whatever the desk
 * calls it. The server does not send one and this page does not invent one.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HotelBillPaperScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    bookingId: Long,
    paymentId: Long?,
) {
    val context = LocalContext.current
    val repository = remember { HotelFolioRepository.get(context) }
    val isReceipt = paymentId != null

    var html by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(bookingId, paymentId) {
        val result = if (paymentId != null) {
            repository.fetchReceiptPaper(bookingId, paymentId)
        } else {
            repository.fetchBillPaper(bookingId)
        }
        when (result) {
            is Resource.Success -> html = if (isReceipt) hotelReceiptHtml(result.data) else hotelBillHtml(result.data)
            is Resource.Error -> if (result.isUnauthorized) onLogout() else error = result.message
            Resource.Loading -> Unit
        }
    }

    val title = if (isReceipt) "Money Receipt" else "Guest Bill"

    AuthenticatedShell(
        title = title,
        currentRoute = Routes.HOTEL,
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
                ) { Text(error!!, color = MaterialTheme.colorScheme.error) }

                html == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

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
                                // Everything is inline — no base URL to resolve against.
                                view.loadDataWithBaseURL(null, html!!, "text/html", "utf-8", null)
                            },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                printManager.print(title, view.createPrintDocumentAdapter(title), PrintAttributes.Builder().build())
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  The built-in papers
// ---------------------------------------------------------------------------

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

/** A money figure off the paper's strings; junk or blank is a dash. */
private fun money(raw: String?): String = raw?.trim()?.toDoubleOrNull()?.let { AmountFormat.format(it) } ?: "—"

private fun moneyOrNull(raw: String?): Double? = raw?.trim()?.toDoubleOrNull()

private fun rateOf(raw: String?): String = raw?.trim()?.toDoubleOrNull()?.let { hotelRate(it) }.orEmpty()

private const val PAPER_CSS = """
  @page { size: A4; margin: 12mm; }
  * { box-sizing: border-box; }
  body { font-family: Arial, Helvetica, sans-serif; font-size: 12px; color: #111; margin: 0; padding: 16px; background: #fff; }
  .head { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #111; padding-bottom: 8px; margin-bottom: 10px; }
  .head h1 { font-size: 20px; margin: 0 0 2px 0; }
  .head .sub { color: #444; font-size: 11px; }
  .title { text-align: right; }
  .title h2 { font-size: 16px; margin: 0 0 4px 0; letter-spacing: 1px; }
  .title div { font-size: 11px; }
  .facts { display: flex; gap: 24px; margin-bottom: 10px; }
  .facts table { border-collapse: collapse; }
  .facts td { padding: 2px 6px 2px 0; vertical-align: top; font-size: 11px; }
  .facts td.k { color: #555; white-space: nowrap; }
  table.lines { width: 100%; border-collapse: collapse; margin-top: 6px; }
  table.lines th, table.lines td { border: 1px solid #999; padding: 4px 6px; font-size: 11px; }
  table.lines th { background: #eee; text-align: left; }
  table.lines td.n, table.lines th.n { text-align: right; white-space: nowrap; }
  table.lines td.c { text-align: center; }
  .small { color: #555; font-size: 10px; }
  .totals { width: 100%; margin-top: 8px; display: flex; justify-content: flex-end; }
  .totals table { border-collapse: collapse; min-width: 300px; }
  .totals td { padding: 3px 6px; font-size: 11px; }
  .totals td.n { text-align: right; white-space: nowrap; }
  .totals tr.big td { font-size: 13px; font-weight: bold; border-top: 1px solid #111; }
  .totals tr.due td { font-weight: bold; }
  .notes { margin-top: 10px; font-size: 11px; color: #333; }
  .sign { display: flex; justify-content: space-between; margin-top: 40px; font-size: 11px; }
  .sign div { border-top: 1px solid #333; padding-top: 4px; width: 40%; text-align: center; }
  .amount { font-size: 22px; font-weight: bold; margin: 12px 0; }
  .receipt-box { border: 1px solid #999; padding: 10px 12px; margin-top: 8px; }
  .receipt-box table td { padding: 3px 8px 3px 0; font-size: 12px; }
  .receipt-box td.k { color: #555; white-space: nowrap; }
"""

private fun head(paper: HotelPaper, title: String, rightLines: List<Pair<String, String>>): String = buildString {
    append("<div class=\"head\"><div>")
    append("<h1>").append(esc(paper.branchName.ifBlank { "Guest Bill" })).append("</h1>")
    if (paper.branchAddress.isNotBlank()) append("<div class=\"sub\">").append(esc(paper.branchAddress)).append("</div>")
    if (paper.branchPhone.isNotBlank()) append("<div class=\"sub\">Phone: ").append(esc(paper.branchPhone)).append("</div>")
    append("</div><div class=\"title\"><h2>").append(esc(title)).append("</h2>")
    rightLines.filter { it.second.isNotBlank() }.forEach { (k, v) ->
        append("<div><b>").append(esc(k)).append(":</b> ").append(esc(v)).append("</div>")
    }
    append("</div></div>")
}

private fun factRows(rows: List<Pair<String, String>>): String = buildString {
    append("<table>")
    rows.filter { it.second.isNotBlank() }.forEach { (k, v) ->
        append("<tr><td class=\"k\">").append(esc(k)).append("</td><td>").append(esc(v)).append("</td></tr>")
    }
    append("</table>")
}

/** The stay facts, in two columns. A walk-in has no stay, so its rows simply are not there. */
private fun stayFacts(paper: HotelPaper, forReceipt: Boolean): String {
    val guest = paper.fact("guest_name").ifBlank { paper.fact("booker_name") }
    val mobile = paper.fact("guest_mobile").ifBlank { paper.fact("booker_mobile") }
    val who = listOf(
        (if (forReceipt) "From" else "Guest") to guest,
        "Mobile" to mobile,
        "NID / Passport" to paper.fact("guest_nid"),
        "Address" to paper.fact("guest_address"),
        "Booked by" to paper.fact("booker_name").takeIf { it.isNotBlank() && it != guest }.orEmpty(),
        "Billed to" to paper.fact("billed_to"),
    )
    val adults = paper.fact("stated_adults").toIntOrNull() ?: 0
    val children = paper.fact("stated_children").toIntOrNull() ?: 0
    val party = buildList {
        if (adults > 0) add("$adults adult${if (adults == 1) "" else "s"}")
        if (children > 0) add("$children child${if (children == 1) "" else "ren"}")
    }.joinToString(", ")
    val stay = listOf(
        "Check-in" to hotelDate(paper.fact("check_in_date")),
        "Check-out" to hotelDate(paper.fact("check_out_date")),
        "Nights" to paper.fact("nights").takeIf { it.isNotBlank() && it != "0" }.orEmpty(),
        "Rooms" to paper.fact("room_list"),
        "Party" to party,
        "Booking type" to paper.fact("booking_type"),
        "Status" to paper.fact("booking_status"),
    )
    return "<div class=\"facts\">${factRows(who)}${factRows(stay)}</div>"
}

/** The bill: header, stay facts, the product table, the totals block, signatures. */
internal fun hotelBillHtml(paper: HotelPaper): String {
    val b = paper.basic
    val discount = moneyOrNull(b["bill_discount"]) ?: 0.0
    val rounding = moneyOrNull(b["bill_rounding"]) ?: 0.0
    val due = moneyOrNull(b["bill_due"]) ?: 0.0
    val serviceRate = rateOf(b["bill_service_charge_rate"])
    val discountRate = rateOf(b["bill_discount_rate"])

    val sb = StringBuilder()
    sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=800\">")
    sb.append("<style>").append(PAPER_CSS).append("</style></head><body>")
    sb.append(
        head(
            paper,
            "BILL",
            listOf(
                "Booking no" to b["booking_no"].orEmpty(),
                "Booking date" to hotelDate(b["booking_date"].orEmpty()),
                "Voucher" to b["voucher_no"].orEmpty(),
                "Printed" to hotelDate(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())),
            ),
        )
    )
    sb.append(stayFacts(paper, forReceipt = false))

    sb.append("<table class=\"lines\"><thead><tr>")
    sb.append("<th class=\"c\">#</th><th>Description</th><th class=\"n\">Qty</th><th class=\"n\">Rate</th>")
    sb.append("<th class=\"n\">Base</th><th class=\"n\">Service</th><th class=\"n\">VAT</th><th class=\"n\">Total</th>")
    sb.append("</tr></thead><tbody>")
    if (paper.products.isEmpty()) {
        sb.append("<tr><td colspan=\"8\" class=\"c\">Nothing on the bill.</td></tr>")
    }
    paper.products.forEachIndexed { index, p ->
        val description = p["description_with_type"].orEmpty().ifBlank { p["description"].orEmpty() }
        val from = hotelDate(p["stay_date"].orEmpty())
        val to = hotelDate(p["stay_date_to"].orEmpty())
        val when_ = when {
            from.isBlank() -> ""
            to.isBlank() || to == from -> from
            else -> "$from — $to"
        }
        val room = p["room_with_type"].orEmpty().ifBlank { p["room"].orEmpty() }
        val sitting = p["sitting"].orEmpty()
        val sub = listOf(room, sitting, when_).filter { it.isNotBlank() }.joinToString(" · ")
        val qty = p["quantity"]?.toDoubleOrNull() ?: 0.0
        val qtyText = if (qty == Math.rint(qty)) qty.toLong().toString() else qty.toString()
        val scRate = rateOf(p["service_charge_rate"])
        val vatRate = rateOf(p["vat_rate"])
        sb.append("<tr>")
        sb.append("<td class=\"c\">").append(index + 1).append("</td>")
        sb.append("<td>").append(esc(asRead(description)))
        if (sub.isNotBlank()) sb.append("<div class=\"small\">").append(esc(sub)).append("</div>")
        sb.append("</td>")
        sb.append("<td class=\"n\">").append(esc(qtyText)).append("</td>")
        sb.append("<td class=\"n\">").append(money(p["unit_rate"])).append("</td>")
        sb.append("<td class=\"n\">").append(money(p["base_amount"])).append("</td>")
        sb.append("<td class=\"n\">").append(money(p["service_charge_amount"]))
        if (scRate.isNotBlank() && scRate != "0") sb.append("<div class=\"small\">").append(scRate).append("%</div>")
        sb.append("</td>")
        sb.append("<td class=\"n\">").append(money(p["vat_amount"]))
        if (vatRate.isNotBlank() && vatRate != "0") sb.append("<div class=\"small\">").append(vatRate).append("%</div>")
        sb.append("</td>")
        sb.append("<td class=\"n\">").append(money(p["line_total"])).append("</td>")
        sb.append("</tr>")
    }
    sb.append("</tbody></table>")

    sb.append("<div class=\"totals\"><table>")
    fun row(label: String, value: String, cls: String = "") {
        sb.append("<tr").append(if (cls.isNotBlank()) " class=\"$cls\"" else "").append("><td>")
            .append(esc(label)).append("</td><td class=\"n\">").append(value).append("</td></tr>")
    }
    row("Charges", money(b["bill_base"]))
    row("Service charge" + if (serviceRate.isNotBlank() && serviceRate != "0") " ($serviceRate%)" else "", money(b["bill_service_charge"]))
    row("VAT", money(b["bill_vat"]))
    val vatSummary = b["bill_vat_summary"].orEmpty()
    if (vatSummary.isNotBlank()) {
        sb.append("<tr><td colspan=\"2\" class=\"small\">").append(esc(vatSummary)).append("</td></tr>")
    }
    row("Gross", money(b["bill_gross"]))
    if (discount != 0.0) {
        row("Less discount" + if (discountRate.isNotBlank() && discountRate != "0") " ($discountRate%)" else "", money(b["bill_discount"]))
        row("Net", money(b["bill_net"]))
    }
    if (rounding != 0.0) row("Rounding", money(b["bill_rounding"]))
    row("Amount payable", money(b["bill_rounded"]), "big")
    row("Paid", money(b["bill_paid"]))
    row(if (due < 0) "In hand" else "Due", money(Math.abs(due).toString()), "due")
    sb.append("</table></div>")

    val notes = b["notes"].orEmpty()
    if (notes.isNotBlank()) sb.append("<div class=\"notes\"><b>Notes:</b> ").append(esc(notes)).append("</div>")
    sb.append("<div class=\"sign\"><div>Prepared by</div><div>Guest</div></div>")
    sb.append("</body></html>")
    return sb.toString()
}

/** One receipt. Amount, purpose, method, reference, advance held — and no VAT, by design. */
internal fun hotelReceiptHtml(paper: HotelPaper): String {
    val b = paper.basic
    val kind = b["receipt_kind"].orEmpty().ifBlank { "Received" }
    val isRefund = kind.equals("Refund", ignoreCase = true)
    val title = if (isRefund) "REFUND VOUCHER" else "MONEY RECEIPT"

    val sb = StringBuilder()
    sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=800\">")
    sb.append("<style>").append(PAPER_CSS).append("</style></head><body>")
    sb.append(
        head(
            paper,
            title,
            listOf(
                "Receipt no" to b["payment_no"].orEmpty(),
                "Date" to hotelDate(b["payment_date"].orEmpty()),
                "Booking no" to b["booking_no"].orEmpty(),
                "Voucher" to b["voucher_no"].orEmpty(),
            ),
        )
    )
    sb.append(stayFacts(paper, forReceipt = true))

    sb.append("<div class=\"receipt-box\">")
    sb.append("<div class=\"small\">").append(if (isRefund) "Refunded" else "Received with thanks").append("</div>")
    sb.append("<div class=\"amount\">").append(money(b["receipt_amount"])).append("</div>")
    sb.append("<table>")
    listOf(
        "Purpose" to b["purpose"].orEmpty(),
        "Method" to b["method"].orEmpty(),
        "Reference" to b["reference"].orEmpty(),
        "Notes" to b["payment_notes"].orEmpty(),
        "Against booking" to b["booking_no"].orEmpty(),
    ).filter { it.second.isNotBlank() }.forEach { (k, v) ->
        sb.append("<tr><td class=\"k\">").append(esc(k)).append("</td><td>").append(esc(v)).append("</td></tr>")
    }
    sb.append("</table>")
    val held = moneyOrNull(b["advance_held"])
    if (held != null) {
        sb.append("<div class=\"small\" style=\"margin-top:8px\">Held on this booking after this ")
            .append(if (isRefund) "refund" else "receipt").append(": <b>").append(AmountFormat.format(held)).append("</b></div>")
    }
    sb.append("</div>")
    sb.append("<div class=\"sign\"><div>Received by</div><div>").append(if (isRefund) "Received back by" else "Payer").append("</div></div>")
    sb.append("</body></html>")
    return sb.toString()
}
