plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        // main — дефолт (src/main/java)
        val test by getting {
            kotlin.srcDirs("src/test/kotlin", "src/test/java")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.20")
}

tasks.test {
    useJUnit() // JUnit4 runner
}
