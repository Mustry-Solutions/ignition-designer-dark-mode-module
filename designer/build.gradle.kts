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

    // The same guard ThemeManager.startup sets before FlatLaf ever initializes.
    // With user scaling on, FlatLaf registers a PERMANENT UIScale listener on
    // the UI defaults that NPEs (null defaultFont) the next time another look
    // and feel is installed — so one test touching FlatLaf breaks every later
    // test that swaps the look and feel, exactly as it aborts a live theme
    // switch. The property has to be set before the first FlatLaf class load,
    // which is why it lives here rather than in a @BeforeAll.
    systemProperty("flatlaf.uiScale.enabled", "false")

    // Keep the module's debug log out of the developer's ~/.ignition during
    // tests: some tests deliberately exercise failure paths that log a stack
    // trace, and those then read as live Designer faults.
    systemProperty("designerdarkmode.logFile",
        layout.buildDirectory.file("test-debug.log").get().asFile.absolutePath)

    testLogging {
        events("passed", "skipped", "failed")
    }
}

/*
 * The headless look-and-feel harness (#32).
 *
 * `test` runs against stub look and feels: fast, hermetic, and blind to the
 * thing that actually breaks. Every bug in this module's history — #14, #17,
 * #19, #22, #23 — came out of the interaction between three real look and
 * feels (Synthetica, JIDE's extension, FlatLaf), and none of them reproduces
 * against a stub.
 *
 * They all reproduce HEADLESSLY, though, against the real jars. This source
 * set drives ThemeManager's own switch sequence with the Designer's real look
 * and feel installed, and diffs `UIManager` before and after a toggle cycle.
 * No gateway, no Designer, no screenshots. Mostly it replaces the "which
 * defaults are wrong" half of the loop rather than the "does this look right"
 * half — but one test (#21) paints a real Ignition scroll pane into a
 * BufferedImage and reads the pixels, for a bug whose wrong colour never
 * reaches UIManager at all. See docs/DEVELOPMENT.md before writing another.
 *
 * Kept out of `test` deliberately. It resolves the whole Designer dependency
 * tree at RUNTIME (the module itself only compiles against it), it needs the
 * JDK module openings below, and it is an order of magnitude slower — so
 * `check` should not drag it in by accident. Run it with:
 *
 *     ./gradlew :designer:lafHarness
 */
val lafHarness: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += output + compileClasspath
}

dependencies {
    // Unlike `main`, the harness needs these at RUNTIME: the whole point is to
    // run against the real Synthetica/JIDE/ignition-laf jars rather than stubs.
    // `designer-api` is a BOM-style pom that drags in the Designer's own
    // dependency tree, which is where those look and feels come from.
    "lafHarnessImplementation"("com.inductiveautomation.ignitionsdk:designer-api:${rootProject.extra["sdk_version"]}")
    "lafHarnessImplementation"("com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.extra["sdk_version"]}")
    "lafHarnessImplementation"("com.formdev:flatlaf:3.7.2")

    "lafHarnessImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
    "lafHarnessRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "lafHarnessRuntimeOnly"("org.slf4j:slf4j-nop:2.0.12")
}

val lafHarnessTask = tasks.register<Test>("lafHarness") {
    group = "verification"
    description = "Drives the theme switch against the real Designer look and feels, headlessly."

    testClassesDirs = lafHarness.output.classesDirs
    classpath = lafHarness.runtimeClasspath

    useJUnitPlatform()

    // Synthetica reaches into java.desktop internals and fails to INITIALIZE
    // without these — you get an IllegalAccessError out of its static init,
    // not a theming difference. The Designer Launcher passes the same set;
    // treat this list as part of the harness, not as tuning.
    jvmArgs(
        "--add-exports", "java.desktop/sun.swing=ALL-UNNAMED",
        "--add-exports", "java.desktop/sun.swing.table=ALL-UNNAMED",
        "--add-exports", "java.desktop/sun.swing.plaf.synth=ALL-UNNAMED",
        "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens", "java.desktop/javax.swing=ALL-UNNAMED",
        "--add-opens", "java.desktop/javax.swing.plaf.synth=ALL-UNNAMED",
        "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
    )

    // No display, and none needed: everything asserted here lives in UIManager
    // and in the module's own state. Window.getWindows() is simply empty, so
    // the component walks run and find nothing.
    systemProperty("java.awt.headless", "true")

    // The same guard ThemeManager.startup sets before FlatLaf ever loads.
    systemProperty("flatlaf.uiScale.enabled", "false")

    systemProperty("designerdarkmode.logFile",
        layout.buildDirectory.file("laf-harness-debug.log").get().asFile.absolutePath)

    testLogging {
        events("passed", "skipped", "failed")
    }
}
