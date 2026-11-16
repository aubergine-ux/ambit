package io.aubergine.ambit.evidence

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import io.aubergine.ambit.R
import io.aubergine.ambit.map.MapPoint
import io.aubergine.ambit.map.MapRenderer
import io.aubergine.ambit.map.MapStyle
import io.aubergine.ambit.ui.alerts.reasonText
import io.aubergine.ambit.ui.guide.labelRes
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Lays an [EvidenceReport] out as an A4 PDF using the platform's PdfDocument.
 *
 * Written for a reader who has never heard of the app: a police officer or an advocate.
 * Plain language, local and UTC times side by side, the method and its limits spelled
 * out, and the map drawn in print colours so it survives a black-and-white copier.
 */
class EvidencePdfWriter(
    private val context: Context,
    private val renderer: MapRenderer,
    private val attribution: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    private val local = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone)
    private val utc = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.BLACK
    }
    private val heading = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.BLACK
    }
    private val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9.5f
        color = Color.rgb(30, 30, 30)
    }
    private val mono = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 7.5f
        typeface = Typeface.MONOSPACE
        color = Color.rgb(30, 30, 30)
    }
    private val rule = Paint().apply {
        color = Color.rgb(180, 180, 180)
        strokeWidth = 0.5f
    }

    fun write(report: EvidenceReport, out: OutputStream) {
        val doc = PdfDocument()
        val pager = Pager(doc)

        pager.text(context.getString(R.string.evidence_pdf_title), title, gapAfter = 6f)
        pager.text(
            context.getString(
                R.string.evidence_pdf_generated,
                local.format(Instant.ofEpochMilli(report.generatedAt)),
                utc.format(Instant.ofEpochMilli(report.generatedAt)),
                report.appVersion,
                report.deviceDescription,
            ),
            body,
            gapAfter = 12f,
        )

        val a = report.alert
        pager.text(context.getString(R.string.evidence_pdf_summary), heading)
        val summary = listOf(
            R.string.evidence_pdf_type to context.getString(a.family.labelRes()),
            R.string.evidence_pdf_identity to a.signatureKey,
            R.string.evidence_pdf_first to "${local.format(Instant.ofEpochMilli(a.firstSeen))}  (${utc.format(Instant.ofEpochMilli(a.firstSeen))})",
            R.string.evidence_pdf_last to "${local.format(Instant.ofEpochMilli(a.lastSeen))}  (${utc.format(Instant.ofEpochMilli(a.lastSeen))})",
            R.string.evidence_pdf_places to report.places.size.toString(),
            R.string.evidence_pdf_sightings to report.sightings.size.toString(),
            R.string.evidence_pdf_addresses to a.addressesSeen.size.toString(),
            R.string.evidence_pdf_spread to context.getString(R.string.value_metres, a.spreadMeters.roundToInt()),
            R.string.evidence_pdf_closest to context.getString(R.string.value_dbm, a.closestRssi),
            R.string.evidence_pdf_confidence to "${a.confidence.score}/100",
        )
        summary.forEach { (label, value) -> pager.text("${context.getString(label)}: $value", body) }
        pager.gap(4f)
        a.confidence.reasons.forEach { pager.text("•  " + reasonText(context, it), body) }
        pager.gap(10f)

        pager.text(context.getString(R.string.evidence_pdf_map), heading)
        pager.block(MAP_HEIGHT + 6f) { canvas, y ->
            canvas.save()
            canvas.translate(MARGIN, y)
            renderer.draw(
                canvas = canvas,
                width = CONTENT_WIDTH.toInt(),
                height = MAP_HEIGHT.toInt(),
                points = report.places.map { MapPoint(it.latitude, it.longitude, it.number.toString()) },
                style = MapStyle.PRINT,
                density = 0.75f,
                attribution = attribution,
                scaleLabel = { m ->
                    if (m >= 1000) context.getString(R.string.value_km, (m / 1000).toInt())
                    else context.getString(R.string.value_metres, m.toInt())
                },
            )
            canvas.restore()
        }
        pager.gap(8f)

        pager.text(context.getString(R.string.evidence_pdf_places_heading), heading)
        report.places.forEach { p ->
            pager.text(
                context.getString(
                    R.string.evidence_pdf_place_row,
                    p.number,
                    local.format(Instant.ofEpochMilli(p.firstSeen)),
                    local.format(Instant.ofEpochMilli(p.lastSeen)),
                    "%.5f, %.5f".format(java.util.Locale.US, p.latitude, p.longitude),
                    p.bestRssi,
                ),
                body,
            )
        }
        pager.gap(10f)

        pager.text(context.getString(R.string.evidence_pdf_sightings_heading), heading)
        pager.text(context.getString(R.string.evidence_pdf_sightings_columns), mono)
        report.sightings.forEach { o ->
            pager.text(
                String.format(
                    java.util.Locale.US,
                    "%-19s  %-20s  %10.5f  %10.5f  %5s  %5d  %s",
                    local.format(Instant.ofEpochMilli(o.timestamp)),
                    utc.format(Instant.ofEpochMilli(o.timestamp)),
                    o.latitude ?: 0.0,
                    o.longitude ?: 0.0,
                    o.accuracyMeters?.roundToInt()?.toString() ?: "-",
                    o.rssi,
                    o.address,
                ),
                mono,
            )
        }
        pager.gap(10f)

        pager.text(context.getString(R.string.evidence_pdf_method_heading), heading)
        pager.text(context.getString(R.string.evidence_pdf_method), body, gapAfter = 8f)
        pager.text(context.getString(R.string.evidence_pdf_limits_heading), heading)
        pager.text(context.getString(R.string.evidence_pdf_limits), body, gapAfter = 8f)
        pager.text(context.getString(R.string.evidence_pdf_integrity_heading), heading)
        pager.text(context.getString(R.string.evidence_pdf_integrity, report.sha256), body)

        pager.finish()
        doc.writeTo(out)
        doc.close()
    }

    /** Flows text and blocks down A4 pages, starting a new page when one fills up. */
    private inner class Pager(private val doc: PdfDocument) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f

        private fun canvas(): Canvas = page?.canvas ?: newPage()

        private fun newPage(): Canvas {
            page?.let { finishPage(it) }
            number += 1
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())
            page = p
            y = MARGIN
            return p.canvas
        }

        private fun finishPage(p: PdfDocument.Page) {
            val footer = context.getString(R.string.evidence_pdf_footer, number)
            p.canvas.drawLine(MARGIN, PAGE_HEIGHT - 30f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 30f, rule)
            p.canvas.drawText(footer, MARGIN, PAGE_HEIGHT - 18f, body)
            doc.finishPage(p)
        }

        private fun ensure(height: Float) {
            if (page == null || y + height > PAGE_HEIGHT - BOTTOM) newPage()
        }

        fun text(text: String, paint: TextPaint, gapAfter: Float = 2f) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            ensure(layout.height.toFloat())
            val c = canvas()
            c.save()
            c.translate(MARGIN, y)
            layout.draw(c)
            c.restore()
            y += layout.height + gapAfter
        }

        fun block(height: Float, draw: (Canvas, Float) -> Unit) {
            ensure(height)
            draw(canvas(), y)
            y += height
        }

        fun gap(amount: Float) {
            y += amount
        }

        fun finish() {
            page?.let { finishPage(it) }
            page = null
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val BOTTOM = 48f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val MAP_HEIGHT = 300f
    }
}
