package com.pedrodalben.bigbangessentials.menu.persistence.yaml;

import com.pedrodalben.bigbangessentials.menu.model.*;
import com.pedrodalben.bigbangessentials.menu.MenuSystem;
import com.pedrodalben.bigbangessentials.util.ChatComponentUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class YamlMenuParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(YamlMenuParser.class);
    private final Yaml yaml = new Yaml();

    // Allowed keys for unknown key checks
    private static final Set<String> ROOT_KEYS = Set.of("id", "schema-version", "size", "title", "flags", "patterns", "pages", "pagination", "dynamic-item-template");
    private static final Set<String> FLAGS_KEYS = Set.of("cache-rendered-items", "close-on-world-change", "prevent-item-take");
    private static final Set<String> PAGINATION_KEYS = Set.of("enabled", "content-slots", "source");
    private static final Set<String> PAGE_KEYS = Set.of("default-page", "items");
    private static final Set<String> ITEM_KEYS = Set.of("slot", "slots", "item", "actions", "deny-actions", "refresh-on-click", "update-on-click", "close-on-click", "cache-rendered-item", "permanent", "priority", "view-permission", "click-permission", "render-conditions", "click-conditions");
    private static final Set<String> ITEM_SPEC_KEYS = Set.of("material-id", "amount", "display-name", "lore");
    private static final Set<String> ACTION_KEYS = Set.of("type", "params", "on-success", "on-failure", "on-deny", "delay-ticks", "chance", "fail-fast", "clicks");
    private static final Set<String> CONDITION_KEYS = Set.of("type", "params", "negate", "failure-message-key");
    private static final Set<String> PERMISSION_KEYS = Set.of("all-of", "all_of", "any-of", "any_of", "none-of", "none_of", "denied-message-key", "denied_message_key", "message");

    public static class MenuValidationException extends Exception {
        private final List<String> errors;
        public MenuValidationException(List<String> errors) {
            super("Menu validation failed with " + errors.size() + " errors: " + String.join("; ", errors));
            this.errors = errors;
        }
        public List<String> getErrors() { return errors; }
    }

    public MenuDefinition parse(Path path) throws Exception {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(path)) {
            root = yaml.load(in);
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML syntax error in " + path.getFileName() + ": " + e.getMessage());
        }

        if (root == null) {
            throw new IllegalArgumentException("Empty YAML file in " + path.getFileName());
        }

        String fileName = path.getFileName().toString().replace(".yml", "").replace(".yaml", "");
        String id = getString(root, "id", fileName);
        if (id.trim().isEmpty()) {
            errors.add("Menu id cannot be empty");
        }

        checkUnknownKeys(root, ROOT_KEYS, "root menu configuration", warnings);

        int schemaVersion = getInt(root, "schema-version", 1);
        if (schemaVersion < 1) {
            errors.add("Invalid schema-version: " + schemaVersion + ". Must be >= 1");
        }

        int size = getInt(root, "size", 54);
        if (size <= 0 || size > 54 || size % 9 != 0) {
            errors.add("Invalid menu size: " + size + ". Must be a multiple of 9 between 9 and 54");
        }

        String rawTitle = getString(root, "title", "Menu");
        Component title = ChatComponentUtil.parseColorCodes(rawTitle);

        // Flags
        Map<String, Object> flagsMap = getMap(root, "flags", Collections.emptyMap());
        checkUnknownKeys(flagsMap, FLAGS_KEYS, "menu flags", warnings);
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
        
        boolean hasDefaultPage = false;
        for (Map.Entry<String, Object> entry : pagesMap.entrySet()) {
            String pageId = entry.getKey();
            if (entry.getValue() instanceof Map pageData) {
                checkUnknownKeys(pageData, PAGE_KEYS, "page '" + pageId + "'", warnings);
                
                boolean isDefault = getBoolean((Map<String, Object>) pageData, "default-page", false);
                if (isDefault) hasDefaultPage = true;

                MenuPageDefinition page = parsePage(id, pageId, (Map<String, Object>) pageData, size, errors, warnings);
                pages.put(pageId, page);
            } else {
                errors.add("Page '" + pageId + "' must be a valid key-value configuration block");
            }
        }

        if (pages.isEmpty()) {
            errors.add("Menu must define at least one page in 'pages'");
        } else if (!hasDefaultPage && !pages.containsKey("main")) {
            errors.add("Menu must have at least one page marked as default-page, or have a page named 'main'");
        }

        // Pagination
        Map<String, Object> paginationMap = getMap(root, "pagination", Collections.emptyMap());
        checkUnknownKeys(paginationMap, PAGINATION_KEYS, "pagination settings", warnings);
        boolean pagEnabled = getBoolean(paginationMap, "enabled", false);
        List<Integer> contentSlots = getIntList(paginationMap, "content-slots", Collections.emptyList());
        String source = getString(paginationMap, "source", "");
        
        MenuItemDefinition dynamicTemplate = null;
        Map<String, Object> templateMap = getMap(root, "dynamic-item-template", null);
        if (templateMap != null) {
            Map<String, Object> templateCopy = new HashMap<>(templateMap);
            if (!templateCopy.containsKey("slot") && !templateCopy.containsKey("slots")) {
                templateCopy.put("slot", 0);
            }
            dynamicTemplate = parseItem(id, "pagination", "dynamic_template", templateCopy, size, errors, warnings);
        }

        if (pagEnabled) {
            if (contentSlots.isEmpty()) {
                errors.add("Menu '" + id + "': Pagination is enabled but 'content-slots' is empty");
            }
            if (source.trim().isEmpty()) {
                errors.add("Menu '" + id + "': Pagination is enabled but 'source' is empty");
            }
            if (dynamicTemplate == null) {
                errors.add("Menu '" + id + "': Pagination is enabled but 'dynamic-item-template' is missing or invalid");
            }

            Set<Integer> uniqueContentSlots = new HashSet<>();
            for (int slot : contentSlots) {
                if (slot < 0 || slot >= size) {
                    errors.add("Menu '" + id + "': Pagination content slot " + slot + " is out of bounds for menu size " + size);
                } else if (!uniqueContentSlots.add(slot)) {
                    errors.add("Menu '" + id + "': Duplicate pagination content slot " + slot);
                }
            }
        }
        
        PaginationSpec pagination = new PaginationSpec(pagEnabled, contentSlots, source, dynamicTemplate);

        // Log warnings in debug mode
        boolean debugEnabled = com.pedrodalben.bigbangessentials.config.ConfigManager.getInstance().isDebugLoggingEnabled();
        if (debugEnabled && !warnings.isEmpty()) {
            for (String warn : warnings) {
                LOGGER.warn("[Menu Validation Warning] Menu '{}' ({}): {}", id, path.getFileName(), warn);
            }
        }

        if (!errors.isEmpty()) {
            throw new MenuValidationException(errors);
        }

        return new MenuDefinition(
            id, schemaVersion, size, title, rawTitle, Collections.emptyMap(),
            null, Collections.emptyList(), pages, patterns, pagination,
            null, flags, Collections.emptyMap()
        );
    }

    private MenuPageDefinition parsePage(String menuId, String pageId, Map<String, Object> data, int menuSize, List<String> errors, List<String> warnings) {
        boolean defaultPage = getBoolean(data, "default-page", false);
        Map<String, Object> itemsMap = getMap(data, "items", Collections.emptyMap());
        Map<String, MenuItemDefinition> items = new HashMap<>();
        
        Set<Integer> boundSlots = new HashSet<>();

        for (Map.Entry<String, Object> itemEntry : itemsMap.entrySet()) {
            String itemId = itemEntry.getKey();
            if (itemEntry.getValue() instanceof Map itemData) {
                checkUnknownKeys((Map<?, ?>) itemData, ITEM_KEYS, "item '" + itemId + "' in page '" + pageId + "'", warnings);
                
                MenuItemDefinition item = parseItem(menuId, pageId, itemId, (Map<String, Object>) itemData, menuSize, errors, warnings);
                items.put(itemId, item);

                // Check slot bounds and duplicates
                for (int slot : item.slotBinding().slots()) {
                    if (slot < 0 || slot >= menuSize) {
                        errors.add("Menu '" + menuId + "', Page '" + pageId + "', Item '" + itemId + "': Slot index " + slot + " is out of bounds for menu size " + menuSize);
                    } else if (!boundSlots.add(slot)) {
                        errors.add("Menu '" + menuId + "', Page '" + pageId + "': Duplicate slot assignment detected for slot " + slot);
                    }
                }
            } else {
                errors.add("Menu '" + menuId + "', Page '" + pageId + "', Item '" + itemId + "': Item configuration must be a map");
            }
        }

        return new MenuPageDefinition(pageId, defaultPage, null, Collections.emptyList(), items, null, Collections.emptyMap());
    }

    private MenuItemDefinition parseItem(String menuId, String pageId, String id, Map<String, Object> data, int menuSize, List<String> errors, List<String> warnings) {
        // Slot binding
        int slot = getInt(data, "slot", -1);
        List<Integer> slots = new ArrayList<>();
        if (slot >= 0) {
            slots.add(slot);
        } else {
            slots.addAll(getIntList(data, "slots", Collections.emptyList()));
        }
        
        if (slots.isEmpty()) {
            errors.add("Menu '" + menuId + "', Page '" + pageId + "', Item '" + id + "': Must define at least one slot index via 'slot' or 'slots'");
        }

        SlotBinding slotBinding = new SlotBinding(pageId, slots, false, 0, false, null);

        // ItemSpec
        Map<String, Object> specData = getMap(data, "item", Collections.emptyMap());
        checkUnknownKeys(specData, ITEM_SPEC_KEYS, "item specification for '" + id + "'", warnings);

        String materialId = getString(specData, "material-id", "minecraft:stone");
        
        // Validate material ID format and existence (bypass if dynamic placeholder)
        boolean hasPlaceholder = materialId.contains("{") && materialId.contains("}");
        if (!hasPlaceholder) {
            try {
                ResourceLocation loc = ResourceLocation.parse(materialId);
        if (BuiltInRegistries.ITEM != null && !BuiltInRegistries.ITEM.keySet().isEmpty()) {
            if (!BuiltInRegistries.ITEM.containsKey(loc)) {
                errors.add("Menu '" + menuId + "', Item '" + id + "': Unknown material-id '" + materialId + "'");
            }
        }
            } catch (Exception e) {
                errors.add("Menu '" + menuId + "', Item '" + id + "': Invalid material-id format '" + materialId + "'");
            }
        }

        int amount = getInt(specData, "amount", 1);
        if (amount < 1 || amount > 99) {
            errors.add("Menu '" + menuId + "', Item '" + id + "': Amount must be between 1 and 99");
        }

        String displayName = getString(specData, "display-name", null);
        List<String> lore = getStringList(specData, "lore", Collections.emptyList());
        
        ItemSpec item = new ItemSpec(materialId, amount, null, displayName, Collections.emptyMap(), lore, Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), null, null, null, null);

        // Permissions
        PermissionSpec viewPerm = parsePermission(menuId, pageId, id, "view-permission", data, errors, warnings);
        PermissionSpec clickPerm = parsePermission(menuId, pageId, id, "click-permission", data, errors, warnings);

        // Conditions
        List<ConditionSpec> renderConditions = parseConditions(menuId, pageId, id, "render-conditions", getList(data, "render-conditions", Collections.emptyList()), errors, warnings);
        List<ConditionSpec> clickConditions = parseConditions(menuId, pageId, id, "click-conditions", getList(data, "click-conditions", Collections.emptyList()), errors, warnings);

        // Actions
        List<ActionSpec> actions = parseActions(menuId, pageId, id, "actions", getList(data, "actions", Collections.emptyList()), errors, warnings);
        List<ActionSpec> denyActions = parseActions(menuId, pageId, id, "deny-actions", getList(data, "deny-actions", Collections.emptyList()), errors, warnings);

        return new MenuItemDefinition(
            id, slotBinding, item, viewPerm, clickPerm,
            renderConditions, clickConditions, actions, denyActions,
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

    private PermissionSpec parsePermission(String menuId, String pageId, String itemId, String key, Map<String, Object> data, List<String> errors, List<String> warnings) {
        Object obj = data.get(key);
        if (obj == null) return null;

        if (obj instanceof String str) {
            return new PermissionSpec(List.of(str), Collections.emptyList(), Collections.emptyList(), null);
        } else if (obj instanceof Map map) {
            checkUnknownKeys((Map<?, ?>) map, PERMISSION_KEYS, "permission spec for item '" + itemId + "'", warnings);
            
            List<String> allOf = getStringList((Map<String, Object>) map, "all-of", getStringList((Map<String, Object>) map, "all_of", Collections.emptyList()));
            List<String> anyOf = getStringList((Map<String, Object>) map, "any-of", getStringList((Map<String, Object>) map, "any_of", Collections.emptyList()));
            List<String> noneOf = getStringList((Map<String, Object>) map, "none-of", getStringList((Map<String, Object>) map, "none_of", Collections.emptyList()));
            
            String deniedMsg = getString((Map<String, Object>) map, "denied-message-key", 
                getString((Map<String, Object>) map, "denied_message_key", 
                getString((Map<String, Object>) map, "message", null)));

            return new PermissionSpec(allOf, anyOf, noneOf, deniedMsg);
        } else {
            errors.add("Menu '" + menuId + "', Page '" + pageId + "', Item '" + itemId + "': Permission spec '" + key + "' must be a String or a Map");
            return null;
        }
    }

    private List<ConditionSpec> parseConditions(String menuId, String pageId, String itemId, String key, List<Object> rawList, List<String> errors, List<String> warnings) {
        List<ConditionSpec> conditions = new ArrayList<>();
        for (int i = 0; i < rawList.size(); i++) {
            Object obj = rawList.get(i);
            if (obj instanceof Map map) {
                checkUnknownKeys((Map<?, ?>) map, CONDITION_KEYS, "condition index " + i + " in item '" + itemId + "'", warnings);

                String type = getString(map, "type", null);
                if (type == null || type.trim().isEmpty()) {
                    errors.add("Menu '" + menuId + "', Item '" + itemId + "', " + key + " index " + i + ": Missing condition 'type'");
                    continue;
                }

                // Verify condition type registration
                if (MenuSystem.getInstance() != null && MenuSystem.getInstance().getConditionRegistry() != null) {
                    if (!MenuSystem.getInstance().getConditionRegistry().getHandler(type).isPresent()) {
                        errors.add("Menu '" + menuId + "', Item '" + itemId + "', " + key + " index " + i + ": Unknown condition type '" + type + "'");
                    }
                }

                Map<String, Object> params = getMap(map, "params", getMap(map, "parameters", Collections.emptyMap()));
                boolean negate = getBoolean(map, "negate", false);
                String failureMsg = getString(map, "failure-message-key", getString(map, "failure_message_key", getString(map, "message", null)));

                conditions.add(new ConditionSpec(type, type + "_" + i, null, negate, failureMsg, params));
            } else {
                errors.add("Menu '" + menuId + "', Item '" + itemId + "', " + key + " index " + i + ": Condition must be a Map");
            }
        }
        return conditions;
    }

    private List<ActionSpec> parseActions(String menuId, String pageId, String itemId, String key, List<Object> rawList, List<String> errors, List<String> warnings) {
        List<ActionSpec> actions = new ArrayList<>();
        for (int i = 0; i < rawList.size(); i++) {
            Object obj = rawList.get(i);
            if (obj instanceof Map map) {
                checkUnknownKeys((Map<?, ?>) map, ACTION_KEYS, "action index " + i + " in item '" + itemId + "'", warnings);

                String type = getString(map, "type", null);
                if (type == null || type.trim().isEmpty()) {
                    errors.add("Menu '" + menuId + "', Item '" + itemId + "', " + key + " index " + i + ": Missing action 'type'");
                    continue;
                }

                // Verify action type registration
                if (MenuSystem.getInstance() != null && MenuSystem.getInstance().getActionRegistry() != null) {
                    if (!MenuSystem.getInstance().getActionRegistry().getHandler(type).isPresent()) {
                        errors.add("Menu '" + menuId + "', Item '" + itemId + "', " + key + " index " + i + ": Unknown action type '" + type + "'");
                    }
                }

                Map<String, Object> params = getMap(map, "params", getMap(map, "parameters", Collections.emptyMap()));
                boolean failFast = getBoolean(map, "fail-fast", true);

                // Nested sub-actions
                List<ActionSpec> onSuccess = parseActions(menuId, pageId, itemId, key + "_onSuccess", getList(map, "on-success", getList(map, "on_success", Collections.emptyList())), errors, warnings);
                List<ActionSpec> onFailure = parseActions(menuId, pageId, itemId, key + "_onFailure", getList(map, "on-failure", getList(map, "on_failure", Collections.emptyList())), errors, warnings);
                List<ActionSpec> onDeny = parseActions(menuId, pageId, itemId, key + "_onDeny", getList(map, "on-deny", getList(map, "on_deny", Collections.emptyList())), errors, warnings);

                List<String> clicks = new ArrayList<>();
                if (map.containsKey("clicks")) {
                    List<?> rawClicks = getList(map, "clicks", Collections.emptyList());
                    for (Object rawClick : rawClicks) {
                        if (rawClick != null) {
                            clicks.add(rawClick.toString().toUpperCase());
                        }
                    }
                }

                actions.add(new ActionSpec(
                    type, type + "_" + i, 
                    getInt(map, "delay-ticks", getInt(map, "delay_ticks", 0)), 
                    getDouble(map, "chance", 1.0), 
                    false, failFast, params, onSuccess, onFailure, onDeny, type, clicks
                ));
            } else {
                errors.add("Menu '" + menuId + "', Item '" + itemId + "', " + key + " index " + i + ": Action must be a Map");
            }
        }
        return actions;
    }

    // Helper methods
    private void checkUnknownKeys(Map<?, ?> map, Set<String> allowedKeys, String context, List<String> warnings) {
        if (map == null) return;
        for (Object keyObj : map.keySet()) {
            String key = String.valueOf(keyObj);
            if (!allowedKeys.contains(key)) {
                warnings.add("Unknown configuration key '" + key + "' in " + context);
            }
        }
    }

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

    private double getDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception e) { return def; }
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
