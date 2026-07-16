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
 * 核心策略：用 Activity.addContentView() 将 GLSurfaceView 添加到 Activity 的 View 层级中，
 * 而不是用 WindowManager（避免权限问题和 z-order 不可控）。
 *
 * 触摸穿透方案：
 * - 面板关闭时：设置 GLSurfaceView setVisibility(VISIBLE) 但用 FLAG_NOT_TOUCHABLE
 *   或通过 setOnTouchListener 不消费事件
 * - 面板打开时：GLSurfaceView 消费所有触摸事件
 *
 * 注意：addContentView 添加的 View 在 DecorView 层级中，
 * GLSurfaceView 的 setZOrderOnTop(true) 确保 GL surface 在最上层合成。
 */
public class OverlayManager {

    private static final String TAG = "OverlayManager";
    private static WeakReference<Activity> activityRef;
    private static OverlayGLView glView;
    private static UIRenderer renderer;
    private static boolean attached = false;
    private static boolean touchPassthrough = true; // 默认穿透

    public static synchronized void attach(Activity activity) {
        if (attached) {
            Log.i(TAG, "Already attached, skipping");
            return;
        }

        Log.i(TAG, "Attaching overlay to " + activity.getClass().getName());
        activityRef = new WeakReference<>(activity);

        // 创建 UIRenderer
        renderer = new UIRenderer(activity);

        // 设置面板状态回调
        renderer.setPanelStateListener(open -> {
            setTouchPassthrough(!open);
        });

        // 创建 GLSurfaceView
        glView = new OverlayGLView(activity, renderer);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        glView.setZOrderOnTop(true); // 确保 GL 在 Minecraft SurfaceView 之上
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glView.setFocusable(false);
        glView.setFocusableInTouchMode(false);

        // 用 FrameLayout 包裹，匹配父容器
        FrameLayout container = new FrameLayout(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        glView.setLayoutParams(params);
        container.addView(glView);

        // addContentView：将容器添加到 Activity 的 DecorView 中
        // 这是应用内操作，不需要 SYSTEM_ALERT_WINDOW 权限
        ViewGroup.LayoutParams contentParams = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        activity.addContentView(container, contentParams);

        attached = true;
        Log.i(TAG, "Overlay attached via addContentView");
    }

    public static synchronized void detach() {
        if (glView != null) {
            glView.onPause();
        }
        glView = null;
        renderer = null;
        attached = false;
        Log.i(TAG, "Overlay detached");
    }

    public static boolean isAttached() {
        return attached;
    }

    /**
     * 设置触摸穿透。
     * @param passthrough true = 触摸穿透到游戏；false = 捕获触摸（面板打开时）
     */
    static void setTouchPassthrough(boolean passthrough) {
        if (touchPassthrough == passthrough) return;
        touchPassthrough = passthrough;
        Log.d(TAG, "Touch " + (passthrough ? "passthrough" : "captured"));

        if (glView != null) {
            // 通知 GLView 更新触摸处理方式
            glView.updateTouchMode(passthrough);
        }
    }

    /**
     * 自定义 GLSurfaceView：
     * - 穿透模式：onTouchEvent 返回 false（不消费事件，事件传递给下层的 Minecraft View）
     * - 捕获模式：onTouchEvent 返回 true（消费事件，转发给 UIRenderer）
     */
    private static class OverlayGLView extends GLSurfaceView {

        private final UIRenderer renderer;
        private boolean passthrough = true;

        public OverlayGLView(Context context, UIRenderer renderer) {
            super(context);
            this.renderer = renderer;
            setRenderer(renderer);
        }

        void updateTouchMode(boolean passthrough) {
            this.passthrough = passthrough;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (passthrough) {
                // 穿透模式：不消费事件，让事件传到 Minecraft
                // 但仍转发一份给 UIRenderer 处理悬浮球点击
                final MotionEvent copy = MotionEvent.obtain(event);
                queueEvent(() -> {
                    renderer.onTouch(copy);
                    copy.recycle();
                });
                return false; // 不消费，事件继续传递
            } else {
                // 捕获模式：消费所有事件
                final MotionEvent copy = MotionEvent.obtain(event);
                queueEvent(() -> {
                    renderer.onTouch(copy);
                    copy.recycle();
                });
                return true; // 消费事件
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            // 捕获模式下拦截所有触摸事件
            return !passthrough;
        }
    }
}
