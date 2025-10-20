plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        val main by getting {
            // Стандартные JVM-ресурсы
            resources.srcDirs("src/main/resources", "src/main/RES")
        }
        val test by getting {
            kotlin.srcDirs("src/test/kotlin", "src/test/java")
            // Стандартные тестовые ресурсы
            resources.srcDirs("src/test/resources", "src/test/RES")
        }
    }
}

// Временная страховка от дубликатов ресурсов, чтобы CI не падал
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processResources") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.20")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
}

ktlint {
    android.set(false)
}

tasks.test {
    useJUnit() // JUnit4 runner
}
