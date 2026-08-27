plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly("com.inductiveautomation.ignitionsdk:designer-api:${rootProject.extra["sdk_version"]}")
    compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.extra["sdk_version"]}")

    // add designer scoped dependencies here
    modlImplementation("com.formdev:flatlaf:3.7.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // slf4j reaches the module via the (compileOnly) Ignition SDK, so it is
    // absent at test runtime. Pin the version the SDK resolves and bind it to
    // a no-op: these tests deliberately exercise failure paths that log.
    testImplementation("org.slf4j:slf4j-api:2.0.12")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.12")
}

tasks.test {
    useJUnitPlatform()

    // The module rewrites the private RGB field of java.awt.Color instances in
    // place; that needs the same module opening the Designer JVM is launched
    // with (see the Designer Launcher's command line, which passes exactly
    // this). Without it the colour-token tests fail on InaccessibleObjectException
    // rather than on anything meaningful.
    jvmArgs("--add-opens", "java.desktop/java.awt=ALL-UNNAMED")

    // Keep the module's debug log out of the developer's ~/.ignition during
    // tests: some tests deliberately exercise failure paths that log a stack
    // trace, and those then read as live Designer faults.
    systemProperty("designerdarkmode.logFile",
        layout.buildDirectory.file("test-debug.log").get().asFile.absolutePath)

    testLogging {
        events("passed", "skipped", "failed")
    }
}
