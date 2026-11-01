# TaskFlow (Android nativo)

Planejador diário nativo para Android com **tarefas, hábitos com sequência, Pomodoro, gamificação e progresso** — tudo offline e sem conta.

Este é o porte nativo (Kotlin + Android Views) do app web TaskFlow, feito para rodar como aplicativo Android de verdade: notificações, alarmes, serviço de foco em primeiro plano e armazenamento local.

## Recursos

- **Hoje** — painel com progresso do dia, foco sugerido, hábitos e tarefas;
- **Tarefas** — criar, editar, concluir, duplicar e excluir; busca por título, notas, categoria ou tag; filtros Todas / Hoje / Atrasadas / Feitas; prioridades, subtarefas com progresso, tempo estimado e meta de pomodoros;
- **Recorrência** — diária, dias úteis, semanal, mensal e anual; ao concluir, a próxima ocorrência é criada automaticamente;
- **Agenda** — calendário mensal com marcação dos dias que têm tarefas e lista do dia selecionado;
- **Hábitos** — sequência atual, recorde, últimos 7 dias, frequência diária/dias úteis e adição de novos hábitos;
- **Foco (Pomodoro)** — timer em primeiro plano com notificação persistente, pausa curta/longa, vínculo com uma tarefa, contagem de sessões e XP;
- **Progresso** — XP, nível, títulos, gráfico dos últimos 7 dias, distribuição por categoria, conquistas e insights;
- **Planejador local** — organiza as tarefas sem horário em blocos realistas (sem chave de API);
- **Modelos prontos** — rotina matinal, preparação para prova, mudança e lançamento de projeto;
- **Revisão semanal** — resumo de conclusões, hábitos e sugestões;
- **Voz** — dite uma tarefa e o app interpreta data ("amanhã", "sexta"), horário ("às 9h"), prioridade e duração;
- **Lembretes** — notificações antes do horário, com ações **Concluir** e **Adiar 10 min**; reagendamento após reiniciar o aparelho;
- **Ajustes** — nome, tema claro/escuro/automático, cor de destaque, sons, durações do Pomodoro, meta diária, notificações, backup/importação em JSON e restauração dos dados.

## Requisitos

- Android **7.0 (API 24)** ou superior;
- Nenhuma conta, login ou conexão obrigatória — os dados ficam no aparelho.

## Instalar

Baixe o APK mais recente em **Releases** e instale (permita "instalar apps desconhecidos" para o navegador ou gerenciador de arquivos).

O APK é assinado com a mesma chave dos outros apps publicados aqui, então atualizações futuras instalam por cima.

## Build

O APK é compilado no GitHub Actions (`.github/workflows/build.yml`):

1. `git push` na branch `main`;
2. **Actions → Build APK**;
3. Baixe o artefato `taskflow-debug`.

Localmente, com Android SDK 35 e JDK 17:

```bash
export AURORA_KEYSTORE_PATH=/caminho/aurora.jks
export AURORA_KEYSTORE_PASS=...
export AURORA_KEY_ALIAS=aurora
export AURORA_KEY_PASS=...
gradle :app:assembleRelease
```

Sem as variáveis de assinatura o build gera um APK de debug.

## Estrutura

```
app/src/main/java/com/taskflow/app/
├── MainActivity.kt              navegação inferior e insets
├── TaskFlowApp.kt               canais de notificação e tema
├── data/
│   ├── Models.kt                tarefas, hábitos, categorias, ajustes
│   ├── Store.kt                 persistência JSON, XP, recorrência, backup
│   └── Templates.kt             modelos de planejamento
├── service/
│   ├── Pomodoro.kt              estado compartilhado do timer
│   ├── PomodoroService.kt       serviço em primeiro plano + notificação
│   ├── ReminderScheduler.kt     AlarmManager e reagendamento
│   ├── ReminderReceiver.kt      notificações com ações
│   └── BootReceiver.kt          restaura lembretes após reiniciar
├── ui/
│   ├── HomeFragment.kt          painel do dia
│   ├── TasksFragment.kt         lista, busca e filtros
│   ├── CalendarFragment.kt      agenda mensal
│   ├── HabitsFragment.kt        hábitos e sequências
│   ├── FocusFragment.kt         Pomodoro
│   ├── TaskEditorActivity.kt    criação/edição completa
│   ├── VoiceActivity.kt         captura e interpretação de voz
│   ├── OnboardingActivity.kt    primeira execução
│   ├── StatsActivity.kt         progresso e conquistas
│   ├── SettingsActivity.kt      preferências e backup
│   ├── adapters/                lista de tarefas e hábitos
│   ├── sheets/                  modelos, planejador, revisão e hábitos
│   └── widgets/                 anel de progresso, calendário e gráfico
└── util/                        datas, sons, tema e notificações
```

## Dados e privacidade

Tudo é gravado em `SharedPreferences` como JSON, no próprio aparelho. Não há telemetria, anúncios ou servidor. O backup exporta tarefas, hábitos, ajustes e XP para um arquivo que você escolhe onde salvar.

## Limitações em relação à versão web

- O app web usa **Gemini** para o planejador; aqui o planejamento é uma **heurística local**, sem chave de API e sem enviar dados para fora;
- Listas compartilhadas em tempo real não existem no nativo — a sincronização é feita por backup manual;
- Lembretes por localização (geofencing) não estão incluídos nesta versão;
- O tema escuro segue o sistema, mas a cor de destaque é escolhida em Ajustes.

## Versão 1.0.0

Primeira versão nativa: tarefas, hábitos, agenda, Pomodoro em primeiro plano, lembretes, gamificação, voz, modelos, planejador local, backup e tema escuro.
