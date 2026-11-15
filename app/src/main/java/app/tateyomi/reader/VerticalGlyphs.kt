package app.tateyomi.reader

/**
 * Symbols that must be rotated manually in vertical writing when the selected
 * system font does not provide a `vert` alternate. The renderer draws these
 * with the font feature disabled and rotates the horizontal glyph by 90°.
 */
internal val ManuallyRotatedInVertical: Set<Char> = setOf(
    '\u2026', // …
    '\u2025', // ‥
    '\u22EF', // ⋯
    '\u2013', // –
    '\u2014', // —
    '\u2015', // ―
    '\u301C', // 〜
    '\uFF5E', // ～
    '\uFF0D', // －
    '\u30FC', // ー
    '\uFF70', // ｰ
)

internal fun needsManualVerticalRotation(ch: Char): Boolean =
    ch in ManuallyRotatedInVertical
