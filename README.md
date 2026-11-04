# TaskFlow (Android nativo)

Planejador diário nativo para Android com **tarefas, hábitos com sequência, Pomodoro, gamificação e progresso** — tudo offline e sem conta.

Este é o porte nativo (Kotlin + Android Views) do app web TaskFlow, feito para rodar como aplicativo Android de verdade: notificações, alarmes, serviço de foco em primeiro plano e armazenamento local.

## Recursos

- **Hoje** — painel com progresso do dia, foco sugerido, hábitos e tarefas;
- **Tarefas** — criar, editar, concluir, duplicar e excluir; busca por título, notas, categoria ou tag; filtros Todas / Hoje / Atrasadas / Feitas; prioridades, subtarefas com progresso, tempo estimado e meta de pomodoros;
- **Treinos** — nova aba dentro de Tarefas para **caminhada e corrida** com GPS em tempo real, mapa OpenStreetMap, trajeto desenhado, distância, ritmo, tempo, passos, calorias e histórico local;
- **Alarme 5 min antes** — por tarefa, com som rápido ou **alarme contínuo**, funcionando com o app fechado/tela bloqueada (AlarmManager + serviço em primeiro plano), ícone no card e reagendamento após reinício;
- **Cronômetro flutuante** — overlay sobre outros apps com pausar/retomar/finalizar; o tempo é salvo automaticamente na tarefa;
- **Recorrência** — diária, dias úteis, semanal, mensal e anual; ao concluir, a próxima ocorrência é criada automaticamente;
- **Agenda** — calendário mensal com marcação dos dias que têm tarefas e lista do dia selecionado;
- **Produtos & Gastos** — nova aba com câmera inteligente, código de barras, **quantidade/unidade**, total do dia/mês, desfazer, planilha de preços mês a mês (normalizada por kg/L/un) e gráficos comparativos;
- **Hábitos** — sequência atual, recorde, últimos 7 dias, frequência diária/dias úteis e adição de novos hábitos;
- **Foco (Pomodoro)** — timer em primeiro plano com notificação persistente, pausa curta/longa, vínculo com uma tarefa, contagem de sessões e XP;
- **Progresso** — XP, nível, títulos, gráfico dos últimos 7 dias, distribuição por categoria, conquistas e insights;
- **Planejador inteligente (IA)** — abra pelo botão **Planejar com IA** em Hoje ou pelo ícone ✦ em Tarefas, descreva o objetivo ("organizar o trabalho e estudar inglês") e receba a semana pronta; usa **Gemini** quando há chave configurada e um planejador local quando não há;
- **Organizador local** — encaixa as tarefas sem horário em blocos realistas do dia;
- **Modelos prontos** — rotina matinal, preparação para prova, mudança e lançamento de projeto;
- **Revisão semanal** — resumo de conclusões, hábitos e sugestões;
- **Voz inteligente global** — FAB de microfone em todas as abas, painel de ditado ao vivo, interpretação contextual (produtos, tarefas e hábitos), comandos de confirmar/cancelar, histórico de ditados e microfone dentro dos campos;
- **Lembretes** — notificações antes do horário, com ações **Concluir** e **Adiar 10 min**; reagendamento após reiniciar o aparelho;
- **Correções de IA** — ícone do planejador corrigido, datas passadas limitadas à semana atual, fallback entre modelos e mensagens de erro detalhadas com plano local de segurança;
- **Ajustes** — nome, tema claro/escuro/automático, cor de destaque, sons, durações do Pomodoro, meta diária, notificações, backup/importação em JSON e restauração dos dados.

### Treinos (caminhada e corrida)

- Acesso pela aba **Tarefas → Treinos**;
- **GPS em tempo real** com `LocationManager` e serviço em primeiro plano (continua com a tela bloqueada);
- **Mapa interativo OpenStreetMap** (osmdroid, sem chave de API) com marcador e linha do trajeto;
- Métricas ao vivo: distância (km), tempo, ritmo min/km, **passos** (`TYPE_STEP_COUNTER`) e **calorias** (MET × peso × tempo);
- **Histórico local** com data, distância, duração, ritmo, passos, calorias e rota;
- Detalhe do treino com mapa do percurso, estatísticas e opção de repetir/excluir;
- **Aviso de voz a cada km** (TTS nativo em pt-BR) com ritmo e dica por modalidade — funciona com a tela bloqueada e em fones Bluetooth, configurável em Ajustes;
- **Histórico e recompensas** — tela com calorias por caminhada/corrida, gráfico por período (7 dias, 8 semanas, 6 meses), pontos por km, conquistas e benefícios à saúde;
- Peso corporal configurável em **Ajustes** para o cálculo de calorias;
- Permissões: localização precisa, atividade física (passos) e notificações.

### Alarme de 5 minutos e cronômetro flutuante

- No editor da tarefa, ative **Alarme 5 min antes** e escolha **Notificação rápida** ou **Alarme contínuo**;
- O alarme é agendado com `AlarmManager` exato (quando permitido), dispara em um serviço em primeiro plano e funciona com o app fechado;
- O canal de notificação e o áudio usam o modo alarme; o alarme contínuo toca até ser parado (ou por 5 minutos);
- Tarefas reagendadas após reinício do aparelho;
- **Cronômetro flutuante**: menu da tarefa → **Cronômetro flutuante** (ou editor → botão), com permissão **Sobrepor outros apps**;
- O overlay mostra HH:MM:SS e botões pausar/retomar/finalizar; ao finalizar, o tempo é somado a `trackedSeconds` da tarefa e aparece no card.

### Produtos & Gastos

- **Câmera com guia de enquadramento** (CameraX), captura com feedback háptico e fallback para a galeria;
- **Reconhecimento do produto** por código de barras: leitura ao vivo (ML Kit) e **decodificação offline com ZXing** na foto, com consulta ao **Open Food Facts**; entrada manual do código como garantia;
- **Categorias personalizadas**: toque em **+ Nova categoria** no formulário para criar categorias próprias (ficam salvas, entram nos filtros e no backup);
- Nome e preço **pré-preenchidos e editáveis**, com selo "Reconhecido por IA";
- **Quantidade com stepper** (- / +, toque longo de 5 em 5, animação de escala) e unidades **un, kg, g, L, mL, pacote, caixa**;
- **Preço unitário ou total** (preferência em Ajustes + alternância no formulário) com prévia "Total do item: R$ 25,80 (2 × R$ 12,90)";
- Histórico compara **preço unitário normalizado** (R$/kg, R$/L, R$/un) para pacotes de tamanhos diferentes;
- Formulário com **máscara R$**, data, local com sugestões e **GPS opcional** (geocodificação reversa);
- **Total do dia e do mês** com animação de contador e **Desfazer** após salvar/excluir;
- **Histórico de preços** em duas abas:
  - **Tabela** com produtos nas linhas, últimos meses nas colunas, preço médio, variação `↓` verde / `↑` vermelho, cabeçalho fixo, ordenação por maior alta, maior queda ou alfabética;
  - **Gráficos** com linha de evolução (cor dinâmica, toque nos pontos para tooltip), barras de gasto mensal, filtros por produto (com busca), categoria e local, e cards de gasto do mês, maior alta, maior queda e economia potencial;
- **Exportação CSV e PDF** da planilha.

## Requisitos

- Android **7.0 (API 24)** ou superior;
- Nenhuma conta ou login — os dados ficam no aparelho;
- Internet é **opcional**: só é usada se você configurar uma chave do Gemini para o planejador/voz com IA.

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
│   ├── Purchase.kt              Product, ProductEntry e PriceHistory
│   ├── ProductMatcher.kt        matching por código de barras e nome (fuzzy)
│   ├── PriceAnalytics.kt        médias, variações e totais por mês
│   └── Templates.kt             modelos de planejamento
├── features/products/domain/    casos de uso de variação e histórico
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

## Produtos: modelo de dados

- `Product` — identidade do produto (nome, código de barras, imagem, categoria);
- `ProductEntry` (compra) — preço, data, local e observação, sempre ligado a um `Product`;
- `PriceHistory` — média/mínimo/máximo e quantidade de compras por produto/mês;
- Compras do mesmo produto são agrupadas por **código de barras** ou **nome semelhante** (fuzzy matching com normalização de acentos e espaços);
- A variação usa somente o mês atual contra o mês anterior; sem registro anterior, mostra `—`.

## Voz

- **FAB de microfone permanente** sobre todas as abas;
- Painel inferior com **transcrição ao vivo** (SpeechRecognizer nativo), forma de onda animada, parar/cancelar/confirmar e **feedback háptico**;
- **Interpretação contextual**:
  - Produtos: "arroz 2 quilos vinte e nove reais e noventa no atacadão" → nome, quantidade/unidade, preço e local;
  - Tarefas: data, horário, prioridade e categoria;
  - Hábitos: nome e frequência;
- **Comandos globais**: "cancelar", "confirmar" e "corrigir nome";
- **Histórico dos últimos ditados** para reutilizar e limpeza em Ajustes;
- **Modo voz preferencial**: campos de nome/título abrem o ditado em vez do teclado (toque longo libera o teclado);
- Sem permissão de microfone ou sem reconhecimento, o app cai no teclado/formulário manual sem travar.

> O TaskFlow é Android nativo: `speech_to_text` foi implementado com `SpeechRecognizer` do Android; não usamos `flutter_tts` (confirmação falada é opcional e não incluída).

## Inteligência artificial (opcional)

O app funciona 100% offline com o planejador e a interpretação de voz locais. Para usar o Gemini:

1. Crie uma chave gratuita em [Google AI Studio](https://aistudio.google.com/app/apikey);
2. Abra **Ajustes → Inteligência artificial → Chave do Gemini** e cole a chave;
3. O modelo padrão é `gemini-2.5-flash` e pode ser trocado na mesma tela.

A chave fica em um `SharedPreferences` privado do app, **não entra no backup** e só é enviada para a API oficial do Google quando você usa o planejador, dita uma tarefa ou fotografa um produto com a IA ligada. Se a chamada falhar (sem internet, chave inválida ou limite), o app cai automaticamente no modo local (OCR/ML Kit) e avisa.

## Dados e privacidade

Tudo é gravado em `SharedPreferences` como JSON, no próprio aparelho. Não há telemetria, anúncios ou servidor. O backup exporta tarefas, hábitos, ajustes e XP para um arquivo que você escolhe onde salvar.

## Limitações em relação à versão web

- Listas compartilhadas em tempo real não existem no nativo — a sincronização é feita por backup manual;
- A sincronização com **Firebase Firestore** não está ativada: o app é local-first e não inclui `google-services.json`. A camada de dados está isolada para receber esse adaptador quando o projeto Firebase for configurado;
- Lembretes por localização (geofencing) não estão incluídos nesta versão;
- O planejador e a voz usam o Gemini apenas se você configurar a chave; caso contrário, rodam no modo local (offline);
- O tema escuro segue o sistema, mas a cor de destaque é escolhida em Ajustes.

## Versão 2.0.0

- **Treinos**: caminhada/corrida com GPS, mapa, passos, calorias e histórico;
- **Alarme 5 min** por tarefa com som rápido/contínuo e execução em segundo plano;
- **Cronômetro flutuante** com overlay e tempo salvo na tarefa;
- **Correções no planejador com IA**: ícone visível, datas no intervalo da semana, fallback de modelos, mensagens de erro e plano local;
- Peso corporal para calorias e melhorias de permissões.

## Versão 1.4.0

- **Quantidade e unidade** no formulário (stepper, toque longo de 5, animação, unidades un/kg/g/L/mL/pacote/caixa);
- **Preço unitário ou total** (Ajustes + formulário), total do item calculado e exibido;
- Lista de compras com quantidade e totais; histórico comparando **preço normalizado por kg/L/un**;
- **Ditado global** com FAB em todas as abas, transcrição ao vivo, interpretação por contexto, comandos, histórico, idioma e modo voz preferencial;
- Microfone nos campos de nome de produto, título de tarefa e nome de hábito;
- Permissão de microfone explicada e fallback para teclado.

## Versão 1.3.0

- **Correção da leitura de código de barras**: a foto agora é decodificada com **ZXing offline** (EAN-13/EAN-8/UPC/Code128), sem depender do download do módulo do Google Play Services; leitura ao vivo com ML Kit e botão **“Digitar código de barras”** como fallback;
- **Categorias personalizadas** com botão **+ Nova categoria** no formulário, disponíveis nos filtros e no backup;
- Teste automatizado que gera um EAN-13 real e valida a decodificação.

## Versão 1.2.0

- **Aba Produtos & Gastos** na navegação principal (agora com 6 abas);
- Câmera nativa com moldura de enquadramento, captura háptica e galeria como fallback;
- Reconhecimento por código de barras (Open Food Facts), Gemini Vision e OCR local (ML Kit);
- Selo de reconhecimento, formulário animado, máscara de moeda e GPS opcional;
- Total do dia/mês com contador animado e desfazer;
- **Histórico de preços** com abas Tabela/Gráficos, cabeçalho fixo, ordenação, busca de produto, tooltips, linha com cor dinâmica e cards de resumo;
- Exportação CSV e PDF.

## Versão 1.1.0

- **Planejador inteligente com IA**: objetivo em texto, plano semanal gerado pelo Gemini e aplicação em um toque, com fallback local;
- **Entrada por voz acessível**: microfone no topo de Hoje e Tarefas, atalho "Voz" e interpretação com IA (ou local, offline);
- Configuração da chave e do modelo do Gemini em **Ajustes → Inteligência artificial**;
- Permissão de internet (usada apenas pela IA opcional).

## Versão 1.0.0

Primeira versão nativa: tarefas, hábitos, agenda, Pomodoro em primeiro plano, lembretes, gamificação, voz, modelos, planejador local, backup e tema escuro.
