package com.pedrodalben.bigbangessentials.menu.persistence.yaml;

import com.pedrodalben.bigbangessentials.menu.model.*;
import net.minecraft.network.chat.Component;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class YamlMenuParser {
    private final Yaml yaml = new Yaml();

    public MenuDefinition parse(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> root = yaml.load(in);
            if (root == null) throw new IllegalArgumentException("Empty YAML");

            String id = getString(root, "id", path.getFileName().toString().replace(".yml", ""));
            int schemaVersion = getInt(root, "schema-version", 1);
            int size = getInt(root, "size", 54);
            Component title = Component.literal(getString(root, "title", "Menu").replace("<gold>", "§6"));
            String rawTitle = getString(root, "title", "Menu");
            
            // Flags
            Map<String, Object> flagsMap = getMap(root, "flags", Collections.emptyMap());
            MenuFlags flags = new MenuFlags(
                getBoolean(flagsMap, "cache-rendered-items", false),
                getBoolean(flagsMap, "close-on-world-change", true),
                getBoolean(flagsMap, "prevent-item-take", true)
            );

            // Patterns
            List<String> patterns = getStringList(root, "patterns", Collections.emptyList());

            // Pages
            Map<String, MenuPageDefinition> pages = new HashMap<>();
            Map<String, Object> pagesMap = getMap(root, "pages", Collections.emptyMap());
            for (Map.Entry<String, Object> entry : pagesMap.entrySet()) {
                String pageId = entry.getKey();
                Map<String, Object> pageData = (Map<String, Object>) entry.getValue();
                pages.put(pageId, parsePage(pageId, pageData));
            }

            // Pagination
            Map<String, Object> paginationMap = getMap(root, "pagination", Collections.emptyMap());
            boolean pagEnabled = getBoolean(paginationMap, "enabled", false);
            List<Integer> contentSlots = getIntList(paginationMap, "content-slots", Collections.emptyList());
            String source = getString(paginationMap, "source", "");
            
            PaginationSpec pagination = new PaginationSpec(pagEnabled, contentSlots, source, null);

            return new MenuDefinition(
                id, schemaVersion, size, title, rawTitle, Collections.emptyMap(),
                null, Collections.emptyList(), pages, patterns, pagination,
                null, flags, Collections.emptyMap()
            );
        }
    }

    private MenuPageDefinition parsePage(String pageId, Map<String, Object> data) {
        boolean defaultPage = getBoolean(data, "default-page", false);
        Map<String, Object> itemsMap = getMap(data, "items", Collections.emptyMap());
        Map<String, MenuItemDefinition> items = new HashMap<>();
        
        for (Map.Entry<String, Object> itemEntry : itemsMap.entrySet()) {
            String itemId = itemEntry.getKey();
            Map<String, Object> itemData = (Map<String, Object>) itemEntry.getValue();
            items.put(itemId, parseItem(itemId, pageId, itemData));
        }

        return new MenuPageDefinition(pageId, defaultPage, null, Collections.emptyList(), items, null, Collections.emptyMap());
    }

    private MenuItemDefinition parseItem(String id, String pageId, Map<String, Object> data) {
        // Slot
        int slot = getInt(data, "slot", -1);
        List<Integer> slots = new ArrayList<>();
        if (slot >= 0) slots.add(slot);
        else slots.addAll(getIntList(data, "slots", Collections.emptyList()));
        
        SlotBinding slotBinding = new SlotBinding(pageId, slots, false, 0, false, null);

        // ItemSpec
        Map<String, Object> specData = getMap(data, "item", Collections.emptyMap());
        String materialId = getString(specData, "material-id", "minecraft:stone");
        int amount = getInt(specData, "amount", 1);
        String displayName = getString(specData, "display-name", null);
        List<String> lore = getStringList(specData, "lore", Collections.emptyList());
        
        ItemSpec item = new ItemSpec(materialId, amount, null, displayName, Collections.emptyMap(), lore, Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), null, null, null, null);

        // Actions
        List<ActionSpec> actions = parseActions(getList(data, "actions", Collections.emptyList()));
        List<ActionSpec> denyActions = parseActions(getList(data, "deny-actions", Collections.emptyList()));

        return new MenuItemDefinition(
            id, slotBinding, item, null, null,
            Collections.emptyList(), Collections.emptyList(), actions, denyActions,
            getBoolean(data, "refresh-on-click", false),
            getBoolean(data, "update-on-click", false),
            getBoolean(data, "close-on-click", false),
            getBoolean(data, "cache-rendered-item", false),
            getBoolean(data, "permanent", false),
            getInt(data, "priority", 0),
            Collections.emptyMap(),
            Collections.emptyList()
        );
    }

    private List<ActionSpec> parseActions(List<Object> rawList) {
        List<ActionSpec> actions = new ArrayList<>();
        for (Object obj : rawList) {
            if (obj instanceof Map map) {
                String type = getString(map, "type", "unknown");
                Map<String, Object> params = getMap(map, "params", Collections.emptyMap());
                actions.add(new ActionSpec(type, type, 0, 1.0, false, true, params, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), type));
            }
        }
        return actions;
    }

    // Helper methods
    private String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : def;
    }
    private int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception e) { return def; }
        }
        return def;
    }
    private boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return def;
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key, Map<String, Object> def) {
        Object val = map.get(key);
        return val instanceof Map ? (Map<String, Object>) val : def;
    }
    @SuppressWarnings("unchecked")
    private List<Object> getList(Map<String, Object> map, String key, List<Object> def) {
        Object val = map.get(key);
        return val instanceof List ? (List<Object>) val : def;
    }
    private List<String> getStringList(Map<String, Object> map, String key, List<String> def) {
        List<Object> list = getList(map, key, null);
        if (list == null) return def;
        List<String> res = new ArrayList<>();
        for (Object o : list) res.add(String.valueOf(o));
        return res;
    }
    private List<Integer> getIntList(Map<String, Object> map, String key, List<Integer> def) {
        List<Object> list = getList(map, key, null);
        if (list == null) return def;
        List<Integer> res = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) res.add(n.intValue());
        }
        return res;
    }
}
