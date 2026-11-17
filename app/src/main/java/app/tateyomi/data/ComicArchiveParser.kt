package app.tateyomi.data

import be.stef.rar.Unrar5j
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

private const val MAX_PAGE_BYTES = 128L * 1024 * 1024
private const val MAX_TOTAL_PAGE_BYTES = 1024L * 1024 * 1024

/**
 * Image-only archives (CBZ / CBR / zip / rar). Every image becomes one
 * full-page [ImageBlock]; the source order is natural-sorted so page 2 comes
 * before page 10.
 */
object ComicArchiveParser {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun parseZip(
        zipFile: File,
        outputDir: File,
        format: BookFormat,
        fallbackTitle: String? = null,
    ): ParsedEpub {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .filter { isPageName(it.name) }
                .sortedWith(compareBy(naturalOrder()) { it.name.replace('\\', '/') })
                .toList()
            if (entries.isEmpty()) throw EpubException("画像が見つかりません")
            val title = fallbackTitle?.takeIf { it.isNotBlank() }
                ?: zipFile.nameWithoutExtension
            val comicInfoEntry = zip.entries().asSequence().firstOrNull {
                !it.isDirectory && it.name.substringAfterLast('/')
                    .equals("ComicInfo.xml", ignoreCase = true)
            }
            val comicInfo = comicInfoEntry
                ?.let { entry -> zip.getInputStream(entry).use { readLimited(it, 2 * 1024 * 1024) } }
            val metadata = metadata(comicInfo, title)
            var total = 0L
            var coverBytes: ByteArray? = null
            val imageFiles = LinkedHashMap<String, File>()
            val blocks = ArrayList<Block>(entries.size)
            entries.forEachIndexed { index, entry ->
                val bytes = zip.getInputStream(entry).use { input ->
                    readLimited(input, MAX_PAGE_BYTES)
                } ?: return@forEachIndexed
                total += bytes.size
                if (total > MAX_TOTAL_PAGE_BYTES) {
                    throw EpubException("画像の合計サイズが大きすぎます")
                }
                val extension = imageExtension(entry.name, bytes) ?: return@forEachIndexed
                val name = pageName(index, extension)
                val target = File(outputDir, name)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
                imageFiles[name] = target
                if (index == 0 && bytes.size <= 8 * 1024 * 1024) coverBytes = bytes
                blocks += ImageBlock(name, alt = entry.name.substringAfterLast('/'))
            }
            return buildResult(title, metadata, blocks, imageFiles, coverBytes, format)
        }
    }

    fun parseRar(
        rarFile: File,
        outputDir: File,
        format: BookFormat,
        fallbackTitle: String? = null,
    ): ParsedEpub {
        val parent = outputDir.parentFile ?: outputDir
        val temporary = File(parent, "rar-tmp-${System.nanoTime()}")
        temporary.mkdirs()
        try {
            val result = try {
                Unrar5j.extract(rarFile.absolutePath, temporary.absolutePath, null)
            } catch (e: Exception) {
                throw EpubException("RARを読み込めませんでした（破損または暗号化）")
            }
            val title = fallbackTitle?.takeIf { it.isNotBlank() }
                ?: rarFile.nameWithoutExtension
            return parseDirectory(temporary, outputDir, format, title, result.errorCount > 0)
        } finally {
            temporary.deleteRecursively()
        }
    }

    fun parseDirectory(
        sourceDir: File,
        outputDir: File,
        format: BookFormat,
        fallbackTitle: String,
        hadExtractionErrors: Boolean = false,
    ): ParsedEpub {
        if (!sourceDir.isDirectory) throw EpubException("画像が見つかりません")
        val title = fallbackTitle.ifBlank { "無題の本" }
        val comicInfoFile = sourceDir.walkTopDown().firstOrNull {
            it.isFile && it.name.equals("ComicInfo.xml", ignoreCase = true)
        }
        val comicInfo = comicInfoFile?.let { file ->
            try {
                file.inputStream().use { input -> readLimited(input, 2 * 1024 * 1024) }
            } catch (_: Exception) {
                null
            }
        }
        val metadata = metadata(comicInfo, title)
        val pages = sourceDir.walkTopDown()
            .filter {
                it.isFile && isPageName(it.relativeTo(sourceDir).invariantSeparatorsPath)
            }
            .sortedWith(compareBy(naturalOrder()) { it.relativeTo(sourceDir).invariantSeparatorsPath })
            .toList()
        if (pages.isEmpty()) {
            throw EpubException(
                if (hadExtractionErrors) {
                    "アーカイブを読み込めませんでした（破損または暗号化）"
                } else {
                    "画像が見つかりません"
                },
            )
        }
        var total = 0L
        var coverBytes: ByteArray? = null
        val imageFiles = LinkedHashMap<String, File>()
        val blocks = ArrayList<Block>(pages.size)
        pages.forEachIndexed { index, page ->
            if (page.length() > MAX_PAGE_BYTES) return@forEachIndexed
            total += page.length()
            if (total > MAX_TOTAL_PAGE_BYTES) {
                throw EpubException("画像の合計サイズが大きすぎます")
            }
            val prefix = readPrefix(page, 32)
            val extension = imageExtension(page.name, prefix)
                ?: page.extension.lowercase().takeIf { it in IMAGE_EXTENSIONS }
                ?: return@forEachIndexed
            val name = pageName(index, extension)
            val target = File(outputDir, name)
            target.parentFile?.mkdirs()
            page.copyTo(target, overwrite = true)
            imageFiles[name] = target
            if (index == 0 && page.length() <= 8 * 1024 * 1024) {
                coverBytes = page.readBytes()
            }
            blocks += ImageBlock(name, alt = page.name)
        }
        return buildResult(title, metadata, blocks, imageFiles, coverBytes, format)
    }

    private fun buildResult(
        fallbackTitle: String,
        metadata: Pair<String, String>,
        blocks: List<Block>,
        imageFiles: Map<String, File>,
        coverBytes: ByteArray?,
        format: BookFormat,
    ): ParsedEpub {
        if (blocks.isEmpty()) throw EpubException("画像が見つかりません")
        val title = metadata.first.ifBlank { fallbackTitle.ifBlank { "無題の本" } }
        val chapter = Chapter(title = title, blocks = blocks, charCount = 0)
        return ParsedEpub(
            title = title,
            author = metadata.second,
            chapters = listOf(chapter),
            toc = listOf(TocEntry(title, 0)),
            charCount = 0,
            coverBytes = coverBytes,
            coverExtension = coverBytes?.let { sniff(it) },
            // The first archive page is a cover image for the shelf, but it is
            // also reading content. Do not mark it as a skippable EPUB cover.
            coverImageName = null,
            coverImageFile = imageFiles.values.firstOrNull(),
            imageFiles = imageFiles,
            format = format,
        )
    }

    private fun metadata(comicInfo: ByteArray?, fallback: String): Pair<String, String> {
        if (comicInfo == null || comicInfo.isEmpty()) return fallback to ""
        return try {
            val doc = Jsoup.parse(ByteArrayInputStream(comicInfo), null, "", Parser.xmlParser())
            val series = doc.selectFirst("Series")?.text()?.trim().orEmpty()
            val number = doc.selectFirst("Number")?.text()?.trim().orEmpty()
            val explicitTitle = doc.selectFirst("Title")?.text()?.trim().orEmpty()
            val title = explicitTitle.ifBlank {
                when {
                    series.isBlank() -> fallback
                    number.isBlank() -> series
                    else -> "$series $number"
                }
            }
            val author = listOf("Writer", "Penciller", "Artist")
                .mapNotNull { doc.selectFirst(it)?.text()?.trim()?.takeIf(String::isNotEmpty) }
                .distinct()
                .joinToString("、")
            val cleanTitle = title.replace(Regex("[\\s\\u3000]+"), " ").trim()
            val cleanAuthor = author.replace(Regex("[\\s\\u3000]+"), " ").trim()
            cleanTitle to cleanAuthor
        } catch (_: Exception) {
            fallback to ""
        }
    }

    private fun isPageName(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val name = normalized.substringAfterLast('/')
        if (name.isEmpty() || name.startsWith(".") || name.startsWith("._")) return false
        if (normalized.startsWith("__MACOSX/")) return false
        if (name.equals("ComicInfo.xml", true) || name.equals("Thumbs.db", true)) return false
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }

    private fun pageName(index: Int, extension: String): String =
        "page-%04d.%s".format(java.util.Locale.ROOT, index + 1, extension)

    private fun readPrefix(file: File, length: Int): ByteArray {
        return try {
            file.inputStream().use { input ->
                val bytes = ByteArray(length)
                val read = input.read(bytes)
                if (read <= 0) ByteArray(0) else bytes.copyOf(read)
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun sniff(bytes: ByteArray): String? = imageExtension("", bytes)
}
