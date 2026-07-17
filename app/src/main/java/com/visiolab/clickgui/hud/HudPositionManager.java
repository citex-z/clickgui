package com.visiolab.clickgui.hud;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

/**
 * HUD 元素位置持久化管理器。
 *
 * 存储 4 个 HUD 元素的 X/Y 偏移量（相对于默认位置的像素偏移）：
 * - arraylist: ArrayList 模块列表
 * - watermark: 水印
 * - keystrokes: 按键显示
 * - island: 灵动岛
 *
 * 使用 SharedPreferences（key: soar_hud_positions），JSON 格式存储。
 */
public class HudPositionManager {

    private static final String TAG = "HudPositionManager";
    private static final String PREFS_KEY = "soar_hud_positions";

    // 元素名称常量
    public static final String ELEMENT_ARRAYLIST = "arraylist";
    public static final String ELEMENT_WATERMARK = "watermark";
    public static final String ELEMENT_KEYSTROKES = "keystrokes";
    public static final String ELEMENT_ISLAND = "island";

    private static SharedPreferences prefs;
    private static JSONObject cache;

    /** 初始化（在 OverlayManager.attach 时调用） */
    public static synchronized void init(Context ctx) {
        if (prefs != null) return;
        prefs = ctx.getApplicationContext().getSharedPreferences("soar_hud", Context.MODE_PRIVATE);
        loadCache();
    }

    /** 从 SharedPreferences 加载 JSON 缓存 */
    private static void loadCache() {
        try {
            String json = prefs.getString(PREFS_KEY, "{}");
            cache = new JSONObject(json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load positions", e);
            cache = new JSONObject();
        }
    }

    /** 保存到 SharedPreferences */
    private static void saveCache() {
        try {
            prefs.edit().putString(PREFS_KEY, cache.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save positions", e);
        }
    }

    /** 获取元素 X 偏移量（像素） */
    public static float getOffsetX(String element) {
        try {
            JSONObject elem = cache.optJSONObject(element);
            if (elem != null) return (float) elem.optDouble("x", 0);
        } catch (Exception ignored) {}
        return 0f;
    }

    /** 获取元素 Y 偏移量（像素） */
    public static float getOffsetY(String element) {
        try {
            JSONObject elem = cache.optJSONObject(element);
            if (elem != null) return (float) elem.optDouble("y", 0);
        } catch (Exception ignored) {}
        return 0f;
    }

    /** 设置元素偏移量并保存 */
    public static void setOffset(String element, float x, float y) {
        try {
            JSONObject elem = new JSONObject();
            elem.put("x", (double) x);
            elem.put("y", (double) y);
            cache.put(element, elem);
            saveCache();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set offset for " + element, e);
        }
    }

    /** 重置所有元素位置到默认 */
    public static void resetAll() {
        try {
            cache = new JSONObject();
            saveCache();
        } catch (Exception e) {
            Log.e(TAG, "Failed to reset positions", e);
        }
    }
}
