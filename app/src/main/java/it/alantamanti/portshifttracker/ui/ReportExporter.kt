package it.alantamanti.portshifttracker.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import it.alantamanti.portshifttracker.data.repository.ShiftWithPay
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ReportExporter {
    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun writeXlsx(context: Context, uri: Uri, month: YearMonth, rows: List<ShiftWithPay>) {
        context.contentResolver.openOutputStream(uri)?.use { out -> writeWorkbook(out, month, rows) }
            ?: error("Impossibile aprire il file Excel")
    }

    fun writePdf(context: Context, uri: Uri, month: YearMonth, rows: List<ShiftWithPay>) {
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; isFakeBoldText = true }
        paint.textSize = 9f

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var canvas = page.canvas
        var y = 42f

        fun header() {
            canvas.drawText("PortShiftTracker - ${month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ITALIAN)} ${month.year}", 32f, y, titlePaint)
            y += 26f
            canvas.drawText("Data", 32f, y, headerPaint)
            canvas.drawText("Prestazione", 92f, y, headerPaint)
            canvas.drawText("Turno", 185f, y, headerPaint)
            canvas.drawText("Base", 270f, y, headerPaint)
            canvas.drawText("Indennità", 330f, y, headerPaint)
            canvas.drawText("Totale", 405f, y, headerPaint)
            canvas.drawText("Mansione", 470f, y, headerPaint)
            y += 14f
        }

        fun newPage() {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            canvas = page.canvas
            y = 42f
            header()
        }

        header()
        rows.sortedBy { it.shift.startEpochMillis }.forEach { row ->
            if (y > 790f) newPage()
            val start = start(row)
            val turn = row.selectedRules.firstOrNull {
                it.category.name in setOf("TURNO", "DOPPIO", "MEZZO_TURNO")
            }?.name.orEmpty()
            canvas.drawText(start.toLocalDate().format(dateFmt), 32f, y, paint)
            canvas.drawText(performanceText(row), 92f, y, paint)
            canvas.drawText(turn.take(14), 185f, y, paint)
            canvas.drawText(euroNumber(row.pay.basePayCents), 270f, y, paint)
            canvas.drawText(euroNumber(row.pay.allowancesCents), 330f, y, paint)
            canvas.drawText(euroNumber(row.pay.totalPayCents), 405f, y, paint)
            canvas.drawText(row.shift.role.take(18), 470f, y, paint)
            y += 13f
            val detail = row.pay.allowanceLines.joinToString(" · ") { it.name }
            if (detail.isNotBlank()) {
                canvas.drawText(detail.take(90), 92f, y, paint)
                y += 12f
            }
        }
        y += 8f
        canvas.drawText("Totale mese: ${euroNumber(rows.sumOf { it.pay.totalPayCents })}", 330f, y, headerPaint)
        doc.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            ?: error("Impossibile aprire il file PDF")
        doc.close()
    }

    private fun writeWorkbook(out: OutputStream, month: YearMonth, rows: List<ShiftWithPay>) {
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${xml(month.toString())}" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""")
            entry("xl/worksheets/sheet1.xml", sheetXml(rows))
        }
    }

    private fun sheetXml(rows: List<ShiftWithPay>): String {
        val headers = listOf("Data", "Prestazione", "Turno", "Ora", "Base €", "Indennità €", "Totale €", "Mansione", "Dettaglio voci")
        val xml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        fun stringCell(col: Int, row: Int, value: String) = "<c r=\"${cellRef(col, row)}\" t=\"inlineStr\"><is><t>${xml(value)}</t></is></c>"
        fun numberCell(col: Int, row: Int, value: Double) = "<c r=\"${cellRef(col, row)}\"><v>${"%.2f".format(Locale.US, value)}</v></c>"

        xml.append("<row r=\"1\">")
        headers.forEachIndexed { i, h -> xml.append(stringCell(i, 1, h)) }
        xml.append("</row>")

        rows.sortedBy { it.shift.startEpochMillis }.forEachIndexed { index, row ->
            val r = index + 2
            val start = start(row)
            val turn = row.selectedRules.firstOrNull {
                it.category.name in setOf("TURNO", "DOPPIO", "MEZZO_TURNO")
            }?.name.orEmpty()
            xml.append("<row r=\"$r\">")
            xml.append(stringCell(0, r, start.toLocalDate().format(dateFmt)))
            xml.append(stringCell(1, r, performanceText(row)))
            xml.append(stringCell(2, r, turn))
            xml.append(stringCell(3, r, start.format(timeFmt)))
            xml.append(numberCell(4, r, row.pay.basePayCents / 100.0))
            xml.append(numberCell(5, r, row.pay.allowancesCents / 100.0))
            xml.append(numberCell(6, r, row.pay.totalPayCents / 100.0))
            xml.append(stringCell(7, r, row.shift.role))
            xml.append(stringCell(8, r, row.pay.allowanceLines.joinToString("; ") { "${it.name} ${euroNumber(it.amountCents)}" }))
            xml.append("</row>")
        }
        val totalRow = rows.size + 3
        xml.append("<row r=\"$totalRow\">")
        xml.append(stringCell(5, totalRow, "Totale mese"))
        xml.append(numberCell(6, totalRow, rows.sumOf { it.pay.totalPayCents } / 100.0))
        xml.append("</row></sheetData></worksheet>")
        return xml.toString()
    }

    private fun start(row: ShiftWithPay): LocalDateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(row.shift.startEpochMillis), ZoneId.of(row.shift.zoneId)
    )

    private fun performanceText(row: ShiftWithPay): String = when (row.shift.performanceType.name) {
        "DOPPIO" -> "Doppio"
        "MEZZO_DOPPIO" -> "Mezzo Doppio"
        else -> "Turno"
    }

    private fun euroNumber(cents: Long): String = "%.2f".format(Locale.ITALY, cents / 100.0)

    private fun cellRef(col: Int, row: Int): String {
        var n = col + 1
        var letters = ""
        while (n > 0) {
            val rem = (n - 1) % 26
            letters = ('A'.code + rem).toChar() + letters
            n = (n - 1) / 26
        }
        return "$letters$row"
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
