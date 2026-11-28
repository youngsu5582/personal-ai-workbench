// 인가 서버 모듈.
//
// 인가 서버 라이브러리는 이 모듈만 본다. 앱의 다른 모듈은 그 클래스를 쓰려 해도 컴파일되지 않는다.
// 실행 가능한 jar 는 만들지 않는다. 앱이 이 모듈을 가져가 한 프로세스로 뜬다.
plugins {
	kotlin("jvm")
	kotlin("plugin.spring")
	id("io.spring.dependency-management")
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
		// Boot 플러그인을 적용하지 않으므로 버전 관리를 직접 건다. 앱과 같은 Boot 버전을 따른다.
		mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server")
	// 발급한 인가를 DB 에 둔다.
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}
