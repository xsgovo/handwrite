package com.xsgovo.handwrite.feature.export

import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.document.ResourceInput
import com.xsgovo.handwrite.core.document.StoredResource
import com.xsgovo.handwrite.core.model.BackgroundAssetKind
import com.xsgovo.handwrite.core.model.BrushStyle
import com.xsgovo.handwrite.core.model.DisplayName
import com.xsgovo.handwrite.core.model.Document
import com.xsgovo.handwrite.core.model.DocumentId
import com.xsgovo.handwrite.core.model.DocumentSnapshot
import com.xsgovo.handwrite.core.model.DomainFailure
import com.xsgovo.handwrite.core.model.DomainResult
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.NameResult
import com.xsgovo.handwrite.core.model.Page
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.PageTemplate
import com.xsgovo.handwrite.core.model.ResourceId
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.model.StrokeSample
import com.xsgovo.handwrite.core.model.LogicalPoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePackageWriterTest {
    @Test
    fun packageContainsManifestPageAndDeduplicatedResource() = runTest {
        val resourceFile = File.createTempFile("handwrite-resource", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        val resource = StoredResource(ResourceId(7), "abc123", "image/png", resourceFile.absolutePath, 4)
        val output = ByteArrayOutputStream()

        NativePackageWriter(FakeResources(resource)).write(snapshot(resource.id), output)

        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("manifest.json", "pages/1.bin", "resources/abc123.png"), entries.keys)
        assertTrue(entries.getValue("manifest.json").decodeToString().contains("com.xsgovo.handwrite.package"))
        assertTrue(entries.getValue("pages/1.bin").isNotEmpty())
        assertEquals(listOf<Byte>(1, 2, 3, 4), entries.getValue("resources/abc123.png").toList())
    }

    private fun snapshot(resourceId: ResourceId): DocumentSnapshot {
        val documentId = DocumentId(1)
        val pageId = PageId(2)
        val name = (DisplayName.create("测试文档") as NameResult.Valid).name
        val page = Page(
            id = pageId,
            documentId = documentId,
            orderKey = 1_024,
            size = PageTemplate.LEGACY_PORTRAIT.size,
            background = PageBackground.Asset(resourceId, BackgroundAssetKind.IMAGE),
        )
        val stroke = StrokeElement(
            id = ElementId(3),
            pageId = pageId,
            orderKey = 1_024,
            style = BrushStyle(argb = 0xFF000000.toInt(), width = 100),
            samples = listOf(StrokeSample(LogicalPoint(10, 20))),
        )
        return DocumentSnapshot(
            document = Document(documentId, name, null, 1, 1, lastActivePageId = pageId),
            pages = listOf(PageContent(page, listOf(stroke))),
        )
    }

    private class FakeResources(private val resource: StoredResource) : BackgroundResourceRepository {
        override suspend fun import(mimeType: String, input: ResourceInput): DomainResult<StoredResource> =
            DomainResult.Failure(DomainFailure.InvalidResource)

        override suspend fun find(resourceId: ResourceId): DomainResult<StoredResource> =
            if (resourceId == resource.id) DomainResult.Success(resource) else DomainResult.Failure(DomainFailure.ResourceNotFound)

        override suspend fun pruneUnreferenced(): DomainResult<Unit> = DomainResult.Success(Unit)
    }
}
