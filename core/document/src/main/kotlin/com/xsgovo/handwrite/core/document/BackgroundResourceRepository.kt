package com.xsgovo.handwrite.core.document

import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.ResourceId
import java.io.InputStream

data class StoredResource(
    val id: ResourceId,
    val sha256: String,
    val mimeType: String,
    val absolutePath: String,
    val byteSize: Long,
)

fun interface ResourceInput {
    fun open(): InputStream
}

interface BackgroundResourceRepository {
    suspend fun import(
        mimeType: String,
        input: ResourceInput,
    ): DomainResult<StoredResource>

    suspend fun find(resourceId: ResourceId): DomainResult<StoredResource>

    suspend fun pruneUnreferenced(): DomainResult<Unit>
}
