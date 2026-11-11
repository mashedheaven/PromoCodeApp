package com.promocodeapp.ui.coupons

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.promocodeapp.core.model.Coupon
import com.promocodeapp.core.model.Money
import com.promocodeapp.core.model.Place
import com.promocodeapp.core.util.formatMedium
import com.promocodeapp.core.util.today
import com.promocodeapp.data.repository.CouponRepository
import com.promocodeapp.data.repository.PlaceRepository
import com.promocodeapp.ui.components.BarcodeImage
import com.promocodeapp.ui.components.ConfirmDialog
import com.promocodeapp.ui.components.expiryColor
import com.promocodeapp.ui.components.expiryText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CouponDetailState(
    val loading: Boolean = true,
    val coupon: Coupon? = null,
    val places: List<Place> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

@HiltViewModel
class CouponDetailViewModel @Inject constructor(
    private val coupons: CouponRepository,
    places: PlaceRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])

    val state: StateFlow<CouponDetailState> = combine(coupons.observe(id), places.observeAll()) { coupon, all ->
        CouponDetailState(
            loading = false,
            coupon = coupon,
            places = all.filter { it.id in coupon?.placeIds.orEmpty() },
            today = clock.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CouponDetailState())

    fun toggleFavorite() = update { coupons.setFavorite(it.id, !it.isFavorite) }
    fun toggleUsed() = update { coupons.setUsed(it.id, !it.isUsed) }
    fun toggleArchived() = update { coupons.setArchived(it.id, !it.isArchived) }
    fun delete() = update { coupons.delete(it.id) }

    private fun update(block: suspend (Coupon) -> Unit) {
        val coupon = state.value.coupon ?: return
        viewModelScope.launch { block(coupon) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: CouponDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val coupon = state.coupon

    // Deleted here or on another device: nothing left to show.
    LaunchedEffect(state.loading, coupon) { if (!state.loading && coupon == null) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(coupon?.merchant.orEmpty(), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (coupon != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (coupon.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (coupon.isFavorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                        IconButton(onClick = { share(context, coupon) }) { Icon(Icons.Default.Share, "Share") }
                        IconButton(onClick = { onEdit(coupon.id) }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        if (coupon == null) return@Scaffold
        val expiry = coupon.expiry(state.today)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(coupon.discount.label(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            if (coupon.code.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(coupon.code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                        }
                        FilledTonalIconButton(onClick = { copy(context, coupon.code) }) { Icon(Icons.Default.ContentCopy, "Copy code") }
                    }
                }
            }

            val barcode = coupon.barcode
            val format = coupon.barcodeFormat
            if (barcode != null && format != null) BarcodeImage(barcode, format)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Fact(
                    "Expires",
                    coupon.expiresOn?.let { "${it.formatMedium()} · ${expiryText(expiry)}" } ?: "No expiry date",
                    valueColor = expiryColor(expiry),
                )
                coupon.minPurchase?.let { min ->
                    val currency = (coupon.discount as? com.promocodeapp.core.model.Discount.FixedAmount)?.currency ?: Money.defaultCurrencyCode()
                    Fact("Minimum purchase", Money.format(min, currency))
                }
                coupon.category?.let { Fact("Category", it) }
                coupon.notes?.let { Fact("Notes", it) }
                if (coupon.isUsed) Fact("Status", "Used")
                if (coupon.isArchived) Fact("Status", "Archived")
            }

            if (state.places.isNotEmpty()) {
                Column {
                    Text("Use it at", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.places.forEach { place ->
                        AssistChip(
                            onClick = { openInMaps(context, place) },
                            label = { Text(place.name) },
                            leadingIcon = { Icon(Icons.Default.Place, null) },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::toggleUsed, modifier = Modifier.weight(1f)) {
                    Text(if (coupon.isUsed) "Mark as unused" else "Mark as used")
                }
                OutlinedButton(onClick = viewModel::toggleArchived, modifier = Modifier.weight(1f)) {
                    Text(if (coupon.isArchived) "Unarchive" else "Archive")
                }
            }
        }
    }

    if (confirmDelete && coupon != null) {
        ConfirmDialog(
            title = "Delete coupon?",
            message = "The ${coupon.merchant} coupon will be removed from all your devices.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Fact(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}

private fun copy(context: Context, code: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Promo code", code))
    // Android 13+ shows its own confirmation.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, coupon: Coupon) {
    val text = buildString {
        append("${coupon.merchant}: ${coupon.discount.label()}")
        if (coupon.code.isNotBlank()) append(" with code ${coupon.code}")
        coupon.expiresOn?.let { append(" (expires ${it.formatMedium()})") }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share coupon"))
}

private fun openInMaps(context: Context, place: Place) {
    val uri = "geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${android.net.Uri.encode(place.name)})".toUri()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
