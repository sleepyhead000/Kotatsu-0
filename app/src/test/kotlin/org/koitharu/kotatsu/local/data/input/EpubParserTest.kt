package org.koitharu.kotatsu.local.data.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

	@Test
	fun parsesMetadataSpineAndText() {
		val file = File.createTempFile("sample", ".epub").apply { deleteOnExit() }
		ZipOutputStream(file.outputStream()).use { zip ->
			zip.putNextEntry(ZipEntry("META-INF/container.xml"))
			zip.write(
				"""<?xml version="1.0"?>
				<container><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
					.toByteArray(),
			)
			zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
			zip.write(
				"""<?xml version="1.0" encoding="utf-8"?>
				<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
					<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
						<dc:title>Sample Book</dc:title>
						<dc:creator>Jane Doe</dc:creator>
						<dc:description>A sample.</dc:description>
					</metadata>
					<manifest>
						<item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
						<item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
					</manifest>
					<spine>
						<itemref idref="ch1"/>
						<itemref idref="ch2"/>
					</spine>
				</package>""".toByteArray(),
			)
			zip.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
			zip.write("<html><head><title>Chapter One</title></head><body><p>Hello</p></body></html>".toByteArray())
			zip.putNextEntry(ZipEntry("OEBPS/ch2.xhtml"))
			zip.write("<html><head><title>Chapter Two</title></head><body><p>World</p></body></html>".toByteArray())
		}

		EpubParser(file).use { epub ->
			assertEquals("Sample Book", epub.title)
			assertEquals(listOf("Jane Doe"), epub.authors)
			assertEquals(listOf("OEBPS/ch1.xhtml", "OEBPS/ch2.xhtml"), epub.chapters.map { it.href })
			assertEquals("Chapter One", epub.chapterTitle("OEBPS/ch1.xhtml"))
			val text = EpubParser.extractText(epub.readText("OEBPS/ch1.xhtml")!!)
			assertTrue(text.contains("Hello"))
		}
	}
}
