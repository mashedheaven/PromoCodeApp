package com.promocodeapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.promocodeapp.data.db.dao.*
import com.promocodeapp.data.db.entity.*

@Database(
    entities = [
        CouponEntity::class,
        CouponLocationEntity::class,
        MembershipEntity::class,
        MembershipLocationEntity::class,
        UserEntity::class,
        PendingChangeEntity::class,
        SyncMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun couponDao(): CouponDao
    abstract fun couponLocationDao(): CouponLocationDao
    abstract fun membershipDao(): MembershipDao
    abstract fun membershipLocationDao(): MembershipLocationDao
    abstract fun userDao(): UserDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "promocodeapp.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
