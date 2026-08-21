package com.pedrodalben.bigbangessentials.npcs;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Bootstraps Minecraft's static registries inside a plain JUnit JVM.
 *
 * <p>The {@code common} module compiles against the NeoForge-patched Minecraft
 * classes, whose {@code FeatureFlags} initializer asks the FML loader for
 * modded feature flags. Outside an FML runtime {@code LoadingModList.get()}
 * returns {@code null} and the whole vanilla bootstrap explodes with an NPE.
 * This helper installs a stub (empty mod list) so vanilla registries
 * initialize normally in unit tests.</p>
 */
public final class MinecraftTestBootstrap {

    private MinecraftTestBootstrap() {
    }

    public static void bootStrap() throws Exception {
        stubFmlLoadingModListIfNeeded();
        stubGameVersionIfNeeded();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    /**
     * Outside a real launcher the version.json resource is missing and
     * {@code SharedConstants.getCurrentVersion()} throws "Game version not
     * set". Install a synthetic 1.21.1 version so vanilla statics can init.
     */
    private static void stubGameVersionIfNeeded() throws Exception {
        java.lang.reflect.Field current = net.minecraft.SharedConstants.class.getDeclaredField("CURRENT_VERSION");
        current.setAccessible(true);
        if (current.get(null) != null) {
            return; // already installed by an earlier bootstrap in this JVM
        }
        net.minecraft.SharedConstants.setVersion(new net.minecraft.WorldVersion() {
            @Override
            public net.minecraft.world.level.storage.DataVersion getDataVersion() {
                return new net.minecraft.world.level.storage.DataVersion(3955);
            }

            @Override
            public String getId() {
                return "1.21.1";
            }

            @Override
            public String getName() {
                return "1.21.1";
            }

            @Override
            public int getProtocolVersion() {
                return 767;
            }

            @Override
            public int getPackVersion(net.minecraft.server.packs.PackType type) {
                return type == net.minecraft.server.packs.PackType.CLIENT_RESOURCES ? 34 : 26;
            }

            @Override
            public java.util.Date getBuildTime() {
                return new java.util.Date(0);
            }

            @Override
            public boolean isStable() {
                return true;
            }
        });
    }

    private static void stubFmlLoadingModListIfNeeded() throws Exception {
        Class<?> loadingModList;
        try {
            loadingModList = Class.forName("net.neoforged.fml.loading.LoadingModList");
        } catch (ClassNotFoundException e) {
            return; // plain-vanilla classpath: FeatureFlags has no FML hook
        }
        Field instance = loadingModList.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        if (instance.get(null) != null) {
            return; // already installed by an earlier bootstrap in this JVM
        }
        Method of = loadingModList.getMethod("of", List.class, List.class, List.class, List.class, Map.class);
        Object empty = of.invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        instance.set(null, empty);
    }
}
