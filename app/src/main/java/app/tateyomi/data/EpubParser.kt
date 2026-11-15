package app.tateyomi.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

class EpubException(message: String) : Exception(message)

private const val MAX_TEXT_BYTES = 32L * 1024 * 1024
private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
private const val MAX_TOTAL_IMAGE_BYTES = 320L * 1024 * 1024

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")

private fun Element.localName(): String = tagName().substringAfterLast(':').lowercase()

private fun Element.childElements(name: String): List<Element> =
    children().filter { it.localName() == name }

private fun Element.elementsNamed(name: String): List<Element> =
    allElements.filter { it.localName() == name }

private fun imageExtension(path: String, bytes: ByteArray): String? {
    val ext = path.substringAfterLast('.', "").lowercase()
    if (ext in IMAGE_EXTENSIONS) return if (ext == "jpeg") "jpg" else ext
    return sniffImage(bytes)
}

private fun sniffImage(bytes: ByteArray): String? {
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
    if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
    ) return "png"
    if (bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte()
    ) return "gif"
    if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
    ) return "webp"
    return null
}

private fun digest(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(bytes).take(12).joinToString("") { "%02x".format(it) }
}

/**
 * The result of reading an EPUB. Image bytes are returned separately and the
 * repository decides where to put them; this keeps the parser free of Android
 * APIs so it can be unit-tested on the JVM.
 */
data class ParsedEpub(
    val title: String,
    val author: String,
    val language: String = "",
    val chapters: List<Chapter>,
    val toc: List<TocEntry>,
    val charCount: Int,
    val coverBytes: ByteArray? = null,
    val coverExtension: String? = null,
    val images: Map<String, ByteArray> = emptyMap(),
)

/**
 * A deliberately small, text-first EPUB 2/3 parser aimed at reflowable books.
 *
 * The renderer uses its own typographic grid, so publisher CSS is not needed.
 * We keep semantic structure (headings, paragraphs, ruby, emphasis, images)
 * and discard scripts, navigation and presentational styling.
 */
object EpubParser {

    fun parse(file: File): ParsedEpub {
        if (!file.isFile) throw EpubException("ファイルが見つかりません")
        return try {
            ZipFile(file).use { zip -> parseZip(zip, file.nameWithoutExtension) }
        } catch (e: EpubException) {
            throw e
        } catch (e: Exception) {
            throw EpubException("EPUBを読み込めませんでした（${e.message ?: "破損している可能性があります"}）")
        }
    }

    private fun parseZip(zip: ZipFile, fallbackTitle: String): ParsedEpub {
        val containerBytes = readEntry(zip, "META-INF/container.xml", 1024 * 1024)
            ?: throw EpubException("EPUBのcontainer.xmlが見つかりません")
        val container = parseXml(containerBytes)
        val opfPath = container.allElements.firstOrNull { it.localName() == "rootfile" }
            ?.attr("full-path")
            ?: throw EpubException("OPFファイルが指定されていません")
        val opfBytes = readEntry(zip, opfPath, MAX_TEXT_BYTES)
            ?: throw EpubException("OPFファイルが見つかりません")
        val opf = parseXml(opfBytes)

        val metadata = opf.allElements.firstOrNull { it.localName() == "metadata" } ?: opf
        val title = firstText(metadata, "title")
            .ifBlank { fallbackTitle.ifBlank { "無題の本" } }
        val authors = metadata.elementsNamed("creator")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        val author = authors.joinToString("、")
        val language = firstText(metadata, "language")

        val manifest = linkedMapOf<String, ManifestItem>()
        val manifestElement = opf.allElements.firstOrNull { it.localName() == "manifest" }
        manifestElement?.childElements("item")?.forEach { item ->
            val id = item.attr("id")
            val href = item.attr("href")
            if (id.isBlank() || href.isBlank()) return@forEach
            val path = resolvePath(opfPath, href) ?: return@forEach
            manifest[id] = ManifestItem(
                id = id,
                href = href,
                mediaType = item.attr("media-type"),
                properties = item.attr("properties"),
                path = path,
                fallback = item.attr("fallback"),
            )
        }

        val spineElement = opf.allElements.firstOrNull { it.localName() == "spine" }
        val itemrefs = spineElement?.childElements("itemref").orEmpty()
        val linearRefs = itemrefs.filter { it.attr("linear").lowercase() != "no" }
        val spineRefs = linearRefs.ifEmpty { itemrefs }
        val spine = spineRefs.mapNotNull { ref ->
            val item = manifest[ref.attr("idref")] ?: return@mapNotNull null
            if (isMarkup(item) || isSupportedImage(item)) item else null
        }
        if (spine.isEmpty()) throw EpubException("本文（spine）が見つかりません")

        val fixedLayout = metadata.elementsNamed("meta").any {
            it.attr("property") == "rendition:layout" && it.text().trim() == "pre-paginated"
        }

        val rawToc = readToc(zip, opf, manifest, opfPath)
        val pathToIndex = spine.mapIndexed { index, item -> item.path to index }.toMap()

        val images = linkedMapOf<String, ByteArray>()
        val imageNames = mutableMapOf<String, String>()
        var imageBytes = 0L
        val parsedChapters = ArrayList<Chapter>(spine.size)
        val chapterPaths = ArrayList<String>(spine.size)

        spine.forEachIndexed { index, item ->
            val extractor = ChapterExtractor(
                chapterPath = item.path,
                zip = zip,
                images = images,
                imageNames = imageNames,
                imageByteBudget = { imageBytes },
                onImageBytes = { imageBytes += it },
            )
            val chapter = if (isMarkup(item)) {
                val bytes = readEntry(zip, item.path, MAX_TEXT_BYTES) ?: ByteArray(0)
                extractor.extract(bytes)
            } else {
                extractor.extractImageOnly(item.path)
            }
            val tocEntriesForItem = rawToc.filter { tocPathMatches(it.path, item.path) }
            val anchoredToc = tocEntriesForItem.mapNotNull { raw ->
                if (raw.fragment.isBlank()) return@mapNotNull null
                val blockIndex = chapter.blocks.indexOfFirst { it.anchorId() == raw.fragment }
                if (blockIndex < 0) null else Triple(blockIndex, raw, (chapter.blocks[blockIndex] as? TextBlock)?.text)
            }.minByOrNull { it.first }
            val tocTitle = anchoredToc?.let {
                chooseTocTitle(it.second.title, it.third, "")
            } ?: tocEntriesForItem.firstOrNull()?.title
            val headingTitle = chapter.blocks
                .filterIsInstance<TextBlock>()
                .firstOrNull { it.style == BlockStyle.HEADING || it.style == BlockStyle.SUBHEADING }
                ?.text
                ?.take(80)
            val finalTitle = chooseChapterTitle(tocTitle, headingTitle)
                .ifBlank { "第${index + 1}章" }
            parsedChapters += chapter.copy(title = finalTitle)
            chapterPaths += item.path
        }

        // Cover pages and other empty scaffolding only produce a blank page in
        // our own renderer. Keep image-only pages (fixed layout manga etc.).
        val keptIndices = parsedChapters.indices.filter { index ->
            val chapter = parsedChapters[index]
            chapter.charCount > 0 || chapter.blocks.any { it is ImageBlock }
        }
        if (keptIndices.isEmpty()) throw EpubException("本文が見つかりません")
        val chapters = keptIndices.map { parsedChapters[it] }
        val oldToNew = keptIndices.withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }

        val toc = rawToc.mapNotNull { raw ->
            val oldIndex = pathToIndex[raw.path]
                ?: chapterPaths.indexOfFirst { tocPathMatches(raw.path, it) }
            val newIndex = oldToNew[oldIndex] ?: return@mapNotNull null
            val blocks = chapters[newIndex].blocks
            val blockIndex = if (raw.fragment.isNotBlank()) {
                blocks.indexOfFirst { block -> block.anchorId() == raw.fragment }.takeIf { it >= 0 }
            } else {
                null
            }
            val blockText = (blockIndex?.let { blocks.getOrNull(it) } as? TextBlock)?.text
            val title = chooseTocTitle(raw.title, blockText, chapters[newIndex].title)
            TocEntry(title, newIndex, raw.level, blockIndex)
        }.filter { it.title.isNotBlank() }
            .distinctBy { Triple(it.chapterIndex, it.blockIndex, it.title) }

        val effectiveToc = if (toc.isEmpty()) {
            chapters.mapIndexed { index, chapter -> TocEntry(chapter.title, index, 0) }
        } else {
            toc
        }

        val charCount = chapters.sumOf { it.charCount }
        val cover = findCover(zip, opf, manifest, opfPath)
            ?: if (fixedLayout) firstSpineImageCover(zip, spine) else null

        return ParsedEpub(
            title = title,
            author = author,
            language = language,
            chapters = chapters,
            toc = effectiveToc,
            charCount = charCount,
            coverBytes = cover?.first,
            coverExtension = cover?.second,
            images = images,
        )
    }

    private fun firstSpineImageCover(zip: ZipFile, spine: List<ManifestItem>): Pair<ByteArray, String>? {
        val item = spine.firstOrNull { isSupportedImage(it) } ?: return null
        val bytes = readEntry(zip, item.path, MAX_IMAGE_BYTES) ?: return null
        val extension = imageExtension(item.path, bytes) ?: return null
        return bytes to extension
    }

    private fun readToc(
        zip: ZipFile,
        opf: Document,
        manifest: Map<String, ManifestItem>,
        opfPath: String,
    ): List<RawToc> {
        val navItem = manifest.values.firstOrNull {
            it.properties.split(' ', '\t').contains("nav")
        }
        if (navItem != null) {
            val bytes = readEntry(zip, navItem.path, MAX_TEXT_BYTES)
            if (bytes != null) {
                val doc = parseHtml(bytes)
                val navs = doc.elementsNamed("nav")
                val tocNav = navs.firstOrNull { nav ->
                    nav.attributes().any { (key, value) ->
                        key.endsWith("type") && value.split(' ', '\t').contains("toc")
                    }
                } ?: navs.firstOrNull { it.select("a[href]").isNotEmpty() }
                if (tocNav != null) {
                    val entries = tocNav.select("a[href]").mapNotNull { anchor ->
                        val href = anchor.attr("href")
                        val path = resolvePath(navItem.path, href) ?: return@mapNotNull null
                        var level = 0
                        var parent: Element? = anchor.parent()
                        while (parent != null && parent != tocNav) {
                            if (parent.localName() == "ol" || parent.localName() == "ul") level++
                            parent = parent.parent()
                        }
                        RawToc(
                            title = baseText(anchor),
                            path = path,
                            level = (level - 1).coerceAtLeast(0),
                            fragment = href.substringAfter('#', ""),
                        )
                    }.filter { it.title.isNotBlank() }
                    if (entries.isNotEmpty()) return entries
                }
            }
        }

        val spineElement = opf.allElements.firstOrNull { it.localName() == "spine" }
        val tocId = spineElement?.attr("toc").orEmpty()
        val ncxItem = manifest.values.firstOrNull { it.id == tocId }
            ?: manifest.values.firstOrNull {
                it.mediaType.equals("application/x-dtbncx+xml", true) || it.path.endsWith(".ncx", true)
            }
        if (ncxItem != null) {
            val bytes = readEntry(zip, ncxItem.path, MAX_TEXT_BYTES)
            if (bytes != null) {
                val doc = parseXmlOrHtml(bytes)
                val navMap = doc.allElements.firstOrNull { it.localName() == "navmap" }
                if (navMap != null) {
                    val result = mutableListOf<RawToc>()
                    fun walk(point: Element, level: Int) {
                        val label = point.childElements("navlabel").firstOrNull()
                            ?.childElements("text")?.firstOrNull()?.let { baseText(it) }
                        val src = point.childElements("content").firstOrNull()?.attr("src")
                        val path = src?.let { resolvePath(ncxItem.path, it) }
                        if (!label.isNullOrBlank() && path != null) {
                            result += RawToc(
                                label,
                                path,
                                level,
                                fragment = src.substringAfter('#', ""),
                            )
                        }
                        point.childElements("navpoint").forEach { walk(it, level + 1) }
                    }
                    navMap.childElements("navpoint").forEach { walk(it, 0) }
                    if (result.isNotEmpty()) return result
                }
            }
        }
        return emptyList()
    }

    private fun findCover(
        zip: ZipFile,
        opf: Document,
        manifest: Map<String, ManifestItem>,
        opfPath: String,
    ): Pair<ByteArray, String>? {
        val byProperty = manifest.values.firstOrNull {
            it.properties.split(' ', '\t').contains("cover-image")
        }
        val byMetaId = opf.elementsNamed("meta").firstOrNull {
            it.attr("name").equals("cover", true)
        }?.attr("content")
        val byGuide = opf.elementsNamed("reference").firstOrNull {
            it.attr("type").equals("cover", true)
        }?.attr("href")?.let { resolvePath(opfPath, it) }

        val candidates = buildList {
            byProperty?.let { add(it) }
            byMetaId?.let { manifest[it]?.let { item -> add(item) } }
            byGuide?.let { path ->
                manifest.values.firstOrNull { it.path == path }?.let { add(it) }
                if (none { it.path == path }) {
                    add(ManifestItem("cover-page", path, "application/xhtml+xml", "", path))
                }
            }
            manifest.values.firstOrNull {
                it.path.contains("cover", true) && isSupportedImage(it)
            }?.let { add(it) }
        }

        for (item in candidates) {
            val bytes = readEntry(zip, item.path, MAX_IMAGE_BYTES) ?: continue
            if (isMarkup(item) || item.path.endsWith(".xhtml", true) || item.path.endsWith(".html", true)) {
                val doc = parseHtml(bytes)
                val img = doc.selectFirst("img[src]") ?: continue
                val imgPath = resolvePath(item.path, img.attr("src")) ?: continue
                val imgBytes = readEntry(zip, imgPath, MAX_IMAGE_BYTES) ?: continue
                val ext = imageExtension(imgPath, imgBytes) ?: continue
                return imgBytes to ext
            }
            val ext = imageExtension(item.path, bytes) ?: continue
            return bytes to ext
        }
        return null
    }

    private fun firstText(element: Element, tag: String): String {
        return element.elementsNamed(tag).firstOrNull()?.let { baseText(it) }.orEmpty()
    }

    private fun Block.anchorId(): String? = when (this) {
        is TextBlock -> anchor
        is ImageBlock -> anchor
    }

    private fun chooseChapterTitle(tocTitle: String?, headingTitle: String?): String {
        val toc = tocTitle?.trim().orEmpty()
        val heading = headingTitle?.trim().orEmpty()
        return when {
            toc.isEmpty() -> heading
            heading.isEmpty() -> toc
            toc == heading -> toc
            toc.length > heading.length + 2 && isSubsequence(heading, toc) -> heading
            else -> toc
        }
    }

    /**
     * Project Gutenberg/Aozora TOCs sometimes contain the ruby reading inline
     * as plain text ("法螺ほら男爵…"). When the TOC points at a heading block we
     * can use the clean heading text instead.
     */
    private fun chooseTocTitle(rawTitle: String, blockText: String?, chapterTitle: String): String {
        val raw = rawTitle.trim()
        val block = blockText?.trim().orEmpty()
        return when {
            raw.isEmpty() -> chapterTitle
            block.isEmpty() -> raw
            raw == block -> block
            raw.length > block.length + 2 && isSubsequence(block, raw) -> block
            else -> raw
        }
    }

    private fun isSubsequence(short: String, long: String): Boolean {
        if (short.isEmpty()) return false
        var index = 0
        short.forEach { ch ->
            index = long.indexOf(ch, index)
            if (index < 0) return false
            index++
        }
        return true
    }

    /**
     * Text content with ruby annotations removed. Jsoup's Element.text()
     * includes <rt> readings, which turns「漢字」into「漢字かんじ」in TOC labels.
     */
    private fun baseText(element: Element): String {
        val builder = StringBuilder()
        fun walk(node: Node) {
            when (node) {
                is TextNode -> builder.append(node.text())
                is Element -> {
                    if (node.localName() !in setOf("rt", "rp", "script", "style")) {
                        node.childNodes().forEach { walk(it) }
                    }
                }
                else -> Unit
            }
        }
        element.childNodes().forEach { walk(it) }
        return builder.toString().trim()
    }

    private fun isMarkup(item: ManifestItem): Boolean {
        val type = item.mediaType.lowercase()
        if (type == "application/xhtml+xml" || type == "text/html") return true
        if (type.isNotEmpty() && type != "application/xml") return false
        return item.path.endsWith(".xhtml", true) || item.path.endsWith(".html", true) ||
            item.path.endsWith(".htm", true)
    }

    private fun isSupportedImage(item: ManifestItem): Boolean {
        val type = item.mediaType.lowercase()
        if (type.startsWith("image/")) return type != "image/svg+xml"
        val ext = item.path.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    private fun tocPathMatches(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.substringBefore('#') == b.substringBefore('#')) return true
        return a.removePrefix("./") == b.removePrefix("./")
    }

    private fun readEntry(zip: ZipFile, path: String, maxBytes: Long): ByteArray? {
        val normalized = path.removePrefix("/").replace('\\', '/')
        val entry = zip.getEntry(normalized)
            ?: zip.getEntry(normalized.removePrefix("./"))
            ?: return null
        if (entry.isDirectory) return null
        if (entry.size > maxBytes) throw EpubException("ファイルが大きすぎます: $normalized")
        return zip.getInputStream(entry).use { readLimited(it, maxBytes) }
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw EpubException("ファイルが大きすぎます")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun parseXml(bytes: ByteArray): Document =
        Jsoup.parse(ByteArrayInputStream(bytes), null, "", Parser.xmlParser())

    private fun parseXmlOrHtml(bytes: ByteArray): Document = try {
        parseXml(bytes)
    } catch (_: Exception) {
        parseHtml(bytes)
    }

    private fun parseHtml(bytes: ByteArray): Document =
        Jsoup.parse(ByteArrayInputStream(bytes), null, "")

    private fun resolvePath(baseFile: String, href: String): String? {
        if (href.isBlank()) return null
        val lower = href.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return null
        val withoutFragment = href.substringBefore('#').substringBefore('?')
        if (withoutFragment.isBlank()) return baseFile
        val decoded = try {
            URLDecoder.decode(withoutFragment.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            withoutFragment
        }
        if (decoded.startsWith("data:")) return null
        val baseDir = baseFile.substringBeforeLast('/', "")
        val joined = when {
            decoded.startsWith("/") -> decoded.removePrefix("/")
            baseDir.isEmpty() -> decoded
            else -> "$baseDir/$decoded"
        }
        return normalizeZipPath(joined)
    }

    private fun normalizeZipPath(path: String): String? {
        val parts = ArrayDeque<String>()
        path.replace('\\', '/').split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast() else return null
                else -> parts.addLast(part)
            }
        }
        return if (parts.isEmpty()) null else parts.joinToString("/")
    }

    private data class RawToc(
        val title: String,
        val path: String,
        val level: Int,
        val fragment: String = "",
    )

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
        val path: String,
        val fallback: String = "",
    )

    private data class BuiltText(
        val text: String,
        val ruby: List<RubySpan>,
        val bold: List<StyleRange>,
    )

    private class InlineBuilder {
        private val text = StringBuilder()
        private val ruby = mutableListOf<RubySpan>()
        private val bold = mutableListOf<StyleRange>()

        fun isEmpty(): Boolean = text.isEmpty()

        fun append(raw: String, boldText: Boolean, preserveWhitespace: Boolean) {
            if (raw.isEmpty()) return
            if (preserveWhitespace) {
                val start = text.length
                text.append(raw.replace("\r\n", "\n").replace('\r', '\n'))
                if (boldText && text.length > start) bold += StyleRange(start, text.length)
                return
            }
            val start = text.length
            var lastWasSpace = text.isNotEmpty() && text.last() == ' '
            for (ch in raw) {
                when (ch) {
                    ' ', '\t', '\n', '\r', '\u000C' -> {
                        if (!lastWasSpace && text.isNotEmpty()) {
                            text.append(' ')
                            lastWasSpace = true
                        }
                    }
                    else -> {
                        text.append(ch)
                        lastWasSpace = false
                    }
                }
            }
            if (boldText && text.length > start) bold += StyleRange(start, text.length)
        }

        fun startRuby(): Int = text.length

        fun endRuby(start: Int, reading: String, boldText: Boolean) {
            val end = text.length
            if (end <= start) return
            val cleaned = reading.trim().filterNot { it == ' ' || it == '\n' || it == '\t' || it == '\r' }
            if (cleaned.isNotEmpty()) ruby += RubySpan(start, end, cleaned)
            if (boldText) bold += StyleRange(start, end)
        }

        fun build(trimWhitespace: Boolean): BuiltText? {
            val raw = text.toString()
            if (raw.isEmpty()) return null
            val start: Int
            val end: Int
            if (trimWhitespace) {
                start = raw.indexOfFirst { !it.isBodyTrimChar() }
                end = raw.indexOfLast { !it.isBodyTrimChar() } + 1
                if (start < 0 || end <= start) return null
            } else {
                start = 0
                end = raw.length
            }
            val value = raw.substring(start, end)
            if (value.isBlank() && value.none { it == '\u3000' }) return null
            val shiftedRuby = ruby.mapNotNull { span ->
                val s = maxOf(span.start, start)
                val e = minOf(span.end, end)
                if (e <= s) null else RubySpan(s - start, e - start, span.text)
            }
            val shiftedBold = bold.mapNotNull { range ->
                val s = maxOf(range.start, start)
                val e = minOf(range.end, end)
                if (e <= s) null else StyleRange(s - start, e - start)
            }
            return BuiltText(value, shiftedRuby, shiftedBold)
        }

        private fun Char.isBodyTrimChar(): Boolean =
            this == ' ' || this == '\u3000' || this == '\t' || this == '\n' || this == '\r' || this == '\u000C'
    }

    private class ChapterExtractor(
        private val chapterPath: String,
        private val zip: ZipFile,
        private val images: MutableMap<String, ByteArray>,
        private val imageNames: MutableMap<String, String>,
        private val imageByteBudget: () -> Long,
        private val onImageBytes: (Long) -> Unit,
    ) {
        private val blocks = mutableListOf<Block>()
        private var buffer = InlineBuilder()
        private var continuation = false
        private var charCount = 0

        fun extract(bytes: ByteArray): Chapter {
            if (bytes.isEmpty()) return Chapter(blocks = emptyList(), charCount = 0)
            val doc = try {
                Jsoup.parse(ByteArrayInputStream(bytes), null, "")
            } catch (_: Exception) {
                return Chapter(blocks = emptyList(), charCount = 0)
            }
            val body = doc.body() ?: doc
            visit(body, BlockStyle.BODY, false, true, false, anchor = null)
            flush(BlockStyle.BODY, true, false, anchor = null)
            return Chapter(blocks = blocks, charCount = charCount)
        }

        /**
         * A fixed-layout EPUB may put image resources directly in the spine.
         * Treat each one as a full-page illustration.
         */
        fun extractImageOnly(path: String): Chapter {
            if (imageByteBudget() > MAX_TOTAL_IMAGE_BYTES) return Chapter()
            val bytes = try {
                readEntry(zip, path, MAX_IMAGE_BYTES)
            } catch (_: Exception) {
                null
            } ?: return Chapter()
            val extension = imageExtension(path, bytes) ?: return Chapter()
            val name = imageNames.getOrPut("path:$path") {
                "${digest(bytes)}.$extension"
            }
            if (name !in images) {
                images[name] = bytes
                onImageBytes(bytes.size.toLong())
            }
            return Chapter(
                blocks = listOf(ImageBlock(name, alt = path.substringAfterLast('/'))),
                charCount = 0,
            )
        }

        private fun visit(
            element: Element,
            inheritedStyle: BlockStyle,
            bold: Boolean,
            indent: Boolean,
            centered: Boolean,
            anchor: String?,
        ) {
            val currentAnchor = element.id().takeIf { it.isNotBlank() } ?: anchor
            element.childNodes().forEach { node ->
                when (node) {
                    is TextNode -> buffer.append(
                        node.text(),
                        bold,
                        preserveWhitespace = inheritedStyle == BlockStyle.POEM,
                    )
                    is Element -> visitElement(node, inheritedStyle, bold, indent, centered, currentAnchor)
                    else -> Unit
                }
            }
        }

        private fun visitElement(
            element: Element,
            inheritedStyle: BlockStyle,
            bold: Boolean,
            indent: Boolean,
            centered: Boolean,
            anchor: String?,
        ) {
            val tag = element.tagName().lowercase()
            val blockAnchor = element.id().takeIf { it.isNotBlank() } ?: anchor
            when (tag) {
                "br" -> {
                    flush(inheritedStyle, indent, centered, anchor, force = true)
                    continuation = true
                }
                "img" -> {
                    flush(inheritedStyle, indent, centered, anchor)
                    addImage(element, blockAnchor)
                }
                "ruby" -> appendRuby(element, bold)
                "rt", "rp" -> Unit
                "strong", "em", "b" -> visit(element, inheritedStyle, true, indent, centered, anchor)
                "i", "u", "s", "span", "a", "small", "sub", "sup", "code",
                -> visit(element, inheritedStyle, bold, indent, centered, anchor)
                "script", "style", "nav", "svg", "head", "title", "meta", "link",
                "iframe", "object", "embed", "form", "input", "button", "select", "textarea",
                -> Unit
                "hr" -> {
                    flush(inheritedStyle, indent, centered, anchor)
                    blocks += TextBlock(
                        "＊　＊　＊",
                        BlockStyle.BODY,
                        indent = false,
                        centered = true,
                        anchor = blockAnchor,
                    )
                    charCount += 5
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flush(inheritedStyle, indent, centered, anchor)
                    val newStyle = if (tag == "h1" || tag == "h2") BlockStyle.HEADING else BlockStyle.SUBHEADING
                    visit(element, newStyle, bold, indent = false, centered = true, anchor = blockAnchor)
                    flush(newStyle, false, true, blockAnchor)
                }
                "blockquote" -> {
                    flush(inheritedStyle, indent, centered, anchor)
                    visit(element, BlockStyle.QUOTE, bold, indent = true, centered = false, anchor = blockAnchor)
                    flush(BlockStyle.QUOTE, true, false, blockAnchor)
                }
                "figcaption" -> {
                    flush(inheritedStyle, indent, centered, anchor)
                    visit(element, BlockStyle.CAPTION, bold, indent = false, centered = true, anchor = blockAnchor)
                    flush(BlockStyle.CAPTION, false, true, blockAnchor)
                }
                "pre" -> {
                    flush(inheritedStyle, indent, centered, anchor)
                    visit(element, BlockStyle.POEM, bold, indent = false, centered = false, anchor = blockAnchor)
                    flush(BlockStyle.POEM, false, false, blockAnchor)
                }
                "li" -> {
                    flush(inheritedStyle, indent, centered, anchor)
                    val parent = element.parent()
                    val ordered = parent?.tagName()?.equals("ol", true) == true
                    val marker = if (ordered) {
                        val index = element.elementSiblingIndex() + 1
                        "$index. "
                    } else {
                        "・"
                    }
                    buffer.append(marker, bold, preserveWhitespace = false)
                    visit(element, inheritedStyle, bold, indent = false, centered = false, anchor = blockAnchor)
                    flush(inheritedStyle, false, false, blockAnchor)
                }
                "p", "div", "section", "article", "header", "footer", "main", "aside",
                "td", "th", "dd", "dt", "tr", "table", "ul", "ol", "figure", "body",
                -> {
                    flush(inheritedStyle, indent, centered, anchor)
                    val newStyle = when {
                        inheritedStyle == BlockStyle.QUOTE -> BlockStyle.QUOTE
                        inheritedStyle == BlockStyle.POEM -> BlockStyle.POEM
                        else -> BlockStyle.BODY
                    }
                    val newIndent = newStyle == BlockStyle.BODY || newStyle == BlockStyle.QUOTE
                    val newCentered = inheritedStyle == BlockStyle.HEADING ||
                        inheritedStyle == BlockStyle.SUBHEADING || inheritedStyle == BlockStyle.CAPTION
                    visit(element, newStyle, bold, newIndent, newCentered, anchor = blockAnchor)
                    flush(newStyle, newIndent, newCentered, blockAnchor)
                }
                else -> visit(element, inheritedStyle, bold, indent, centered, anchor)
            }
        }

        private fun appendRuby(element: Element, bold: Boolean) {
            val start = buffer.startRuby()
            val readings = StringBuilder()
            element.childNodes().forEach { child ->
                when (child) {
                    is TextNode -> buffer.append(child.text(), bold, preserveWhitespace = false)
                    is Element -> when (child.tagName().lowercase()) {
                        "rt", "rp" -> readings.append(child.text())
                        "rb" -> child.childNodes().forEach { rb ->
                            if (rb is TextNode) buffer.append(rb.text(), bold, false)
                            else if (rb is Element) appendInlineChildren(rb, bold)
                        }
                        else -> appendInlineChildren(child, bold)
                    }
                    else -> Unit
                }
            }
            buffer.endRuby(start, readings.toString(), bold)
        }

        private fun appendInlineChildren(element: Element, bold: Boolean) {
            val isBold = bold || element.tagName().lowercase() in setOf("em", "strong", "b")
            element.childNodes().forEach { child ->
                when (child) {
                    is TextNode -> buffer.append(child.text(), isBold, false)
                    is Element -> when (child.tagName().lowercase()) {
                        "rt", "rp", "script", "style" -> Unit
                        "br" -> buffer.append(" ", isBold, false)
                        "ruby" -> appendRuby(child, isBold)
                        else -> appendInlineChildren(child, isBold)
                    }
                    else -> Unit
                }
            }
        }

        private fun flush(
            style: BlockStyle,
            indent: Boolean,
            centered: Boolean,
            anchor: String? = null,
            force: Boolean = false,
        ) {
            val built = buffer.build(trimWhitespace = style != BlockStyle.POEM)
            buffer = InlineBuilder()
            if (built == null) {
                if (force) {
                    blocks += TextBlock(
                        "",
                        style = style,
                        indent = false,
                        centered = false,
                        anchor = anchor,
                    )
                }
                continuation = false
                return
            }
            val actualIndent = indent && !continuation
            blocks += TextBlock(
                text = built.text,
                style = style,
                indent = actualIndent,
                centered = centered,
                ruby = built.ruby,
                bold = built.bold,
                anchor = anchor,
            )
            charCount += built.text.length
            continuation = false
        }

        private fun addImage(element: Element, anchor: String?) {
            if (imageByteBudget() > MAX_TOTAL_IMAGE_BYTES) return
            val alt = element.attr("alt")
            val src = element.attr("src").ifBlank { element.attr("xlink:href") }.ifBlank {
                element.attr("data-src")
            }
            if (src.isBlank()) return

            val bytes: ByteArray
            val extension: String
            val cacheKey: String
            if (src.startsWith("data:", ignoreCase = true)) {
                val comma = src.indexOf(',')
                if (comma < 0) return
                val meta = src.substring(0, comma)
                if (!meta.contains("base64", true)) return
                bytes = try {
                    Base64.getDecoder().decode(src.substring(comma + 1))
                } catch (_: Exception) {
                    return
                }
                extension = sniffImage(bytes) ?: return
                cacheKey = "data:${digest(bytes)}"
            } else {
                val path = resolvePath(chapterPath, src) ?: return
                val existing = imageNames[path]
                if (existing != null) {
                    blocks += ImageBlock(existing, alt = alt, anchor = anchor)
                    return
                }
                bytes = try {
                    readEntry(zip, path, MAX_IMAGE_BYTES)
                } catch (_: Exception) {
                    null
                } ?: return
                extension = imageExtension(path, bytes) ?: return
                cacheKey = path
            }

            val name = imageNames[cacheKey] ?: run {
                val n = "${digest(bytes)}.$extension"
                imageNames[cacheKey] = n
                n
            }
            if (name !in images) {
                images[name] = bytes
                onImageBytes(bytes.size.toLong())
            }
            blocks += ImageBlock(name, alt = alt, anchor = anchor)
        }
    }
}
