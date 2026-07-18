package com.xsgovo.handwrite.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LibraryItemEntity::class,
        DocumentStateEntity::class,
        PageEntity::class,
        PageElementEntity::class,
        ResourceEntity::class,
        AppliedOperationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HandwriteDatabase : RoomDatabase() {
    abstract fun handwriteDao(): HandwriteDao
}
