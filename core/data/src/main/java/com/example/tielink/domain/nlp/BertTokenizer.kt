package com.example.tielink.domain.nlp

import java.text.Normalizer

data class BertInputs(
    val inputIds: IntArray,
    val attentionMask: IntArray,
    val tokenTypeIds: IntArray
)

/** Standard BasicTokenizer + greedy WordPiece tokenizer used by BERT models. */
class BertTokenizer(
    vocabulary: List<String>,
    private val lowercase: Boolean = true
) {
    private val tokenToId = vocabulary.withIndex().associate { (index, token) -> token to index }
    private val unknownTokenId = requireToken("[UNK]")
    private val clsTokenId = requireToken("[CLS]")
    private val sepTokenId = requireToken("[SEP]")
    private val padTokenId = requireToken("[PAD]")

    fun tokenize(text: String): List<String> = basicTokenize(text).flatMap(::wordPieceTokenize)

    fun encode(text: String, maxSequenceLength: Int): BertInputs {
        require(maxSequenceLength >= 2) { "BERT sequence length must allow [CLS] and [SEP]" }

        val tokenIds = tokenize(text)
            .take(maxSequenceLength - 2)
            .map { tokenToId[it] ?: unknownTokenId }
        val sequence = listOf(clsTokenId) + tokenIds + sepTokenId

        return BertInputs(
            inputIds = IntArray(maxSequenceLength) { index -> sequence.getOrElse(index) { padTokenId } },
            attentionMask = IntArray(maxSequenceLength) { index -> if (index < sequence.size) 1 else 0 },
            tokenTypeIds = IntArray(maxSequenceLength)
        )
    }

    private fun basicTokenize(text: String): List<String> {
        val cleaned = buildString {
            text.forEach { character ->
                when {
                    character == '\u0000' || character == '\uFFFD' || character.isControl() -> Unit
                    character.isWhitespace() -> append(' ')
                    character.isChineseCharacter() -> append(' ').append(character).append(' ')
                    else -> append(character)
                }
            }
        }

        return cleaned.split(WHITESPACE_REGEX)
            .asSequence()
            .filter { it.isNotEmpty() }
            .flatMap { splitPunctuation(normalizeToken(it)).asSequence() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun normalizeToken(token: String): String {
        val value = if (lowercase) token.lowercase() else token
        if (!lowercase) return value
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
    }

    private fun splitPunctuation(token: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        token.forEach { character ->
            if (character.isPunctuation()) {
                if (current.isNotEmpty()) result += current.toString()
                current.clear()
                result += character.toString()
            } else {
                current.append(character)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun wordPieceTokenize(token: String): List<String> {
        if (tokenToId.containsKey(token)) return listOf(token)
        if (token.length > MAX_WORD_LENGTH) return listOf("[UNK]")

        val pieces = mutableListOf<String>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var matchedPiece: String? = null
            while (start < end) {
                val candidate = (if (start == 0) "" else "##") + token.substring(start, end)
                if (tokenToId.containsKey(candidate)) {
                    matchedPiece = candidate
                    break
                }
                end--
            }
            if (matchedPiece == null) return listOf("[UNK]")
            pieces += matchedPiece
            start = end
        }
        return pieces
    }

    private fun requireToken(token: String): Int =
        requireNotNull(tokenToId[token]) { "BERT vocabulary is missing required token $token" }

    private fun Char.isChineseCharacter(): Boolean = code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF ||
        code in 0xF900..0xFAFF

    private fun Char.isControl(): Boolean {
        if (this == '\t' || this == '\n' || this == '\r') return false
        val type = Character.getType(this)
        return type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()
    }

    private fun Char.isPunctuation(): Boolean {
        if (code in 33..47 || code in 58..64 || code in 91..96 || code in 123..126) return true
        return Character.getType(this) in PUNCTUATION_TYPES
    }

    companion object {
        private const val MAX_WORD_LENGTH = 100
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val PUNCTUATION_TYPES = setOf(
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt()
        )
    }
}
