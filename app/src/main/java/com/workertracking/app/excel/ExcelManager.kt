package com.workertracking.app.excel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.workertracking.app.model.AttendanceEntry
import com.workertracking.app.model.AttendanceStatus
import com.workertracking.app.model.MonthInfo
import com.workertracking.app.model.Worker
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportResult(
    val monthInfo: MonthInfo?,
    val workers: List<Worker>,
    val entries: List<AttendanceEntry>
)

class ExcelManager(private val context: Context) {

    // ==========================================
    // 1. EXPORT TIMESHEET (TABEL / LISTA OBECNOŚCI)
    // ==========================================
    fun exportTimesheet(
        monthInfo: MonthInfo,
        workers: List<Worker>,
        entries: List<AttendanceEntry>,
        companyName: String = "DreamHaus",
        responsiblePerson: String = "Maksym Terekhov"
    ): File {
        val excelDir = File(context.cacheDir, "excel").apply { mkdirs() }
        val outputFile = File(excelDir, "timesheet_${monthInfo.year}_${monthInfo.month}.xlsx")

        val templateStream = try {
            context.assets.open("template_timesheet.xlsx")
        } catch (e: Exception) {
            throw IllegalStateException("Template asset template_timesheet.xlsx not found", e)
        }

        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(templateStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val baos = ByteArrayOutputStream()
                zis.copyTo(baos)
                zipEntries[entry.name] = baos.toByteArray()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sheetKey = "xl/worksheets/sheet1.xml"
        if (zipEntries.containsKey(sheetKey)) {
            val originalXml = String(zipEntries[sheetKey]!!, Charsets.UTF_8)
            val modifiedXml = generateSheetXml(originalXml, monthInfo, workers, entries, companyName, responsiblePerson)
            zipEntries[sheetKey] = modifiedXml.toByteArray(Charsets.UTF_8)
        }

        val stylesKey = "xl/styles.xml"
        zipEntries[stylesKey] = getEnhancedTimesheetStylesXml().toByteArray(Charsets.UTF_8)

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                for ((name, bytes) in zipEntries) {
                    val newEntry = ZipEntry(name)
                    zos.putNextEntry(newEntry)
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }

        return outputFile
    }

    private fun generateSheetXml(
        originalXml: String,
        monthInfo: MonthInfo,
        workers: List<Worker>,
        entries: List<AttendanceEntry>,
        companyName: String,
        responsiblePerson: String
    ): String {
        val entryMap = entries.filter { it.year == monthInfo.year && it.month == monthInfo.month }
            .associateBy { "${it.workerId}_${it.day}" }

        val activeWorkers = workers.filter { it.isActive }

        // Update Row 1 with Company Name & Title
        val row1Pattern = Pattern.compile("(<row[^>]*r=\"1\"[^>]*>)(.*?)(</row>)", Pattern.DOTALL)
        val row1Title = "${companyName.uppercase()}  •  ТАБЕЛЬ УЧЁТА ПОСЕЩЕНИЙ  /  LISTA OBECNOŚCI"
        val row1Xml = "<row r=\"1\" spans=\"1:36\"><c r=\"A1\" s=\"1\" t=\"inlineStr\"><is><t>${escapeXml(row1Title)}</t></is></c></row>"

        var xmlWithHeader = if (row1Pattern.matcher(originalXml).find()) {
            row1Pattern.matcher(originalXml).replaceFirst(row1Xml)
        } else {
            originalXml
        }

        // Update Row 2 with Month & Responsible person
        val monthPattern = Pattern.compile("(<row[^>]*r=\"2\"[^>]*>)(.*?)(</row>)", Pattern.DOTALL)
        val monthHeaderLegend = "Фирма: $companyName   •   Месяц: ${monthInfo.titleRuPl}   •   Заполнил: $responsiblePerson       [🟢 Явка=часы, 🔴 Н/N=неявка, 🟡 Б/Ch=болезнь, 🔵 О/U=отпуск, ⚪ В/W=выходной]"
        val row2Xml = "<row r=\"2\" spans=\"1:36\"><c r=\"A2\" s=\"2\" t=\"inlineStr\"><is><t>${escapeXml(monthHeaderLegend)}</t></is></c></row>"

        xmlWithHeader = if (monthPattern.matcher(xmlWithHeader).find()) {
            monthPattern.matcher(xmlWithHeader).replaceFirst(row2Xml)
        } else {
            xmlWithHeader
        }

        // Build worker rows (rows 4 to 4 + size - 1)
        val rowBlocks = StringBuilder()
        var grandTotalDays = 0
        var grandTotalHours = 0.0

        activeWorkers.forEachIndexed { index, worker ->
            val rowNum = 4 + index
            val (rowXml, days, hours) = buildWorkerRowXmlWithStats(rowNum, index + 1, worker, monthInfo, entryMap)
            rowBlocks.append(rowXml)
            grandTotalDays += days
            grandTotalHours += hours
        }

        val totalRowNum = 4 + activeWorkers.size
        // Summary Row "ИТОГО / RAZEM"
        rowBlocks.append("<row r=\"$totalRowNum\" spans=\"1:36\">")
        rowBlocks.append("<c r=\"A$totalRowNum\" s=\"4\"/>")
        rowBlocks.append("<c r=\"B$totalRowNum\" s=\"5\" t=\"inlineStr\"><is><t>ИТОГО / RAZEM</t></is></c>")
        for (day in 1..31) {
            val colLetter = getColumnLetter(day + 2)
            rowBlocks.append("<c r=\"$colLetter$totalRowNum\" s=\"4\"/>")
        }
        rowBlocks.append("<c r=\"AH$totalRowNum\" s=\"4\"><v>$grandTotalDays</v></c>")
        val hStr = if (grandTotalHours % 1.0 == 0.0) grandTotalHours.toInt().toString() else grandTotalHours.toString()
        rowBlocks.append("<c r=\"AI$totalRowNum\" s=\"4\"><v>$hStr</v></c>")
        rowBlocks.append("<c r=\"AJ$totalRowNum\" s=\"5\"/>")
        rowBlocks.append("</row>")

        // Visual Colored Legend Row below table
        val legendRow = totalRowNum + 1
        rowBlocks.append("<row r=\"$legendRow\" spans=\"1:36\" ht=\"20\" customHeight=\"1\">")
        rowBlocks.append("<c r=\"B$legendRow\" s=\"5\" t=\"inlineStr\"><is><t>Обозначения / Legenda:</t></is></c>")
        rowBlocks.append("<c r=\"C$legendRow\" s=\"16\"><v>10</v></c>")
        rowBlocks.append("<c r=\"D$legendRow\" s=\"5\" t=\"inlineStr\"><is><t>Явка (часы)</t></is></c>")
        rowBlocks.append("<c r=\"G$legendRow\" s=\"17\" t=\"inlineStr\"><is><t>Н</t></is></c>")
        rowBlocks.append("<c r=\"H$legendRow\" s=\"5\" t=\"inlineStr\"><is><t>Неявка</t></is></c>")
        rowBlocks.append("<c r=\"K$legendRow\" s=\"18\" t=\"inlineStr\"><is><t>Б</t></is></c>")
        rowBlocks.append("<c r=\"L$legendRow\" s=\"5\" t=\"inlineStr\"><is><t>Болезнь</t></is></c>")
        rowBlocks.append("<c r=\"O$legendRow\" s=\"19\" t=\"inlineStr\"><is><t>О</t></is></c>")
        rowBlocks.append("<c r=\"P$legendRow\" s=\"5\" t=\"inlineStr\"><is><t>Отпуск</t></is></c>")
        rowBlocks.append("<c r=\"S$legendRow\" s=\"20\" t=\"inlineStr\"><is><t>В</t></is></c>")
        rowBlocks.append("<c r=\"T$legendRow\" s=\"5\" t=\"inlineStr\"><is><t>Выходной</t></is></c>")
        rowBlocks.append("</row>")

        // Spacer Row
        val spacerRow = totalRowNum + 2
        rowBlocks.append("<row r=\"$spacerRow\" spans=\"1:36\"/>")

        // Signature Row 1: Sporządził (Maksym Terekhov)
        val sigRow1 = totalRowNum + 3
        rowBlocks.append("<row r=\"$sigRow1\" spans=\"1:36\" ht=\"24\" customHeight=\"1\">")
        rowBlocks.append("<c r=\"B$sigRow1\" s=\"5\" t=\"inlineStr\"><is><t>Заполнил / Sporządził: $responsiblePerson ($companyName)</t></is></c>")
        rowBlocks.append("<c r=\"H$sigRow1\" s=\"5\" t=\"inlineStr\"><is><t>Подпись / Podpis: ___________________</t></is></c>")
        rowBlocks.append("<c r=\"P$sigRow1\" s=\"5\" t=\"inlineStr\"><is><t>Дата / Data: __.__.202_</t></is></c>")
        rowBlocks.append("</row>")

        // Signature Row 2: Sprawdził / Kierownik robót
        val sigRow2 = totalRowNum + 4
        rowBlocks.append("<row r=\"$sigRow2\" spans=\"1:36\" ht=\"24\" customHeight=\"1\">")
        rowBlocks.append("<c r=\"B$sigRow2\" s=\"5\" t=\"inlineStr\"><is><t>Проверил / Sprawdził: ___________________ (Kierownik robót)</t></is></c>")
        rowBlocks.append("<c r=\"H$sigRow2\" s=\"5\" t=\"inlineStr\"><is><t>Подпись / Podpis: ___________________</t></is></c>")
        rowBlocks.append("<c r=\"P$sigRow2\" s=\"5\" t=\"inlineStr\"><is><t>Печать / Pieczęć: ( M.P. )</t></is></c>")
        rowBlocks.append("</row>")

        val rowsStartIdx = xmlWithHeader.indexOf("<row r=\"4\"")
        val rowsEndIdx = xmlWithHeader.indexOf("</sheetData>")

        if (rowsStartIdx != -1 && rowsEndIdx != -1) {
            val prefix = xmlWithHeader.substring(0, rowsStartIdx)
            val suffix = xmlWithHeader.substring(rowsEndIdx)
            return prefix + rowBlocks.toString() + suffix
        }

        return xmlWithHeader
    }

    private data class RowStats(val rowXml: String, val days: Int, val hours: Double)

    private fun buildWorkerRowXmlWithStats(
        rowNum: Int,
        orderIndex: Int,
        worker: Worker,
        monthInfo: MonthInfo,
        entryMap: Map<String, AttendanceEntry>
    ): RowStats {
        val sb = StringBuilder()
        sb.append("<row r=\"$rowNum\" spans=\"1:36\">")

        sb.append("<c r=\"A$rowNum\" s=\"4\"><v>$orderIndex</v></c>")
        sb.append("<c r=\"B$rowNum\" s=\"5\" t=\"inlineStr\"><is><t>${escapeXml(worker.fullName)}</t></is></c>")

        var totalDays = 0
        var totalHours = 0.0

        for (day in 1..31) {
            val colLetter = getColumnLetter(day + 2)
            val cellRef = "$colLetter$rowNum"
            val entry = entryMap["${worker.id}_$day"]

            if (day > monthInfo.daysInMonth) {
                // Inactive day outside month: blocked gray fill
                sb.append("<c r=\"$cellRef\" s=\"21\"/>")
            } else if (entry != null) {
                if (entry.hours != null && entry.hours > 0) {
                    totalDays++
                    totalHours += entry.hours
                    val hoursStr = if (entry.hours % 1.0 == 0.0) entry.hours.toInt().toString() else entry.hours.toString()
                    // Green badge (#D1FAE5) with dark green bold text (#065F46)
                    sb.append("<c r=\"$cellRef\" s=\"16\"><v>$hoursStr</v></c>")
                } else if (entry.status != null) {
                    val (styleIdx, code) = when (entry.status) {
                        AttendanceStatus.ABSENT -> Pair("17", entry.status.codeRu)   // Red (#FEE2E2), dark red bold
                        AttendanceStatus.SICK -> Pair("18", entry.status.codeRu)     // Yellow (#FEF3C7), dark amber bold
                        AttendanceStatus.VACATION -> Pair("19", entry.status.codeRu) // Sky blue (#E0F2FE), dark blue bold
                        AttendanceStatus.DAY_OFF -> Pair("20", entry.status.codeRu)  // Soft gray (#F3F4F6), dark gray bold
                        AttendanceStatus.PRESENT -> Pair("16", entry.status.codeRu)  // Green (#D1FAE5)
                    }
                    sb.append("<c r=\"$cellRef\" s=\"$styleIdx\" t=\"inlineStr\"><is><t>$code</t></is></c>")
                } else {
                    sb.append("<c r=\"$cellRef\" s=\"22\"/>")
                }
            } else {
                sb.append("<c r=\"$cellRef\" s=\"22\"/>")
            }
        }

        val totalDaysStr = totalDays.toString()
        sb.append("<c r=\"AH$rowNum\" s=\"4\"><f>COUNT(C$rowNum:AG$rowNum)</f><v>$totalDaysStr</v></c>")

        val totalHoursStr = if (totalHours % 1.0 == 0.0) totalHours.toInt().toString() else totalHours.toString()
        sb.append("<c r=\"AI$rowNum\" s=\"4\"><f>SUM(C$rowNum:AG$rowNum)</f><v>$totalHoursStr</v></c>")

        val notes = escapeXml(worker.notes)
        if (notes.isNotEmpty()) {
            sb.append("<c r=\"AJ$rowNum\" s=\"5\" t=\"inlineStr\"><is><t>$notes</t></is></c>")
        } else {
            sb.append("<c r=\"AJ$rowNum\" s=\"5\"/>")
        }

        sb.append("</row>")
        return RowStats(sb.toString(), totalDays, totalHours)
    }

    private fun getEnhancedTimesheetStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:x14ac="http://schemas.microsoft.com/office/spreadsheetml/2009/9/ac" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006">
  <fonts count="12">
    <font><sz val="11.0"/><color theme="1"/><name val="Calibri"/><scheme val="minor"/></font>
    <font><b/><sz val="13.0"/><color theme="1"/><name val="Calibri"/></font>
    <font><i/><sz val="8.0"/><color theme="1"/><name val="Calibri"/></font>
    <font><b/><sz val="8.0"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><sz val="11.0"/><color theme="1"/><name val="Calibri"/></font>
    <font><sz val="10.0"/><color theme="1"/><name val="Calibri"/></font>
    <font><b/><sz val="11.0"/><color theme="1"/><name val="Calibri"/></font>
    <font><b/><sz val="11.0"/><color rgb="FF047857"/><name val="Calibri"/></font>
    <font><b/><sz val="11.0"/><color rgb="FFB91C1C"/><name val="Calibri"/></font>
    <font><b/><sz val="11.0"/><color rgb="FF92400E"/><name val="Calibri"/></font>
    <font><b/><sz val="11.0"/><color rgb="FF0369A1"/><name val="Calibri"/></font>
    <font><b/><sz val="11.0"/><color rgb="FF1E293B"/><name val="Calibri"/></font>
  </fonts>
  <fills count="12">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="lightGray"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF2F5496"/><bgColor rgb="FF2F5496"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFD9E2F3"/><bgColor rgb="FFD9E2F3"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFFFFF"/><bgColor rgb="FFFFFFFF"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFF2CC"/><bgColor rgb="FFFFF2CC"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFA7F3D0"/><bgColor rgb="FFA7F3D0"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFECACA"/><bgColor rgb="FFFECACA"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFDE68A"/><bgColor rgb="FFFDE68A"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFBAE6FD"/><bgColor rgb="FFBAE6FD"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFCBD5E1"/><bgColor rgb="FFCBD5E1"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF94A3B8"/><bgColor rgb="FF94A3B8"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border/>
    <border>
      <left style="thin"><color rgb="FFB0B0B0"/></left>
      <right style="thin"><color rgb="FFB0B0B0"/></right>
      <top style="thin"><color rgb="FFB0B0B0"/></top>
      <bottom style="thin"><color rgb="FFB0B0B0"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf borderId="0" fillId="0" fontId="0" numFmtId="0" applyAlignment="1" applyFont="1"/>
  </cellStyleXfs>
  <cellXfs count="23">
    <xf borderId="0" fillId="0" fontId="0" numFmtId="0" xfId="0" applyAlignment="1" applyFont="1"><alignment readingOrder="0" shrinkToFit="0" vertical="bottom" wrapText="0"/></xf>
    <xf borderId="0" fillId="0" fontId="1" numFmtId="0" xfId="0" applyAlignment="1" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf borderId="0" fillId="0" fontId="2" numFmtId="0" xfId="0" applyAlignment="1" applyFont="1"><alignment horizontal="center"/></xf>
    <xf borderId="1" fillId="2" fontId="3" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" shrinkToFit="0" vertical="center" wrapText="1"/></xf>
    <xf borderId="1" fillId="3" fontId="4" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center"/></xf>
    <xf borderId="1" fillId="3" fontId="5" numFmtId="0" xfId="0" applyBorder="1" applyFill="1" applyFont="1"/>
    <xf borderId="1" fillId="3" fontId="4" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" readingOrder="0"/></xf>
    <xf borderId="1" fillId="3" fontId="6" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center"/></xf>
    <xf borderId="1" fillId="3" fontId="4" numFmtId="0" xfId="0" applyBorder="1" applyFill="1" applyFont="1"/>
    <xf borderId="1" fillId="4" fontId="4" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center"/></xf>
    <xf borderId="1" fillId="4" fontId="5" numFmtId="0" xfId="0" applyBorder="1" applyFill="1" applyFont="1"/>
    <xf borderId="1" fillId="4" fontId="4" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" readingOrder="0"/></xf>
    <xf borderId="1" fillId="4" fontId="6" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center"/></xf>
    <xf borderId="1" fillId="4" fontId="4" numFmtId="0" xfId="0" applyBorder="1" applyFill="1" applyFont="1"/>
    <xf borderId="1" fillId="5" fontId="6" numFmtId="0" xfId="0" applyBorder="1" applyFill="1" applyFont="1"/>
    <xf borderId="1" fillId="5" fontId="6" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center"/></xf>
    <xf borderId="1" fillId="6" fontId="7" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf borderId="1" fillId="7" fontId="8" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf borderId="1" fillId="8" fontId="9" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf borderId="1" fillId="9" fontId="10" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf borderId="1" fillId="10" fontId="11" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf borderId="1" fillId="11" fontId="4" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf borderId="1" fillId="4" fontId="4" numFmtId="0" xfId="0" applyAlignment="1" applyBorder="1" applyFill="1" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
  </cellXfs>
  <cellStyles count="1">
    <cellStyle xfId="0" name="Normal" builtinId="0"/>
  </cellStyles>
  <dxfs count="0"/>
</styleSheet>""".trimIndent()
    }

    // ==========================================
    // 2. EXPORT WORKERS REGISTRY AS SEPARATE EXCEL
    // ==========================================
    fun exportWorkersRegistry(
        workers: List<Worker>,
        companyName: String = "DreamHaus",
        responsiblePerson: String = "Maksym Terekhov"
    ): File {
        val excelDir = File(context.cacheDir, "excel").apply { mkdirs() }
        val outputFile = File(excelDir, "rejestr_pracownikow.xlsx")

        val typesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".trimIndent()

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent()

        val wbRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trimIndent()

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Рабочие - Pracownicy" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".trimIndent()

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="4">
    <font><sz val="10"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="13"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><i/><sz val="9"/><color rgb="FF64748B"/><name val="Calibri"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1E293B"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF2563EB"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="6">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" alignment="center"/>
    <xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" alignment="center"/>
    <xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" alignment="center"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" alignment="center"/>
  </cellXfs>
</styleSheet>""".trimIndent()

        val sheetXml = buildWorkersSheetXml(workers, companyName, responsiblePerson)

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                fun addFile(path: String, content: String) {
                    val entry = ZipEntry(path)
                    zos.putNextEntry(entry)
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                addFile("[Content_Types].xml", typesXml)
                addFile("_rels/.rels", relsXml)
                addFile("xl/_rels/workbook.xml.rels", wbRelsXml)
                addFile("xl/workbook.xml", workbookXml)
                addFile("xl/styles.xml", stylesXml)
                addFile("xl/worksheets/sheet1.xml", sheetXml)
            }
        }

        return outputFile
    }

    private fun buildWorkersSheetXml(
        workers: List<Worker>,
        companyName: String,
        responsiblePerson: String
    ): String {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val totalCount = workers.size
        val activeCount = workers.count { it.isActive }

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        sb.append("<cols>")
        sb.append("""<col min="1" max="1" width="6" customWidth="1"/>""")   // A: №
        sb.append("""<col min="2" max="2" width="28" customWidth="1"/>""")  // B: ФИО
        sb.append("""<col min="3" max="3" width="24" customWidth="1"/>""")  // C: Должность
        sb.append("""<col min="4" max="4" width="16" customWidth="1"/>""")  // D: Ставка
        sb.append("""<col min="5" max="5" width="18" customWidth="1"/>""")  // E: Телефон
        sb.append("""<col min="6" max="6" width="18" customWidth="1"/>""")  // F: SEP
        sb.append("""<col min="7" max="7" width="14" customWidth="1"/>""")  // G: Обувь
        sb.append("""<col min="8" max="8" width="14" customWidth="1"/>""")  // H: Куртка
        sb.append("""<col min="9" max="9" width="14" customWidth="1"/>""")  // I: Штаны
        sb.append("""<col min="10" max="10" width="14" customWidth="1"/>""")// J: Год рожд.
        sb.append("""<col min="11" max="11" width="12" customWidth="1"/>""")// K: Возраст
        sb.append("""<col min="12" max="12" width="26" customWidth="1"/>""")// L: Примечания
        sb.append("</cols>")

        sb.append("<sheetData>")

        // Row 1: Title Banner with Company Name
        sb.append("""<row r="1" ht="32" customHeight="1">""")
        sb.append("""<c r="A1" s="1" t="inlineStr"><is><t>${escapeXml(companyName.uppercase())}  •  РЕЕСТР СОТРУДНИКОВ  /  REJESTR PRACOWNIKÓW</t></is></c>""")
        sb.append("</row>")

        // Row 2: Subtitle with date and responsible person
        sb.append("""<row r="2" ht="22" customHeight="1">""")
        sb.append("""<c r="A2" s="2" t="inlineStr"><is><t>Фирма: $companyName   •   Составил / Sporządził: $responsiblePerson   •   Дата: $todayStr   •   Сотрудников: $totalCount (активных: $activeCount)</t></is></c>""")
        sb.append("</row>")

        // Row 3: Headers
        sb.append("""<row r="3" ht="24" customHeight="1">""")
        val headers = listOf(
            "A3" to "№",
            "B3" to "ФИО / Imię i nazwisko",
            "C3" to "Должность / Stanowisko",
            "D3" to "Ставка (zł/ч)",
            "E3" to "Телефон / Telefon",
            "F3" to "SEP (Uprawnienia)",
            "G3" to "Обувь / Buty",
            "H3" to "Куртка / Kurtka",
            "I3" to "Штаны / Spodnie",
            "J3" to "Год рожд.",
            "K3" to "Возраст",
            "L3" to "Примечания / Uwagi"
        )
        headers.forEach { (ref, label) ->
            sb.append("""<c r="$ref" s="3" t="inlineStr"><is><t>${escapeXml(label)}</t></is></c>""")
        }
        sb.append("</row>")

        // Rows 4+: Worker records
        workers.forEachIndexed { index, worker ->
            val r = 4 + index
            sb.append("""<row r="$r" ht="20" customHeight="1">""")

            sb.append("""<c r="A$r" s="5"><v>${index + 1}</v></c>""")
            sb.append("""<c r="B$r" s="4" t="inlineStr"><is><t>${escapeXml(worker.fullName)}</t></is></c>""")
            sb.append("""<c r="C$r" s="4" t="inlineStr"><is><t>${escapeXml(worker.position)}</t></is></c>""")

            val rateText = if (worker.hourlyRate > 0) "${worker.hourlyRate} zł" else "—"
            sb.append("""<c r="D$r" s="5" t="inlineStr"><is><t>${escapeXml(rateText)}</t></is></c>""")
            sb.append("""<c r="E$r" s="5" t="inlineStr"><is><t>${escapeXml(worker.phone.ifEmpty { "—" })}</t></is></c>""")

            val sepText = if (worker.hasSep) {
                if (worker.sepDetails.isNotBlank()) "Да (${worker.sepDetails})" else "Да (SEP)"
            } else {
                "Нет"
            }
            sb.append("""<c r="F$r" s="5" t="inlineStr"><is><t>${escapeXml(sepText)}</t></is></c>""")
            sb.append("""<c r="G$r" s="5" t="inlineStr"><is><t>${escapeXml(worker.shoeSize.ifEmpty { "—" })}</t></is></c>""")
            sb.append("""<c r="H$r" s="5" t="inlineStr"><is><t>${escapeXml(worker.jacketSize.ifEmpty { "—" })}</t></is></c>""")
            sb.append("""<c r="I$r" s="5" t="inlineStr"><is><t>${escapeXml(worker.pantsSize.ifEmpty { "—" })}</t></is></c>""")

            val byText = worker.birthYear?.toString() ?: "—"
            sb.append("""<c r="J$r" s="5" t="inlineStr"><is><t>${escapeXml(byText)}</t></is></c>""")

            val ageText = worker.age?.toString() ?: "—"
            sb.append("""<c r="K$r" s="5" t="inlineStr"><is><t>${escapeXml(ageText)}</t></is></c>""")

            sb.append("""<c r="L$r" s="4" t="inlineStr"><is><t>${escapeXml(worker.notes)}</t></is></c>""")

            sb.append("</row>")
        }

        // Signature footer rows
        val lastR = 4 + workers.size
        val sig1 = lastR + 2
        sb.append("""<row r="$sig1" ht="24" customHeight="1">""")
        sb.append("""<c r="B$sig1" s="4" t="inlineStr"><is><t>Документ составил / Sporządził: $responsiblePerson ($companyName)</t></is></c>""")
        sb.append("""<c r="F$sig1" s="4" t="inlineStr"><is><t>Подпись / Podpis: ___________________</t></is></c>""")
        sb.append("""<c r="J$sig1" s="4" t="inlineStr"><is><t>Дата / Data: $todayStr</t></is></c>""")
        sb.append("</row>")

        val sig2 = lastR + 3
        sb.append("""<row r="$sig2" ht="24" customHeight="1">""")
        sb.append("""<c r="B$sig2" s="4" t="inlineStr"><is><t>Утвердил / Zatwierdził (Zarząd $companyName): ___________________</t></is></c>""")
        sb.append("""<c r="F$sig2" s="4" t="inlineStr"><is><t>Подпись / Podpis: ___________________</t></is></c>""")
        sb.append("""<c r="J$sig2" s="4" t="inlineStr"><is><t>Печать / Pieczęć: ( M.P. )</t></is></c>""")
        sb.append("</row>")

        sb.append("</sheetData>")

        sb.append("<mergeCells count=\"2\">")
        sb.append("""<mergeCell ref="A1:L1"/>""")
        sb.append("""<mergeCell ref="A2:L2"/>""")
        sb.append("</mergeCells>")

        sb.append("</worksheet>")
        return sb.toString()
    }

    fun getShareIntent(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportDateAdvanceGroupToTextFile(group: com.workertracking.app.model.DateAdvanceGroup): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeDate = group.date.replace(".", "_")
        val file = File(reportsDir, "vedomost_$safeDate.txt")
        file.writeText(group.formatAsShareText(), Charsets.UTF_8)
        return file
    }

    fun exportSingleAdvanceToTextFile(advance: com.workertracking.app.model.AdvancePayment, companyName: String = "DreamHaus"): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeDate = advance.date.replace(".", "_")
        val safeName = advance.workerName.replace(" ", "_").replace(":", "")
        val file = File(reportsDir, "avans_${safeName}_$safeDate.txt")
        val amountStr = if (advance.amount % 1.0 == 0.0) advance.amount.toInt().toString() else "%.2f".format(advance.amount)
        val text = buildString {
            appendLine("$companyName • Выплата аванса")
            appendLine("Дата: ${advance.date}")
            appendLine("Сотрудник: ${advance.workerName}")
            appendLine("Сумма: $amountStr ${advance.currency}")
            appendLine("Способ оплаты: ${advance.paymentMethod}")
            if (advance.comment.isNotEmpty()) appendLine("Примечание: ${advance.comment}")
        }
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    fun getTextFileShareIntent(file: File, subject: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, file.readText())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ==========================================
    // 3. IMPORT TIMESHEET (.XLSX)
    // ==========================================
    fun importTimesheet(inputStream: InputStream): ImportResult {
        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val baos = ByteArrayOutputStream()
                zis.copyTo(baos)
                zipEntries[entry.name] = baos.toByteArray()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = mutableListOf<String>()
        val sstBytes = zipEntries["xl/sharedStrings.xml"]
        if (sstBytes != null) {
            val sstXml = String(sstBytes, Charsets.UTF_8)
            val siPattern = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL)
            val siMatcher = siPattern.matcher(sstXml)
            val tPattern = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL)
            while (siMatcher.find()) {
                val siContent = siMatcher.group(1) ?: ""
                val tMatcher = tPattern.matcher(siContent)
                val fullText = StringBuilder()
                while (tMatcher.find()) {
                    fullText.append(tMatcher.group(1))
                }
                sharedStrings.add(unescapeXml(fullText.toString()))
            }
        }

        val sheetBytes = zipEntries["xl/worksheets/sheet1.xml"]
            ?: throw IllegalArgumentException("Не найден лист sheet1.xml в файле Excel")

        val sheetXml = String(sheetBytes, Charsets.UTF_8)

        var detectedMonth: MonthInfo? = null
        val row2Matcher = Pattern.compile("<row[^>]*r=\"2\"[^>]*>(.*?)</row>", Pattern.DOTALL).matcher(sheetXml)
        if (row2Matcher.find()) {
            val row2Content = row2Matcher.group(1) ?: ""
            val text = extractCellText(row2Content, sharedStrings)
            detectedMonth = parseMonthInfoFromText(text)
        }

        val effectiveMonth = detectedMonth ?: MonthInfo.current()

        val rowPattern = Pattern.compile("<row[^>]*r=\"(\\d+)\"[^>]*>(.*?)</row>", Pattern.DOTALL)
        val rowMatcher = rowPattern.matcher(sheetXml)

        val workers = mutableListOf<Worker>()
        val entries = mutableListOf<AttendanceEntry>()

        while (rowMatcher.find()) {
            val rowNum = rowMatcher.group(1)?.toIntOrNull() ?: continue
            if (rowNum < 4) continue

            val rowContent = rowMatcher.group(2) ?: continue
            val cellMap = extractRowCells(rowContent, sharedStrings)

            val name = cellMap["B"]?.trim() ?: ""
            if (name.isEmpty() || name.contains("ИТОГО", ignoreCase = true) || name.contains("RAZEM", ignoreCase = true) || name.contains("Заполнил", ignoreCase = true) || name.contains("Sporządził", ignoreCase = true)) {
                continue
            }

            val workerId = System.currentTimeMillis() + workers.size
            val orderNum = cellMap["A"]?.toDoubleOrNull()?.toInt() ?: (workers.size + 1)
            val notes = cellMap["AJ"] ?: ""

            val worker = Worker(
                id = workerId,
                orderIndex = orderNum,
                fullName = name,
                notes = notes
            )
            workers.add(worker)

            for (day in 1..31) {
                val colLetter = getColumnLetter(day + 2)
                val cellVal = cellMap[colLetter]?.trim() ?: continue
                if (cellVal.isEmpty()) continue

                val numHours = cellVal.toDoubleOrNull()
                if (numHours != null) {
                    entries.add(
                        AttendanceEntry(
                            workerId = workerId,
                            year = effectiveMonth.year,
                            month = effectiveMonth.month,
                            day = day,
                            hours = numHours
                        )
                    )
                } else {
                    val status = AttendanceStatus.fromString(cellVal)
                    if (status != null) {
                        entries.add(
                            AttendanceEntry(
                                workerId = workerId,
                                year = effectiveMonth.year,
                                month = effectiveMonth.month,
                                day = day,
                                status = status
                            )
                        )
                    }
                }
            }
        }

        return ImportResult(effectiveMonth, workers, entries)
    }

    private fun extractRowCells(rowXml: String, sharedStrings: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val cPattern = Pattern.compile("<c[^>]*r=\"([A-Z]+)\\d+\"([^>]*)>(.*?)</c>", Pattern.DOTALL)
        val cMatcher = cPattern.matcher(rowXml)
        while (cMatcher.find()) {
            val col = cMatcher.group(1) ?: continue
            val attrs = cMatcher.group(2) ?: ""
            val inner = cMatcher.group(3) ?: ""

            var valText = ""
            if (inner.contains("<is>")) {
                val tMatcher = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL).matcher(inner)
                if (tMatcher.find()) {
                    valText = unescapeXml(tMatcher.group(1) ?: "")
                }
            } else if (inner.contains("<v>")) {
                val vMatcher = Pattern.compile("<v>(.*?)</v>").matcher(inner)
                if (vMatcher.find()) {
                    val rawVal = vMatcher.group(1) ?: ""
                    if (attrs.contains("t=\"s\"")) {
                        val sIdx = rawVal.toIntOrNull()
                        if (sIdx != null && sIdx in sharedStrings.indices) {
                            valText = sharedStrings[sIdx]
                        }
                    } else {
                        valText = rawVal
                    }
                }
            }
            if (valText.isNotEmpty()) {
                map[col] = valText
            }
        }
        return map
    }

    private fun extractCellText(content: String, sharedStrings: List<String>): String {
        val vMatcher = Pattern.compile("<v>(.*?)</v>").matcher(content)
        if (vMatcher.find()) {
            val idx = vMatcher.group(1)?.toIntOrNull()
            if (idx != null && idx in sharedStrings.indices) {
                return sharedStrings[idx]
            }
        }
        val tMatcher = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL).matcher(content)
        if (tMatcher.find()) {
            return unescapeXml(tMatcher.group(1) ?: "")
        }
        return ""
    }

    private fun parseMonthInfoFromText(text: String): MonthInfo? {
        val lower = text.lowercase()
        val yearMatcher = Pattern.compile("\\b(20\\d\\d)\\b").matcher(text)
        val year = if (yearMatcher.find()) yearMatcher.group(1)?.toIntOrNull() ?: 2026 else 2026

        val monthIndex = MonthInfo.monthNames.indexOfFirst { (ru, pl) ->
            lower.contains(ru.lowercase()) || lower.contains(pl.lowercase())
        }

        return if (monthIndex != -1) {
            MonthInfo(year, monthIndex + 1)
        } else {
            null
        }
    }

    private fun getColumnLetter(colIndex: Int): String {
        var temp = colIndex
        val colLetter = StringBuilder()
        while (temp > 0) {
            val rem = (temp - 1) % 26
            colLetter.append((65 + rem).toChar())
            temp = (temp - 1) / 26
        }
        return colLetter.reverse().toString()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun unescapeXml(str: String): String {
        return str.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}
