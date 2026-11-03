package org.classflow.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** Native RemoteViews-backed list, with one undivided, time-positioned rectangle per occurrence. */
@Composable
internal fun WeekTimelineGrid(
    context: Context,
    layout: WidgetWeekLayout.Layout,
    width: Float,
    height: Float,
    axisColor: Color
) {
    val axisWidth = 36f
    val dayWidth = ((width - axisWidth) / 7f).coerceAtLeast(1f)
    LazyColumn(modifier = GlanceModifier.fillMaxWidth().height(height.coerceAtLeast(1f).dp)) {
        items(layout.bands, itemId = { it.start.toLong() }) { band ->
            val bandHeight = layout.height(band)
            Row(modifier = GlanceModifier.fillMaxWidth().height(bandHeight.dp)) {
                Box(modifier = GlanceModifier.width(axisWidth.dp).fillMaxHeight()) {
                    TimelineTicks(layout.ticks.filter { it.minute >= band.start && it.minute < band.end }, layout, band.start, axisColor)
                }
                for (day in 1..7) {
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                        TimelineCards(context, band.cards.filter { it.occurrence.dayOfWeek == day }, layout, band, dayWidth)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTicks(ticks: List<WidgetWeekLayout.Tick>, layout: WidgetWeekLayout.Layout, start: Int, color: Color) {
    // Box has a ten-child limit in Glance; grouping adds no padding or visual boundaries.
    if (ticks.size > 8) {
        ticks.chunked((ticks.size + 1) / 2).forEach { group ->
            Box(modifier = GlanceModifier.fillMaxSize()) { TimelineTicks(group, layout, start, color) }
        }
    } else ticks.forEach { tick ->
        Box(modifier = GlanceModifier.fillMaxSize().padding(top = (layout.y(tick.minute) - layout.y(start)).dp)) {
            Text(tick.label, style = TextStyle(fontSize = 8.sp, color = ColorProvider(color)), maxLines = 2)
        }
    }
}

@Composable
private fun TimelineCards(
    context: Context,
    cards: List<WidgetWeekLayout.Card>,
    layout: WidgetWeekLayout.Layout,
    band: WidgetWeekLayout.Band,
    dayWidth: Float
) {
    if (cards.size > 8) {
        cards.chunked((cards.size + 1) / 2).forEach { group ->
            Box(modifier = GlanceModifier.fillMaxSize()) { TimelineCards(context, group, layout, band, dayWidth) }
        }
    } else cards.forEach { card ->
        val top = layout.y(card.start) - layout.y(band.start)
        val bottom = layout.y(band.end) - layout.y(card.end)
        val columnWidth = dayWidth / card.laneCount
        val left = columnWidth * card.lane
        val right = dayWidth - left - columnWidth
        val rgb = WidgetCardStyle.rgb(card.occurrence.colorHex)
        val foreground = if (WidgetCardStyle.useDarkText(rgb)) Color.Black else Color.White
        val cardHeight = layout.y(card.end) - layout.y(card.start)
        val narrow = columnWidth < 24f
        val title = if (narrow) {
            // In a narrow overlap lane, prioritise the name over a leading decorative emoji.
            card.occurrence.title.dropWhile { !it.isLetterOrDigit() }.ifBlank { card.occurrence.title }
        } else card.occurrence.title
        val fontSize = if (narrow) 8f else 9f
        Box(modifier = GlanceModifier.fillMaxSize().padding(start = (left + 1.5f).dp, end = (right + 1.5f).dp,
            top = (top + 1f).dp, bottom = (bottom + 1f).dp)) {
            Box(modifier = GlanceModifier.fillMaxSize().background(Color(rgb or -0x1000000)).cornerRadius(5.dp)
                .clickable(actionStartActivity(widgetOccurrenceIntent(context, card.occurrence))), contentAlignment = Alignment.Center) {
                Text(title, modifier = GlanceModifier.padding(horizontal = 1.dp),
                    style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = ColorProvider(foreground)),
                    maxLines = ((cardHeight - 2f) / (fontSize * 1.25f)).toInt().coerceIn(1, 8))
            }
        }
    }
}
