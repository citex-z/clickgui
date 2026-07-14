package com.visiolab.clickgui.model;

import java.util.ArrayList;
import java.util.List;

public class Category {

    public final String name;
    public final String icon; // emoji 占位图标，可换成自绘 SVG/纹理图集
    public final String section; // 分组：MAIN / FEATURES / SYSTEM
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

        // MOD CONFIG section
        Category client = new Category("Client", "\u2302", "MOD CONFIG");
        client.add(new ModuleItem("ArrayList", "Display enabled modules HUD", true));
        client.add(new ModuleItem("ClickGUI", "Click GUI settings", true));
        client.add(new ModuleItem("Watermark", "Client watermark HUD", true));
        client.add(new ModuleItem("Notifications", "Toast notifications", true));
        client.add(new ModuleItem("Radar", "Entity radar", false));
        client.add(new ModuleItem("HUD Editor", "Edit HUD positions", false));
        list.add(client);

        Category combat = new Category("Combat", "\u2694", "MOD CONFIG");
        combat.add(new ModuleItem("KillAura", "Auto attack nearby entities", false,
                true, 0.6f, 1, 8, "blocks"));
        combat.add(new ModuleItem("Criticals", "Critical hit bypass", false));
        combat.add(new ModuleItem("Velocity", "Reduce knockback", false,
                true, 0.3f, 0, 1, "%"));
        combat.add(new ModuleItem("AutoClicker", "Auto click", false,
                true, 0.5f, 1, 20, "cps"));
        list.add(combat);

        Category misc = new Category("Misc", "\u2726", "MOD CONFIG");
        misc.add(new ModuleItem("Scaffold", "Auto place blocks", false));
        misc.add(new ModuleItem("Timer", "Game timer speed", false,
                true, 0.4f, 0.1f, 10, "x"));
        misc.add(new ModuleItem("IRC", "IRC friend list", false));
        list.add(misc);

        Category movement = new Category("Movement", "\u2192", "MOD CONFIG");
        movement.add(new ModuleItem("Sprint", "Auto sprint", true));
        movement.add(new ModuleItem("Speed", "Movement speed boost", false,
                true, 0.3f, 1, 10, "level"));
        movement.add(new ModuleItem("Fly", "Free flight", false));
        movement.add(new ModuleItem("NoSlow", "Remove slowdown", false));
        list.add(movement);

        Category player = new Category("Player", "\u263A", "MOD CONFIG");
        player.add(new ModuleItem("FullBright", "Maximum brightness", true));
        player.add(new ModuleItem("Freecam", "Free camera view", false));
        player.add(new ModuleItem("NoFall", "Remove fall damage", false));
        list.add(player);

        Category render = new Category("Render", "\u25C9", "MOD CONFIG");
        render.add(new ModuleItem("ESP", "Entity ESP overlay", false));
        render.add(new ModuleItem("Tracers", "Entity tracer lines", false));
        render.add(new ModuleItem("Nametags", "Enhanced nametags", false));
        render.add(new ModuleItem("Chams", "Entity chams", false));
        list.add(render);

        Category world = new Category("World", "\u25EF", "MOD CONFIG");
        world.add(new ModuleItem("XRay", "X-ray vision", false));
        world.add(new ModuleItem("Nuker", "Block nuker", false));
        world.add(new ModuleItem("FastPlace", "Fast block placement", false));
        list.add(world);

        // PERSONALIZATION section
        Category preferences = new Category("Preferences", "\u2699", "PERSONALIZATION");
        preferences.add(new ModuleItem("Dark Theme", "Dark color scheme", true));
        preferences.add(new ModuleItem("Blur Background", "Blur panel background", false));
        preferences.add(new ModuleItem("Gradient Speed", "Gradient flow speed", false,
                true, 0.4f, 0, 1, ""));
        preferences.add(new ModuleItem("Theme", "Accent color theme", true));
        list.add(preferences);

        return list;
    }
}
