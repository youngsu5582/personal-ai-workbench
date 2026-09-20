plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "dev.joyson"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.1")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	// flyway-core 만 넣으면 자동 설정이 붙지 않는다 — Boot 4 는 자동 설정이 모듈별로 갈려 있다.
	implementation("org.springframework.boot:spring-boot-flyway")
	// Flyway 도 DB 별 지원이 모듈로 갈렸다. 없으면 postgresql URL 을 못 다룬다.
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	// LauncherSessionListener 를 구현하므로 런타임이 아니라 컴파일에도 필요하다.
	testImplementation("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

// 로컬 profile 은 개발용 태스크에서만 켠다.
// jar 로 뜨는 배포에는 붙지 않으므로, 환경변수를 빠뜨리면 로컬 값으로 뜨는 대신 기동에 실패한다.
tasks.bootRun {
	systemProperty("spring.profiles.active", "local")
}

tasks.withType<Test> {
	useJUnitPlatform()

	// 테스트는 application.yaml 을 **상속하지 않는다.**
	//
	// Boot 는 application.yaml 을 읽고 그 위에 application-{profile}.yaml 을 덮는다.
	// 교체가 아니라 중첩이라, application.yaml 의 `spring.config.import: file:.env` 가
	// 테스트에도 적용된다. 그러면 .env 가 있는 기계에서만 개발 DB 주소와 실제 API 키가
	// 테스트 컨텍스트로 들어와, 같은 코드가 기계마다 다르게 돈다.
	//
	// 이름을 바꿔 아예 다른 파일을 읽게 한다. 테스트가 쓰는 설정은 application-test.yaml 에만 있다.
	// 리스너가 아니라 여기 두는 이유는, 리스너가 없어도 상속은 끊겨 있어야 하기 때문이다.
	systemProperty("spring.config.name", "application-test")
	testLogging {
		events("passed", "skipped", "failed")
		showStandardStreams = true
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}
