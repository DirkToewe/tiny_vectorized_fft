plugins {
    id("java")
}

group = "io.github.dirktoewe"
version = "0.0-SNAPSHOT"

java {
  sourceCompatibility = JavaVersion.VERSION_19
}

tasks.compileJava {
  options.compilerArgs.addAll(
    listOf(
       "--add-modules=jdk.incubator.vector"
    )
  )
}

repositories {
  mavenCentral()
}

val v_jmh = "1.36"

dependencies {
  implementation("org.openjdk.jmh:jmh-core:$v_jmh")
  annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$v_jmh")
}
