# ltsxmods

Workspace root for ModDevGradle multi-project development.

## Modules
- :ltsxcore -> `ltsxcore-1.21.1`
- :ltsxlogica -> `ltsxlogica-1.21.1`
- template -> `mod-template-1.21.1`

## Commands
- Build all mods: `./gradlew :buildAllMods`
- Run integrated client: `./gradlew :runClient`
- Run integrated server: `./gradlew :runServer`
- Run integrated gametest server: `./gradlew :runGameTestServer`
- Create new mod module: `./scripts/new-mod.ps1 -ModId logicb`

## Windows helper scripts
- `./scripts/run-client.ps1`
- `./scripts/run-server.ps1`
- `./scripts/run-gametest.ps1`
