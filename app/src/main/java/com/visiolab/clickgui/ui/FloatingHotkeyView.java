package com.visiolab.clickgui.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.visiolab.clickgui.model.ModuleItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 快捷键管理器 — 每个快捷键一个 WindowManager 小窗口。
 *
 * 在 GL HUD 层之后添加，z-order 在上，可接收触摸。
 * 每个窗口只覆盖快捷键自身的区域，不遮挡游戏其他区域。
 */
public class FloatingHotkeyView {

    private static final String TAG = "FloatingHotkey";
    private final Activity activity;
    private final WindowManager wm;
    private final Map<String, HotkeyWindow> hotkeys = new HashMap<>();
    private Typeface font;
    private OnHotkeyToggleListener toggleListener;

    // 主题色
    private static final int COLOR_ON = Color.parseColor("#1A73E8");
    private static final int COLOR_OFF = Color.parseColor("#2A292E");

    public interface OnHotkeyToggleListener {
        void onToggle(String moduleName, boolean enabled);
    }

    public FloatingHotkeyView(Activity activity) {
        this.activity = activity;
        this.wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
    }

    public void setFont(Typeface font) { this.font = font; }
    public void setOnHotkeyToggleListener(OnHotkeyToggleListener listener) { this.toggleListener = listener; }

    /** 设置/更新快捷键列表 */
    public void updateHotkeys(List<ModuleItem> modules, int screenW, int screenH) {
        // 简单策略：在屏幕右下角排列快捷键
        int btnW = dp(80);
        int btnH = dp(36);
        int gap = dp(8);
        int startX = screenW - btnW - dp(16);
        int startY = screenH - dp(100);

        int row = 0;
        for (ModuleItem module : modules) {
            if (!module.showToggle) continue;
            String key = module.name;
            HotkeyWindow hw = hotkeys.get(key);
            if (hw == null) {
                hw = new HotkeyWindow(activity, module, font);
                hw.setToggleListener((name, enabled) -> {
                    if (toggleListener != null) toggleListener.onToggle(name, enabled);
                });
                hw.show(startX, startY - row * (btnH + gap), btnW, btnH);
                hotkeys.put(key, hw);
                Log.d(TAG, "Added hotkey: " + key);
            } else {
                hw.updateState(module.enabled);
            }
            row++;
        }
    }

    /** 显示所有快捷键 */
    public void showAll() {
        for (HotkeyWindow hw : hotkeys.values()) hw.show();
    }

    /** 隐藏所有快捷键 */
    public void hideAll() {
        for (HotkeyWindow hw : hotkeys.values()) hw.hide();
    }

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
        private Typeface font;
        private OnHotkeyToggleListener toggleListener;

        HotkeyWindow(Activity activity, ModuleItem module, Typeface font) {
            this.activity = activity;
            this.module = module;
            this.font = font;
            this.view = new HotkeyBtnView(activity, module.name, module.enabled, font);
        }

        void setToggleListener(OnHotkeyToggleListener listener) { this.toggleListener = listener; }

        void show(int x, int y, int w, int h) {
            if (shown) return;
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

        void remove() {
            hide();
        }

        void updateState(boolean enabled) {
            view.setEnabled(enabled);
            view.postInvalidate();
        }
    }

    /** 快捷键按钮 View — Canvas 自绘 */
    private class HotkeyBtnView extends View {
        private final String text;
        private boolean enabled;
        private final Paint textPaint;
        private final Paint bgPaint;
        private final Typeface font;
        private float pressScale = 1f;

        HotkeyBtnView(Context context, String text, boolean enabled, Typeface font) {
            super(context);
            this.text = text;
            this.enabled = enabled;
            this.font = font;
            setLayerType(LAYER_TYPE_SOFTWARE, null);

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(13));
            if (font != null) textPaint.setTypeface(font);

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float r = dp(8);

            // 背景
            bgPaint.setColor(enabled ? COLOR_ON : COLOR_OFF);
            bgPaint.setAlpha(enabled ? 200 : 180);
            canvas.drawRoundRect(0, 0, w, h, r, r, bgPaint);

            // 文字颜色：开启=白色，关闭=灰色
            textPaint.setColor(enabled ? Color.WHITE : Color.parseColor("#888888"));
            canvas.drawText(text, w / 2f, h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pressScale = 0.9f;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    pressScale = 1f;
                    enabled = !enabled;
                    invalidate();
                    if (toggleListener != null) {
                        toggleListener.onToggle(text, enabled);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    pressScale = 1f;
                    invalidate();
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
