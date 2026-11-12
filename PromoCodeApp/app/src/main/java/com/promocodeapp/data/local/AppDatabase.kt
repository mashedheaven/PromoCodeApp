package com.promocodeapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CouponEntity::class,
        PlaceEntity::class,
        MembershipEntity::class,
        PlaceAlertStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun couponDao(): CouponDao
    abstract fun placeDao(): PlaceDao
    abstract fun membershipDao(): MembershipDao
    abstract fun placeAlertStateDao(): PlaceAlertStateDao

    companion object {
        const val NAME = "promocodeapp.db"
    }
}
