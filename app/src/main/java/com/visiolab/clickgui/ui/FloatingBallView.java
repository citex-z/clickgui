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
import android.widget.PopupWindow;

/**
 * 悬浮球 — 原生 View + Canvas 自绘。
 *
 * 功能：
 * - 单击：打开 ClickGUI 面板
 * - 长按拖拽：移动位置
 * - 渐变色圆形 + 阴影
 *
 * 实现：PopupWindow 包裹自定义 View
 */
public class FloatingBallView {

    private static final String TAG = "FloatingBall";
    private final Activity activity;
    private PopupWindow popup;
    private BallView ballView;
    private int ballSize;
    private int posX, posY;
    private OnBallClickListener clickListener;

    // 阻尼拖拽
    private boolean dragging = false;
    private float downX, downY;
    private boolean moved = false;
    private static final float DAMPING = 0.2f; // 阻尼系数

    // 颜色（跟主题一致）
    private static final int COLOR_START = Color.parseColor("#1A73E8"); // 蓝
    private static final int COLOR_END = Color.parseColor("#00BCD4");   // 青

    public interface OnBallClickListener {
        void onBallClick();
    }

    public FloatingBallView(Activity activity) {
        this.activity = activity;
        this.ballSize = dp(activity, 48); // 48dp
        this.posX = dp(activity, 16);
        this.posY = dp(activity, 100);

        ballView = new BallView(activity);
        popup = new PopupWindow(ballView, ballSize, ballSize);
        popup.setClippingEnabled(false);
        popup.setBackgroundDrawable(null);
        popup.setOutsideTouchable(false);
    }

    public void setOnBallClickListener(OnBallClickListener listener) {
        this.clickListener = listener;
    }

    public void show() {
        if (popup != null && !popup.isShowing()) {
            activity.runOnUiThread(() -> {
                try {
                    popup.showAtLocation(
                        activity.getWindow().getDecorView(),
                        Gravity.TOP | Gravity.START,
                        posX, posY
                    );
                    Log.d(TAG, "Ball shown at (" + posX + "," + posY + ")");
                } catch (Exception e) {
                    Log.e(TAG, "show failed", e);
                }
            });
        }
    }

    public void hide() {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
        }
    }

    public boolean isShowing() {
        return popup != null && popup.isShowing();
    }

    /** 自绘悬浮球 View */
    private class BallView extends View {
        private final Paint circlePaint;
        private final Paint shadowPaint;
        private final Paint dotPaint;
        private float pulseScale = 1f;

        public BallView(Context context) {
            super(context);
            // 渐变圆球
            GradientDrawable ballDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{COLOR_START, COLOR_END}
            );
            ballDrawable.setShape(GradientDrawable.OVAL);
            ballDrawable.setBounds(0, 0, ballSize, ballSize);
            setBackground(ballDrawable);

            circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(Color.WHITE);
            circlePaint.setAlpha(200);

            shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(0x40000000);
            shadowPaint.setShadowLayer(dp(context, 6), 0, dp(context, 3), 0x66000000);
            setLayerType(LAYER_TYPE_SOFTWARE, shadowPaint);

            dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setColor(Color.WHITE);
            dotPaint.setAlpha(180);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            // 内部装饰：小圆点（类似加载图标）
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
                        int newX = posX + (int) dx;
                        int newY = posY + (int) dy;
                        // 边界检测
                        int screenW = getResources().getDisplayMetrics().widthPixels;
                        int screenH = getResources().getDisplayMetrics().heightPixels;
                        newX = Math.max(0, Math.min(newX, screenW - ballSize));
                        newY = Math.max(0, Math.min(newY, screenH - ballSize));
                        posX = newX;
                        posY = newY;
                        popup.update(posX, posY, -1, -1);
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
