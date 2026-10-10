package com.mbox.stream.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** kind is "favorite" or "watchlist". */
@Entity(tableName = "user_list", primaryKeys = ["titleId", "kind"])
data class UserListEntry(val titleId: String, val kind: String, val addedAt: Long)

@Entity(tableName = "progress")
data class ProgressEntry(
    @PrimaryKey val entryKey: String,
    val titleId: String,
    val episodeKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val label: String
)

@Entity(tableName = "custom_source")
data class CustomSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val uri: String,
    val isLocal: Boolean
)

@Dao
interface UserDao {
    @Query("SELECT * FROM user_list ORDER BY addedAt DESC")
    fun observeLists(): Flow<List<UserListEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToList(entry: UserListEntry)

    @Query("DELETE FROM user_list WHERE titleId = :titleId AND kind = :kind")
    suspend fun removeFromList(titleId: String, kind: String)

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    fun observeProgress(): Flow<List<ProgressEntry>>

    @Query("SELECT * FROM progress WHERE entryKey = :entryKey")
    suspend fun progress(entryKey: String): ProgressEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(entry: ProgressEntry)

    @Query("DELETE FROM progress")
    suspend fun clearProgress()

    @Query("SELECT * FROM custom_source ORDER BY id DESC")
    fun observeCustomSources(): Flow<List<CustomSource>>

    @Insert
    suspend fun addCustomSource(source: CustomSource)

    @Delete
    suspend fun deleteCustomSource(source: CustomSource)
}

@Database(
    entities = [UserListEntry::class, ProgressEntry::class, CustomSource::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
