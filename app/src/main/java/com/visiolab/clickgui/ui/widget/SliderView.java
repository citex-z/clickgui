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
 * 自定义滑块组件 — Canvas 自绘，用于 ClickGUI 面板。
 *
 * 视觉：
 * - 灰色 #3A3A3A 圆角轨道
 * - 蓝色 → 青色渐变进度填充
 * - 圆形 thumb，按下时变大（OvershootInterpolator 回弹），带阴影
 * - thumb 右侧显示数值 + 后缀（如 "1.5x"、"50%"）
 *
 * 交互：
 * - ACTION_DOWN：请求父布局不拦截触摸 + 开始拖动
 * - ACTION_MOVE：根据触摸 x 计算进度
 * - ACTION_UP：结束拖动
 * - 回调 onProgressChanged(float progress, float value)
 *
 * 构造：SliderView(Context, float min, float max, float initial, String suffix)
 */
public class SliderView extends View {

    // 默认尺寸
    private static final int DEFAULT_WIDTH_DP = 200;
    private static final int DEFAULT_HEIGHT_DP = 32;

    // 颜色（跟主题一致）
    private int colorTrack = Color.parseColor("#3A3A3A");
    private int colorProgressStart = Color.parseColor("#1A73E8"); // 蓝
    private int colorProgressEnd = Color.parseColor("#00BCD4");   // 青
    private int colorThumb = Color.WHITE;
    private int colorText = Color.WHITE;

    // 数值范围
    private final float min;
    private final float max;
    private float value;          // 当前实际值
    private final String suffix;  // 后缀（如 "x"、"%"）

    // thumb 状态
    private float thumbScale = 1f;
    private boolean dragging = false;
    private ValueAnimator thumbAnim;

    // 画笔
    private final Paint trackPaint;
    private final Paint progressPaint;
    private final Paint thumbPaint;
    private final Paint textPaint;
    private final RectF trackRect = new RectF();

    // 回调
    public interface OnProgressChangedListener {
        void onProgressChanged(SliderView view, float progress, float value);
    }

    private OnProgressChangedListener listener;

    public SliderView(Context context, float min, float max, float initial, String suffix) {
        super(context);
        this.min = min;
        this.max = max;
        this.value = clamp(initial, min, max);
        this.suffix = suffix == null ? "" : suffix;

        // 轨道画笔
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(colorTrack);

        // 进度画笔（运行时设置渐变 shader）
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // thumb 画笔（带阴影，需要软件渲染层）
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(colorThumb);
        thumbPaint.setShadowLayer(dp(3), 0, dp(2), 0x66000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // 数值文字画笔
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(colorText);
        textPaint.setTextSize(sp(11));
    }

    /** 设置进度变化监听器 */
    public void setOnProgressChangedListener(OnProgressChangedListener listener) {
        this.listener = listener;
    }

    /** 设置进度渐变起止颜色（主题切换时调用） */
    public void setGradientColors(int startColor, int endColor) {
        this.colorProgressStart = startColor;
        this.colorProgressEnd = endColor;
        invalidate();
    }

    /** 设置轨道颜色 */
    public void setTrackColor(int color) {
        this.colorTrack = color;
        trackPaint.setColor(color);
        invalidate();
    }

    /** 设置当前值（不触发回调，不触发动画） */
    public void setValue(float v) {
        this.value = clamp(v, min, max);
        invalidate();
    }

    public float getValue() {
        return value;
    }

    /** 进度比例 0..1 */
    public float getProgress() {
        if (max <= min) {
            return 0f;
        }
        return (value - min) / (max - min);
    }

    /** thumb 半径（px） */
    private float thumbRadius() {
        return dp(7);
    }

    /** 轨道左右内边距（px） */
    private float trackHPad() {
        return dp(8);
    }

    /** 根据触摸 x 坐标更新进度并回调 */
    private void setProgressFromTouch(float touchX) {
        float pad = trackHPad();
        float usable = getWidth() - pad * 2f;
        float p = usable > 0f ? (touchX - pad) / usable : 0f;
        p = clamp(p, 0f, 1f);
        float newVal = min + p * (max - min);
        value = newVal;
        invalidate();
        if (listener != null) {
            listener.onProgressChanged(this, p, value);
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
        // 轨道垂直居中
        float trackH = dp(6);
        float trackY = (h - trackH) / 2f;
        float pad = trackHPad();
        trackRect.set(pad, trackY, w - pad, trackY + trackH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float radius = trackRect.height() / 2f;
        float progress = getProgress();

        // 1) 灰色圆角轨道
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        // 2) 渐变进度填充
        float pad = trackHPad();
        float usable = w - pad * 2f;
        float progressW = usable * progress;
        if (progressW > 0f) {
            LinearGradient gradient = new LinearGradient(
                    trackRect.left, 0f,
                    trackRect.left + Math.max(progressW, 1f), 0f,
                    colorProgressStart, colorProgressEnd,
                    Shader.TileMode.CLAMP);
            progressPaint.setShader(gradient);
            RectF progressRect = new RectF(
                    trackRect.left, trackRect.top,
                    trackRect.left + progressW, trackRect.bottom);
            canvas.drawRoundRect(progressRect, radius, radius, progressPaint);
        }

        // 3) 圆形 thumb（按下时变大 + Overshoot 回弹）
        float thumbCx = trackRect.left + usable * progress;
        float thumbCy = h / 2f;
        float tr = thumbRadius() * thumbScale;
        canvas.drawCircle(thumbCx, thumbCy, tr, thumbPaint);

        // 4) 数值 + 后缀显示在 thumb 右侧（超出右边界则改为左侧）
        String valStr = formatValue(value) + suffix;
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = thumbCy - (fm.ascent + fm.descent) / 2f;
        float textW = textPaint.measureText(valStr);

        textPaint.setTextAlign(Paint.Align.LEFT);
        float textX = thumbCx + tr + dp(4);
        if (textX + textW > w - dp(2)) {
            // 改为显示在 thumb 左侧，右对齐
            textPaint.setTextAlign(Paint.Align.RIGHT);
            textX = thumbCx - tr - dp(4);
        }
        canvas.drawText(valStr, textX, textY, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 请求父布局不要拦截触摸事件，保证拖动连贯
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                dragging = true;
                animateThumb(1.4f); // 按下时变大
                setProgressFromTouch(event.getX());
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    setProgressFromTouch(event.getX());
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                animateThumb(1f); // 还原
                return true;
        }
        return super.onTouchEvent(event);
    }

    /** thumb 缩放动画（OvershootInterpolator 回弹） */
    private void animateThumb(float targetScale) {
        if (thumbAnim != null && thumbAnim.isRunning()) {
            thumbAnim.cancel();
        }
        thumbAnim = ValueAnimator.ofFloat(thumbScale, targetScale);
        thumbAnim.setDuration(180);
        thumbAnim.setInterpolator(new OvershootInterpolator(2f));
        thumbAnim.addUpdateListener(animation -> {
            thumbScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        thumbAnim.start();
    }

    /** 数值格式化：整数显示为整型，否则保留 1 位小数 */
    private String formatValue(float v) {
        if (Math.abs(v - Math.round(v)) < 0.001f) {
            return String.valueOf(Math.round(v));
        }
        return String.format("%.1f", v);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** dp 转 px */
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** sp 转 px（用于文字） */
    private int sp(int v) {
        return (int) (v * getResources().getDisplayMetrics().scaledDensity + 0.5f);
    }
}
