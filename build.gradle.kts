import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission
import org.gradle.api.file.DuplicatesStrategy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import tasks.PrintFilePathTask
import versioning.BuildConfig

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.3.20"
  id("com.gradleup.shadow") version "9.4.0"
  id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
  id("com.diffplug.spotless") version "8.4.0"
  id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

BuildConfig.init(project)

group = "trqxyz.spectra"

version = "1.0"

val packetEventsSpigot = "com.github.retrooper:packetevents-spigot:2.13.0"

repositories {
  mavenCentral()
  maven("https://jitpack.io")
  maven("https://repo.papermc.io/repository/maven-public/")
  maven("https://repo.codemc.io/repository/maven-releases/")
  maven("https://repo.codemc.io/repository/maven-snapshots/")
  maven("https://maven.enginehub.org/repo/")
  maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
  maven("https://repo.opencollab.dev/maven-snapshots/")
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
  compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.15")
  compileOnly("me.clip:placeholderapi:2.12.2")
  compileOnly("org.geysermc.floodgate:api:2.0-SNAPSHOT")
  compileOnly("litebans:api:0.6.1")

  if (BuildConfig.shadePE) {
    implementation(packetEventsSpigot)
  } else {
    compileOnly(packetEventsSpigot)
  }
  implementation("org.bstats:bstats-bukkit:3.2.1")

  implementation("org.incendo:cloud-paper:2.0.0-beta.14")
  implementation("org.incendo:cloud-processors-requirements:1.0.0-rc.1")
  implementation("org.incendo:cloud-kotlin-extensions:2.0.0")
  implementation("org.incendo:cloud-kotlin-coroutines:2.0.0")

  implementation("net.kyori:adventure-platform-bukkit:4.4.1")
  implementation("net.kyori:adventure-text-minimessage:4.26.1")
  implementation("net.kyori:adventure-text-serializer-plain:4.26.1")
  implementation("net.kyori:adventure-text-serializer-gson:4.26.1")

  implementation("com.zaxxer:HikariCP:7.0.2")
  implementation("org.slf4j:slf4j-jdk14:2.0.17")
  implementation("org.jetbrains.exposed:exposed-core:1.1.1")
  implementation("org.jetbrains.exposed:exposed-java-time:1.1.1")
  implementation("org.jetbrains.exposed:exposed-jdbc:1.1.1")
  implementation("org.flywaydb:flyway-core:12.1.1")
  implementation("org.flywaydb:flyway-mysql:12.1.1")
  implementation("org.mariadb.jdbc:mariadb-java-client:3.5.7")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")

  implementation("io.lettuce:lettuce-core:6.5.0.RELEASE") { exclude(group = "io.netty") }
  compileOnly("io.netty:netty-handler:4.1.113.Final")

  implementation(kotlin("stdlib"))
  implementation("it.unimi.dsi:fastutil:8.5.15")
  implementation("org.jetbrains:annotations:26.1.0")
  implementation("com.google.flatbuffers:flatbuffers-java:25.2.10")
  implementation("org.spongepowered:configurate-yaml:4.2.0")
  implementation("ru.vyarus:yaml-config-updater:1.4.4")
  implementation("io.insert-koin:koin-core:4.2.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")

  testImplementation(kotlin("test"))
  testImplementation(packetEventsSpigot)
  testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
  testImplementation("io.mockk:mockk:1.14.9")
  testImplementation("org.testcontainers:junit-jupiter:1.21.4")
  testImplementation("org.testcontainers:mariadb:1.21.4")
  testCompileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
  testRuntimeOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
  testRuntimeOnly("org.xerial:sqlite-jdbc:3.51.3.0")
  testRuntimeOnly("io.netty:netty-handler:4.1.113.Final")
}

val javaTarget = (findProperty("javaTarget") as String?)?.trim()?.toInt() ?: 17

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
  disableAutoTargetJvm()
}

kotlin { jvmToolchain(21) }

tasks.withType<JavaCompile> {
  options.release.set(javaTarget)
  options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(javaTarget.toString()))
    freeCompilerArgs.addAll("-jvm-default=enable")
  }
}

tasks.jar { archiveClassifier.set("thin") }

tasks.shadowJar {
  archiveBaseName.set(rootProject.name)
  archiveClassifier.set(
    listOfNotNull("java$javaTarget", if (BuildConfig.shadePE) null else "unbundled")
      .joinToString("-")
  )

  eachFile {
    if (path == "META-INF/services/org.flywaydb.core.extensibility.Plugin") {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
  }

  minimize {
    exclude(dependency("org.slf4j:slf4j-api"))
    exclude(dependency("org.slf4j:slf4j-jdk14"))
    exclude(dependency("org.jetbrains.exposed:exposed-core"))
    exclude(dependency("org.jetbrains.exposed:exposed-jdbc"))
    exclude(dependency("org.jetbrains.exposed:exposed-java-time"))
    exclude(dependency("org.flywaydb:flyway-core"))
    exclude(dependency("org.flywaydb:flyway-mysql"))
    exclude(dependency("org.mariadb.jdbc:mariadb-java-client"))
    exclude(dependency("io.lettuce:lettuce-core"))
    exclude(dependency("io.projectreactor:reactor-core"))
    exclude(dependency("org.reactivestreams:reactive-streams"))
    exclude(dependency("net.kyori:adventure-text-serializer-gson:.*"))
  }

  mergeServiceFiles()

  if (BuildConfig.shadePE) {
    relocate("com.github.retrooper.packetevents", "trqxyz.spectra.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "trqxyz.spectra.libs.packetevents.impl")
    relocate("net.kyori", "trqxyz.spectra.libs.kyori")
  }
  relocate("org.bstats", "trqxyz.spectra.libs.bstats")
  relocate("org.incendo", "trqxyz.spectra.libs.incendo")
  relocate("io.leangen.geantyref", "trqxyz.spectra.libs.geantyref")
  relocate("it.unimi.dsi.fastutil", "trqxyz.spectra.libs.fastutil")
  relocate("com.google.flatbuffers", "trqxyz.spectra.libs.flatbuffers")
  relocate("com.fasterxml.jackson", "trqxyz.spectra.libs.jackson")
  relocate("com.zaxxer", "trqxyz.spectra.libs.hikari")
  relocate("org.slf4j", "trqxyz.spectra.libs.slf4j")
  relocate("org.jetbrains.exposed", "trqxyz.spectra.libs.jetbrains.exposed")
  relocate("org.spongepowered.configurate", "trqxyz.spectra.libs.configurate")
  relocate("org.yaml.snakeyaml", "trqxyz.spectra.libs.snakeyaml")
  relocate("ru.vyarus.yaml.updater", "trqxyz.spectra.libs.yamlupdater")
  relocate("org.joml", "trqxyz.spectra.libs.joml")
  relocate("org.koin", "trqxyz.spectra.libs.koin")
  relocate("org.flywaydb", "trqxyz.spectra.libs.flyway")
  relocate("tools.jackson", "trqxyz.spectra.libs.tools.jackson")
  relocate("io.lettuce", "trqxyz.spectra.libs.lettuce")
  relocate("reactor", "trqxyz.spectra.libs.reactor")
  relocate("org.reactivestreams", "trqxyz.spectra.libs.reactivestreams")
}

tasks.register<PrintFilePathTask>("printShadowJarPath") {
  description = "Prints the absolute path of the release shadow JAR."
  group = "help"
  file.set(tasks.shadowJar.flatMap { it.archiveFile })
}

tasks.test {
  useJUnitPlatform { excludeTags("container") }
  jvmArgs(
    "-XX:+EnableDynamicAgentLoading",
    "--add-opens",
    "java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens",
    "java.base/java.lang=ALL-UNNAMED",
  )
}

val containerTest by
  tasks.registering(Test::class) {
    description = "Runs container-backed integration tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("container") }
    shouldRunAfter(tasks.test)
    jvmArgs(
      "-XX:+EnableDynamicAgentLoading",
      "--add-opens",
      "java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens",
      "java.base/java.lang=ALL-UNNAMED",
    )
  }

tasks.build { dependsOn(tasks.shadowJar) }

detekt {
  toolVersion = "1.23.8"
  buildUponDefaultConfig = true
  allRules = false
  parallel = true
  baseline = file("config/detekt/baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
  jvmTarget = javaTarget.toString()
  exclude("**/flatbuffers/**")
  exclude("**/build/**")
}

bukkit {
  name = "Spectra"
  main = "trqxyz.spectra.SpectraPlugin"
  version = project.version.toString()
  apiVersion = "1.13"
  authors = listOf("Kaelus")
  website = "https://dsc.gg/kaelus"
  foliaSupported = true
  if (!BuildConfig.shadePE) {
    depend = listOf("packetevents")
  }
  softDepend =
    listOf(
      "ProtocolLib",
      "ProtocolSupport",
      "Essentials",
      "ViaVersion",
      "ViaBackwards",
      "ViaRewind",
      "Geyser-Spigot",
      "floodgate",
      "FastLogin",
      "PlaceholderAPI",
      "WorldGuard",
      "Vault",
      "LiteBans",
    )

  permissions {
    register("spectra.help") {
      description = "Allows usage of the help command"
      default = Permission.Default.OP
    }
    register("spectra.alerts") {
      description = "Receive alerts for violations"
      default = Permission.Default.OP
    }
    register("spectra.alerts.enable-on-join") {
      description = "Automatically enables alerts on join"
      default = Permission.Default.OP
    }
    register("spectra.reload") {
      description = "Allows reloading the config"
      default = Permission.Default.OP
    }
    register("spectra.connect") {
      description = "Allows linking/unlinking this server to the Spectra web panel"
      default = Permission.Default.OP
    }
    register("spectra.exempt") {
      description = "Exempt from all checks"
      default = Permission.Default.FALSE
    }
    register("spectra.disable") {
      description = "Disables anti-cheat tracking for the player"
      default = Permission.Default.FALSE
    }
    register("spectra.datacollect") {
      description = "Parent permission for data collection commands"
      default = Permission.Default.OP
      children =
        listOf(
          "spectra.datacollect.start",
          "spectra.datacollect.stop",
          "spectra.datacollect.cancel",
          "spectra.datacollect.status",
        )
    }
    register("spectra.datacollect.start") {
      description = "Allows starting a data collection session"
      default = Permission.Default.FALSE
    }
    register("spectra.datacollect.stop") {
      description = "Allows stopping a data collection session"
      default = Permission.Default.FALSE
    }
    register("spectra.datacollect.cancel") {
      description = "Allows cancelling a data collection session without saving"
      default = Permission.Default.FALSE
    }
    register("spectra.datacollect.status") {
      description = "Allows viewing data collection session status"
      default = Permission.Default.FALSE
    }
    register("spectra.prob") {
      description = "Allows usage of the probability display command"
      default = Permission.Default.OP
      children = listOf("spectra.prob.self", "spectra.prob.list")
    }
    register("spectra.prob.self") {
      description = "Allows enabling the probability display only on self"
      default = Permission.Default.FALSE
    }
    register("spectra.prob.list") {
      description = "Allows listing active monitor sessions"
      default = Permission.Default.OP
    }
    register("spectra.view") {
      description = "Allows toggling AI nametag view above players"
      default = Permission.Default.OP
    }
    register("spectra.profile") {
      description = "Allows usage of the profile command"
      default = Permission.Default.OP
    }
    register("spectra.brand") {
      description = "Receive client brand notifications"
      default = Permission.Default.OP
    }
    register("spectra.brand.enable-on-join") {
      description = "Automatically enables brand notifications on join"
      default = Permission.Default.OP
    }
    register("spectra.history") {
      description = "Allows viewing a player's violation history"
      default = Permission.Default.OP
    }
    register("spectra.logs") {
      description = "Allows viewing recent violations"
      default = Permission.Default.OP
    }
    register("spectra.stats") {
      description = "Allows viewing server statistics"
      default = Permission.Default.OP
    }
    register("spectra.report") {
      description = "Allows submitting player reports"
      default = Permission.Default.TRUE
    }
    register("spectra.reports") {
      description = "Allows reviewing and managing player reports"
      default = Permission.Default.OP
    }
    register("spectra.decisions") {
      description = "Allows viewing recent AI decisions"
      default = Permission.Default.OP
    }
    register("spectra.exempt.manage") {
      description = "Allows managing punishment exemptions for players"
      default = Permission.Default.OP
    }
    register("spectra.punish.manage") {
      description = "Allows managing player punishments"
      default = Permission.Default.OP
    }
    register("spectra.suspicious") {
      description = "Permission for suspicious player commands"
      default = Permission.Default.OP
      children =
        listOf(
          "spectra.suspicious.alerts",
          "spectra.suspicious.list",
          "spectra.suspicious.top",
          "spectra.suspicious.flagged",
        )
    }
    register("spectra.suspicious.alerts") {
      description = "Allows toggling suspicious player alerts"
      default = Permission.Default.OP
    }
    register("spectra.suspicious.alerts.enable-on-join") {
      description = "Automatically enables suspicious alerts on join"
      default = Permission.Default.OP
    }
    register("spectra.suspicious.list") {
      description = "Allows listing suspicious players"
      default = Permission.Default.OP
    }
    register("spectra.suspicious.top") {
      description = "Allows viewing the top suspicious player"
      default = Permission.Default.OP
    }
    register("spectra.suspicious.flagged") {
      description = "Allows viewing online players with recorded flags"
      default = Permission.Default.OP
    }
  }
}

spotless {
  isEnforceCheck = true

  kotlin {
    target("src/**/*.kt")
    ktfmt().googleStyle()
  }

  kotlinGradle {
    target("*.gradle.kts")
    ktfmt().googleStyle()
  }
}
