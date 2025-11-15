package com.promocodeapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.promocodeapp.domain.model.Coupon
import com.promocodeapp.domain.model.DiscountType
import com.promocodeapp.domain.model.Location
import com.promocodeapp.domain.repository.CouponRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CouponUiState(
    val coupons: List<Coupon> = emptyList(),
    val filteredCoupons: List<Coupon> = emptyList(),
    val selectedCoupon: Coupon? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val filterType: FilterType = FilterType.ALL,
    val sortBy: SortBy = SortBy.EXPIRATION
)

enum class FilterType {
    ALL, FAVORITES, EXPIRING_TODAY, EXPIRING_THIS_WEEK, EXPIRED, BY_MERCHANT
}

enum class SortBy {
    EXPIRATION, MERCHANT, DISCOUNT, CREATED
}

@HiltViewModel
class CouponViewModel @Inject constructor(
    private val couponRepository: CouponRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CouponUiState())
    val uiState: StateFlow<CouponUiState> = _uiState.asStateFlow()

    private var userId: String = ""

    fun initialize(userId: String) {
        this.userId = userId
        loadCoupons()
    }

    private fun loadCoupons() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            couponRepository.getUserCoupons(userId)
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unknown error occurred"
                    )
                }
                .collectLatest { coupons ->
                    _uiState.value = _uiState.value.copy(
                        coupons = coupons,
                        isLoading = false,
                        error = null
                    )
                    applyFiltersAndSort()
                }
        }
    }

    fun createCoupon(
        code: String,
        merchantName: String,
        discountType: DiscountType,
        discountValue: Double,
        expirationDate: Long,
        minPurchaseAmount: Double? = null,
        category: String? = null,
        description: String? = null,
        locations: List<Location> = emptyList()
    ) {
        viewModelScope.launch {
            val coupon = Coupon(
                id = 0,
                userId = userId,
                code = code,
                merchantName = merchantName,
                discountType = discountType,
                discountValue = discountValue,
                minPurchaseAmount = minPurchaseAmount,
                description = description,
                expirationDate = expirationDate,
                createdDate = System.currentTimeMillis(),
                category = category,
                locations = locations,
                lastModified = System.currentTimeMillis()
            )

            couponRepository.createCoupon(coupon)
                .onSuccess {
                    loadCoupons() // Reload coupons
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to create coupon"
                    )
                }
        }
    }

    fun updateCoupon(coupon: Coupon) {
        viewModelScope.launch {
            val updatedCoupon = coupon.copy(
                lastModified = System.currentTimeMillis()
            )

            couponRepository.updateCoupon(updatedCoupon)
                .onSuccess {
                    loadCoupons()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to update coupon"
                    )
                }
        }
    }

    fun deleteCoupon(couponId: Long) {
        viewModelScope.launch {
            couponRepository.deleteCoupon(couponId)
                .onSuccess {
                    loadCoupons()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to delete coupon"
                    )
                }
        }
    }

    fun archiveCoupon(couponId: Long) {
        viewModelScope.launch {
            couponRepository.archiveCoupon(couponId)
                .onSuccess {
                    loadCoupons()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to archive coupon"
                    )
                }
        }
    }

    fun toggleFavorite(couponId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            couponRepository.toggleFavorite(couponId, !isFavorite)
                .onSuccess {
                    loadCoupons()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to toggle favorite"
                    )
                }
        }
    }

    fun selectCoupon(coupon: Coupon) {
        _uiState.value = _uiState.value.copy(selectedCoupon = coupon)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedCoupon = null)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFiltersAndSort()
    }

    fun setFilterType(filterType: FilterType) {
        _uiState.value = _uiState.value.copy(filterType = filterType)
        applyFiltersAndSort()
    }

    fun setSortBy(sortBy: SortBy) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy)
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        var filtered = state.coupons

        // Apply search filter
        if (state.searchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                it.merchantName.contains(state.searchQuery, ignoreCase = true) ||
                it.description?.contains(state.searchQuery, ignoreCase = true) == true ||
                it.code.contains(state.searchQuery, ignoreCase = true)
            }
        }

        // Apply category filter
        filtered = when (state.filterType) {
            FilterType.ALL -> filtered.filter { !it.isExpired }
            FilterType.FAVORITES -> filtered.filter { it.isFavorite && !it.isExpired }
            FilterType.EXPIRING_TODAY -> filtered.filter { it.expiresInHours in 0..24 }
            FilterType.EXPIRING_THIS_WEEK -> filtered.filter { it.daysUntilExpiration in 0..7 }
            FilterType.EXPIRED -> filtered.filter { it.isExpired }
            FilterType.BY_MERCHANT -> filtered
        }

        // Apply sorting
        filtered = when (state.sortBy) {
            SortBy.EXPIRATION -> filtered.sortedBy { it.expirationDate }
            SortBy.MERCHANT -> filtered.sortedBy { it.merchantName }
            SortBy.DISCOUNT -> filtered.sortedByDescending { it.discountValue }
            SortBy.CREATED -> filtered.sortedByDescending { it.createdDate }
        }

        _uiState.value = state.copy(filteredCoupons = filtered)
    }

    fun deleteExpiredCoupons() {
        viewModelScope.launch {
            couponRepository.deleteExpiredCoupons(userId)
                .onSuccess {
                    loadCoupons()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to delete expired coupons"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
