package org.classflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Autocomplete search suggestion text field matching WakeUp 课程表 and macOS SearchSuggestionTextField:
 * - Direct freeform typing is fully unrestricted.
 * - Focusing on the field or clicking the dropdown arrow opens a clean suggestion dropdown of existing records.
 * - Typing narrows down matching candidates in real time.
 * - Selecting a suggestion autofills the field immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suggestions: List<String>,
    label: String = "",
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    colors: TextFieldColors? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(value, suggestions) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            suggestions
        } else {
            suggestions.filter { it.contains(trimmed, ignoreCase = true) && !it.equals(trimmed, ignoreCase = true) }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredSuggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = if (label.isNotEmpty()) { { Text(label) } } else null,
            placeholder = { Text(placeholder) },
            colors = colors ?: OutlinedTextFieldDefaults.colors(),
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            },
            trailingIcon = {
                Row {
                    if (value.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onValueChange("")
                                expanded = suggestions.isNotEmpty()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "清空", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (suggestions.isNotEmpty()) {
                        IconButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "已有选项")
                        }
                    }
                }
            },
            singleLine = singleLine,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused && filteredSuggestions.isNotEmpty()) {
                        expanded = true
                    }
                }
        )

        ExposedDropdownMenu(
            expanded = expanded && filteredSuggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 240.dp)
        ) {
            filteredSuggestions.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp)
                        )
                    },
                    onClick = {
                        onValueChange(item)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
