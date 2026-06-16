package com.pedrodalben.bigbangessentials.menu;

import com.pedrodalben.bigbangessentials.menu.api.RegistrationResult;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.action.MenuActionHandler;
import com.pedrodalben.bigbangessentials.menu.action.builtin.*;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionEvaluationContext;
import com.pedrodalben.bigbangessentials.menu.condition.ConditionResult;
import com.pedrodalben.bigbangessentials.menu.condition.MenuConditionHandler;
import com.pedrodalben.bigbangessentials.menu.condition.builtin.*;
import com.pedrodalben.bigbangessentials.menu.model.*;
import com.pedrodalben.bigbangessentials.menu.placeholder.PlaceholderService;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuActionRegistryImpl;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuConditionRegistryImpl;
import com.pedrodalben.bigbangessentials.menu.runtime.MenuRegistry;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.persistence.yaml.YamlMenuParser;
import com.pedrodalben.bigbangessentials.menu.persistence.yaml.YamlMenuPersistenceService;
import com.pedrodalben.bigbangessentials.util.ChatComponentUtil;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MenuSystemTest {

    private MenuActionRegistryImpl actionRegistry;
    private MenuConditionRegistryImpl conditionRegistry;
    private MenuRegistry menuRegistry;

    @BeforeAll
    public static void beforeClass() {
        try {
            Bootstrap.bootStrap();
            System.out.println("Bootstrap succeeded!");
        } catch (Throwable t) {
            System.out.println("Bootstrap failed:");
            t.printStackTrace();
        }
    }

    @BeforeEach
    public void setUp() {
        actionRegistry = new MenuActionRegistryImpl();
        conditionRegistry = new MenuConditionRegistryImpl();
        menuRegistry = new MenuRegistry();
        
        // Mock the MenuSystem singleton to return our test registries
        // (If MenuSystem.getInstance() was already initialized, we can just register handlers on it)
        try {
            MenuSystem.getInstance().initialize();
        } catch (Exception ignored) {}
    }

    @Test
    public void testActionRegistry() {
        MenuActionHandler dummyAction = new MenuActionHandler() {
            @Override
            public String type() { return "dummy_action"; }
            @Override
            public CompletionStage<ActionExecutionResult> execute(ActionContext context) {
                return CompletableFuture.completedFuture(ActionExecutionResult.success());
            }
        };

        RegistrationResult res = actionRegistry.registerActionHandler("dummy_action", dummyAction);
        assertTrue(res.success());
        assertTrue(actionRegistry.getHandler("dummy_action").isPresent());
        assertEquals(dummyAction, actionRegistry.getHandler("dummy_action").get());

        RegistrationResult unreg = actionRegistry.unregisterActionHandler("dummy_action");
        assertTrue(unreg.success());
        assertFalse(actionRegistry.getHandler("dummy_action").isPresent());
    }

    @Test
    public void testConditionRegistry() {
        MenuConditionHandler dummyCondition = new MenuConditionHandler() {
            @Override
            public String type() { return "dummy_cond"; }
            @Override
            public CompletionStage<ConditionResult> evaluate(ConditionEvaluationContext context) {
                return CompletableFuture.completedFuture(ConditionResult.pass());
            }
        };

        RegistrationResult res = conditionRegistry.registerConditionHandler("dummy_cond", dummyCondition);
        assertTrue(res.success());
        assertTrue(conditionRegistry.getHandler("dummy_cond").isPresent());
        assertEquals(dummyCondition, conditionRegistry.getHandler("dummy_cond").get());

        RegistrationResult unreg = conditionRegistry.unregisterConditionHandler("dummy_cond");
        assertTrue(unreg.success());
        assertFalse(conditionRegistry.getHandler("dummy_cond").isPresent());
    }

    @Test
    public void testMenuRegistry() {
        MenuDefinition menu = new MenuDefinition(
            "test_menu", 1, 27, null, "Test Title", Collections.emptyMap(),
            null, Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(), null,
            null, null, Collections.emptyMap()
        );

        menuRegistry.registerMenu(menu);
        assertTrue(menuRegistry.getMenu("test_menu").isPresent());
        assertEquals(menu, menuRegistry.getMenu("test_menu").get());

        List<String> errors = List.of("Duplicate slot detected");
        menuRegistry.registerInvalidMenu("invalid_menu", errors);
        assertTrue(menuRegistry.getInvalidMenus().containsKey("invalid_menu"));
        assertEquals(errors, menuRegistry.getInvalidMenus().get("invalid_menu"));
        assertFalse(menuRegistry.getMenu("invalid_menu").isPresent());
    }

    @Test
    public void testPlaceholderResolution() {
        ServerPlayer player = mock(ServerPlayer.class);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "TestPlayer");
        when(player.getGameProfile()).thenReturn(profile);
        when(player.getName()).thenReturn(Component.literal("TestPlayer"));
        when(player.getUUID()).thenReturn(profile.getId());
        when(player.getHealth()).thenReturn(18.5f);
        
        FoodData food = mock(FoodData.class);
        when(food.getFoodLevel()).thenReturn(15);
        when(player.getFoodData()).thenReturn(food);
        
        Level level = mock(Level.class);
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        when(level.dimension()).thenReturn(dimKey);
        when(player.level()).thenReturn(level);

        when(player.getServer()).thenReturn(null);

        MenuContext context = new MenuContext(
            player.getUUID(), "pt_BR", 
            Map.of("test_key", "Ativado123"), 
            null, "test", "test", UUID.randomUUID()
        );

        // Test resolving player name
        assertEquals("Hello TestPlayer", PlaceholderService.resolve("Hello {player_name}", player, context));
        // Test resolving health and food
        assertEquals("Health: 18.5, Food: 15", PlaceholderService.resolve("Health: {player_health}, Food: {player_food}", player, context));
        // Test resolving world
        assertEquals("World: overworld", PlaceholderService.resolve("World: {player_world}", player, context));
        // Test resolving server players (should fallback to 0/20)
        assertEquals("Online: 0/20", PlaceholderService.resolve("Online: {server_online_players}/{server_max_players}", player, context));
        // Test resolving context placeholder
        assertEquals("Value is Ativado123", PlaceholderService.resolve("Value is {context:test_key}", player, context));
        // Test fallback to raw string when placeholder is unknown
        assertEquals("Hello {unknown_placeholder}", PlaceholderService.resolve("Hello {unknown_placeholder}", player, context));
    }

    @Test
    public void testNamedMenuColorTagsAreParsed() {
        Component parsed = ChatComponentUtil.parseColorCodes("<gold>Menu <yellow>Warps");
        String plain = parsed.getString();
        assertEquals("Menu Warps", plain);
    }

    @Test
    public void testParserAndValidation() throws IOException {
        YamlMenuParser parser = new YamlMenuParser();

        // 1. Validate out of bounds slot
        String invalidSlotYaml = """
            id: broken_slots
            schema-version: 1
            size: 9
            title: "Broken slots"
            pages:
              main:
                default-page: true
                items:
                  button:
                    slot: 12
                    item:
                      material-id: "minecraft:stone"
                      amount: 1
                      display-name: "Stone"
            """;
        Path tempFile = Files.createTempFile("invalid_slot", ".yml");
        Files.writeString(tempFile, invalidSlotYaml);
        
        Exception ex1 = assertThrows(YamlMenuParser.MenuValidationException.class, () -> parser.parse(tempFile));
        assertTrue(ex1.getMessage().contains("Slot index 12 is out of bounds"));
        
        // 2. Validate duplicate slot
        String duplicateSlotYaml = """
            id: duplicate_slots
            schema-version: 1
            size: 9
            title: "Duplicate slots"
            pages:
              main:
                default-page: true
                items:
                  button1:
                    slot: 2
                    item:
                      material-id: "minecraft:stone"
                      amount: 1
                  button2:
                    slot: 2
                    item:
                      material-id: "minecraft:grass_block"
                      amount: 1
            """;
        Files.writeString(tempFile, duplicateSlotYaml);
        Exception ex2 = assertThrows(YamlMenuParser.MenuValidationException.class, () -> parser.parse(tempFile));
        assertTrue(ex2.getMessage().contains("Duplicate slot assignment"));

        // 3. Validate invalid material ID format
        String invalidMaterialYaml = """
            id: invalid_material
            schema-version: 1
            size: 9
            title: "Invalid Material"
            pages:
              main:
                default-page: true
                items:
                  button:
                    slot: 0
                    item:
                      material-id: "minecraft:!!invalid!!"
                      amount: 1
            """;
        Files.writeString(tempFile, invalidMaterialYaml);
        Exception ex3 = assertThrows(YamlMenuParser.MenuValidationException.class, () -> parser.parse(tempFile));
        assertTrue(ex3.getMessage().contains("Invalid material-id format") || ex3.getMessage().contains("Unknown material-id"));

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testParserRejectsInvalidPaginationConfiguration() throws IOException {
        YamlMenuParser parser = new YamlMenuParser();

        String invalidPaginationYaml = """
            id: invalid_pagination
            schema-version: 1
            size: 27
            title: "Invalid Pagination"
            pagination:
              enabled: true
              source: ""
              content-slots: [10, 10, 40]
            pages:
              main:
                default-page: true
                items: {}
            """;

        Path tempFile = Files.createTempFile("invalid_pagination", ".yml");
        Files.writeString(tempFile, invalidPaginationYaml);

        Exception ex = assertThrows(YamlMenuParser.MenuValidationException.class, () -> parser.parse(tempFile));
        assertTrue(ex.getMessage().contains("Pagination is enabled but 'source' is empty"));
        assertTrue(ex.getMessage().contains("Pagination is enabled but 'dynamic-item-template' is missing or invalid"));
        assertTrue(ex.getMessage().contains("Duplicate pagination content slot 10"));
        assertTrue(ex.getMessage().contains("Pagination content slot 40 is out of bounds"));

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testBuiltinActions() {
        ServerPlayer player = mock(ServerPlayer.class);
        MenuSession session = mock(MenuSession.class);
        MenuDefinition menu = mock(MenuDefinition.class);
        MenuPageDefinition page = mock(MenuPageDefinition.class);
        MenuItemDefinition item = mock(MenuItemDefinition.class);
        
        // SendMessageAction
        SendMessageAction sendMsgAction = new SendMessageAction();
        ActionContext context = new ActionContext(
            player, session, menu, page, item, MenuClickType.LEFT, null, 
            Map.of("message", "Test message to player {player_name}")
        );
        when(player.getName()).thenReturn(Component.literal("TestPlayer"));
        
        CompletionStage<ActionExecutionResult> fut = sendMsgAction.execute(context);
        ActionExecutionResult res = fut.toCompletableFuture().join();
        assertEquals(ActionStatus.SUCCESS, res.status());
        verify(player).sendSystemMessage(any(Component.class));
    }

    @Test
    public void testBuiltinConditions() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        
        ConditionSpec spec = new ConditionSpec("context_equals", "test", null, false, "fail", Map.of("key", "test_key", "value", "expected_val"));
        ConditionEvaluationContext context = new ConditionEvaluationContext(
            player, 
            new MenuContext(player.getUUID(), "pt_BR", Map.of("test_key", "expected_val"), null, "test", "test", UUID.randomUUID()),
            spec,
            Map.of("key", "test_key", "value", "expected_val")
        );
        
        ContextEqualsCondition cond = new ContextEqualsCondition();
        ConditionResult res = cond.evaluate(context).toCompletableFuture().join();
        assertEquals(ConditionResultType.PASS, res.type());

        // Test failure
        ConditionEvaluationContext contextFail = new ConditionEvaluationContext(
            player, 
            new MenuContext(player.getUUID(), "pt_BR", Map.of("test_key", "wrong_val"), null, "test", "test", UUID.randomUUID()),
            spec,
            Map.of("key", "test_key", "value", "expected_val")
        );
        ConditionResult resFail = cond.evaluate(contextFail).toCompletableFuture().join();
        assertEquals(ConditionResultType.FAIL, resFail.type());
    }

    @Test
    public void testLoadRealMenus() throws Exception {
        YamlMenuParser parser = new YamlMenuParser();
        
        Path mainMenuPath = Path.of("src/test/resources/menus/main_menu.yml");
        Path secondMenuPath = Path.of("src/test/resources/menus/second_menu.yml");
        
        assertTrue(Files.exists(mainMenuPath), "main_menu.yml should exist");
        assertTrue(Files.exists(secondMenuPath), "second_menu.yml should exist");
        
        MenuDefinition mainMenu = parser.parse(mainMenuPath);
        assertNotNull(mainMenu);
        assertEquals("main_menu", mainMenu.id());
        
        MenuDefinition secondMenu = parser.parse(secondMenuPath);
        assertNotNull(secondMenu);
        assertEquals("second_menu", secondMenu.id());
    }

    @Test
    public void testValidateMenuWithInvalidActionAndCondition() throws IOException {
        YamlMenuParser parser = new YamlMenuParser();
        String yaml = """
            id: invalid_action_menu
            schema-version: 1
            size: 9
            title: "Invalid Action"
            pages:
              main:
                default-page: true
                items:
                  btn:
                    slot: 0
                    item:
                      material-id: "minecraft:stone"
                      amount: 1
                    actions:
                      - type: "this_action_does_not_exist"
                    click-conditions:
                      - type: "this_condition_does_not_exist"
            """;
        Path tempFile = Files.createTempFile("invalid_action_cond", ".yml");
        Files.writeString(tempFile, yaml);
        try {
            Exception ex = assertThrows(YamlMenuParser.MenuValidationException.class, () -> parser.parse(tempFile));
            assertTrue(ex.getMessage().contains("Unknown action type 'this_action_does_not_exist'")
                    || ex.getMessage().contains("Unknown condition type 'this_condition_does_not_exist'"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testReloadAllBehavior() throws IOException {
        MenuRegistry registry = new MenuRegistry();
        Path tempDir = Files.createTempDirectory("menus_test");
        YamlMenuPersistenceService service = new YamlMenuPersistenceService(tempDir, registry);
        
        // Create one valid menu
        String validYaml = """
            id: valid_reload_menu
            schema-version: 1
            size: 9
            title: "Valid"
            pages:
              main:
                default-page: true
                items:
                  btn:
                    slot: 0
                    item:
                      material-id: "minecraft:stone"
            """;
        Files.writeString(tempDir.resolve("menu1.yml"), validYaml);
        
        // Create one invalid menu
        String invalidYaml = """
            id: invalid_reload_menu
            schema-version: 1
            size: 99
            title: "Invalid size"
            pages:
              main:
                default-page: true
                items:
                  btn:
                    slot: 0
                    item:
                      material-id: "minecraft:stone"
            """;
        Files.writeString(tempDir.resolve("menu2.yml"), invalidYaml);
        
        service.loadAllMenus();
        
        assertTrue(registry.getMenu("valid_reload_menu").isPresent());
        assertFalse(registry.getMenu("invalid_reload_menu").isPresent());
        assertTrue(registry.getInvalidMenus().containsKey("menu2"));
        
        // Clean up temp dir
        Files.deleteIfExists(tempDir.resolve("menu1.yml"));
        Files.deleteIfExists(tempDir.resolve("menu2.yml"));
        Files.delete(tempDir);
    }

    @Test
    public void testActionExecutionSetRemoveContext() {
        ServerPlayer player = mock(ServerPlayer.class);
        MenuSession session = new MenuSession();
        session.setSessionData(new HashMap<>());
        
        ActionContext contextSet = new ActionContext(
            player, session, mock(MenuDefinition.class), mock(MenuPageDefinition.class), mock(MenuItemDefinition.class),
            MenuClickType.LEFT, null, Map.of("key", "test_var", "value", "hello_world")
        );
        
        SetContextValueAction setAction = new SetContextValueAction();
        ActionExecutionResult resSet = setAction.execute(contextSet).toCompletableFuture().join();
        assertEquals(ActionStatus.SUCCESS, resSet.status());
        assertEquals("hello_world", session.getSessionData().get("test_var"));
        
        ActionContext contextRemove = new ActionContext(
            player, session, mock(MenuDefinition.class), mock(MenuPageDefinition.class), mock(MenuItemDefinition.class),
            MenuClickType.LEFT, null, Map.of("key", "test_var")
        );
        
        RemoveContextValueAction removeAction = new RemoveContextValueAction();
        ActionExecutionResult resRemove = removeAction.execute(contextRemove).toCompletableFuture().join();
        assertEquals(ActionStatus.SUCCESS, resRemove.status());
        assertFalse(session.getSessionData().containsKey("test_var"));
    }

    @Test
    public void testConditionPermissionAndDenyAction() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        
        PermissionCondition cond = new PermissionCondition();
        ConditionSpec spec = new ConditionSpec("permission", "perm1", null, false, "failed", Map.of("permission", "test.vip"));
        ConditionEvaluationContext context = new ConditionEvaluationContext(
            player, new MenuContext(player.getUUID(), "pt_BR", Collections.emptyMap(), null, "test", "test", UUID.randomUUID()),
            spec, Map.of("permission", "test.vip")
        );
        
        ConditionResult res = cond.evaluate(context).toCompletableFuture().join();
        assertEquals(ConditionResultType.FAIL, res.type());
    }

    @Test
    public void testOpenAndCloseMenuActions() throws Exception {
        ServerPlayer player = mock(ServerPlayer.class);
        com.pedrodalben.bigbangessentials.menu.api.MenuService mockService = mock(com.pedrodalben.bigbangessentials.menu.api.MenuService.class);
        
        // Inject mockService into MenuSystem
        java.lang.reflect.Field field = MenuSystem.class.getDeclaredField("menuService");
        field.setAccessible(true);
        field.set(MenuSystem.getInstance(), mockService);
        
        // 1. OpenMenuAction
        when(mockService.openMenu(eq(player), eq("target_menu"), any())).thenReturn(CompletableFuture.completedFuture(new com.pedrodalben.bigbangessentials.menu.api.MenuOpenResult(true, null)));
        
        OpenMenuAction openAction = new OpenMenuAction();
        ActionContext openContext = new ActionContext(
            player, mock(MenuSession.class), mock(MenuDefinition.class), mock(MenuPageDefinition.class), mock(MenuItemDefinition.class),
            MenuClickType.LEFT, new MenuContext(UUID.randomUUID(), "pt_BR", Collections.emptyMap(), null, "test", "test", UUID.randomUUID()), 
            Map.of("menu", "target_menu")
        );
        
        ActionExecutionResult openRes = openAction.execute(openContext).toCompletableFuture().join();
        assertEquals(ActionStatus.SUCCESS, openRes.status());
        verify(mockService).openMenu(eq(player), eq("target_menu"), any());
        
        // 2. CloseMenuAction
        MenuDefinition menuDef = mock(MenuDefinition.class);
        when(menuDef.id()).thenReturn("menu_to_close");
        
        CloseMenuAction closeAction = new CloseMenuAction();
        ActionContext closeContext = new ActionContext(
            player, mock(MenuSession.class), menuDef, mock(MenuPageDefinition.class), mock(MenuItemDefinition.class),
            MenuClickType.LEFT, null, Collections.emptyMap()
        );
        
        ActionExecutionResult closeRes = closeAction.execute(closeContext).toCompletableFuture().join();
        assertEquals(ActionStatus.SUCCESS, closeRes.status());
        verify(mockService).closeMenu(eq(player), eq("menu_to_close"), any());
    }
}
