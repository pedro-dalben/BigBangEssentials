package com.pedrodalben.bigbangessentials.menu.integration.rankup;

import com.pedrodalben.bigbangessentials.menu.persistence.yaml.YamlMenuParser;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.PaginationSpec;
import com.pedrodalben.bigbangessentials.menu.model.ActionSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankupMenuParsingTest {

    private static YamlMenuParser parser;

    @BeforeAll
    static void setup() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable t) {
            // Ignored for testing
        }
        parser = new YamlMenuParser();
    }

    private MenuDefinition parseYaml(String menuName) throws Exception {
        RankupMenuIntegration integration = RankupMenuIntegration.getInstance();
        Method method = RankupMenuIntegration.class.getDeclaredMethod("getHardcodedDefault", String.class);
        method.setAccessible(true);
        String yaml = (String) method.invoke(integration, menuName);

        Path tempFile = Files.createTempFile(menuName, ".yml");
        Files.writeString(tempFile, yaml, StandardCharsets.UTF_8);
        MenuDefinition spec = parser.parse(tempFile);
        Files.deleteIfExists(tempFile);
        return spec;
    }

    @Test
    void testRankupMenuYamlParses() throws Exception {
        MenuDefinition spec = parseYaml("rankup_menu.yml");
        assertNotNull(spec);
        assertEquals("rankup_menu", spec.id());
        
        PaginationSpec pagination = spec.pagination();
        assertNotNull(pagination);
        assertTrue(pagination.enabled());
        
        assertNotNull(pagination.dynamicItemTemplate());
        List<ActionSpec> actions = pagination.dynamicItemTemplate().actions();
        assertFalse(actions.isEmpty());
        ActionSpec clickAction = actions.get(0);
        assertEquals("rankup_rank_click", clickAction.type());
        assertEquals("{rank_id}", clickAction.params().get("rank_id"));
    }

    @Test
    void testRankDetailsMenuYamlParses() throws Exception {
        MenuDefinition spec = parseYaml("rankup_rank_details_menu.yml");
        assertNotNull(spec);
        assertEquals("rankup_rank_details_menu", spec.id());
        
        List<Integer> pagSlots = spec.pagination().contentSlots();
        spec.pages().get("main").items().values().forEach(item -> {
            item.slotBinding().slots().forEach(slot -> {
                assertFalse(pagSlots.contains(slot), "Fixed item slot overlaps with pagination slot: " + slot);
            });
        });
    }

    @Test
    void testAdminHomeMenuYamlParses() throws Exception {
        MenuDefinition spec = parseYaml("rankup_admin_home_menu.yml");
        assertNotNull(spec);
        assertEquals("rankup_admin_home_menu", spec.id());
        
        PaginationSpec pagination = spec.pagination();
        assertNotNull(pagination);
        ActionSpec action = pagination.dynamicItemTemplate().actions().get(0);
        assertEquals("rankup_admin", action.type());
        assertEquals("{rank_id}", action.params().get("rank_id"));
        
        List<Integer> pagSlots = spec.pagination().contentSlots();
        spec.pages().get("main").items().values().forEach(item -> {
            item.slotBinding().slots().forEach(slot -> {
                assertFalse(pagSlots.contains(slot), "Fixed item slot overlaps with pagination slot: " + slot);
            });
        });
    }

    @Test
    void testAdminRankEditMenuYamlParses() throws Exception {
        MenuDefinition spec = parseYaml("rankup_admin_rank_edit_menu.yml");
        assertNotNull(spec);
        assertEquals("rankup_admin_rank_edit_menu", spec.id());
    }
}
