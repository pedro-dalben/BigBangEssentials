package com.pedrodalben.bigbangessentials.tablist;

import com.pedrodalben.bigbangessentials.tablist.render.CompiledTabTemplate;
import com.pedrodalben.bigbangessentials.tablist.render.TabAnimationRegistry;
import com.pedrodalben.bigbangessentials.tablist.render.TabTemplateCompiler;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabTemplateCompilerTest {

    @BeforeAll
    static void beforeAll() {
        try { Bootstrap.bootStrap(); } catch (Throwable ignored) {}
    }

    @Test
    void compile_LiteralOnly() {
        CompiledTabTemplate tpl = TabTemplateCompiler.compile("Hello World");
        assertEquals("Hello World", tpl.render(null, null));
    }

    @Test
    void compile_EmptyString() {
        CompiledTabTemplate tpl = TabTemplateCompiler.compile("");
        assertEquals("", tpl.render(null, null));
    }

    @Test
    void compile_NullString() {
        CompiledTabTemplate tpl = TabTemplateCompiler.compile(null);
        assertEquals("", tpl.render(null, null));
    }

    @Test
    void compile_ColorCodes() {
        CompiledTabTemplate tpl = TabTemplateCompiler.compile("&aGreen &cRed");
        assertEquals("\u00a7aGreen \u00a7cRed", tpl.render(null, null));
    }

    @Test
    void compile_InternalPlaceholderDetected() {
        CompiledTabTemplate tpl = TabTemplateCompiler.compile("{prefix}Hello");
        assertTrue(tpl.getPlaceholderDependencies().contains("prefix"));
    }

    @Test
    void compile_ExternalPlaceholderDetected() {
        CompiledTabTemplate tpl = TabTemplateCompiler.compile("{player_name}");
        assertTrue(tpl.getPlaceholderDependencies().contains("player_name"));
    }

    @Test
    void compile_AnimationDetected() {
        CompiledTabTemplate tpl = TabTemplateCompiler.compile("{animation:my_anim}");
        assertTrue(tpl.getAnimationDependencies().contains("my_anim"));
    }

    @Test
    void render_AnimationFromRegistry() {
        CompiledTabTemplate tpl = TabTemplateCompiler.compile("{animation:test_anim}");
        TabAnimationRegistry reg = new TabAnimationRegistry();
        java.util.Map<String, com.pedrodalben.bigbangessentials.tablist.config.TablistConfig.AnimationSection> anims = new java.util.HashMap<>();
        com.pedrodalben.bigbangessentials.tablist.config.TablistConfig.AnimationSection section = new com.pedrodalben.bigbangessentials.tablist.config.TablistConfig.AnimationSection();
        section.frames = java.util.List.of("Frame1", "Frame2");
        anims.put("test_anim", section);
        reg.loadFromConfig(anims);
        reg.tickAll(); // advance 0 ticks, still frame 1
        assertEquals("Frame1", tpl.render(null, reg));
    }

    @Test
    void render_UnknownAnimation() {
        CompiledTabTemplate tpl = TabTemplateCompiler.compile("{animation:unknown}");
        TabAnimationRegistry reg = new TabAnimationRegistry();
        reg.loadFromConfig(new java.util.HashMap<>());
        // Unknown animations show the raw placeholder as fallback
        assertEquals("{animation:unknown}", tpl.render(null, reg));
    }
}
