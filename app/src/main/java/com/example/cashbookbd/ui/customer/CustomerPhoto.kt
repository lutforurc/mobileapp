package com.example.cashbookbd.ui.customer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.ui.components.SecondaryButton
import java.io.ByteArrayOutputStream

/** The server's hard cap (`PHOTO_MAX_BYTES = 153600`). */
private const val PHOTO_MAX_BYTES = 150 * 1024

/**
 * Encodes a picked image the way the web's PhotoInput sends one: a
 * `data:image/jpeg;base64,…` URI within the server's 150 KB cap. The long
 * edge is capped at 800px, then JPEG quality steps down until it fits.
 * Null when the image can't be read or won't fit even at the floor.
 */
fun encodeCustomerPhoto(context: Context, uri: Uri): String? {
    val source = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull() ?: return null

    val scale = 800f / maxOf(source.width, source.height)
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        source
    }

    var quality = 90
    var bytes: ByteArray
    do {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        quality -= 10
    } while (bytes.size > PHOTO_MAX_BYTES && quality >= 20)

    if (bytes.size > PHOTO_MAX_BYTES) return null
    return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/**
 * Where a stored photo path serves from — the web PhotoInput's rule: full
 * URLs/data URIs pass through; a relative path rides the API host with
 * `/public` unless the server runs locally.
 */
fun customerPhotoUrl(path: String, isLocalEnv: Boolean): String? {
    if (path.isBlank()) return null
    if (path.startsWith("http") || path.startsWith("data:") || path.startsWith("blob:")) return path
    val base = BuildConfig.BASE_URL.removeSuffix("/").removeSuffix("/api")
    val publicSegment = if (isLocalEnv) "" else "/public"
    return "$base$publicSegment/${path.trimStart('/')}"
}

/**
 * The customer forms' photo field: a preview of the newly picked photo (or
 * the stored one on Edit), with Choose and Remove — one shared component so
 * Add and Edit stay identical.
 */
@Composable
fun CustomerPhotoField(
    /** The freshly picked `data:` URI, or blank when none picked. */
    photo: String,
    /** The stored photo's URL (Edit), or null when there is none. */
    existingUrl: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Text(
            text = "Photo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        val preview = remember(photo) { decodeDataUri(photo) }
        when {
            preview != null -> Image(
                bitmap = preview,
                contentDescription = "Selected photo",
                modifier = Modifier.size(96.dp),
            )
            existingUrl != null -> AsyncImage(
                model = existingUrl,
                contentDescription = "Stored photo",
                modifier = Modifier.size(96.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row {
            SecondaryButton(text = "Choose Photo", onClick = onPick, compact = true)
            if (photo.isNotBlank() || existingUrl != null) {
                Spacer(Modifier.width(8.dp))
                SecondaryButton(text = "Remove", onClick = onClear, compact = true)
            }
        }
    }
}

/** Decodes a `data:image/…;base64,` URI into a previewable bitmap. */
private fun decodeDataUri(uri: String): ImageBitmap? {
    if (!uri.startsWith("data:")) return null
    val comma = uri.indexOf(',')
    if (comma < 0) return null
    return runCatching {
        val bytes = Base64.decode(uri.substring(comma + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
