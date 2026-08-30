package com.example.shuati.logic

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * 轻量级 xlsx（Excel 2007+）读写器，零外部依赖，替代 Apache POI 以大幅减小包体。
 *
 * xlsx 本质是「ZIP 容器 + XML」：工作簿由 `[Content_Types].xml`、`xl/workbook.xml`、
 * `xl/worksheets/sheet1.xml` 等条目组成，单元格文本用 inline string 或 sharedStrings 表示。
 *
 * 本实现仅覆盖本项目所需的能力：
 * - 读取：取第一个 sheet 的每行每单元格的**文本值**（兼容 inlineStr / sharedStr / 数字 / 空）。
 * - 写入：把二维字符串表格写成第一个 sheet（用 inline string，简单可靠，兼容 Excel/WPS）。
 * 不涉及样式、公式、合并单元格、多 sheet 等高级特性。
 */
object LightXlsx {

    /** 把二维字符串表格写成 xlsx 字节流。cellTexts[i][j] 为第 i 行第 j 列的文本。 */
    fun write(cellTexts: List<List<String>>, out: OutputStream) {
        val rowsXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<sheetData>")
            cellTexts.forEachIndexed { r, row ->
                append("<row r=\"${r + 1}\">")
                row.forEachIndexed { c, text ->
                    append("<c r=\"${columnName(c)}${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    append(xmlEscape(text))
                    append("</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        ZipOutputStream(out).use { zos ->
            put(zos, "[Content_Types].xml", CONTENT_TYPES)
            put(zos, "_rels/.rels", ROOT_RELS)
            put(zos, "xl/workbook.xml", WORKBOOK_XML)
            put(zos, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put(zos, "xl/worksheets/sheet1.xml", rowsXml)
        }
    }

    /** 读取第一个 sheet，返回所有行的单元格文本（行号连续，缺失行给空 list）。 */
    fun read(stream: InputStream): List<List<String>> {
        // 校验 xlsx 魔数 "PK\x03\x04"：无效字节应明确抛错（由 UI 提示用户），而非静默返回空
        val head = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = stream.read(head, read, 4 - read)
            if (n < 0) break
            read += n
        }
        val isZip = read >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
        if (!isZip) {
            throw IllegalArgumentException("无效的 .xlsx 文件（不是合法的 ZIP/OOXML 格式）")
        }
        val entries = mutableMapOf<String, ByteArray>()
        // 合并已读的魔数头 + 剩余字节
        val full = ByteArrayOutputStream()
        full.write(head, 0, read)
        stream.copyTo(full)
        ZipInputStream(full.toByteArray().inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }

        // 共享字符串（Excel/WPS 导出常用 sharedStrings，而非 inlineStr）
        val shared = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { raw ->
            val doc = parseXml(raw)
            val siNodes = doc.getElementsByTagName("si")
            for (i in 0 until siNodes.length) {
                shared.add(siText(siNodes.item(i) as Element))
            }
        }

        val sheetXml = entries["xl/worksheets/sheet1.xml"] ?: return emptyList()
        val doc = parseXml(sheetXml)
        val rows = doc.getElementsByTagName("row")

        val out = mutableListOf<List<String>>()
        var lastRowNum = 0
        val byIndex = mutableMapOf<Int, List<String>>()
        for (i in 0 until rows.length) {
            val rowEl = rows.item(i) as Element
            val rowIndex = (rowEl.getAttribute("r").toIntOrNull() ?: (lastRowNum + 1)) - 1
            val cells = mutableListOf<Pair<Int, String>>()
            val cellNodes = rowEl.getElementsByTagName("c")
            for (j in 0 until cellNodes.length) {
                val cEl = cellNodes.item(j) as Element
                val cellRef = cEl.getAttribute("r")
                val col = if (cellRef.isNotEmpty()) columnIndexOf(cellRef) else j
                cells.add(col to cellValue(cEl, shared))
            }
            val maxCol = cells.maxOfOrNull { it.first } ?: -1
            val rowTexts = MutableList(maxCol + 1) { "" }
            cells.forEach { (c, v) -> rowTexts[c] = v }
            byIndex[rowIndex] = rowTexts
            if (rowIndex > lastRowNum) lastRowNum = rowIndex
        }
        for (r in 0..lastRowNum) {
            out.add(byIndex[r] ?: emptyList())
        }
        return out
    }

    // ── 内部工具 ────────────────────────────────────────────────

    private fun cellValue(cEl: Element, shared: List<String>): String {
        val type = cEl.getAttribute("t")
        val v = cEl.getElementsByTagName("v")
        return when (type) {
            "s" -> { // shared string
                val idx = if (v.length > 0) v.item(0).textContent.toIntOrNull() ?: -1 else -1
                if (idx in shared.indices) shared[idx] else ""
            }
            "inlineStr" -> { // inline string
                val isEl = cEl.getElementsByTagName("is")
                if (isEl.length > 0) siText(isEl.item(0) as Element) else ""
            }
            "str", "" -> if (v.length > 0) v.item(0).textContent else "" // 公式结果字符串 / 数字 / 空
            "b" -> if (v.length > 0) (v.item(0).textContent == "1").toString() else ""
            else -> if (v.length > 0) v.item(0).textContent else ""
        }
    }

    /** 提取 <si> 内的全部文本（处理富文本 run）。 */
    private fun siText(si: Element): String {
        val tNodes = si.getElementsByTagName("t")
        if (tNodes.length == 0) return ""
        return buildString {
            for (k in 0 until tNodes.length) append(tNodes.item(k).textContent)
        }
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(bytes.inputStream())
    }

    /** 列名：0→A、25→Z、26→AA。 */
    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.append(('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return sb.reverse().toString()
    }

    /** 列引用（如 "AB12"）→ 列下标（AB→27）。 */
    private fun columnIndexOf(ref: String): Int {
        var result = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            result = result * 26 + (ch.uppercaseChar().code - 'A'.code + 1)
        }
        return result - 1
    }

    private fun xmlEscape(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }

    private fun put(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="题库" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
}
