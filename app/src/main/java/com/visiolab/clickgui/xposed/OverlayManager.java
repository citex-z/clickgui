package com.visiolab.clickgui.xposed;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.visiolab.clickgui.gl.UIRenderer;

import java.lang.ref.WeakReference;

/**
 * 管理注入到 Minecraft 中的 GL 覆盖层。
 *
 * 方案：WindowManager 悬浮窗 + FLAG_NOT_TOUCHABLE
 *
 * 触摸穿透原理：
 * - FLAG_NOT_TOUCHABLE：系统直接跳过本窗口，触摸事件发给下面的 Minecraft
 * - 移除 FLAG_NOT_TOUCHABLE：本窗口捕获触摸事件
 *
 * 这比 addContentView + return false 可靠得多：
 * - return false 只在 View 层级内传递，不会传给兄弟 View（Minecraft SurfaceView）
 * - FLAG_NOT_TOUCHABLE 是系统级路由，直接跳过本窗口
 *
 * 窗口类型选择：
 * - TYPE_APPLICATION：需要 Activity 的 window token，应用内窗口，不需要权限
 * - 通过 activity.getWindow().getAttributes().token 获取 token
 */
public class OverlayManager {

    private static final String TAG = "OverlayManager";
    private static WeakReference<Activity> activityRef;
    private static OverlayGLView glView;
    private static WindowManager windowManager;
    private static WindowManager.LayoutParams layoutParams;
    private static UIRenderer renderer;
    private static boolean attached = false;
    private static FrameLayout container;

    public static synchronized void attach(Activity activity) {
        if (attached) {
            Log.i(TAG, "Already attached, skipping");
            return;
        }

        Log.i(TAG, "Attaching overlay to " + activity.getClass().getName());
        activityRef = new WeakReference<>(activity);

        // 初始化模块资源加载器
        ModuleResourceLoader.init(activity);
        Log.i(TAG, "Module resources: " + (ModuleResourceLoader.isInitialized() ? "OK" : "FAILED"));

        // 创建 UIRenderer
        renderer = new UIRenderer(activity);
        renderer.setPanelStateListener(open -> setTouchPassthrough(!open));

        // 创建 GLSurfaceView（EGL 配置必须在 setRenderer 之前）
        glView = new OverlayGLView(activity, renderer);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        glView.setZOrderOnTop(true);
        glView.setFocusable(false);
        glView.setFocusableInTouchMode(false);
        glView.setRenderer(renderer); // 必须在 EGL 配置之后
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        // 使用 TYPE_APPLICATION（应用内窗口，需要 Activity token）
        layoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE // 默认穿透
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        );

        // 设置 token（TYPE_APPLICATION 需要 Activity 的 window token）
        try {
            layoutParams.token = activity.getWindow().getAttributes().token;
        } catch (Throwable e) {
            Log.w(TAG, "Failed to get window token, trying without", e);
        }

        try {
            windowManager.addView(glView, layoutParams);
            attached = true;
            Log.i(TAG, "Overlay attached via WindowManager (TYPE_APPLICATION)");
        } catch (Throwable e) {
            Log.e(TAG, "TYPE_APPLICATION failed: " + e.getMessage());
            // 尝试 TYPE_APPLICATION_PANEL
            try {
                layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                layoutParams.token = activity.getWindow().getDecorView().getWindowToken();
                windowManager.addView(glView, layoutParams);
                attached = true;
                Log.i(TAG, "Overlay attached via TYPE_APPLICATION_PANEL");
            } catch (Throwable e2) {
                Log.e(TAG, "All window types failed", e2);
                // 最后兜底：用 addContentView
                tryFallbackAddContentView(activity);
            }
        }
    }

    /** 兜底方案：用 addContentView */
    private static void tryFallbackAddContentView(Activity activity) {
        try {
            Log.w(TAG, "Falling back to addContentView");
            container = new FrameLayout(activity);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            glView.setLayoutParams(params);
            container.addView(glView);
            activity.addContentView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            attached = true;
            Log.i(TAG, "Overlay attached via addContentView (fallback)");
        } catch (Throwable e) {
            Log.e(TAG, "addContentView also failed", e);
        }
    }

    public static synchronized void detach() {
        if (glView != null) {
            glView.onPause();
            if (windowManager != null && attached) {
                try { windowManager.removeView(glView); } catch (Throwable ignored) {}
            }
        }
        glView = null;
        renderer = null;
        windowManager = null;
        layoutParams = null;
        container = null;
        attached = false;
        Log.i(TAG, "Overlay detached");
    }

    public static boolean isAttached() { return attached; }

    /**
     * 切换触摸穿透/捕获。
     * @param passthrough true = 穿透到游戏；false = 捕获（面板打开时）
     */
    static void setTouchPassthrough(boolean passthrough) {
        if (layoutParams == null || windowManager == null || glView == null) return;

        boolean currentlyPassthrough = (layoutParams.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0;
        if (currentlyPassthrough == passthrough) return;

        if (passthrough) {
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            layoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        try {
            windowManager.updateViewLayout(glView, layoutParams);
            Log.d(TAG, "Touch " + (passthrough ? "passthrough → game" : "captured → UI"));
        } catch (Throwable e) {
            Log.w(TAG, "updateViewLayout failed", e);
        }
    }

    /**
     * GLSurfaceView：在 GL 线程转发触摸事件。
     * 只有当 FLAG_NOT_TOUCHABLE 被移除时（面板打开），onTouchEvent 才会被调用。
     */
    private static class OverlayGLView extends GLSurfaceView {
        private final UIRenderer renderer;

        public OverlayGLView(Context context, UIRenderer renderer) {
            super(context);
            this.renderer = renderer;
            // setRenderer 由外部调用
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final MotionEvent copy = MotionEvent.obtain(event);
            queueEvent(() -> {
                renderer.onTouch(copy);
                copy.recycle();
            });
            return true; // WindowManager 模式下总是消费事件
        }
    }
}
