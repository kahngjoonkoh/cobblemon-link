plugins { java }

group = "io.github.kahngjoonkoh"
version = "1.1.7"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.190-stable")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.5.1")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT") // Bedrock battle forms; optional at runtime
    testImplementation("io.papermc.paper:paper-api:26.3.build.190-stable") // Adventure + Gson for IconsTest
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

tasks.test { useJUnitPlatform() }
tasks.processResources { inputs.property("version", version); filesMatching("paper-plugin.yml") { expand("version" to version) } }
tasks.jar { archiveFileName = "cobblemon-link-plugin-${version}.jar" }
