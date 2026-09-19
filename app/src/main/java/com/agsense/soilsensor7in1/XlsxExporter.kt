package com.agsense.soilsensor7in1

import java.io.ByteArrayOutputStream
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal dependency-free .xlsx writer for the sensor history table. */
object XlsxExporter {

    private val headers = listOf(
        "זמן", "טמפרטורה (°C)", "לחות (%)", "EC (µS/cm)", "מליחות (mg/L)",
        "חנקן N (mg/kg)", "זרחן P (mg/kg)", "אשלגן K (mg/kg)", "pH"
    )

    fun build(rows: List<HistoryRow>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            add("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            add("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="History" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            add("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            add("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm/yyyy\ hh:mm:ss"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>""")

            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            sb.append("""<sheetViews><sheetView rightToLeft="1" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
            sb.append("""<cols><col min="1" max="1" width="22" customWidth="1"/><col min="2" max="9" width="16" customWidth="1"/></cols>""")
            sb.append("<sheetData>")
            sb.append("""<row r="1">""")
            headers.forEachIndexed { i, h ->
                sb.append("""<c r="${col(i)}1" s="2" t="inlineStr"><is><t>${esc(h)}</t></is></c>""")
            }
            sb.append("</row>")
            rows.forEachIndexed { idx, r ->
                val n = idx + 2
                // Excel date serial in local time
                val local = r.timestampMillis + TimeZone.getDefault().getOffset(r.timestampMillis)
                val serial = local / 86_400_000.0 + 25569.0
                sb.append("""<row r="$n">""")
                sb.append("""<c r="A$n" s="1"><v>$serial</v></c>""")
                sb.append("""<c r="B$n"><v>${r.temperatureC}</v></c>""")
                sb.append("""<c r="C$n"><v>${r.moisturePercent}</v></c>""")
                sb.append("""<c r="D$n"><v>${r.ecUsCm}</v></c>""")
                sb.append("""<c r="E$n"><v>${r.salinityMgL}</v></c>""")
                sb.append("""<c r="F$n"><v>${r.nitrogenMgKg}</v></c>""")
                sb.append("""<c r="G$n"><v>${r.phosphorusMgKg}</v></c>""")
                sb.append("""<c r="H$n"><v>${r.potassiumMgKg}</v></c>""")
                sb.append("""<c r="I$n"><v>${r.ph}</v></c>""")
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            add("xl/worksheets/sheet1.xml", sb.toString())
        }
        return out.toByteArray()
    }

    private fun col(i: Int): String = ('A' + i).toString()

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
