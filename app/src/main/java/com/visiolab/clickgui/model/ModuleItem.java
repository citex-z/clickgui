package com.visiolab.clickgui.model;

import com.visiolab.clickgui.anim.Anim;

import java.util.ArrayList;
import java.util.List;

public class ModuleItem {

    public final String name;
    public final String description;
    public boolean enabled;
    public final boolean hasSlider;
    public float sliderValue;   // 0..1 归一化
    public final float sliderMin, sliderMax;
    public final String sliderUnit;
    public boolean showToggle = true;

    // 额外配置项列表（下拉框、额外开关、额外滑块等）
    public final List<ConfigOption> options = new ArrayList<>();

    // 动画
    public final Anim toggleAnim = new Anim(0f, 12f);
    public final Anim hoverAnim = new Anim(0f, 14f);
    public final Anim hudAnim = new Anim(0f, 10f);

    public boolean expanded = false;
    public final Anim expandAnim = new Anim(0f, 14f);
    public int activeTab = 0;

    public ModuleItem(String name, String description, boolean enabled) {
        this(name, description, enabled, false, 0, 0, 1, "");
    }

    public ModuleItem(String name, String description, boolean enabled,
                       boolean hasSlider, float sliderValue, float min, float max, String unit) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.hasSlider = hasSlider;
        this.sliderValue = sliderValue;
        this.sliderMin = min;
        this.sliderMax = max;
        this.sliderUnit = unit;
        toggleAnim.snapTo(enabled ? 1f : 0f);
    }

    /** 添加一个下拉框配置项 */
    public ModuleItem addDropdown(String label, String[] choices, int defaultIndex) {
        options.add(new ConfigOption(label, ConfigOption.TYPE_DROPDOWN, choices, defaultIndex));
        return this;
    }

    /** 添加一个额外开关配置项 */
    public ModuleItem addSwitch(String label, boolean defaultValue) {
        options.add(new ConfigOption(label, ConfigOption.TYPE_SWITCH, null, defaultValue ? 1f : 0f));
        return this;
    }

    /** 添加一个额外滑块配置项 */
    public ModuleItem addSlider(String label, float defaultValue, float min, float max, String unit) {
        ConfigOption opt = new ConfigOption(label, ConfigOption.TYPE_SLIDER, null,
                (defaultValue - min) / (max - min));
        opt.sliderMin = min;
        opt.sliderMax = max;
        opt.sliderUnit = unit;
        options.add(opt);
        return this;
    }

    public float getRealValue() {
        return sliderMin + (sliderMax - sliderMin) * sliderValue;
    }

    /** 单个配置项（下拉框/开关/滑块） */
    public static class ConfigOption {
        public static final int TYPE_DROPDOWN = 0;
        public static final int TYPE_SWITCH = 1;
        public static final int TYPE_SLIDER = 2;

        public final String label;
        public final int type;
        public final String[] dropdownChoices; // 下拉框选项
        public int dropdownIndex;               // 当前选中索引
        public float sliderValue;               // 0..1 归一化（滑块用）
        public float sliderMin, sliderMax;
        public String sliderUnit;
        public boolean boolValue;               // 开关用
        public final com.visiolab.clickgui.anim.Anim switchAnim = new com.visiolab.clickgui.anim.Anim(0f, 12f); // 开关动画

        public ConfigOption(String label, int type, String[] choices, float defaultValue) {
            this.label = label;
            this.type = type;
            this.dropdownChoices = choices;
            if (type == TYPE_DROPDOWN) {
                this.dropdownIndex = (int) defaultValue;
            } else if (type == TYPE_SWITCH) {
                this.boolValue = defaultValue > 0.5f;
                this.switchAnim.snapTo(this.boolValue ? 1f : 0f);
            } else {
                this.sliderValue = defaultValue;
            }
        }

        public float getRealSliderValue() {
            return sliderMin + (sliderMax - sliderMin) * sliderValue;
        }
    }
}
