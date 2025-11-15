package com.promocodeapp.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promocodeapp.domain.model.Coupon
import com.promocodeapp.domain.model.DiscountType
import com.promocodeapp.presentation.viewmodel.CouponViewModel
import com.promocodeapp.presentation.viewmodel.FilterType
import com.promocodeapp.presentation.viewmodel.SortBy
import com.promocodeapp.util.DateUtils
import kotlin.math.roundToInt

@Composable
fun CouponListScreen(
    userId: String,
    viewModel: CouponViewModel = hiltViewModel(),
    onNavigateToDetail: (couponId: Long) -> Unit,
    onNavigateToCreateCoupon: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(userId) {
        viewModel.initialize(userId)
    }

    Scaffold(
        topBar = {
            CouponTopAppBar()
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreateCoupon,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Coupon")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search and Filter Section
            SearchAndFilterSection(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                filterType = uiState.filterType,
                onFilterChange = { viewModel.setFilterType(it) },
                sortBy = uiState.sortBy,
                onSortChange = { viewModel.setSortBy(it) }
            )

            // Error Message
            if (uiState.error != null) {
                ErrorBanner(
                    message = uiState.error!!,
                    onDismiss = { viewModel.clearError() }
                )
            }

            // Loading State
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredCoupons.isEmpty()) {
                EmptyStateScreen(filterType = uiState.filterType)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredCoupons) { coupon ->
                        CouponCard(
                            coupon = coupon,
                            onClick = {
                                viewModel.selectCoupon(coupon)
                                onNavigateToDetail(coupon.id)
                            },
                            onFavoriteClick = {
                                viewModel.toggleFavorite(coupon.id, coupon.isFavorite)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponTopAppBar() {
    TopAppBar(
        title = {
            Text(
                "My Coupons",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun SearchAndFilterSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterType: FilterType,
    onFilterChange: (FilterType) -> Unit,
    sortBy: SortBy,
    onSortChange: (SortBy) -> Unit
) {
    var expandedFilter by remember { mutableStateOf(false) }
    var expandedSort by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search coupons...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Filter and Sort Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expandedFilter = !expandedFilter },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filter", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(filterType.name.replace("_", " "))
                }

                DropdownMenu(
                    expanded = expandedFilter,
                    onDismissRequest = { expandedFilter = false },
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    FilterType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name.replace("_", " ")) },
                            onClick = {
                                onFilterChange(type)
                                expandedFilter = false
                            }
                        )
                    }
                }
            }

            // Sort Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expandedSort = !expandedSort },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(sortBy.name)
                }

                DropdownMenu(
                    expanded = expandedSort,
                    onDismissRequest = { expandedSort = false }
                ) {
                    SortBy.values().forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.name) },
                            onClick = {
                                onSortChange(sort)
                                expandedSort = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CouponCard(
    coupon: Coupon,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val daysRemaining = DateUtils.daysUntil(coupon.expirationDate)
    val expirationColor = when {
        coupon.isExpired -> Color.Red
        daysRemaining <= 1 -> Color(0xFFFF6B6B)
        daysRemaining <= 7 -> Color(0xFFFFA500)
        else -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row with Merchant and Favorite Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = coupon.merchantName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (coupon.category != null) {
                        Text(
                            text = coupon.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        if (coupon.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (coupon.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Discount Value
            DiscountBadge(coupon.discountType, coupon.discountValue)

            Spacer(modifier = Modifier.height(12.dp))

            // Code and Expiration Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Code
                Column {
                    Text(
                        text = "Code",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = coupon.code,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Expiration
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Expires",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            coupon.isExpired -> "Expired"
                            daysRemaining == 0L -> "Today"
                            daysRemaining == 1L -> "Tomorrow"
                            else -> "$daysRemaining days"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = expirationColor
                    )
                }
            }

            // Expiration Progress Bar
            if (!coupon.isExpired) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = (daysRemaining.coerceIn(0, 30).toFloat() / 30f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = expirationColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun DiscountBadge(discountType: DiscountType, discountValue: Double) {
    val badgeText = when (discountType) {
        is DiscountType.Percentage -> "${discountValue.roundToInt()}% OFF"
        is DiscountType.FixedAmount -> "\$${String.format("%.2f", discountValue)} OFF"
        DiscountType.BuyOneGetOne -> "BOGO"
        DiscountType.FreeShipping -> "FREE SHIPPING"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
fun EmptyStateScreen(filterType: FilterType) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.ReceiptLong,
                contentDescription = "No Coupons",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
            Text(
                text = when (filterType) {
                    FilterType.FAVORITES -> "No favorite coupons yet"
                    FilterType.EXPIRING_TODAY -> "No coupons expiring today"
                    FilterType.EXPIRING_THIS_WEEK -> "No coupons expiring this week"
                    FilterType.EXPIRED -> "No expired coupons"
                    else -> "No coupons yet. Add one to get started!"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}
