package app.tateyomi.data

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.min

private const val MAX_KINDLE_BYTES = 512L * 1024 * 1024
private const val MAX_KINDLE_IMAGE_BYTES = 64L * 1024 * 1024
private const val MAX_KINDLE_TOTAL_IMAGES = 512L * 1024 * 1024
private const val MAX_KINDLE_TEXT_BYTES = 32 * 1024 * 1024
private const val HUFF_OUTPUT_LIMIT = 64 * 1024 * 1024

/**
 * Reads unencrypted Mobipocket / Kindle files (MOBI, PRC, AZW, AZW3).
 *
 * PalmDOC and HUFF/CDIC text records are decoded in pure Kotlin. Old MOBI
 * text is parsed directly; KF8 (AZW3) files are rebuilt from their skeleton
 * and fragment indexes before parsing. DRM-protected files are rejected.
 */
object KindleParser {

    fun parse(
        file: File,
        fallbackTitle: String? = null,
        imageDir: File? = null,
    ): ParsedEpub {
        if (!file.isFile) throw EpubException("ファイルが見つかりません")
        if (file.length() > MAX_KINDLE_BYTES) throw EpubException("ファイルが大きすぎます")
        return try {
            PalmDb(file).use { db ->
                parseDb(
                    db = db,
                    fallbackTitle = fallbackTitle?.takeIf { it.isNotBlank() }
                        ?: file.nameWithoutExtension,
                    imageDir = imageDir,
                )
            }
        } catch (e: EpubException) {
            throw e
        } catch (e: Exception) {
            throw EpubException("Kindleファイルを読み込めませんでした（${e.message ?: "破損している可能性があります"}）")
        }
    }

    private fun parseDb(db: PalmDb, fallbackTitle: String, imageDir: File?): ParsedEpub {
        if (db.recordCount <= 0) throw EpubException("Kindleファイルではありません")
        val first = MobiHeader.parse(db, 0)
        if (first.encrypted) {
            throw EpubException("DRM保護されたKindleファイルは読み込めません")
        }
        if (first.isPrintReplica(db)) {
            throw EpubException("AZW4（Print Replica）は読み込めません")
        }
        if (first.version >= 8) {
            return parseKf8(db, first, fallbackTitle, imageDir)
        }
        return parseMobi7(db, first, fallbackTitle, imageDir)
    }

    private fun parseMobi7(
        db: PalmDb,
        header: MobiHeader,
        fallbackTitle: String,
        imageDir: File?,
    ): ParsedEpub {
        val boundary = findBoundary(db)
        val endRecord = if (boundary != null && boundary > header.start) boundary else db.recordCount
        val resources = extractResources(db, header, endRecord, imageDir)
        val raw = header.readText(db)
        val html = if (header.isPalmDoc) raw else rewriteMobi7(raw, resources, header.textCharset)
        val writtenImages = LinkedHashMap<String, File>()
        val storedImageNames = mutableSetOf<String>()
        val imageSink: ((String, ByteArray) -> Unit)? = imageDir?.let { dir ->
            dir.mkdirs()
            val sink: (String, ByteArray) -> Unit = { name, bytes ->
                val file = File(dir, name)
                file.writeBytes(bytes)
                writtenImages[name] = file
            }
            sink
        }
        val chapters = buildMarkupChapters(
            html = html,
            resourceSource = resources.source,
            images = resources.images,
            bookTitle = header.title.ifBlank { fallbackTitle },
            imageSink = imageSink,
            storedImageNames = storedImageNames,
        )
        return finish(
            title = header.title.ifBlank { fallbackTitle.ifBlank { "無題の本" } },
            author = header.authors.joinToString("、"),
            language = header.language,
            chapters = chapters,
            resources = resources,
            cover = resources.coverBytes,
            format = BookFormat.MOBI,
            imageFiles = writtenImages,
        )
    }

    private fun parseKf8(
        db: PalmDb,
        header: MobiHeader,
        fallbackTitle: String,
        imageDir: File?,
    ): ParsedEpub {
        val resources = extractResources(db, header, db.recordCount, imageDir)
        val raw = header.readText(db)
        val parts = try {
            Kf8Reconstructor(db, header).rebuild(raw)
        } catch (_: Exception) {
            listOf(raw)
        }
        val htmlParts = if (parts.isEmpty()) listOf(raw) else parts
        val imageNames = mutableMapOf<String, String>()
        val writtenImages = LinkedHashMap<String, File>()
        val storedImageNames = mutableSetOf<String>()
        val imageSink: ((String, ByteArray) -> Unit)? = imageDir?.let { dir ->
            dir.mkdirs()
            val sink: (String, ByteArray) -> Unit = { name, bytes ->
                val file = File(dir, name)
                file.writeBytes(bytes)
                writtenImages[name] = file
            }
            sink
        }
        var imageBytes = 0L
        var totalChars = 0
        fun parseParts(partsToParse: List<ByteArray>): List<Chapter> {
            val chapters = ArrayList<Chapter>()
            partsToParse.forEachIndexed { index, part ->
                val rewritten = rewriteKf8(part, resources, header.textCharset)
                val extractor = EpubParser.ChapterExtractor(
                    chapterPath = "part-$index.xhtml",
                    source = resources.source,
                    images = resources.images,
                    imageNames = imageNames,
                    imageByteBudget = { imageBytes },
                    onImageBytes = { imageBytes += it },
                    imageSink = imageSink,
                    storedImageNames = storedImageNames,
                )
                val chapter = extractor.extract(rewritten)
                val title = chapterTitle(chapter, "第${index + 1}章")
                val stored = chapter.copy(title = title)
                if (stored.charCount > 0 || stored.blocks.any { it is ImageBlock }) {
                    chapters += stored
                    totalChars += stored.charCount
                    if (totalChars > MAX_IMPORT_CHARS) {
                        throw EpubException("本文が大きすぎて読み込めません")
                    }
                }
            }
            return chapters
        }
        var chapters = parseParts(htmlParts)
        if (chapters.isEmpty() && htmlParts.size != 1) {
            chapters = parseParts(listOf(raw))
        }
        if (chapters.isEmpty()) throw EpubException("本文が見つかりません")
        return finish(
            title = header.title.ifBlank { fallbackTitle.ifBlank { "無題の本" } },
            author = header.authors.joinToString("、"),
            language = header.language,
            chapters = chapters,
            resources = resources,
            cover = resources.coverBytes,
            format = BookFormat.AZW3,
            imageFiles = writtenImages,
        )
    }

    private fun finish(
        title: String,
        author: String,
        language: String,
        chapters: List<Chapter>,
        resources: ResourceSet,
        cover: ByteArray?,
        format: BookFormat,
        imageFiles: Map<String, File> = emptyMap(),
    ): ParsedEpub {
        val toc = ArrayList<TocEntry>()
        chapters.forEachIndexed { index, chapter ->
            toc += TocEntry(chapter.title, index, 0)
            chapter.blocks.forEachIndexed { blockIndex, block ->
                if (block is TextBlock &&
                    (block.style == BlockStyle.HEADING || block.style == BlockStyle.SUBHEADING)
                ) {
                    val heading = block.text.trim().take(80)
                    if (heading.isNotEmpty() && heading != chapter.title) {
                        toc += TocEntry(heading, index, 1, blockIndex)
                    }
                }
            }
        }
        val usedImages = chapters.flatMap { chapter ->
            chapter.blocks.flatMap { block ->
                when (block) {
                    is ImageBlock -> listOf(block.file)
                    is TextBlock -> block.inlineImages.map { it.file }
                }
            }
        }.toSet()
        return ParsedEpub(
            title = title,
            author = author,
            language = language,
            chapters = chapters,
            toc = toc.take(MAX_GENERATED_TOC_ENTRIES),
            charCount = chapters.sumOf { it.charCount },
            coverBytes = cover,
            coverExtension = cover?.let { imageExtension("", it) },
            // Cover images are not reliably addressable from the rebuilt
            // content, so do not mark them as a skippable spine page.
            coverImageName = null,
            images = resources.images.filterKeys { it in usedImages },
            imageFiles = imageFiles.filterKeys { it in usedImages },
            format = format,
        )
    }

    private fun buildMarkupChapters(
        html: ByteArray,
        resourceSource: MarkupResourceSource,
        images: MutableMap<String, ByteArray>,
        bookTitle: String,
        imageSink: ((String, ByteArray) -> Unit)? = null,
        storedImageNames: MutableSet<String> = mutableSetOf(),
    ): List<Chapter> {
        val imageNames = mutableMapOf<String, String>()
        var imageBytes = 0L
        val extractor = EpubParser.ChapterExtractor(
            chapterPath = "kindle.xhtml",
            source = resourceSource,
            images = images,
            imageNames = imageNames,
            imageByteBudget = { imageBytes },
            onImageBytes = { imageBytes += it },
            imageSink = imageSink,
            storedImageNames = storedImageNames,
        )
        val chapter = extractor.extract(html)
        if (chapter.charCount == 0 && chapter.blocks.none { it is ImageBlock }) {
            throw EpubException("本文が見つかりません")
        }
        if (chapter.charCount > MAX_IMPORT_CHARS) {
            throw EpubException("本文が大きすぎて読み込めません")
        }
        return listOf(chapter.copy(title = bookTitle))
    }

    private fun chapterTitle(chapter: Chapter, fallback: String): String {
        val heading = chapter.blocks.filterIsInstance<TextBlock>()
            .firstOrNull { it.style == BlockStyle.HEADING || it.style == BlockStyle.SUBHEADING }
            ?.text
            ?.trim()
            ?.take(80)
        return heading?.takeIf { it.isNotEmpty() } ?: fallback
    }

    private fun findBoundary(db: PalmDb): Int? {
        for (i in 1 until db.recordCount) {
            if (db.recordSize(i) == 8) {
                val record = db.record(i)
                if (String(record, StandardCharsets.ISO_8859_1) == "BOUNDARY") return i
            }
        }
        return null
    }

    private fun extractResources(
        db: PalmDb,
        header: MobiHeader,
        endRecord: Int,
        imageDir: File?,
    ): ResourceSet {
        val names = ArrayList<String?>()
        val images = LinkedHashMap<String, ByteArray>()
        val byRecord = HashMap<Int, String>()
        val recordByName = HashMap<String, Int>()
        val start = header.firstResource.coerceIn(1, db.recordCount)
        val end = endRecord.coerceIn(start, db.recordCount)
        var total = 0L
        for (i in start until end) {
            val size = db.recordSize(i)
            val prefix = db.recordPrefix(i, 32)
            if (prefix.size < 4 || isMagicRecord(prefix)) {
                names += null
                continue
            }
            if (size.toLong() > MAX_KINDLE_IMAGE_BYTES || total > MAX_KINDLE_TOTAL_IMAGES) {
                names += null
                continue
            }
            val extension = imageExtension("", prefix)
            if (extension == null) {
                names += null
                continue
            }
            val name = "image-%05d.%s".format(java.util.Locale.ROOT, i, extension)
            byRecord[i] = name
            recordByName[name] = i
            names += name
            total += size
            if (imageDir == null) {
                images[name] = db.record(i)
            }
        }
        val source = MarkupResourceSource { path, maxBytes ->
            val key = path.removePrefix("./")
            if (!key.startsWith("images/")) {
                null
            } else {
                val name = key.removePrefix("images/")
                val memory = images[name]
                if (memory != null) {
                    memory
                } else {
                    val record = recordByName[name]
                    if (record != null && db.recordSize(record) <= maxBytes) {
                        db.record(record)
                    } else {
                        null
                    }
                }
            }
        }
        fun readByName(name: String): ByteArray? =
            images[name] ?: recordByName[name]?.let { record -> db.record(record) }

        val candidates = ArrayList<Int>()
        header.coverOffset?.let { candidates += start + it }
        if (header.coverUri != null) {
            val match = Regex("""kindle:embed:([0-9A-Va-v]+)""")
                .find(header.coverUri)
            val index = match?.groupValues?.getOrNull(1)?.let(::fromBase32) ?: -1
            if (index > 0) {
                names.getOrNull(index - 1)?.let { name ->
                    recordByName[name]?.let { candidates += it }
                }
            }
        }
        val coverBytes = candidates.firstNotNullOfOrNull { record ->
            byRecord[record]?.let { name -> readByName(name) }
        } ?: names.firstOrNull { it != null }?.let { name -> readByName(name!!) }
        return ResourceSet(names, images, source, coverBytes)
    }

    private fun isMagicRecord(data: ByteArray): Boolean {
        if (data.size >= 8 && String(data, 0, 8, StandardCharsets.ISO_8859_1) == "BOUNDARY") {
            return true
        }
        if (data.size >= 4) {
            when (String(data, 0, 4, StandardCharsets.ISO_8859_1)) {
                "FLIS", "FCIS", "FDST", "DATP", "SRCS", "CMET", "FONT", "CRES",
                "CONT", "RESC", "HUFF", "CDIC", "INDX", "TAGX", "kind", "AUDI", "VIDE",
                -> return true
            }
        }
        return data.size >= 4 &&
            data[0] == 0xE9.toByte() && data[1] == 0x8E.toByte() &&
            data[2] == 0x0D.toByte() && data[3] == 0x0A.toByte()
    }

    private fun rewriteMobi7(raw: ByteArray, resources: ResourceSet, codec: Charset): ByteArray {
        var text = raw.toString(codec)
        val recindex = Regex(
            """recindex\s*=\s*["']?0*(\d+)["']?""",
            RegexOption.IGNORE_CASE,
        )
        text = recindex.replace(text) { match ->
            val index = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            val name = resources.names.getOrNull(index - 1)
            if (name != null) """src="images/$name"""" else match.value
        }
        text = text.replace(
            Regex("""<mbp:pagebreak\s*/?>""", RegexOption.IGNORE_CASE),
            "<div class=\"pagebreak\"></div>",
        )
        return text.toByteArray(StandardCharsets.UTF_8)
    }

    private fun rewriteKf8(
        raw: ByteArray,
        resources: ResourceSet,
        codec: Charset = StandardCharsets.UTF_8,
    ): ByteArray {
        var text = raw.toString(codec)
        val embed = Regex(
            """kindle:embed:([0-9A-Va-v]+)(?:\?[^"'\s>]*)?""",
        )
        text = embed.replace(text) { match ->
            val index = fromBase32(match.groupValues[1])
            val name = resources.names.getOrNull(index - 1)
            if (name != null) "images/$name" else match.value
        }
        return text.toByteArray(StandardCharsets.UTF_8)
    }

    private fun charset(name: String): Charset = try {
        Charset.forName(name)
    } catch (_: Exception) {
        StandardCharsets.UTF_8
    }

    private fun fromBase32(value: String): Int {
        var result = 0
        value.forEach { ch ->
            val digit = when (ch) {
                in '0'..'9' -> ch - '0'
                in 'A'..'V' -> ch - 'A' + 10
                in 'a'..'v' -> ch - 'a' + 10
                else -> return -1
            }
            result = result * 32 + digit
        }
        return result
    }

    private data class ResourceSet(
        val names: List<String?>,
        val images: MutableMap<String, ByteArray>,
        val source: MarkupResourceSource,
        val coverBytes: ByteArray?,
    )

    private class PalmDb(file: File) : Closeable {
        private val raf = RandomAccessFile(file, "r")
        private val length = raf.length()
        val recordCount: Int
        private val offsets: IntArray

        init {
            val header = ByteArray(78)
            if (length >= header.size) {
                raf.seek(0)
                raf.readFully(header)
            }
            recordCount = readU16(header, 76).coerceAtLeast(0)
            offsets = IntArray(recordCount) { index ->
                val at = 78L + index * 8L
                if (at + 4 > length) {
                    0
                } else {
                    val bytes = ByteArray(4)
                    raf.seek(at)
                    raf.readFully(bytes)
                    readI32(bytes, 0).coerceIn(0, length.toInt())
                }
            }
        }

        fun recordSize(index: Int): Int {
            if (index !in 0 until recordCount) return 0
            val start = offsets[index].toLong()
            val end = if (index + 1 < recordCount) offsets[index + 1].toLong() else length
            if (start >= end || start >= length) return 0
            return min(end - start, length - start).toInt()
        }

        fun record(index: Int): ByteArray {
            val size = recordSize(index)
            if (size <= 0) return ByteArray(0)
            val bytes = ByteArray(size)
            raf.seek(offsets[index].toLong())
            raf.readFully(bytes)
            return bytes
        }

        fun recordPrefix(index: Int, maxBytes: Int): ByteArray {
            val size = recordSize(index).coerceAtMost(maxBytes)
            if (size <= 0) return ByteArray(0)
            val bytes = ByteArray(size)
            raf.seek(offsets[index].toLong())
            raf.readFully(bytes)
            return bytes
        }

        override fun close() {
            raf.close()
        }
    }

    private class MobiHeader(
        val start: Int,
        val record: ByteArray,
        val isPalmDoc: Boolean,
    ) {
        val compression: Int = readU16(record, 0)
        val textLength: Int = readI32(record, 4)
        val textRecords: Int = readU16(record, 8)
        val encrypted: Boolean = readU16(record, 12) != 0
        val version: Int
        val codePage: Int
        val headerLength: Int
        val title: String
        val authors: List<String>
        val language: String
        val firstResource: Int
        val coverOffset: Int?
        val coverUri: String?
        val exth: Map<Int, List<ByteArray>>
        private val trailingFlags: Int
        private val huffOffset: Int
        private val huffCount: Int
        val skelIndex: Int
        val fragIndex: Int
        val fdstIndex: Int
        val fdstCount: Int
        val textCharset: Charset

        init {
            if (isPalmDoc) {
                version = 0
                codePage = 1252
                headerLength = 16
                title = ""
                authors = emptyList()
                language = ""
                firstResource = 1
                coverOffset = null
                coverUri = null
                exth = emptyMap()
                trailingFlags = 0
                huffOffset = 0
                huffCount = 0
                skelIndex = -1
                fragIndex = -1
                fdstIndex = -1
                fdstCount = 0
                textCharset = charset("windows-1252")
            } else {
                if (record.size < 0x18 || String(record, 16, 4, StandardCharsets.ISO_8859_1) != "MOBI") {
                    throw EpubException("Kindleファイルではありません")
                }
                version = readI32(record, 0x24)
                codePage = readI32(record, 0x1C)
                headerLength = readI32(record, 0x14)
                textCharset = when (codePage) {
                    65001 -> StandardCharsets.UTF_8
                    932 -> charset("Shift_JIS")
                    936 -> charset("GBK")
                    949 -> charset("EUC-KR")
                    950 -> charset("Big5")
                    1200 -> charset("UTF-16LE")
                    else -> charset("windows-1252")
                }
                val titleOffset = readI32(record, 0x54)
                val titleLength = readI32(record, 0x58)
                val titleEnd = titleOffset.toLong() + titleLength
                val rawTitle = if (titleOffset >= 0 && titleLength > 0 &&
                    titleEnd <= record.size
                ) {
                    String(record, titleOffset, titleLength, textCharset)
                } else {
                    ""
                }
                val exthOffset = 16 + headerLength
                val hasExth = record.size >= exthOffset + 12 &&
                    readI32(record, 0x80) and 0x40 != 0
                val parsedExth = if (hasExth) parseExth(record, exthOffset) else emptyMap()
                exth = parsedExth
                title = parsedExth.string(503, textCharset)?.takeIf { it.isNotBlank() }
                    ?: rawTitle.ifBlank { "" }
                authors = parsedExth.strings(100, textCharset).filter { it.isNotBlank() }
                language = parsedExth.string(524, textCharset).orEmpty()
                val firstImage = readI32(record, 0x6C)
                val firstNonText = readI32(record, 0x50)
                firstResource = when {
                    firstImage != -1 && firstImage != 0xFFFFFFFF.toInt() -> start + firstImage
                    firstNonText != -1 && firstNonText != 0xFFFFFFFF.toInt() -> start + firstNonText
                    else -> start + 1
                }.coerceAtLeast(start + 1)
                coverOffset = parsedExth.intValue(201)
                coverUri = parsedExth.string(129, textCharset)
                trailingFlags = readU16(record, 0xF2)
                huffOffset = readI32(record, 0x70).let {
                    if (it == -1 || it == 0xFFFFFFFF.toInt()) -1 else start + it
                }
                huffCount = readI32(record, 0x74)
                skelIndex = readI32(record, 0xFC).let {
                    if (it == -1 || it == 0xFFFFFFFF.toInt()) -1 else start + it
                }
                fragIndex = readI32(record, 0xF8).let {
                    if (it == -1 || it == 0xFFFFFFFF.toInt()) -1 else start + it
                }
                fdstIndex = readI32(record, 0xC0).let {
                    if (it == -1 || it == 0xFFFFFFFF.toInt()) -1 else start + it
                }
                fdstCount = readI32(record, 0xC4)
            }
        }

        fun isPrintReplica(db: PalmDb): Boolean {
            if (isPalmDoc) return false
            val firstText = db.record(start + 1)
            return firstText.size >= 4 &&
                String(firstText, 0, 4, StandardCharsets.ISO_8859_1) == "%MOP"
        }

        fun readText(db: PalmDb): ByteArray {
            if (isPalmDoc) {
                val raw = decompressRecords(db, 1)
                return plainToHtml(raw)
            }
            val huff = if (compression == 0x4448) loadHuff(db) else null
            return decompressRecords(db, start + 1, huff)
        }

        private fun decompressRecords(
            db: PalmDb,
            firstRecord: Int,
            huff: HuffCdicReader? = null,
        ): ByteArray {
            val output = ByteArrayOutputStream(
                maxOf(textLength, 1024).coerceAtMost(8 * 1024 * 1024),
            )
            val count = min(textRecords, maxOf(0, db.recordCount - firstRecord))
            for (i in 0 until count) {
                var data = db.record(firstRecord + i)
                if (data.isEmpty()) continue
                if (!isPalmDoc && trailingFlags != 0) {
                    data = trimTrailing(data, trailingFlags)
                }
                val unpacked = when {
                    huff != null -> huff.decompress(data)
                    compression == 1 -> data
                    compression == 2 -> Palmdoc.decompress(data)
                    else -> throw EpubException("未対応のKindle圧縮形式です")
                }
                output.write(unpacked)
                if (output.size() > MAX_KINDLE_TEXT_BYTES) {
                    throw EpubException("本文が大きすぎて読み込めません")
                }
            }
            return output.toByteArray()
        }

        private fun loadHuff(db: PalmDb): HuffCdicReader {
            if (huffOffset < 0 || huffCount <= 0) {
                throw EpubException("Kindleの圧縮データが見つかりません")
            }
            val huff = db.record(huffOffset)
            val count = huffCount.coerceIn(1, maxOf(1, db.recordCount - huffOffset))
                .coerceAtMost(1024)
            val cdics = (1 until count).map { db.record(huffOffset + it) }
            return HuffCdicReader(huff, cdics)
        }

        private fun trimTrailing(data: ByteArray, flags: Int): ByteArray {
            var end = data.size
            var bits = flags ushr 1
            var trailers = 0
            while (bits > 0) {
                if (bits and 1 != 0) trailers++
                bits = bits ushr 1
            }
            var remaining = trailers
            while (remaining-- > 0 && end > 0) {
                val size = trailingEntrySize(data, end)
                if (size <= 0 || size > end) return data.copyOf(end)
                end -= size
            }
            if (flags and 1 != 0 && end > 0) {
                val overlap = (data[end - 1].toInt() and 3) + 1
                if (overlap <= end) end -= overlap
            }
            return data.copyOf(end)
        }

        private fun trailingEntrySize(data: ByteArray, end: Int): Int {
            var num = 0
            val from = maxOf(0, end - 4)
            for (i in from until end) {
                val value = data[i].toInt() and 0xFF
                if (value and 0x80 != 0) num = 0
                num = (num shl 7) or (value and 0x7F)
            }
            return num
        }

        private fun plainToHtml(raw: ByteArray): ByteArray {
            var text = raw.toString(StandardCharsets.UTF_8)
            if (text.contains('\uFFFD')) text = raw.toString(charset("windows-1252"))
            val looksHtml = Regex("""<\s*(html|body|p|div|br|h[1-6])\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)
            if (!looksHtml) {
                text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                text = text.split(Regex("""\r?\n\s*\r?\n"""))
                    .joinToString("") { "<p>${it.replace("\n", "<br/>")}</p>" }
            }
            return text.toByteArray(StandardCharsets.UTF_8)
        }

        companion object {
            fun parse(db: PalmDb, start: Int): MobiHeader {
                val record = db.record(start)
                if (record.isEmpty()) throw EpubException("Kindleファイルではありません")
                val hasMobi = record.size >= 20 &&
                    String(record, 16, 4, StandardCharsets.ISO_8859_1) == "MOBI"
                val isPalmDoc = !hasMobi && readU16(record, 0) in 1..2 &&
                    readU16(record, 8) > 0
                if (!hasMobi && !isPalmDoc) throw EpubException("Kindleファイルではありません")
                return MobiHeader(start, record, isPalmDoc)
            }
        }
    }

    private class Kf8Reconstructor(
        private val db: PalmDb,
        private val header: MobiHeader,
    ) {
        private data class Skeleton(
            val name: String,
            val fragCount: Int,
            val start: Int,
            val length: Int,
        )

        private data class Fragment(
            val insertPos: Int,
            val aid: String,
            val fileNumber: Int,
            val sequence: Int,
            val start: Int,
            val length: Int,
        )

        fun rebuild(raw: ByteArray): List<ByteArray> {
            if (header.skelIndex < 0 || header.fragIndex < 0) return listOf(raw)
            val flows = splitFlows(raw)
            val text = flows.firstOrNull() ?: raw
            val skeletons = readSkeletons()
            val fragments = readFragments()
            if (skeletons.isEmpty() || fragments.isEmpty()) return listOf(raw)
            val parts = ArrayList<ByteArray>()
            var fragPtr = 0
            for (skeletonInfo in skeletons) {
                val base = skeletonInfo.start + skeletonInfo.length
                if (base > text.size || skeletonInfo.start > base) break
                var skeleton = text.copyOfRange(skeletonInfo.start, base)
                var dataPtr = base
                val fragmentCount = min(skeletonInfo.fragCount, fragments.size - fragPtr)
                repeat(fragmentCount) {
                    val fragment = fragments[fragPtr++]
                    if (dataPtr + fragment.length > text.size) return@repeat
                    val slice = text.copyOfRange(dataPtr, dataPtr + fragment.length)
                    dataPtr += fragment.length
                    var insert = (fragment.insertPos - skeletonInfo.start)
                        .coerceIn(0, skeleton.size)
                    insert = fixInsertPosition(skeleton, insert, fragment.aid)
                    skeleton = skeleton.copyOfRange(0, insert) +
                        slice +
                        skeleton.copyOfRange(insert, skeleton.size)
                }
                parts += skeleton
            }
            return parts.filter { it.isNotEmpty() }
        }

        private fun splitFlows(raw: ByteArray): List<ByteArray> {
            if (header.fdstIndex < 0 || header.fdstCount <= 1) return listOf(raw)
            val fdst = db.record(header.fdstIndex)
            if (fdst.size < 12 || String(fdst, 0, 4, StandardCharsets.ISO_8859_1) != "FDST") {
                return listOf(raw)
            }
            val count = readI32(fdst, 8)
            if (count <= 0 || count > 10_000) return listOf(raw)
            val starts = IntArray(count)
            var position = 12
            for (i in 0 until count) {
                if (position + 8 > fdst.size) return listOf(raw)
                starts[i] = readI32(fdst, position)
                position += 8
            }
            val result = ArrayList<ByteArray>(count)
            for (i in 0 until count) {
                val start = starts[i].coerceIn(0, raw.size)
                val end = if (i + 1 < count) starts[i + 1].coerceIn(start, raw.size) else raw.size
                result += raw.copyOfRange(start, end)
            }
            return result
        }

        private fun readSkeletons(): List<Skeleton> {
            val index = MobiIndexReader(db).read(header.skelIndex)
            return index.entries.mapNotNull { entry ->
                val count = entry.tags[1]?.firstOrNull() ?: return@mapNotNull null
                val range = entry.tags[6] ?: return@mapNotNull null
                if (range.size < 2) return@mapNotNull null
                val name = entry.text.toString(StandardCharsets.UTF_8)
                Skeleton(name, count, range[0], range[1])
            }
        }

        private fun readFragments(): List<Fragment> {
            val index = MobiIndexReader(db).read(header.fragIndex)
            return index.entries.mapNotNull { entry ->
                val insert = entry.text.toString(StandardCharsets.UTF_8).toIntOrNull()
                    ?: return@mapNotNull null
                val ctocOffset = entry.tags[2]?.firstOrNull()
                val aid = ctocOffset?.let { index.ctoc[it] }
                    ?.toString(StandardCharsets.UTF_8)
                    ?.trim()
                    .orEmpty()
                val fileNumber = entry.tags[3]?.firstOrNull() ?: 0
                val sequence = entry.tags[4]?.firstOrNull() ?: 0
                val range = entry.tags[6] ?: return@mapNotNull null
                if (range.size < 2) return@mapNotNull null
                Fragment(insert, aid, fileNumber, sequence, range[0], range[1])
            }
        }

        private fun fixInsertPosition(skeleton: ByteArray, position: Int, aid: String): Int {
            val safe = position.coerceIn(0, skeleton.size)
            val head = skeleton.copyOfRange(0, safe)
            val tail = skeleton.copyOfRange(safe, skeleton.size)
            val broken = indexOf(tail, '>'.code.toByte()) < indexOf(tail, '<'.code.toByte()) ||
                indexOfLast(head, '>'.code.toByte()) < indexOfLast(head, '<'.code.toByte())
            if (!broken) return safe
            val cleanAid = aid.trim()
                .removePrefix("in: ")
                .removePrefix("before: ")
                .trim(':')
                .trim()
            if (cleanAid.isBlank()) return safe
            val needle = "aid=\"$cleanAid\""
            val alt = "aid='$cleanAid'"
            val text = skeleton.toString(StandardCharsets.ISO_8859_1)
            var at = text.indexOf(needle, ignoreCase = true)
            if (at < 0) at = text.indexOf(alt, ignoreCase = true)
            if (at >= 0) {
                val end = text.indexOf('>', at)
                if (end >= 0) return end + 1
            }
            return safe
        }

        private fun indexOf(data: ByteArray, byte: Byte): Int {
            for (i in data.indices) if (data[i] == byte) return i
            return -1
        }

        private fun indexOfLast(data: ByteArray, byte: Byte): Int {
            for (i in data.indices.reversed()) if (data[i] == byte) return i
            return -1
        }
    }

    private class MobiIndexReader(private val db: PalmDb) {
        data class Entry(val text: ByteArray, val tags: Map<Int, List<Int>>)
        data class Index(val entries: List<Entry>, val ctoc: Map<Int, ByteArray>)

        fun read(indexRecord: Int): Index {
            val main = db.record(indexRecord)
            if (main.size < 56 || String(main, 0, 4, StandardCharsets.ISO_8859_1) != "INDX") {
                return Index(emptyList(), emptyMap())
            }
            val headerLength = readI32(main, 4)
            val count = readI32(main, 24)
            val nctoc = readI32(main, 52)
            val ctoc = LinkedHashMap<Int, ByteArray>()
            var ctocOffset = 0
            for (j in 0 until nctoc.coerceIn(0, 1000)) {
                val data = db.record(indexRecord + count + 1 + j)
                readCtoc(data).forEach { (offset, value) ->
                    ctoc[offset + ctocOffset] = value
                }
                ctocOffset += 0x10000
            }
            val tagStart = headerLength
            if (tagStart < 0 || tagStart + 12 > main.size) return Index(emptyList(), ctoc)
            if (String(main, tagStart, 4, StandardCharsets.ISO_8859_1) != "TAGX") {
                return Index(emptyList(), ctoc)
            }
            val firstEntry = readI32(main, tagStart + 4)
            val controlBytes = readI32(main, tagStart + 8)
            val tags = ArrayList<TagSpec>()
            var pos = tagStart + 12
            while (pos + 4 <= tagStart + firstEntry && pos + 4 <= main.size) {
                tags += TagSpec(
                    tag = main[pos].toInt() and 0xFF,
                    valuesPerEntry = main[pos + 1].toInt() and 0xFF,
                    mask = main[pos + 2].toInt() and 0xFF,
                    endFlag = main[pos + 3].toInt() and 0xFF,
                )
                pos += 4
            }
            val entries = ArrayList<Entry>()
            for (section in indexRecord + 1..indexRecord + count) {
                val data = db.record(section)
                if (data.size < 32) continue
                val idxt = readI32(data, 20)
                val entriesInSection = readI32(data, 24)
                if (entriesInSection <= 0 || entriesInSection > 1_000_000) continue
                val positions = IntArray(entriesInSection)
                for (j in 0 until entriesInSection) {
                    val at = idxt + 4 + j * 2
                    if (at + 2 > data.size) continue
                    positions[j] = readU16(data, at)
                }
                for (j in 0 until entriesInSection) {
                    val startPos = positions[j]
                    if (startPos < 0 || startPos >= data.size) continue
                    val endPos = if (j + 1 < entriesInSection) {
                        positions[j + 1].coerceAtMost(data.size)
                    } else {
                        idxt.coerceAtMost(data.size)
                    }
                    if (endPos <= startPos) continue
                    val textLength = data[startPos].toInt() and 0xFF
                    val textEnd = (startPos + 1 + textLength).coerceAtMost(endPos)
                    val text = data.copyOfRange(startPos + 1, textEnd)
                    val tagMap = readTagMap(
                        controlBytes = controlBytes,
                        tags = tags,
                        data = data,
                        start = startPos + 1 + textLength,
                        end = endPos,
                    )
                    entries += Entry(text, tagMap)
                }
            }
            return Index(entries, ctoc)
        }

        private data class TagSpec(
            val tag: Int,
            val valuesPerEntry: Int,
            val mask: Int,
            val endFlag: Int,
        )

        private fun readCtoc(data: ByteArray): Map<Int, ByteArray> {
            val result = LinkedHashMap<Int, ByteArray>()
            var offset = 0
            while (offset < data.size) {
                if (data[offset].toInt() == 0) break
                val (consumed, length) = variableWidth(data, offset)
                offset += consumed
                if (length < 0 || offset + length > data.size) break
                result[offset - consumed] = data.copyOfRange(offset, offset + length)
                offset += length
            }
            return result
        }

        private fun readTagMap(
            controlBytes: Int,
            tags: List<TagSpec>,
            data: ByteArray,
            start: Int,
            end: Int,
        ): Map<Int, List<Int>> {
            val result = LinkedHashMap<Int, List<Int>>()
            var controlIndex = 0
            var dataStart = start + controlBytes
            val pending = ArrayList<Pair<Int, Pair<Int, Int?>>>()
            for (spec in tags) {
                if (spec.endFlag == 1) {
                    controlIndex++
                    continue
                }
                val controlAt = start + controlIndex
                val value = if (controlAt in data.indices) data[controlAt].toInt() and spec.mask else 0
                if (value != 0) {
                    if (value == spec.mask) {
                        if (Integer.bitCount(spec.mask) > 1) {
                            val (consumed, length) = variableWidth(data, dataStart)
                            dataStart += consumed
                            pending += spec.tag to (0 to length)
                        } else {
                            pending += spec.tag to (1 to null)
                        }
                    } else {
                        var shifted = value
                        var mask = spec.mask
                        while (mask and 1 == 0) {
                            mask = mask ushr 1
                            shifted = shifted ushr 1
                        }
                        pending += spec.tag to (shifted to null)
                    }
                }
            }
            for ((tag, pair) in pending) {
                val (countValue, byteCount) = pair
                val values = ArrayList<Int>()
                val maxValues = (end - start).coerceAtLeast(0) + 1
                if (byteCount == null) {
                    val wanted = countValue *
                        (tags.firstOrNull { it.tag == tag }?.valuesPerEntry.orZero())
                    repeat(wanted.coerceIn(0, maxValues)) {
                        val (consumed, value) = variableWidth(data, dataStart)
                        dataStart += consumed
                        values += value
                    }
                } else {
                    var consumedTotal = 0
                    var guard = 0
                    while (consumedTotal < byteCount && dataStart < data.size &&
                        guard++ < maxValues
                    ) {
                        val (consumed, value) = variableWidth(data, dataStart)
                        dataStart += consumed
                        consumedTotal += consumed
                        values += value
                    }
                }
                result[tag] = values
            }
            return result
        }

        private fun Int?.orZero(): Int = this ?: 1
    }

    private class Palmdoc {
        companion object {
            fun decompress(data: ByteArray): ByteArray {
                val out = GrowableBytes(maxOf(data.size * 2, 256))
                var position = 0
                while (position < data.size) {
                    val c = data[position].toInt() and 0xFF
                    position++
                    when {
                        c in 1..8 -> {
                            val count = min(c, data.size - position)
                            out.write(data, position, count)
                            position += count
                        }
                        c < 0x80 -> out.writeByte(c)
                        c >= 0xC0 -> {
                            out.writeByte(' '.code)
                            out.writeByte(c xor 0x80)
                        }
                        else -> {
                            if (position >= data.size) break
                            val c2 = (c shl 8) or (data[position].toInt() and 0xFF)
                            position++
                            val distance = (c2 ushr 3) and 0x07FF
                            val length = (c2 and 7) + 3
                            out.copyBack(distance, length)
                        }
                    }
                }
                return out.toByteArray()
            }
        }
    }

    private class GrowableBytes(initial: Int) {
        private var data = ByteArray(initial.coerceAtLeast(32))
        private var size = 0

        fun writeByte(value: Int) {
            ensure(1)
            data[size++] = value.toByte()
        }

        fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length <= 0) return
            ensure(length)
            System.arraycopy(bytes, offset, data, size, length)
            size += length
        }

        fun copyBack(distance: Int, length: Int) {
            if (distance <= 0 || distance > size) {
                repeat(length) { writeByte(0) }
                return
            }
            var source = size - distance
            repeat(length) {
                ensure(1)
                data[size++] = data[source++]
            }
        }

        fun toByteArray(): ByteArray = data.copyOf(size)

        private fun ensure(extra: Int) {
            if (size + extra <= data.size) return
            var next = data.size
            while (next < size + extra) next *= 2
            data = data.copyOf(next)
        }
    }

    private class HuffCdicReader(huff: ByteArray, cdics: List<ByteArray>) {
        private data class Dict1(val codeLength: Int, val term: Boolean, val maxCode: Long)
        private data class Phrase(val data: ByteArray, val terminal: Boolean)

        private val dict1 = arrayOfNulls<Dict1>(256)
        private val minCode = LongArray(33)
        private val maxCode = LongArray(33)
        private val dictionary = ArrayList<Phrase?>()

        init {
            loadHuff(huff)
            cdics.forEach { loadCdic(it) }
        }

        fun decompress(data: ByteArray): ByteArray {
            val output = ByteArrayOutputStream(maxOf(data.size * 2, 256))
            unpack(data, output, 0)
            return output.toByteArray()
        }

        private fun unpack(data: ByteArray, output: ByteArrayOutputStream, depth: Int) {
            if (depth > 64) throw EpubException("Kindleの圧縮データを展開できません")
            var bitsLeft = data.size * 8
            val padded = data + ByteArray(8)
            var position = 0
            var x = readU64(padded, position)
            var n = 32
            while (true) {
                if (n <= 0) {
                    position += 4
                    x = readU64(padded, position)
                    n += 32
                }
                val code = (x ushr n) and 0xFFFFFFFFL
                val first = dict1[((code ushr 24) and 0xFF).toInt()]
                    ?: throw EpubException("Kindleの圧縮データを展開できません")
                var codeLength = first.codeLength
                var max = first.maxCode
                if (!first.term) {
                    while (codeLength < 32 && code < minCode[codeLength]) codeLength++
                    max = maxCode[codeLength]
                }
                n -= codeLength
                bitsLeft -= codeLength
                if (bitsLeft < 0) break
                if (codeLength <= 0 || codeLength > 32) break
                val index = ((max - code) ushr (32 - codeLength)).toInt()
                if (index < 0 || index >= dictionary.size) break
                val phrase = dictionary[index] ?: break
                val value = if (phrase.terminal) {
                    phrase.data
                } else {
                    val expanded = ByteArrayOutputStream()
                    unpack(phrase.data, expanded, depth + 1)
                    val bytes = expanded.toByteArray()
                    dictionary[index] = Phrase(bytes, true)
                    bytes
                }
                output.write(value)
                if (output.size() > HUFF_OUTPUT_LIMIT) {
                    throw EpubException("Kindleの圧縮データが大きすぎます")
                }
            }
        }

        private fun loadHuff(huff: ByteArray) {
            if (huff.size < 16 ||
                String(huff, 0, 8, StandardCharsets.ISO_8859_1) != "HUFF\u0000\u0000\u0000\u0018"
            ) {
                throw EpubException("Kindleの圧縮データを展開できません")
            }
            val off1 = readI32(huff, 8)
            val off2 = readI32(huff, 12)
            for (i in 0 until 256) {
                val value = readI32(huff, off1 + i * 4).toLong() and 0xFFFFFFFFL
                val codeLength = (value and 0x1F).toInt()
                if (codeLength <= 0 || codeLength > 32) {
                    dict1[i] = Dict1(32, true, -1L)
                    continue
                }
                val term = value and 0x80 != 0L
                val base = value ushr 8
                val max = ((base + 1) shl (32 - codeLength)) - 1
                dict1[i] = Dict1(codeLength, term, max)
            }
            minCode[0] = 0
            maxCode[0] = 0
            for (i in 1..32) {
                val min = readI32(huff, off2 + (i - 1) * 8).toLong() and 0xFFFFFFFFL
                val max = readI32(huff, off2 + (i - 1) * 8 + 4).toLong() and 0xFFFFFFFFL
                minCode[i] = min shl (32 - i)
                maxCode[i] = ((max + 1) shl (32 - i)) - 1
            }
        }

        private fun loadCdic(cdic: ByteArray) {
            if (cdic.size < 16 ||
                String(cdic, 0, 8, StandardCharsets.ISO_8859_1) != "CDIC\u0000\u0000\u0000\u0010"
            ) {
                throw EpubException("Kindleの圧縮データを展開できません")
            }
            val phrases = readI32(cdic, 8)
            val bits = readI32(cdic, 12)
            if (bits !in 0..20) throw EpubException("Kindleの圧縮データを展開できません")
            val count = min(1 shl bits, phrases - dictionary.size)
            if (count <= 0) return
            for (i in 0 until count) {
                val at = 16 + i * 2
                if (at + 2 > cdic.size) break
                val offset = readU16(cdic, at)
                val lengthAt = 16 + offset
                if (lengthAt + 2 > cdic.size) continue
                val length = readU16(cdic, lengthAt)
                val start = 18 + offset
                val end = (start + (length and 0x7FFF)).coerceAtMost(cdic.size)
                if (start > end) continue
                dictionary += Phrase(
                    cdic.copyOfRange(start, end),
                    length and 0x8000 != 0,
                )
            }
        }
    }

    private fun parseExth(
        record: ByteArray,
        offset: Int,
    ): Map<Int, List<ByteArray>> {
        if (offset + 12 > record.size) return emptyMap()
        if (String(record, offset, 4, StandardCharsets.ISO_8859_1) != "EXTH") return emptyMap()
        val count = readI32(record, offset + 8)
        if (count <= 0 || count > 100_000) return emptyMap()
        val result = LinkedHashMap<Int, MutableList<ByteArray>>()
        var pos = offset + 12
        repeat(count) {
            if (pos + 8 > record.size) return@repeat
            val type = readI32(record, pos)
            val size = readI32(record, pos + 4)
            val end = pos.toLong() + size
            if (size < 8 || end > record.size) return@repeat
            val value = record.copyOfRange(pos + 8, pos + size)
            result.getOrPut(type) { ArrayList() } += value
            pos += size
        }
        return result
    }

    private fun Map<Int, List<ByteArray>>.string(type: Int, codec: Charset): String? =
        this[type]?.firstOrNull()?.toString(codec)?.trim()

    private fun Map<Int, List<ByteArray>>.strings(type: Int, codec: Charset): List<String> =
        this[type].orEmpty().map { it.toString(codec).trim() }

    private fun Map<Int, List<ByteArray>>.intValue(type: Int): Int? {
        val bytes = this[type]?.firstOrNull() ?: return null
        return when (bytes.size) {
            1 -> bytes[0].toInt() and 0xFF
            2 -> readU16(bytes, 0)
            4 -> readI32(bytes, 0)
            else -> null
        }
    }

    private fun readU16(data: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun readI32(data: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private fun readU64(data: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            val byte = if (offset + i in data.indices) data[offset + i].toInt() and 0xFF else 0
            value = (value shl 8) or byte.toLong()
        }
        return value
    }

    private fun variableWidth(data: ByteArray, offset: Int): Pair<Int, Int> {
        if (offset !in data.indices) return 1 to 0
        var value = 0
        var consumed = 0
        while (offset + consumed < data.size) {
            val byte = data[offset + consumed].toInt() and 0xFF
            consumed++
            value = (value shl 7) or (byte and 0x7F)
            if (byte and 0x80 != 0) break
            if (consumed > 8) break
        }
        return consumed to value
    }
}
