package com.xnota.platform

import com.xnota.core.util.ExportZip
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Wraps a PDF export's output stream and throws the instant [cancelled] turns true, so PdfBox's
 * otherwise-uninterruptible `save()` (which writes the whole document in one call) aborts promptly
 * when the user taps Cancel, instead of running to completion on a background thread.
 */
class CancellableOutputStream(
    private val out: OutputStream,
    private val cancelled: () -> Boolean,
) : OutputStream() {
    override fun write(b: Int) { if (cancelled()) throw InterruptedIOException(); out.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (cancelled()) throw InterruptedIOException()
        out.write(b, off, len)
    }
    override fun flush() { out.flush() }
    override fun close() { out.close() }
}

/**
 * Writes a batch PDF export into the file the user picked, a zip or a lone PDF. Each document renders whole into a scratch
 * file first, so one that fails leaves no broken entry, and only one is ever loaded at a time however long the batch.
 */
class PdfBatch(
    private val scratchDir: File,
    /** Writes [source] as a PDF to the stream, reporting its own progress and polling for a cancel. */
    private val render: (source: String, out: OutputStream, onProgress: (Int, Int) -> Unit, isCancelled: () -> Boolean) -> Unit,
    private val isCancelled: () -> Boolean,
    private val onProgress: (Progress) -> Unit,
) {
    /** The picked file: opened once a document is ready for it, and thrown away when the export does not keep it. */
    interface Destination {
        fun open(): OutputStream
        fun discard()
    }

    /** Document [index] of [count] (0 while gathering), called [name], at [done] of [total] in its render; a negative [total] marks its write, in permille. */
    class Progress(val index: Int, val count: Int, val name: String, val done: Int, val total: Int) {
        /** The share of the whole batch done, a document's pages and its final write counting half each. */
        val fraction: Float
            get() {
                if (count <= 0) return 0f
                val within = when {
                    total > 0 -> 0.5f * done / total
                    total < 0 -> 0.5f + 0.5f * done / 1000f
                    else -> 0f
                }
                return ((index + within.coerceIn(0f, 1f)) / count).coerceIn(0f, 1f)
            }

        companion object {
            val GATHERING = Progress(0, 0, "", 0, 0)
        }
    }

    /** How a batch ended: of the [found] documents, [written] made it into the destination, and [kept] says whether it was kept. */
    class Result(val found: Int, val written: Int, val cancelled: Boolean, val kept: Boolean)

    /** Every document of [entries] as a PDF in a zip written to [dest], with the folders between them. */
    fun zip(entries: List<ExportZip.Entry>, dest: Destination): Result {
        val count = entries.count { it.source != null }
        var written = 0
        var kept = false
        var scratch: File? = null
        var raw: OutputStream? = null
        try {
            if (count > 0) {
                scratchDir.mkdirs()
                val tmp = File.createTempFile("batch", ".pdf", scratchDir).also { scratch = it }
                var zip: ZipOutputStream? = null
                // Folders wait for a document to follow them, so a batch that writes nothing never touches the destination.
                val folders = ArrayList<String>()
                var index = 0
                for (e in entries) {
                    if (isCancelled()) break
                    val source = e.source
                    if (source == null) {
                        folders.add(e.path)
                        continue
                    }
                    val crc = renderTo(tmp, source, index++, count, e.name) ?: continue
                    val z = zip ?: ZipOutputStream(BufferedOutputStream(dest.open().also { raw = it }, BUFFER)).also { zip = it }
                    folders.forEach { z.putNextEntry(stored(it, 0, 0)); z.closeEntry() }
                    folders.clear()
                    // Not cancellable part-way: a zip can only be closed between entries, and one copy is quick.
                    z.putNextEntry(stored(e.path, tmp.length(), crc))
                    tmp.inputStream().use { it.copyTo(z, BUFFER) }
                    z.closeEntry()
                    written++
                }
                zip?.let { z ->
                    folders.forEach { z.putNextEntry(stored(it, 0, 0)); z.closeEntry() }
                    z.close()
                }
                kept = written > 0 && !isCancelled()
            }
        } catch (_: Throwable) {
            // The destination failed, so nothing in it can be trusted; whatever it holds goes.
            kept = false
        } finally {
            runCatching { raw?.close() }
            scratch?.delete()
            if (!kept) runCatching { dest.discard() }
        }
        return Result(count, if (kept) written else 0, isCancelled(), kept)
    }

    /** The document [source], called [name], as a lone PDF written to [dest]. */
    fun single(source: String, name: String, dest: Destination): Result {
        var kept = false
        var scratch: File? = null
        try {
            scratchDir.mkdirs()
            val tmp = File.createTempFile("batch", ".pdf", scratchDir).also { scratch = it }
            if (renderTo(tmp, source, 0, 1, name) != null) {
                dest.open().use { out -> tmp.inputStream().use { it.copyTo(out, BUFFER) } }
                kept = !isCancelled()
            }
        } catch (_: Throwable) {
            kept = false
        } finally {
            scratch?.delete()
            if (!kept) runCatching { dest.discard() }
        }
        return Result(1, if (kept) 1 else 0, isCancelled(), kept)
    }

    /** Renders [source] into [scratch] as document [index] of [count]; the CRC-32 of what landed, or null when it failed, wrote nothing or was cancelled. */
    private fun renderTo(scratch: File, source: String, index: Int, count: Int, name: String): Long? {
        onProgress(Progress(index, count, name, 0, 0))
        val crc = CRC32()
        // A failure, out of memory included, takes down only its own document, all of which is unreachable once it unwinds.
        val ok = runCatching {
            FileOutputStream(scratch).use { fo ->
                val out = CancellableOutputStream(CheckedOutputStream(fo, crc), isCancelled)
                render(source, out, { done, total -> onProgress(Progress(index, count, name, done, total)) }, isCancelled)
            }
        }.isSuccess
        return if (ok && !isCancelled() && scratch.length() > 0) crc.value else null
    }

    /** A zip entry kept as it is: PDFs are compressed inside already, so deflating them again buys little and costs time. */
    private fun stored(path: String, size: Long, crc: Long) = ZipEntry(path).apply {
        method = ZipEntry.STORED
        this.size = size
        compressedSize = size
        this.crc = crc
    }

    private companion object {
        const val BUFFER = 1 shl 16
    }
}
