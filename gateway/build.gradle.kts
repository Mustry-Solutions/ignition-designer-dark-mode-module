plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly("com.inductiveautomation.ignitionsdk:gateway-api:${rootProject.extra["sdk_version"]}")
    compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.extra["sdk_version"]}")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The hook extends the SDK's AbstractGatewayModuleHook, which is compileOnly
// above and so absent from the test classpath. The test needs it to build the
// hook at all; it touches nothing else in the SDK.
configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}
