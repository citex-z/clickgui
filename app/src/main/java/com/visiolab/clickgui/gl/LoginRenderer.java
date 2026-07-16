package com.visiolab.clickgui.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.MotionEvent;

import com.visiolab.clickgui.anim.Anim;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 加载动画 + 登录界面渲染器。
 *
 * 动画时间线（还原 AE 演绎动画）：
 *   阶段1 (0 ~ 2008ms)  加载阶段：米色全屏，中央标题，进度条从左到右填满，底部 Loading + 旋转圆点
 *   阶段2 (2008 ~ 3200ms) 过渡阶段：背景图从左滑入，米色面板从左到右擦除，标题飞移到右侧面板，
 *                          进度条 morph 成登录按钮，输入栏浮现
 *   阶段3 (3200ms+)     登录就绪：左侧 65% 背景图 + 右侧 35% 米色面板（标题 + 输入框 + 登录按钮）
 *
 * 配色（来自设计稿）：
 *   米色背景   #ECEAE4
 *   深灰按钮   #3A3A3A
 *   标题文字   #323132
 *   按钮文字   #ECEAE4
 */
public class LoginRenderer {

    // ---- 配色 ----
    private static final float[] BEIGE = hexToColor("#ECEAE4");
    private static final float[] BEIGE_LIGHT = hexToColor("#F4F2EC"); // 面板顶部高光
    private static final float[] BEIGE_DARK = hexToColor("#E2DFD8");  // 面板底部阴影
    private static final float[] DARK_GRAY = hexToColor("#3A3A3A");
    private static final float[] DARK_GRAY_LIGHT = hexToColor("#4A4A4A"); // 按钮顶部高光
    private static final float[] DARK_GRAY_DARK = hexToColor("#2C2C2C");  // 按钮底部阴影
    private static final float[] TITLE_COLOR = hexToColor("#323132");
    private static final float[] BUTTON_TEXT = hexToColor("#ECEAE4");
    private static final float[] DOT_COLOR = hexToColor("#3A3A3A");
    private static final float[] ACCENT = hexToColor("#5A6B8C");  // 聚焦输入框强调色（柔和蓝灰）
    private static final float[] ACCENT_GLOW = hexToColor("#5A6B8C"); // 进度条/聚焦发光

    // ---- 动画时间线 (秒) ----
    private static final float T_LOADING_END = 2.008f;
    private static final float T_TRANSITION_END = 3.200f;
    private static final float T_LOADING_FADE = 1.875f; // Loading 文字开始淡出

    private final Context context;
    private QuadBatch quad;
    private TextRenderer text;
    private BlurPipeline blur;

    /** 获取模块 AssetManager（Xposed 注入模式） */
    private android.content.res.AssetManager getModuleAssets() {
        try {
            Class<?> mrl = Class.forName("com.visiolab.clickgui.xposed.ModuleResourceLoader");
            java.lang.reflect.Method getAssets = mrl.getMethod("getAssets");
            java.lang.reflect.Method isInit = mrl.getMethod("isInitialized");
            if ((boolean) isInit.invoke(null)) {
                android.content.res.AssetManager am = (android.content.res.AssetManager) getAssets.invoke(null);
                if (am != null) return am;
            }
        } catch (Throwable e) { /* 非注入模式 */ }
        return context.getAssets();
    }

    private float animTime = 0f;
    private boolean loginDone = false;

    // ---- 关闭淡出动画 ----
    private boolean fadingOut = false;
    private float fadeOutTimer = 0f;
    private static final float FADE_OUT_DURATION = 0.5f; // 0.5秒淡出

    // ---- 背景图纹理 ----
    private int bgTexture = -1;
    private boolean bgLoaded = false;
    private int texProgram = -1;
    private int tAPos, tAUV, tUTex, tUScreenSize, tUColor, tUXOffset, tUBlur;

    // ---- 输入状态 ----
    private final StringBuilder username = new StringBuilder();
    private final StringBuilder password = new StringBuilder();
    private int activeField = -1; // 0=username, 1=password
    private float[] usernameRect, passwordRect, loginBtnRect;

    // ---- 登录按钮点击动画 ----
    private final Anim loginBtnPress = new Anim(0f, 18f);

    // ---- 背景图模糊参数 ----
    private float bgSlideOffset = 0f;

    public LoginRenderer(Context ctx) {
        this.context = ctx;
    }

    public void init(QuadBatch quad, TextRenderer text, BlurPipeline blur) {
        this.quad = quad;
        this.text = text;
        this.blur = blur;
        loadBackgroundTexture();
        initTexProgram();
    }

    // ================================================================
    //  主渲染入口
    // ================================================================

    public void onDrawFrame(float dt, int screenW, int screenH) {
        if (loginDone) {
            // 关闭淡出动画：淡出到完全透明，不绘制任何黑色遮罩
            if (fadingOut) {
                fadeOutTimer += dt;
                float fadeAlpha = clamp01(1f - fadeOutTimer / FADE_OUT_DURATION);
                // 直接用渐变的 alpha 绘制米色背景和最后一帧，逐渐淡出到透明
                GLES20.glClearColor(BEIGE[0], BEIGE[1], BEIGE[2], fadeAlpha);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                drawFrozenFrame(screenW, screenH, fadeAlpha);
                // 淡出完成时，UIRenderer 检测 isFadeOutComplete() 后切换
                if (fadeOutTimer >= FADE_OUT_DURATION) {
                    // 淡出完成，不再绘制
                }
            }
            return;
        }

        animTime += dt;

        GLES20.glClearColor(BEIGE[0], BEIGE[1], BEIGE[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        float t = animTime;
        boolean inLoading = t < T_LOADING_END;
        float transProgress = clamp01((t - T_LOADING_END) / (T_TRANSITION_END - T_LOADING_END));

        if (inLoading) {
            drawLoadingPhase(t, screenW, screenH);
        } else {
            drawTransitionAndLogin(transProgress, screenW, screenH);
        }
    }

    /** 绘制淡出时的冻结帧（alpha 从 1→0，逐渐淡出到全透明） */
    private void drawFrozenFrame(int sw, int sh, float alpha) {
        if (alpha <= 0.01f) return;
        // 绘制当前登录界面的最后一帧，用 alpha 控制透明度
        boolean inLoading = animTime < T_LOADING_END;
        float transProgress = clamp01((animTime - T_LOADING_END) / (T_TRANSITION_END - T_LOADING_END));
        if (inLoading) {
            drawLoadingPhase(animTime, sw, sh);
        } else {
            drawTransitionAndLogin(transProgress, sw, sh);
        }
    }

    public boolean isFadeOutComplete() {
        return loginDone && fadingOut && fadeOutTimer >= FADE_OUT_DURATION;
    }

    // ================================================================
    //  阶段 1：加载阶段
    // ================================================================

    private void drawLoadingPhase(float t, int sw, int sh) {
        // 1. 米色全屏背景
        quad.addRect(0, 0, sw, sh, 0f, new float[]{BEIGE[0], BEIGE[1], BEIGE[2], 1f});
        quad.flush(sw, sh);

        float cx = sw / 2f, cy = sh / 2f;

        // 2. "Rlin Client" 标题 — 屏幕正中央（字号加大）
        float titleSize = Math.min(sw, sh) * 0.065f;
        String title = "Rlin Client";
        float titleW = text.measure(title, titleSize);
        text.draw(title, cx - titleW / 2f, cy - titleSize * 0.7f, titleSize,
                new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], 1f}, sw, sh);

        // 3. "v0.0.1 Test" — 主标题右下方，间隔一定距离
        float subSize = titleSize * 0.35f;
        String sub = "v0.0.1 Test";
        float subW = text.measure(sub, subSize);
        text.draw(sub, cx + titleW / 2f + 10f, cy - titleSize * 0.5f, subSize,
                new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], 0.8f}, sw, sh);

        // 4. 进度条 — 标题下方（视觉中心），加粗 + 柔光
        float barW = Math.min(sw * 0.5f, 500f);
        float barH = 12f; // 加粗进度条
        float barX = cx - barW / 2f;
        float barY = cy + titleSize * 1.2f;
        // 背景
        quad.addRect(barX, barY, barW, barH, barH / 2f,
                new float[]{DARK_GRAY[0], DARK_GRAY[1], DARK_GRAY[2], 0.15f});
        // 填充
        float fillProgress = clamp01(t / T_LOADING_END);
        float fillW = barW * fillProgress;
        if (fillW > barH) {
            // 填充下方的柔和发光（更宽、更模糊感）
            quad.addRect(barX - 2f, barY - 3f, fillW + 4f, barH + 6f, (barH + 6f) / 2f,
                    new float[]{ACCENT_GLOW[0], ACCENT_GLOW[1], ACCENT_GLOW[2], 0.18f});
            // 主填充：顶部高光 -> 底部深色渐变，更有质感
            quad.addRectGradient(barX, barY, fillW, barH, barH / 2f,
                    DARK_GRAY_LIGHT, DARK_GRAY_LIGHT, DARK_GRAY_DARK, DARK_GRAY_DARK);
        }
        quad.flush(sw, sh);

        // 4.1 状态文字 — 进度条上方，按进度切换阶段文案（注入/加载对话的状态提示）
        float statusSize = titleSize * 0.22f;
        String statusText;
        if (fillProgress < 0.35f) statusText = "Initializing core...";
        else if (fillProgress < 0.70f) statusText = "Loading assets...";
        else if (fillProgress < 1f) statusText = "Preparing interface...";
        else statusText = "Ready";
        int pct = (int) Math.floor(fillProgress * 100f);
        statusText = statusText + "  " + pct + "%";
        float statusW = text.measure(statusText, statusSize);
        text.draw(statusText, cx - statusW / 2f, barY - statusSize - 6f, statusSize,
                new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], 0.55f}, sw, sh);

        // 5. "Loading" 文字 + 三个旋转圆点 — 进度条正下方
        float loadAlpha = t > T_LOADING_FADE ? clamp01(1f - (t - T_LOADING_FADE) / (T_LOADING_END - T_LOADING_FADE)) : 1f;
        if (loadAlpha > 0.01f) {
            float loadSize = titleSize * 0.30f;
            String loadText = "Loading";
            float loadW = text.measure(loadText, loadSize);
            // 文字和圆点整体居中：先算出总宽 = loadW + 间距 + 轨道直径
            float totalW = loadW + 4f + loadSize * 0.36f * 2f;
            float loadX = cx - totalW / 2f;
            float loadY = barY + barH + 18f;
            text.draw(loadText, loadX, loadY, loadSize,
                    new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], loadAlpha}, sw, sh);

            // 三个旋转圆点（在"Login"正右侧，再往下微调）
            float dotR = loadSize * 0.14f;         // 更小的圆点
            float dotOrbit = loadSize * 0.28f;      // 更紧凑的轨道
            // 基于 "Login" 文字的正右侧
            String loginStr = "login";
            float loginSize = titleSize * 0.35f;
            float loginW = text.measure(loginStr, loginSize);
            float loginX = cx - loginW / 2f;
            float loginY_ = barY + barH + 12f;
            float dotCx = loginX + loginW + 4f + dotOrbit;
            float dotCy = loginY_ + loginSize * 0.5f + 2f; // 再往下2px微调
            float rotation = t * 4f;
            for (int i = 0; i < 3; i++) {
                float angle = rotation + i * (float) (Math.PI * 2f / 3f);
                float dx = dotCx + (float) Math.cos(angle) * dotOrbit;
                float dy = dotCy + (float) Math.sin(angle) * dotOrbit;
                float dotAlpha = loadAlpha * (0.4f + 0.6f * (1f - i / 3f));
                quad.addRect(dx - dotR, dy - dotR, dotR * 2, dotR * 2, dotR,
                        new float[]{DOT_COLOR[0], DOT_COLOR[1], DOT_COLOR[2], dotAlpha});
            }
            quad.flush(sw, sh);
        }

        // "login" 文字提前渐显（进度条快满时）
        if (t > T_LOADING_END * 0.75f) {
            float loginAlpha = clamp01((t - T_LOADING_END * 0.75f) / (T_LOADING_END * 0.25f));
            float loginSize = titleSize * 0.35f;
            String loginStr = "login";
            float loginW = text.measure(loginStr, loginSize);
            text.draw(loginStr, cx - loginW / 2f, barY + barH + 12f, loginSize,
                    new float[]{BUTTON_TEXT[0], BUTTON_TEXT[1], BUTTON_TEXT[2], loginAlpha}, sw, sh);
        }
    }

    // ================================================================
    //  阶段 2+3：过渡 + 登录就绪
    // ================================================================

    private void drawTransitionAndLogin(float progress, int sw, int sh) {
        float easedP = easeOutCubic(progress);

        // ---- 背景图：从左滑入 ----
        float bgSlideX = lerp(-sw * 0.3f, 0f, easedP);
        float bgAlpha = easedP;

        if (bgLoaded && bgTexture > 0) {
            drawTexturedRect(bgSlideX, 0, sw, sh, bgTexture, sw, sh, bgAlpha, 0.02f);
        }

        // ---- 米色面板擦除（露出左侧背景图）----
        float rightPanelStart = sw * 0.65f;
        float rightPanelW = sw - rightPanelStart;
        float wipeProgress = easedP;
        float beigeVisibleStart = lerp(0f, rightPanelStart, wipeProgress);
        float beigeW = sw - beigeVisibleStart;
        float finalPanelCx = rightPanelStart + rightPanelW * 0.5f;

        if (beigeW > 0) {
            quad.addRect(beigeVisibleStart, 0, beigeW, sh, 0f,
                    new float[]{BEIGE[0], BEIGE[1], BEIGE[2], 1f});
            quad.flush(sw, sh);
        }

        // ---- 标题：从屏幕中央飞移到右侧面板上方（字号加大）----
        float titleStartX = sw / 2f;
        float titleEndX = finalPanelCx;
        float titleStartY = sh / 2f - sh * 0.045f;
        float titleEndY = sh * 0.18f;
        float titleX = lerp(titleStartX, titleEndX, easedP);
        float titleY = lerp(titleStartY, titleEndY, easedP);

        float titleSizeStart = Math.min(sw, sh) * 0.065f;
        float titleSizeEnd = Math.min(sw, sh) * 0.040f;
        float titleSize = lerp(titleSizeStart, titleSizeEnd, easedP);
        String titleStr = "Rlin Client";
        float titleW = text.measure(titleStr, titleSize);
        text.draw(titleStr, titleX - titleW / 2f, titleY, titleSize,
                new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], 1f}, sw, sh);

        // v0.0.1 Test — 主标题右下方，间隔一定距离
        float subSize = titleSize * 0.40f;
        String subStr = "v0.0.1 Test";
        float subW = text.measure(subStr, subSize);
        text.draw(subStr, titleX + titleW * 0.5f + 8f, titleY + titleSize * 0.2f, subSize,
                new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], 0.7f}, sw, sh);

        // ---- 进度条 → 登录按钮 变形 ----
        float morphP = clamp01((animTime - T_LOADING_END - 0.017f) / (T_TRANSITION_END - T_LOADING_END - 0.017f));
        float morphEased = easeOutCubic(morphP);

        // 起始：粗圆角条（与加载阶段进度条一致）
        float barStartX = sw / 2f - Math.min(sw * 0.25f, 250f);
        float barStartY = sh / 2f + titleSizeStart * 1.2f;
        float barStartW = Math.min(sw * 0.5f, 500f);
        float barStartH = 8f;
        float barStartRadius = barStartH / 2f;

        // 终止：登录按钮
        float btnW = rightPanelW * 0.65f;
        float btnH = sh * 0.065f;
        float btnX = finalPanelCx - btnW / 2f;
        float btnY = sh * 0.72f;

        // morph 中间值：X 从屏幕中心渐变到右侧面板中心，Y 从进度条渐变到按钮
        float morphBarX = lerp(barStartX + barStartW * 0.5f, finalPanelCx, morphEased) - lerp(barStartW, btnW, morphEased) * 0.5f;
        float morphBarY = lerp(barStartY, btnY, morphEased);
        float morphBarW = lerp(barStartW, btnW, morphEased);
        float morphBarH = lerp(barStartH, btnH, morphEased);
        // 终态保持圆角（不再归零成直角矩形），变成圆角按钮卡片
        float morphBarR = lerp(barStartRadius, btnH * 0.28f, morphEased);
        float pressT = loginBtnPress.get();
        float pressOffset = pressT * 2f;

        // 按钮主体：顶部高光 -> 底部深色渐变，按下时整体下沉 2px
        quad.addRectGradient(morphBarX, morphBarY + pressOffset, morphBarW, morphBarH, morphBarR,
                DARK_GRAY_LIGHT, DARK_GRAY_LIGHT, DARK_GRAY_DARK, DARK_GRAY_DARK);
        // 顶部细高光条（增加按钮卡片的质感）
        if (morphBarH > 14f) {
            quad.addRect(morphBarX + morphBarR, morphBarY + pressOffset + 1.5f,
                    morphBarW - morphBarR * 2f, Math.max(1f, morphBarH * 0.12f), 0f,
                    new float[]{1f, 1f, 1f, 0.12f * morphEased});
        }
        quad.flush(sw, sh);

        // "login" 文字（在按钮中央）
        if (morphP > 0.1f) {
            float loginAlpha = clamp01((morphP - 0.1f) / 0.4f);
            float loginSize = lerp(titleSizeStart * 0.28f, btnH * 0.40f, morphEased);
            String loginStr = "login";
            float loginW = text.measure(loginStr, loginSize);
            float loginX = lerp(sw / 2f, morphBarX + morphBarW / 2f, morphEased) - loginW / 2f;
            float loginY = lerp(barStartY + barStartH + 12f, morphBarY + (morphBarH - loginSize) / 2f + 1f, morphEased);
            text.draw(loginStr, loginX, loginY + pressOffset, loginSize,
                    new float[]{BUTTON_TEXT[0], BUTTON_TEXT[1], BUTTON_TEXT[2], loginAlpha}, sw, sh);

            if (morphP > 0.9f) {
                loginBtnRect = new float[]{btnX, btnY, btnW, btnH};
            }
        }

        // ---- 输入栏浮现（Y 坐标自适应屏幕，X 用面板中心）----
        float inputP = clamp01((animTime - T_LOADING_END - 0.583f) / (T_TRANSITION_END - T_LOADING_END - 0.583f));
        float inputAlpha = easeOutCubic(inputP);
        if (inputAlpha > 0.01f) {
            float inputW = rightPanelW * 0.80f;
            float inputX = finalPanelCx - inputW / 2f;
            float inputH = sh * 0.055f;
            float inputGap = sh * 0.045f;
            // 输入框垂直位置：放在标题与按钮之间的中间区域，自适应
            float inputStartY = btnY - inputGap * 3f - inputH * 2f - titleSize * 1.2f;
            if (inputStartY < sh * 0.15f) inputStartY = sh * 0.15f; // 避免太靠上
            float unY = inputStartY;
            float unLabelSize = titleSize * 0.45f;
            String unLabel = "user name:";
            float unLabelW = text.measure(unLabel, unLabelSize);
            text.draw(unLabel, inputX, unY - unLabelSize - 4f, unLabelSize,
                    new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], inputAlpha}, sw, sh);

            // 下划线输入框
            float unLineY = unY + inputH;
            quad.addRect(inputX, unLineY, inputW, 1.5f * (sw / 1280f), 0f,
                    new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], inputAlpha});
            // 输入的文字
            String unText = username.toString();
            if (unText.length() > 0) {
                float unTextSize = unLabelSize * 1.1f;
                text.draw(unText, inputX + 4f, unLineY - unTextSize - 2f, unTextSize,
                        new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], inputAlpha}, sw, sh);
            }
            // 光标（闪烁）
            if (activeField == 0 && (int)(animTime * 2f) % 2 == 0) {
                float cursorX = inputX + 4f + text.measure(username.toString(), unLabelSize * 1.1f) + 2f;
                quad.addRect(cursorX, unLineY - unLabelSize * 1.3f, 1.5f * (sw / 1280f), unLabelSize * 1.3f, 0f,
                        new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], inputAlpha});
            }
            usernameRect = new float[]{inputX, unLineY - inputH, inputW, inputH + unLabelSize};

            // password
            float pwY = unY + inputH + inputGap;
            float pwLabelSize = unLabelSize;
            String pwLabel = "password:";
            float pwLabelW = text.measure(pwLabel, pwLabelSize);
            text.draw(pwLabel, inputX, pwY - pwLabelSize - 4f, pwLabelSize,
                    new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], inputAlpha}, sw, sh);

            float pwLineY = pwY + inputH;
            quad.addRect(inputX, pwLineY, inputW, 1.5f * (sw / 1280f), 0f,
                    new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], inputAlpha});
            // 密码用 ● 显示
            String pwText = password.toString();
            if (pwText.length() > 0) {
                float pwTextSize = pwLabelSize * 1.1f;
                String masked = repeat("●", pwText.length());
                text.draw(masked, inputX + 4f, pwLineY - pwTextSize - 2f, pwTextSize,
                        new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], inputAlpha}, sw, sh);
            }
            // 光标
            if (activeField == 1 && (int)(animTime * 2f) % 2 == 0) {
                float cursorX = inputX + 4f + text.measure(repeat("●", password.length()), pwLabelSize * 1.1f) + 2f;
                quad.addRect(cursorX, pwLineY - pwLabelSize * 1.3f, 1.5f * (sw / 1280f), pwLabelSize * 1.3f, 0f,
                        new float[]{TITLE_COLOR[0], TITLE_COLOR[1], TITLE_COLOR[2], inputAlpha});
            }
            passwordRect = new float[]{inputX, pwLineY - inputH, inputW, inputH + pwLabelSize};

            quad.flush(sw, sh);
        }

        // 更新按钮按下动画
        loginBtnPress.update(0.016f);
    }

    // ================================================================
    //  触摸交互
    // ================================================================

    public void onTouch(MotionEvent event, int sw, int sh) {
        if (animTime < T_TRANSITION_END) return; // 动画未完成不响应

        float ex = event.getX(), ey = event.getY();
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            // 点击用户名输入框
            if (usernameRect != null && inside(ex, ey, usernameRect)) {
                activeField = 0;
                return;
            }
            // 点击密码输入框
            if (passwordRect != null && inside(ex, ey, passwordRect)) {
                activeField = 1;
                return;
            }
            // 点击登录按钮（启动淡出动画）
            if (loginBtnRect != null && inside(ex, ey, loginBtnRect)) {
                activeField = -1; // 立即取消聚焦，防止 IME 继续发送文本
                loginBtnPress.snapTo(1f);
                loginBtnPress.setTarget(0f);
                // 随便填都能登录，启动淡出
                loginDone = true;
                fadingOut = true;
                fadeOutTimer = 0f;
                return;
            }
            // 点击空白处取消聚焦
            activeField = -1;
        }
    }

    public boolean isLoginDone() {
        return loginDone;
    }

    // ================================================================
    //  键盘输入支持（由外部 Activity 或 View 注入）
    // ================================================================

    /** 注入一个字符到当前聚焦的输入框 */
    public void appendChar(char c) {
        if (animTime < T_TRANSITION_END) return;
        if (activeField == 0) {
            username.append(c);
        } else if (activeField == 1) {
            password.append(c);
        }
    }

    /** 删除当前聚焦输入框的最后一个字符 */
    public void deleteChar() {
        if (activeField == 0 && username.length() > 0) {
            username.deleteCharAt(username.length() - 1);
        } else if (activeField == 1 && password.length() > 0) {
            password.deleteCharAt(password.length() - 1);
        }
    }

    /** 是否有输入框处于聚焦状态 */
    public boolean hasActiveField() {
        return activeField >= 0;
    }

    /** 当前聚焦的字段索引：0=用户名, 1=密码, -1=未聚焦（供 UIRenderer 判断字段切换） */
    public int getActiveField() {
        return activeField;
    }

    /** 读取当前聚焦输入框的文本（供隐藏 EditText 同步显示用） */
    public String getActiveFieldText() {
        if (activeField == 0) return username.toString();
        if (activeField == 1) return password.toString();
        return "";
    }

    /** 用整段文本覆盖当前聚焦输入框（隐藏 EditText 的 TextWatcher 回写用）。
     *  比逐字符 append/delete 更稳健，能正确处理 IME 组词、粘贴、整段删除等情况。 */
    public void setActiveFieldText(String text) {
        if (animTime < T_TRANSITION_END) return;
        if (loginDone) return; // 登录已提交，不再接受输入，避免淡出期间 TextWatcher 回调导致崩溃
        String s = text == null ? "" : text;
        if (activeField == 0) {
            username.setLength(0);
            username.append(s);
        } else if (activeField == 1) {
            password.setLength(0);
            password.append(s);
        }
    }

    // ================================================================
    //  背景图纹理加载与渲染
    // ================================================================

    private void loadBackgroundTexture() {
        try {
            // 优先使用模块资源（Xposed 注入模式）
            android.content.res.AssetManager am = getModuleAssets();
            Bitmap bmp = BitmapFactory.decodeStream(am.open("login_bg.jpg"));
            if (bmp == null) return;
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            bgTexture = ids[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTexture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            bmp.recycle();
            bgLoaded = true;
        } catch (Exception e) {
            // 加载失败不阻塞，背景就是纯色
        }
    }

    private void initTexProgram() {
        String vert =
            "precision highp float;\n" +
            "uniform vec2 uScreenSize;\n" +
            "attribute vec2 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec2 ndc = vec2(aPos.x / uScreenSize.x * 2.0 - 1.0,\n" +
            "                  1.0 - aPos.y / uScreenSize.y * 2.0);\n" +
            "  gl_Position = vec4(ndc, 0.0, 1.0);\n" +
            "  vUV = aUV;\n" +
            "}\n";
        String frag =
            "precision highp float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uColor;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec4 c = texture2D(uTex, vUV);\n" +
            "  gl_FragColor = vec4(c.rgb * uColor.rgb, c.a * uColor.a);\n" +
            "}\n";
        texProgram = GLUtil.buildProgram(vert, frag);
        tAPos = GLES20.glGetAttribLocation(texProgram, "aPos");
        tAUV = GLES20.glGetAttribLocation(texProgram, "aUV");
        tUTex = GLES20.glGetUniformLocation(texProgram, "uTex");
        tUScreenSize = GLES20.glGetUniformLocation(texProgram, "uScreenSize");
        tUColor = GLES20.glGetUniformLocation(texProgram, "uColor");
    }

    /** 绘制一张全屏纹理矩形（带 alpha 和可选偏移） */
    private void drawTexturedRect(float x, float y, float w, float h, int tex, int sw, int sh, float alpha, @SuppressWarnings("unused") float blur) {
        if (!bgLoaded || tex <= 0) return;

        GLES20.glUseProgram(texProgram);
        GLES20.glUniform2f(tUScreenSize, sw, sh);
        GLES20.glUniform1i(tUTex, 0);
        GLES20.glUniform4f(tUColor, 1f, 1f, 1f, alpha);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);

        float[] verts = {
            x, y,      0f, 0f,
            x + w, y,   1f, 0f,
            x, y + h,  0f, 1f,
            x, y + h,  0f, 1f,
            x + w, y,   1f, 0f,
            x + w, y + h, 1f, 1f,
        };

        FloatBuffer buf = ByteBuffer.allocateDirect(verts.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        buf.put(verts).position(0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glEnableVertexAttribArray(tAPos);
        GLES20.glVertexAttribPointer(tAPos, 2, GLES20.GL_FLOAT, false, 16, buf);
        buf.position(2);
        GLES20.glEnableVertexAttribArray(tAUV);
        GLES20.glVertexAttribPointer(tAUV, 2, GLES20.GL_FLOAT, false, 16, buf);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisableVertexAttribArray(tAPos);
        GLES20.glDisableVertexAttribArray(tAUV);
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private static float[] hexToColor(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return new float[]{r / 255f, g / 255f, b / 255f, 1f};
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float easeOutCubic(float t) { return 1f - (float) Math.pow(1f - t, 3); }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private boolean inside(float px, float py, float[] r) {
        return px >= r[0] && px <= r[0] + r[2] && py >= r[1] && py <= r[1] + r[3];
    }
}
