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
import android.view.WindowManager;

/**
 * 悬浮球 — WindowManager + Canvas 自绘。
 *
 * 1:1 还原原 GL 版本的三层嵌套样式：
 * 1. 外层：纯色大球（深色主题=白色，浅色主题=黑色）
 * 2. 中层：深灰→浅灰对角线渐变球（半径=外层×0.78）
 * 3. 内层：纯色小球（半径=外层×0.40，与外层同色）
 *
 * 无图标，纯几何。
 */
public class FloatingBallView {

    private static final String TAG = "FloatingBall";
    private final Activity activity;
    private BallView ballView;
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private int ballRadius; // 半径 px
    private int ballDiameter;
    private int posX, posY;
    private OnBallClickListener clickListener;
    private boolean shown = false;

    // 拖拽
    private float downX, downY;
    private float ballOffsetX, ballOffsetY;
    private boolean moved = false;
    private static final int DRAG_THRESHOLD = 14; // 移动超过14px才算拖拽

    // 深色主题（默认）
    private boolean isDark = true;

    public interface OnBallClickListener { void onBallClick(); }

    public FloatingBallView(Activity activity) {
        this.activity = activity;
        this.wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        // 原版: ballRadius = 22f * uiScale, uiScale≈1 → 约22dp半径 → 直径44dp
        this.ballRadius = dp(activity, 22);
        this.ballDiameter = ballRadius * 2;

        // 初始位置: 右下角 (原版: width - ballRadius - 24, height - ballRadius - 40)
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        this.posX = screenW - ballRadius - dp(activity, 24);
        this.posY = screenH - ballRadius - dp(activity, 40);

        ballView = new BallView(activity);
        params = new WindowManager.LayoutParams(
            ballDiameter, ballDiameter,
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

    public void setDarkTheme(boolean dark) { this.isDark = dark; }

    public void show() {
        if (shown) return;
        activity.runOnUiThread(() -> {
            try {
                try {
                    wm.addView(ballView, params);
                } catch (Throwable e) {
                    Log.w(TAG, "TYPE_APPLICATION failed, trying PANEL", e);
                    params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                    params.token = activity.getWindow().getDecorView().getWindowToken();
                    wm.addView(ballView, params);
                }
                shown = true;
                Log.i(TAG, "Ball shown at (" + posX + "," + posY + ") r=" + ballRadius);
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

    /**
     * 悬浮球 View — 三层嵌套自绘。
     * 外层: 纯色大球 (r)
     * 中层: 深灰→浅灰对角渐变 (r × 0.78)
     * 内层: 纯色小球 (r × 0.40)
     */
    private class BallView extends View {
        private final Paint outerPaint;
        private final Paint midPaint;
        private final Paint innerPaint;
        private final GradientDrawable midDrawable;

        BallView(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);

            outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            midPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            // 中层渐变 drawable（左上深灰→右下浅灰）
            midDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{ Color.parseColor("#404040"), Color.parseColor("#8C8C8C") }
            );
            midDrawable.setShape(GradientDrawable.OVAL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            // 深色主题=白色，浅色主题=黑色
            int pureColor = isDark ? Color.WHITE : Color.BLACK;

            // 1. 外层：纯色大球
            outerPaint.setColor(pureColor);
            canvas.drawCircle(cx, cy, ballRadius, outerPaint);

            // 2. 中层：深灰→浅灰对角渐变球 (r × 0.78)
            float midR = ballRadius * 0.78f;
            midDrawable.setBounds(
                (int)(cx - midR), (int)(cy - midR),
                (int)(cx + midR), (int)(cy + midR)
            );
            midDrawable.draw(canvas);

            // 3. 内层：纯色小球 (r × 0.40)
            float innerR = ballRadius * 0.40f;
            innerPaint.setColor(pureColor);
            canvas.drawCircle(cx, cy, innerR, innerPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float ex = event.getRawX();
            float ey = event.getRawY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ex;
                    downY = ey;
                    moved = false;
                    ballOffsetX = ex - posX;
                    ballOffsetY = ey - posY;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float newX = ex - ballOffsetX;
                    float newY = ey - ballOffsetY;
                    if (Math.hypot(ex - downX, ey - downY) > DRAG_THRESHOLD) {
                        moved = true;
                    }
                    if (moved) {
                        // 边界检测
                        int screenW = getResources().getDisplayMetrics().widthPixels;
                        int screenH = getResources().getDisplayMetrics().heightPixels;
                        posX = (int) Math.max(0, Math.min(newX, screenW - ballDiameter));
                        posY = (int) Math.max(0, Math.min(newY, screenH - ballDiameter));
                        updatePosition();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
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
