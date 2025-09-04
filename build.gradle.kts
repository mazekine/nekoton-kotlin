import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    `java-library`
    `maven-publish`
    id("org.jetbrains.dokka") version "1.9.20"
    signing
}

group = "com.mazekine"
version = "0.1.0"

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
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
