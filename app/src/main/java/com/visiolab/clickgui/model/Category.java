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
        client.add(new ModuleItem("ArrayList", "Display enabled modules HUD", true)
                .addSwitch("Background", true)
                .addDropdown("Color", new String[]{"Custom", "Rainbow", "LightRainbow", "Astolfo", "Valentine"}, 1)
                .addSwitch("Hide Render Modules", true)
                .addSlider("Custom Speed", 10, 5, 20, ""));
        client.add(new ModuleItem("ClickGUI", "Click GUI settings", true)
                .addSwitch("Blur Background", true)
                .addSlider("GUI Scale", 1, 0.5f, 2.0f, "x"));
        client.add(new ModuleItem("Watermark", "Client watermark HUD", true)
                .addSwitch("Show Version", true)
                .addSwitch("Show FPS", false));
        client.add(new ModuleItem("Notifications", "Toast notifications", true)
                .addSlider("Duration", 2, 1, 5, "s")
                .addSwitch("Show in Chat", true));
        client.add(new ModuleItem("Keystrokes", "Display WASD + mouse keys", false)
                .addSwitch("Show Mouse", true)
                .addSlider("Size", 1, 0.5f, 2.0f, "x"));
        client.add(new ModuleItem("Radar", "Entity radar HUD", false)
                .addDropdown("Shape", new String[]{"Circle", "Square"}, 0)
                .addSlider("Range", 50, 10, 200, "blocks"));
        client.add(new ModuleItem("HUD Editor", "Edit HUD positions", false));
        client.add(new ModuleItem("Cape", "Custom client cape", false));
        list.add(client);

        Category combat = new Category("Combat", "\u2694", "MOD CONFIG");
        combat.add(new ModuleItem("KillAura", "Auto attack nearby entities", false,
                true, 0.6f, 1, 8, "blocks")
                .addDropdown("Mode", new String[]{"Switch", "Single"}, 0)
                .addDropdown("Priority", new String[]{"Distance", "Health"}, 0)
                .addDropdown("CPS Mode", new String[]{"Tick", "Delay", "HurtTime"}, 0)
                .addSlider("Min CPS", 8, 1, 20, "cps")
                .addSlider("Max CPS", 12, 1, 20, "cps")
                .addSlider("Wall Range", 3, 0, 6, "blocks")
                .addDropdown("Auto Block", new String[]{"OFF", "Fake", "Grim"}, 2)
                .addSwitch("Silent Rotation", true)
                .addSwitch("Sprint", true)
                .addSwitch("Auto Disable", false));
        combat.add(new ModuleItem("Criticals", "Critical hit bypass", false)
                .addDropdown("Mode", new String[]{"Packet", "Jump", "NoGround"}, 0));
        combat.add(new ModuleItem("Velocity", "Reduce knockback", false,
                true, 0.3f, 0, 1, "%")
                .addDropdown("Mode", new String[]{"Simple", "AAC", "Grim"}, 0)
                .addSwitch("Only Ground", true));
        combat.add(new ModuleItem("AutoClicker", "Auto clicker", false,
                true, 0.5f, 1, 20, "cps")
                .addDropdown("Mode", new String[]{"Left", "Right", "Both"}, 0)
                .addSwitch("Break Blocks", false));
        combat.add(new ModuleItem("Reach", "Extended attack range", false,
                true, 0.5f, 3, 6, "blocks"));
        combat.add(new ModuleItem("AimAssist", "Smooth aim assistance", false,
                true, 0.4f, 0, 1, "%")
                .addDropdown("Mode", new String[]{"Smooth", "Instant", "Silent"}, 0)
                .addSlider("FOV", 90, 30, 180, "deg")
                .addSwitch("Through Walls", false));
        combat.add(new ModuleItem("AutoArmor", "Auto equip best armor", false)
                .addSwitch("Drop Wrong", true));
        combat.add(new ModuleItem("TriggerBot", "Auto attack on crosshair", false,
                true, 0.5f, 1, 20, "cps"));
        list.add(combat);

        Category movement = new Category("Movement", "\u2192", "MOD CONFIG");
        movement.add(new ModuleItem("Sprint", "Auto sprint", true)
                .addDropdown("Mode", new String[]{"Normal", "Omni"}, 0));
        movement.add(new ModuleItem("Speed", "Movement speed boost", false,
                true, 0.3f, 1, 10, "level")
                .addSwitch("Follow Target", true)
                .addSwitch("Only When Jump", true)
                .addSwitch("Adaptive", true));
        movement.add(new ModuleItem("Fly", "Free flight", false)
                .addDropdown("Mode", new String[]{"Vanilla", "Creative", "Packet"}, 0)
                .addSlider("Speed", 1, 1, 10, "x"));
        movement.add(new ModuleItem("NoSlow", "Remove slowdown", false)
                .addSwitch("Soul Sand", true)
                .addSwitch("Web", true));
        movement.add(new ModuleItem("Step", "Auto step up blocks", false,
                true, 0.5f, 0.5f, 2.5f, "blocks"));
        movement.add(new ModuleItem("NoFall", "Remove fall damage", false)
                .addDropdown("Mode", new String[]{"Packet", "AirJump", "NoGround"}, 0));
        movement.add(new ModuleItem("Jesus", "Walk on water", false));
        movement.add(new ModuleItem("Spider", "Climb walls", false));
        movement.add(new ModuleItem("HighJump", "Jump higher", false,
                true, 0.3f, 1, 5, "x"));
        movement.add(new ModuleItem("Sneak", "Auto sneak", false));
        list.add(movement);

        Category player = new Category("Player", "\u263A", "MOD CONFIG");
        player.add(new ModuleItem("FullBright", "Maximum brightness", true)
                .addDropdown("Mode", new String[]{"Gamma", "NightVision", "Potion"}, 0));
        player.add(new ModuleItem("Freecam", "Free camera view", false)
                .addSlider("Speed", 1, 1, 10, "x"));
        player.add(new ModuleItem("NoFall", "Remove fall damage", false)
                .addDropdown("Mode", new String[]{"Packet", "AirJump"}, 0));
        player.add(new ModuleItem("AutoRespawn", "Auto respawn on death", false)
                .addSlider("Delay", 1, 0, 5, "s"));
        player.add(new ModuleItem("AntiAFK", "Prevent AFK kick", false)
                .addSlider("Interval", 30, 5, 120, "s"));
        player.add(new ModuleItem("FastEat", "Eat faster", false,
                true, 0.5f, 0.1f, 1, "x"));
        player.add(new ModuleItem("SkinBlinker", "Flash skin", false)
                .addSlider("Speed", 0.5f, 0.1f, 2, "Hz"));
        player.add(new ModuleItem("AutoFish", "Auto fish", false));
        list.add(player);

        Category render = new Category("Render", "\u25C9", "MOD CONFIG");
        render.add(new ModuleItem("ESP", "Entity ESP overlay", false)
                .addDropdown("Mode", new String[]{"Box", "Glow", "2D", "3D"}, 0));
        render.add(new ModuleItem("Tracers", "Entity tracer lines", false)
                .addDropdown("Mode", new String[]{"Simple", "Fade", "Rainbow"}, 0));
        render.add(new ModuleItem("Nametags", "Enhanced nametags", false)
                .addSwitch("Show Health", true)
                .addSwitch("Show Ping", true));
        render.add(new ModuleItem("Chams", "Entity chams", false)
                .addSwitch("Through Walls", true));
        render.add(new ModuleItem("XRay", "X-ray vision", false));
        render.add(new ModuleItem("FullBright", "Maximum brightness", false)
                .addDropdown("Mode", new String[]{"Gamma", "NightVision"}, 0));
        render.add(new ModuleItem("NoHurtCam", "Remove hurt camera", false));
        render.add(new ModuleItem("MotionBlur", "Motion blur effect", false,
                true, 0.3f, 0, 1, "%"));
        render.add(new ModuleItem("FreeCam", "Free camera view", false));
        render.add(new ModuleItem("Zoom", "Zoom view", false,
                true, 0.3f, 1, 10, "x")
                .addSwitch("Smooth", true));
        // HUD 模块（参考南方客户端）
        render.add(new ModuleItem("PotionEffects", "Display active potion effects", false));
        render.add(new ModuleItem("TargetHUD", "Target info display", false));
        render.add(new ModuleItem("ArmorHUD", "Display armor durability", false)
                .addSwitch("Show Durability", true));
        render.add(new ModuleItem("CPS", "Clicks per second counter", false));
        render.add(new ModuleItem("FPS", "FPS counter HUD", false));
        render.add(new ModuleItem("Coordinates", "Player coordinates HUD", false)
                .addSwitch("Show Direction", true));
        render.add(new ModuleItem("ReachDisplay", "Attack reach display", false));
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
