package com.taskflow.app.voice

import java.text.Normalizer

data class ParsedProduct(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "un",
    val priceCents: Long? = null,
    val place: String = ""
)

/** Interpreta frases como "arroz 2 quilos vinte e nove reais e noventa no atacadão". */
object VoiceProductParser {

    private val unitWords = mapOf(
        "kg" to "kg", "quilo" to "kg", "quilos" to "kg",
        "g" to "g", "grama" to "g", "gramas" to "g",
        "l" to "L", "litro" to "L", "litros" to "L",
        "ml" to "mL", "mililitro" to "mL", "mililitros" to "mL",
        "pacote" to "pacote", "pacotes" to "pacote",
        "caixa" to "caixa", "caixas" to "caixa",
        "unidade" to "un", "unidades" to "un", "un" to "un"
    )

    private val unitsPattern = "kg|quilos?|gramas?|g|litros?|l|mililitros?|ml|pacotes?|caixas?|unidades?|un"

    fun parse(text: String, today: java.time.LocalDate = java.time.LocalDate.now()): ParsedProduct {
        var working = text.trim()
        var quantity = 1.0
        var unit = "un"
        var priceCents: Long? = null
        var place = ""

        // ---------- quantidade numérica: "2 kg", "500 gramas" ----------
        val numericQty = Regex("""(\d+(?:[.,]\d+)?)\s*($unitsPattern)\b""", RegexOption.IGNORE_CASE)
            .find(working)
        if (numericQty != null) {
            quantity = numericQty.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 1.0
            unit = unitWords[numericQty.groupValues[2].lowercase()] ?: "un"
            working = working.removeRange(numericQty.range)
        } else {
            // ---------- quantidade por palavras: "dois quilos", "meio litro" ----------
            val wordQty = Regex("""((?:meio|uma?|dois|duas|três|tres|quatro|cinco|seis|sete|oito|nove|dez|onze|doze|treze|catorze|quatorze|quinze|dezesseis|dezessete|dezoito|dezenove|vinte|trinta|quarenta|cinquenta|sessenta|setenta|oitenta|noventa|cem|cento|duzentos|trezentos|quatrocentos|quinhentos)(?:\s+e\s+(?:meio|uma?|dois|duas|três|tres|quatro|cinco|seis|sete|oito|nove|dez))?)\s+($unitsPattern)\b""", RegexOption.IGNORE_CASE)
                .find(working)
            if (wordQty != null) {
                quantity = parseWords(wordQty.groupValues[1]) ?: 1.0
                unit = unitWords[wordQty.groupValues[2].lowercase()] ?: "un"
                working = working.removeRange(wordQty.range)
            }
        }

        // ---------- preço em dígitos: "R$ 29,90", "29 reais e 90" ----------
        val numericPrice = Regex("""(?:R\$\s*)?(\d{1,4})(?:[.,](\d{2}))?\s*(?:reais?)?(?:\s*e\s*(\d{1,2})\s*centavos?)?""", RegexOption.IGNORE_CASE)
            .findAll(working)
            .lastOrNull { match ->
                val after = working.substring(match.range.last + 1).trim()
                match.groupValues[2].isNotBlank() || match.groupValues[3].isNotBlank() ||
                    after.startsWith("reais", true) || match.value.contains("R$", true) ||
                    match.value.contains("reais", true)
            }
        if (numericPrice != null) {
            val reais = numericPrice.groupValues[1].toLongOrNull() ?: 0
            val cents = numericPrice.groupValues[2].ifBlank { numericPrice.groupValues[3] }.toLongOrNull() ?: 0
            priceCents = reais * 100 + cents
            working = working.removeRange(numericPrice.range)
        } else {
            // ---------- preço por palavras: "vinte e nove reais e noventa" ----------
            val wordPrice = Regex("""([a-zà-ú\s]+?)\s*reais?(?:\s*e\s*([a-zà-ú\s]+?))?(?=\s+(?:no|na|em)\s+|$)""", RegexOption.IGNORE_CASE)
                .find(working)
            if (wordPrice != null) {
                val reais = parseWords(wordPrice.groupValues[1].trim().split(" ").takeLast(6).joinToString(" "))
                val cents = parseWords(wordPrice.groupValues[2].trim().split(" ").takeLast(3).joinToString(" "))?.toLong()
                if (reais != null) {
                    priceCents = reais.toLong() * 100 + (cents ?: 0L)
                    // remove apenas a partir do primeiro número por extenso,
                    // preservando o nome do produto que veio antes.
                    val groupRange = wordPrice.groups[1]?.range
                    val numberWordsSet = units.keys + tens.keys + hundreds.keys + "meio"
                    val prefix = wordPrice.groupValues[1]
                    val firstNumber = prefix.split(" ").firstOrNull { it.lowercase() in numberWordsSet }
                    val offset = if (firstNumber != null) prefix.indexOf(firstNumber) else 0
                    val start = (groupRange?.first ?: wordPrice.range.first) + offset
                    working = working.removeRange(start.coerceAtLeast(0), wordPrice.range.last + 1)
                }
            }
        }

        // ---------- local: "no atacadão", "no mercado extra" ----------
        val placeMatch = Regex("""\b(?:no|na|em)\s+([a-zà-ú0-9][a-zà-ú0-9\s.'-]{1,60})$""", RegexOption.IGNORE_CASE)
            .find(working.trim())
        if (placeMatch != null) {
            place = placeMatch.groupValues[1].trim().trim('.', ',')
            working = working.removeRange(placeMatch.range)
        }

        // ---------- nome ----------
        var name = working
            .replace(Regex("""\b(?:comprar|adicionar|registrar|criar|novo|nova|por|de|do|da|com|e)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim().trim(',', '.', '-', ':', ';')
        name = name.split(" ").filter { it.isNotBlank() }.take(8).joinToString(" ")
        if (name.isBlank()) name = text.trim().split(" ").take(5).joinToString(" ")

        return ParsedProduct(
            name = com.taskflow.app.util.Dates.capitalize(name),
            quantity = quantity.coerceAtLeast(0.01),
            unit = unit,
            priceCents = priceCents,
            place = place
        )
    }

    private val units = mapOf(
        "zero" to 0, "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "três" to 3, "tres" to 3,
        "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9,
        "dez" to 10, "onze" to 11, "doze" to 12, "treze" to 13, "catorze" to 14, "quatorze" to 14,
        "quinze" to 15, "dezesseis" to 16, "dezessete" to 17, "dezoito" to 18, "dezenove" to 19
    )
    private val tens = mapOf(
        "vinte" to 20, "trinta" to 30, "quarenta" to 40, "cinquenta" to 50,
        "sessenta" to 60, "setenta" to 70, "oitenta" to 80, "noventa" to 90
    )
    private val hundreds = mapOf(
        "cem" to 100, "cento" to 100, "duzentos" to 200, "trezentos" to 300,
        "quatrocentos" to 400, "quinhentos" to 500, "seiscentos" to 600,
        "setecentos" to 700, "oitocentos" to 800, "novecentos" to 900
    )

    fun parseWords(phrase: String): Double? {
        val clean = Normalizer.normalize(phrase.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\be\\b"), " ")
            .replace(Regex("[^a-z\\s]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) return null
        if (clean == "meio") return 0.5
        var total = 0.0
        var found = false
        clean.split(" ").forEach { word ->
            val value = units[word] ?: tens[word] ?: hundreds[word]
            if (value != null) {
                total += value
                found = true
            }
        }
        return if (found) total else null
    }
}
