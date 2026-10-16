# ☔ Chuva

App Android pessoal para saber **se e quando vai chover onde você está** — sem anúncio, sem assinatura,
com cara de Android moderno (Material You / Material 3).

## O que ele faz

**Aba Previsão**
- Frase direta no topo: *"Chuva em ~40 min"*, *"Chovendo agora — deve parar em ~25 min"*, *"Sem chuva nas próximas 24 h"*.
- **Próximas 3 horas** em blocos de 15 min (mm), com cor por intensidade.
- **Próximas 24 horas**: mm de chuva, % de chance, temperatura e ícone hora a hora.
- **7 dias**: acumulado (mm), chance máxima, mín/máx.
- Puxe para baixo para atualizar. Usa sua localização (ou São Paulo, se você negar a permissão).

**Aba Mapa**
- **Radar**: imagens reais de radar das últimas ~2 h, animadas — dá para ver as formações chegando.
- **Previsão**: mapa de chuva prevista (mm/h) hora a hora para as próximas 24 h, numa área de ~120 km
  em volta de você. *"Prever nesta área"* recalcula para onde o mapa estiver.
- Nomes de ruas/bairros ficam **por cima** da chuva, então dá para ler onde está chovendo.
- Tema claro/escuro automático e cores do seu papel de parede (Android 12+).

**Notificações** (sino no topo da tela)
- **Aviso antes de chover**: checa a previsão de 15 em 15 min e avisa com 15, 30 ou 60 min de antecedência
  (*"Chuva em ~25 min em Pinheiros · Moderada · 1,8 mm na primeira hora"*). Avisa **uma vez por chuva**
  e só volta a avisar depois de uma hora seca.
- **Silenciar à noite** (22h–7h).
- **Resumo da manhã** (~6h30): vai chover hoje, a partir de que horas e quanto.
- Por padrão usa o último lugar onde você abriu o app; permitindo localização *"o tempo todo"*,
  os alertas acompanham você.

## Fontes de dados (todas gratuitas, sem chave)

| O quê | Fonte |
|---|---|
| Previsão pontual e mapa de previsão | [Open-Meteo](https://open-meteo.com) (melhor modelo disponível para a região) |
| Radar observado | [RainViewer](https://www.rainviewer.com/api.html) |
| Mapa base | [OpenFreeMap](https://openfreemap.org) © OpenMapTiles © OpenStreetMap |

### Radar manda no "agora", modelo no "depois"

- **Está chovendo / chuva chegando** vem do **radar observado**: o app lê a intensidade (dBZ) dos tiles
  do RainViewer num raio de 60 km, acha a chuva mais próxima e, comparando com o quadro de 30 min antes,
  estima se ela está vindo e em quanto tempo.
- **Previsão** (próximas horas/dias) vem do modelo do Open-Meteo, que erra bastante o "neste minuto".
- **Filtro de clutter**: o radar vê prédios e torres (ex.: espigão da Paulista) como chuva parada.
  Pixels com eco em ≥ metade dos quadros das últimas 2 h, com a vizinhança seca, são ignorados,
  assim como pixels de chuva isolados. O mapa de radar ainda mostra essas manchas (os tiles vêm prontos).
- O plano gratuito do RainViewer só tem o passado (~2 h) e zoom até 7 (~1 km por pixel).

## Instalar no celular

1. Abra **[Releases](https://github.com/giwnltrema/rain-forecast/releases/latest)** no celular e baixe o `chuva-NN.apk` mais recente
   (cada push gera um novo automaticamente pelo GitHub Actions).
2. Toque no arquivo e permita *"instalar apps desconhecidos"* para o navegador/Arquivos.
3. Atualizações: é só baixar o APK novo e instalar por cima.

## Rodar/compilar localmente

Android Studio (Ladybug ou mais novo) → *Open* nesta pasta → Run. Ou:

```bash
./gradlew testDebugUnitTest assembleRelease
# APK em app/build/outputs/apk/release/app-release.apk
```

## Stack

Kotlin · Jetpack Compose · Material 3 · MapLibre · OkHttp · kotlinx.serialization · Fused Location.

```
app/src/main/java/com/giwnltrema/chuva/
├── data/          # Open-Meteo, RainViewer (leitura do radar + filtro de clutter), "vai chover?", alertas
├── notify/        # checagem periódica (WorkManager) e notificações
├── ui/home/       # tela de previsão
├── ui/map/        # radar + mapa de previsão (MapLibre)
└── ui/theme/      # Material 3 + escala de cores da chuva
```

### Sobre a chave de assinatura

`app/chuva.keystore` está versionada **de propósito**: é um app pessoal e isso permite que APKs
gerados no CI e no seu PC se atualizem um por cima do outro. Não use essa chave para publicar na Play Store.

## Ideias para depois

- Widget na tela inicial com a frase de resumo.
- Locais favoritos (casa, trabalho).
