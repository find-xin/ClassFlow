package org.classflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Google Calendar style circular color picker row matching macOS CompactColorPickerRow:
 * - 8 classic Google Calendar preset color circles.
 * - Current custom color circle with active ring when selected.
 * - Circular (+) button opening a custom color dialog with hex code input (#RRGGBB),
 *   preview circle, and hue slider.
 */
@Composable
fun GoogleCalendarColorPicker(
    selectedHex: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    // Google Calendar Classic Swatches
    val presets = remember {
        listOf(
            "#FF5A5F", // Tomato / Coral
            "#F4511E", // Tangerine
            "#E4C441", // Banana
            "#0B8043", // Basil
            "#039BE5", // Peacock
            "#3F51B5", // Blueberry
            "#7986CB", // Lavender
            "#8E24AA"  // Grape
        )
    }

    val cleanSelected = selectedHex.replace("#", "").uppercase()
    val isCustom = presets.none { it.replace("#", "").uppercase() == cleanSelected }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Preset Swatches
        presets.forEach { presetHex ->
            val color = parseHexColor(presetHex)
            val isSelected = (cleanSelected == presetHex.replace("#", "").uppercase())

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.5.dp, Color.White, CircleShape)
                        } else Modifier
                    )
                    .clickable { onColorSelected(presetHex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. Active Custom Color Swatch
        if (isCustom) {
            val customColor = parseHexColor(selectedHex)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(customColor)
                    .border(2.5.dp, Color.White, CircleShape)
                    .clickable { /* Already selected */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 3. (+) Add Custom Color Button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { showCustomDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "自定义颜色",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showCustomDialog) {
        CustomColorDialog(
            initialHex = selectedHex,
            onDismiss = { showCustomDialog = false },
            onConfirm = { hex ->
                onColorSelected(hex)
                showCustomDialog = false
            }
        )
    }
}

@Composable
fun CustomColorDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var hexInput by remember { mutableStateOf(initialHex.replace("#", "").uppercase()) }
    var hue by remember { mutableFloatStateOf(0.55f) }
    var saturation by remember { mutableFloatStateOf(0.70f) }
    var value by remember { mutableFloatStateOf(0.85f) }

    // Live preview color
    val parsedColor = remember(hexInput) {
        try {
            val clean = hexInput.trim().replace("#", "")
            if (clean.length == 6) {
                Color(android.graphics.Color.parseColor("#$clean"))
            } else {
                Color.hsv(hue * 360f, saturation, value)
            }
        } catch (e: Throwable) {
            Color.hsv(hue * 360f, saturation, value)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("自定义颜色", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live preview circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(parsedColor)
                        .border(3.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                )

                // Direct Hex Input (色号自定义)
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        val filtered = input.replace("#", "").filter {
                            it.isDigit() || it in 'A'..'F' || it in 'a'..'f'
                        }.take(6).uppercase()
                        hexInput = filtered
                    },
                    label = { Text("颜色色号 (HEX)") },
                    prefix = { Text("#", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Hue Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "色相微调",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = hue,
                        onValueChange = {
                            hue = it
                            val hsvColor = android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, saturation, value))
                            hexInput = String.format("%06X", 0xFFFFFF and hsvColor)
                        },
                        valueRange = 0f..1f
                    )
                }

                // Saturation Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "饱和度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = saturation,
                        onValueChange = {
                            saturation = it
                            val hsvColor = android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, saturation, value))
                            hexInput = String.format("%06X", 0xFFFFFF and hsvColor)
                        },
                        valueRange = 0.1f..1f
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalHex = if (hexInput.length == 6) "#$hexInput" else "#4F46E5"
                    onConfirm(finalHex)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("使用此颜色")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF4F46E5)): Color {
    return try {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(clean))
    } catch (e: Throwable) {
        fallback
    }
}
