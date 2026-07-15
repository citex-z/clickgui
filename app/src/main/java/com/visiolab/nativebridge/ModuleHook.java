package com.visiolab.nativebridge;

import com.visiolab.clickgui.model.ModuleItem;

/**
 * 模块钩子：将 ClickGUI 中的模块开关和配置项变更路由到 NativeBridge。
 *
 * 使用方式：
 * 在 UIRenderer 中，当用户切换模块或修改配置时调用：
 *   ModuleHook.onToggle(moduleItem);
 *   ModuleHook.onConfigChange(moduleItem, configLabel, value);
 */
public class ModuleHook {

    /**
     * 模块开关切换时调用，根据模块名称路由到对应的 native 方法。
     */
    public static void onToggle(ModuleItem m) {
        if (!NativeBridge.isNativeLoaded()) return; // native 库未加载时静默跳过

        boolean en = m.enabled;
        switch (m.name) {
            // Combat
            case "KillAura":
                float kaRange = getConfigSlider(m, "Range", 3.5f);
                float kaSpeed = getConfigSlider(m, "Speed", 4.5f);
                NativeBridge.setKillAuraEnabled(en, kaRange, kaSpeed);
                break;
            case "AutoClicker":
                int cps = (int) getConfigSlider(m, "CPS", 10);
                boolean leftOnly = getConfigBool(m, "Only Left", true);
                NativeBridge.setAutoClickerEnabled(en, cps, leftOnly);
                break;
            case "Criticals":
                NativeBridge.setCriticalsEnabled(en);
                break;
            case "TriggerBot":
                float tbRange = getConfigSlider(m, "Range", 3.5f);
                NativeBridge.setTriggerBotEnabled(en, tbRange);
                break;

            // Movement
            case "Fly":
                float flySpeed = getConfigSlider(m, "Speed", 1.0f);
                NativeBridge.setFlyEnabled(en, flySpeed);
                break;
            case "Speed":
                float spd = getConfigSlider(m, "Speed", 1.0f);
                NativeBridge.setSpeedEnabled(en, spd);
                break;
            case "NoFall":
                NativeBridge.setNoFallEnabled(en);
                break;
            case "AutoJump":
                NativeBridge.setAutoJumpEnabled(en);
                break;
            case "SprintToggle":
                NativeBridge.setSprintToggleEnabled(en);
                break;
            case "AirJump":
                NativeBridge.setAirJumpEnabled(en);
                break;
            case "Phase":
                NativeBridge.setPhaseEnabled(en);
                break;

            // Render
            case "ESP":
                NativeBridge.setEspEnabled(en, 0, new float[]{1f, 0f, 0f, 1f});
                break;
            case "Tracers":
                NativeBridge.setTracersEnabled(en, new float[]{1f, 0f, 0f, 1f});
                break;
            case "FullBright":
                float bright = getConfigSlider(m, "Level", 15f);
                NativeBridge.setFullBrightEnabled(en, bright);
                break;
            case "NameTags":
                float ntScale = getConfigSlider(m, "Scale", 1.0f);
                NativeBridge.setNameTagsEnabled(en, ntScale);
                break;
            case "Chams":
                NativeBridge.setChamsEnabled(en, new float[]{0f, 1f, 0f, 1f});
                break;
            case "XRay":
                NativeBridge.setXrayEnabled(en, new int[]{14, 15, 56, 73, 129, 153});
                break;
            case "Trajectories":
                NativeBridge.setTrajectoriesEnabled(en);
                break;

            // Player
            case "FastEat":
                float feSpeed = getConfigSlider(m, "Speed", 2.0f);
                NativeBridge.setFastEatEnabled(en, feSpeed);
                break;
            case "AutoRespawn":
                NativeBridge.setAutoRespawnEnabled(en);
                break;
            case "Reach":
                float rRange = getConfigSlider(m, "Range", 4.5f);
                NativeBridge.setReachEnabled(en, rRange);
                break;
            case "Freecam":
                NativeBridge.setFreecamEnabled(en);
                break;
            case "NoClip":
                NativeBridge.setNoClipEnabled(en);
                break;

            // World
            case "Nuker":
                float nkRange = getConfigSlider(m, "Range", 4.0f);
                NativeBridge.setNukerEnabled(en, nkRange);
                break;
            case "AutoMine":
                NativeBridge.setAutoMineEnabled(en);
                break;
            case "Scaffold":
                float sfRange = getConfigSlider(m, "Range", 4.0f);
                NativeBridge.setScaffoldEnabled(en, sfRange);
                break;
            case "FastPlace":
                float fpSpeed = getConfigSlider(m, "Speed", 2.0f);
                NativeBridge.setFastPlaceEnabled(en, fpSpeed);
                break;
            case "Timer":
                float tmSpeed = getConfigSlider(m, "Speed", 1.0f);
                NativeBridge.setTimerEnabled(en, tmSpeed);
                break;
        }
    }

    /**
     * 配置项变更时调用。
     */
    public static void onConfigChange(ModuleItem m, String configLabel, String value) {
        if (!NativeBridge.isNativeLoaded()) return;
        NativeBridge.onConfigChanged(m.name, configLabel, value);
    }

    // ===== 辅助方法：从 ModuleItem 的 options 中读取配置值 =====

    private static float getConfigSlider(ModuleItem m, String label, float defaultVal) {
        if (m.options == null) return defaultVal;
        for (ModuleItem.ConfigOption opt : m.options) {
            if (label.equals(opt.label) && opt.type == ModuleItem.ConfigOption.TYPE_SLIDER) {
                return opt.sliderValue;
            }
        }
        return defaultVal;
    }

    private static boolean getConfigBool(ModuleItem m, String label, boolean defaultVal) {
        if (m.options == null) return defaultVal;
        for (ModuleItem.ConfigOption opt : m.options) {
            if (label.equals(opt.label) && opt.type == ModuleItem.ConfigOption.TYPE_SWITCH) {
                return opt.boolValue;
            }
        }
        return defaultVal;
    }

    private static int getConfigDropdown(ModuleItem m, String label, int defaultVal) {
        if (m.options == null) return defaultVal;
        for (ModuleItem.ConfigOption opt : m.options) {
            if (label.equals(opt.label) && opt.type == ModuleItem.ConfigOption.TYPE_DROPDOWN) {
                return opt.dropdownIndex;
            }
        }
        return defaultVal;
    }
}
