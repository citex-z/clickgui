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

    /** 模块名称中英文映射 */
    public static String localizedName(String name, boolean zh) {
        if (!zh) return name;
        switch (name) {
            // Client
            case "ArrayList": return "模块列表";
            case "ClickGUI": return "点击界面";
            case "Watermark": return "水印";
            case "Notifications": return "通知";
            case "Keystrokes": return "按键显示";
            case "Radar": return "雷达";
            case "HUD Editor": return "HUD编辑器";
            case "Cape": return "披风";
            // Combat
            case "KillAura": return "杀戮光环";
            case "Criticals": return "暴击";
            case "Velocity": return "击退减免";
            case "AutoClicker": return "自动点击";
            case "Reach": return "攻击距离";
            case "AimAssist": return "瞄准辅助";
            case "AutoArmor": return "自动穿甲";
            case "TriggerBot": return "触发攻击";
            // Movement
            case "Sprint": return "自动疾跑";
            case "Speed": return "加速";
            case "Fly": return "飞行";
            case "NoSlow": return "无减速";
            case "Step": return "自动跨步";
            case "NoFall": return "无掉落伤害";
            case "Jesus": return "水上行走";
            case "Spider": return "爬墙";
            case "HighJump": return "高跳";
            case "Sneak": return "自动潜行";
            // Player
            case "FullBright": return "全亮";
            case "Freecam": return "自由视角";
            case "AutoRespawn": return "自动重生";
            case "AntiAFK": return "防挂机";
            case "FastEat": return "快速进食";
            case "SkinBlinker": return "皮肤闪烁";
            case "AutoFish": return "自动钓鱼";
            // Render
            case "ESP": return "实体透视";
            case "Tracers": return "追踪线";
            case "Nametags": return "名称标签";
            case "Chams": return "实体高亮";
            case "XRay": return "透视";
            case "NoHurtCam": return "无受伤抖动";
            case "MotionBlur": return "动态模糊";
            case "FreeCam": return "自由相机";
            case "Zoom": return "缩放";
            case "PotionEffects": return "药水效果";
            case "TargetHUD": return "目标显示";
            case "ArmorHUD": return "护甲显示";
            case "CPS": return "点击速度";
            case "FPS": return "帧率显示";
            case "Coordinates": return "坐标显示";
            case "ReachDisplay": return "距离显示";
            // World
            case "Nuker": return "方块破坏";
            case "FastPlace": return "快速放置";
            case "AutoMine": return "自动挖矿";
            case "Scaffold": return "自动搭桥";
            case "Timer": return "时间加速";
            case "AutoFarm": return "自动农场";
            case "MobAura": return "生物光环";
            // Misc
            case "IRC": return "聊天";
            case "AutoGG": return "自动GG";
            case "AutoTip": return "自动打赏";
            case "NameProtect": return "名称保护";
            case "FakeLag": return "假延迟";
            case "PingSpoof": return "延迟伪装";
            // Preferences
            case "Dark Theme": return "深色主题";
            case "Blur Background": return "模糊背景";
            case "Gradient Speed": return "渐变速度";
            case "Theme Color": return "主题颜色";
            case "Background Opacity": return "背景透明度";
            case "Text Scale": return "文字缩放";
            // 配置选项标签也翻译
            case "Mode": return "模式";
            case "Priority": return "优先级";
            case "CPS Mode": return "CPS模式";
            case "Min CPS": return "最小CPS";
            case "Max CPS": return "最大CPS";
            case "Wall Range": return "穿墙距离";
            case "Auto Block": return "自动格挡";
            case "Silent Rotation": return "静默旋转";
            case "Auto Disable": return "自动禁用";
            case "Follow Target": return "跟随目标";
            case "Only When Jump": return "仅跳跃时";
            case "Adaptive": return "自适应";
            case "Soul Sand": return "灵魂沙";
            case "Web": return "蛛网";
            case "Show Health": return "显示血量";
            case "Show Ping": return "显示延迟";
            case "Through Walls": return "穿墙";
            case "Smooth": return "平滑";
            case "Background": return "背景";
            case "Color": return "颜色";
            case "Hide Render Modules": return "隐藏渲染模块";
            case "Custom Speed": return "自定义速度";
            case "GUI Scale": return "界面缩放";
            case "Show Version": return "显示版本";
            case "Show FPS": return "显示帧率";
            case "Duration": return "持续时间";
            case "Show in Chat": return "聊天中显示";
            case "Show Mouse": return "显示鼠标";
            case "Size": return "大小";
            case "Shape": return "形状";
            case "Range": return "范围";
            case "Delay": return "延迟";
            case "Interval": return "间隔";
            case "Drop Wrong": return "丢弃旧装备";
            case "Break Blocks": return "破坏方块";
            case "Show Direction": return "显示方向";
            case "Show Durability": return "显示耐久";
            case "FOV": return "视野范围";
            default: return name;
        }
    }

    /** 分类名称中英文映射 */
    public static String localizedCategory(String name, boolean zh) {
        if (!zh) return name;
        switch (name) {
            case "Client": return "客户端";
            case "Combat": return "战斗";
            case "Movement": return "移动";
            case "Player": return "玩家";
            case "Render": return "渲染";
            case "World": return "世界";
            case "Misc": return "其他";
            case "Preferences": return "偏好设置";
            default: return name;
        }
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
