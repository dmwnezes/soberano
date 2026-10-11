# Soberano

Simulador de governo para Android: escolha um país real (ou crie o seu) no mapa de 2026 e governe — economia, saúde, segurança, leis, eleições, diplomacia, blocos, espionagem e guerras com exército, marinha e aeronáutica, em tempo real com pausa.

## Baixar

A cada envio para `main`, o GitHub Actions roda os testes, gera o APK e publica em **Releases** (`Soberano.apk`). A assinatura é fixa, então cada versão instala por cima da anterior.

## Como o projeto está organizado

- `app/src/main/java/.../sim` — simulação em Kotlin puro (economia, política, diplomacia, militar, IA, eventos), sem dependência da interface.
- `app/src/main/java/.../ui` — telas em Jetpack Compose; o mapa é desenhado em Canvas.
- `app/src/main/assets/world.bin` — 199 países e 1.821 províncias, gerado por `tools/build_world.py` a partir do Natural Earth.
- `app/src/main/assets/flags` — bandeiras do projeto flag-icons.
- `app/src/test` — testes da simulação (10 anos de jogo, guerra, saves) e capturas das telas.

## Créditos

- Mapa: [Natural Earth](https://www.naturalearthdata.com/) (domínio público).
- Bandeiras: [flag-icons](https://github.com/lipis/flag-icons) (MIT).
- Fonte: Outfit (OFL).

criado por: @dmwnezes
