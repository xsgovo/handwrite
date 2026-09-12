package com.xsgovo.handwrite.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LibraryItemEntity::class,
        DocumentStateEntity::class,
        PageEntity::class,
        PageElementEntity::class,
        ResourceEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class HandwriteDatabase : RoomDatabase() {
    abstract fun handwriteDao(): HandwriteDao
}

// v3 → v4：页面增加书签标记。
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pages ADD COLUMN bookmarked INTEGER NOT NULL DEFAULT 0")
    }
}
