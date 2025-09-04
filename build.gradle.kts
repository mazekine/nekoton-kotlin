import org.gradle.jvm.tasks.Jar
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.bundling.Zip
import java.security.MessageDigest
import java.io.File
import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    `java-library`
    `maven-publish`
    id("org.jetbrains.dokka") version "1.9.20"
    signing
}

group = "com.mazekine"

val commitCount = ByteArrayOutputStream().use { output ->
    exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        standardOutput = output
    }
    output.toString().trim()
}
version = "0.$commitCount.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

kotlin {
    jvmToolchain(23)
    
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

// Ensure Java compile target matches Kotlin target
tasks.withType<JavaCompile> {
    options.release.set(21)
}

dependencies {
    // Kotlin standard libraries
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.1")
    
    // Serialization for JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    
    // HTTP client for transport
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-client-logging:2.3.12")
    
    // Crypto libraries
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    
    // BigInteger support
    implementation("com.ionspin.kotlin:bignum:0.3.10")
    
    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
}

// Add Rust compilation task
tasks.register<Exec>("buildRustLibrary") {
    workingDir = file("nekoton-jni")
    commandLine("bash", "-lc", "cargo build --release")
    
    inputs.dir("nekoton-jni/src")
    inputs.file("nekoton-jni/Cargo.toml")
    outputs.file("nekoton-jni/target/release/libnekoton_jni.so")
}

tasks.named("compileKotlin") {
    dependsOn("buildRustLibrary")
}

// Copy native library to resources
tasks.register<Copy>("copyNativeLibrary") {
    dependsOn("buildRustLibrary")
    from("nekoton-jni/target/release")
    include("libnekoton_jni.so", "libnekoton_jni.dylib", "nekoton_jni.dll")
    into("src/main/resources")
}

tasks.named("processResources") {
    dependsOn("copyNativeLibrary")
}

tasks.test {
    useJUnitPlatform()
}

// Documentation and source packaging
tasks.dokkaJavadoc.configure {
    outputDirectory.set(buildDir.resolve("dokkaJavadoc"))
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc.map { it.outputDirectory })
}

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("copyNativeLibrary"))
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
            
            pom {
                name.set("Nekoton Kotlin")
                description.set("Kotlin bindings for Nekoton - Broxus SDK with TIP3 wallets support")
                url.set("https://github.com/broxus/nekoton-kotlin")
                
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://opensource.org/licenses/Apache-2.0")
                    }
                }
                
                developers {
                    developer {
                        id.set("com.mazekine")
                        name.set("Mazekine Team")
                        organization.set("Mazekine")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/mazekine/nekoton-kotlin.git")
                    developerConnection.set("scm:git:ssh://github.com:mazekine/nekoton-kotlin.git")
                    url.set("https://github.com/mazekine/nekoton-kotlin")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = findProperty("sonatypeUsername") as String? ?: System.getenv("SONATYPE_USERNAME")
                password = findProperty("sonatypePassword") as String? ?: System.getenv("SONATYPE_PASSWORD")
            }
        }
        maven {
            name = "bundle"
            url = layout.buildDirectory.dir("central-repo").get().asFile.toURI()
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}

val generatePomFile = tasks.named<GenerateMavenPom>("generatePomFileForMavenPublication")

val generatePomProperties by tasks.registering {
    val props = layout.buildDirectory.file("generated-pom.properties")
    outputs.file(props)
    doLast {
        props.get().asFile.writeText(
            "version=${project.version}\n" +
            "groupId=${project.group}\n" +
            "artifactId=${project.name}\n"
        )
    }
}

tasks.jar {
    dependsOn(generatePomFile, generatePomProperties)
    from(generatePomFile.map { it.destination }) {
        into("META-INF/maven/${project.group}/${project.name}")
        rename { "pom.xml" }
    }
    from(generatePomProperties.map { it.outputs.files.singleFile }) {
        into("META-INF/maven/${project.group}/${project.name}")
        rename { "pom.properties" }
    }
}

val bundleRepoDir = layout.buildDirectory.dir("central-repo")
val groupPath = "${project.group.toString().replace('.', '/')}/${project.name}/${project.version}"

val publishToBundleRepo = tasks.named("publishMavenPublicationToBundleRepository")

val generateChecksums by tasks.registering {
    dependsOn(publishToBundleRepo)
    doLast {
        bundleRepoDir.map { it.dir(groupPath) }.get().asFile.listFiles()?.forEach { file ->
            if (file.isFile && !file.name.endsWith(".asc") && !file.name.endsWith(".md5") && !file.name.endsWith(".sha1")) {
                val bytes = file.readBytes()
                val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                val md5 = MessageDigest.getInstance("MD5").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                File(file.parentFile, "${file.name}.sha1").writeText(sha1)
                File(file.parentFile, "${file.name}.md5").writeText(md5)
            }
        }
    }
}

val createCentralBundle by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Creates a bundle for manual upload to Sonatype Central"
    dependsOn(generateChecksums)

    archiveFileName.set("${project.name}-${project.version}-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("bundle"))

    from(bundleRepoDir) {
        include("$groupPath/**")
    }
}