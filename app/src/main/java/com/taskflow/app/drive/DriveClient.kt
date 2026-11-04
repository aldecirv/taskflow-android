package com.taskflow.app.drive

import android.accounts.AccountManager
import android.content.Context
import android.graphics.pdf.PdfDocument
import com.google.android.gms.auth.GoogleAuthUtil
import com.taskflow.app.data.Store
import com.taskflow.app.data.Workout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Envio dos treinos para o Google Drive (Drive API v3 + OAuth 2.0).
 *
 * Escopo drive.file: o app só enxerga o que ele mesmo criou. Os arquivos ficam em
 * "TaskFlow / Treinos / AAAA-MM" na conta Google escolhida no aparelho.
 * Nada é apagado localmente antes do upload ser confirmado (HTTP 2xx).
 */
object DriveClient {

    private const val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
    const val PREFS = "taskflow_drive"
    private const val KEY_SYNCED = "synced_ids"
    private const val KEY_ROOT = "root_folder"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    class DriveException(message: String) : Exception(message)

    // ------------------------------------------------------------------ estado
    fun syncedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_SYNCED, emptySet())?.toSet() ?: emptySet()

    fun pending(context: Context): List<Workout> {
        val done = syncedIds(context)
        return Store.workouts.filter { it.id !in done }
    }

    private fun markSynced(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SYNCED, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(id)
        prefs.edit().putStringSet(KEY_SYNCED, set).apply()
    }

    // ------------------------------------------------------------------ OAuth
    private fun token(context: Context): String {
        val manager = AccountManager.get(context)
        val account = manager.getAccountsByType("com.google").firstOrNull()
            ?: throw DriveException("Nenhuma conta Google configurada no aparelho.")
        return GoogleAuthUtil.getToken(context, account, SCOPE)
    }

    // ------------------------------------------------------------------ HTTP
    private fun request(token: String, url: String, method: String, body: ByteArray?, contentType: String?): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        contentType?.let { conn.setRequestProperty("Content-Type", it) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw DriveException("Drive API $code: ${text.take(180)}")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun ensureFolder(token: String, name: String, parent: String?): String {
        val escaped = name.replace("'", "\\'")
        val parentQuery = if (parent != null) "'$parent' in parents and " else ""
        val q = java.net.URLEncoder.encode("${parentQuery}name = '$escaped' and mimeType = '$FOLDER_MIME' and trashed = false", "UTF-8")
        val found = request(token, "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)", "GET", null, null)
        found.optJSONArray("files")?.let { if (it.length() > 0) return it.getJSONObject(0).getString("id") }
        val meta = JSONObject().put("name", name).put("mimeType", FOLDER_MIME)
        if (parent != null) meta.put("parents", org.json.JSONArray().put(parent))
        val created = request(
            token,
            "https://www.googleapis.com/drive/v3/files?fields=id",
            "POST",
            meta.toString().toByteArray(),
            "application/json"
        )
        return created.getString("id")
    }

    private fun upload(token: String, folderId: String, name: String, mime: String, data: ByteArray): String {
        val meta = JSONObject()
            .put("name", name)
            .put("mimeType", mime)
            .put("parents", org.json.JSONArray().put(folderId))
        val created = request(
            token,
            "https://www.googleapis.com/drive/v3/files?fields=id",
            "POST",
            meta.toString().toByteArray(),
            "application/json"
        )
        val id = created.getString("id")
        val conn = URL("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media").openConnection() as HttpURLConnection
        conn.requestMethod = "PATCH"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", mime)
        conn.doOutput = true
        conn.outputStream.use { it.write(data) }
        if (conn.responseCode !in 200..299) {
            throw DriveException("Upload $name falhou (${conn.responseCode})")
        }
        return id
    }

    // ------------------------------------------------------------------ relatórios
    private fun jsonReport(w: Workout): String = JSONObject().apply {
        put("schema", "taskflow.workout/1")
        put("atividade", w.mode.label)
        put("data", w.startedAt.toLocalDate().toString())
        put("inicio", w.startedAt.format(DateTimeFormatter.ofPattern("HH:mm")))
        put("fim", w.startedAt.plusSeconds(w.durationSeconds.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")))
        put("duracaoSegundos", w.durationSeconds)
        put("distanciaKm", w.distanceKm)
        put("passos", w.steps)
        put("calorias", w.calories)
        put("ritmoSegundosPorKm", w.avgPaceSecondsPerKm)
        put("pesoKg", w.weightKg)
        put("pontos", points(w))
        put("percurso", org.json.JSONArray().apply {
            w.route.forEach { p -> put(JSONObject().put("lat", p.latitude).put("lon", p.longitude).put("t", p.elapsedSeconds)) }
        })
        put("geradoEm", java.time.LocalDateTime.now().toString())
    }.toString(2)

    private fun csvReport(w: Workout): String = buildString {
        appendLine("campo;valor")
        appendLine("atividade;${w.mode.label}")
        appendLine("data;${w.startedAt.toLocalDate()}")
        appendLine("inicio;${w.startedAt.format(DateTimeFormatter.ofPattern("HH:mm"))}")
        appendLine("duracao;${w.durationLabel()}")
        appendLine("distancia_km;${String.format(Locale("pt", "BR"), "%.2f", w.distanceKm)}")
        appendLine("passos;${w.steps}")
        appendLine("calorias;${w.calories}")
        appendLine("ritmo;${w.paceLabel()}")
        appendLine("pontos;${points(w)}")
    }

    private fun pdfReport(w: Workout): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        val paint = android.graphics.Paint().apply { textSize = 20f; isFakeBoldText = true }
        c.drawText("TaskFlow — ${w.mode.label}", 40f, 60f, paint)
        paint.textSize = 12f
        paint.isFakeBoldText = false
        val lines = listOf(
            "Data: ${w.startedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}",
            "Duracao: ${w.durationLabel()}",
            "Distancia: ${w.distanceLabel()}",
            "Passos: ${w.steps}",
            "Calorias: ${w.calories} kcal",
            "Ritmo medio: ${w.paceLabel()}",
            "Pontos: ${points(w)}"
        )
        lines.forEachIndexed { i, line -> c.drawText(line, 40f, 100f + i * 22f, paint) }
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    fun points(w: Workout): Int {
        var p = (w.distanceKm * if (w.mode.name == "RUN") 16 else 10).toInt()
        if (w.distanceKm >= 5) p += 20
        if (w.distanceKm >= 10) p += 40
        return p
    }

    // ------------------------------------------------------------------ sync
    private fun baseName(w: Workout): String =
        "${w.startedAt.toLocalDate()}_${w.mode.name.lowercase()}_${String.format(Locale.US, "%.2f", w.distanceKm)}km"

    fun syncWorkout(context: Context, workout: Workout): String {
        val token = token(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = prefs.getString(KEY_ROOT, null) ?: ensureFolder(token, "TaskFlow", null).also {
            prefs.edit().putString(KEY_ROOT, it).apply()
        }
        val treinos = ensureFolder(token, "Treinos", root)
        val month = workout.startedAt.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val folder = ensureFolder(token, month, treinos)
        val base = baseName(workout)
        upload(token, folder, "$base.json", "application/json", jsonReport(workout).toByteArray())
        upload(token, folder, "$base.csv", "text/csv", csvReport(workout).toByteArray())
        upload(token, folder, "$base.pdf", "application/pdf", pdfReport(workout))
        markSynced(context, workout.id)
        return "Enviado para TaskFlow/Treinos/$month"
    }

    fun syncPending(context: Context): String {
        val pending = pending(context)
        if (pending.isEmpty()) return "Tudo sincronizado."
        var ok = 0
        val errors = mutableListOf<String>()
        pending.forEach { workout ->
            runCatching { syncWorkout(context, workout) }
                .onSuccess { ok++ }
                .onFailure { errors.add(it.message ?: "erro") }
        }
        return buildString {
            append("$ok ${if (ok == 1) "treino enviado" else "treinos enviados"}")
            if (errors.isNotEmpty()) append(" · ${errors.size} falha(s): ${errors.first()}")
        }
    }
}
