package com.visiolab.clickgui.model;

import com.visiolab.clickgui.anim.Anim;

public class ModuleItem {

    public final String name;
    public final String description;
    public boolean enabled;
    public final boolean hasSlider;
    public float sliderValue;   // 0..1 归一化
    public final float sliderMin, sliderMax;
    public final String sliderUnit;
    // 有些"模块"其实只是纯数值配置(比如渐变速度)，没有真正的开关语义，
    // 这种情况下不应该在行内显示一个开关控件——默认true，特殊场景手动关掉。
    public boolean showToggle = true;

    // 每一行的展开/悬停动画（对应原版 Category 里每个 Module 卡片的 hover 状态）
    public final Anim toggleAnim = new Anim(0f, 12f);
    public final Anim hoverAnim = new Anim(0f, 14f);
    // ArrayList HUD 专用的滑入/淡出动画：跟 toggleAnim 分开，因为 ClickGUI 面板关闭时
    // toggleAnim 不会再更新（drawModuleCards 不执行），但 HUD 得始终独立继续动画。
    public final Anim hudAnim = new Anim(0f, 10f);

    // 卡片展开/收起状态：点击 ▾ 箭头切换，内联展开设置面板
    public boolean expanded = false;
    public final Anim expandAnim = new Anim(0f, 14f); // 0=收起, 1=展开
    public int activeTab = 0; // 当前标签页索引

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

    public float getRealValue() {
        return sliderMin + (sliderMax - sliderMin) * sliderValue;
    }
}
