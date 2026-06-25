# Plano Técnico de Desenvolvimento

Este documento descreve as decisões de design de código e o pipeline de build adotado no desenvolvimento do BigBang Regions.

## Organização do Código

O código está estruturado para maximizar o desacoplamento das APIs do Minecraft:

* **Domínio Puro (`com.bigbangcraft.regions.domain`)**:
  * Contém entidades como `Region`, `RegionBounds`, `RegionRole` e `RegionType`.
  * Totalmente isolado: nenhuma dependência de classes do net.minecraft ou do fabricmc. Isso facilita a escrita de testes unitários rápidos e mock-free.
* **Motor de Flags (`com.bigbangcraft.regions.flag`)**:
  * Trata a resolução da cascata de flags (Bypass -> Região Prioritária -> Tipo de Região -> Global -> Código).
* **Camada de Dados (`com.bigbangcraft.regions.storage`, `com.bigbangcraft.regions.repository`)**:
  * Abstrai o uso do SQLite JDBC. Executa operações em transações.
* **Cache Espacial (`com.bigbangcraft.regions.cache`)**:
  * Otimiza buscas utilizando um índice de chunks em memória, evitando scans lineares O(N) em todas as regiões registradas durante cliques e movimentos do jogador.

## Pipeline de Build

O build utiliza o Gradle com o plugin `fabric-loom` para remapear o código fonte contra os mappings oficiais da Mojang (`officialMojangMappings`).

* **Compilação**: `./gradlew compileJava` (Java 21)
* **Remapeamento**: Loom remapeia automaticamente classes internas do Minecraft para nomes de produção em runtime.
* **Testes**: Executados através de JUnit 5 com `./gradlew test`.
* **Empacotamento**: O comando `./gradlew build` gera o jar remapeado final em `build/libs/`.
