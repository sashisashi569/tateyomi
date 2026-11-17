package app.tateyomi.data

import be.stef.rar.Unrar5j
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile

private const val MAX_ENTRY_BYTES = 1024L * 1024 * 1024
private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

enum class ContainerFormat {
    ZIP,
    RAR,
    SEVEN_Z,
}

/**
 * Extracts zip / rar / 7z containers used for bulk book imports. Single-book
 * formats (EPUB, CBZ, CBR) are still handled by their own parsers.
 */
object ArchiveExtractor {

    private val BOOK_EXTENSIONS = setOf(
        "epub", "cbz", "cbr", "mobi", "azw", "azw3", "prc", "pdb",
    )
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun detect(file: File): ContainerFormat? {
        val header = readPrefix(file, 8)
        if (header.size >= 4 &&
            header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            (header[2] == 3.toByte() || header[2] == 5.toByte() || header[2] == 7.toByte())
        ) {
            return ContainerFormat.ZIP
        }
        if (header.size >= 4 &&
            header[0] == 'R'.code.toByte() && header[1] == 'a'.code.toByte() &&
            header[2] == 'r'.code.toByte() && header[3] == '!'.code.toByte()
        ) {
            return ContainerFormat.RAR
        }
        if (header.size >= 6 &&
            header[0] == 0x37.toByte() && header[1] == 0x7A.toByte() &&
            header[2] == 0xBC.toByte() && header[3] == 0xAF.toByte() &&
            header[4] == 0x27.toByte() && header[5] == 0x1C.toByte()
        ) {
            return ContainerFormat.SEVEN_Z
        }
        return null
    }

    fun zipHasBookEntries(file: File): Boolean {
        return try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().any { entry ->
                    !entry.isDirectory && isBookFileName(entry.name)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun extract(file: File, format: ContainerFormat, destination: File) {
        destination.mkdirs()
        when (format) {
            ContainerFormat.ZIP -> extractZip(file, destination)
            ContainerFormat.RAR -> extractRar(file, destination)
            ContainerFormat.SEVEN_Z -> extractSevenZ(file, destination)
        }
    }

    fun findBookFiles(root: File): List<File> {
        return root.walkTopDown()
            .filter { it.isFile && isBookFileName(it.relativeTo(root).invariantSeparatorsPath) }
            .sortedWith(compareBy(naturalOrder()) { it.relativeTo(root).invariantSeparatorsPath })
            .toList()
    }

    fun findImageFiles(root: File): List<File> {
        return root.walkTopDown()
            .filter { it.isFile && isImageFileName(it.relativeTo(root).invariantSeparatorsPath) }
            .sortedWith(compareBy(naturalOrder()) { it.relativeTo(root).invariantSeparatorsPath })
            .toList()
    }

    fun isBookFileName(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val name = normalized.substringAfterLast('/')
        if (name.isEmpty() || name.startsWith(".") || name.startsWith("._")) return false
        if (normalized.startsWith("__MACOSX/")) return false
        return name.substringAfterLast('.', "").lowercase() in BOOK_EXTENSIONS
    }

    fun isImageFileName(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val name = normalized.substringAfterLast('/')
        if (name.isEmpty() || name.startsWith(".") || name.startsWith("._")) return false
        if (normalized.startsWith("__MACOSX/")) return false
        return name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }

    private fun extractZip(file: File, destination: File) {
        ZipFile(file).use { zip ->
            var total = 0L
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                if (entry.name.startsWith("__MACOSX/") || entry.name.substringAfterLast('/').startsWith(".")) {
                    return@forEach
                }
                val target = safeTarget(destination, entry.name)
                if (entry.size > MAX_ENTRY_BYTES) return@forEach
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output ->
                        total += copyLimited(input, output, MAX_ENTRY_BYTES)
                    }
                }
                if (total > MAX_TOTAL_BYTES) {
                    throw EpubException("アーカイブの展開サイズが大きすぎます")
                }
            }
        }
    }

    private fun extractRar(file: File, destination: File) {
        try {
            val result = Unrar5j.extract(file.absolutePath, destination.absolutePath, null)
            val files = destination.walkTopDown().filter { it.isFile }.toList()
            if (result.errorCount > 0 && files.isEmpty()) {
                throw EpubException("RARを読み込めませんでした（破損または暗号化）")
            }
            if (files.sumOf { it.length() } > MAX_TOTAL_BYTES) {
                throw EpubException("アーカイブの展開サイズが大きすぎます")
            }
        } catch (e: EpubException) {
            throw e
        } catch (_: Exception) {
            throw EpubException("RARを読み込めませんでした（破損または暗号化）")
        }
    }

    private fun extractSevenZ(file: File, destination: File) {
        try {
            SevenZFile.builder().setFile(file).get().use { archive ->
                var total = 0L
                while (true) {
                    val entry = archive.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name.startsWith("__MACOSX/") ||
                        entry.name.substringAfterLast('/').startsWith(".")
                    ) {
                        continue
                    }
                    val target = safeTarget(destination, entry.name)
                    if (entry.size > MAX_ENTRY_BYTES) continue
                    target.parentFile?.mkdirs()
                    archive.getInputStream(entry).use { input ->
                        target.outputStream().buffered().use { output ->
                            total += copyLimited(input, output, MAX_ENTRY_BYTES)
                        }
                    }
                    if (total > MAX_TOTAL_BYTES) {
                        throw EpubException("アーカイブの展開サイズが大きすぎます")
                    }
                }
            }
        } catch (e: EpubException) {
            throw e
        } catch (_: Exception) {
            throw EpubException("7zを読み込めませんでした（破損または暗号化）")
        }
    }

    private fun safeTarget(root: File, entryName: String): File {
        val normalized = entryName.replace('\\', '/').removePrefix("./").removePrefix("/")
        val target = File(root, normalized)
        val rootPath = root.canonicalFile.path + File.separator
        if (!target.canonicalFile.path.startsWith(rootPath)) {
            throw EpubException("アーカイブ内のパスが不正です")
        }
        return target
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw EpubException("アーカイブ内のファイルが大きすぎます")
            output.write(buffer, 0, read)
        }
        return total
    }

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
}
