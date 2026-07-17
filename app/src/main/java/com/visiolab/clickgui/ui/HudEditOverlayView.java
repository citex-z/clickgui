package com.visiolab.clickgui.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * HUD 编辑模式触摸拦截层。
 *
 * 全屏透明 View，仅在编辑模式激活：
 * - 顶部显示 "编辑模式" 提示 + "完成" 按钮
 * - 触摸事件转发给 HudRenderer.onEditTouch()
 * - 点击 "完成" 按钮退出编辑模式
 */
public class HudEditOverlayView extends FrameLayout {

    private final HudRendererRef rendererRef;
    private final Runnable onFinishCallback;
    private TextView finishBtn;
    private Paint hintPaint;
    private int screenW, screenH;

    /** HudRenderer 引用接口（避免直接依赖 GL 类） */
    public interface HudRendererRef {
        boolean onEditTouch(int action, float x, float y);
    }

    public HudEditOverlayView(Context context, HudRendererRef rendererRef, Runnable onFinishCallback) {
        super(context);
        this.rendererRef = rendererRef;
        this.onFinishCallback = onFinishCallback;

        screenW = context.getResources().getDisplayMetrics().widthPixels;
        screenH = context.getResources().getDisplayMetrics().heightPixels;

        setupView();
    }

    private void setupView() {
        // 顶部提示文字
        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.parseColor("#88CCFF"));
        hintPaint.setTextSize(sp(13));
        hintPaint.setTextAlign(Paint.Align.CENTER);

        // "完成" 按钮（右上角）
        finishBtn = new TextView(getContext());
        finishBtn.setText("Done");
        finishBtn.setTextColor(Color.WHITE);
        finishBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        finishBtn.setGravity(Gravity.CENTER);
        finishBtn.setSingleLine(true);
        finishBtn.setBackgroundColor(Color.parseColor("#4A90D9"));
        int padH = dp(16);
        int padV = dp(8);
        finishBtn.setPadding(padH, padV, padH, padV);
        finishBtn.setOnClickListener(v -> {
            if (onFinishCallback != null) onFinishCallback.run();
        });

        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        btnLp.gravity = Gravity.TOP | Gravity.END;
        btnLp.topMargin = dp(12);
        btnLp.rightMargin = dp(12);
        addView(finishBtn, btnLp);

        // 让整个 View 可消费触摸 + 能绘制
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false); // FrameLayout 默认不调用 onDraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 顶部居中提示
        String hint = "HUD Edit Mode — Drag to move";
        float x = getWidth() / 2f;
        float y = dp(40);
        canvas.drawText(hint, x, y, hintPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float ex = event.getRawX();
        float ey = event.getRawY();

        // 检查是否点击了"完成"按钮区域
        if (finishBtn != null && event.getAction() == MotionEvent.ACTION_DOWN) {
            int[] loc = new int[2];
            finishBtn.getLocationOnScreen(loc);
            if (ex >= loc[0] && ex <= loc[0] + finishBtn.getWidth()
                    && ey >= loc[1] && ey <= loc[1] + finishBtn.getHeight()) {
                return super.onTouchEvent(event); // 让按钮处理
            }
        }

        // 转发给 HudRenderer，始终消费事件以保证收到完整触摸序列
        if (rendererRef != null) {
            rendererRef.onEditTouch(event.getAction(), ex, ey);
        }
        return true; // 始终消费，确保 MOVE/UP 事件能收到
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(int v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
