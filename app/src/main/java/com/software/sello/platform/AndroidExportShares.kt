package com.software.sello.platform

import android.content.Context
import androidx.core.content.FileProvider
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.ExportShares
import com.software.sello.domain.port.SharedExport
import java.io.File
import java.io.IOException
import kotlinx.coroutines.withContext

/**
 * Exports to share live in `cache/exports/`, the one folder the export provider serves
 * (`res/xml/export_paths.xml`); a share grants read access to that one file only. Each
 * share removes the earlier ones, and the app removes them all on start.
 */
class AndroidExportShares(
    private val context: Context,
    private val dispatchers: DispatcherProvider
) : ExportShares {
    private val folder get() = File(context.cacheDir, FOLDER)

    override suspend fun place(bytes: ByteArray, name: String, mimeType: String): SharedExport? =
        withContext(dispatchers.io) {
            emptyFolder()
            try {
                val file = File(folder.apply { mkdirs() }, name)
                // Only a name: a path could leave the folder.
                if (file.parentFile != folder) return@withContext null
                file.outputStream().use { stream ->
                    stream.write(bytes)
                    stream.flush()
                    stream.fd.sync()
                }
                val uri = FileProvider.getUriForFile(context, authority(context), file)
                SharedExport(uri.toString(), mimeType)
            } catch (_: IOException) {
                emptyFolder()
                null
            } catch (_: IllegalArgumentException) {
                emptyFolder()
                null
            }
        }

    override suspend fun clear() = withContext(dispatchers.io) { emptyFolder() }

    private fun emptyFolder() {
        folder.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val FOLDER = "exports"

        /** The export provider's authority, as the manifest declares it. */
        fun authority(context: Context) = "${context.packageName}.exports"
    }
}
