package com.visiolab.clickgui.gl;

import android.view.MotionEvent;

import com.visiolab.clickgui.anim.Anim;
import com.visiolab.clickgui.hotkey.HotkeyItem;
import com.visiolab.clickgui.hotkey.HotkeyManager;
import com.visiolab.clickgui.model.Category;
import com.visiolab.clickgui.model.ModuleItem;

import java.util.List;

/**
 * 悬浮快捷键渲染器。
 * 屏幕上显示可拖动的方形圆角按钮，点击切换绑定模块的开关。
 * 风格统一：白灰南方风格 + 主题色渐变高亮。
 */
public class FloatingHotkeyRenderer {

    private final QuadBatch quad;
    private final TextRenderer text;

    // 尺寸常量
    private static final float BTN_SIZE = 44f;
    private static final float BTN_RADIUS = 12f;
    private static final float DRAG_THRESHOLD = 8f; // 移动超过这个距离才算拖动

    // 拖动状态
    private HotkeyItem draggingItem = null;
    private float dragOffsetX = 0f, dragOffsetY = 0f;
    private float touchStartX = 0f, touchStartY = 0f;
    private boolean isDragging = false;
    private long pressStartTime = 0;

    // 模块列表引用（用于查找绑定的模块）
    private List<Category> categories;

    // Hotkeys 模块引用
    private ModuleItem hotkeysModule;

    public FloatingHotkeyRenderer(QuadBatch quad, TextRenderer text) {
        this.quad = quad;
        this.text = text;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public void setHotkeysModule(ModuleItem m) {
        this.hotkeysModule = m;
    }

    /** 渲染所有悬浮快捷键 */
    public void render(float dt, float screenW, float screenH, float uiScale) {
        if (hotkeysModule == null) return;
        float alpha = Math.max(0f, Math.min(1f, hotkeysModule.hudAnim.get()));
        if (alpha <= 0.01f) return;

        HotkeyManager mgr = HotkeyManager.getInstance();
        if (!mgr.isLoaded() || mgr.getHotkeys().isEmpty()) return;

        int sw = (int) screenW;
        int sh = (int) screenH;

        for (HotkeyItem hk : mgr.getHotkeys()) {
            float s = uiScale;
            float size = BTN_SIZE * s;

            // 按压缩放动画
            float targetPress = (hk.pressed || hk.dragging) ? 1f : 0f;
            hk.pressAnim += (targetPress - hk.pressAnim) * Math.min(1f, dt * 12f);
            float scale = 1f - hk.pressAnim * 0.08f; // 按下缩小8%
            float drawSize = size * scale;
            float offsetX = (size - drawSize) / 2f;
            float offsetX2 = (size - drawSize) / 2f;

            float x = hk.x + offsetX;
            float y = hk.y + offsetX2;

            // 查找绑定模块的开关状态
            boolean moduleEnabled = isModuleEnabled(hk.boundModuleName);

            // 拖动时放大阴影
            if (hk.dragging) {
                float shadowAlpha = 0.3f * alpha;
                quad.addRect(x - 4f * s, y + 2f * s, drawSize + 8f * s, drawSize + 4f * s, BTN_RADIUS * s,
                        new float[]{0f, 0f, 0f, shadowAlpha});
            }

            if (moduleEnabled) {
                // 开启状态：绿色背景（统一风格）
                quad.addRect(x, y, drawSize, drawSize, BTN_RADIUS * s,
                        new float[]{0.30f, 0.56f, 0.33f, 0.92f * alpha});
            } else {
                // 关闭状态：白灰背景（统一风格）
                quad.addRect(x, y, drawSize, drawSize, BTN_RADIUS * s,
                        new float[]{0.92f, 0.92f, 0.92f, 0.88f * alpha});
            }

            // 文字
            float fontSize = 11f * s;
            // 缩短文字如果太长
            String displayText = hk.displayText;
            if (displayText.length() > 6) displayText = displayText.substring(0, 5) + "..";
            float textW = text.measure(displayText, fontSize);
            float textX = x + (drawSize - textW) / 2f;
            float textY = y + (drawSize - fontSize) / 2f + 1f * s;

            float[] textColor = moduleEnabled
                    ? new float[]{1f, 1f, 1f, alpha}
                    : new float[]{0.2f, 0.2f, 0.2f, alpha};
            text.draw(displayText, textX, textY, fontSize, textColor, sw, sh);
        }

        quad.flush(sw, sh);
    }

    /** 查找模块是否启用 */
    private boolean isModuleEnabled(String moduleName) {
        if (categories == null || moduleName == null) return false;
        for (Category c : categories) {
            for (ModuleItem m : c.modules) {
                if (m.name.equals(moduleName)) return m.enabled;
            }
        }
        return false;
    }

    /** 查找模块对象 */
    public ModuleItem findModule(String moduleName) {
        if (categories == null || moduleName == null) return null;
        for (Category c : categories) {
            for (ModuleItem m : c.modules) {
                if (m.name.equals(moduleName)) return m;
            }
        }
        return null;
    }

    /**
     * 处理触摸事件。
     * @return true 如果事件被快捷键消费（阻止后续 UI 处理）
     */
    public boolean onTouchEvent(MotionEvent event, float uiScale, int screenW, int screenH) {
        if (hotkeysModule == null || !hotkeysModule.enabled) return false;

        HotkeyManager mgr = HotkeyManager.getInstance();
        if (!mgr.isLoaded() || mgr.getHotkeys().isEmpty()) return false;

        float ex = event.getX();
        float ey = event.getY();
        int action = event.getActionMasked();
        float s = uiScale;
        float size = BTN_SIZE * s;

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                // 从后往前检查（上层优先）
                for (int i = mgr.getHotkeys().size() - 1; i >= 0; i--) {
                    HotkeyItem hk = mgr.getHotkeys().get(i);
                    if (ex >= hk.x && ex <= hk.x + size && ey >= hk.y && ey <= hk.y + size) {
                        draggingItem = hk;
                        hk.pressed = true;
                        touchStartX = ex;
                        touchStartY = ey;
                        // 记录手指相对按钮的偏移
                        dragOffsetX = ex - hk.x;
                        dragOffsetY = ey - hk.y;
                        pressStartTime = System.currentTimeMillis();
                        isDragging = false;
                        return true;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (draggingItem != null) {
                    float dx = ex - touchStartX;
                    float dy = ey - touchStartY;
                    if (!isDragging && Math.sqrt(dx * dx + dy * dy) > DRAG_THRESHOLD) {
                        isDragging = true;
                        draggingItem.dragging = true;
                    }
                    if (isDragging) {
                        // 直接设置位置 = 手指位置 - 偏移
                        float newX = ex - dragOffsetX;
                        float newY = ey - dragOffsetY;
                        // 限制在屏幕范围内
                        newX = Math.max(0, Math.min(newX, screenW - size));
                        newY = Math.max(0, Math.min(newY, screenH - size));
                        draggingItem.x = newX;
                        draggingItem.y = newY;
                    }
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (draggingItem != null) {
                    if (isDragging) {
                        // 拖动结束，保存位置
                        mgr.save();
                        draggingItem.dragging = false;
                    } else {
                        // 点击：切换模块开关
                        ModuleItem m = findModule(draggingItem.boundModuleName);
                        if (m != null) {
                            m.enabled = !m.enabled;
                        }
                    }
                    draggingItem.pressed = false;
                    draggingItem = null;
                    isDragging = false;
                    return true;
                }
                break;
            }
        }
        return false;
    }

    /** 重新计算拖动偏移 */
    private void updateDragOffset(float touchX, float touchY, HotkeyItem hk) {
        dragOffsetX = touchX - hk.x;
        dragOffsetY = touchY - hk.y;
    }
}
