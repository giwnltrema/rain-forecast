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

## Fontes de dados (todas gratuitas, sem chave)

| O quê | Fonte |
|---|---|
| Previsão pontual e mapa de previsão | [Open-Meteo](https://open-meteo.com) (melhor modelo disponível para a região) |
| Radar observado | [RainViewer](https://www.rainviewer.com/api.html) |
| Mapa base | © OpenStreetMap, © CARTO |

> O plano gratuito do RainViewer hoje só oferece o **passado** (~2 h) e até zoom 7 — acima disso
> a imagem é ampliada. Por isso a parte "vai chover daqui a pouco" vem do modelo do Open-Meteo.

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
├── data/          # Open-Meteo, RainViewer, localização, "vai chover?" (RainSummary)
├── ui/home/       # tela de previsão
├── ui/map/        # radar + mapa de previsão (MapLibre)
└── ui/theme/      # Material 3 + escala de cores da chuva
```

### Sobre a chave de assinatura

`app/chuva.keystore` está versionada **de propósito**: é um app pessoal e isso permite que APKs
gerados no CI e no seu PC se atualizem um por cima do outro. Não use essa chave para publicar na Play Store.

## Ideias para depois

- Notificação "vai chover em 30 min" (WorkManager checando a cada 15 min).
- Widget na tela inicial com a frase de resumo.
- Locais favoritos (casa, trabalho).
