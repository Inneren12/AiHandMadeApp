plugins {
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(17)

  sourceSets {
    val test by getting {
      kotlin.srcDirs("src/test/kotlin", "src/test/java")
    }
  }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
}

tasks.test {
  useJUnit()
}
