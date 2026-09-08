package com.githubclient.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        RepoCacheEntity::class,
        ToolchainItemEntity::class,
        DownloadTaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun repoCacheDao(): RepoCacheDao
    abstract fun toolchainDao(): ToolchainDao
    abstract fun downloadTaskDao(): DownloadTaskDao
}
