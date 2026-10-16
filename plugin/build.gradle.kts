plugins { java }

group = "io.github.kahngjoonkoh"
version = "0.3.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.190-stable")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.5.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

tasks.test { useJUnitPlatform() }
tasks.processResources { inputs.property("version", version); filesMatching("paper-plugin.yml") { expand("version" to version) } }
tasks.jar { archiveFileName = "cobblemon-link-${version}.jar" }
