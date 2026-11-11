package com.promocodeapp.ui.coupons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.promocodeapp.core.model.BarcodeFormat
import com.promocodeapp.core.model.Coupon
import com.promocodeapp.core.model.Discount
import com.promocodeapp.core.model.Money
import com.promocodeapp.core.model.Place
import com.promocodeapp.core.util.newId
import com.promocodeapp.data.repository.CouponRepository
import com.promocodeapp.data.repository.PlaceRepository
import com.promocodeapp.ui.components.DateField
import com.promocodeapp.ui.components.DropdownField
import com.promocodeapp.ui.components.PlacePickerDialog
import com.promocodeapp.ui.components.ScannedCode
import com.promocodeapp.ui.components.scanBarcode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class DiscountKind(val label: String) {
    PERCENT("Percent off"),
    AMOUNT("Amount off"),
    BOGO("Buy one, get one"),
    FREE_SHIPPING("Free shipping"),
}

val CouponCategories = listOf("Groceries", "Restaurants", "Fashion", "Electronics", "Home", "Beauty", "Travel", "Entertainment", "Other")

data class CouponForm(
    val merchant: String = "",
    val code: String = "",
    val kind: DiscountKind = DiscountKind.PERCENT,
    val value: String = "",
    val minPurchase: String = "",
    val category: String? = null,
    val notes: String = "",
    val expiresOn: LocalDate? = null,
    val barcode: String? = null,
    val barcodeFormat: BarcodeFormat? = null,
    val placeIds: Set<String> = emptySet(),
    val merchantError: String? = null,
    val valueError: String? = null,
    val minPurchaseError: String? = null,
) {
    val needsValue: Boolean get() = kind == DiscountKind.PERCENT || kind == DiscountKind.AMOUNT
}

@HiltViewModel
class CouponEditViewModel @Inject constructor(
    private val coupons: CouponRepository,
    places: PlaceRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val editingId: String? = savedState["id"]
    private var original: Coupon? = null

    val isNew: Boolean get() = editingId == null
    private val _form = MutableStateFlow(CouponForm())
    val form: StateFlow<CouponForm> = _form.asStateFlow()
    val places: StateFlow<List<Place>> = places.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (editingId != null) viewModelScope.launch {
            val c = coupons.get(editingId) ?: return@launch
            original = c
            _form.value = CouponForm(
                merchant = c.merchant,
                code = c.code,
                kind = when (c.discount) {
                    is Discount.Percentage -> DiscountKind.PERCENT
                    is Discount.FixedAmount -> DiscountKind.AMOUNT
                    Discount.BuyOneGetOne -> DiscountKind.BOGO
                    Discount.FreeShipping -> DiscountKind.FREE_SHIPPING
                },
                value = when (val d = c.discount) {
                    is Discount.Percentage -> number(d.percent)
                    is Discount.FixedAmount -> number(d.amount)
                    else -> ""
                },
                minPurchase = c.minPurchase?.let(::number).orEmpty(),
                category = c.category,
                notes = c.notes.orEmpty(),
                expiresOn = c.expiresOn,
                barcode = c.barcode,
                barcodeFormat = c.barcodeFormat,
                placeIds = c.placeIds.toSet(),
            )
        }
    }

    fun edit(transform: (CouponForm) -> CouponForm) = _form.update(transform)

    fun togglePlace(id: String) = _form.update {
        it.copy(placeIds = if (id in it.placeIds) it.placeIds - id else it.placeIds + id)
    }

    fun addPlace(id: String) = _form.update { it.copy(placeIds = it.placeIds + id) }

    fun onScanned(scan: ScannedCode) = _form.update {
        it.copy(code = scan.value, barcode = scan.value, barcodeFormat = scan.format)
    }

    /** @return true if the coupon was saved. */
    fun save(onSaved: () -> Unit) {
        val f = _form.value
        val value = Money.parse(f.value)
        val minPurchase = Money.parse(f.minPurchase)
        val checked = f.copy(
            merchantError = if (f.merchant.isBlank()) "Enter the store name" else null,
            valueError = when {
                !f.needsValue -> null
                value == null || value <= 0 -> "Enter the discount"
                f.kind == DiscountKind.PERCENT && value > 100 -> "Can't be more than 100%"
                else -> null
            },
            minPurchaseError = if (f.minPurchase.isNotBlank() && minPurchase == null) "Enter an amount" else null,
        )
        _form.value = checked
        if (checked.merchantError != null || checked.valueError != null || checked.minPurchaseError != null) return

        val base = original
        val now = clock.millis()
        val currency = (base?.discount as? Discount.FixedAmount)?.currency ?: Money.defaultCurrencyCode()
        val code = f.code.trim()
        val coupon = Coupon(
            id = base?.id ?: newId(),
            code = code,
            merchant = f.merchant.trim(),
            discount = when (f.kind) {
                DiscountKind.PERCENT -> Discount.Percentage(value!!)
                DiscountKind.AMOUNT -> Discount.FixedAmount(value!!, currency)
                DiscountKind.BOGO -> Discount.BuyOneGetOne
                DiscountKind.FREE_SHIPPING -> Discount.FreeShipping
            },
            minPurchase = minPurchase,
            category = f.category,
            notes = f.notes.trim().ifEmpty { null },
            expiresOn = f.expiresOn,
            // A scanned barcode stops being valid once the code is retyped.
            barcode = f.barcode?.takeIf { it == code },
            barcodeFormat = f.barcodeFormat?.takeIf { f.barcode == code },
            isFavorite = base?.isFavorite ?: false,
            usedAt = base?.usedAt,
            isArchived = base?.isArchived ?: false,
            placeIds = f.placeIds.toList(),
            createdAt = base?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            coupons.save(coupon)
            onSaved()
        }
    }

    private fun number(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CouponEditScreen(
    onDone: () -> Unit,
    onCreatePlace: (suggestedName: String) -> Unit,
    /** Id of a place created via [onCreatePlace], delivered once. */
    newPlaceId: String?,
    onNewPlaceConsumed: () -> Unit,
    viewModel: CouponEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pickingPlaces by remember { mutableStateOf(false) }

    LaunchedEffect(newPlaceId) {
        if (newPlaceId != null) {
            viewModel.addPlace(newPlaceId)
            onNewPlaceConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New coupon" else "Edit coupon") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, "Discard") } },
                actions = { TextButton(onClick = { viewModel.save(onDone) }) { Text("Save") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.merchant,
                onValueChange = { v -> viewModel.edit { it.copy(merchant = v, merchantError = null) } },
                label = { Text("Store") },
                isError = form.merchantError != null,
                supportingText = form.merchantError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.code,
                onValueChange = { v -> viewModel.edit { it.copy(code = v) } },
                label = { Text("Promo code") },
                supportingText = { Text(if (form.barcode != null && form.barcode == form.code.trim()) "Scanned barcode saved" else "Type it, or scan a barcode or QR code") },
                trailingIcon = {
                    IconButton(onClick = {
                        scanBarcode(
                            context,
                            onResult = { it?.let(viewModel::onScanned) },
                            onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
                        )
                    }) { Icon(Icons.Default.QrCodeScanner, "Scan barcode") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    label = "Discount",
                    value = form.kind,
                    options = DiscountKind.entries,
                    optionLabel = { it.label },
                    onSelect = { k -> viewModel.edit { it.copy(kind = k, valueError = null) } },
                    modifier = Modifier.weight(1.4f),
                )
                if (form.needsValue) {
                    OutlinedTextField(
                        value = form.value,
                        onValueChange = { v -> viewModel.edit { it.copy(value = v, valueError = null) } },
                        label = { Text(if (form.kind == DiscountKind.PERCENT) "Percent" else "Amount") },
                        suffix = if (form.kind == DiscountKind.PERCENT) ({ Text("%") }) else null,
                        isError = form.valueError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            form.valueError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            DateField(
                label = "Expires",
                date = form.expiresOn,
                onDateChange = { d -> viewModel.edit { it.copy(expiresOn = d) } },
                placeholder = "No expiry date",
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.minPurchase,
                onValueChange = { v -> viewModel.edit { it.copy(minPurchase = v, minPurchaseError = null) } },
                label = { Text("Minimum purchase (optional)") },
                isError = form.minPurchaseError != null,
                supportingText = form.minPurchaseError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "Category",
                value = form.category,
                options = listOf<String?>(null) + CouponCategories,
                optionLabel = { it ?: "None" },
                onSelect = { c -> viewModel.edit { it.copy(category = c) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Where can you use it?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "You'll get a notification when you're near one of these places.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                places.filter { it.id in form.placeIds }.forEach { place ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.togglePlace(place.id) },
                        label = { Text(place.name) },
                        leadingIcon = { Icon(Icons.Default.Place, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Default.Clear, "Remove ${place.name}", Modifier.size(18.dp)) },
                    )
                }
                AssistChip(
                    onClick = { pickingPlaces = true },
                    label = { Text("Add place") },
                    leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                )
            }

            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.edit { it.copy(notes = v) } },
                label = { Text("Notes (optional)") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (pickingPlaces) {
        PlacePickerDialog(
            places = places,
            selectedIds = form.placeIds,
            onToggle = viewModel::togglePlace,
            onCreateNew = { pickingPlaces = false; onCreatePlace(form.merchant.trim()) },
            onDismiss = { pickingPlaces = false },
        )
    }
}
