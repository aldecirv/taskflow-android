package com.taskflow.app.util

import com.taskflow.app.data.Priority
import java.time.LocalDate
import java.time.LocalTime

data class ParsedVoiceTask(
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val priority: Priority,
    val category: String,
    val estimatedMinutes: Int
)

/** Interpretação local (offline) de uma frase ditada, no estilo do app web. */
object VoiceParser {

    private val weekdays = mapOf(
        "segunda" to 1, "terca" to 2, "quarta" to 3, "quinta" to 4,
        "sexta" to 5, "sabado" to 6, "domingo" to 7
    )

    fun parseLocal(transcript: String, today: LocalDate = LocalDate.now()): ParsedVoiceTask {
        val original = transcript.trim()
        val lower = original.lowercase()
        var date = today
        var time: LocalTime? = null
        var priority = Priority.MEDIA
        var estimate = 30

        // ---- data ----
        when {
            lower.contains("depois de amanhã") || lower.contains("depois de amanha") -> date = today.plusDays(2)
            lower.contains("amanhã") || lower.contains("amanha") -> date = today.plusDays(1)
            lower.contains("hoje") -> date = today
            else -> {
                weekdays.entries.firstOrNull { lower.contains(it.key) }?.let { entry ->
                    var candidate = today
                    while (candidate.dayOfWeek.value != entry.value || candidate == today) {
                        candidate = candidate.plusDays(1)
                    }
                    date = candidate
                }
            }
        }

        // ---- horário ----
        val explicitTime = Regex("""(\d{1,2})\s*(?::|h)\s*(\d{2})?""").find(lower)
        val simpleTime = if (explicitTime == null) {
            Regex("""(?:às|as)\s+(\d{1,2})\b""").find(lower)
        } else null

        if (explicitTime != null) {
            val hour = explicitTime.groupValues[1].toIntOrNull()
            val minute = explicitTime.groupValues[2].toIntOrNull() ?: 0
            time = safeTime(hour, minute, lower)
        } else if (simpleTime != null) {
            time = safeTime(simpleTime.groupValues[1].toIntOrNull(), 0, lower)
        } else if (lower.contains("meio-dia") || lower.contains("meio dia")) {
            time = LocalTime.of(12, 0)
        }

        // ---- prioridade ----
        when {
            lower.contains("urgente") || lower.contains("emergência") || lower.contains("emergencia") ->
                priority = Priority.URGENTE
            lower.contains("prioridade alta") || lower.contains("muito importante") || lower.contains("importante") ->
                priority = Priority.ALTA
            lower.contains("prioridade baixa") || lower.contains("quando der") ||
                lower.contains("pouco importante") -> priority = Priority.BAIXA
        }

        // ---- duração ----
        Regex("""(?:por|durante)?\s*(\d{1,3})\s*(?:min|minutos)""").find(lower)?.let {
            estimate = it.groupValues[1].toIntOrNull()?.coerceIn(5, 240) ?: 30
        }

        // ---- categoria ----
        val category = when {
            listOf("trabalho", "reunião", "reuniao", "relatório", "relatorio", "cliente", "projeto").any(lower::contains) -> "Trabalho"
            listOf("estudar", "estudo", "inglês", "ingles", "livro", "prova", "curso", "aula").any(lower::contains) -> "Estudo"
            listOf("médico", "medico", "academia", "treino", "saúde", "saude", "remédio", "remedio", "exercício", "exercicio").any(lower::contains) -> "Saúde & Bem-estar"
            listOf("pagar", "banco", "fatura", "dinheiro", "investir", "orçamento", "orcamento").any(lower::contains) -> "Finanças"
            else -> "Pessoal"
        }

        // ---- título ----
        var title = original
        val phrases = listOf(
            "criar tarefa", "adicionar tarefa", "nova tarefa", "me lembra de", "me lembre de",
            "lembrete de", "lembrete", "lembrar de", "por favor",
            "depois de amanhã", "depois de amanha", "amanhã", "amanha", "hoje",
            "segunda-feira", "terça-feira", "terca-feira", "quarta-feira", "quinta-feira",
            "sexta-feira", "sábado", "sabado", "domingo", "segunda", "terça", "terca",
            "quarta", "quinta", "sexta",
            "prioridade alta", "prioridade baixa", "urgente", "importante",
            "de manhã", "de manha", "da manhã", "da manha", "à tarde", "a tarde", "da tarde",
            "à noite", "a noite", "da noite", "meio-dia", "meio dia", "às", "as"
        )
        phrases.forEach { phrase ->
            title = title.replace(
                Regex(
                    "(?<![\\p{L}\\p{N}])" + Regex.escape(phrase) + "(?![\\p{L}\\p{N}])",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
        }
        title = title.replace(Regex("""\d{1,2}\s*(?::|h)\s*\d{0,2}"""), " ")
        title = title.replace(Regex("""(?:por|durante)?\s*\d{1,3}\s*(?:min|minutos)\b""", RegexOption.IGNORE_CASE), " ")
        title = title.replace(
            Regex("""^(para|adicionar|criar|lembrar de|me lembra de|me lembre de)\s+""", RegexOption.IGNORE_CASE),
            ""
        )
        title = title.replace(Regex("""\s+"""), " ").trim().trim('.', ',', '!', '?', '-', ':')

        return ParsedVoiceTask(
            title = Dates.capitalize(title.ifBlank { original }),
            date = date,
            time = time,
            priority = priority,
            category = category,
            estimatedMinutes = estimate
        )
    }

    private fun safeTime(hour: Int?, minute: Int, lower: String): LocalTime? {
        if (hour == null || hour !in 0..23 || minute !in 0..59) return null
        var h = hour
        if (h in 1..11 && (lower.contains("tarde") || lower.contains("noite"))) h += 12
        return LocalTime.of(h, minute)
    }
}
