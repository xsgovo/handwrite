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

/**
 * 背景资源（图片/PDF）的导入与查找契约。实现为内容寻址：同字节内容即同资源（按哈希去重）；
 * 导入后的引用计数由文档写入方（页面背景的增删）负责增减。
 */
interface BackgroundResourceRepository {
    suspend fun import(
        mimeType: String,
        input: ResourceInput,
    ): DomainResult<StoredResource>

    suspend fun find(resourceId: ResourceId): DomainResult<StoredResource>

    suspend fun pruneUnreferenced(): DomainResult<Unit>
}
