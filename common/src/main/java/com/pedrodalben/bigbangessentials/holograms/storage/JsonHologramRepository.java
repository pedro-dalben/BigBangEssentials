package com.pedrodalben.bigbangessentials.holograms.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinition;
import com.pedrodalben.bigbangessentials.holograms.api.HologramDefinitionBuilder;
import com.pedrodalben.bigbangessentials.holograms.api.HologramLocation;
import com.pedrodalben.bigbangessentials.holograms.api.HologramPage;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class JsonHologramRepository implements HologramRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonHologramRepository.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "persistent_holograms.json";

    @Override
    public List<HologramDefinition> loadAll() {
        Path path = ResourceUtil.getConfigPath(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.writeString(path, "[]");
                return List.of();
            }

            JsonArray root = GSON.fromJson(Files.readString(path), JsonArray.class);
            if (root == null) {
                return List.of();
            }

            List<HologramDefinition> definitions = new ArrayList<>();
            for (int i = 0; i < root.size(); i++) {
                JsonObject json = root.get(i).getAsJsonObject();
                definitions.add(readDefinition(json));
            }
            return definitions;
        } catch (Exception e) {
            LOGGER.warn("Failed to load persistent holograms: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public void saveAll(Collection<HologramDefinition> definitions) {
        Path path = ResourceUtil.getConfigPath(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            JsonArray root = new JsonArray();
            for (HologramDefinition definition : definitions) {
                root.add(writeDefinition(definition));
            }
            Path temp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, GSON.toJson(root));
            Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("Failed to persist holograms: {}", e.getMessage(), e);
        }
    }

    private HologramDefinition readDefinition(JsonObject json) {
        ResourceLocation dimensionId = ResourceLocation.parse(json.get("dimension").getAsString());
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

        HologramDefinitionBuilder builder = HologramDefinition.builder(json.get("id").getAsString())
            .ownerId(json.has("ownerId") ? json.get("ownerId").getAsString() : "")
            .location(new HologramLocation(
                dimension,
                json.get("x").getAsDouble(),
                json.get("y").getAsDouble(),
                json.get("z").getAsDouble()
            ))
            .viewDistance(json.get("viewDistance").getAsInt())
            .visibilityPolicy(com.pedrodalben.bigbangessentials.holograms.api.HologramVisibilityPolicy.valueOf(json.get("visibilityPolicy").getAsString()))
            .updatePolicy(com.pedrodalben.bigbangessentials.holograms.api.HologramUpdatePolicy.valueOf(json.get("updatePolicy").getAsString()))
            .rendererType(com.pedrodalben.bigbangessentials.holograms.api.HologramRendererType.valueOf(json.get("rendererType").getAsString()))
            .persistent(true)
            .refreshIntervalTicks(json.get("refreshIntervalTicks").getAsInt())
            .offset(
                json.get("offsetX").getAsDouble(),
                json.get("offsetY").getAsDouble(),
                json.get("offsetZ").getAsDouble()
            )
            .lineWidth(json.get("lineWidth").getAsInt())
            .textOpacity(json.get("textOpacity").getAsByte())
            .backgroundColor(json.get("backgroundColor").getAsInt())
            .shadow(json.get("shadow").getAsBoolean())
            .seeThrough(json.get("seeThrough").getAsBoolean())
            .billboard(Display.BillboardConstraints.valueOf(json.get("billboard").getAsString()))
            .scale(json.get("scale").getAsFloat())
            .hideInSpectator(json.has("hideInSpectator") && json.get("hideInSpectator").getAsBoolean())
            .requiredPermission(json.has("requiredPermission") ? json.get("requiredPermission").getAsString() : "")
            .pageSwitchIntervalTicks(json.has("pageSwitchIntervalTicks") ? json.get("pageSwitchIntervalTicks").getAsInt() : 0);

        if (json.has("metadata")) {
            JsonObject metadata = json.getAsJsonObject("metadata");
            for (String key : metadata.keySet()) {
                builder.metadata(key, metadata.get(key).getAsString());
            }
        }

        JsonArray pagesArray = json.getAsJsonArray("pages");
        List<HologramPage> pages = new ArrayList<>();
        for (int i = 0; i < pagesArray.size(); i++) {
            JsonArray linesArray = pagesArray.get(i).getAsJsonArray();
            List<String> lines = new ArrayList<>();
            for (int j = 0; j < linesArray.size(); j++) {
                lines.add(linesArray.get(j).getAsString());
            }
            pages.add(HologramPage.ofLines(lines));
        }
        builder.pages(pages);
        return builder.build();
    }

    private JsonObject writeDefinition(HologramDefinition definition) {
        JsonObject json = new JsonObject();
        json.addProperty("id", definition.id());
        json.addProperty("ownerId", definition.ownerId());
        json.addProperty("dimension", definition.location().dimensionId().toString());
        json.addProperty("x", definition.location().x());
        json.addProperty("y", definition.location().y());
        json.addProperty("z", definition.location().z());
        json.addProperty("viewDistance", definition.viewDistance());
        json.addProperty("visibilityPolicy", definition.visibilityPolicy().name());
        json.addProperty("updatePolicy", definition.updatePolicy().name());
        json.addProperty("rendererType", definition.rendererType().name());
        json.addProperty("refreshIntervalTicks", definition.refreshIntervalTicks());
        json.addProperty("offsetX", definition.offsetX());
        json.addProperty("offsetY", definition.offsetY());
        json.addProperty("offsetZ", definition.offsetZ());
        json.addProperty("lineWidth", definition.lineWidth());
        json.addProperty("textOpacity", definition.textOpacity());
        json.addProperty("backgroundColor", definition.backgroundColor());
        json.addProperty("shadow", definition.shadow());
        json.addProperty("seeThrough", definition.seeThrough());
        json.addProperty("billboard", definition.billboard().name());
        json.addProperty("scale", definition.scale());
        json.addProperty("hideInSpectator", definition.hideInSpectator());
        json.addProperty("requiredPermission", definition.requiredPermission());
        json.addProperty("pageSwitchIntervalTicks", definition.pageSwitchIntervalTicks());

        JsonObject metadata = new JsonObject();
        definition.metadata().forEach(metadata::addProperty);
        json.add("metadata", metadata);

        JsonArray pagesArray = new JsonArray();
        for (var page : definition.pages()) {
            JsonArray linesArray = new JsonArray();
            for (var line : page.lines()) {
                linesArray.add(line.persistentValue());
            }
            pagesArray.add(linesArray);
        }
        json.add("pages", pagesArray);
        return json;
    }
}
