package org.classflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.classflow.data.model.AppSettings

data class SkinPreset(
    val id: String,
    val name: String,
    val lightColors: List<Color>,
    val darkColors: List<Color>
)

val skinPresets = listOf(
    SkinPreset(
        id = "default",
        name = "默认设置",
        lightColors = listOf(Color(0xFFB7D2EC), Color(0xFFCFE1F6), Color(0xFFB9CCE6)),
        darkColors = listOf(Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF0A0D12))
    ),
    SkinPreset(
        id = "aurora",
        name = "极光",
        lightColors = listOf(Color(0xFF1A2238), Color(0xFF283C63), Color(0xFF1B998B)),
        darkColors = listOf(Color(0xFF0A111C), Color(0xFF14223A), Color(0xFF0D332F))
    ),
    SkinPreset(
        id = "midnight",
        name = "星空",
        lightColors = listOf(Color(0xFF151821), Color(0xFF1E2433), Color(0xFF11141B)),
        darkColors = listOf(Color(0xFF08090C), Color(0xFF0F1117), Color(0xFF060709))
    ),
    SkinPreset(
        id = "warm_cream",
        name = "暖阳",
        lightColors = listOf(Color(0xFFEFE0CE), Color(0xFFF9F2E7), Color(0xFFE8D5BF)),
        darkColors = listOf(Color(0xFF161311), Color(0xFF221D18), Color(0xFF181512))
    ),
    SkinPreset(
        id = "mint",
        name = "薄荷",
        lightColors = listOf(Color(0xFFC6E8DB), Color(0xFFDDF4EC), Color(0xFFC3E3D6)),
        darkColors = listOf(Color(0xFF0A1412), Color(0xFF112620), Color(0xFF0E1D19))
    ),
    SkinPreset(
        id = "sky",
        name = "天空",
        lightColors = listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD), Color(0xFF7DD3FC)),
        darkColors = listOf(Color(0xFF09131E), Color(0xFF0F1D2E), Color(0xFF0B1622))
    )
)

/**
 * Direct Kotlin/Compose port of macOS TimetableAppearanceSheet.swift:
 * - 皮肤设置 (Horizontal gradient cards with selection border & checkmark)
 * - 整体界面 (Pure color background, grid guidelines, time axis, weekends, non-current week)
 * - 课程格子 (Text color mode, border mode, grid height, radius, opacity, font size, at prefix, centering)
 */
@Composable
fun TimetableAppearanceDialog(
    settings: AppSettings,
    isDark: Boolean,
    onUpdateSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val dialogBg = if (isDark) Color(0xFF161822) else Color(0xFFF9FAFC)
    val cardBg = if (isDark) Color(0xFF202332) else Color.White
    val textPrimary = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1F2937)
    val textSecondary = if (isDark) Color(0xFFA1A3AC) else Color(0xFF6B7280)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F2)

    val context = androidx.compose.ui.platform.LocalContext.current
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val savedPath = org.classflow.data.local.SettingsManager.saveCustomWallpaperFromUri(context, uri)
            onUpdateSettings(
                settings.copy(
                    customWallpaperUri = savedPath ?: uri.toString(),
                    wallpaperPreset = "custom",
                    usePureColorBackground = false
                )
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = dialogBg,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (macOS title bar style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF1C1E2A) else Color.White)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = textPrimary)
                    }

                    Text("课表外观", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("完成", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                HorizontalDivider(color = dividerColor)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Skin Presets (皮肤设置)
                    Text("皮肤设置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        skinPresets.forEach { preset ->
                            val isSelected = (settings.wallpaperPreset == preset.id && settings.customWallpaperUri.isNullOrBlank() && !settings.usePureColorBackground)
                            val colors = if (isDark) preset.darkColors else preset.lightColors

                            Column(
                                modifier = Modifier
                                    .width(110.dp)
                                    .clickable {
                                        onUpdateSettings(
                                            settings.copy(
                                                wallpaperPreset = preset.id,
                                                customWallpaperUri = null,
                                                usePureColorBackground = false
                                            )
                                        )
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 110.dp, height = 150.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = colors,
                                                start = Offset(0f, 0f),
                                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                            )
                                        )
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.5.dp, Color(0xFFFF5A5F), RoundedCornerShape(14.dp))
                                            } else Modifier
                                        )
                                        .padding(8.dp),
                                    contentAlignment = Alignment.BottomStart
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF5A5F)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = preset.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFFFF5A5F) else textPrimary
                                )
                            }
                        }
                    }

                    // 2. Interface Section (整体界面)
                    Text("整体界面", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppearanceSwitchRow(
                                title = "使用纯色背景",
                                checked = settings.usePureColorBackground,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(usePureColorBackground = it)) }
                            )
                            HorizontalDivider(color = dividerColor)

                            // Custom wallpaper picker row matching macOS
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("背景图片", fontSize = 13.5.sp, color = textPrimary)
                                    Text(
                                        if (!settings.customWallpaperUri.isNullOrBlank()) "已选择自定义背景壁纸" else "当前使用主题预设背景",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (!settings.customWallpaperUri.isNullOrBlank()) {
                                        OutlinedButton(
                                            onClick = { onUpdateSettings(settings.copy(customWallpaperUri = null, wallpaperPreset = "default")) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("恢复默认", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Button(
                                        onClick = { photoPickerLauncher.launch("image/*") },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            if (!settings.customWallpaperUri.isNullOrBlank()) "更换图片..." else "选择壁纸...",
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = dividerColor)
                            AppearanceSwitchRow(
                                title = "显示网格辅助线",
                                checked = settings.showGridGuideLines,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showGridGuideLines = it)) }
                            )
                            HorizontalDivider(color = dividerColor)
                            AppearanceSwitchRow(
                                title = "节数栏显示时间",
                                checked = settings.showSectionTimeInAxis,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showSectionTimeInAxis = it)) }
                            )
                            HorizontalDivider(color = dividerColor)
                            AppearanceSwitchRow(
                                title = "显示周六",
                                checked = settings.showSaturday,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showSaturday = it)) }
                            )
                            HorizontalDivider(color = dividerColor)
                            AppearanceSwitchRow(
                                title = "显示周日",
                                checked = settings.showSunday,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showSunday = it)) }
                            )
                            HorizontalDivider(color = dividerColor)
                            AppearanceSwitchRow(
                                title = "显示非本周课程",
                                checked = settings.showNonCurrentWeekCourses,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showNonCurrentWeekCourses = it)) }
                            )
                        }
                    }

                    // 3. Card Section (课程格子)
                    Text("课程格子", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Text Color Mode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("文字颜色", fontSize = 13.5.sp, color = textPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("auto" to "自动", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                                        val isSel = (settings.cardTextColorMode == mode)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) Color(0xFFFF5A5F) else if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6))
                                                .clickable { onUpdateSettings(settings.copy(cardTextColorMode = mode)) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(label, fontSize = 11.5.sp, color = if (isSel) Color.White else textSecondary)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = dividerColor)

                            // Border Color Mode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("边框颜色", fontSize = 13.5.sp, color = textPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("none" to "默认", "cardColor" to "同色", "subtle" to "细线").forEach { (mode, label) ->
                                        val isSel = (settings.cardBorderColorMode == mode)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) Color(0xFFFF5A5F) else if (isDark) Color(0xFF282B3C) else Color(0xFFF3F4F6))
                                                .clickable { onUpdateSettings(settings.copy(cardBorderColorMode = mode)) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(label, fontSize = 11.5.sp, color = if (isSel) Color.White else textSecondary)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = dividerColor)

                            // Grid Height
                            AppearanceSliderRow(
                                title = "格子高度",
                                value = settings.gridHeight,
                                valueDisplay = "${settings.gridHeight.toInt()} dp",
                                valueRange = 48f..96f,
                                textPrimary = textPrimary,
                                onValueChange = { onUpdateSettings(settings.copy(gridHeight = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            // Card Radius
                            AppearanceSliderRow(
                                title = "圆角半径",
                                value = settings.cardCornerRadius,
                                valueDisplay = "${settings.cardCornerRadius.toInt()} dp",
                                valueRange = 0f..16f,
                                textPrimary = textPrimary,
                                onValueChange = { onUpdateSettings(settings.copy(cardCornerRadius = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            // Card Opacity
                            AppearanceSliderRow(
                                title = "不透明度",
                                value = settings.cardOpacity,
                                valueDisplay = "${(settings.cardOpacity * 100).toInt()}%",
                                valueRange = 0.20f..1.0f,
                                textPrimary = textPrimary,
                                onValueChange = { onUpdateSettings(settings.copy(cardOpacity = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            // Non-current Week Opacity Ratio
                            AppearanceSliderRow(
                                title = "非本周课程不透明比",
                                value = settings.nonCurrentWeekOpacityRatio,
                                valueDisplay = "${(settings.nonCurrentWeekOpacityRatio * 100).toInt()}%",
                                valueRange = 0.10f..1.0f,
                                textPrimary = textPrimary,
                                onValueChange = { onUpdateSettings(settings.copy(nonCurrentWeekOpacityRatio = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            // Card Font Size
                            AppearanceSliderRow(
                                title = "字体大小",
                                value = settings.cardFontSize,
                                valueDisplay = "${settings.cardFontSize.toInt()} sp",
                                valueRange = 10f..18f,
                                textPrimary = textPrimary,
                                onValueChange = { onUpdateSettings(settings.copy(cardFontSize = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "格子内显示上课时间",
                                checked = settings.showTimeInCard,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showTimeInCard = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "格子内显示上课地点",
                                checked = settings.showLocationInCard,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showLocationInCard = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "教室前显示 @",
                                checked = settings.showAtPrefixForRoom,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showAtPrefixForRoom = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "格子前显示授课老师",
                                checked = settings.showTeacherInCard,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showTeacherInCard = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "格子内显示备注",
                                checked = settings.showNotesInCard,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(showNotesInCard = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "格子文字水平居中",
                                checked = settings.cardTextHorizontalCenter,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(cardTextHorizontalCenter = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "格子文字垂直居中",
                                checked = settings.cardTextVerticalCenter,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(cardTextVerticalCenter = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "格子使用虚线边框",
                                checked = settings.cardUseDottedBorder,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(cardUseDottedBorder = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "边框使用格子颜色",
                                checked = settings.cardBorderUseCardColor,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(cardBorderUseCardColor = it)) }
                            )

                            HorizontalDivider(color = dividerColor)

                            AppearanceSwitchRow(
                                title = "课程文字颜色叠加格子颜色",
                                checked = settings.cardTextBlendCardColor,
                                textPrimary = textPrimary,
                                onCheckedChange = { onUpdateSettings(settings.copy(cardTextBlendCardColor = it)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSwitchRow(
    title: String,
    checked: Boolean,
    textPrimary: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.5.sp, color = textPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFF5A5F)
            )
        )
    }
}

@Composable
private fun AppearanceSliderRow(
    title: String,
    value: Float,
    valueDisplay: String,
    valueRange: ClosedFloatingPointRange<Float>,
    textPrimary: Color,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 13.5.sp, color = textPrimary)
            Text(valueDisplay, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5A5F))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF5A5F),
                activeTrackColor = Color(0xFFFF5A5F)
            )
        )
    }
}
