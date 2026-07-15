package com.visiolab.clickgui.hotkey;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 快捷键管理器：持久化存储 + 增删改查。
 * 数据存储在 SharedPreferences 中，JSON 序列化。
 */
public class HotkeyManager {

    private static final String PREFS_NAME = "soar_hotkeys";
    private static final String KEY_HOTKEYS = "hotkey_list";

    private final List<HotkeyItem> hotkeys = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean loaded = false;

    private static HotkeyManager instance;

    public static HotkeyManager getInstance() {
        if (instance == null) instance = new HotkeyManager();
        return instance;
    }

    private HotkeyManager() {}

    /** 初始化：加载持久化数据 */
    public void init(Context context) {
        if (loaded) return;
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
        loaded = true;
    }

    /** 无 Context 的后备初始化（使用已有的 prefs） */
    public void ensureLoaded() {
        if (!loaded && prefs != null) {
            load();
            loaded = true;
        }
    }

    public List<HotkeyItem> getHotkeys() {
        return hotkeys;
    }

    public void addHotkey(HotkeyItem item) {
        hotkeys.add(item);
        save();
    }

    public void removeHotkey(int index) {
        if (index >= 0 && index < hotkeys.size()) {
            hotkeys.remove(index);
            save();
        }
    }

    public void removeHotkey(HotkeyItem item) {
        hotkeys.remove(item);
        save();
    }

    public void updatePosition(HotkeyItem item, float x, float y) {
        item.x = x;
        item.y = y;
        save();
    }

    public void save() {
        if (prefs == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (HotkeyItem h : hotkeys) {
                JSONObject obj = new JSONObject();
                obj.put("text", h.displayText);
                obj.put("module", h.boundModuleName);
                obj.put("x", h.x);
                obj.put("y", h.y);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_HOTKEYS, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void load() {
        if (prefs == null) return;
        hotkeys.clear();
        try {
            String json = prefs.getString(KEY_HOTKEYS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                HotkeyItem h = new HotkeyItem(
                        obj.getString("text"),
                        obj.getString("module"));
                h.x = (float) obj.getDouble("x");
                h.y = (float) obj.getDouble("y");
                hotkeys.add(h);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isLoaded() {
        return loaded;
    }
}
