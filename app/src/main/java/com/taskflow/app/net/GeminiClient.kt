package com.taskflow.app.net

import android.content.Context
import com.taskflow.app.data.AiConfig
import com.taskflow.app.data.AiPlan
import com.taskflow.app.data.Categories
import com.taskflow.app.data.LocalPlanner
import com.taskflow.app.data.PlanSource
import com.taskflow.app.data.PlannedTask
import com.taskflow.app.data.Priority
import com.taskflow.app.util.ParsedVoiceTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Cliente REST do Gemini (Google AI Studio).
 * A chave é do próprio usuário, configurada em Ajustes, e nunca sai do aparelho
 * a não ser para a chamada oficial da API.
 */
object GeminiClient {

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val FALLBACK_MODEL = "gemini-2.0-flash"

    class GeminiException(val code: Int, message: String) : Exception(message)

    suspend fun planWeek(
        context: Context,
        prompt: String,
        startDate: LocalDate,
        existingTasks: List<String>
    ): Result<AiPlan> = withContext(Dispatchers.IO) {
        runCatching {
            val system = """
                Você é o planejador inteligente do TaskFlow, um app brasileiro de produtividade.
                Distribua as tarefas de forma equilibrada entre ${startDate} e ${startDate.plusDays(6)},
                evitando sobrecarga e horários improváveis (não antes das 06:00 nem depois das 22:00).
                Use categorias válidas: Trabalho, Estudo, Saúde & Bem-estar, Pessoal, Finanças.
                Prioridades válidas: baixa, média, alta, urgente.
                Cada tarefa deve ter entre 15 e 180 minutos e subtarefas acionáveis.
                Responda SOMENTE com JSON válido, em português do Brasil, no formato:
                {"summary":"frase curta","tasks":[{"title":"...","category":"Trabalho","priority":"alta",
                "date":"YYYY-MM-DD","time":"HH:MM","estimatedMinutes":60,"notes":"...","subtasks":["..."]}]}
                Gere no máximo 8 tarefas.
            """.trimIndent()

            val agenda = if (existingTasks.isEmpty()) "Nenhum compromisso cadastrado."
            else "Compromissos já existentes (evite conflitos):\n" + existingTasks.take(12).joinToString("\n")

            val user = """
                Objetivo: $prompt
                Data de hoje: $startDate
                $agenda
                Gere o cronograma da semana.
            """.trimIndent()

            val raw = call(context, system, user)
            parsePlanJson(raw, startDate)
        }
    }

    suspend fun parseVoice(
        context: Context,
        transcript: String,
        today: LocalDate
    ): Result<ParsedVoiceTask> = withContext(Dispatchers.IO) {
        runCatching {
            val system = """
                Você interpreta tarefas ditadas em português do Brasil para o app TaskFlow.
                Responda SOMENTE com JSON válido no formato:
                {"title":"...","date":"YYYY-MM-DD","time":"HH:MM" ou null,
                "priority":"baixa|média|alta|urgente","category":"Trabalho|Estudo|Saúde & Bem-estar|Pessoal|Finanças",
                "estimatedMinutes":30}
                Não invente informações que não estão na fala. Use a data de hoje quando não houver data.
                Título curto e imperativo, sem a data/horário repetidos.
            """.trimIndent()
            val user = "Hoje é $today (${today.dayOfWeek}). Fala do usuário: \"$transcript\""
            val raw = call(context, system, user)
            parseVoiceJson(raw, today)
        }
    }

    // ------------------------------------------------------------------ request

    private fun call(context: Context, system: String, user: String): String {
        val apiKey = AiConfig.apiKey(context)
        if (apiKey.isBlank()) throw GeminiException(401, "Chave da API não configurada.")

        val models = listOf(AiConfig.model(context), AiConfig.DEFAULT_MODEL, FALLBACK_MODEL).distinct()
        var lastError: GeminiException? = null

        models.forEach { model ->
            try {
                return request(model, apiKey, system, user)
            } catch (e: GeminiException) {
                lastError = e
                if (e.code != 404) throw e
            }
        }
        throw lastError ?: GeminiException(0, "Não foi possível falar com o Gemini.")
    }

    private fun request(model: String, apiKey: String, system: String, user: String): String {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.6)
                put("responseMimeType", "application/json")
            })
        }.toString()

        val connection = (URL("$BASE/$model:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("User-Agent", "taskflow-android/1.1.0")
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw GeminiException(code, extractError(code, text))
            }

            val root = JSONObject(text)
            val parts = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw GeminiException(code, "O Gemini não retornou conteúdo.")

            val content = buildString {
                for (i in 0 until parts.length()) {
                    append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }
            if (content.isBlank()) throw GeminiException(code, "Resposta vazia do Gemini.")
            return content
        } finally {
            connection.disconnect()
        }
    }

    private fun extractError(code: Int, raw: String): String {
        val message = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return when (code) {
            400 -> "Requisição recusada (400). Verifique a chave e o modelo."
            401, 403 -> "Chave da API inválida ou sem permissão (${code})."
            404 -> "Modelo não encontrado (404). Ajuste o modelo nas configurações."
            429 -> "Limite de uso do Gemini atingido (429). Tente em alguns minutos."
            else -> message?.take(180) ?: "Falha na API do Gemini (HTTP $code)."
        }
    }

    // -------------------------------------------------------------------- JSON

    internal fun parsePlanJson(raw: String, startDate: LocalDate): AiPlan {
        val json = JSONObject(cleanJson(raw))
        val summary = json.optString("summary").ifBlank { "Cronograma gerado pelo Gemini." }
        val array = json.optJSONArray("tasks") ?: throw GeminiException(422, "O plano veio sem tarefas.")
        val tasks = mutableListOf<PlannedTask>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            if (title.isBlank()) continue
            val subtasks = mutableListOf<String>()
            item.optJSONArray("subtasks")?.let { subs ->
                for (j in 0 until subs.length()) subtasks.add(subs.optString(j))
            }
            tasks.add(
                PlannedTask(
                    title = title,
                    category = normalizeCategory(item.optString("category")),
                    priority = normalizePriority(item.optString("priority")),
                    date = parseDate(item.optString("date"), startDate),
                    time = parseTime(item.optString("time")),
                    estimateMinutes = item.optInt("estimatedMinutes", 30).coerceIn(10, 240),
                    notes = item.optString("notes"),
                    subtasks = subtasks.filter { it.isNotBlank() }
                )
            )
        }
        if (tasks.isEmpty()) throw GeminiException(422, "O Gemini não retornou tarefas válidas.")
        return AiPlan(summary, tasks.take(10), PlanSource.GEMINI)
    }

    internal fun parseVoiceJson(raw: String, today: LocalDate): ParsedVoiceTask {
        val json = JSONObject(cleanJson(raw))
        val title = json.optString("title").trim()
        if (title.isBlank()) throw GeminiException(422, "Não foi possível entender a tarefa.")
        return ParsedVoiceTask(
            title = title,
            date = parseDate(json.optString("date"), today),
            time = parseTime(json.optString("time")),
            priority = normalizePriority(json.optString("priority")),
            category = normalizeCategory(json.optString("category")),
            estimatedMinutes = json.optInt("estimatedMinutes", 30).coerceIn(5, 240)
        )
    }

    private fun cleanJson(raw: String): String = raw.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    private fun parseDate(value: String, fallback: LocalDate): LocalDate =
        runCatching { LocalDate.parse(value.take(10)) }.getOrDefault(fallback)

    private fun parseTime(value: String): LocalTime? {
        if (value.isBlank() || value == "null") return null
        return runCatching { LocalTime.parse(value.take(5)) }
            .recoverCatching { LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm")) }
            .getOrNull()
    }

    private fun normalizePriority(value: String): Priority {
        val text = LocalPlanner.normalize(value)
        return when {
            text.contains("urgent") -> Priority.URGENTE
            text.contains("alta") || text.contains("high") -> Priority.ALTA
            text.contains("baixa") || text.contains("low") -> Priority.BAIXA
            else -> Priority.MEDIA
        }
    }

    private fun normalizeCategory(value: String): String {
        val text = LocalPlanner.normalize(value)
        Categories.all.firstOrNull { category ->
            val name = LocalPlanner.normalize(category.name)
            text.contains(name) || name.contains(text) && text.isNotBlank()
        }?.let { return it.name }
        return when {
            text.contains("estud") -> "Estudo"
            text.contains("saude") || text.contains("bem") -> "Saúde & Bem-estar"
            text.contains("financ") || text.contains("dinheiro") -> "Finanças"
            text.contains("trabalho") || text.contains("projeto") -> "Trabalho"
            else -> "Pessoal"
        }
    }
}
