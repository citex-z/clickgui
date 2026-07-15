package com.visiolab.clickgui.hotkey;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮快捷键数据模型 + 持久化。
 * 每个快捷键：自定义文字 + 绑定模块名 + 屏幕坐标。
 */
public class HotkeyItem {
    public String displayText;
    public String boundModuleName;
    public float x, y; // 屏幕坐标（左上角）
    public transient boolean dragging = false;
    public transient boolean pressed = false;
    public transient float pressAnim = 0f; // 按压缩放动画

    public HotkeyItem(String displayText, String boundModuleName) {
        this.displayText = displayText;
        this.boundModuleName = boundModuleName;
        this.x = 100f;
        this.y = 200f;
    }
}
