package com.promocodeapp.ui.coupons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.promocodeapp.core.model.Coupon
import com.promocodeapp.core.model.CouponFilter
import com.promocodeapp.core.model.CouponQuery
import com.promocodeapp.core.model.CouponSort
import com.promocodeapp.core.util.today
import com.promocodeapp.data.repository.CouponRepository
import com.promocodeapp.data.repository.PlaceRepository
import com.promocodeapp.ui.components.EmptyState
import com.promocodeapp.ui.components.expiryColor
import com.promocodeapp.ui.components.expiryText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CouponRow(val coupon: Coupon, val placeNames: List<String>)

data class CouponListState(
    val loading: Boolean = true,
    val rows: List<CouponRow> = emptyList(),
    val query: CouponQuery = CouponQuery(),
    val today: LocalDate = LocalDate.now(),
    /** Name of the place the list is narrowed to, if any. */
    val placeName: String? = null,
    val totalCoupons: Int = 0,
    val hasPlaces: Boolean = false,
)

@HiltViewModel
class CouponListViewModel @Inject constructor(
    private val coupons: CouponRepository,
    places: PlaceRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val query = MutableStateFlow(
        CouponQuery(
            placeId = savedState.get<String>("placeId"),
            filter = savedState.get<String>("filter")?.let { name -> CouponFilter.entries.firstOrNull { it.name == name } }
                ?: CouponFilter.ACTIVE,
        )
    )

    // One long-lived pipeline: Room pushes changes, so nothing is ever reloaded by hand.
    val state: StateFlow<CouponListState> = combine(coupons.observeAll(), places.observeAll(), query) { all, allPlaces, q ->
        val today = clock.today()
        val names = allPlaces.associate { it.id to it.name }
        CouponListState(
            loading = false,
            rows = q.apply(all, today).map { c -> CouponRow(c, c.placeIds.mapNotNull(names::get)) },
            query = q,
            today = today,
            placeName = q.placeId?.let(names::get),
            totalCoupons = all.size,
            hasPlaces = allPlaces.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CouponListState())

    fun setText(text: String) = query.update { it.copy(text = text) }
    fun setFilter(filter: CouponFilter) = query.update { it.copy(filter = filter) }
    fun setSort(sort: CouponSort) = query.update { it.copy(sort = sort) }
    fun clearPlace() = query.update { it.copy(placeId = null) }

    fun toggleFavorite(coupon: Coupon) {
        viewModelScope.launch { coupons.setFavorite(coupon.id, !coupon.isFavorite) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponListScreen(
    onOpenCoupon: (String) -> Unit,
    onAddCoupon: () -> Unit,
    onSetUpAlerts: () -> Unit,
    alertsNeedSetup: Boolean,
    viewModel: CouponListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sortOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coupons") },
                actions = {
                    IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        CouponSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                leadingIcon = { RadioButton(selected = state.query.sort == sort, onClick = null) },
                                onClick = { viewModel.setSort(sort); sortOpen = false },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCoupon,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add coupon") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query.text,
                onValueChange = viewModel::setText,
                placeholder = { Text("Search store, code or notes") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.query.text.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setText("") }) { Icon(Icons.Default.Clear, "Clear search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                state.placeName?.let { name ->
                    item {
                        InputChip(
                            selected = true,
                            onClick = viewModel::clearPlace,
                            label = { Text(name) },
                            leadingIcon = { Icon(Icons.Default.Place, null, Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Default.Clear, "Show all places", Modifier.size(18.dp)) },
                        )
                    }
                }
                items(CouponFilter.entries) { filter ->
                    FilterChip(
                        selected = state.query.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }

            if (alertsNeedSetup && state.hasPlaces) AlertsBanner(onSetUpAlerts)

            when {
                state.loading -> Unit
                state.rows.isEmpty() -> EmptyState(
                    icon = Icons.Default.LocalOffer,
                    title = if (state.totalCoupons == 0) "No coupons yet" else "Nothing here",
                    message = when {
                        state.totalCoupons == 0 -> "Add a promo code and the stores where it works. You'll get a reminder when you're nearby."
                        state.query.text.isNotBlank() -> "No coupons match \"${state.query.text.trim()}\"."
                        else -> emptyMessage(state.query.filter)
                    },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.rows, key = { it.coupon.id }) { row ->
                        CouponCard(
                            row = row,
                            today = state.today,
                            onClick = { onOpenCoupon(row.coupon.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(row.coupon) },
                        )
                    }
                }
            }
        }
    }
}

private fun emptyMessage(filter: CouponFilter) = when (filter) {
    CouponFilter.ACTIVE -> "You have no active coupons."
    CouponFilter.FAVORITES -> "Tap the heart on a coupon to keep it here."
    CouponFilter.EXPIRING_SOON -> "Nothing expires in the next week."
    CouponFilter.USED -> "Coupons you mark as used show up here."
    CouponFilter.EXPIRED -> "No expired coupons."
    CouponFilter.ARCHIVED -> "The archive is empty."
}

@Composable
private fun AlertsBanner(onSetUp: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NotificationsActive, null)
            Spacer(Modifier.width(12.dp))
            Text("Nearby alerts are off", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onSetUp) { Text("Turn on") }
        }
    }
}

@Composable
fun CouponCard(row: CouponRow, today: LocalDate, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val coupon = row.coupon
    val expiry = coupon.expiry(today)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        coupon.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    coupon.category?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (coupon.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (coupon.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (coupon.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        coupon.discount.label(),
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    coupon.code,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        coupon.isUsed -> "Used"
                        coupon.isArchived -> "Archived"
                        else -> expiryText(expiry)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (coupon.isUsed || coupon.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else expiryColor(expiry),
                )
                if (row.placeNames.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        row.placeNames.joinToString(),
                        Modifier.padding(start = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
