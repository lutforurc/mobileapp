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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.cashbookbd.ui.theme.accents

/**
 * The web's tutorial-video link: a play button beside a list's title that
 * opens the screen's YouTube walkthrough. Callers gate it on the branch's
 * `need_demo_tutorial` setting, exactly as the web gates its icon; the one
 * shared component keeps every screen's link the same colour and behaviour.
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

/** The playlist links the web pages point at (react f99f8ee / 74a65dc). */
object TutorialVideos {
    const val COMPANY_AND_USER_LIST = "https://www.youtube.com/watch?v=ZAlHW1F-9vw&list=PLZcNDKJT-3gc"
    const val BRANCH_LIST = "https://www.youtube.com/watch?v=WMebDncBOrY&list=PLZcNDKJT-3gc"
    const val REGISTRATION = "https://www.youtube.com/watch?v=aedE-I79XHM&list=PLZcNDKJT-3gc&index=2&t=17s"
}
