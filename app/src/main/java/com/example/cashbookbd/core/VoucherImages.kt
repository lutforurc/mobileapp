package com.example.cashbookbd.core

import com.example.cashbookbd.BuildConfig

/**
 * One attachment on a voucher: the stored file name plus the branch context the
 * URL needs. `main_trx_master.voucher_image` holds several files pipe-separated;
 * the ledger/cashbook reports return it verbatim.
 */
data class VoucherAttachment(
    val fileName: String,
    val branchPad: String,
) {
    /** Images render as tappable thumbnails; other types open externally. */
    val isImage: Boolean
        get() = fileName.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    companion object {
        // Every type the upload rule accepts and thumbnails (incl. bmp); the
        // web treats anything that isn't a pdf/spreadsheet as an image.
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    }
}

/**
 * Builds the voucher-attachment URLs exactly like the web's ImagePopup:
 *
 *   base  = {host}/public/project_voucher/{branchPad}     (no /public when the
 *                                                          server runs local)
 *   full  = {base}/voucher/{file}
 *   thumb = {base}/thumbnail/{file}
 *
 * The host is [BuildConfig.BASE_URL] with its trailing `api/` removed. The
 * files are public static assets — the web drops them straight into `<img>`
 * tags with no auth header, so they load the same way here.
 */
object VoucherImages {

    /** Splits the raw pipe-separated `voucher_image` value into attachments. */
    fun attachments(raw: String?, branchPad: String?): List<VoucherAttachment> {
        val pad = branchPad?.trim().orEmpty()
        if (raw.isNullOrBlank() || pad.isBlank()) return emptyList()
        return raw.split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { VoucherAttachment(fileName = it, branchPad = pad) }
    }

    /** A raw branch id ("7") as the 4-wide pad the file paths use ("0007"). */
    fun branchPad(branchId: String?): String? =
        branchId?.trim()?.takeIf { it.isNotEmpty() }?.padStart(4, '0')

    fun fullUrl(attachment: VoucherAttachment, isLocalEnv: Boolean): String =
        "${base(attachment.branchPad, isLocalEnv)}/voucher/${attachment.fileName}"

    fun thumbnailUrl(attachment: VoucherAttachment, isLocalEnv: Boolean): String =
        "${base(attachment.branchPad, isLocalEnv)}/thumbnail/${attachment.fileName}"

    private fun base(branchPad: String, isLocalEnv: Boolean): String {
        val host = BuildConfig.BASE_URL.trimEnd('/').removeSuffix("/api")
        val publicSegment = if (isLocalEnv) "" else "/public"
        return "$host$publicSegment/project_voucher/$branchPad"
    }
}
