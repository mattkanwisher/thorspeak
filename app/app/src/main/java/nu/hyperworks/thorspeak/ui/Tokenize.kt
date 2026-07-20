package nu.hyperworks.thorspeak.ui

/**
 * Split Japanese text into tappable tokens by script-class runs (kanji run,
 * kana run, latin run, punctuation). No tokenizer needed — a kanji run is
 * usually exactly what you want to look up.
 */
object Tokenize {

    private enum class CharClass { KANJI, HIRAGANA, KATAKANA, LATIN, OTHER }

    private fun classify(c: Char): CharClass = when {
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF -> CharClass.KANJI
        c.code in 0x3040..0x309F -> CharClass.HIRAGANA
        c.code in 0x30A0..0x30FF || c.code in 0x31F0..0x31FF -> CharClass.KATAKANA
        c.isLetterOrDigit() -> CharClass.LATIN
        else -> CharClass.OTHER
    }

    data class Token(val text: String, val lookupable: Boolean)

    fun split(text: String): List<Token> {
        if (text.isEmpty()) return emptyList()
        val tokens = mutableListOf<Token>()
        var start = 0
        var cls = classify(text[0])
        for (i in 1..text.length) {
            val c = if (i < text.length) classify(text[i]) else null
            if (c != cls) {
                val chunk = text.substring(start, i)
                tokens.add(Token(chunk, cls != CharClass.OTHER))
                start = i
                cls = c ?: CharClass.OTHER
            }
        }
        return tokens
    }
}
