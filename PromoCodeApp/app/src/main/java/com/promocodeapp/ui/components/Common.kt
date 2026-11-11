package com.promocodeapp.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.promocodeapp.core.model.BarcodeFormat
import com.promocodeapp.core.model.Expiry
import com.promocodeapp.core.model.Place
import com.promocodeapp.core.util.formatMedium
import com.promocodeapp.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun expiryColor(expiry: Expiry): Color {
    val status = LocalStatusColors.current
    return when (expiry) {
        Expiry.Never -> MaterialTheme.colorScheme.onSurfaceVariant
        is Expiry.Expired, Expiry.Today -> status.urgent
        is Expiry.InDays -> if (expiry.days <= 7) status.warning else status.ok
    }
}

fun expiryText(expiry: Expiry): String = when (expiry) {
    Expiry.Never -> "No expiry"
    is Expiry.Expired -> if (expiry.daysAgo == 1L) "Expired yesterday" else "Expired ${expiry.daysAgo} days ago"
    Expiry.Today -> "Expires today"
    is Expiry.InDays -> if (expiry.days == 1L) "Expires tomorrow" else "Expires in ${expiry.days} days"
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Read-only field that opens a menu of [options]. */
@Composable
fun <T> DropdownField(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field swallows taps, so an overlay opens the menu instead.
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(4.dp)).clickable(onClickLabel = "Choose $label") { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Not set",
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = date?.formatMedium() ?: placeholder,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Default.Event, null) },
            trailingIcon = {
                if (date != null) {
                    IconButton(onClick = { onDateChange(null) }) { Icon(Icons.Default.Clear, "Clear $label") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.matchParentSize().padding(end = if (date != null) 48.dp else 0.dp)
                .clip(RoundedCornerShape(4.dp)).clickable(onClickLabel = "Pick $label") { open = true }
        )
    }
    if (open) {
        // The picker works in UTC midnights; convert without shifting the day.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** Multi-select list of saved places with a shortcut to create a new one. */
@Composable
fun PlacePickerDialog(
    places: List<Place>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where can you use it?") },
        text = {
            if (places.isEmpty()) {
                Text("You haven't saved any places yet. Add a store location to get an alert when you're nearby.")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(places, key = { it.id }) { place ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(place.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = place.id in selectedIds, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(place.name, style = MaterialTheme.typography.bodyLarge)
                                place.address?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onCreateNew) { Text("New place") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- barcodes ---

data class ScannedCode(val value: String, val format: BarcodeFormat?)

/**
 * Opens Google's code scanner. It runs inside Play services, so the app needs
 * no camera permission. [onResult] receives null if nothing was scanned.
 */
fun scanBarcode(context: Context, onResult: (ScannedCode?) -> Unit, onError: (String) -> Unit) {
    runCatching {
        GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { code ->
                val value = code.rawValue
                onResult(if (value.isNullOrBlank()) null else ScannedCode(value, code.format.toModel()))
            }
            .addOnCanceledListener { onResult(null) }
            .addOnFailureListener { onError("The scanner isn't available right now. Try again in a moment.") }
    }.onFailure { onError("Scanning needs Google Play services.") }
}

private fun Int.toModel(): BarcodeFormat? = when (this) {
    Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
    Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
    Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
    Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
    Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
    Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
    Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
    Barcode.FORMAT_ITF -> BarcodeFormat.ITF
    Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
    Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
    Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
    Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
    else -> null
}

/** Draws a scannable barcode on a white card so it reads in dark theme too. */
@Composable
fun BarcodeImage(content: String, format: BarcodeFormat, modifier: Modifier = Modifier) {
    val bitmap = remember(content, format) { BarcodeRenderer.render(content, format) }
    if (bitmap == null) return
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Barcode for $content",
            // Nearest-neighbour scaling keeps bar edges crisp for scanners.
            filterQuality = FilterQuality.None,
            contentScale = ContentScale.FillBounds,
            modifier = if (format.isTwoDimensional) Modifier.size(200.dp)
            else Modifier.fillMaxWidth().aspectRatio(if (format == BarcodeFormat.PDF_417) 3f else 2.6f),
        )
    }
}
