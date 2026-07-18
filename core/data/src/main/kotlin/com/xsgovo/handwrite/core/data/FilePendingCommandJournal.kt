package com.xsgovo.handwrite.core.data

import com.xsgovo.handwrite.core.data.codec.PendingCommandCodec
import com.xsgovo.handwrite.core.document.PendingCommand
import com.xsgovo.handwrite.core.document.PendingCommandJournal
import com.xsgovo.handwrite.core.model.DomainFailure
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.OperationId
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class FilePendingCommandJournal(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher,
    initialSequence: Long = System.currentTimeMillis(),
) : PendingCommandJournal {
    private val nextSequence = AtomicLong(initialSequence)

    override suspend fun append(command: PendingCommand): DomainResult<Unit> = guardedIo {
        directory.mkdirsOrThrow()
        val sequence = nextSequence.updateAndGet { previous -> maxOf(previous + 1, System.currentTimeMillis()) }
        val destination = File(directory, "%019d-%s.pb".format(sequence, command.operationId.fileKey()))
        val temporary = File(directory, destination.name + ".tmp")
        temporary.outputStream().use { output ->
            output.write(PendingCommandCodec.encode(command, sequence))
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override suspend fun readAll(): DomainResult<List<PendingCommand>> = guardedIo {
        if (!directory.exists()) return@guardedIo emptyList()
        directory.listFiles { file -> file.extension == "pb" }
            .orEmpty()
            .map { PendingCommandCodec.decode(it.readBytes()) }
            .sortedBy { it.sequence }
            .map { it.command }
    }

    override suspend fun remove(operationId: OperationId): DomainResult<Unit> = guardedIo {
        if (!directory.exists()) return@guardedIo
        directory.listFiles { file -> file.extension == "pb" && file.name.endsWith("-${operationId.fileKey()}.pb") }
            .orEmpty()
            .forEach { file ->
                if (!file.delete() && file.exists()) throw IOException("Cannot remove pending journal entry")
            }
    }

    private suspend fun <T> guardedIo(block: () -> T): DomainResult<T> = try {
        withContext(ioDispatcher) { DomainResult.Success(block()) }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        DomainResult.Failure(DomainFailure.DatabaseUnavailable)
    }
}

private fun File.mkdirsOrThrow() {
    if (!exists() && !mkdirs()) throw IOException("Cannot create pending journal directory")
}

private fun OperationId.fileKey(): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
