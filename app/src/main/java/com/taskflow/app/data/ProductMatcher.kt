package com.taskflow.app.data

import java.text.Normalizer

/**
 * Matching de produtos: código de barras exato ou nome parecido (fuzzy).
 * Usado para que "Café Pilão 500g" e "cafe pilao 500 g" sejam o mesmo produto.
 */
object ProductMatcher {

    const val THRESHOLD = 0.84

    fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun similarity(a: String, b: String): Double {
        val first = normalize(a)
        val second = normalize(b)
        if (first.isBlank() || second.isBlank()) return 0.0
        if (first == second) return 1.0
        if (first.replace(" ", "") == second.replace(" ", "")) return 1.0
        if (first.contains(second) || second.contains(first)) {
            val ratio = minOf(first.length, second.length).toDouble() / maxOf(first.length, second.length)
            if (ratio >= 0.6) return 0.9
        }

        val tokensA = first.split(' ').filter { it.isNotBlank() }.toSet()
        val tokensB = second.split(' ').filter { it.isNotBlank() }.toSet()
        val jaccard = if (tokensA.isEmpty() || tokensB.isEmpty()) 0.0
        else tokensA.intersect(tokensB).size.toDouble() / tokensA.union(tokensB).size

        val dice = diceCoefficient(first.replace(" ", ""), second.replace(" ", ""))
        return (jaccard * 0.6) + (dice * 0.4)
    }

    fun isSameProduct(a: String, b: String): Boolean = similarity(a, b) >= THRESHOLD

    private fun diceCoefficient(a: String, b: String): Double {
        if (a.length < 2 || b.length < 2) return if (a == b) 1.0 else 0.0
        val bigramsA = (0 until a.length - 1).map { a.substring(it, it + 2) }
        val bigramsB = (0 until b.length - 1).map { b.substring(it, it + 2) }.toMutableList()
        var intersection = 0
        bigramsA.forEach { bigram ->
            val index = bigramsB.indexOf(bigram)
            if (index >= 0) {
                intersection++
                bigramsB.removeAt(index)
            }
        }
        return 2.0 * intersection / (bigramsA.size + (b.length - 1))
    }
}
