package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.OutageRed
import com.example.ui.theme.PowerAmber
import com.example.ui.theme.PowerOnGreen
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun DayTimelineBar(
    slots: List<Boolean>,
    dayDate: LocalDate,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val safeSlots = if (slots.size >= 48) slots.take(48) else slots + List(48 - slots.size) { false }
    val isToday = dayDate == now.toLocalDate()
    val currentMinutesFraction = if (isToday) {
        ((now.hour * 60 + now.minute) / 1440f).coerceIn(0f, 1f)
    } else {
        null
    }

    val powerOnColor = PowerOnGreen.copy(alpha = 0.85f)
    val outageColor = OutageRed
    val dividerColor = MaterialTheme.colorScheme.surface
    val markerColor = PowerAmber
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Шкала отключений на 24 часа"
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            val totalSlots = 48
            val slotWidth = size.width / totalSlots
            val barTop = 6.dp.toPx()
            val barHeight = size.height - barTop

            for (i in 0 until totalSlots) {
                val x = i * slotWidth
                val gap = if (i % 2 == 1) 1.5.dp.toPx() else 0.6.dp.toPx()
                val cellColor = if (safeSlots[i]) outageColor else powerOnColor
                drawRoundRect(
                    color = cellColor,
                    topLeft = Offset(x, barTop),
                    size = Size((slotWidth - gap).coerceAtLeast(1f), barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }

            if (currentMinutesFraction != null) {
                val markerX = (size.width * currentMinutesFraction).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
                drawLine(
                    color = dividerColor,
                    start = Offset(markerX, 0f),
                    end = Offset(markerX, size.height),
                    strokeWidth = 4.dp.toPx(),
                )
                drawLine(
                    color = markerColor,
                    start = Offset(markerX, 2.dp.toPx()),
                    end = Offset(markerX, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
                drawCircle(
                    color = markerColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(markerX, 3.5.dp.toPx()),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LegendDot(color = powerOnColor, label = "Свет есть")
            LegendDot(color = outageColor, label = "Нет света")
            if (isToday) {
                LegendDot(color = PowerAmber, label = "Сейчас")
            }
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
