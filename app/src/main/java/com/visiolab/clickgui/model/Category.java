package com.visiolab.clickgui.model;

import java.util.ArrayList;
import java.util.List;

public class Category {

    public final String name;
    public final String icon;
    public final String section; // 分组：MOD CONFIG / PERSONALIZATION
    public final List<ModuleItem> modules = new ArrayList<>();

    public Category(String name, String icon, String section) {
        this.name = name;
        this.icon = icon;
        this.section = section;
    }

    public Category add(ModuleItem m) {
        modules.add(m);
        return this;
    }

    public static List<Category> buildDefault() {
        List<Category> list = new ArrayList<>();

        // ===== MOD CONFIG =====
        Category client = new Category("Client", "\u2302", "MOD CONFIG");
        client.add(new ModuleItem("ArrayList", "Display enabled modules HUD", true));
        client.add(new ModuleItem("ClickGUI", "Click GUI settings", true));
        client.add(new ModuleItem("Watermark", "Client watermark HUD", true));
        client.add(new ModuleItem("Notifications", "Toast notifications", true));
        client.add(new ModuleItem("Keystrokes", "Display WASD + mouse keys", false));
        client.add(new ModuleItem("Radar", "Entity radar HUD", false));
        client.add(new ModuleItem("HUD Editor", "Edit HUD positions", false));
        client.add(new ModuleItem("Cape", "Custom client cape", false));
        list.add(client);

        Category combat = new Category("Combat", "\u2694", "MOD CONFIG");
        combat.add(new ModuleItem("KillAura", "Auto attack nearby entities", false,
                true, 0.6f, 1, 8, "blocks"));
        combat.add(new ModuleItem("Criticals", "Critical hit bypass", false));
        combat.add(new ModuleItem("Velocity", "Reduce knockback", false,
                true, 0.3f, 0, 1, "%"));
        combat.add(new ModuleItem("AutoClicker", "Auto clicker", false,
                true, 0.5f, 1, 20, "cps"));
        combat.add(new ModuleItem("Reach", "Extended attack range", false,
                true, 0.5f, 3, 6, "blocks"));
        combat.add(new ModuleItem("AimAssist", "Smooth aim assistance", false,
                true, 0.4f, 0, 1, "%"));
        combat.add(new ModuleItem("AutoArmor", "Auto equip best armor", false));
        combat.add(new ModuleItem("TriggerBot", "Auto attack on crosshair", false));
        list.add(combat);

        Category movement = new Category("Movement", "\u2192", "MOD CONFIG");
        movement.add(new ModuleItem("Sprint", "Auto sprint", true));
        movement.add(new ModuleItem("Speed", "Movement speed boost", false,
                true, 0.3f, 1, 10, "level"));
        movement.add(new ModuleItem("Fly", "Free flight", false));
        movement.add(new ModuleItem("NoSlow", "Remove slowdown", false));
        movement.add(new ModuleItem("Step", "Auto step up blocks", false,
                true, 0.5f, 0.5f, 2.5f, "blocks"));
        movement.add(new ModuleItem("NoFall", "Remove fall damage", false));
        movement.add(new ModuleItem("Jesus", "Walk on water", false));
        movement.add(new ModuleItem("Spider", "Climb walls", false));
        movement.add(new ModuleItem("HighJump", "Jump higher", false,
                true, 0.3f, 1, 5, "x"));
        movement.add(new ModuleItem("Sneak", "Auto sneak", false));
        list.add(movement);

        Category player = new Category("Player", "\u263A", "MOD CONFIG");
        player.add(new ModuleItem("FullBright", "Maximum brightness", true));
        player.add(new ModuleItem("Freecam", "Free camera view", false));
        player.add(new ModuleItem("NoFall", "Remove fall damage", false));
        player.add(new ModuleItem("AutoRespawn", "Auto respawn on death", false));
        player.add(new ModuleItem("AntiAFK", "Prevent AFK kick", false));
        player.add(new ModuleItem("FastEat", "Eat faster", false,
                true, 0.5f, 0.1f, 1, "x"));
        player.add(new ModuleItem("SkinBlinker", "Flash skin", false));
        player.add(new ModuleItem("AutoFish", "Auto fish", false));
        list.add(player);

        Category render = new Category("Render", "\u25C9", "MOD CONFIG");
        render.add(new ModuleItem("ESP", "Entity ESP overlay", false));
        render.add(new ModuleItem("Tracers", "Entity tracer lines", false));
        render.add(new ModuleItem("Nametags", "Enhanced nametags", false));
        render.add(new ModuleItem("Chams", "Entity chams", false));
        render.add(new ModuleItem("XRay", "X-ray vision", false));
        render.add(new ModuleItem("FullBright", "Maximum brightness", false));
        render.add(new ModuleItem("NoHurtCam", "Remove hurt camera", false));
        render.add(new ModuleItem("MotionBlur", "Motion blur effect", false,
                true, 0.3f, 0, 1, "%"));
        render.add(new ModuleItem("FreeCam", "Free camera view", false));
        render.add(new ModuleItem("Zoom", "Zoom view", false,
                true, 0.3f, 1, 10, "x"));
        list.add(render);

        Category world = new Category("World", "\u25EF", "MOD CONFIG");
        world.add(new ModuleItem("Nuker", "Block nuker", false));
        world.add(new ModuleItem("FastPlace", "Fast block placement", false));
        world.add(new ModuleItem("AutoMine", "Auto mine blocks", false));
        world.add(new ModuleItem("Scaffold", "Auto place blocks", false));
        world.add(new ModuleItem("Timer", "Game timer speed", false,
                true, 0.4f, 0.1f, 10, "x"));
        world.add(new ModuleItem("AutoFarm", "Auto harvest crops", false));
        world.add(new ModuleItem("MobAura", "Auto attack mobs", false));
        list.add(world);

        Category misc = new Category("Misc", "\u2726", "MOD CONFIG");
        misc.add(new ModuleItem("IRC", "IRC friend list", false));
        misc.add(new ModuleItem("AutoGG", "Auto GG on win", false));
        misc.add(new ModuleItem("AutoTip", "Auto tip players", false));
        misc.add(new ModuleItem("NameProtect", "Hide player names", false));
        misc.add(new ModuleItem("FakeLag", "Simulate lag", false,
                true, 0.3f, 0, 1, "s"));
        misc.add(new ModuleItem("PingSpoof", "Fake ping", false,
                true, 0.2f, 0, 999, "ms"));
        list.add(misc);

        // ===== PERSONALIZATION =====
        Category preferences = new Category("Preferences", "\u2699", "PERSONALIZATION");
        preferences.add(new ModuleItem("Dark Theme", "Dark color scheme", true));
        preferences.add(new ModuleItem("Blur Background", "Blur panel background", false));
        preferences.add(new ModuleItem("Gradient Speed", "Gradient flow speed", false,
                true, 0.4f, 0, 1, ""));
        preferences.add(new ModuleItem("Theme Color", "Accent color theme", true));
        preferences.add(new ModuleItem("Background Opacity", "Panel transparency", false,
                true, 0.9f, 0.3f, 1, "%"));
        preferences.add(new ModuleItem("Text Scale", "UI text size", false,
                true, 1.0f, 0.5f, 2.0f, "x"));
        list.add(preferences);

        return list;
    }
}
