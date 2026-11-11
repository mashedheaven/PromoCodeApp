package com.promocodeapp.ui.memberships

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.promocodeapp.core.model.BillingCycle
import com.promocodeapp.core.model.Expiry
import com.promocodeapp.core.model.Membership
import com.promocodeapp.core.model.MembershipType
import com.promocodeapp.core.model.Money
import com.promocodeapp.core.model.Place
import com.promocodeapp.core.util.formatMedium
import com.promocodeapp.core.util.newId
import com.promocodeapp.core.util.today
import com.promocodeapp.data.repository.MembershipRepository
import com.promocodeapp.data.repository.PlaceRepository
import com.promocodeapp.ui.components.ConfirmDialog
import com.promocodeapp.ui.components.DateField
import com.promocodeapp.ui.components.DropdownField
import com.promocodeapp.ui.components.EmptyState
import com.promocodeapp.ui.components.PlacePickerDialog
import com.promocodeapp.ui.components.expiryColor
import com.promocodeapp.ui.components.scanBarcode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class MembershipListState(
    val memberships: List<Membership> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** Yearly spend per currency, across active memberships. */
    val annualCosts: Map<String, Double> = emptyMap(),
)

@HiltViewModel
class MembershipListViewModel @Inject constructor(memberships: MembershipRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<MembershipListState?> = memberships.observeAll().map { all ->
        val today = clock.today()
        MembershipListState(
            // Soonest renewal first; inactive ones sink to the bottom.
            memberships = all.sortedWith(compareBy({ !it.isActive }, { it.nextRenewal(today) ?: LocalDate.MAX })),
            today = today,
            annualCosts = all.filter { it.isActive && it.annualCost > 0 }.groupBy { it.currency }
                .mapValues { (_, list) -> list.sumOf { it.annualCost } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembershipListScreen(
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    viewModel: MembershipListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Memberships") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add membership") })
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        if (s.memberships.isEmpty()) {
            EmptyState(
                Icons.Default.CardMembership, "No memberships yet",
                "Track gym memberships, subscriptions and loyalty cards, and get reminded before they renew.",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.annualCosts.isNotEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("You spend per year", style = MaterialTheme.typography.labelLarge)
                            Text(
                                s.annualCosts.entries.joinToString(" + ") { Money.format(it.value, it.key) },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            items(s.memberships, key = { it.id }) { m -> MembershipCard(m, s.today) { onOpen(m.id) } }
        }
    }
}

@Composable
private fun MembershipCard(m: Membership, today: LocalDate, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.organization, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(m.type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (m.memberNumber.isNotBlank()) {
                Text("No. ${m.memberNumber}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val next = m.nextRenewal(today)
            val hasStatus = !m.isActive || next != null
            Row {
                when {
                    !m.isActive -> Text("Inactive", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    next != null -> {
                        val expiry = Expiry.of(next, today)
                        val days = (expiry as? Expiry.InDays)?.days
                        Text(
                            when {
                                expiry is Expiry.Expired -> "Ended ${next.formatMedium()}"
                                days == null -> "Renews today"
                                days == 1L -> "Renews tomorrow"
                                else -> "Renews in $days days"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = expiryColor(expiry),
                        )
                    }
                }
                val fee = m.fee
                if (fee != null && fee > 0 && m.billingCycle != BillingCycle.NONE) {
                    Text(
                        "${if (hasStatus) "  ·  " else ""}${Money.format(fee, m.currency)} ${m.billingCycle.label.lowercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

data class MembershipForm(
    val organization: String = "",
    val memberNumber: String = "",
    val type: MembershipType = MembershipType.OTHER,
    val billingCycle: BillingCycle = BillingCycle.NONE,
    val fee: String = "",
    val renewsOn: LocalDate? = null,
    val reminderDays: Int = 7,
    val notes: String = "",
    val isActive: Boolean = true,
    val placeIds: Set<String> = emptySet(),
    val organizationError: String? = null,
    val feeError: String? = null,
)

val ReminderChoices = listOf(0, 1, 3, 7, 14, 30)

@HiltViewModel
class MembershipEditViewModel @Inject constructor(
    private val memberships: MembershipRepository,
    places: PlaceRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val editingId: String? = savedState["id"]
    private var original: Membership? = null
    val isNew: Boolean get() = editingId == null

    private val _form = MutableStateFlow(MembershipForm())
    val form: StateFlow<MembershipForm> = _form.asStateFlow()
    val places: StateFlow<List<Place>> = places.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (editingId != null) viewModelScope.launch {
            val m = memberships.get(editingId) ?: return@launch
            original = m
            _form.value = MembershipForm(
                organization = m.organization,
                memberNumber = m.memberNumber,
                type = m.type,
                billingCycle = m.billingCycle,
                fee = m.fee?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty(),
                renewsOn = m.renewsOn,
                reminderDays = m.reminderDays,
                notes = m.notes.orEmpty(),
                isActive = m.isActive,
                placeIds = m.placeIds.toSet(),
            )
        }
    }

    fun edit(transform: (MembershipForm) -> MembershipForm) = _form.update(transform)

    fun togglePlace(id: String) = _form.update {
        it.copy(placeIds = if (id in it.placeIds) it.placeIds - id else it.placeIds + id)
    }

    fun addPlace(id: String) = _form.update { it.copy(placeIds = it.placeIds + id) }

    fun save(onSaved: () -> Unit) {
        val f = _form.value
        val fee = Money.parse(f.fee)
        val paid = f.billingCycle != BillingCycle.NONE
        val checked = f.copy(
            organizationError = if (f.organization.isBlank()) "Enter the organization" else null,
            feeError = if (paid && f.fee.isNotBlank() && fee == null) "Enter an amount" else null,
        )
        _form.value = checked
        if (checked.organizationError != null || checked.feeError != null) return

        val base = original
        val now = clock.millis()
        val membership = Membership(
            id = base?.id ?: newId(),
            organization = f.organization.trim(),
            memberNumber = f.memberNumber.trim(),
            type = f.type,
            startedOn = base?.startedOn ?: clock.today(),
            renewsOn = f.renewsOn,
            fee = if (paid) fee else null,
            billingCycle = f.billingCycle,
            currency = base?.currency ?: Money.defaultCurrencyCode(),
            notes = f.notes.trim().ifEmpty { null },
            reminderDays = f.reminderDays,
            isActive = f.isActive,
            placeIds = f.placeIds.toList(),
            createdAt = base?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            memberships.save(membership)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = original?.id ?: return
        viewModelScope.launch {
            memberships.delete(id)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MembershipEditScreen(
    onDone: () -> Unit,
    onCreatePlace: (suggestedName: String) -> Unit,
    newPlaceId: String?,
    onNewPlaceConsumed: () -> Unit,
    viewModel: MembershipEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pickingPlaces by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(newPlaceId) {
        if (newPlaceId != null) {
            viewModel.addPlace(newPlaceId)
            onNewPlaceConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New membership" else "Edit membership") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, "Discard") } },
                actions = {
                    if (!viewModel.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                    TextButton(onClick = { viewModel.save(onDone) }) { Text("Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.organization,
                onValueChange = { v -> viewModel.edit { it.copy(organization = v, organizationError = null) } },
                label = { Text("Organization") },
                isError = form.organizationError != null,
                supportingText = form.organizationError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField("Type", form.type, MembershipType.entries, { it.label }, { t -> viewModel.edit { it.copy(type = t) } }, Modifier.fillMaxWidth())
            OutlinedTextField(
                value = form.memberNumber,
                onValueChange = { v -> viewModel.edit { it.copy(memberNumber = v) } },
                label = { Text("Member number (optional)") },
                trailingIcon = {
                    IconButton(onClick = {
                        scanBarcode(
                            context,
                            onResult = { scan -> scan?.let { s -> viewModel.edit { it.copy(memberNumber = s.value) } } },
                            onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
                        )
                    }) { Icon(Icons.Default.QrCodeScanner, "Scan card") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField("Billing", form.billingCycle, BillingCycle.entries, { it.label }, { b -> viewModel.edit { it.copy(billingCycle = b) } }, Modifier.weight(1f))
                if (form.billingCycle != BillingCycle.NONE) {
                    OutlinedTextField(
                        value = form.fee,
                        onValueChange = { v -> viewModel.edit { it.copy(fee = v, feeError = null) } },
                        label = { Text("Fee") },
                        isError = form.feeError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            form.feeError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            DateField(
                label = if (form.billingCycle == BillingCycle.NONE) "Valid until" else "Next renewal",
                date = form.renewsOn,
                onDateChange = { d -> viewModel.edit { it.copy(renewsOn = d) } },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "Remind me",
                value = form.reminderDays,
                options = ReminderChoices,
                optionLabel = { if (it == 0) "Never" else if (it == 1) "1 day before" else "$it days before" },
                onSelect = { d -> viewModel.edit { it.copy(reminderDays = d) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = form.isActive, onCheckedChange = { a -> viewModel.edit { it.copy(isActive = a) } })
            }

            Text("Locations", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Get a reminder to use this membership when you're nearby.",
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
                label = { Text("Benefits and notes (optional)") },
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
            onCreateNew = { pickingPlaces = false; onCreatePlace(form.organization.trim()) },
            onDismiss = { pickingPlaces = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete membership?",
            message = "${form.organization} will be removed from all your devices.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; viewModel.delete(onDone) },
            onDismiss = { confirmDelete = false },
        )
    }
}
