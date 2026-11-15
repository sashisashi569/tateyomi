package app.tateyomi.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Reads the family name from the OpenType/TrueType `name` table. Android's
 * Typeface API does not expose a family name, and file names are often
 * machine-oriented (e.g. NotoSerifJP-Regular.otf).
 */
object FontMetadata {

    fun familyName(file: File): String? = runCatching { readFamilyName(file) }.getOrNull()

    private fun readFamilyName(file: File): String? {
        if (!file.isFile || file.length() < 12) return null
        RandomAccessFile(file, "r").use { raf ->
            val base = sfntBase(raf) ?: return null
            raf.seek(base)
            val tag = ByteArray(4)
            raf.readFully(tag)
            if (String(tag, Charsets.ISO_8859_1) !in setOf("OTTO", "true", "typ1") &&
                !(tag[0] == 0.toByte() && tag[1] == 1.toByte() && tag[2] == 0.toByte() && tag[3] == 0.toByte())
            ) {
                return null
            }

            raf.seek(base + 4)
            val numTables = readU16(raf)
            if (numTables <= 0 || numTables > 4096) return null

            var nameOffset = -1L
            var nameLength = 0L
            for (index in 0 until numTables) {
                raf.seek(base + 12 + index * 16L)
                val tableTag = ByteArray(4)
                raf.readFully(tableTag)
                raf.skipBytes(4) // checksum
                val offset = readU32(raf)
                val length = readU32(raf)
                if (String(tableTag, Charsets.ISO_8859_1) == "name") {
                    nameOffset = offset
                    nameLength = length
                    break
                }
            }
            if (nameOffset < 0 || nameLength < 6 || nameOffset >= file.length()) return null
            val safeLength = kotlin.math.min(nameLength, 4L * 1024 * 1024).toInt()
            val table = ByteArray(safeLength)
            raf.seek(nameOffset)
            raf.readFully(table)
            return parseNameTable(table)
        }
    }

    private fun sfntBase(raf: RandomAccessFile): Long? {
        raf.seek(0)
        val head = ByteArray(4)
        raf.readFully(head)
        return when (String(head, Charsets.ISO_8859_1)) {
            "ttcf" -> {
                raf.seek(8)
                val numFonts = readU32(raf)
                if (numFonts <= 0 || numFonts > 1024) return null
                raf.seek(12)
                readU32(raf)
            }
            "OTTO", "true", "typ1" -> 0L
            else -> {
                if (head[0] == 0.toByte() && head[1] == 1.toByte() &&
                    head[2] == 0.toByte() && head[3] == 0.toByte()
                ) {
                    0L
                } else {
                    null
                }
            }
        }
    }

    private fun parseNameTable(table: ByteArray): String? {
        fun u16(offset: Int): Int {
            if (offset < 0 || offset + 1 >= table.size) return -1
            return ((table[offset].toInt() and 0xFF) shl 8) or (table[offset + 1].toInt() and 0xFF)
        }

        val count = u16(2)
        val stringOffset = u16(4)
        if (count <= 0 || count > 10_000 || stringOffset < 6) return null

        var bestScore = -1
        var bestName = ""
        for (index in 0 until count) {
            val record = 6 + index * 12
            if (record + 12 > table.size) break
            val platform = u16(record)
            val language = u16(record + 4)
            val nameId = u16(record + 6)
            val length = u16(record + 8)
            val offset = u16(record + 10)
            if (nameId != 1 && nameId != 16) continue
            if (length <= 0 || offset < 0) continue
            val start = stringOffset + offset
            val end = start + length
            if (start < 0 || end > table.size) continue

            val text = decode(platform, table.copyOfRange(start, end))
                ?.trim()
                ?.replace("\u0000", "")
                ?.trim()
                ?: continue
            if (text.isBlank()) continue

            var score = if (nameId == 16) 30 else 0
            score += when (platform) {
                3 -> 20
                0 -> 15
                1 -> 5
                else -> 0
            }
            if (platform == 3 && language == 0x409) score += 5
            if (platform == 3 && language == 0x411) score += 4
            if (score > bestScore) {
                bestScore = score
                bestName = text.take(80)
            }
        }
        return bestName.ifBlank { null }
    }

    private fun decode(platform: Int, bytes: ByteArray): String? = when (platform) {
        0, 3 -> String(bytes, Charsets.UTF_16BE)
        1 -> runCatching {
            String(bytes, Charset.forName("x-MacRoman"))
        }.getOrElse {
            String(bytes, Charsets.ISO_8859_1)
        }
        else -> null
    }

    private fun readU16(raf: RandomAccessFile): Int {
        val high = raf.read()
        val low = raf.read()
        if (high < 0 || low < 0) return -1
        return (high shl 8) or low
    }

    private fun readU32(raf: RandomAccessFile): Long {
        val high = readU16(raf).toLong()
        val low = readU16(raf).toLong()
        if (high < 0 || low < 0) return -1
        return (high shl 16) or low
    }
}
