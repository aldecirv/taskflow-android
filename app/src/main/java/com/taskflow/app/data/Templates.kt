package com.taskflow.app.data

object Templates {
    val all = listOf(
        PlanTemplate(
            id = "morning_routine",
            title = "Rotina matinal produtiva",
            description = "Comece o dia com clareza mental, energia física e sem estresse.",
            category = "Saúde & Bem-estar",
            badge = "Popular",
            emoji = "🌅",
            tasks = listOf(
                TemplateTask("Hidratação e 10 min de respiração", 0, "06:30", Priority.ALTA, 15,
                    listOf("Beber 500 ml de água", "10 min de respiração ou meditação")),
                TemplateTask("Alongamento ou exercício leve", 0, "06:45", Priority.MEDIA, 25,
                    listOf("Alongar a coluna", "Caminhada curta")),
                TemplateTask("Café da manhã sem celular", 0, "07:15", Priority.MEDIA, 20,
                    listOf("Evitar redes sociais nos primeiros 30 min")),
                TemplateTask("Revisar as 3 prioridades do dia", 0, "07:45", Priority.URGENTE, 15,
                    listOf("Abrir o TaskFlow", "Eleger o foco principal da manhã"))
            )
        ),
        PlanTemplate(
            id = "exam_prep",
            title = "Preparar prova em 30 dias",
            description = "Ciclos de repetição espaçada, questões e revisões ativas.",
            category = "Estudo",
            badge = "Acadêmico",
            emoji = "📚",
            tasks = listOf(
                TemplateTask("Mapear edital e tópicos críticos", 0, "09:00", Priority.URGENTE, 60,
                    listOf("Listar assuntos de maior peso", "Separar bibliografia base")),
                TemplateTask("Ciclo 1: bloco de questões comentadas", 1, "14:00", Priority.ALTA, 90,
                    listOf("Resolver 25 questões", "Anotar pegadinhas no caderno de erros")),
                TemplateTask("Flashcards e repetição espaçada", 2, "19:00", Priority.MEDIA, 30,
                    listOf("Revisar 30 cards ativos")),
                TemplateTask("Simulado parcial cronometrado", 5, "08:30", Priority.URGENTE, 120,
                    listOf("Sem distrações", "Calcular taxa de acerto"))
            )
        ),
        PlanTemplate(
            id = "house_move",
            title = "Organizar mudança sem caos",
            description = "Passo a passo para empacotar, contratar frete e trocar endereços.",
            category = "Pessoal",
            badge = "Organização",
            emoji = "📦",
            tasks = listOf(
                TemplateTask("Desapego e doação de itens", 0, "10:00", Priority.ALTA, 90,
                    listOf("Separar roupas não usadas há 1 ano", "Descartar eletrônicos quebrados")),
                TemplateTask("Orçamento de transportadoras", 1, "11:00", Priority.URGENTE, 45,
                    listOf("Cotar com 3 empresas", "Verificar seguro de carga")),
                TemplateTask("Empacotar livros e decoração", 3, "15:00", Priority.MEDIA, 120,
                    listOf("Etiquetar caixas por cômodo", "Proteger os itens frágeis")),
                TemplateTask("Transferir internet e contas", 4, "09:30", Priority.ALTA, 40,
                    listOf("Agendar visita técnica no novo endereço"))
            )
        ),
        PlanTemplate(
            id = "launch_project",
            title = "Lançamento de projeto",
            description = "Do checklist de testes finais ao anúncio público e suporte inicial.",
            category = "Trabalho",
            badge = "Negócios",
            emoji = "🚀",
            tasks = listOf(
                TemplateTask("Auditoria de qualidade e testes", 0, "10:00", Priority.URGENTE, 90,
                    listOf("Validar fluxos críticos", "Testar em celular e desktop")),
                TemplateTask("Material de divulgação e release notes", 1, "14:30", Priority.ALTA, 60,
                    listOf("Atualizar screenshots", "Escrever o texto do anúncio")),
                TemplateTask("Deploy em produção e monitoramento", 2, "09:00", Priority.URGENTE, 60,
                    listOf("Publicar a versão", "Acompanhar logs de erro"))
            )
        )
    )
}
