package com.visiolab.clickgui.xposed;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.visiolab.clickgui.gl.HudRenderer;
import com.visiolab.clickgui.ui.FloatingBallView;
import com.visiolab.clickgui.ui.FloatingHotkeyView;
import com.visiolab.clickgui.ui.ClickGuiPanel;

import java.lang.ref.WeakReference;

/**
 * 混合架构 Overlay 管理器。
 *
 * 三层独立窗口，互不干扰：
 * 1. HUD 层：GLSurfaceView，永远 FLAG_NOT_TOUCHABLE（只画不拦截触摸）
 * 2. 悬浮球：小 PopupWindow + Canvas 自绘（点击/拖拽）
 * 3. ClickGUI 面板：全屏 PopupWindow + 原生 View（仅打开时存在）
 *
 * 触摸穿透不再需要动态切换 FLAG：
 * - HUD 层永远穿透
 * - 悬浮球只拦截自身区域
 * - 面板打开时全屏捕获，关闭就 dismiss
 */
public class OverlayManager {

    private static final String TAG = "OverlayManager";
    private static WeakReference<Activity> activityRef;
    private static GLSurfaceView hudGlView;
    private static HudRenderer hudRenderer;
    private static FloatingBallView floatingBall;
    private static FloatingHotkeyView hotkeyView;
    private static ClickGuiPanel guiPanel;
    private static WindowManager windowManager;
    private static boolean attached = false;

    /** 模块状态回调：面板中切换模块时通知 HUD 更新 */
    public interface OnModuleChangedListener {
        void onModuleChanged(String name, boolean enabled);
    }

    public static synchronized void attach(Activity activity) {
        if (attached) {
            Log.i(TAG, "Already attached, skipping");
            return;
        }

        Log.i(TAG, "Attaching overlay (hybrid mode) to " + activity.getClass().getName());
        activityRef = new WeakReference<>(activity);
        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        // 初始化模块资源
        ModuleResourceLoader.init(activity);

        // ---- 第1层：HUD GL (永远穿透) ----
        hudRenderer = new HudRenderer(activity);
        hudGlView = new GLSurfaceView(activity);
        hudGlView.setEGLContextClientVersion(2);
        hudGlView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        hudGlView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        hudGlView.setZOrderOnTop(true);
        hudGlView.setFocusable(false);
        hudGlView.setFocusableInTouchMode(false);
        hudGlView.setRenderer(hudRenderer);
        hudGlView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        WindowManager.LayoutParams hudParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE // 永远穿透！
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        );
        try {
            hudParams.token = activity.getWindow().getAttributes().token;
        } catch (Throwable ignored) {}

        try {
            windowManager.addView(hudGlView, hudParams);
            Log.i(TAG, "HUD GL layer attached (always passthrough)");
        } catch (Throwable e) {
            Log.e(TAG, "HUD GL attach failed", e);
            try {
                hudParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                hudParams.token = activity.getWindow().getDecorView().getWindowToken();
                windowManager.addView(hudGlView, hudParams);
                Log.i(TAG, "HUD GL attached via TYPE_APPLICATION_PANEL");
            } catch (Throwable e2) {
                Log.e(TAG, "HUD GL all attempts failed", e2);
            }
        }

        // ---- 第2层：悬浮球 ----
        floatingBall = new FloatingBallView(activity);
        floatingBall.setOnBallClickListener(() -> {
            Log.i(TAG, "Ball clicked → opening ClickGUI panel");
            openGuiPanel(activity);
        });
        floatingBall.show();
        Log.i(TAG, "Floating ball attached");

        // ---- 第3层：快捷键（默认不显示，由 ClickGUI 面板中拖拽添加） ----
        hotkeyView = new FloatingHotkeyView(activity);
        hotkeyView.setOnHotkeyToggleListener((name, enabled) -> {
            Log.i(TAG, "Hotkey toggled: " + name + " → " + enabled);
            if (hudRenderer != null) {
                hudRenderer.onModuleChanged(name, enabled);
            }
        });
        // 不默认添加任何快捷键 — 用户在 ClickGUI 面板中拖拽模块到快捷键区域才会显示
        Log.i(TAG, "Floating hotkey manager attached (empty by default)");

        attached = true;
        Log.i(TAG, "=== Overlay fully attached (hybrid mode) ===");
    }

    /** 打开 ClickGUI 面板 */
    private static void openGuiPanel(Activity activity) {
        if (guiPanel != null && guiPanel.isShowing()) return;

        // 隐藏悬浮球和快捷键
        floatingBall.hide();
        if (hotkeyView != null) hotkeyView.hideAll();

        guiPanel = new ClickGuiPanel(activity);
        guiPanel.setOnCloseListener(() -> {
            Log.i(TAG, "GUI panel closed → showing floating ball");
            guiPanel = null;
            floatingBall.show();
            if (hotkeyView != null) hotkeyView.showAll();
        });
        guiPanel.setOnModuleChangedListener((name, enabled) -> {
            // 通知 HUD 层更新
            if (hudRenderer != null) {
                hudRenderer.onModuleChanged(name, enabled);
            }
        });
        guiPanel.show();
    }

    public static synchronized void detach() {
        if (guiPanel != null) { guiPanel.dismiss(); guiPanel = null; }
        if (hotkeyView != null) { hotkeyView.removeAll(); hotkeyView = null; }
        if (floatingBall != null) { floatingBall.hide(); floatingBall = null; }
        if (hudGlView != null) {
            hudGlView.onPause();
            if (windowManager != null) {
                try { windowManager.removeView(hudGlView); } catch (Throwable ignored) {}
            }
        }
        hudGlView = null;
        hudRenderer = null;
        windowManager = null;
        attached = false;
        Log.i(TAG, "Overlay detached");
    }

    public static boolean isAttached() { return attached; }
    public static Activity getActivity() { return activityRef != null ? activityRef.get() : null; }
}
