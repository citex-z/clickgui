package com.visiolab.clickgui.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * 自定义开关组件 — Canvas 自绘，用于 ClickGUI 面板。
 *
 * 视觉：
 * - 开启：蓝色 #1A73E8 → 青色 #00BCD4 渐变背景，圆形 thumb 在右侧，白色
 * - 关闭：灰色 #3A3A3A 背景，圆形 thumb 在左侧，白色
 * - thumb 带 setShadowLayer 阴影效果
 *
 * 交互：
 * - ACTION_DOWN 切换状态
 * - ValueAnimator + OvershootInterpolator 平滑移动 thumb
 * - 状态变化回调 onCheckedChanged
 *
 * 构造：SwitchView(Context, boolean initialState)
 */
public class SwitchView extends View {

    // 默认尺寸
    private static final int DEFAULT_WIDTH_DP = 48;
    private static final int DEFAULT_HEIGHT_DP = 24;

    // 颜色（跟主题一致）
    private int colorOnStart = Color.parseColor("#1A73E8"); // 蓝
    private int colorOnEnd = Color.parseColor("#00BCD4");   // 青
    private int colorOff = Color.parseColor("#3A3A3A");     // 灰
    private int colorThumb = Color.WHITE;

    // 状态
    private boolean checked;
    // thumb 位置进度：0=关闭(左)，1=开启(右)
    private float thumbProgress;

    // 画笔
    private final Paint bgPaint;
    private final Paint thumbPaint;
    private final RectF bgRect = new RectF();

    // 动画
    private ValueAnimator thumbAnimator;

    // 回调
    public interface OnCheckedChangedListener {
        void onCheckedChanged(SwitchView view, boolean isChecked);
    }

    private OnCheckedChangedListener listener;

    public SwitchView(Context context, boolean initialState) {
        super(context);
        this.checked = initialState;
        this.thumbProgress = initialState ? 1f : 0f;

        // 背景画笔
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // thumb 画笔（带阴影，需要软件渲染层）
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(colorThumb);
        thumbPaint.setShadowLayer(dp(3), 0f, dp(1.5f), 0x66000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** 设置状态变化监听器 */
    public void setOnCheckedChangedListener(OnCheckedChangedListener listener) {
        this.listener = listener;
    }

    /**
     * 设置开启状态的渐变起止颜色。
     * 用于主题切换时同步强调色。
     */
    public void setGradientColors(int startColor, int endColor) {
        this.colorOnStart = startColor;
        this.colorOnEnd = endColor;
        invalidate();
    }

    /** 设置关闭状态背景色 */
    public void setOffColor(int color) {
        this.colorOff = color;
        invalidate();
    }

    /** 设置 thumb 颜色 */
    public void setThumbColor(int color) {
        this.colorThumb = color;
        thumbPaint.setColor(color);
        invalidate();
    }

    /** 设置当前开关状态，可选是否带动画 */
    public void setChecked(boolean checked, boolean animate) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        animateThumb(animate);
    }

    public boolean isChecked() {
        return checked;
    }

    /** 切换 thumb 位置动画 */
    private void animateThumb(boolean animate) {
        if (thumbAnimator != null && thumbAnimator.isRunning()) {
            thumbAnimator.cancel();
        }
        float target = checked ? 1f : 0f;
        if (animate) {
            thumbAnimator = ValueAnimator.ofFloat(thumbProgress, target);
            thumbAnimator.setDuration(280);
            thumbAnimator.setInterpolator(new OvershootInterpolator(1.5f));
            thumbAnimator.addUpdateListener(animation -> {
                thumbProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            thumbAnimator.start();
        } else {
            thumbProgress = target;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(dp(DEFAULT_WIDTH_DP), widthMeasureSpec);
        int h = resolveSize(dp(DEFAULT_HEIGHT_DP), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bgRect.set(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float radius = h / 2f;

        // 1) 灰色背景作为底色（始终绘制）
        bgPaint.setShader(null);
        bgPaint.setColor(colorOff);
        canvas.drawRoundRect(bgRect, radius, radius, bgPaint);

        // 2) 开启渐变背景按 thumbProgress 淡入，实现平滑过渡
        if (thumbProgress > 0f) {
            int alpha = (int) (255f * thumbProgress);
            LinearGradient gradient = new LinearGradient(
                    0f, 0f, w, 0f,
                    withAlpha(colorOnStart, alpha),
                    withAlpha(colorOnEnd, alpha),
                    Shader.TileMode.CLAMP);
            bgPaint.setShader(gradient);
            canvas.drawRoundRect(bgRect, radius, radius, bgPaint);
        }

        // 3) 圆形 thumb：左右滑动 + Overshoot 回弹
        float thumbRadius = radius - dp(2);
        float left = thumbRadius + dp(2);
        float right = w - thumbRadius - dp(2);
        float thumbCx = left + (right - left) * thumbProgress;
        float thumbCy = h / 2f;
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 点击即切换状态
                checked = !checked;
                animateThumb(true);
                if (listener != null) {
                    listener.onCheckedChanged(this, checked);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return super.onTouchEvent(event);
    }

    /** 给颜色叠加 alpha 通道 */
    private static int withAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** dp 转 px */
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
