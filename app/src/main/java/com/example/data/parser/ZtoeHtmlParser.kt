package com.example.data.parser

import com.example.data.model.DaySchedule
import com.example.data.model.ParsedSchedulePage
import com.example.data.model.QueueDaySchedule
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object ZtoeHtmlParser {

    private val DATE_REGEX = Regex("""\b(\d{2}\.\d{2}\.\d{4})\b""")
    private val QUEUE_REGEX = Regex("""^\s*(\d+\.\d+)\s*$""")
    private val PIDCHERGA_REGEX = Regex("""pidcherga_id=(\d+)""")
    private val UPDATED_REGEX = Regex("""Дата\s+оновлення\s+інформації\s*[-–—:]\s*(.+)""", RegexOption.IGNORE_CASE)
    private val BACKGROUND_REGEX = Regex("""background(?:-color)?\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.US)

    fun decodeBytes(bytes: ByteArray, contentTypeHeader: String? = null): String {
        val headerCharset = extractCharset(contentTypeHeader)
        if (headerCharset != null) {
            return String(bytes, headerCharset)
        }
        // Peek ASCII prefix to detect <meta charset=...>
        val preview = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val metaCharset = extractMetaCharset(preview) ?: Charset.forName("windows-1251")
        return String(bytes, metaCharset)
    }

    fun parse(html: String, fetchedAtEpochMillis: Long = System.currentTimeMillis()): ParsedSchedulePage {
        val doc = Jsoup.parse(html)

        val siteUpdatedText = doc.select("h4, h3, h5, p")
            .asSequence()
            .map { it.text().trim() }
            .mapNotNull { text -> UPDATED_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim() }
            .firstOrNull()
            ?: doc.select("td, div")
                .asSequence()
                .map { it.ownText().trim() }
                .mapNotNull { text -> UPDATED_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim() }
                .firstOrNull()
            ?: ""

        val importantNotice = extractImportantNotice(doc)

        val daySchedules = mutableListOf<DaySchedule>()
        for (table in doc.select("table")) {
            val parsedDay = tryParseScheduleTable(table)
            if (parsedDay != null) {
                daySchedules.add(parsedDay)
            }
        }

        // Deduplicate by dateIso keeping the latest/richest table if duplicate
        val uniqueDays = daySchedules
            .distinctBy { it.dateIso }
            .sortedBy { it.dateIso }

        return ParsedSchedulePage(
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            siteUpdatedText = siteUpdatedText,
            importantNotice = importantNotice,
            days = uniqueDays,
        )
    }

    private fun extractImportantNotice(doc: org.jsoup.nodes.Document): String {
        val heading = doc.select("h3").firstOrNull { h3 ->
            val text = h3.text()
            text.contains("УВАГА", ignoreCase = true) || text.contains("НЕК", ignoreCase = true)
        } ?: return ""

        // Replace <br> with newlines before reading text
        val clone = heading.clone()
        clone.select("br").append("\\n")
        val raw = clone.text().replace("\\n", "\n")
        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Stop before "Пошук черги та підчерги за адресою" if it's inside the same h3
        val filtered = mutableListOf<String>()
        for (line in lines) {
            if (line.contains("Пошук черги", ignoreCase = true)) break
            filtered.add(line)
        }
        return filtered.joinToString("\n")
    }

    private fun tryParseScheduleTable(table: Element): DaySchedule? {
        val directRows = directRowsOf(table)
        if (directRows.size < 3) return null

        var detectedDate: LocalDate? = null
        var detectedDisplayDate: String? = null
        var hasTimeHeader = false

        // Inspect top rows for date & time slots header
        for (row in directRows.take(3)) {
            val rowText = row.text()
            if (detectedDate == null) {
                val dateMatch = DATE_REGEX.find(rowText)
                if (dateMatch != null) {
                    val dateStr = dateMatch.groupValues[1]
                    detectedDate = runCatching { LocalDate.parse(dateStr, DATE_FORMATTER) }.getOrNull()
                    if (detectedDate != null) {
                        detectedDisplayDate = dateStr
                    }
                }
            }
            if (rowText.contains("00:00-00:30") || rowText.contains("00:00")) {
                hasTimeHeader = true
            }
        }

        if (!hasTimeHeader && directRows.none { findQueueIdInRow(it) != null }) {
            return null
        }

        val queues = mutableListOf<QueueDaySchedule>()
        for (row in directRows) {
            val queueInfo = findQueueIdInRow(row) ?: continue
            val slotCells = extractSlotCells(row, queueInfo.second)
            if (slotCells.size < 24) continue

            val slots = slotCells.take(48).map { isOutageCell(it) }
            val paddedSlots = if (slots.size == 48) {
                slots
            } else {
                slots + List(48 - slots.size) { false }
            }

            queues.add(
                QueueDaySchedule(
                    queueId = queueInfo.first,
                    pidchergaId = queueInfo.third,
                    slots = paddedSlots,
                )
            )
        }

        if (queues.isEmpty()) return null

        val finalDate = detectedDate ?: LocalDate.now()
        val finalDisplayDate = detectedDisplayDate ?: finalDate.format(DATE_FORMATTER)

        return DaySchedule(
            dateIso = finalDate.toString(),
            displayDate = finalDisplayDate,
            queues = queues,
        )
    }

    private fun directRowsOf(table: Element): List<Element> {
        val rows = mutableListOf<Element>()
        for (child in table.children()) {
            when (child.tagName().lowercase(Locale.US)) {
                "tr" -> rows.add(child)
                "tbody", "thead", "tfoot" -> {
                    for (subChild in child.children()) {
                        if (subChild.tagName().equals("tr", ignoreCase = true)) {
                            rows.add(subChild)
                        }
                    }
                }
            }
        }
        return rows
    }

    /**
     * Returns Triple(queueId, cellIndexOfQueueId, pidchergaId) if row represents a subqueue.
     */
    private fun findQueueIdInRow(row: Element): Triple<String, Int, String?>? {
        val cells = row.children().filter { it.tagName().equals("td", ignoreCase = true) }
        val maxSearchIndex = minOf(cells.size, 3)
        for (index in 0 until maxSearchIndex) {
            val cell = cells[index]
            val text = cell.text().trim()
            val match = QUEUE_REGEX.matchEntire(text)
            if (match != null) {
                val queueId = match.groupValues[1]
                val href = cell.selectFirst("a[href]")?.attr("href").orEmpty()
                val pidchergaId = PIDCHERGA_REGEX.find(href)?.groupValues?.getOrNull(1)
                return Triple(queueId, index, pidchergaId)
            }
        }
        return null
    }

    private fun extractSlotCells(row: Element, queueCellIndex: Int): List<Element> {
        val cells = row.children().filter { it.tagName().equals("td", ignoreCase = true) }
        val afterQueue = cells.drop(queueCellIndex + 1)

        // ZTOE places a 3pt spacer <td> between queue label and the 48 slot cells
        val spacerIndex = afterQueue.indexOfFirst { cell ->
            val style = cell.attr("style").lowercase(Locale.US)
            style.contains("width:3pt") || style.contains("width: 3pt")
        }
        if (spacerIndex != -1 && spacerIndex + 1 < afterQueue.size) {
            return afterQueue.drop(spacerIndex + 1)
        }

        // Fallback: cells with explicit background style
        val styledCells = afterQueue.filter { cell ->
            cell.attr("style").contains("background", ignoreCase = true) ||
                cell.hasAttr("bgcolor")
        }
        return if (styledCells.size >= 48) styledCells.takeLast(48) else afterQueue
    }

    internal fun isOutageCell(cell: Element): Boolean {
        val style = cell.attr("style")
        val bgFromStyle = BACKGROUND_REGEX.find(style)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.lowercase(Locale.US)
        val bgAttr = cell.attr("bgcolor").trim().lowercase(Locale.US).takeIf { it.isNotEmpty() }
        val colorValue = bgFromStyle ?: bgAttr ?: return false

        val normalized = colorValue
            .replace("!important", "", ignoreCase = true)
            .replace(" ", "")
        val isWhite = normalized == "#ffffff" ||
            normalized == "#fff" ||
            normalized == "white" ||
            normalized == "rgb(255,255,255)" ||
            normalized == "transparent" ||
            normalized == "none"

        return !isWhite
    }

    private fun extractCharset(contentTypeHeader: String?): Charset? {
        if (contentTypeHeader.isNullOrBlank()) return null
        val match = Regex("""charset\s*=\s*["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
            .find(contentTypeHeader) ?: return null
        return runCatching { Charset.forName(match.groupValues[1]) }.getOrNull()
    }

    private fun extractMetaCharset(htmlPrefix: String): Charset? {
        val match = Regex("""charset\s*=\s*["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
            .find(htmlPrefix) ?: return null
        return runCatching { Charset.forName(match.groupValues[1]) }.getOrNull()
    }
}
