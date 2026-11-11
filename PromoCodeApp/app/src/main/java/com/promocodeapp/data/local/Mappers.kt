package com.promocodeapp.data.local

import com.promocodeapp.core.model.BarcodeFormat
import com.promocodeapp.core.model.BillingCycle
import com.promocodeapp.core.model.Coupon
import com.promocodeapp.core.model.Discount
import com.promocodeapp.core.model.Membership
import com.promocodeapp.core.model.MembershipType
import com.promocodeapp.core.model.Place

object DiscountTypes {
    const val PERCENTAGE = "PERCENTAGE"
    const val FIXED_AMOUNT = "FIXED_AMOUNT"
    const val BOGO = "BOGO"
    const val FREE_SHIPPING = "FREE_SHIPPING"
}

fun CouponEntity.toModel(): Coupon = Coupon(
    id = id,
    code = code,
    merchant = merchant,
    discount = when (discountType) {
        DiscountTypes.FIXED_AMOUNT -> Discount.FixedAmount(discountValue, currency)
        DiscountTypes.BOGO -> Discount.BuyOneGetOne
        DiscountTypes.FREE_SHIPPING -> Discount.FreeShipping
        else -> Discount.Percentage(discountValue)
    },
    minPurchase = minPurchase,
    category = category,
    notes = notes,
    expiresOn = expiresOn,
    barcode = barcode,
    barcodeFormat = BarcodeFormat.fromName(barcodeFormat),
    isFavorite = isFavorite,
    usedAt = usedAt,
    isArchived = isArchived,
    placeIds = placeIds,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** [existing] carries over device-local columns that the model does not hold. */
fun Coupon.toEntity(existing: CouponEntity? = null, currencyFallback: String = "USD"): CouponEntity = CouponEntity(
    id = id,
    code = code,
    merchant = merchant,
    discountType = when (discount) {
        is Discount.Percentage -> DiscountTypes.PERCENTAGE
        is Discount.FixedAmount -> DiscountTypes.FIXED_AMOUNT
        Discount.BuyOneGetOne -> DiscountTypes.BOGO
        Discount.FreeShipping -> DiscountTypes.FREE_SHIPPING
    },
    discountValue = when (discount) {
        is Discount.Percentage -> discount.percent
        is Discount.FixedAmount -> discount.amount
        else -> 0.0
    },
    currency = (discount as? Discount.FixedAmount)?.currency ?: existing?.currency ?: currencyFallback,
    minPurchase = minPurchase,
    category = category,
    notes = notes,
    expiresOn = expiresOn,
    barcode = barcode,
    barcodeFormat = barcodeFormat?.name,
    isFavorite = isFavorite,
    usedAt = usedAt,
    isArchived = isArchived,
    placeIds = placeIds,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
    dirty = true,
    // A changed expiry date deserves a fresh reminder.
    reminderSentFor = existing?.reminderSentFor?.takeIf { it == expiresOn },
)

fun PlaceEntity.toModel(): Place = Place(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Place.toEntity(): PlaceEntity = PlaceEntity(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters.coerceIn(Place.MIN_RADIUS_METERS, Place.MAX_RADIUS_METERS),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MembershipEntity.toModel(): Membership = Membership(
    id = id,
    organization = organization,
    memberNumber = memberNumber,
    type = MembershipType.fromName(type),
    startedOn = startedOn,
    renewsOn = renewsOn,
    fee = fee,
    billingCycle = BillingCycle.fromName(billingCycle),
    currency = currency,
    notes = notes,
    reminderDays = reminderDays,
    isActive = isActive,
    placeIds = placeIds,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Membership.toEntity(existing: MembershipEntity? = null): MembershipEntity = MembershipEntity(
    id = id,
    organization = organization,
    memberNumber = memberNumber,
    type = type.name,
    startedOn = startedOn,
    renewsOn = renewsOn,
    fee = fee,
    billingCycle = billingCycle.name,
    currency = currency,
    notes = notes,
    reminderDays = reminderDays,
    isActive = isActive,
    placeIds = placeIds,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
    dirty = true,
    reminderSentFor = existing?.reminderSentFor,
)
