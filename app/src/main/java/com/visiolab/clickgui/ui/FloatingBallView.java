package com.visiolab.clickgui.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

/**
 * 悬浮球 — WindowManager + Canvas 自绘。
 *
 * 使用 WindowManager.addView 而非 PopupWindow，
 * 确保在 GL SurfaceView 之上（添加顺序决定 z-order）。
 *
 * 功能：
 * - 单击：打开 ClickGUI 面板
 * - 长按拖拽：移动位置
 * - 渐变色圆形 + 阴影
 */
public class FloatingBallView {

    private static final String TAG = "FloatingBall";
    private final Activity activity;
    private BallView ballView;
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private int ballSize;
    private int posX, posY;
    private OnBallClickListener clickListener;
    private boolean shown = false;

    // 阻尼拖拽
    private float downX, downY;
    private boolean moved = false;
    private static final float DAMPING = 0.2f;

    // 主题色
    private static final int COLOR_START = Color.parseColor("#1A73E8");
    private static final int COLOR_END = Color.parseColor("#00BCD4");

    public interface OnBallClickListener { void onBallClick(); }

    public FloatingBallView(Activity activity) {
        this.activity = activity;
        this.wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        this.ballSize = dp(activity, 48);
        this.posX = dp(activity, 16);
        this.posY = dp(activity, 100);

        ballView = new BallView(activity);
        params = new WindowManager.LayoutParams(
            ballSize, ballSize,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = posX;
        params.y = posY;
        try {
            params.token = activity.getWindow().getAttributes().token;
        } catch (Throwable ignored) {}
    }

    public void setOnBallClickListener(OnBallClickListener listener) {
        this.clickListener = listener;
    }

    public void show() {
        if (shown) return;
        activity.runOnUiThread(() -> {
            try {
                // 尝试 TYPE_APPLICATION
                try {
                    wm.addView(ballView, params);
                } catch (Throwable e) {
                    Log.w(TAG, "TYPE_APPLICATION failed, trying PANEL", e);
                    params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                    params.token = activity.getWindow().getDecorView().getWindowToken();
                    wm.addView(ballView, params);
                }
                shown = true;
                Log.i(TAG, "Ball shown at (" + posX + "," + posY + ")");
            } catch (Throwable e) {
                Log.e(TAG, "Ball show failed", e);
            }
        });
    }

    public void hide() {
        if (!shown) return;
        try { wm.removeView(ballView); } catch (Throwable ignored) {}
        shown = false;
    }

    public boolean isShowing() { return shown; }

    private void updatePosition() {
        if (!shown) return;
        params.x = posX;
        params.y = posY;
        try { wm.updateViewLayout(ballView, params); } catch (Throwable ignored) {}
    }

    /** 自绘悬浮球 */
    private class BallView extends View {
        private final Paint dotPaint;
        private float pulseScale = 1f;

        public BallView(Context context) {
            super(context);
            GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{COLOR_START, COLOR_END}
            );
            bg.setShape(GradientDrawable.OVAL);
            bg.setCornerRadius(ballSize);
            setBackground(bg);
            setLayerType(LAYER_TYPE_SOFTWARE, null);

            dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setColor(Color.WHITE);
            dotPaint.setAlpha(180);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float dotR = dp(getContext(), 4) * pulseScale;
            canvas.drawCircle(cx, cy - dp(getContext(), 6), dotR, dotPaint);
            canvas.drawCircle(cx - dp(getContext(), 7), cy + dp(getContext(), 4), dotR, dotPaint);
            canvas.drawCircle(cx + dp(getContext(), 7), cy + dp(getContext(), 4), dotR, dotPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    moved = false;
                    pulseScale = 0.8f;
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = (event.getRawX() - downX) * DAMPING;
                    float dy = (event.getRawY() - downY) * DAMPING;
                    if (Math.abs(event.getRawX() - downX) > dp(getContext(), 8) ||
                        Math.abs(event.getRawY() - downY) > dp(getContext(), 8)) {
                        moved = true;
                        int screenW = getResources().getDisplayMetrics().widthPixels;
                        int screenH = getResources().getDisplayMetrics().heightPixels;
                        posX = Math.max(0, Math.min(posX + (int) dx, screenW - ballSize));
                        posY = Math.max(0, Math.min(posY + (int) dy, screenH - ballSize));
                        updatePosition();
                        downX = event.getRawX();
                        downY = event.getRawY();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pulseScale = 1f;
                    invalidate();
                    if (!moved && clickListener != null) {
                        clickListener.onBallClick();
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
