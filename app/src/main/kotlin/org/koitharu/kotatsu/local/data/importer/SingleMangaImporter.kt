package org.koitharu.kotatsu.local.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import org.koitharu.kotatsu.core.exceptions.UnsupportedFileException
import org.koitharu.kotatsu.core.util.ext.openSource
import org.koitharu.kotatsu.core.util.ext.resolveName
import org.koitharu.kotatsu.core.util.ext.writeAllCancellable
import org.koitharu.kotatsu.local.data.LocalStorageChanges
import org.koitharu.kotatsu.local.data.LocalStorageManager
import org.koitharu.kotatsu.local.data.hasZipExtension
import org.koitharu.kotatsu.local.data.input.LocalMangaParser
import org.koitharu.kotatsu.local.domain.model.LocalManga
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val PDF_PAGE_WIDTH = 1240

@Reusable
class SingleMangaImporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val storageManager: LocalStorageManager,
	@LocalStorageChanges private val localStorageChanges: MutableSharedFlow<LocalManga?>,
) {

	private val contentResolver = context.contentResolver

	suspend fun import(uri: Uri): LocalManga {
		val result = if (isDirectory(uri)) {
			importDirectory(uri)
		} else {
			importFile(uri)
		}
		localStorageChanges.emit(result)
		return result
	}

	private suspend fun importFile(uri: Uri): LocalManga = withContext(Dispatchers.IO) {
		val contentResolver = storageManager.contentResolver
		val name = contentResolver.resolveName(uri) ?: throw IOException("Cannot fetch name from uri: $uri")
		when {
			name.endsWith(".pdf", ignoreCase = true) -> importPdf(uri, name)
			hasZipExtension(name) -> importArchive(uri, name)
			else -> throw UnsupportedFileException("Unsupported file $name on $uri")
		}
	}

	private suspend fun importArchive(uri: Uri, name: String): LocalManga {
		val dest = File(getOutputDir(), name)
		runInterruptible {
			storageManager.contentResolver.openSource(uri)
		}.use { source ->
			dest.sink().buffer().use { output ->
				output.writeAllCancellable(source)
			}
		}
		return LocalMangaParser(dest).getManga(withDetails = false)
	}

	private suspend fun importPdf(uri: Uri, name: String): LocalManga {
		val dest = File(getOutputDir(), name.substringBeforeLast('.'))
		dest.mkdirs()
		runInterruptible(Dispatchers.IO) {
			val descriptor = storageManager.contentResolver.openFileDescriptor(uri, "r")
				?: throw IOException("Cannot open $uri")
			descriptor.use { pfd ->
				PdfRenderer(pfd).use { renderer ->
					for (i in 0 until renderer.pageCount) {
						renderer.openPage(i).use { page ->
							val width = PDF_PAGE_WIDTH
							val height = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
							val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
							bitmap.eraseColor(Color.WHITE)
							page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
							File(dest, "page_%04d.png".format(i + 1)).outputStream().use { out ->
								bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
							}
							bitmap.recycle()
						}
					}
				}
			}
		}
		return LocalMangaParser(dest).getManga(withDetails = false)
	}

	private suspend fun importDirectory(uri: Uri): LocalManga {
		val root = requireNotNull(DocumentFile.fromTreeUri(context, uri)) {
			"Provided uri $uri is not a tree"
		}
		val dest = File(getOutputDir(), root.requireName())
		dest.mkdir()
		for (docFile in root.listFiles()) {
			docFile.copyTo(dest)
		}
		return LocalMangaParser(dest).getManga(withDetails = false)
	}

	private suspend fun DocumentFile.copyTo(destDir: File) {
		if (isDirectory) {
			val subDir = File(destDir, requireName())
			subDir.mkdir()
			for (docFile in listFiles()) {
				docFile.copyTo(subDir)
			}
		} else {
			source().use { input ->
				File(destDir, requireName()).sink().buffer().use { output ->
					output.writeAllCancellable(input)
				}
			}
		}
	}

	private suspend fun getOutputDir(): File {
		return storageManager.getDefaultWriteableDir() ?: throw IOException("External files dir unavailable")
	}

	private suspend fun DocumentFile.source() = runInterruptible(Dispatchers.IO) {
		contentResolver.openSource(uri)
	}

	private fun DocumentFile.requireName(): String {
		return name ?: throw IOException("Cannot fetch name from uri: $uri")
	}

	private fun isDirectory(uri: Uri): Boolean {
		return runCatching {
			DocumentFile.fromTreeUri(context, uri)
		}.isSuccess
	}
}
