package app.tateyomi.data

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val MAX_HTML_BYTES = 32L * 1024 * 1024

/** Reads standalone XHTML / HTML files and zip archives that contain them. */
object XhtmlParser {

    private val HTML_EXTENSIONS = setOf("xhtml", "html", "htm")

    fun parse(
        file: File,
        fallbackTitle: String? = null,
        imageDir: File? = null,
    ): ParsedEpub {
        if (!file.isFile) throw EpubException("ファイルが見つかりません")
        if (file.length() > MAX_HTML_BYTES) throw EpubException("ファイルが大きすぎます")
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            throw EpubException("XHTMLを読み込めませんでした")
        }
        val name = file.name.ifBlank { "book.xhtml" }
        val source = MarkupResourceSource { path, _ ->
            if (path == name || path == file.name) bytes else null
        }
        return parseDocuments(
            fallbackTitle = fallbackTitle?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            entries = listOf(name to bytes),
            source = source,
            format = BookFormat.XHTML,
            imageDir = imageDir,
        )
    }

    fun parseArchive(
        zip: ZipFile,
        fallbackTitle: String,
        imageDir: File? = null,
    ): ParsedEpub {
        val entries = zip.entries().asSequence()
            .filter { !it.isDirectory }
            .map { it.name.replace('\\', '/') }
            .filter { path ->
                val name = path.substringAfterLast('/')
                !name.startsWith(".") && !path.startsWith("__MACOSX/") &&
                    path.substringAfterLast('.', "").lowercase() in HTML_EXTENSIONS
            }
            .sortedWith(naturalOrder())
            .toList()
        if (entries.isEmpty()) throw EpubException("XHTMLが見つかりません")
        val source = MarkupResourceSource { path, maxBytes -> readEntry(zip, path, maxBytes) }
        val documents = entries.mapNotNull { path ->
            val bytes = readEntry(zip, path, MAX_HTML_BYTES) ?: return@mapNotNull null
            path to bytes
        }
        if (documents.isEmpty()) throw EpubException("XHTMLが見つかりません")
        return parseDocuments(
            fallbackTitle = fallbackTitle,
            entries = documents,
            source = source,
            format = BookFormat.XHTML,
            imageDir = imageDir,
        )
    }

    private fun parseDocuments(
        fallbackTitle: String,
        entries: List<Pair<String, ByteArray>>,
        source: MarkupResourceSource,
        format: BookFormat,
        imageDir: File? = null,
    ): ParsedEpub {
        val firstDoc = parseDocument(entries.first().second)
        val metadata = metadata(firstDoc, fallbackTitle)
        val images = linkedMapOf<String, ByteArray>()
        val imageNames = mutableMapOf<String, String>()
        var imageBytes = 0L
        val storedImageNames = mutableSetOf<String>()
        val imageSink: ((String, ByteArray) -> Unit)? = imageDir?.let { dir ->
            dir.mkdirs()
            val sink: (String, ByteArray) -> Unit = { name, bytes ->
                File(dir, name).writeBytes(bytes)
            }
            sink
        }
        val chapters = ArrayList<Chapter>(entries.size)
        val toc = ArrayList<TocEntry>()
        var totalChars = 0
        entries.forEachIndexed { index, (path, bytes) ->
            val document = if (index == 0) firstDoc else parseDocument(bytes)
            val extractor = EpubParser.ChapterExtractor(
                chapterPath = path,
                source = source,
                images = images,
                imageNames = imageNames,
                imageByteBudget = { imageBytes },
                onImageBytes = { imageBytes += it },
                imageSink = imageSink,
                storedImageNames = storedImageNames,
            )
            val chapter = extractor.extract(bytes)
            val fallbackChapterTitle = path.substringAfterLast('/').substringBeforeLast('.')
                .ifBlank { "第${index + 1}章" }
            val chapterTitle = chapterTitle(document, chapter, fallbackChapterTitle)
            val stored = chapter.copy(title = chapterTitle)
            chapters += stored
            totalChars += stored.charCount
            if (totalChars > MAX_IMPORT_CHARS) {
                throw EpubException("本文が大きすぎて読み込めません")
            }
            toc += TocEntry(chapterTitle, index, 0)
            stored.blocks.forEachIndexed { blockIndex, block ->
                if (block is TextBlock &&
                    (block.style == BlockStyle.HEADING || block.style == BlockStyle.SUBHEADING)
                ) {
                    val heading = block.text.trim().take(80)
                    if (heading.isNotEmpty() && heading != chapterTitle) {
                        toc += TocEntry(heading, index, 1, blockIndex)
                    }
                }
            }
        }
        val keptIndices = chapters.indices.filter { index ->
            val chapter = chapters[index]
            chapter.charCount > 0 || chapter.blocks.any { b -> b is ImageBlock }
        }
        if (keptIndices.isEmpty()) throw EpubException("本文が見つかりません")
        val kept = keptIndices.map { chapters[it] }
        val oldToNew = keptIndices.withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }
        val keptToc = toc.mapNotNull { entry ->
            val newIndex = oldToNew[entry.chapterIndex] ?: return@mapNotNull null
            entry.copy(chapterIndex = newIndex)
        }
        return ParsedEpub(
            title = metadata.first.ifBlank { fallbackTitle.ifBlank { "無題の本" } },
            author = metadata.second,
            chapters = kept,
            toc = keptToc.ifEmpty {
                kept.mapIndexed { index, chapter -> TocEntry(chapter.title, index, 0) }
            }.take(MAX_GENERATED_TOC_ENTRIES),
            charCount = kept.sumOf { it.charCount },
            images = images,
            format = format,
        )
    }

    private fun parseDocument(bytes: ByteArray): Document =
        Jsoup.parse(ByteArrayInputStream(bytes), null, "")

    private fun chapterTitle(document: Document, chapter: Chapter, fallback: String): String {
        val heading = chapter.blocks.filterIsInstance<TextBlock>()
            .firstOrNull { it.style == BlockStyle.HEADING || it.style == BlockStyle.SUBHEADING }
            ?.text
            ?.trim()
            ?.take(80)
            ?.takeIf { it.isNotEmpty() }
        if (heading != null) return heading
        val title = document.title().trim().take(80)
        return title.ifEmpty { fallback }
    }

    private fun metadata(document: Document, fallback: String): Pair<String, String> {
        val metaTags = document.select("meta")
        fun meta(vararg names: String): String? {
            val element = metaTags.firstOrNull { element ->
                names.any { name ->
                    element.attr("name").equals(name, ignoreCase = true) ||
                        element.attr("property").equals(name, ignoreCase = true) ||
                        element.attr("property").equals("og:$name", ignoreCase = true)
                }
            }
            return element?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
        }

        val title = meta("DC.title", "dc.title", "title", "og:title")
            ?: document.selectFirst(".jtitle")?.text()?.trim()
            ?: document.title().trim().takeIf { it.isNotEmpty() }
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: fallback
        val author = meta("DC.creator", "dc.creator", "author", "creator")
            ?: document.selectFirst(".author")?.text()?.trim()
            ?: document.selectFirst("h2.author, h3.author")?.text()?.trim()
            ?: ""
        return clean(title).ifBlank { fallback } to clean(author)
    }

    private fun clean(value: String): String =
        value.replace(Regex("[\\s\\u3000]+"), " ").trim().take(200)

    private fun readEntry(zip: ZipFile, path: String, maxBytes: Long): ByteArray? {
        val normalized = path.removePrefix("./").removePrefix("/")
        val entry = zip.getEntry(normalized)
            ?: zip.getEntry(path.removePrefix("/"))
            ?: return null
        if (entry.isDirectory) return null
        if (entry.size > maxBytes) return null
        return zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return@use null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }
}

/** Case-insensitive, digit-aware ordering used for comic page names. */
internal fun naturalOrder(): Comparator<String> = Comparator { a, b ->
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var endA = i
            while (endA < a.length && a[endA].isDigit()) endA++
            var endB = j
            while (endB < b.length && b[endB].isDigit()) endB++
            val numA = a.substring(i, endA).trimStart('0')
            val numB = b.substring(j, endB).trimStart('0')
            val cmp = when {
                numA.length != numB.length -> numA.length.compareTo(numB.length)
                else -> numA.compareTo(numB)
            }
            if (cmp != 0) return@Comparator cmp
            i = endA
            j = endB
        } else {
            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) return@Comparator cmp
            i++
            j++
        }
    }
    (a.length - i).compareTo(b.length - j)
}
