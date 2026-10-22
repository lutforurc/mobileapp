package com.example.cashbookbd.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Picked files into multipart parts — the voucher photos, the opening-balance
 * sheets. Read on the caller's dispatcher (call from IO).
 */
object UriParts {

    /** The file's own name as the picker reports it, or [fallback]. */
    fun displayName(context: Context, uri: Uri, fallback: String = "file"): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback

    /** One part under [field], or null when the file cannot be read. */
    fun part(context: Context, uri: Uri, field: String): MultipartBody.Part? {
        val resolver = context.contentResolver
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = displayName(context, uri, "upload." + mime.substringAfter('/', "bin"))
        return MultipartBody.Part.createFormData(field, name, bytes.toRequestBody(mime.toMediaType()))
    }

    /** A plain text form field. */
    fun text(value: String): RequestBody = value.toRequestBody("text/plain".toMediaType())
}
