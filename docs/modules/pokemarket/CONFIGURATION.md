# Configuração

Ative com `modules.pokemarketEnabled: true` em `config.json` ou no arquivo dividido de módulos. O módulo permanece bloqueado sem Cobblemon.

Dependências de desenvolvimento: Cobblemon é resolvido do Modrinth Maven (`maven.modrinth:cobblemon`) via propriedades definidas em `gradle.properties`. São `compileOnly`/`modCompileOnly` e não entram no JAR do BigBangEssentials. O servidor fornece Cobblemon separadamente em runtime.
