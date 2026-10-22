package com.xnota.platform

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import kotlin.math.abs

/**
 * Joins a second PDF onto the end of a note's source PDF, so a note embeds one PDF however many it
 * took in. Appending keeps every page already in the source at its index, which is what the note's
 * pages, markups and the Contents tab point at.
 */
object PdfJoin {

    /** Cap on PdfBox's in-RAM scratch while joining; the rest spills to temp files. */
    private const val SCRATCH_MAIN_MEM_BYTES = 32L * 1024 * 1024

    /** Write buffer between PdfBox and the output file. */
    private const val WRITE_BUFFER = 1 shl 16

    /** How far two reads of one page's size may differ, in points, and still be the same page. */
    private const val SIZE_SLACK = 0.01f

    /**
     * Writes [base]'s pages then [extra]'s to [out], with their bookmarks and form fields merged.
     * True only when PDFium reads [out] back as [baseSizes] then [extraSizes] (width, height pairs
     * in points), so no page of [base] moved. [isCancelled] fails the write part way. IO.
     */
    fun append(
        context: Context,
        base: File,
        extra: File,
        out: File,
        baseSizes: FloatArray,
        extraSizes: FloatArray,
        isCancelled: () -> Boolean,
    ): Boolean {
        PDFBoxResourceLoader.init(context.applicationContext)
        val mem = MemoryUsageSetting.setupMixed(SCRATCH_MAIN_MEM_BYTES).setTempDir(context.cacheDir)
        val written = runCatching {
            PDDocument.load(base, mem).use { dst ->
                PDDocument.load(extra, mem).use { src ->
                    PDFMergerUtility().apply { isIgnoreAcroFormErrors = true }.appendDocument(dst, src)
                    dst.isAllSecurityToBeRemoved = true
                    FileOutputStream(out).use { dst.save(Cancellable(BufferedOutputStream(it, WRITE_BUFFER), isCancelled)) }
                }
            }
            // The in-place save tells a new source from the old by its size alone.
            if (out.length() == base.length()) out.appendBytes(byteArrayOf('\n'.code.toByte()))
        }.isSuccess
        return written && !isCancelled() && readsAs(out, baseSizes + extraSizes)
    }

    /** True when PDFium reads [file] as exactly the pages sized [sizes]. */
    private fun readsAs(file: File, sizes: FloatArray): Boolean {
        val source = PdfSource.open(file)
        try {
            val got = source.allPageSizePoints() ?: return false
            return got.size == sizes.size && got.indices.all { abs(got[it] - sizes[it]) <= SIZE_SLACK }
        } finally {
            source.close()
        }
    }

    /** Fails the write at its next token once [isCancelled]. */
    private class Cancellable(private val out: OutputStream, private val isCancelled: () -> Boolean) : OutputStream() {
        override fun write(b: Int) { stopIfCancelled(); out.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { stopIfCancelled(); out.write(b, off, len) }
        override fun flush() { out.flush() }
        override fun close() { out.close() }
        private fun stopIfCancelled() { if (isCancelled()) throw InterruptedIOException() }
    }
}
