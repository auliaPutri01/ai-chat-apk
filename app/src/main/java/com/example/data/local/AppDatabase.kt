package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ApiConfigDao
import com.example.data.local.dao.ChatDao
import com.example.data.local.entity.ApiConfigEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ApiConfigEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun apiConfigDao(): ApiConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migrasi 1 -> 2 (NON-destruktif): menambah kolom lampiran & fallback.
         *
         * SQL di bawah HARUS cocok persis dengan entity:
         * - chat_messages.attachmentsJson : TEXT  , nullable  -> String?
         * - chat_messages.servedBy        : TEXT  , nullable  -> String?
         * - api_configs.supportsVision    : INTEGER NOT NULL DEFAULT 1 -> Boolean
         *   dengan @ColumnInfo(defaultValue = "1") di entity
         * - api_configs.fallbackConfigId  : TEXT  , nullable  -> String?
         *
         * Tidak ada tabel yang dihapus dan tidak ada data yang hilang; profil serta
         * riwayat chat lama tetap utuh setelah update.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentsJson TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN servedBy TEXT")
                db.execSQL(
                    "ALTER TABLE api_configs ADD COLUMN supportsVision INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL("ALTER TABLE api_configs ADD COLUMN fallbackConfigId TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_hub.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
