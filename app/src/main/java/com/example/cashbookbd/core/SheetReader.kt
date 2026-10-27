package com.example.cashbookbd.core

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads the first sheet of a spreadsheet into rows of text — the opening
 * imports' "Upload" (the web reads it with SheetJS). Handles `.xlsx` (zipped
 * XML: shared strings + the first worksheet), `.csv`, and tab-separated text
 * pasted from Excel. The old binary `.xls` is not readable here and is
 * refused by name, so the user can re-save it as `.xlsx`.
 */
object SheetReader {

    class UnsupportedSheet(message: String) : Exception(message)

    /** Call off the main thread. */
    fun read(context: Context, uri: Uri): List<List<String>> {
        val name = UriParts.displayName(context, uri, "").lowercase()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw UnsupportedSheet("This file could not be read.")
        return when {
            name.endsWith(".xls") -> throw UnsupportedSheet("Old .xls files cannot be read on the phone — save it as .xlsx and upload again.")
            name.endsWith(".csv") -> parseDelimited(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"), ',')
            // An .xlsx is a zip: its first two bytes are "PK".
            bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> readXlsx(bytes)
            else -> parseDelimited(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"), ',')
        }
    }

    /** Rows pasted from Excel: tabs between cells, a line per row. */
    fun fromPaste(text: String): List<List<String>> = parseDelimited(text, '\t')

    // ---- xlsx --------------------------------------------------------------------

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        var shared: List<String> = emptyList()
        var sheetXml: ByteArray? = null
        var firstSheetName: String? = null
        val sheets = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name
                when {
                    entryName == "xl/sharedStrings.xml" -> shared = readSharedStrings(zip.readBytes())
                    entryName.startsWith("xl/worksheets/sheet") && entryName.endsWith(".xml") -> sheets[entryName] = zip.readBytes()
                }
            }
        }
        // sheet1.xml is the first sheet in every workbook Excel writes.
        firstSheetName = sheets.keys.minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
        sheetXml = firstSheetName?.let { sheets[it] } ?: throw UnsupportedSheet("This file has no sheet.")
        return readSheet(ByteArrayInputStream(sheetXml), shared)
    }

    private fun readSharedStrings(xml: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(xml), "UTF-8")
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> inT = inSi
                    // Phonetic runs are readings, not the text.
                    "rPh" -> inT = false
                }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inT = false
                    "si" -> { out += sb.toString(); inSi = false }
                }
            }
        }
        return out
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (!c.isLetter()) break
            n = n * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    private fun readSheet(input: InputStream, shared: List<String>): List<List<String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        val p = Xml.newPullParser()
        p.setInput(input, "UTF-8")
        var rowIndex = -1
        var col = -1
        var type = ""
        var value = StringBuilder()
        var inValue = false
        var nextCol = 0
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> {
                        rowIndex = (p.getAttributeValue(null, "r")?.toIntOrNull()?.minus(1)) ?: (rowIndex + 1)
                        nextCol = 0
                    }
                    "c" -> {
                        col = p.getAttributeValue(null, "r")?.let(::columnIndex)?.takeIf { it >= 0 } ?: nextCol
                        type = p.getAttributeValue(null, "t").orEmpty()
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> cleanNumber(raw)
                        }
                        if (rowIndex >= 0) rows.getOrPut(rowIndex) { mutableMapOf() }[col] = text
                        nextCol = col + 1
                    }
                }
            }
        }
        return rows.values.map { cells ->
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            List(width) { i -> cells[i].orEmpty() }
        }
    }

    /** Excel keeps 12.5 as "12.5" but 3 as "3" — and sometimes "2.9999999999999996". */
    private fun cleanNumber(raw: String): String {
        val d = raw.toDoubleOrNull() ?: return raw
        if (!raw.contains('E') && !raw.contains('e') && raw.length <= 15) return raw
        return java.math.BigDecimal(d).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    // ---- csv / tsv ---------------------------------------------------------------

    private fun parseDelimited(text: String, sep: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                !quoted && c == sep -> { row += cell.toString(); cell.setLength(0) }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    rows += row; row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }
}
