package com.visiolab.clickgui.xposed;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.visiolab.clickgui.gl.UIRenderer;

import java.lang.ref.WeakReference;

/**
 * 管理注入到 Minecraft 中的悬浮窗 GL 覆盖层。
 *
 * 工作原理：
 * 1. 在 Minecraft Activity 上创建一个 WindowManager 悬浮窗
 * 2. 悬浮窗内是 GLSurfaceView，渲染我们的 ClickGUI
 * 3. 面板关闭时：FLAG_NOT_TOUCHABLE = 触摸穿透到游戏
 * 4. 面板打开时：移除 FLAG_NOT_TOUCHABLE = 捕获触摸事件
 */
public class OverlayManager {

    private static final String TAG = "OverlayManager";
    private static WeakReference<Activity> activityRef;
    private static OverlayGLView glView;
    private static WindowManager windowManager;
    private static WindowManager.LayoutParams layoutParams;
    private static UIRenderer renderer;
    private static boolean attached = false;

    public static synchronized void attach(Activity activity) {
        if (attached) {
            Log.i(TAG, "Already attached, skipping");
            return;
        }

        Log.i(TAG, "Attaching overlay to " + activity.getClass().getName());
        activityRef = new WeakReference<>(activity);
        windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);

        // 创建 UIRenderer
        renderer = new UIRenderer(activity);

        // 设置面板状态回调：面板开关时切换触摸穿透
        renderer.setPanelStateListener(open -> {
            setTouchCapture(open);
        });

        // 创建 GLSurfaceView
        glView = new OverlayGLView(activity, renderer);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        glView.setZOrderOnTop(true);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // WindowManager LayoutParams
        layoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );

        try {
            windowManager.addView(glView, layoutParams);
            attached = true;
            Log.i(TAG, "Overlay attached successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay view, trying alternative type", e);
            try {
                layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                windowManager.addView(glView, layoutParams);
                attached = true;
                Log.i(TAG, "Overlay attached with TYPE_APPLICATION_PANEL");
            } catch (Exception e2) {
                Log.e(TAG, "All attach attempts failed", e2);
            }
        }
    }

    public static synchronized void detach() {
        if (glView != null) {
            if (glView != null) glView.onPause();
            if (windowManager != null) {
                try {
                    windowManager.removeView(glView);
                } catch (Exception e) {
                    Log.w(TAG, "Error removing view", e);
                }
            }
        }
        glView = null;
        renderer = null;
        windowManager = null;
        layoutParams = null;
        attached = false;
        Log.i(TAG, "Overlay detached");
    }

    /**
     * 更新触摸穿透状态。
     * @param capture true = 捕获触摸（面板打开）；false = 穿透到游戏（面板关闭）
     */
    static void setTouchCapture(boolean capture) {
        if (glView == null || windowManager == null || layoutParams == null) return;

        boolean currentlyPassThrough = (layoutParams.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0;
        boolean shouldPassThrough = !capture;

        if (currentlyPassThrough != shouldPassThrough) {
            if (shouldPassThrough) {
                layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            } else {
                layoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }
            try {
                windowManager.updateViewLayout(glView, layoutParams);
                Log.d(TAG, "Touch " + (capture ? "captured" : "passthrough"));
            } catch (Exception e) {
                Log.w(TAG, "Error updating view layout", e);
            }
        }
    }

    /**
     * 自定义 GLSurfaceView：转发触摸事件给 UIRenderer。
     */
    private static class OverlayGLView extends GLSurfaceView {

        private final UIRenderer renderer;

        public OverlayGLView(Context context, UIRenderer renderer) {
            super(context);
            this.renderer = renderer;
            setRenderer(renderer);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final MotionEvent copy = MotionEvent.obtain(event);
            queueEvent(() -> {
                renderer.onTouch(copy);
                copy.recycle();
            });
            return true;
        }
    }
}
