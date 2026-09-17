package com.mustrysolutions.designerdarkmode.designer;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnection;
import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnectionManager;
import com.inductiveautomation.ignition.client.model.ClientLocalizationManager;
import com.inductiveautomation.ignition.client.model.LocaleListener;
import com.inductiveautomation.ignition.common.i18n.translation.KeyHashRule;
import com.inductiveautomation.ignition.common.i18n.translation.Translation;
import com.inductiveautomation.ignition.common.i18n.translation.TranslationMap;
import com.inductiveautomation.ignition.common.i18n.translation.TranslationPackageDiff;

/**
 * The two client statics some Vision components read in their constructors,
 * stubbed so the probe can build them headlessly: a Comments Panel asks
 * {@code GatewayConnectionManager.getInstance()} for push notifications, a
 * Spinner (and the Easy Chart, which also needs a display) asks
 * {@code ClientLocalizationManager.get()} for the locale. Both answer with
 * nothing, which is all a construction needs. The Easy Chart stays
 * unbuildable: it creates a {@code DropTarget}, which is a
 * {@code HeadlessException}.
 */
final class VisionClientStubs {

    private static boolean installed;

    private VisionClientStubs() {
    }

    static synchronized void install() throws Exception {
        if (installed) {
            return;
        }
        GatewayConnection connection = (GatewayConnection) Proxy.newProxyInstance(
            VisionClientStubs.class.getClassLoader(), new Class<?>[] {GatewayConnection.class},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? Boolean.FALSE
                : method.getReturnType() == int.class ? Integer.valueOf(0) : null);
        GatewayConnectionManager.setConnection(connection);
        Field instance = ClientLocalizationManager.class.getDeclaredField("_instance");
        instance.setAccessible(true);
        instance.set(null, new NoTranslations());
        installed = true;
    }

    /** A localization manager with one locale and no translations. */
    static final class NoTranslations extends ClientLocalizationManager {
        @Override public boolean isTranslationEnabled() { return false; }
        @Override public Collection<Locale> getAvailableLocales() { return List.of(Locale.US); }
        @Override public String get(Locale locale, String key) { return key; }
        @Override public String getString(String key) { return key; }
        @Override public String getStrict(String key) { return null; }
        @Override public String getStrict(Locale locale, String key) { return null; }
        @Override public String getStringForBundleKey(String key) { return key; }
        @Override public TranslationMap getTranslationsFor(String key) { return null; }
        @Override public Locale getCurrentLocale() { return Locale.US; }
        @Override public void setCurrentLocale(Locale locale) { }
        @Override public Locale getPreviewLocale() { return Locale.US; }
        @Override public void addLocaleListener(LocaleListener listener) { }
        @Override public void removeLocaleListener(LocaleListener listener) { }
        @Override public void resetLocale() { }
        @Override public Locale getBaseLocale() { return Locale.US; }
        @Override public KeyHashRule getKeyHashRule() { return null; }
        @Override public boolean isDefined(String key) { return false; }
        @Override public boolean isDefined(String key, Locale locale) { return false; }
        @Override public Collection<String> getAvailableKeys() { return List.of(); }
        @Override public TranslationPackageDiff createDiff() { return null; }
        @Override public Iterator<Translation> getAllTranslations() { return Collections.emptyIterator(); }
    }
}
