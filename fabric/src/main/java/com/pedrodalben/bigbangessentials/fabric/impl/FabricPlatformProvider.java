package com.pedrodalben.bigbangessentials.fabric.impl;

import com.pedrodalben.bigbangessentials.fabric.accessor.FabricEntityDataAccessor;
import com.pedrodalben.bigbangessentials.util.PlatformProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;

public class FabricPlatformProvider implements PlatformProvider {
    private static MinecraftServer activeServer;

    public static void setServer(MinecraftServer server) {
        activeServer = server;
    }

    @Override
    public MinecraftServer getCurrentServer() {
        return activeServer;
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public String getModName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getName())
                .orElse(null);
    }

    @Override
    public String getModVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
    }

    @Override
    public String getLoaderName() {
        return "Fabric";
    }

    @Override
    public String getLoaderVersion() {
        return getModVersion("fabricloader");
    }

    @Override
    public Collection<ModInfo> getMods() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(container -> new ModInfo(
                        container.getMetadata().getId(),
                        container.getMetadata().getName(),
                        container.getMetadata().getVersion().getFriendlyString()
                ))
                .toList();
    }

    @Override
    public CompoundTag getPersistentData(Entity entity) {
        return ((FabricEntityDataAccessor) entity).bbEssentials$getPersistentData();
    }

    private static final Collection<Object> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public void postEvent(Object event) {
        List<ListenerInvocation> invocations = new ArrayList<>();
        int order = 0;

        for (Object listener : listeners) {
            Method[] methods;
            try {
                methods = listener.getClass().getDeclaredMethods();
            } catch (Throwable t) {
                continue;
            }

            for (Method method : methods) {
                SubscribeEvent subscribeEvent = method.getAnnotation(SubscribeEvent.class);
                if (subscribeEvent == null || method.getParameterCount() != 1) {
                    continue;
                }

                if (!method.getParameterTypes()[0].isAssignableFrom(event.getClass())) {
                    continue;
                }

                invocations.add(new ListenerInvocation(
                        listener,
                        method,
                        subscribeEvent.priority(),
                        subscribeEvent.receiveCanceled(),
                        order++
                ));
            }
        }

        invocations.sort(Comparator
                .comparingInt((ListenerInvocation invocation) -> invocation.priority().ordinal())
                .thenComparingInt(ListenerInvocation::order));

        for (ListenerInvocation invocation : invocations) {
            if (event instanceof ICancellableEvent cancellable
                    && cancellable.isCanceled()
                    && !invocation.receiveCanceled()) {
                continue;
            }

            try {
                invocation.method().setAccessible(true);
                invocation.method().invoke(invocation.listener(), event);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    cause.printStackTrace();
                } else {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void registerEventListener(Object listener) {
        listeners.add(listener);
    }

    private record ListenerInvocation(
            Object listener,
            Method method,
            EventPriority priority,
            boolean receiveCanceled,
            int order
    ) {}
}
