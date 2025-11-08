plugins {
    kotlin("jvm") version "2.1.21"
    application
    id("org.owasp.dependencycheck") version "11.1.0"
}
repositories { mavenCentral() }
dependencies {
    val ktor = "3.3.2"
    implementation("io.ktor:ktor-server-core-jvm:$ktor")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktor")
    implementation("io.ktor:ktor-server-cors-jvm:$ktor")
    implementation("io.ktor:ktor-server-compression-jvm:$ktor")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktor")
    implementation("io.ktor:ktor-server-rate-limit-jvm:$ktor")
    implementation("org.apache.logging.log4j:log4j-core:2.24.0")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.0")

    implementation("org.jetbrains.exposed:exposed-core:0.54.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.54.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.54.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")

    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
}
application { mainClass.set("io.ktor.server.netty.EngineMain") }
kotlin { jvmToolchain(21) }

// OWASP Dependency Check configuration
dependencyCheck {
    format = "ALL"
    outputDirectory = "build/reports/dependency-check"
    suppressionFile = "dependency-check-suppressions.xml"
    failBuildOnCVSS = 7.0f  // Fail build on CVSS >= 7.0 (High/Critical)
    analyzers {
        assemblyEnabled = false
        nuspecEnabled = false
        nodeEnabled = false
    }
    skipProjects = listOf(":test")
}
