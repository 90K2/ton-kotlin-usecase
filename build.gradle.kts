import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.7.0"
	id("io.spring.dependency-management") version "1.1.0"
	kotlin("jvm") version "1.8.20"
	kotlin("plugin.spring") version "1.8.20"
}

group = "org.ton"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
	mavenCentral()
	maven("https://s01.oss.sonatype.org/service/local/repositories/snapshots/content/")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	testImplementation("org.springframework.boot:spring-boot-starter-test")

//	implementation("org.ton:ton-kotlin:0.2.16")
	implementation("org.ton:ton-kotlin-jvm:0.3.0-20230412.120307-1")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.2")
	implementation("jakarta.validation:jakarta.validation-api:2.0.2")


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
