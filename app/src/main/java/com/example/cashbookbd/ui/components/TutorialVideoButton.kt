package com.example.cashbookbd.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.ui.theme.accents

/**
 * The web's tutorial-video link: a play button beside a list's title that
 * opens the screen's walkthrough. The one shared component keeps every
 * screen's link the same colour and behaviour.
 *
 * Prefer [TutorialVideoLink], which looks the URL up by screen key. This one
 * takes a URL already decided on, and does not gate on anything.
 */
@Composable
fun TutorialVideoButton(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    IconButton(onClick = { openTutorialVideo(context, url) }, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Watch tutorial video",
            tint = MaterialTheme.accents.red,
        )
    }
}

/**
 * The walkthrough link for one screen, by its key in the tutorial_videos table.
 *
 * Three things decide whether it appears, exactly as on the web: the branch
 * asked for walkthroughs (`need_demo_tutorial`), a video has been recorded for
 * this screen **in this app**, and the settings payload has landed. The URL is
 * the operator's to change from the admin screen — nothing here is compiled in.
 *
 * The keys are the web's route paths ("/reports/ledger") and its pinned names
 * for the screens a route cannot tell apart ("cash-payment.trading"), because
 * both apps read one table. What differs is the column: the row carries a web
 * URL and a mobile URL, and a screen recorded only for the browser shows no
 * button here rather than playing a walkthrough of a page this app does not
 * have. See [TutorialScreens].
 */
@Composable
fun TutorialVideoLink(screenKey: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sessionManager = remember(context) { ServiceLocator.provideSessionManager(context.applicationContext) }
    val session by sessionManager.state.collectAsStateWithLifecycle()
    val settings = session.settings ?: return
    if (!settings.needDemoTutorial) return
    val url = settings.tutorialVideos[screenKey]?.takeIf { it.isNotBlank() } ?: return

    TutorialVideoButton(url = url, modifier = modifier)
}

/** Opens the video in YouTube/browser; a device with neither just ignores it. */
fun openTutorialVideo(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

/**
 * The screen keys this app's screens answer to.
 *
 * They are the web's, not invented here: one `tutorial_videos` table serves
 * both apps, so a video recorded once shows up in both. A key with no row
 * simply finds nothing, which is what a screen whose walkthrough has not been
 * recorded should do.
 */
object TutorialScreens {
    /**
     * Registration is the one link still compiled in, and the server agrees:
     * its seed drops the login and registration rows on purpose. Both screens
     * are shown before anyone has signed in, so the settings payload that
     * carries the URLs does not exist yet.
     */
    const val REGISTRATION_URL =
        "https://www.youtube.com/watch?v=aedE-I79XHM&list=PLZcNDKJT-3gc&index=2&t=17s"

    const val COMPANY_LIST = "/company/company-list"
    const val BRANCH_LIST = "/branch/branch-list"
    const val USER_LIST = "/user/user-list"
    const val CUSTOMER_SUPPLIER = "/customer-supplier/customer-supplier"
    const val REGISTER = "/register"

    // Screens a route cannot tell apart carry the web's pinned keys: one cash
    // payment route serves a trading, general, head-office or project-expense
    // form depending on the branch, and each has its own walkthrough.
    const val CASH_PAYMENT_TRADING = "cash-payment.trading"
    const val CASH_PAYMENT_GENERAL = "cash-payment.general"
    const val CASH_PAYMENT_HEAD_OFFICE = "cash-payment.head-office"
    const val CASH_PAYMENT_PROJECT_EXPENSE = "cash-payment.project-expense"
    const val CASH_RECEIVED_TRADING = "cash-received.trading"
    const val CASH_RECEIVED_GENERAL = "cash-received.general"
    const val CASH_RECEIVED_HEAD_OFFICE = "cash-received.head-office"
    const val BANK_PAYMENT = "bank-payment"
    const val COMBINED_ENTRY_TRADING = "combined-entry.trading"

    const val PROJECT_PURCHASE = "/real-estate/project-purchase"
    const val PROJECT_LABOUR = "/real-estate/project-labour"
    const val PROJECT_INCOME = "/real-estate/project-income"
    const val PROJECT_COST_REPORT = "/real-estate/project-cost-report"
    const val PROJECT_INCOME_REPORT = "/real-estate/project-income-report"
    const val INSTALLMENT_CREATE = "/real-estate/installment-create"
    const val UNIT_SALES = "/real-estate/unit-sales"
    const val SOLD_UNITS = "/real-estate/sold-units"
    const val FLAT_LAYOUT = "/real-estate/flat-layout"
}
