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

    /** 还原 Legacy-SoarClient 的 7 大分类 + 常见模块，作为 demo 数据 */
    public static List<Category> buildDefault() {
        List<Category> list = new ArrayList<>();

        Category home = new Category("Home", "\u2302", "MAIN");
        home.add(new ModuleItem("Watermark", "Display client watermark", true));
        home.add(new ModuleItem("Notifications", "Toast notifications on toggle", true));
        list.add(home);

        Category modulesCat = new Category("Module", "\u2694", "MAIN");
        modulesCat.add(new ModuleItem("KillAura", "Auto-attack nearby entities", false,
                true, 0.6f, 1, 8, "blocks"));
        modulesCat.add(new ModuleItem("Sprint", "Auto sprint", true));
        modulesCat.add(new ModuleItem("Scaffold", "Auto place blocks", false));
        modulesCat.add(new ModuleItem("Speed", "Movement speed boost", false,
                true, 0.3f, 1, 10, "level"));
        modulesCat.add(new ModuleItem("FullBright", "Maximum brightness", true));
        modulesCat.add(new ModuleItem("Freecam", "Free camera view", false));
        list.add(modulesCat);

        Category cosmetics = new Category("Cosmetics", "\u2726", "FEATURES");
        cosmetics.add(new ModuleItem("Cape", "Custom cape", true));
        cosmetics.add(new ModuleItem("Particles", "Custom particle effects", false));
        list.add(cosmetics);

        Category music = new Category("Music", "\u266A", "FEATURES");
        music.add(new ModuleItem("Background Music", "Menu background music", true,
                true, 0.5f, 0, 1, "%"));
        list.add(music);

        Category profile = new Category("Profile", "\u263A", "FEATURES");
        profile.add(new ModuleItem("Show Skin", "Show skin preview", true));
        list.add(profile);

        Category screenshot = new Category("Screenshot", "\u2710", "FEATURES");
        screenshot.add(new ModuleItem("Auto Upload", "Auto upload screenshots", false));
        list.add(screenshot);

        Category setting = new Category("Setting", "\u2699", "SYSTEM");
        setting.add(new ModuleItem("Dark Theme", "Dark color scheme", true));
        setting.add(new ModuleItem("Blur Background", "Blur panel background", false));
        setting.add(new ModuleItem("ArrayList", "Show enabled modules list", true));
        setting.add(new ModuleItem("Shadow Enabled", "Panel edge shadow", false));
        ModuleItem gradientSpeedItem = new ModuleItem("Gradient Speed", "Gradient flow speed", false,
                true, 0.4f, 0, 1, "");
        gradientSpeedItem.showToggle = false;
        setting.add(gradientSpeedItem);
        setting.add(new ModuleItem("Notification", "Toggle notifications", true));
        list.add(setting);

        return list;
    }
}
