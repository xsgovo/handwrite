package com.xsgovo.handwrite.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.xsgovo.handwrite.core.data.FilePendingCommandJournal
import com.xsgovo.handwrite.core.data.ContentAddressedResourceRepository
import com.xsgovo.handwrite.core.data.ProtoSettingsRepository
import com.xsgovo.handwrite.core.data.RoomDocumentRepository
import com.xsgovo.handwrite.core.data.db.HandwriteDao
import com.xsgovo.handwrite.core.data.db.HandwriteDatabase
import com.xsgovo.handwrite.core.data.settings.AppSettingsPayload
import com.xsgovo.handwrite.core.data.settings.AppSettingsSerializer
import com.xsgovo.handwrite.core.document.DocumentCommandStore
import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.document.DocumentRepository
import com.xsgovo.handwrite.core.document.DurableCommandExecutor
import com.xsgovo.handwrite.core.document.EpochClock
import com.xsgovo.handwrite.core.document.PendingCommandJournal
import com.xsgovo.handwrite.core.document.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): HandwriteDatabase = Room.databaseBuilder(
        context,
        HandwriteDatabase::class.java,
        "handwrite.db",
    ).build()

    @Provides
    fun provideDao(database: HandwriteDatabase): HandwriteDao = database.handwriteDao()

    @Provides
    @Singleton
    fun provideRoomDocumentRepository(
        database: HandwriteDatabase,
        dao: HandwriteDao,
        clock: EpochClock,
    ): RoomDocumentRepository = RoomDocumentRepository(database, dao, clock)

    @Provides
    fun provideDocumentRepository(repository: RoomDocumentRepository): DocumentRepository = repository

    @Provides
    fun provideDocumentCommandStore(repository: RoomDocumentRepository): DocumentCommandStore = repository

    @Provides
    @Singleton
    fun provideBackgroundResourceRepository(
        @ApplicationContext context: Context,
        database: HandwriteDatabase,
        dao: HandwriteDao,
    ): BackgroundResourceRepository = ContentAddressedResourceRepository(
        database = database,
        dao = dao,
        directory = File(context.filesDir, "resources"),
        ioDispatcher = Dispatchers.IO,
    )

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<AppSettingsPayload> = DataStoreFactory.create(
        serializer = AppSettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { AppSettingsSerializer.defaultValue },
        produceFile = { context.dataStoreFile("app_settings.pb") },
    )

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: DataStore<AppSettingsPayload>,
    ): SettingsRepository = ProtoSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun providePendingCommandJournal(
        @ApplicationContext context: Context,
    ): PendingCommandJournal = FilePendingCommandJournal(
        directory = File(context.noBackupFilesDir, "pending_commands"),
        ioDispatcher = Dispatchers.IO,
    )

    @Provides
    fun provideEpochClock(): EpochClock = EpochClock(System::currentTimeMillis)

    @Provides
    @Singleton
    fun provideDurableCommandExecutor(
        store: DocumentCommandStore,
        journal: PendingCommandJournal,
    ): DurableCommandExecutor = DurableCommandExecutor(store, journal)
}
