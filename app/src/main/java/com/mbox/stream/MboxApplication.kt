package com.mbox.stream

import android.app.Application
import androidx.room.Room
import com.mbox.stream.data.db.AppDatabase
import com.mbox.stream.settings.SettingsStore

class MboxApplication : Application() {
    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "mbox.db").build()
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
