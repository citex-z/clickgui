package com.visiolab.clickgui.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.visiolab.clickgui.model.ModuleItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 快捷键管理器 — 每个快捷键一个 WindowManager 小窗口。
 *
 * 特性：
 * - 长按拖拽放置位置，短按切换开关
 * - 初始位置在屏幕中间
 * - 小尺寸（不遮挡游戏）
 * - 缩放反馈动画
 */
public class FloatingHotkeyView {

    private static final String TAG = "FloatingHotkey";
    private final Activity activity;
    private final WindowManager wm;
    private final Map<String, HotkeyWindow> hotkeys = new HashMap<>();
    private Typeface font;
    private OnHotkeyToggleListener toggleListener;

    // 主题色（与 ClickGuiPanel 一致）
    private static final int COLOR_ON = Color.parseColor("#4A90D9");
    private static final int COLOR_OFF = Color.parseColor("#D5D5D5");

    // 快捷键尺寸（小尺寸）
    private static final int BTN_W = 56;
    private static final int BTN_H = 28;

    // 长按判定阈值
    private static final int LONG_PRESS_THRESHOLD = 300; // ms
    private static final int DRAG_THRESHOLD = 10; // px

    public interface OnHotkeyToggleListener {
        void onToggle(String moduleName, boolean enabled);
    }

    public FloatingHotkeyView(Activity activity) {
        this.activity = activity;
        this.wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setFont(Typeface font) { this.font = font; }
    public void setOnHotkeyToggleListener(OnHotkeyToggleListener listener) { this.toggleListener = listener; }

    /** 添加单个快捷键 — 初始位置在屏幕中间偏右 */
    public void addHotkey(ModuleItem module, int screenW, int screenH) {
        if (hotkeys.containsKey(module.name)) return;
        int btnW = dp(BTN_W);
        int btnH = dp(BTN_H);

        // 初始位置：屏幕中间，多个快捷键纵向排列
        int count = hotkeys.size();
        int x = screenW / 2 - btnW / 2;
        int y = screenH / 2 - btnH / 2 + count * (btnH + dp(4));

        HotkeyWindow hw = new HotkeyWindow(activity, module, font);
        hw.setToggleListener((name, enabled) -> {
            if (toggleListener != null) toggleListener.onToggle(name, enabled);
        });
        hw.show(x, y, btnW, btnH);
        hotkeys.put(module.name, hw);
        Log.d(TAG, "Added hotkey: " + module.name + " at center (" + x + "," + y + ")");
    }

    /** 移除单个快捷键 */
    public void removeHotkey(String name) {
        HotkeyWindow hw = hotkeys.remove(name);
        if (hw != null) hw.remove();
        Log.d(TAG, "Removed hotkey: " + name);
    }

    /** 检查快捷键是否已存在 */
    public boolean hasHotkey(String name) { return hotkeys.containsKey(name); }

    /** 显示所有快捷键 */
    public void showAll() { for (HotkeyWindow hw : hotkeys.values()) hw.show(); }

    /** 隐藏所有快捷键 */
    public void hideAll() { for (HotkeyWindow hw : hotkeys.values()) hw.hide(); }

    /** 移除所有快捷键 */
    public void removeAll() {
        for (HotkeyWindow hw : hotkeys.values()) hw.remove();
        hotkeys.clear();
    }

    /** 单个快捷键窗口 */
    private class HotkeyWindow {
        private final Activity activity;
        private final ModuleItem module;
        private final HotkeyBtnView view;
        private WindowManager.LayoutParams params;
        private boolean shown = false;
        private int curX, curY, btnW, btnH;

        HotkeyWindow(Activity activity, ModuleItem module, Typeface font) {
            this.activity = activity;
            this.module = module;
            this.view = new HotkeyBtnView(activity, module.name, module.enabled, font);
            this.view.setOnDragListener((newX, newY) -> {
                curX = newX;
                curY = newY;
                if (shown && params != null) {
                    params.x = newX;
                    params.y = newY;
                    try { wm.updateViewLayout(view, params); } catch (Throwable ignored) {}
                }
            });
            this.view.setOnToggleListener((enabled) -> {
                module.enabled = enabled;
                if (FloatingHotkeyView.this.toggleListener != null) {
                    FloatingHotkeyView.this.toggleListener.onToggle(module.name, enabled);
                }
            });
        }

        void setToggleListener(OnHotkeyToggleListener listener) {
            // 保留兼容性，但实际 toggle 通过 view.setOnToggleListener 处理
        }

        void show(int x, int y, int w, int h) {
            if (shown) return;
            curX = x; curY = y; btnW = w; btnH = h;
            params = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = x;
            params.y = y;
            try { params.token = activity.getWindow().getAttributes().token; } catch (Throwable ignored) {}

            try {
                wm.addView(view, params);
                shown = true;
            } catch (Throwable e) {
                try {
                    params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                    params.token = activity.getWindow().getDecorView().getWindowToken();
                    wm.addView(view, params);
                    shown = true;
                } catch (Throwable e2) {
                    Log.e(TAG, "Hotkey show failed: " + module.name, e2);
                }
            }
        }

        void show() {
            if (shown || params == null) return;
            try { wm.addView(view, params); shown = true; } catch (Throwable ignored) {}
        }

        void hide() {
            if (!shown) return;
            try { wm.removeView(view); } catch (Throwable ignored) {}
            shown = false;
        }

        void remove() { hide(); }
        void updateState(boolean enabled) { view.setModuleEnabled(enabled); }
    }

    /**
     * 快捷键按钮 View — Canvas 自绘 + 长按拖拽 + 缩放反馈
     */
    private class HotkeyBtnView extends View {
        private final String text;
        private boolean moduleEnabled;
        private final Paint textPaint;
        private final Paint bgPaint;
        private final Typeface font;
        private float scale = 1f;
        private float targetScale = 1f;

        // 拖拽
        private OnDragListener dragListener;
        private OnToggleListener toggleListener;
        private float downX, downY;
        private long downTime;
        private boolean isDragging = false;
        private boolean isLongPress = false;
        private int offsetX, offsetY;

        interface OnDragListener { void onDrag(int x, int y); }
        interface OnToggleListener { void onToggle(boolean enabled); }

        HotkeyBtnView(Context context, String text, boolean enabled, Typeface font) {
            super(context);
            this.text = text;
            this.moduleEnabled = enabled;
            this.font = font;
            setLayerType(LAYER_TYPE_SOFTWARE, null);

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(10));
            if (font != null) textPaint.setTypeface(font);

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        void setOnDragListener(OnDragListener listener) { this.dragListener = listener; }
        void setOnToggleListener(OnToggleListener listener) { this.toggleListener = listener; }

        void setModuleEnabled(boolean enabled) {
            this.moduleEnabled = enabled;
            postInvalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            // 缩放动画平滑
            scale += (targetScale - scale) * 0.2f;
            if (Math.abs(scale - targetScale) > 0.01f) postInvalidate();

            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;

            canvas.save();
            canvas.scale(scale, scale, cx, cy);

            float r = dp(6);

            // 背景
            bgPaint.setColor(moduleEnabled ? COLOR_ON : COLOR_OFF);
            bgPaint.setAlpha(moduleEnabled ? 210 : 200);
            canvas.drawRoundRect(0, 0, w, h, r, r, bgPaint);

            // 文字
            textPaint.setColor(moduleEnabled ? Color.WHITE : Color.parseColor("#888888"));
            canvas.drawText(text, cx, cy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);

            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    downTime = System.currentTimeMillis();
                    isDragging = false;
                    isLongPress = false;
                    targetScale = 0.92f; // 按下缩小反馈
                    postInvalidate();
                    // 记录触摸点相对于窗口的偏移
                    int[] location = new int[2];
                    getLocationOnScreen(location);
                    offsetX = (int) (downX - location[0]);
                    offsetY = (int) (downY - location[1]);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float mx = event.getRawX();
                    float my = event.getRawY();
                    long elapsed = System.currentTimeMillis() - downTime;

                    if (!isDragging && elapsed > LONG_PRESS_THRESHOLD
                            && (Math.abs(mx - downX) > DRAG_THRESHOLD || Math.abs(my - downY) > DRAG_THRESHOLD)) {
                        isDragging = true;
                        isLongPress = true;
                        targetScale = 1.1f; // 长按拖拽放大反馈
                        postInvalidate();
                    }

                    if (isDragging && dragListener != null) {
                        int newX = (int) (mx - offsetX);
                        int newY = (int) (my - offsetY);
                        dragListener.onDrag(newX, newY);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    targetScale = 1f;
                    postInvalidate();

                    if (!isLongPress) {
                        // 短按 = 切换开关
                        moduleEnabled = !moduleEnabled;
                        postInvalidate();
                        if (toggleListener != null) {
                            toggleListener.onToggle(moduleEnabled);
                        }
                    }
                    isDragging = false;
                    isLongPress = false;
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(int v) {
        return v * activity.getResources().getDisplayMetrics().scaledDensity;
    }
}
