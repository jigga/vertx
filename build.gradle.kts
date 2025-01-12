import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  id("com.github.johnrengelman.shadow") version "7.1.2" apply(false)
}

// ext properties
val mainVerticleName: String by extra("unknown")

// project properties
val vertxVersion: String by project
val junitJupiterVersion: String by project

// build script variables
val launcherClassName = "io.vertx.core.Launcher"
val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

subprojects {

  plugins.apply("application") // application plugin implicitly applies the java plugin
  plugins.apply("com.github.johnrengelman.shadow")

  repositories {
    mavenCentral()
  }

  dependencies {
    add("implementation", platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    add("implementation", "io.vertx:vertx-core")
    add("runtimeOnly", "io.netty:netty-resolver-dns-native-macos:4.1.115.Final:osx-aarch_64")
    add("runtimeOnly", "io.netty:netty-tcnative-boringssl-static:2.0.69.Final:osx-aarch_64")
    add("testImplementation", platform("org.junit:junit-bom:$junitJupiterVersion"))
    add("testImplementation", "org.junit.jupiter:junit-jupiter")
    add("testImplementation", "io.vertx:vertx-junit5")
  }

  // Thanks, ChatGPT for providing correct response to how to apply the same plugin configuration for all subprojects
  the<JavaPluginExtension>().apply {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  the<JavaApplication>().apply {
    mainClass.set(launcherClassName)
  }

  tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
      events = setOf(PASSED, SKIPPED, FAILED)
    }
  }

  afterEvaluate {
    val mainVerticleName = project.property("mainVerticleName")!!.toString()
    println("Project: $project, mainVerticleName: $mainVerticleName")
    tasks.withType<ShadowJar> {
      archiveClassifier.set("fat")
      manifest {
        attributes(mapOf("Main-Verticle" to mainVerticleName))
      }
      mergeServiceFiles()
    }

    tasks.withType<JavaExec> {
      args = listOf("run", mainVerticleName, "--redeploy=$watchForChange", "--launcher-class=$launcherClassName", "--on-redeploy=$doOnChange", "--conf=conf.json")
    }
  }
}
