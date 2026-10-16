package org.classflow.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar
import java.util.Locale

/**
 * Native Android DatePickerDialog wrapper.
 * Formats output as "yyyy-MM-dd".
 */
fun showDatePicker(
    context: Context,
    initialDate: String,
    onDateSelected: (String) -> Unit
) {
    val cal = Calendar.getInstance()
    try {
        val parts = initialDate.trim().split("-")
        if (parts.size == 3) {
            cal.set(Calendar.YEAR, parts[0].toInt())
            cal.set(Calendar.MONTH, parts[1].toInt() - 1)
            cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
        }
    } catch (_: Exception) {}

    val dialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            onDateSelected(formatted)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )
    dialog.show()
}

/**
 * Native Android TimePickerDialog wrapper.
 * Formats output as "HH:mm".
 */
fun showTimePicker(
    context: Context,
    initialTime: String,
    onTimeSelected: (String) -> Unit
) {
    val cal = Calendar.getInstance()
    try {
        val parts = initialTime.trim().split(":")
        if (parts.size >= 2) {
            cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            cal.set(Calendar.MINUTE, parts[1].toInt())
        }
    } catch (_: Exception) {}

    val dialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            onTimeSelected(formatted)
        },
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        true
    )
    dialog.show()
}

/**
 * Native Android chained Date & Time Picker wrapper.
 * First prompts date, then immediately prompts time.
 * Formats output as "yyyy-MM-dd HH:mm".
 */
fun showDateTimePicker(
    context: Context,
    initialDateTime: String,
    onDateTimeSelected: (String) -> Unit
) {
    val cal = Calendar.getInstance()
    try {
        val parts = initialDateTime.trim().split(" ")
        if (parts.isNotEmpty()) {
            val dateParts = parts[0].split("-")
            if (dateParts.size == 3) {
                cal.set(Calendar.YEAR, dateParts[0].toInt())
                cal.set(Calendar.MONTH, dateParts[1].toInt() - 1)
                cal.set(Calendar.DAY_OF_MONTH, dateParts[2].toInt())
            }
            if (parts.size > 1) {
                val timeParts = parts[1].split(":")
                if (timeParts.size >= 2) {
                    cal.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                    cal.set(Calendar.MINUTE, timeParts[1].toInt())
                }
            }
        }
    } catch (_: Exception) {}

    val dateDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val timeDialog = TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val formatted = String.format(
                        Locale.getDefault(),
                        "%04d-%02d-%02d %02d:%02d",
                        year,
                        month + 1,
                        dayOfMonth,
                        hourOfDay,
                        minute
                    )
                    onDateTimeSelected(formatted)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            )
            timeDialog.show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )
    dateDialog.show()
}
