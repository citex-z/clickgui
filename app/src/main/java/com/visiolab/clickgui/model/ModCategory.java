package com.visiolab.clickgui.model;

/**
 * 模块分类标签（ALL / COMBAT / MOVEMENT / RENDER / PLAYER / OTHER）。
 * 在 ModuleCategory 内容区顶部以一排可切换的标签形式展示。
 */
public enum ModCategory {

    ALL("全部"),
    战斗("战斗"),
    移动("移动"),
    渲染("渲染"),
    玩家("玩家"),
    其它("其它");

    private final String displayName;

    ModCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
