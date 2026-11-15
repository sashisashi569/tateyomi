package app.tateyomi

import app.tateyomi.data.BlockStyle
import app.tateyomi.data.EpubException
import app.tateyomi.data.EpubParser
import app.tateyomi.data.ImageBlock
import app.tateyomi.data.TextBlock
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val pixelPng: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lK3Q6wAAAABJRU5ErkJggg==",
    )

    @Test
    fun parsesMetadataStructureRubyImagesAndToc() {
        val epub = writeEpub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles>
                        <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OPS/content.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>テスト縦読み</dc:title>
                        <dc:creator>著者A</dc:creator>
                        <dc:creator>著者B</dc:creator>
                        <dc:language>ja</dc:language>
                      </metadata>
                      <manifest>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="front" href="front.xhtml" media-type="application/xhtml+xml"/>
                        <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="pixel" href="images/pixel.png" media-type="image/png"/>
                        <item id="cover" href="images/cover.png" media-type="image/png" properties="cover-image"/>
                      </manifest>
                      <spine>
                        <itemref idref="front"/>
                        <itemref idref="ch1"/>
                        <itemref idref="ch2"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "OPS/nav.xhtml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                      <body>
                        <nav epub:type="toc">
                          <ol>
                            <li><a href="ch1.xhtml#top">第一話だいいちわ</a>
                              <ol>
                                <li><a href="ch1.xhtml#sub">小見出しこみだし</a></li>
                              </ol>
                            </li>
                            <li><a href="ch2.xhtml">第二話</a></li>
                          </ol>
                        </nav>
                      </body>
                    </html>
                """.trimIndent(),
                "OPS/front.xhtml" to "<html><body><div></div></body></html>",
                "OPS/ch1.xhtml" to """
                    <html><body>
                      <h1 id="top">第一話</h1>
                      <p>これは<ruby>漢字<rt>かんじ</rt></ruby>のテストです。</p>
                      <h3 id="sub">小見出し</h3>
                      <p><strong>強調</strong>と<img src="images/pixel.png" alt="点"/></p>
                    </body></html>
                """.trimIndent(),
                "OPS/ch2.xhtml" to "<html><body><p>二章の本文です。</p></body></html>",
                "OPS/images/pixel.png" to pixelPng,
                "OPS/images/cover.png" to pixelPng,
            ),
        )

        val parsed = EpubParser.parse(epub)
        assertEquals("テスト縦読み", parsed.title)
        assertEquals("著者A、著者B", parsed.author)
        assertEquals("ja", parsed.language)
        assertEquals(2, parsed.chapters.size)
        assertEquals("第一話", parsed.chapters[0].title)
        assertEquals("第二話", parsed.chapters[1].title)
        assertEquals(3, parsed.toc.size)
        assertEquals("第一話", parsed.toc[0].title)
        assertEquals(0, parsed.toc[0].chapterIndex)
        assertEquals(0, parsed.toc[0].blockIndex)
        assertEquals("小見出し", parsed.toc[1].title)
        assertEquals(0, parsed.toc[1].chapterIndex)
        assertEquals(1, parsed.toc[1].level)
        assertNotNull(parsed.toc[1].blockIndex)
        assertEquals("第二話", parsed.toc[2].title)
        assertEquals(1, parsed.toc[2].chapterIndex)
        assertTrue(parsed.charCount > 0)

        val firstChapterText = parsed.chapters[0].blocks.filterIsInstance<TextBlock>()
        assertTrue(firstChapterText.any { it.style == BlockStyle.HEADING && it.text == "第一話" })
        assertTrue(firstChapterText.any { block -> block.ruby.any { it.text == "かんじ" } })
        assertTrue(firstChapterText.any { block -> block.bold.isNotEmpty() })
        assertTrue(
            parsed.chapters[0].blocks.filterIsInstance<TextBlock>()
                .any { it.inlineImages.isNotEmpty() },
        )
        assertEquals(1, parsed.images.size)
        assertTrue(parsed.chapters.none { it.charCount == 0 && it.blocks.isEmpty() })
        assertNotNull(parsed.coverBytes)
        assertEquals("png", parsed.coverExtension)
    }

    @Test
    fun parsesPrefixedOpfAndImageOnlySpine() {
        val epub = writeEpub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles><rootfile full-path="OEBPS/book.opf"/></rootfiles>
                    </container>
                """.trimIndent(),
                "OEBPS/book.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <opf:package xmlns:opf="http://www.idpf.org/2007/opf"
                                 xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0">
                      <opf:metadata>
                        <dc:title>画像だけの本</dc:title>
                        <opf:meta property="rendition:layout">pre-paginated</opf:meta>
                      </opf:metadata>
                      <opf:manifest>
                        <opf:item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <opf:item id="page1" href="images/page1.png" media-type="image/png"/>
                      </opf:manifest>
                      <opf:spine>
                        <opf:itemref idref="page1"/>
                      </opf:spine>
                    </opf:package>
                """.trimIndent(),
                "OEBPS/nav.xhtml" to """
                    <html xmlns:epub="http://www.idpf.org/2007/ops"><body>
                      <nav epub:type="toc"><ol><li><a href="images/page1.png">頁一</a></li></ol></nav>
                    </body></html>
                """.trimIndent(),
                "OEBPS/images/page1.png" to pixelPng,
            ),
        )

        val parsed = EpubParser.parse(epub)
        assertEquals("画像だけの本", parsed.title)
        assertEquals(1, parsed.chapters.size)
        assertEquals("頁一", parsed.chapters[0].title)
        assertEquals(1, parsed.toc.size)
        assertTrue(parsed.chapters[0].blocks.single() is ImageBlock)
        assertEquals(1, parsed.images.size)
        assertNotNull(parsed.coverBytes)
        assertEquals(0, parsed.charCount)
    }

    @Test
    fun keepsInlineGaijiImagesInsideTheTextBlock() {
        val epub = writeEpub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles>
                        <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OPS/content.opf" to """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>外字テスト</dc:title>
                      </metadata>
                      <manifest>
                        <item id="text" href="text.xhtml" media-type="application/xhtml+xml"/>
                        <item id="gaiji" href="images/gaiji.png" media-type="image/png"/>
                      </manifest>
                      <spine><itemref idref="text"/></spine>
                    </package>
                """.trimIndent(),
                "OPS/text.xhtml" to """
                    <html><body>
                      <p>前<img class="gaiji" src="images/gaiji.png" alt="引用符"/>後</p>
                    </body></html>
                """.trimIndent(),
                "OPS/images/gaiji.png" to pixelPng,
            ),
        )

        val parsed = EpubParser.parse(epub)
        assertEquals(1, parsed.chapters.size)
        val block = parsed.chapters[0].blocks.filterIsInstance<TextBlock>().single()
        assertEquals("前\uFFFC後", block.text)
        assertEquals(1, block.inlineImages.size)
        assertEquals("引用符", block.inlineImages[0].alt)
        assertTrue(parsed.chapters[0].blocks.none { it is ImageBlock })
        assertEquals(1, parsed.images.size)
    }

    @Test
    fun promotesSoloParagraphImagesToFullPageIllustrations() {
        val epub = writeEpub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles>
                        <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OPS/content.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>挿絵テスト</dc:title>
                      </metadata>
                      <manifest>
                        <item id="text" href="text.xhtml" media-type="application/xhtml+xml"/>
                        <item id="illust" href="images/illust.png" media-type="image/png"/>
                      </manifest>
                      <spine><itemref idref="text"/></spine>
                    </package>
                """.trimIndent(),
                "OPS/text.xhtml" to """
                    <html><body>
                      <p>挿絵の前の段落です。</p>
                      <p class="illust"><img src="images/illust.png" alt="挿絵"/></p>
                      <p>挿絵の後の段落です。</p>
                    </body></html>
                """.trimIndent(),
                "OPS/images/illust.png" to pixelPng,
            ),
        )

        val parsed = EpubParser.parse(epub)
        assertEquals(1, parsed.chapters.size)
        val blocks = parsed.chapters[0].blocks
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is TextBlock)
        assertTrue(blocks[1] is ImageBlock)
        assertTrue(blocks[2] is TextBlock)
        assertTrue(blocks.filterIsInstance<TextBlock>().none { it.inlineImages.isNotEmpty() })
        assertEquals(1, parsed.images.size)
    }

    @Test
    fun keepsGaijiWrappedInSpanAloneInsideParagraphInline() {
        val epub = writeEpub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles>
                        <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OPS/content.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>外字段落テスト</dc:title>
                      </metadata>
                      <manifest>
                        <item id="text" href="text.xhtml" media-type="application/xhtml+xml"/>
                        <item id="gaiji" href="images/gaiji.png" media-type="image/png"/>
                      </manifest>
                      <spine><itemref idref="text"/></spine>
                    </package>
                """.trimIndent(),
                "OPS/text.xhtml" to """
                    <html><body>
                      <p><span class="gaiji"><img src="images/gaiji.png" alt="字"/></span></p>
                    </body></html>
                """.trimIndent(),
                "OPS/images/gaiji.png" to pixelPng,
            ),
        )

        val parsed = EpubParser.parse(epub)
        assertEquals(1, parsed.chapters.size)
        val block = parsed.chapters[0].blocks.filterIsInstance<TextBlock>().single()
        assertEquals("\uFFFC", block.text)
        assertEquals(1, block.inlineImages.size)
        assertTrue(parsed.chapters[0].blocks.none { it is ImageBlock })
    }

    @Test
    fun keepsSvgWrappedIllustrationPages() {
        val epub = writeEpub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles>
                        <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "OPS/content.opf" to """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>SVG挿絵テスト</dc:title>
                      </metadata>
                      <manifest>
                        <item id="plate" href="xhtml/plate.xhtml" media-type="application/xhtml+xml" properties="svg"/>
                        <item id="text" href="xhtml/text.xhtml" media-type="application/xhtml+xml"/>
                        <item id="image" href="image/plate.png" media-type="image/png"/>
                      </manifest>
                      <spine>
                        <itemref idref="plate"/>
                        <itemref idref="text"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "OPS/xhtml/plate.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml"
                          xmlns:xlink="http://www.w3.org/1999/xlink">
                      <body><div class="main">
                        <svg version="1.1" viewBox="0 0 1443 2048">
                          <image width="1443" height="2048" xlink:href="../image/plate.png"/>
                        </svg>
                      </div></body>
                    </html>
                """.trimIndent(),
                "OPS/xhtml/text.xhtml" to "<html><body><p>SVGの後の本文です。</p></body></html>",
                "OPS/image/plate.png" to pixelPng,
            ),
        )

        val parsed = EpubParser.parse(epub)
        assertEquals(2, parsed.chapters.size)
        assertTrue(parsed.chapters[0].blocks.single() is ImageBlock)
        assertEquals(1, parsed.images.size)
        assertTrue(parsed.chapters[1].blocks.filterIsInstance<TextBlock>().any {
            it.text.contains("SVGの後の本文")
        })
    }

    @Test
    fun rejectsNonZipFile() {
        val file = temporaryFolder.newFile("broken.epub")
        file.writeText("this is not an epub")
        var threw = false
        try {
            EpubParser.parse(file)
        } catch (_: EpubException) {
            threw = true
        }
        assertTrue(threw)
    }

    private fun writeEpub(files: Map<String, Any>): File {
        val file = temporaryFolder.newFile("book-${System.nanoTime()}.epub")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                when (content) {
                    is ByteArray -> zip.write(content)
                    is String -> zip.write(content.toByteArray(Charsets.UTF_8))
                    else -> error("Unsupported entry type")
                }
                zip.closeEntry()
            }
        }
        return file
    }
}
