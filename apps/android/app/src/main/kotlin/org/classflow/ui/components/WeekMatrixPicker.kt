package org.classflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 1:1 Parity of macOS WeekMatrixPickerView:
 * - Quick action pills: 全选, 单周, 双周, 前八周, 后八周, 清空
 * - 5-column grid of 1..25 weeks
 * - Selection summary and total count
 */
@Composable
fun WeekMatrixPicker(
    selectedWeeks: Set<Int>,
    onWeeksChange: (Set<Int>) -> Unit,
    maxWeeks: Int = 25,
    accentColor: Color = Color(0xFFFF5A5F),
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isDark) Color(0xFF1E202B) else Color(0xFFF3F4F6)
    val pillBg = if (isDark) Color(0xFF282B3C) else Color(0xFFE5E7EB)
    val unselectedItemBg = if (isDark) Color(0xFF282B3C) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFFA1A3AC) else Color(0xFF6B7280)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Quick Presets Scroll Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetButton("全选", pillBg, textPrimary) {
                onWeeksChange((1..maxWeeks).toSet())
            }
            PresetButton("单周", pillBg, textPrimary) {
                onWeeksChange((1..maxWeeks).filter { it % 2 == 1 }.toSet())
            }
            PresetButton("双周", pillBg, textPrimary) {
                onWeeksChange((1..maxWeeks).filter { it % 2 == 0 }.toSet())
            }
            PresetButton("前八周", pillBg, textPrimary) {
                onWeeksChange((1..minOf(8, maxWeeks)).toSet())
            }
            PresetButton("后八周", pillBg, textPrimary) {
                val s = minOf(9, maxWeeks)
                val e = minOf(16, maxWeeks)
                onWeeksChange(if (s <= e) (s..e).toSet() else emptySet())
            }
            PresetButton("清空", pillBg, textPrimary) {
                onWeeksChange(emptySet())
            }
        }

        // 2. 5-Column Grid of 1..maxWeeks
        val rows = (maxWeeks + 4) / 5
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (c in 1..5) {
                        val week = r * 5 + c
                        if (week <= maxWeeks) {
                            val isSelected = selectedWeeks.contains(week)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) accentColor else unselectedItemBg)
                                    .clickable {
                                        val next = selectedWeeks.toMutableSet()
                                        if (isSelected) next.remove(week) else next.add(week)
                                        onWeeksChange(next)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = week.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) Color.White else textPrimary
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 3. Selection Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatSummaryText(selectedWeeks, maxWeeks),
                fontSize = 11.sp,
                color = textSecondary,
                maxLines = 1
            )
            Text(
                text = "共 ${selectedWeeks.size} 周",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (selectedWeeks.isEmpty()) textSecondary else accentColor
            )
        }
    }
}

@Composable
private fun PresetButton(
    title: String,
    bg: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

private fun formatSummaryText(selectedWeeks: Set<Int>, maxWeeks: Int): String {
    if (selectedWeeks.isEmpty()) return "未选择任何周次"
    val sorted = selectedWeeks.sorted()
    if (sorted.size == maxWeeks) return "全学期每周上课 (1-${maxWeeks}周)"

    val ranges = mutableListOf<String>()
    var start = sorted.first()
    var prev = sorted.first()

    for (w in sorted.drop(1)) {
        if (w == prev + 1) {
            prev = w
        } else {
            ranges.add(if (start == prev) "${start}周" else "$start-${prev}周")
            start = w
            prev = w
        }
    }
    ranges.add(if (start == prev) "${start}周" else "$start-${prev}周")
    return "已选: ${ranges.joinToString(", ")}"
}
