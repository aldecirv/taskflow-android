package com.taskflow.app.data

import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime

/**
 * Planejador local: entende palavras-chave do objetivo e monta uma semana equilibrada.
 * Funciona offline e é o fallback quando não há chave do Gemini configurada.
 */
object LocalPlanner {

    fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    fun plan(prompt: String, start: LocalDate = LocalDate.now()): AiPlan {
        val text = normalize(prompt)
        val areas = mutableListOf<String>()

        if (listOf("trabalho", "projeto", "reuniao", "relatorio", "cliente", "entregar", "sprint").any(text::contains)) {
            areas.add("Trabalho")
        }
        if (listOf("estud", "ingles", "curso", "prova", "faculdade", "ler", "livro", "concurso").any(text::contains)) {
            areas.add("Estudo")
        }
        if (listOf("treino", "academia", "exercicio", "saude", "meditar", "correr", "caminhar").any(text::contains)) {
            areas.add("Saúde & Bem-estar")
        }
        if (listOf("financ", "pagar", "conta", "orcamento", "investir", "dinheiro", "fatura").any(text::contains)) {
            areas.add("Finanças")
        }
        if (listOf("casa", "familia", "compras", "mercado", "pessoal", "viagem").any(text::contains)) {
            areas.add("Pessoal")
        }
        if (areas.isEmpty()) {
            areas.add("Trabalho")
            areas.add("Pessoal")
        }

        val tasks = mutableListOf<PlannedTask>()
        tasks.add(
            PlannedTask(
                title = "Definir as 3 prioridades da semana",
                category = "Pessoal",
                priority = Priority.ALTA,
                date = start,
                time = LocalTime.of(8, 30),
                estimateMinutes = 20,
                notes = "Gerado a partir do objetivo: ${prompt.take(90)}",
                subtasks = listOf("Revisar pendências", "Escolher o foco principal")
            )
        )

        val english = text.contains("ingles")
        areas.take(3).forEach { area ->
            when (area) {
                "Trabalho" -> {
                    tasks.add(PlannedTask("Bloco focado: principais entregas", area, Priority.URGENTE,
                        start, LocalTime.of(9, 0), 90, "Reserve este bloco sem reuniões.",
                        listOf("Listar os 3 entregáveis", "Trabalhar sem notificações")))
                    tasks.add(PlannedTask("Revisar pendências e alinhar próximos passos", area, Priority.MEDIA,
                        start.plusDays(2), LocalTime.of(16, 0), 30))
                }
                "Estudo" -> {
                    tasks.add(PlannedTask(
                        if (english) "Sessão de inglês: listening e vocabulário" else "Sessão de estudo: conteúdo principal",
                        area, Priority.ALTA, start.plusDays(1), LocalTime.of(19, 0), 45,
                        "Estudo ativo, sem multitarefa.",
                        if (english) listOf("20 min de podcast", "Anotar 5 expressões novas") else listOf("Ler o material", "Fazer resumo ativo")
                    ))
                    tasks.add(PlannedTask(
                        if (english) "Praticar conversação em inglês" else "Revisar o que aprendeu",
                        area, Priority.MEDIA, start.plusDays(3), LocalTime.of(19, 0), 30
                    ))
                }
                "Saúde & Bem-estar" -> {
                    tasks.add(PlannedTask("Treino / atividade física", area, Priority.MEDIA,
                        start.plusDays(1), LocalTime.of(7, 0), 45))
                    tasks.add(PlannedTask("Caminhada leve e alongamento", area, Priority.BAIXA,
                        start.plusDays(3), LocalTime.of(7, 0), 30))
                }
                "Finanças" -> tasks.add(PlannedTask("Revisar gastos e contas da semana", area, Priority.ALTA,
                    start.plusDays(1), LocalTime.of(18, 0), 30))
                "Pessoal" -> tasks.add(PlannedTask("Tempo para casa e família", area, Priority.MEDIA,
                    start.plusDays(4), LocalTime.of(18, 30), 60))
            }
        }

        tasks.add(
            PlannedTask(
                title = "Revisão semanal e próximos passos",
                category = "Pessoal",
                priority = Priority.BAIXA,
                date = start.plusDays(6),
                time = LocalTime.of(17, 0),
                estimateMinutes = 30,
                subtasks = listOf("Revisar o que foi concluído", "Planejar a próxima semana")
            )
        )

        val summary = "Plano local com ${tasks.size} tarefas distribuídas na semana para: ${prompt.trim().take(80)}"
        return AiPlan(summary, tasks, PlanSource.LOCAL)
    }
}
