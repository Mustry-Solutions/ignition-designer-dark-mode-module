package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.color.ColorSpace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the one thing in this module that can do damage outside it.
 *
 * <p>{@link IaColorTokens} restyles the Designer by rewriting the private RGB
 * field of {@link Color} instances <em>in place</em>, so that every component
 * and painter already holding a token renders dark on its next repaint. That
 * only works because the instance is shared — which is also precisely why it
 * is dangerous: a token that happens to be one of the JDK's own {@code Color}
 * singletons is shared with the entire JVM, not just the Designer.
 *
 * <p>These tests pin the two invariants that keep that safe: the JDK globals
 * are never touched, and everything that <em>is</em> touched comes back
 * exactly as it was.
 */
class IaColorTokensTest {

    private IaColorTokens tokens;

    @BeforeEach
    void setUp() throws Exception {
        tokens = new IaColorTokens();
        tokens.reflectColorInternals();
    }

    @Test
    @DisplayName("mutating a colour in place changes what it reports")
    void mutatesInPlace() throws Exception {
        Color token = new Color(0xFA, 0xFA, 0xFB);
        int original = token.getRGB();

        tokens.setColorValue(token, 0xFF2E3133);

        assertEquals(0xFF2E3133, token.getRGB());
        assertEquals(0x2E, token.getRed());
        assertEquals(0x31, token.getGreen());
        assertEquals(0x33, token.getBlue());
        assertEquals(0xFFFAFAFB, original, "sanity: the token started light");
    }

    @Test
    @DisplayName("restoring returns the exact original ARGB")
    void restoresExactly() throws Exception {
        Color token = new Color(0xFA, 0xFA, 0xFB);
        int original = token.getRGB();

        tokens.setColorValue(token, 0xFF2E3133);
        tokens.setColorValue(token, original);

        assertEquals(original, token.getRGB());
    }

    /**
     * A {@code Color} built through the {@link java.awt.color.ColorSpace}
     * constructor keeps a float copy of itself in the private
     * {@code frgbvalue}/{@code fvalue} fields, and {@code getColorComponents()}
     * returns that copy rather than deriving it from the packed int. Rewriting
     * only the int would leave such a colour reporting its pre-mutation value
     * to anything asking for components — Java2D does, when compositing — while
     * {@code getRGB()} claimed otherwise.
     *
     * <p>Note this is genuinely defensive: an int-RGB {@code Color}, which is
     * what Ignition's tokens are, never populates these fields at all (they are
     * not lazily filled — verified on JDK 17). The clearing exists for the case
     * where a token is not int-constructed, so the test has to build that case
     * explicitly to exercise it.
     */
    @Test
    @DisplayName("float caches are invalidated for a ColorSpace-constructed token")
    void invalidatesFloatCaches() throws Exception {
        Color token = new Color(ColorSpace.getInstance(ColorSpace.CS_sRGB),
            new float[] {0.98f, 0.98f, 0.984f}, 1.0f);
        assertArrayEquals(new float[] {0.98f, 0.98f, 0.984f},
            token.getColorComponents(null), 1e-6f, "sanity: the float cache is populated");

        tokens.setColorValue(token, 0xFF2E3133);

        assertArrayEquals(new float[] {0x2E / 255f, 0x31 / 255f, 0x33 / 255f},
            token.getColorComponents(null), 1e-6f,
            "components still describe the pre-mutation colour — the "
                + "frgbvalue/fvalue caches were not cleared");
    }

    @Test
    @DisplayName("every JDK global Color singleton is refused")
    void refusesJdkGlobals() {
        for (Color global : new Color[] {
            Color.WHITE, Color.BLACK, Color.GRAY, Color.LIGHT_GRAY, Color.DARK_GRAY,
            Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.ORANGE,
            Color.CYAN, Color.MAGENTA, Color.PINK,
        }) {
            assertTrue(IaColorTokens.isJdkGlobal(global),
                "JDK global " + global + " must never be mutated in place");
        }
    }

    /**
     * The guard is deliberately identity-based, not value-based: an IA token
     * that merely <em>equals</em> white is a private instance and is safe (and
     * necessary) to restyle. Only the shared singleton itself is off limits.
     */
    @Test
    @DisplayName("a private colour equal to a JDK global is still restyleable")
    void allowsPrivateInstanceEqualToGlobal() {
        Color privateWhite = new Color(255, 255, 255);

        assertEquals(Color.WHITE, privateWhite, "sanity: equal by value");
        assertFalse(IaColorTokens.isJdkGlobal(privateWhite),
            "an IA-owned instance that happens to equal white must stay restyleable");
    }

    @Test
    @DisplayName("Color.WHITE is untouched by a full install/uninstall cycle")
    void installLeavesWhiteAlone() {
        int whiteBefore = Color.WHITE.getRGB();

        // No Ignition classes on the test classpath, so install() finds no
        // tokens and logs; the point is that it must not damage anything on
        // the way through, and uninstall() must be safe with nothing recorded.
        tokens.install();
        tokens.uninstall();

        assertEquals(whiteBefore, Color.WHITE.getRGB());
        assertEquals(0xFFFFFFFF, Color.WHITE.getRGB());
    }

    @Test
    @DisplayName("uninstall with nothing installed is a no-op, not a failure")
    void uninstallWithoutInstallIsSafe() {
        tokens.uninstall();
        tokens.uninstall();
    }
}
