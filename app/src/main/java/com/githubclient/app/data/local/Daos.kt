package com.githubclient.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts LIMIT 1")
    suspend fun getCurrentAccount(): AccountEntity?

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface RepoCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(repos: List<RepoCacheEntity>)

    /** 只观察指定账号的缓存，切换账号后不会串到别的账号 */
    @Query("SELECT * FROM repo_cache WHERE accountLogin = :login ORDER BY stars DESC")
    fun observeRepos(login: String): Flow<List<RepoCacheEntity>>

    /** 只清理指定账号的缓存，不影响其他账号 */
    @Query("DELETE FROM repo_cache WHERE accountLogin = :login")
    suspend fun clearFor(login: String)

    @Query("DELETE FROM repo_cache")
    suspend fun clear()
}

@Dao
interface ToolchainDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ToolchainItemEntity)

    @Query("SELECT * FROM toolchain_items ORDER BY toolName")
    fun observeAll(): Flow<List<ToolchainItemEntity>>

    @Query("SELECT * FROM toolchain_items WHERE toolName = :name")
    suspend fun getByName(name: String): ToolchainItemEntity?

    @Query("DELETE FROM toolchain_items WHERE toolName = :name")
    suspend fun deleteByName(name: String)
}

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity): Long

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("UPDATE download_tasks SET downloadedBytes = :downloaded, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, downloaded: Long, status: String)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: Long)
}
