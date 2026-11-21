package com.example

import com.example.data.parser.ZtoeHtmlParser
import com.example.domain.ScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.time.Duration
import java.time.LocalDateTime

class ZtoeParserAndCalculatorTest {

    private fun buildSlotCells(outageIndices: Set<Int>): String {
        return (0 until 48).joinToString("") { idx ->
            val bg = if (idx in outageIndices) "#ff3333" else "#ffffff"
            """<td style="text-align:center;border-top:1pt solid black; background: $bg"><a href="/unhooking-search.php?pidcherga_id=1">&nbsp;</a></td>"""
        }
    }

    private fun buildSampleHtml(): String {
        val headers = (0 until 48).joinToString("") { idx ->
            val h1 = idx / 2
            val m1 = if (idx % 2 == 0) "00" else "30"
            val h2 = (idx + 1) / 2
            val m2 = if ((idx + 1) % 2 == 0) "00" else "30"
            """<td style="background: white;">%02d:%s-%02d:%s</td>""".format(h1, m1, h2, m2)
        }
        // Queue 1.1: outages at 10:00-12:00 (slots 20..23) and 22:30-24:00 (slots 45..47)
        val row11Slots = buildSlotCells(setOf(20, 21, 22, 23, 45, 46, 47))
        // Queue 1.2: outage at 14:00-16:30 (slots 28..32)
        val row12Slots = buildSlotCells(setOf(28, 29, 30, 31, 32))

        return """
            <html>
            <head><META http-equiv="Content-Type" content="text/html; charset=windows-1251"></head>
            <body>
            <table width="100%"><tr><td align="center">
              <h3>УВАГА! ВАЖЛИВА ІНФОРМАЦІЯ!<br>з 16:30 до 20:00 - 1,5 черги</h3>
              <h3>Графіки погодинних відключень</h3>
              <h4>Дата оновлення інформації - 17:30 21.11.2026</h4>
              <table x:str border="0">
                <tr>
                  <td rowspan="2"><b>Черга</b></td>
                  <td rowspan="2"><b>Підчерга</b></td>
                  <td style="width:3pt">&nbsp;</td>
                  <td colspan="48"><b>21.11.2026</b></td>
                </tr>
                <tr>
                  <td style="width:3pt">&nbsp;</td>
                  $headers
                </tr>
                <tr>
                  <td rowspan="2"><b>1</b></td>
                  <td><a href="/unhooking-search.php?pidcherga_id=1"><b>1.1</b></a></td>
                  <td style="width:3pt">&nbsp;</td>
                  $row11Slots
                </tr>
                <tr>
                  <td><a href="/unhooking-search.php?pidcherga_id=2"><b>1.2</b></a></td>
                  <td style="width:3pt">&nbsp;</td>
                  $row12Slots
                </tr>
              </table>
            </td></tr></table>
            </body>
            </html>
        """.trimIndent()
    }

    @Test
    fun `parses windows-1251 ZTOE schedule table accurately for rowspan and non-rowspan rows`() {
        val cp1251Bytes = buildSampleHtml().toByteArray(Charset.forName("windows-1251"))
        val decoded = ZtoeHtmlParser.decodeBytes(cp1251Bytes)
        val parsed = ZtoeHtmlParser.parse(decoded, fetchedAtEpochMillis = 123456L)

        assertEquals("17:30 21.11.2026", parsed.siteUpdatedText)
        assertTrue(parsed.importantNotice.contains("1,5 черги"))
        assertEquals(1, parsed.days.size)

        val day = parsed.days.first()
        assertEquals("2026-11-21", day.dateIso)
        assertEquals("21.11.2026", day.displayDate)
        assertEquals(2, day.queues.size)

        val q11 = day.queues[0]
        assertEquals("1.1", q11.queueId)
        assertEquals("1", q11.pidchergaId)
        assertEquals(48, q11.slots.size)
        assertTrue(q11.slots[20])
        assertTrue(q11.slots[23])
        assertFalse(q11.slots[24])
        assertTrue(q11.slots[45])
        assertTrue(q11.slots[47])

        val q12 = day.queues[1]
        assertEquals("1.2", q12.queueId)
        assertEquals("2", q12.pidchergaId)
        assertEquals(5, q12.slots.count { it })
    }

    @Test
    fun `calculates countdown and next outage duration accurately`() {
        val parsed = ZtoeHtmlParser.parse(buildSampleHtml(), fetchedAtEpochMillis = 123456L)
        val now = LocalDateTime.of(2026, 11, 21, 18, 0, 0)

        val analysis = ScheduleCalculator.analyze(
            page = parsed,
            queueId = "1.1",
            leadTimeMinutes = 30,
            now = now,
        )

        assertFalse(analysis.isCurrentlyOutage)
        assertNotNull(analysis.nextOutage)
        assertEquals(LocalDateTime.of(2026, 11, 21, 22, 30), analysis.nextOutage?.start)
        assertEquals(LocalDateTime.of(2026, 11, 22, 0, 0), analysis.nextOutage?.end)
        assertEquals(Duration.ofMinutes(270), analysis.timeUntilNextOutage)
        assertEquals(Duration.ofMinutes(90), analysis.nextOutageDuration)
        assertEquals(LocalDateTime.of(2026, 11, 21, 22, 0), analysis.plannedAlertTime)

        val dayIntervals = analysis.dayOutagesByDate["2026-11-21"].orEmpty()
        assertEquals(2, dayIntervals.size)
        assertEquals("10:00 – 12:00", ScheduleCalculator.formatIntervalRange(dayIntervals[0]))
        assertEquals("22:30 – 24:00", ScheduleCalculator.formatIntervalRange(dayIntervals[1]))
    }
}
