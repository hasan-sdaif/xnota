package com.xnota.platform

import com.xnota.core.util.ExportZip.Entry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PdfBatchTest {

    private val scratchDir = Files.createTempDirectory("pdfbatch").toFile()
    private val cancel = AtomicBoolean(false)
    private val progress = ArrayList<PdfBatch.Progress>()

    @After
    fun cleanUp() {
        scratchDir.deleteRecursively()
    }

    /** The picked file, in memory: what was written, and whether it was thrown away. */
    private class Sink(private val failAfter: Int = Int.MAX_VALUE) : PdfBatch.Destination {
        val bytes = ByteArrayOutputStream()
        var opened = 0
        var closed = false
        var discarded = false

        override fun open(): OutputStream {
            opened++
            return object : OutputStream() {
                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (bytes.size() + len > failAfter) throw IOException("disk full")
                    bytes.write(b, off, len)
                }
                override fun close() { closed = true }
            }
        }

        override fun discard() { discarded = true }
    }

    private fun body(source: String) = "%PDF-1.7 $source ".repeat(400).toByteArray()

    private fun batch(render: (String, OutputStream, (Int, Int) -> Unit, () -> Boolean) -> Unit) =
        PdfBatch(scratchDir, render, { cancel.get() }) { progress.add(it) }

    private val renderAll: (String, OutputStream, (Int, Int) -> Unit, () -> Boolean) -> Unit = { source, out, onProgress, _ ->
        onProgress(0, 2)
        out.write(body(source))
        onProgress(2, 2)
    }

    private fun read(zip: ByteArray): List<Pair<ZipEntry, ByteArray>> {
        val out = ArrayList<Pair<ZipEntry, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out.add(e to z.readBytes()) // reading a stored entry to its end checks its CRC
            }
        }
        return out
    }

    private val plan = listOf(
        Entry("School/", null, "School"),
        Entry("School/Art/", null, "Art"),
        Entry("School/Algebra.pdf", "a", "Algebra.xnote"),
        Entry("Board.pdf", "b", "Board.xcanvas"),
    )

    @Test
    fun `a zip holds every document stored as it rendered, with its folders`() {
        val sink = Sink()
        val result = batch(renderAll).zip(plan, sink)
        assertTrue(result.kept)
        assertEquals(2, result.found)
        assertEquals(2, result.written)
        assertFalse(sink.discarded)
        assertTrue(sink.closed)
        val entries = read(sink.bytes.toByteArray())
        assertEquals(listOf("School/", "School/Art/", "School/Algebra.pdf", "Board.pdf"), entries.map { it.first.name })
        assertTrue(entries.all { it.first.method == ZipEntry.STORED })
        assertArrayEquals(body("a"), entries[2].second)
        assertArrayEquals(body("b"), entries[3].second)
        assertEquals(0, scratchDir.listFiles()!!.size)
    }

    @Test
    fun `a document that fails is left out and the rest are kept`() {
        val render: (String, OutputStream, (Int, Int) -> Unit, () -> Boolean) -> Unit = { source, out, p, c ->
            when (source) {
                "a" -> throw OutOfMemoryError("too big")
                "c" -> Unit // a document that could not be read writes nothing
                else -> renderAll(source, out, p, c)
            }
        }
        val sink = Sink()
        val result = batch(render).zip(plan + Entry("Empty.pdf", "c", "Empty.xnote") + Entry("Last/", null, "Last"), sink)
        assertTrue(result.kept)
        assertEquals(3, result.found)
        assertEquals(1, result.written)
        assertEquals(listOf("School/", "School/Art/", "Board.pdf", "Last/"), read(sink.bytes.toByteArray()).map { it.first.name })
    }

    @Test
    fun `a cancel part-way discards the destination and the scratch file`() {
        val render: (String, OutputStream, (Int, Int) -> Unit, () -> Boolean) -> Unit = { source, out, p, c ->
            if (source == "b") {
                out.write(body(source))
                cancel.set(true)
                out.write(body(source)) // the cancelled stream throws here
            }
            renderAll(source, out, p, c)
        }
        val sink = Sink()
        val result = batch(render).zip(plan + Entry("Later.pdf", "c", "Later.xnote"), sink)
        assertTrue(result.cancelled)
        assertFalse(result.kept)
        assertTrue(sink.discarded)
        assertTrue(sink.closed)
        assertEquals(0, scratchDir.listFiles()!!.size)
        assertTrue(progress.none { it.name == "Later.xnote" })
    }

    @Test
    fun `folders without documents leave nothing behind`() {
        val sink = Sink()
        val result = batch(renderAll).zip(plan.filter { it.source == null }, sink)
        assertEquals(0, result.found)
        assertFalse(result.kept)
        assertEquals(0, sink.opened)
        assertTrue(sink.discarded)
    }

    @Test
    fun `a batch where every document fails is discarded`() {
        val sink = Sink()
        val result = batch { _, _, _, _ -> throw IllegalStateException() }.zip(plan, sink)
        assertEquals(2, result.found)
        assertEquals(0, result.written)
        assertFalse(result.kept)
        assertEquals(0, sink.opened)
        assertTrue(sink.discarded)
    }

    @Test
    fun `a destination that fails is discarded`() {
        val sink = Sink(failAfter = 2000)
        val result = batch(renderAll).zip(plan, sink)
        assertFalse(result.kept)
        assertFalse(result.cancelled)
        assertEquals(0, result.written)
        assertTrue(sink.discarded)
        assertTrue(sink.closed)
        assertEquals(0, scratchDir.listFiles()!!.size)
    }

    @Test
    fun `a lone document is written as it rendered`() {
        val sink = Sink()
        val result = batch(renderAll).single("a", "Algebra.xnote", sink)
        assertTrue(result.kept)
        assertEquals(1, result.written)
        assertArrayEquals(body("a"), sink.bytes.toByteArray())
        assertEquals(0, scratchDir.listFiles()!!.size)
    }

    @Test
    fun `a lone document that fails is discarded`() {
        val sink = Sink()
        val result = batch { _, _, _, _ -> throw IOException() }.single("a", "Algebra.xnote", sink)
        assertFalse(result.kept)
        assertFalse(result.cancelled)
        assertEquals(0, sink.opened)
        assertTrue(sink.discarded)
    }

    @Test
    fun `progress walks the documents and only ever grows`() {
        batch(renderAll).zip(plan, Sink())
        assertEquals(listOf("Algebra.xnote", "Board.xcanvas"), progress.map { it.name }.distinct())
        assertTrue(progress.all { it.count == 2 })
        val fractions = progress.map { it.fraction }
        assertEquals(fractions.sorted(), fractions)
        assertEquals(0.75f, fractions.last(), 1e-6f)
        assertEquals(0f, PdfBatch.Progress.GATHERING.fraction, 0f)
        assertEquals(0.875f, PdfBatch.Progress(1, 2, "", 500, -1).fraction, 1e-6f)
    }
}
