package com.example.cashbookbd.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cashbookbd.core.VoucherAttachment
import com.example.cashbookbd.core.VoucherImages

/**
 * The voucher-attachment column's cell — the web ImagePopup's mobile shape.
 * Each image renders its server-side thumbnail as a tappable square; PDFs and
 * spreadsheets render as a small document chip. Tapping hands the attachment
 * to [onOpen] (the screen shows the image viewer, or opens the file
 * externally for non-images).
 */
@Composable
fun VoucherAttachmentsCell(
    attachments: List<VoucherAttachment>,
    isLocalEnv: Boolean,
    onOpen: (VoucherAttachment) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            if (attachment.isImage) {
                // The account-avatar pattern: an icon underneath, the remote
                // thumbnail drawn over it once it loads.
                Box(
                    modifier = Modifier
                        .size(ThumbSize)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onOpen(attachment) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    AsyncImage(
                        model = VoucherImages.thumbnailUrl(attachment, isLocalEnv),
                        contentDescription = "Voucher photo ${attachment.fileName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(ThumbSize),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onOpen(attachment) },
                ) {
                    Text(
                        text = attachment.fileName.substringAfterLast('.', "file").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private val ThumbSize = 34.dp

/**
 * Full-size viewer for a tapped voucher photo — the web's lightbox as a
 * dialog. "Open" hands the full URL to the browser for pinch-zooming or
 * download; non-image files never reach here (they open externally).
 */
@Composable
fun VoucherImageViewerDialog(
    attachment: VoucherAttachment,
    isLocalEnv: Boolean,
    onDismiss: () -> Unit,
) {
    val fullUrl = VoucherImages.fullUrl(attachment, isLocalEnv)
    // No title — the photo itself is the content; the file name is noise.
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The minimum height keeps the dialog body visible while the
                // photo loads; the icon underneath stays as the failure state
                // (same fallback pattern as the thumbnail cell).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    AsyncImage(
                        model = fullUrl,
                        contentDescription = "Voucher photo ${attachment.fileName}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            val context = androidx.compose.ui.platform.LocalContext.current
            LinkButton(
                text = "Open",
                onClick = { openVoucherAttachment(context, attachment, isLocalEnv) },
            )
        },
        dismissButton = { LinkButton(text = "Close", onClick = onDismiss) },
    )
}

/**
 * Opens an attachment outside the app — the browser fetches the full file
 * (images zoomable, PDFs/sheets via their handler apps).
 */
fun openVoucherAttachment(
    context: Context,
    attachment: VoucherAttachment,
    isLocalEnv: Boolean,
) {
    val url = VoucherImages.fullUrl(attachment, isLocalEnv)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser — nothing sensible to do beyond ignoring the tap.
    }
}
