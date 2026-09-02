package com.example.cashbookbd.ui.asset

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.asset.AssetMenu
import com.example.cashbookbd.navigation.AuthenticatedShell
import com.example.cashbookbd.ui.components.PrimaryButton
import com.example.cashbookbd.ui.components.SecondaryButton

/** One sticker's worth of an asset — everything the label prints, and no more. */
data class AssetLabelRow(
    val code: String,
    val name: String,
    val categoryName: String = "",
    val location: String = "",
    val serialNo: String = "",
)

/**
 * What the register handed over to be printed.
 *
 * ⚠️ A HOLDER RATHER THAN A ROUTE ARGUMENT, deliberately. The sheet prints the
 * rows that are ON SCREEN — a page of the register, filtered and searched — and
 * a JSON blob of them in a route string would be a URL thousands of characters
 * long, re-encoded on every rotation and truncated by the back stack. The rows
 * are read once, on the way in, by the one screen that opens next.
 */
object AssetLabelsHolder {
    var rows: List<AssetLabelRow> = emptyList()

    /** What the labels are for, printed small at the top of the sheet. */
    var caption: String = ""

    fun hold(rows: List<AssetLabelRow>, caption: String) {
        this.rows = rows
        this.caption = caption
    }
}

/**
 * The stickers that go on the things themselves.
 *
 * ⚠️ THE LABEL IS WHAT MAKES THE REGISTER TRUE. A register nobody can match to
 * the objects in the room is a list of sentences: two identical chairs, one sold
 * and one kept, cannot be told apart a year later, and the count that follows is
 * guesswork. The code on the sticker is the only thing that ties a row to a
 * thing.
 *
 * ⚠️ THE QR HOLDS A LINK, NOT THE CODE. A phone's own camera opens a link with
 * no app to install and no scanner to buy — which is the whole difference
 * between a system people use in the store room and one they use at a desk
 * afterwards from memory. It opens the register searched for that asset.
 *
 * ⚠️ AND THE CODE IS PRINTED IN PLAIN TEXT UNDER IT. Stickers get scuffed, and a
 * QR with a scratch through it reads as nothing at all; the printed code still
 * reads. It is also what somebody types into the search box when the sticker has
 * gone entirely.
 *
 * ⚠️ THE QR IS DRAWN OFFLINE, by an encoder inlined into the page. A store room
 * is the one place in the building with no signal, and a label sheet that needs
 * the internet to draw its own codes is a sheet that comes out blank exactly
 * where it is needed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AssetLabelsPrintScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Read once: the sheet is what the register handed over when it opened this
    // screen, and it must not change under the person looking at it.
    val rows = remember { AssetLabelsHolder.rows }
    val caption = remember { AssetLabelsHolder.caption }
    val html = remember(rows, caption) { assetLabelsHtml(rows, caption, webOrigin()) }

    AuthenticatedShell(
        title = "Asset labels",
        currentRoute = AssetMenu.ROUTE_HOME,
        navController = navController,
        onLogout = onLogout,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // The encoder that draws the codes lives in the page.
                            settings.javaScriptEnabled = true
                            // A sheet of 62mm labels is wider than a phone; start
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
                        // The host root, so the sheet resolves relative links the
                        // way the web app does. Nothing is fetched from it: every
                        // byte the page needs is already in the page.
                        view.loadDataWithBaseURL(webOrigin(), html, "text/html", "utf-8", null)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                        val printManager =
                            context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        printManager.print(
                            "Asset labels",
                            view.createPrintDocumentAdapter("Asset labels"),
                            PrintAttributes.Builder().build(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Where scanning a sticker lands: this deployment's own host, never a hard-coded
 * one. The stickers outlive the deployment, and one printed against a
 * developer's machine leads nowhere for the next five years.
 */
private fun webOrigin(): String = BuildConfig.BASE_URL.trimEnd('/').removeSuffix("/api")

private fun scanLink(origin: String, code: String): String =
    "$origin/asset/setup?tab=register&q=" + urlPart(code)

/** encodeURIComponent, as the web builds the same link. */
private fun urlPart(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
        .replace("+", "%20")
        .replace("%7E", "~")

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

/**
 * The sheet itself.
 *
 * ⚠️ SIZED IN MILLIMETRES, not in pixels. These are cut out with scissors and
 * stuck on a chair: a label that comes out a different size on a different
 * printer is one that does not fit the thing it is for. Same 62 × 30 mm, same
 * dashed edge and same 8mm page margin as the web sheet, so the two printers in
 * an office produce the same stickers.
 *
 * ⚠️ BLACK ON WHITE, in the page's own literal colours rather than the app's
 * palette — paper has no dark mode, and a QR drawn in a theme colour is a QR
 * that does not scan.
 */
private fun assetLabelsHtml(
    rows: List<AssetLabelRow>,
    caption: String,
    origin: String,
): String {
    val head = buildString {
        append("Asset labels")
        if (caption.isNotBlank()) append(" — ").append(caption)
        append(" · ").append(rows.size).append(" label(s)")
    }

    val labels = rows.joinToString("") { row ->
        val foot = listOfNotNull(
            row.categoryName.takeIf { it.isNotBlank() },
            row.location.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        buildString {
            append("<div class=\"asset-label\">")
            append("<div class=\"asset-label-qr\" data-link=\"")
            append(escapeHtml(scanLink(origin, row.code)))
            append("\"></div>")
            append("<div class=\"asset-label-body\">")
            append("<div class=\"asset-label-name\">").append(escapeHtml(row.name)).append("</div>")
            append("<div class=\"asset-label-code\">").append(escapeHtml(row.code)).append("</div>")
            if (foot.isNotBlank()) {
                append("<div class=\"asset-label-foot\">").append(escapeHtml(foot)).append("</div>")
            }
            if (row.serialNo.isNotBlank()) {
                append("<div class=\"asset-label-foot\">Sl. ")
                append(escapeHtml(row.serialNo)).append("</div>")
            }
            append("</div></div>")
        }
    }

    val empty = if (rows.isEmpty()) {
        "<p class=\"asset-label-empty\">Nothing on screen to print. Go back, find the assets " +
            "you want stickers for, then print the labels.</p>"
    } else {
        ""
    }

    return """<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Asset labels</title>
<style>
  @page { margin: 8mm; }
  html, body { margin: 0; padding: 0; background: #fff; color: #000;
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  body { padding: 4mm; }
  .asset-label-head { font-size: 7pt; margin-bottom: 2mm; }
  .asset-label-empty { font-size: 9pt; }
  .asset-label-sheet { display: flex; flex-wrap: wrap; gap: 3mm; }
  .asset-label {
    width: 62mm; height: 30mm; box-sizing: border-box;
    border: 1px dashed #999; padding: 2mm;
    display: flex; align-items: center; gap: 2mm;
    /* Never split one label across two sheets. */
    break-inside: avoid; page-break-inside: avoid; overflow: hidden;
  }
  .asset-label-qr { width: 25mm; height: 25mm; flex: 0 0 auto; }
  .asset-label-qr svg { display: block; width: 100%; height: 100%; }
  .asset-label-body { min-width: 0; }
  .asset-label-name {
    font-size: 8pt; font-weight: 600; line-height: 1.15;
    /* Two lines and no more: the sticker cannot grow. */
    display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .asset-label-code {
    font-family: ui-monospace, monospace; font-size: 9pt; font-weight: 700;
    letter-spacing: 0.02em;
  }
  .asset-label-foot {
    font-size: 6pt; color: #444; line-height: 1.2;
    overflow: hidden; white-space: nowrap; text-overflow: ellipsis;
  }
</style></head>
<body>
<div class="asset-label-head">${escapeHtml(head)}</div>
$empty
<div class="asset-label-sheet">$labels</div>
<script>
$QR_ENCODER_JS
</script>
</body></html>"""
}

/**
 * A QR encoder, inlined.
 *
 * ⚠️ NOTHING IS FETCHED. Byte mode, error correction level M, versions 1 to 10
 * chosen by length — enough for a link several times longer than the ones these
 * labels carry. Level M is the same middle ground the web's component uses: it
 * survives a scuffed sticker without making the modules so small that a phone
 * camera cannot resolve them at 25mm.
 *
 * ⚠️ SVG OF RECTANGLES, NOT A CANVAS. A canvas is a screen-resolution picture,
 * and a QR printed from one is a QR that scans badly; the path is drawn at
 * whatever resolution the printer has.
 */
private const val QR_ENCODER_JS = """
(function () {
  // GF(256) with the QR primitive polynomial, x^8+x^4+x^3+x^2+1 = 285.
  var EXP = [], LOG = [];
  (function () {
    var x = 1;
    for (var i = 0; i < 255; i++) { EXP[i] = x; LOG[x] = i; x <<= 1; if (x & 256) x ^= 285; }
    for (var i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
  })();

  function mul(a, b) { return (a === 0 || b === 0) ? 0 : EXP[LOG[a] + LOG[b]]; }

  // The generator polynomial of degree n, highest coefficient first.
  function genPoly(n) {
    var g = [1];
    for (var i = 0; i < n; i++) {
      var ng = [];
      for (var k = 0; k <= g.length; k++) ng[k] = 0;
      for (var j = 0; j < g.length; j++) { ng[j] ^= g[j]; ng[j + 1] ^= mul(g[j], EXP[i]); }
      g = ng;
    }
    return g;
  }

  function ecBytes(data, n) {
    var g = genPoly(n), r = [];
    for (var i = 0; i < n; i++) r[i] = 0;
    for (var i = 0; i < data.length; i++) {
      var f = data[i] ^ r[0];
      r.shift(); r.push(0);
      if (f !== 0) for (var j = 0; j < n; j++) r[j] ^= mul(g[j + 1], f);
    }
    return r;
  }

  // Alignment-pattern centres, versions 1..10.
  var ALIGN = [[], [6,18], [6,22], [6,26], [6,30], [6,34], [6,22,38], [6,24,42], [6,26,46], [6,28,50]];

  // Level M blocks per version: [how many, codewords in the block, data of them].
  var BLOCKS = [
    [[1,26,16]], [[1,44,28]], [[1,70,44]], [[2,50,32]], [[2,67,43]],
    [[4,43,27]], [[4,49,31]], [[2,60,38],[2,61,39]], [[3,58,36],[2,59,37]],
    [[4,69,43],[1,70,44]]
  ];

  function dataCount(v) {
    var b = BLOCKS[v - 1], t = 0;
    for (var i = 0; i < b.length; i++) t += b[i][0] * b[i][2];
    return t;
  }

  function bitLen(x) { var n = 0; while (x) { n++; x >>>= 1; } return n; }

  function bchRem(v, poly) {
    var g = bitLen(poly) - 1, r = v;
    while (bitLen(r) > g) r ^= poly << (bitLen(r) - g - 1);
    return r;
  }

  // Level M is 00, so the five bits are the mask alone. 1335 = 0x537, 21522 = 0x5412.
  function formatBits(mask) { var d = mask; return (((d << 10) | bchRem(d << 10, 1335)) ^ 21522); }

  // 7973 = 0x1F25, the version-information generator.
  function versionBits(v) { return (v << 12) | bchRem(v << 12, 7973); }

  function maskAt(m, i, j) {
    switch (m) {
      case 0: return (i + j) % 2 === 0;
      case 1: return i % 2 === 0;
      case 2: return j % 3 === 0;
      case 3: return (i + j) % 3 === 0;
      case 4: return (Math.floor(i / 2) + Math.floor(j / 3)) % 2 === 0;
      case 5: return ((i * j) % 2) + ((i * j) % 3) === 0;
      case 6: return (((i * j) % 2) + ((i * j) % 3)) % 2 === 0;
      default: return (((i * j) % 3) + ((i + j) % 2)) % 2 === 0;
    }
  }

  function utf8(s) {
    var out = [];
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (c < 128) out.push(c);
      else if (c < 2048) { out.push(192 | (c >> 6), 128 | (c & 63)); }
      else if (c < 55296 || c > 57343) {
        out.push(224 | (c >> 12), 128 | ((c >> 6) & 63), 128 | (c & 63));
      } else {
        var c2 = s.charCodeAt(++i);
        var cp = 65536 + ((c & 1023) << 10) + (c2 & 1023);
        out.push(240 | (cp >> 18), 128 | ((cp >> 12) & 63), 128 | ((cp >> 6) & 63), 128 | (cp & 63));
      }
    }
    return out;
  }

  // Mode indicator, length, the bytes, the terminator, then the pad pair.
  function makeData(v, bytes) {
    var dc = dataCount(v), bits = [];
    function put(val, len) { for (var i = len - 1; i >= 0; i--) bits.push((val >>> i) & 1); }
    put(4, 4);
    put(bytes.length, v < 10 ? 8 : 16);
    for (var i = 0; i < bytes.length; i++) put(bytes[i], 8);
    var cap = dc * 8;
    for (var i = 0; i < 4 && bits.length < cap; i++) bits.push(0);
    while (bits.length % 8 !== 0) bits.push(0);
    var words = [];
    for (var i = 0; i < bits.length; i += 8) {
      var b = 0;
      for (var j = 0; j < 8; j++) b = (b << 1) | bits[i + j];
      words.push(b);
    }
    var pad = [236, 17], k = 0;
    while (words.length < dc) words.push(pad[(k++) % 2]);
    return words;
  }

  // Data blocks first, column by column, then the error-correction blocks.
  function interleave(v, words) {
    var spec = BLOCKS[v - 1], dcd = [], ecd = [], off = 0, maxD = 0, maxE = 0;
    for (var g = 0; g < spec.length; g++) {
      for (var b = 0; b < spec[g][0]; b++) {
        var dat = spec[g][2], ecn = spec[g][1] - dat;
        var d = words.slice(off, off + dat);
        off += dat;
        dcd.push(d); ecd.push(ecBytes(d, ecn));
        if (dat > maxD) maxD = dat;
        if (ecn > maxE) maxE = ecn;
      }
    }
    var out = [];
    for (var i = 0; i < maxD; i++) for (var b = 0; b < dcd.length; b++) if (i < dcd[b].length) out.push(dcd[b][i]);
    for (var i = 0; i < maxE; i++) for (var b = 0; b < ecd.length; b++) if (i < ecd[b].length) out.push(ecd[b][i]);
    return out;
  }

  function build(v, words, mask) {
    var n = 17 + 4 * v, m = [];
    for (var r = 0; r < n; r++) { m[r] = []; for (var c = 0; c < n; c++) m[r][c] = null; }

    function probe(row, col) {
      for (var r = -1; r <= 7; r++) {
        if (row + r < 0 || row + r >= n) continue;
        for (var c = -1; c <= 7; c++) {
          if (col + c < 0 || col + c >= n) continue;
          m[row + r][col + c] = ((r >= 0 && r <= 6 && (c === 0 || c === 6))
            || (c >= 0 && c <= 6 && (r === 0 || r === 6))
            || (r >= 2 && r <= 4 && c >= 2 && c <= 4));
        }
      }
    }
    probe(0, 0); probe(n - 7, 0); probe(0, n - 7);

    var pos = ALIGN[v - 1];
    for (var i = 0; i < pos.length; i++) {
      for (var j = 0; j < pos.length; j++) {
        var row = pos[i], col = pos[j];
        if (m[row][col] !== null) continue;
        for (var r = -2; r <= 2; r++) {
          for (var c = -2; c <= 2; c++) {
            m[row + r][col + c] = (r === -2 || r === 2 || c === -2 || c === 2 || (r === 0 && c === 0));
          }
        }
      }
    }

    for (var r = 8; r < n - 8; r++) if (m[r][6] === null) m[r][6] = (r % 2 === 0);
    for (var c = 8; c < n - 8; c++) if (m[6][c] === null) m[6][c] = (c % 2 === 0);

    var fb = formatBits(mask);
    for (var i = 0; i < 15; i++) {
      var bit = ((fb >> i) & 1) === 1;
      if (i < 6) m[i][8] = bit; else if (i < 8) m[i + 1][8] = bit; else m[n - 15 + i][8] = bit;
      if (i < 8) m[8][n - i - 1] = bit; else if (i < 9) m[8][15 - i] = bit; else m[8][14 - i] = bit;
    }
    m[n - 8][8] = true;

    if (v >= 7) {
      var vb = versionBits(v);
      for (var i = 0; i < 18; i++) {
        var vbit = ((vb >> i) & 1) === 1;
        m[Math.floor(i / 3)][(i % 3) + n - 11] = vbit;
        m[(i % 3) + n - 11][Math.floor(i / 3)] = vbit;
      }
    }

    // The zigzag, two columns at a time from the right, skipping the timing column.
    var inc = -1, row2 = n - 1, bitIndex = 7, byteIndex = 0;
    for (var col = n - 1; col > 0; col -= 2) {
      if (col === 6) col--;
      for (;;) {
        for (var cc = 0; cc < 2; cc++) {
          if (m[row2][col - cc] === null) {
            var dark = false;
            if (byteIndex < words.length) dark = (((words[byteIndex] >>> bitIndex) & 1) === 1);
            if (maskAt(mask, row2, col - cc)) dark = !dark;
            m[row2][col - cc] = dark;
            bitIndex--;
            if (bitIndex === -1) { byteIndex++; bitIndex = 7; }
          }
        }
        row2 += inc;
        if (row2 < 0 || row2 >= n) { row2 -= inc; inc = -inc; break; }
      }
    }
    return m;
  }

  // The standard four penalties: runs, blocks, the finder-lookalike, and the
  // dark-to-light balance. The lowest-scoring mask is the one printed.
  function penalty(m) {
    var n = m.length, p = 0;
    function d(r, c) { return m[r][c] === true; }
    for (var r = 0; r < n; r++) {
      for (var c = 0; c < n; c++) {
        var same = 0, dark = d(r, c);
        for (var dr = -1; dr <= 1; dr++) {
          if (r + dr < 0 || r + dr >= n) continue;
          for (var dc = -1; dc <= 1; dc++) {
            if (c + dc < 0 || c + dc >= n) continue;
            if (dr === 0 && dc === 0) continue;
            if (d(r + dr, c + dc) === dark) same++;
          }
        }
        if (same > 5) p += (3 + same - 5);
      }
    }
    for (var r = 0; r < n - 1; r++) {
      for (var c = 0; c < n - 1; c++) {
        var cnt = 0;
        if (d(r, c)) cnt++;
        if (d(r + 1, c)) cnt++;
        if (d(r, c + 1)) cnt++;
        if (d(r + 1, c + 1)) cnt++;
        if (cnt === 0 || cnt === 4) p += 3;
      }
    }
    for (var r = 0; r < n; r++) {
      for (var c = 0; c < n - 6; c++) {
        if (d(r,c) && !d(r,c+1) && d(r,c+2) && d(r,c+3) && d(r,c+4) && !d(r,c+5) && d(r,c+6)) p += 40;
      }
    }
    for (var c = 0; c < n; c++) {
      for (var r = 0; r < n - 6; r++) {
        if (d(r,c) && !d(r+1,c) && d(r+2,c) && d(r+3,c) && d(r+4,c) && !d(r+5,c) && d(r+6,c)) p += 40;
      }
    }
    var dk = 0;
    for (var r = 0; r < n; r++) for (var c = 0; c < n; c++) if (d(r, c)) dk++;
    p += Math.floor(Math.abs((100 * dk) / (n * n) - 50) / 5) * 10;
    return p;
  }

  function toSvg(text) {
    var bytes = utf8(text), v = 0;
    for (var t = 1; t <= 10; t++) {
      if (bytes.length * 8 <= dataCount(t) * 8 - 4 - (t < 10 ? 8 : 16)) { v = t; break; }
    }
    // Longer than version 10 holds. The printed code underneath still reads,
    // which is exactly why it is printed.
    if (v === 0) return '';

    var words = interleave(v, makeData(v, bytes));
    var best = null, bestP = 0;
    for (var mk = 0; mk < 8; mk++) {
      var m = build(v, words, mk), p = penalty(m);
      if (best === null || p < bestP) { best = m; bestP = p; }
    }

    // Four modules of quiet zone on every side, as the standard requires: a QR
    // butted against a dashed border is one a camera cannot find the edge of.
    var q = 4, n = best.length, size = n + q * 2, path = '';
    for (var r = 0; r < n; r++) {
      for (var c = 0; c < n; c++) {
        if (best[r][c] === true) path += 'M' + (c + q) + ' ' + (r + q) + 'h1v1h-1z';
      }
    }
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + size + ' ' + size
      + '" shape-rendering="crispEdges"><rect width="' + size + '" height="' + size
      + '" fill="#ffffff"/><path d="' + path + '" fill="#000000"/></svg>';
  }

  var boxes = document.querySelectorAll('.asset-label-qr');
  for (var i = 0; i < boxes.length; i++) {
    boxes[i].innerHTML = toSvg(boxes[i].getAttribute('data-link') || '');
  }
})();
"""
