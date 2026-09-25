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

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // slf4j reaches the module via the (compileOnly) Ignition SDK, so it is
    // absent at test runtime. Pin the version the SDK resolves and bind it to
    // a no-op: these tests deliberately exercise failure paths that log.
    testImplementation("org.slf4j:slf4j-api:2.0.12")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.12")
}

/*
 * The module version, for Tools -> About Designer Dark Mode. Only this one
 * file is expanded: the message bundle next to it goes through untouched.
 */
tasks.processResources {
    val moduleVersion = project.version.toString()
    inputs.property("moduleVersion", moduleVersion)
    filesMatching("**/designerdarkmode-build.properties") {
        expand("version" to moduleVersion)
    }
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
 * half — but some tests paint real Ignition components into a BufferedImage
 * and read the pixels, for bugs whose wrong colour never reaches UIManager at
 * all (the first was #21). See docs/DEVELOPMENT.md before writing another.
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
    "lafHarnessImplementation"("com.inductiveautomation.ignitionsdk:designer-api:${rootProject.extra["harness_sdk_version"]}")
    "lafHarnessImplementation"("com.inductiveautomation.ignitionsdk:ignition-common:${rootProject.extra["harness_sdk_version"]}")
    "lafHarnessImplementation"("com.formdev:flatlaf:3.7.2")

    "lafHarnessImplementation"("org.junit.jupiter:junit-jupiter:6.1.3")
    "lafHarnessRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "lafHarnessRuntimeOnly"("org.slf4j:slf4j-nop:2.0.12")
}

/**
 * The JVM the harness runs in, shared with the Vision probe below.
 *
 * Synthetica reaches into java.desktop internals and fails to INITIALIZE
 * without these — you get an IllegalAccessError out of its static init, not a
 * theming difference. The Designer Launcher passes the same set; treat this
 * list as part of the harness, not as tuning.
 */
val harnessJvmArgs = listOf(
    "--add-exports", "java.desktop/sun.swing=ALL-UNNAMED",
    "--add-exports", "java.desktop/sun.swing.table=ALL-UNNAMED",
    "--add-exports", "java.desktop/sun.swing.plaf.synth=ALL-UNNAMED",
    "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
    // On Windows, JIDE's header UI for a table built under dark mode
    // subclasses WindowsTableHeaderUI; without this, building the Open/Create
    // Project table fails with an IllegalAccessError (#130's test, windows-latest).
    // The Designer passes it on every platform; elsewhere the package is
    // absent and the JVM only warns.
    "--add-exports", "java.desktop/com.sun.java.swing.plaf.windows=ALL-UNNAMED",
    "--add-opens", "java.desktop/javax.swing=ALL-UNNAMED",
    "--add-opens", "java.desktop/javax.swing.plaf.synth=ALL-UNNAMED",
    // CellRendererSanitizer replaces BasicTableUI's protected rendererPane;
    // without this the interception is unavailable and the tests that cover
    // it cannot run at all. The real Designer opens exactly this package —
    // checked against the running process's command line — so leaving it
    // out made the harness LESS capable than the thing it models.
    "--add-opens", "java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
)

/*
 * The windowed mode (#42).
 *
 * Headless by default: hermetic, and everything the harness asserted for its
 * first year lives in UIManager or in the module's own state. But
 * `java.awt.headless=true` is what empties `Window.getWindows()`, and every
 * pass in the module that walks windows — the white-background swaps, the
 * cached-painter repoint, the JInternalFrame neutralisation — ran and found
 * nothing. With a display present a JFrame can be built, packed and driven
 * without ever being shown, and those passes become assertable.
 *
 *     ./gradlew :designer:lafHarness -Pharness.windowed=true
 *
 * CI passes it on every row (xvfb-run on Linux). The tests that need a
 * window live in WindowedCycleTest and skip themselves headless — unless
 * this property was given, in which case a headless JVM is a broken runner
 * and they fail instead. JIDE's unlicensed-use dialog, which used to make a
 * windowed Gradle worker hang, no longer applies: DesignerLookAndFeel runs
 * IgnitionLookAndFeel.init(), which is where the Designer licenses JIDE.
 */
val harnessWindowed = (project.findProperty("harness.windowed") as String?).toBoolean()

/** Everything a headless run against the real look and feels needs. */
fun Test.runsHeadlessAgainstTheRealLookAndFeels(logFile: String) {
    useJUnitPlatform()
    jvmArgs(harnessJvmArgs)

    if (harnessWindowed) {
        systemProperty("java.awt.headless", "false")
        // Tell the tests the display is required, not optional.
        systemProperty("designerdarkmode.harness.windowed", "true")
        // No Dock icon, no focus theft: the frames are never shown, but the
        // AWT toolkit registers as an application on macOS regardless.
        systemProperty("apple.awt.UIElement", "true")
        // Synthetica's LabelPainter reflects into DefaultTreeCellRenderer
        // .selected while painting a tree row. Headlessly no row is ever
        // painted; with a display the render dies with an
        // InaccessibleObjectException that looks nothing like theming.
        jvmArgs("--add-opens", "java.desktop/javax.swing.tree=ALL-UNNAMED")
    } else {
        // No display, and none needed: everything asserted here lives in
        // UIManager and in the module's own state. Window.getWindows() is
        // simply empty, so the component walks run and find nothing.
        systemProperty("java.awt.headless", "true")
    }

    // The same guard ThemeManager.startup sets before FlatLaf ever loads.
    systemProperty("flatlaf.uiScale.enabled", "false")

    systemProperty("designerdarkmode.logFile",
        layout.buildDirectory.file(logFile).get().asFile.absolutePath)

    // A hung test should name itself. Since #85 the harness stalls in about
    // half of CI runs, in a different PropertyKeyFieldTest method each time,
    // and never locally. No single test takes more than a second, so anything
    // past a minute is the hang; JUnit then prints a dump of every thread
    // before interrupting the test (which a deadlock or a spin ignores — the
    // outer watchdog, ops/laf-harness-watchdog.sh, handles that end).
    systemProperty("junit.jupiter.execution.timeout.default", "60 s")
    systemProperty("junit.jupiter.execution.timeout.threaddump.enabled", "true")
}

val lafHarnessTask = tasks.register<Test>("lafHarness") {
    group = "verification"
    description = "Drives the theme switch against the real Designer look and feels, " +
        (if (harnessWindowed) "against real windows." else "headlessly.")

    testClassesDirs = lafHarness.output.classesDirs
    classpath = lafHarness.runtimeClasspath

    runsHeadlessAgainstTheRealLookAndFeels("laf-harness-debug.log")

    testLogging {
        // standardOut/standardError: the harness prints almost nothing, and
        // JUnit's timeout thread dump goes through the test JVM's streams.
        events("passed", "skipped", "failed", "standardOut", "standardError")
        // Full traces, because the harness runs on platforms nobody has a
        // shell on. Gradle's short format keeps the top frame only, and the
        // first Windows run failed 58 tests with "HeadlessException at
        // ThemeSwitchCycleTest.java:51" — the harness's own line, not the
        // frame inside Synthetica that threw, which is the one that matters.
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

/*
 * The Vision probe (#92).
 *
 * Vision's jars are not a published artifact, so nothing that needs a real
 * `PMIButton` or Vision's own serialization delegates can live in the harness
 * above. A Designer that has run Vision keeps them in its module cache,
 * though, and `ops/vision-jars.sh` prints the newest set as a classpath.
 * With `-Pvision.jars=<that>` this source set exists and compiles against
 * them, on top of everything the harness has; without it, it does not exist
 * and CI never sees it.
 *
 *     ./gradlew :designer:visionProbe -Pvision.jars="$(ops/vision-jars.sh)"
 */
val visionJars = (project.findProperty("vision.jars") as String?)
    ?.split(File.pathSeparator, ",")
    ?.map { file(it.trim()) }
    ?.filter { it.isFile }
if (!visionJars.isNullOrEmpty()) {
    val visionProbe: SourceSet by sourceSets.creating {
        compileClasspath += lafHarness.runtimeClasspath
        runtimeClasspath += output + lafHarness.runtimeClasspath
    }
    dependencies {
        "visionProbeImplementation"(files(visionJars))
    }
    tasks.register<Test>("visionProbe") {
        group = "verification"
        description = "Saves and loads real Vision windows across a theme switch, against the cached Vision jars."

        testClassesDirs = visionProbe.output.classesDirs
        classpath = visionProbe.runtimeClasspath

        runsHeadlessAgainstTheRealLookAndFeels("vision-probe-debug.log")

        testLogging {
            events("passed", "skipped", "failed", "standardOut", "standardError")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
