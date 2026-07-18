package com.xsgovo.handwrite.core.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.xsgovo.handwrite.core.data.db.HandwriteDao
import com.xsgovo.handwrite.core.data.db.HandwriteDatabase
import com.xsgovo.handwrite.core.data.db.ResourceEntity
import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.document.ResourceInput
import com.xsgovo.handwrite.core.document.StoredResource
import com.xsgovo.handwrite.core.model.DomainFailure
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.ResourceId
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class ContentAddressedResourceRepository(
    private val database: HandwriteDatabase,
    private val dao: HandwriteDao,
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher,
) : BackgroundResourceRepository {
    override suspend fun import(
        mimeType: String,
        input: ResourceInput,
    ): DomainResult<StoredResource> = withContext(ioDispatcher) {
        val normalizedMimeType = mimeType.lowercase()
        if (!normalizedMimeType.isSupportedBackground()) {
            return@withContext DomainResult.Failure(DomainFailure.InvalidResource)
        }
        directory.mkdirs()
        if (!directory.isDirectory) return@withContext DomainResult.Failure(DomainFailure.StorageFull)

        val temporary = File(directory, ".import-${UUID.randomUUID()}")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var byteSize = 0L
            input.open().use { source ->
                temporary.outputStream().buffered().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        target.write(buffer, 0, count)
                        byteSize += count
                    }
                }
            }
            if (byteSize == 0L) return@withContext DomainResult.Failure(DomainFailure.InvalidResource)

            val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            dao.findResourceByHash(hash)?.let { existing ->
                return@withContext DomainResult.Success(existing.toStoredResource(directory))
            }

            val relativePath = "$hash.${normalizedMimeType.fileExtension()}"
            val destination = File(directory, relativePath)
            if (!destination.exists() && !temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = false)
            }
            val id = try {
                dao.insertResource(
                    ResourceEntity(
                        sha256 = hash,
                        mimeType = normalizedMimeType,
                        relativePath = relativePath,
                        byteSize = byteSize,
                        referenceCount = 0,
                    ),
                )
            } catch (exception: SQLiteConstraintException) {
                dao.findResourceByHash(hash)?.id ?: throw exception
            }
            val stored = dao.findResource(id) ?: return@withContext DomainResult.Failure(DomainFailure.DatabaseUnavailable)
            DomainResult.Success(stored.toStoredResource(directory))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: FileNotFoundException) {
            DomainResult.Failure(DomainFailure.ResourceNotFound)
        } catch (exception: SQLiteException) {
            DomainResult.Failure(DomainFailure.DatabaseUnavailable)
        } catch (exception: IOException) {
            DomainResult.Failure(DomainFailure.StorageFull)
        } finally {
            temporary.delete()
        }
    }

    override suspend fun find(resourceId: ResourceId): DomainResult<StoredResource> = withContext(ioDispatcher) {
        try {
            val resource = dao.findResource(resourceId.value)
                ?: return@withContext DomainResult.Failure(DomainFailure.ResourceNotFound)
            val stored = resource.toStoredResource(directory)
            if (!File(stored.absolutePath).isFile) {
                DomainResult.Failure(DomainFailure.ResourceNotFound)
            } else {
                DomainResult.Success(stored)
            }
        } catch (exception: SQLiteException) {
            DomainResult.Failure(DomainFailure.DatabaseUnavailable)
        }
    }

    override suspend fun pruneUnreferenced(): DomainResult<Unit> = withContext(ioDispatcher) {
        try {
            val removed = mutableListOf<ResourceEntity>()
            database.withTransaction {
                dao.findUnreferencedResources().forEach { resource ->
                    if (dao.deleteUnreferencedResource(resource.id) != 0) {
                        removed += resource
                    }
                }
            }
            removed.forEach { resource -> File(directory, resource.relativePath).delete() }
            DomainResult.Success(Unit)
        } catch (exception: SQLiteException) {
            DomainResult.Failure(DomainFailure.DatabaseUnavailable)
        }
    }
}

private fun ResourceEntity.toStoredResource(directory: File): StoredResource = StoredResource(
    id = ResourceId(id),
    sha256 = sha256,
    mimeType = mimeType,
    absolutePath = File(directory, relativePath).absolutePath,
    byteSize = byteSize,
)

private fun String.isSupportedBackground(): Boolean = startsWith("image/") || this == PDF_MIME_TYPE

private fun String.fileExtension(): String = when (lowercase()) {
    "image/png" -> "png"
    "image/jpeg" -> "jpg"
    "image/webp" -> "webp"
    "image/avif" -> "avif"
    PDF_MIME_TYPE -> "pdf"
    else -> "image"
}

private const val PDF_MIME_TYPE = "application/pdf"
