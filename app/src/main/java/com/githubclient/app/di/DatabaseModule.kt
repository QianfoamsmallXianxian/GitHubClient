package com.githubclient.app.di

import android.content.Context
import androidx.room.Room
import com.githubclient.app.data.local.AccountDao
import com.githubclient.app.data.local.AppDatabase
import com.githubclient.app.data.local.DownloadTaskDao
import com.githubclient.app.data.local.RepoCacheDao
import com.githubclient.app.data.local.ToolchainDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "github_client.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideRepoCacheDao(db: AppDatabase): RepoCacheDao = db.repoCacheDao()

    @Provides
    fun provideToolchainDao(db: AppDatabase): ToolchainDao = db.toolchainDao()

    @Provides
    fun provideDownloadTaskDao(db: AppDatabase): DownloadTaskDao = db.downloadTaskDao()
}
