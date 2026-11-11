package com.promocodeapp.ui.places

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.promocodeapp.BuildConfig
import com.promocodeapp.core.model.Place
import com.promocodeapp.core.util.newId
import com.promocodeapp.core.util.today
import com.promocodeapp.data.repository.CouponRepository
import com.promocodeapp.data.repository.MembershipRepository
import com.promocodeapp.data.repository.PlaceRepository
import com.promocodeapp.data.settings.SettingsStore
import com.promocodeapp.location.GeocodedAddress
import com.promocodeapp.location.LatLng
import com.promocodeapp.location.LocationSource
import com.promocodeapp.ui.components.ConfirmDialog
import com.promocodeapp.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import com.google.android.gms.maps.model.LatLng as MapLatLng

data class PlaceRow(val place: Place, val activeCoupons: Int, val memberships: Int)

@HiltViewModel
class PlaceListViewModel @Inject constructor(
    places: PlaceRepository,
    coupons: CouponRepository,
    memberships: MembershipRepository,
    clock: Clock,
) : ViewModel() {
    val rows: StateFlow<List<PlaceRow>?> = combine(places.observeAll(), coupons.observeAll(), memberships.observeAll()) { p, c, m ->
        val today = clock.today()
        val active = c.filter { it.isRedeemable(today) }
        p.map { place ->
            PlaceRow(place, active.count { place.id in it.placeIds }, m.count { it.isActive && place.id in it.placeIds })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceListScreen(
    onOpenPlace: (String) -> Unit,
    onAddPlace: () -> Unit,
    viewModel: PlaceListViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Places") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddPlace, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add place") })
        },
    ) { padding ->
        val list = rows ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                Icons.Default.Place, "No places yet",
                "Save the stores you visit, then attach coupons to them to be reminded when you're nearby.",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.place.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.place.name) },
                        supportingContent = {
                            val watching = listOfNotNull(
                                row.activeCoupons.takeIf { it > 0 }?.let { if (it == 1) "1 coupon" else "$it coupons" },
                                row.memberships.takeIf { it > 0 }?.let { if (it == 1) "1 membership" else "$it memberships" },
                            ).joinToString(" · ").ifEmpty { "Nothing attached, so no alerts" }
                            Text(listOfNotNull(row.place.address, watching).joinToString("\n"))
                        },
                        leadingContent = { Icon(Icons.Default.Place, null) },
                        trailingContent = { Text("${row.place.radiusMeters} m") },
                        modifier = Modifier.clickable { onOpenPlace(row.place.id) },
                    )
                }
            }
        }
    }
}

data class PlaceForm(
    val name: String = "",
    val address: String = "",
    val position: LatLng? = null,
    val radiusMeters: Int = Place.DEFAULT_RADIUS_METERS,
    val searchResults: List<GeocodedAddress> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val nameError: String? = null,
)

@HiltViewModel
class PlaceEditViewModel @Inject constructor(
    private val places: PlaceRepository,
    private val locations: LocationSource,
    private val settings: SettingsStore,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val editingId: String? = savedState["id"]
    private var original: Place? = null
    val isNew: Boolean get() = editingId == null

    private val _form = MutableStateFlow(PlaceForm(name = savedState.get<String>("name").orEmpty()))
    val form: StateFlow<PlaceForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = editingId?.let { places.get(it) }
            if (existing != null) {
                original = existing
                _form.value = PlaceForm(existing.name, existing.address.orEmpty(), LatLng(existing.latitude, existing.longitude), existing.radiusMeters)
            } else {
                _form.update { it.copy(radiusMeters = settings.current().defaultRadiusMeters) }
            }
        }
    }

    fun setName(name: String) = _form.update { it.copy(name = name, nameError = null) }
    fun setAddress(address: String) = _form.update { it.copy(address = address) }
    fun setRadius(radius: Int) = _form.update { it.copy(radiusMeters = radius) }

    /** Moves the pin, e.g. from a tap on the map, and looks up the street address. */
    fun setPosition(position: LatLng) {
        _form.update { it.copy(position = position, searchResults = emptyList(), message = null) }
        viewModelScope.launch {
            locations.describe(position)?.let { label -> _form.update { if (it.position == position) it.copy(address = label) else it } }
        }
    }

    fun useCurrentLocation() {
        _form.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val position = locations.current()
            _form.update { it.copy(busy = false, message = if (position == null) "Couldn't get your location. Check that location is turned on." else null) }
            position?.let(::setPosition)
        }
    }

    fun onLocationDenied() = _form.update { it.copy(message = "Location permission is needed to use your current position.") }

    fun searchAddress() {
        val query = _form.value.address.trim()
        if (query.isEmpty()) return
        _form.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val results = locations.search(query)
            _form.update {
                it.copy(busy = false, searchResults = results, message = if (results.isEmpty()) "No match found. Check the address or your connection." else null)
            }
            if (results.size == 1) choose(results[0])
        }
    }

    fun choose(result: GeocodedAddress) =
        _form.update { it.copy(position = result.position, address = result.label, searchResults = emptyList(), message = null) }

    fun save(onSaved: (String) -> Unit) {
        val f = _form.value
        val position = f.position
        if (f.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this place a name") }
            return
        }
        if (position == null || !Place.isValidCoordinate(position.latitude, position.longitude)) {
            _form.update { it.copy(message = "Set the location first: use your current position or search for an address.") }
            return
        }
        val now = clock.millis()
        val place = Place(
            id = original?.id ?: newId(),
            name = f.name.trim(),
            address = f.address.trim().ifEmpty { null },
            latitude = position.latitude,
            longitude = position.longitude,
            radiusMeters = f.radiusMeters,
            createdAt = original?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            places.save(place)
            onSaved(place.id)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = original?.id ?: return
        viewModelScope.launch {
            places.delete(id)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditScreen(
    onCancel: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: PlaceEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.useCurrentLocation() else viewModel.onLocationDenied()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New place" else "Edit place") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Discard") } },
                actions = {
                    if (!viewModel.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                    TextButton(onClick = { viewModel.save(onSaved) }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                placeholder = { Text("e.g. Target – Downtown") },
                isError = form.nameError != null,
                supportingText = form.nameError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.address,
                onValueChange = viewModel::setAddress,
                label = { Text("Address") },
                trailingIcon = { IconButton(onClick = viewModel::searchAddress) { Icon(Icons.Default.Search, "Find address") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.Words),
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchAddress() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            form.searchResults.forEach { result ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.choose(result) },
                ) { Text(result.label, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    enabled = !form.busy,
                ) {
                    Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp))
                    Text("Use my current location", Modifier.padding(start = 8.dp))
                }
                if (form.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            form.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            val position = form.position
            if (position != null) {
                Text(
                    String.format(Locale.US, "Pinned at %.5f, %.5f", position.latitude, position.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
                PlaceMap(position, form.radiusMeters, viewModel::setPosition)
            }

            Text("Alert radius: ${form.radiusMeters} m", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = form.radiusMeters.toFloat(),
                onValueChange = { viewModel.setRadius((it / 50).roundToInt() * 50) },
                valueRange = Place.MIN_RADIUS_METERS.toFloat()..1000f,
                steps = 17,
            )
            Text(
                "A larger radius is more reliable but alerts you earlier. 150 m suits most stores.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete place?",
            message = "Coupons and memberships linked to ${form.name} are kept, but you'll no longer be alerted here.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; viewModel.delete(onCancel) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Tap-to-pin map with a preview of the alert radius. */
@Composable
private fun PlaceMap(position: LatLng?, radiusMeters: Int, onPick: (LatLng) -> Unit) {
    val target = position?.let { MapLatLng(it.latitude, it.longitude) }
    val camera = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(target ?: MapLatLng(20.0, 0.0), if (target != null) 16f else 1f)
    }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (target != null) camera.position = CameraPosition.fromLatLngZoom(target, maxOf(camera.position.zoom, 15f))
    }
    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false),
        onMapClick = { onPick(LatLng(it.latitude, it.longitude)) },
    ) {
        if (target != null) {
            Marker(state = rememberUpdatedMarkerState(position = target))
            Circle(center = target, radius = radiusMeters.toDouble(), strokeWidth = 2f, strokeColor = Color(0xFF6750A4), fillColor = Color(0x336750A4))
        }
    }
}
