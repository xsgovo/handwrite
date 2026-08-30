package com.xsgovo.handwrite.feature.export

import com.xsgovo.handwrite.core.document.BackgroundResourceRepository
import com.xsgovo.handwrite.core.document.ResourceInput
import com.xsgovo.handwrite.core.document.StoredResource
import com.xsgovo.handwrite.core.model.BackgroundAssetKind
import com.xsgovo.handwrite.core.model.BrushBlendMode
import com.xsgovo.handwrite.core.model.BrushId
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
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.ResourceId
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.model.StrokeSample
import com.xsgovo.handwrite.core.model.LogicalPoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePackageWriterTest {
    @Test
    fun packageContainsManifestPageAndDeduplicatedResource() = runTest {
        val resource = tempResource(ResourceId(7), "abc123")
        val output = ByteArrayOutputStream()

        NativePackageWriter(FakeResources(resource)).write(snapshot(resource.id), output)

        val entries = readZipEntries(output.toByteArray())
        assertEquals(setOf("manifest.json", "pages/1.bin", "resources/abc123.png"), entries.keys)
        assertTrue(entries.getValue("manifest.json").decodeToString().contains("com.xsgovo.handwrite.package"))
        assertTrue(entries.getValue("pages/1.bin").isNotEmpty())
        assertEquals(listOf<Byte>(1, 2, 3, 4), entries.getValue("resources/abc123.png").toList())
    }

    @Test
    fun exportedPackageDecodesBackToTheWrittenStrokes() = runTest {
        val resource = tempResource(ResourceId(3), "def456")
        val strokes = listOf(
            StrokeElement(
                id = ElementId(11),
                pageId = PageId(2),
                orderKey = 2_048L,
                style = BrushStyle(
                    id = BrushId.PRESSURE_PEN,
                    argb = 0x80123456.toInt(),
                    width = 42,
                    blendMode = BrushBlendMode.HIGHLIGHT,
                    pressureSensitivity = PressureSensitivity.HIGH,
                ),
                samples = listOf(
                    StrokeSample(
                        LogicalPoint(100, 200),
                        pressure = 10_000,
                        elapsedMillis = 0,
                        tiltX = -120,
                        tiltY = 340,
                    ),
                    StrokeSample(LogicalPoint(130, 180), pressure = StrokeSample.MAX_PRESSURE, elapsedMillis = 250),
                ),
            ),
            StrokeElement(
                id = ElementId(12),
                pageId = PageId(2),
                orderKey = 4_096L,
                style = BrushStyle(argb = 0xFF000000.toInt(), width = 3),
                samples = listOf(StrokeSample(LogicalPoint(0, 0))),
            ),
        )
        val output = ByteArrayOutputStream()

        NativePackageWriter(FakeResources(resource)).write(snapshot(resource.id, strokes), output)

        val entries = readZipEntries(output.toByteArray())
        val manifest = Json.parseToJsonElement(entries.getValue("manifest.json").decodeToString()).jsonObject
        assertEquals("com.xsgovo.handwrite.package", manifest.getValue("format").jsonPrimitive.content)
        assertEquals(1, manifest.getValue("version").jsonPrimitive.int)
        assertEquals("测试文档", manifest.getValue("name").jsonPrimitive.content)
        val pageManifest = manifest.getValue("pages").jsonArray.single().jsonObject
        assertEquals("pages/1.bin", pageManifest.getValue("file").jsonPrimitive.content)
        val background = pageManifest.getValue("background").jsonObject
        assertEquals("asset", background.getValue("type").jsonPrimitive.content)
        assertEquals("def456", background.getValue("sha256").jsonPrimitive.content)

        val decoded = DataInputStream(ByteArrayInputStream(entries.getValue("pages/1.bin"))).use { input ->
            assertEquals(0x48575047, input.readInt())
            assertEquals(1, input.readInt())
            List(input.readInt()) { input.readExportedStroke() }
        }

        assertEquals(strokes.map(StrokeElement::orderKey), decoded.map(ExportedStroke::orderKey))
        assertEquals(strokes.map(StrokeElement::style), decoded.map(ExportedStroke::style))
        assertEquals(strokes.map(StrokeElement::samples), decoded.map(ExportedStroke::samples))
    }

    private fun tempResource(resourceId: ResourceId, sha256: String): StoredResource {
        val file = File.createTempFile("handwrite-resource", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        return StoredResource(resourceId, sha256, "image/png", file.absolutePath, 4)
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    // Decodes the export page format independently of the writer to pin the binary contract.
    private fun DataInputStream.readExportedStroke(): ExportedStroke {
        val orderKey = readLong()
        val style = BrushStyle(
            id = BrushId(readUTF()),
            argb = readInt(),
            width = readInt(),
            blendMode = BrushBlendMode.entries[readByte().toInt()],
            pressureSensitivity = PressureSensitivity.entries[readByte().toInt()],
        )
        var previousX = 0
        var previousY = 0
        val samples = List(readInt()) {
            val x = previousX + readInt()
            val y = previousY + readInt()
            val pressure = readUnsignedShort()
            val elapsedMillis = readInt()
            val tiltX = readShort().toInt().takeUnless { it == Short.MIN_VALUE.toInt() }
            val tiltY = readShort().toInt().takeUnless { it == Short.MIN_VALUE.toInt() }
            previousX = x
            previousY = y
            StrokeSample(LogicalPoint(x, y), pressure, elapsedMillis, tiltX, tiltY)
        }
        return ExportedStroke(orderKey, style, samples)
    }

    private data class ExportedStroke(
        val orderKey: Long,
        val style: BrushStyle,
        val samples: List<StrokeSample>,
    )

    private fun snapshot(resourceId: ResourceId, strokes: List<StrokeElement> = emptyList()): DocumentSnapshot {
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
        val elements = strokes.ifEmpty {
            listOf(
                StrokeElement(
                    id = ElementId(3),
                    pageId = pageId,
                    orderKey = 1_024,
                    style = BrushStyle(argb = 0xFF000000.toInt(), width = 100),
                    samples = listOf(StrokeSample(LogicalPoint(10, 20))),
                ),
            )
        }
        return DocumentSnapshot(
            document = Document(documentId, name, 1, 1, lastActivePageId = pageId),
            pages = listOf(PageContent(page, elements)),
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
