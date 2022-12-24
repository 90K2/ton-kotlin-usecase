import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.7.0"
	id("io.spring.dependency-management") version "1.1.0"
	kotlin("jvm") version "1.7.0-RC2"
	kotlin("plugin.spring") version "1.7.0-RC2"
}

group = "org.ton"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
	mavenCentral()
	maven { url = uri("https://jitpack.io") }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	testImplementation("org.springframework.boot:spring-boot-starter-test")

	implementation("org.ton:ton-kotlin:0.2.4")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.2")

}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
