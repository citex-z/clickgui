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
    private static final float BTN_HEIGHT = 44f;      // 按钮高度（固定）
    private static final float BTN_WIDTH = 120f;      // 按钮宽度（固定）
    private static final float BTN_PADDING = 16f;      // 文字左右内边距
    private static final float BTN_RADIUS = 10f;      // 圆角
    private static final float DRAG_THRESHOLD = 8f;  // 移动超过这个距离才算拖动

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

    // 模块切换回调
    public interface OnHotkeyToggledListener {
        void onHotkeyToggled(ModuleItem module);
    }
    private OnHotkeyToggledListener toggleListener;

    public void setOnHotkeyToggledListener(OnHotkeyToggledListener listener) {
        this.toggleListener = listener;
    }

    private void onHotkeyToggled(ModuleItem m) {
        if (toggleListener != null) toggleListener.onHotkeyToggled(m);
    }

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
            float fontSize = 13f * s;
            float btnH = BTN_HEIGHT * s;
            float btnW = BTN_WIDTH * s; // 固定宽度
            float btnR = BTN_RADIUS * s;

            // 文字：小写
            String displayText = hk.displayText.toLowerCase();
            float textW = text.measure(displayText, fontSize);

            // 按压+点击缩放动画
            float targetPress = (hk.pressed || hk.dragging) ? 1f : 0f;
            hk.pressAnim += (targetPress - hk.pressAnim) * Math.min(1f, dt * 12f);
            hk.clickScale = Math.min(1f, hk.clickScale + dt * 3.5f);
            float clickShrink = (1f - hk.clickScale) * 0.12f;
            float scale = 1f - hk.pressAnim * 0.06f - clickShrink;
            float drawW = btnW * scale;
            float drawH = btnH * scale;
            float offsetX = (btnW - drawW) / 2f;
            float offsetY = (btnH - drawH) / 2f;

            float x = hk.x + offsetX;
            float y = hk.y + offsetY;

            boolean moduleEnabled = isModuleEnabled(hk.boundModuleName);

            // 拖动时阴影
            if (hk.dragging) {
                quad.addRect(x - 3f * s, y + 2f * s, drawW + 6f * s, drawH + 3f * s, btnR,
                        new float[]{0f, 0f, 0f, 0.3f * alpha});
            }

            // 背景：很浅的半透明黑色圆角矩形
            quad.addRect(x, y, drawW, drawH, btnR,
                    new float[]{0f, 0f, 0f, 0.35f * alpha});

            // 开启状态：主题色边框
            if (moduleEnabled) {
                float bw = 2f * s; // 边框宽度
                // 外圈：主题色
                quad.addRect(x - bw, y - bw, drawW + bw * 2f, drawH + bw * 2f, btnR + bw,
                        new float[]{0.18f, 0.46f, 0.58f, 0.85f * alpha});
                // 内圈：重新画背景
                quad.addRect(x, y, drawW, drawH, btnR,
                        new float[]{0f, 0f, 0f, 0.35f * alpha});
            }

            // 文字居中
            float textX = x + (drawW - textW) / 2f;
            float textY = y + (drawH - fontSize) / 2f;
            // 文字颜色：开启=主题色亮色，关闭=白色
            float[] textColor = moduleEnabled
                    ? new float[]{0.35f, 0.70f, 0.85f, alpha}  // 主题蓝亮色
                    : new float[]{1f, 1f, 1f, alpha};             // 白色
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
        float btnH = BTN_HEIGHT * s;
        float fontSize = 13f * s;

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                for (int i = mgr.getHotkeys().size() - 1; i >= 0; i--) {
                    HotkeyItem hk = mgr.getHotkeys().get(i);
                    float btnW = BTN_WIDTH * s; // 固定宽度
                    if (ex >= hk.x && ex <= hk.x + btnW && ey >= hk.y && ey <= hk.y + btnH) {
                        draggingItem = hk;
                        hk.pressed = true;
                        touchStartX = ex;
                        touchStartY = ey;
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
                        float newX = ex - dragOffsetX;
                        float newY = ey - dragOffsetY;
                        // 限制在屏幕范围内
                        float btnW = BTN_WIDTH * s; // 固定宽度
                        newX = Math.max(0, Math.min(newX, screenW - btnW));
                        newY = Math.max(0, Math.min(newY, screenH - btnH));
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
                        mgr.save();
                        draggingItem.dragging = false;
                    } else {
                        // 点击切换模块
                        draggingItem.clickScale = 0f; // 触发缩放弹回
                        ModuleItem m = findModule(draggingItem.boundModuleName);
                        if (m != null) {
                            m.enabled = !m.enabled;
                            onHotkeyToggled(m);
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
