package org.koitharu.kotatsu.local.data.input

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.zip.ZipFile

internal class EpubParser(private val file: File) : AutoCloseable {

	private val zip = ZipFile(file)
	private val packagePath: String
	private val packageDocument: Document
	private val manifest: Map<String, Item>
	private val spine: List<String>

	val title: String
	val authors: List<String>
	val description: String?
	val coverHref: String?
	val chapters: List<Chapter>

	init {
		val container = readText("META-INF/container.xml")?.let {
			Jsoup.parse(it, "", Parser.xmlParser())
		} ?: throw IllegalArgumentException("Invalid EPUB: container.xml not found")
		packagePath = container.findFirstTag("rootfile")?.attr("full-path")
			?.takeIf { it.isNotBlank() }
			?: throw IllegalArgumentException("Invalid EPUB: package path not found")
		packageDocument = Jsoup.parse(
			readText(packagePath) ?: throw IllegalArgumentException("Invalid EPUB: $packagePath not found"),
			"",
			Parser.xmlParser(),
		)
		title = packageDocument.findFirstTag("dc:title", "title")?.text().orEmpty()
		authors = packageDocument.findAllTags("dc:creator", "creator")
			.map { it.text() }
			.filter { it.isNotBlank() }
		description = packageDocument.findFirstTag("dc:description", "description")
			?.text()
			?.takeIf { it.isNotBlank() }
		val items = packageDocument.findAllTags("item")
			.mapNotNull { item ->
				val id = item.attr("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
				val href = item.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
				id to Item(
					href = resolvePath(packagePath, href),
					mediaType = item.attr("media-type"),
					properties = item.attr("properties"),
				)
			}
		manifest = items.toMap()
		coverHref = manifest.values.firstOrNull { "cover-image" in it.properties.split(' ') }?.href
			?: packageDocument.findFirstTag("meta") { it.attr("name") == "cover" }
				?.attr("content")
				?.let { manifest[it]?.href }
		spine = packageDocument.findAllTags("itemref")
			.mapNotNull { it.attr("idref").takeIf { idref -> idref.isNotBlank() } }
		chapters = spine.mapNotNull { idref ->
			val item = manifest[idref] ?: return@mapNotNull null
			if (item.mediaType !in TEXT_TYPES) return@mapNotNull null
			Chapter(item.href)
		}
	}

	fun chapterTitle(href: String): String? {
		val text = readText(href) ?: return null
		val doc = Jsoup.parse(text)
		return doc.findFirstTag("title")?.text()?.takeIf { it.isNotBlank() }
			?: doc.selectFirst("h1, h2, h3")?.text()?.takeIf { it.isNotBlank() }
	}

	fun readText(href: String): String? {
		val entry = zip.getEntry(href)
			?: zip.getEntry(URLDecoder.decode(href, "UTF-8"))
			?: zip.entries().asSequence().firstOrNull { it.name.equals(href, ignoreCase = true) }
			?: return null
		return zip.getInputStream(entry).bufferedReader().use { it.readText() }
	}

	override fun close() = zip.close()

	data class Chapter(val href: String)

	private data class Item(
		val href: String,
		val mediaType: String,
		val properties: String,
	)

	companion object {

		private val TEXT_TYPES = setOf("application/xhtml+xml", "text/html", "application/html")

		fun extractText(html: String): String {
			val doc = Jsoup.parse(html)
			doc.select("script, style, svg, img, video, audio").remove()
			return doc.body().html()
		}

		private fun resolvePath(base: String, href: String): String = runCatching {
			URI(base).resolve(href).path.orEmpty()
		}.getOrDefault(href)
	}
}

private fun Document.findFirstTag(vararg names: String): Element? =
	getAllElements().firstOrNull { it.matchesTag(names) }

private fun Document.findFirstTag(name: String, predicate: (Element) -> Boolean): Element? =
	getAllElements().firstOrNull { it.matchesTag(arrayOf(name)) && predicate(it) }

private fun Document.findAllTags(vararg names: String): List<Element> =
	getAllElements().filter { it.matchesTag(names) }

private fun Element.matchesTag(names: Array<out String>): Boolean {
	val tag = tagName().substringAfter(':').lowercase()
	return names.any { tag == it.substringAfter(':').lowercase() }
}
