package com.mustrysolutions.designerdarkmode.designer;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/**
 * Runs a test class's tests and its {@code @BeforeEach}/{@code @AfterEach}
 * on the EDT, each as one event, the way a Designer runs the switch.
 *
 * <p>Run from the test thread instead, a switch and the snapshots around it
 * share the JVM with whatever the EDT does meanwhile, and two flakes came
 * from that (#160, #171). A JIDE component whose UI is updated on the EDT
 * between Synthetica's {@code setLookAndFeel} and its {@code setFont} installs
 * JIDE's extension itself, from Synthetica's Tahoma 11, and the module's own
 * install then finds it done (#160). One updated after a restore and before a
 * snapshot puts its lazy action map into the defaults, and the next restore's
 * fresh table does not have it (#171). Both were reproduced by doing exactly
 * that once; as one EDT event, nothing can land in between.
 */
final class RunOnEdt implements InvocationInterceptor {

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        onEdt(invocation);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        onEdt(invocation);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        onEdt(invocation);
    }

    private static void onEdt(Invocation<Void> invocation) throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                invocation.proceed();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }
}
