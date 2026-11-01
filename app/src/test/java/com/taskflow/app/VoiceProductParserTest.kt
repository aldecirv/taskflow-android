package com.taskflow.app

import com.taskflow.app.voice.VoiceProductParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceProductParserTest {

    @Test
    fun parsesNameQuantityPriceAndPlace() {
        val parsed = VoiceProductParser.parse("arroz 2 quilos vinte e nove reais e noventa no atacadão")
        assertTrue(parsed.name.contains("Arroz", ignoreCase = true))
        assertEquals(2.0, parsed.quantity, 0.001)
        assertEquals("kg", parsed.unit)
        assertEquals(2990L, parsed.priceCents)
        assertTrue(parsed.place.contains("atacad", ignoreCase = true))
    }

    @Test
    fun parsesLitersAndWordPrice() {
        val parsed = VoiceProductParser.parse("leite dois litros por doze reais e noventa")
        assertEquals(2.0, parsed.quantity, 0.001)
        assertEquals("L", parsed.unit)
        assertEquals(1290L, parsed.priceCents)
        assertTrue(parsed.name.contains("Leite", ignoreCase = true))
    }

    @Test
    fun parsesGramsAndDozens() {
        val parsed = VoiceProductParser.parse("café 500 gramas dezenove reais e noventa")
        assertEquals(500.0, parsed.quantity, 0.001)
        assertEquals("g", parsed.unit)
        assertEquals(1990L, parsed.priceCents)
        assertTrue(parsed.name.contains("Caf", ignoreCase = true))
    }

    @Test
    fun parsesHalfKilo() {
        val parsed = VoiceProductParser.parse("banana meio quilo cinco reais")
        assertEquals(0.5, parsed.quantity, 0.001)
        assertEquals("kg", parsed.unit)
        assertEquals(500L, parsed.priceCents)
        assertTrue(parsed.name.contains("Banana", ignoreCase = true))
    }
}
