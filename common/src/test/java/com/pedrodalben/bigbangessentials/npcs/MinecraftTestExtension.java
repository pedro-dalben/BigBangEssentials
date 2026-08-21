package com.pedrodalben.bigbangessentials.npcs;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Global JUnit extension: installs the FML stub and bootstraps Minecraft's
 * static registries BEFORE the first test class runs.
 *
 * <p>Without it, the first test class that touches {@code FeatureFlags} (for
 * example via {@code Bootstrap.bootStrap()} wrapped in {@code try/catch}) fails
 * its class initializer. A failed initializer can never be retried in the same
 * JVM, so every later class that needs the registry dies with
 * {@code NoClassDefFoundError} — the failure ordering makes tests flaky.</p>
 */
public final class MinecraftTestExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        try {
            MinecraftTestBootstrap.bootStrap();
        } catch (Throwable t) {
            // Do not fail unrelated tests: classes that need the registry will
            // report their own (clearer) failure when they touch it.
            System.err.println("[MinecraftTestExtension] Minecraft bootstrap failed: " + t);
        }
    }
}
