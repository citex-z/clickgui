package com.visiolab.clickgui.gl;

import android.opengl.GLES20;

import com.visiolab.clickgui.anim.Anim;
import com.visiolab.clickgui.model.Category;
import com.visiolab.clickgui.model.ModuleItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 灵动岛渲染器——仿照 Apple Dynamic Island + Jonqwq 的 MC 版本实现。
 * MIT 协议，原作者 @Jon_awa
 *
 * 核心行为：
 * - 默认状态：屏幕顶部居中显示一个小药丸，包含客户端名称 + FPS + Ping
 * - 有内容时：平滑展开为更大的圆角矩形，显示内容
 * - 内容类型：MODULE（模块开关）、SUCCESS、WARNING、INFO
 * - 动画：EaseOutExpo 入场/退场，宽度/高度/位置平滑过渡
 */
public class DynamicIslandRenderer {

    private final QuadBatch quad;
    private final TextRenderer text;
    private final BlurPipeline blur;

    // 灵动岛状态
    private float renderX = 0f;
    private float targetX = 0f;
    private float currentWidth = 0f;
    private float targetWidth = 0f;
    private float currentHeight = 0f;
    private float targetHeight = 0f;

    // 默认状态的高度（小药丸）
    private static final float DEFAULT_HEIGHT = 30f;
    private static final float DEFAULT_PAD_X = 16f;
    private static final float ISLAND_Y = 15f; // 距顶部偏移
    private static final float CORNER_RADIUS = 15f;

    // 内容列表
    private final List<IslandContent> contents = new ArrayList<>();
    private final List<TimedContent> timedContents = new ArrayList<>();

    // 模块引用
    private ModuleItem islandModule;
    private ModuleItem.ConfigOption showFpsOpt, showPingOpt, durationOpt, scaleOpt, blurBgOpt;

    // 世界场景绘制回调（用于背景模糊）
    private BlurPipeline.SceneDrawer worldSceneDrawer;

    // FPS/Ping 模拟值
    private int currentFps = 60;
    private int currentPing = 15;
    private long lastFpsUpdate = 0;
    private int frameCount = 0;

    // 优先级
    private static final int PRIORITY_LOW = 0;
    private static final int PRIORITY_MEDIUM = 1;
    private static final int PRIORITY_TOP = 2;

    public enum ContentType {
        MODULE,
        SUCCESS,
        WARNING,
        INFO
    }

    public DynamicIslandRenderer(QuadBatch quad, TextRenderer text, BlurPipeline blur, BlurPipeline.SceneDrawer worldSceneDrawer) {
        this.quad = quad;
        this.text = text;
        this.blur = blur;
        this.worldSceneDrawer = worldSceneDrawer;
    }

    /** 从 categories 中查找 DynamicIsland 模块并提取配置项引用 */
    public void refreshModuleRefs(List<Category> categories) {
        for (Category c : categories) {
            if (!c.name.equals("Client")) continue;
            for (ModuleItem m : c.modules) {
                if (m.name.equals("DynamicIsland")) {
                    islandModule = m;
                    for (ModuleItem.ConfigOption opt : m.options) {
                        switch (opt.label) {
                            case "Show FPS": showFpsOpt = opt; break;
                            case "Show Ping": showPingOpt = opt; break;
                            case "Duration": durationOpt = opt; break;
                            case "Scale": scaleOpt = opt; break;
                            case "Blur Background": blurBgOpt = opt; break;
                        }
                    }
                    break;
                }
            }
        }
    }

    /** 添加模块开关内容 */
    public void addModuleToggle(String moduleName, boolean enabled) {
        // 如果已有同模块的内容，刷新时间
        for (TimedContent tc : timedContents) {
            if (tc.moduleName != null && tc.moduleName.equals(moduleName)) {
                tc.time = tc.timer.getTime() + getDurationMs();
                tc.enabled = enabled;
                tc.exiting = false;
                tc.animation.reset();
                tc.animation.setDirection(AnimDir.FORWARDS);
                return;
            }
        }
        timedContents.add(new TimedContent(ContentType.MODULE, "Module Toggled",
                moduleName + (enabled ? " has been Enabled" : " has been Disabled"),
                moduleName, enabled, getDurationMs()));
    }

    /** 添加成功提示 */
    public void addSuccess(String title, String description) {
        timedContents.add(new TimedContent(ContentType.SUCCESS, title, description, null, true, getDurationMs()));
    }

    /** 添加警告提示 */
    public void addWarning(String title, String description) {
        timedContents.add(new TimedContent(ContentType.WARNING, title, description, null, true, getDurationMs()));
    }

    /** 添加信息提示 */
    public void addInfo(String title, String description) {
        timedContents.add(new TimedContent(ContentType.INFO, title, description, null, true, getDurationMs()));
    }

    private long getDurationMs() {
        // Duration slider: 2-8s, 归一化值映射到 2000-8000ms
        return durationOpt != null ? (long) (2000 + durationOpt.sliderValue * 6000) : 3000;
    }

    /** 每帧更新和渲染 */
    public void update(float dt, float screenW, float screenH, float uiScale) {
        if (islandModule == null) return;
        float a = Math.max(0f, Math.min(1f, islandModule.hudAnim.get()));
        if (a <= 0.01f) return;

        int sw = (int) screenW;
        int sh = (int) screenH;

        // FPS 计数
        frameCount++;
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastFpsUpdate > 1000) {
            currentFps = (int) (frameCount * 1000f / (nowMs - lastFpsUpdate));
            frameCount = 0;
            lastFpsUpdate = nowMs;
            currentPing = 10 + (int) (Math.random() * 15);
        }

        float scale = scaleOpt != null ? (0.7f + scaleOpt.sliderValue * 0.8f) : 1f;
        float s = uiScale * scale;

        // 处理 timedContents：将未过期的转为 contents
        // 原版行为：内容在 time 到期后不会立即移除，而是等退场动画(Backwards)播完后才移除
        contents.clear();
        for (int i = timedContents.size() - 1; i >= 0; i--) {
            TimedContent tc = timedContents.get(i);
            long elapsed = tc.timer.getTime();

            // 判断是否应该开始退场
            long remain = tc.time - elapsed;
            if (remain < 300 && !tc.exiting) {
                tc.exiting = true;
                tc.animation.setDirection(AnimDir.BACKWARDS);
            }

            // 退场动画播完后才移除（不是 timer 到期就移除）
            if (tc.exiting) {
                double out = tc.animation.getOutput(elapsed, tc.time);
                if (out <= 0.01) {
                    timedContents.remove(i);
                    continue;
                }
            } else if (elapsed >= tc.time) {
                // 安全兜底：timer 到期但没开始退场（理论上不会发生）
                tc.exiting = true;
                tc.animation.setDirection(AnimDir.BACKWARDS);
            }

            // 计算内容宽度
            float titleW = text.measure(tc.title, 14f * s);
            float descW = text.measure(tc.description, 12f * s);
            float contentW = 50f * s + Math.max(titleW, descW);
            float contentH = 40f * s;
            contents.add(new IslandContent(tc, contentW, contentH, PRIORITY_LOW));
        }

        // 计算目标尺寸
        boolean hasContent = !contents.isEmpty();
        if (hasContent) {
            // 取最大宽度
            float maxW = 0f;
            float totalH = 0f;
            for (IslandContent ic : contents) {
                if (ic.width > maxW) maxW = ic.width;
                totalH += ic.height;
            }
            targetWidth = maxW + 20f * s;
            targetHeight = totalH + 10f * s;
        } else {
            // 默认状态：客户端名 + FPS + Ping
            StringBuilder defaultText = new StringBuilder();
            if (showFpsOpt != null && showFpsOpt.boolValue) defaultText.append(" | ").append(currentFps).append("fps");
            if (showPingOpt != null && showPingOpt.boolValue) defaultText.append(" | ").append(currentPing).append("ms");
            String nameStr = "Rlin";
            float nameW = text.measure(nameStr, 14f * s);
            float defW = defaultText.length() > 0 ? text.measure(defaultText.toString(), 12f * s) : 0f;
            targetWidth = nameW + defW + 30f * s;
            targetHeight = DEFAULT_HEIGHT * s;
        }

        // 平滑过渡（速度 0.5，约 0.2 秒内完成大部分变化）
        currentWidth = smoothAnim(currentWidth, targetWidth, 0.5f, dt);
        currentHeight = smoothAnim(currentHeight, targetHeight, 0.5f, dt);

        // 位置居中
        targetX = (screenW - currentWidth) / 2f;
        renderX = smoothAnim(renderX, targetX, 0.5f, dt);

        // ---- 渲染（1:1 还原原版三层结构）----
        // 原版: addBlur(black rect) → addBloom(black rect) → drawRoundedRect(black, alpha=130)
        float islandX = renderX;
        float islandY = ISLAND_Y * s;
        float islandW = currentWidth;
        float islandH = currentHeight;
        float cornerR = CORNER_RADIUS * s;

        // 1. 背景模糊（可选）：捕获世界场景 → 模糊 → 毛玻璃面板裁剪
        boolean blurBg = blurBgOpt != null && blurBgOpt.boolValue;
        if (blurBg) {
            blur.captureAndBlur(worldSceneDrawer);
            blur.drawGlassPanel(islandX, islandY, islandW, islandH, cornerR,
                    new float[]{0.02f, 0.02f, 0.02f, 0.5f}, a, sw, sh);
        }

        // 2. 边缘模糊 (bloom)：捕获黑色形状 → 模糊 → alpha混合绘制柔和暗光晕
        //    用 drawGlow (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA) 而非 drawGlowColor (GL_ONE,GL_ONE)
        //    因为黑色 RGB=0 在叠加混合下不可见，必须用 alpha 混合
        blur.captureAndBlurTransparent(() -> {
            quad.addRect(islandX, islandY, islandW, islandH, cornerR, new float[]{0f, 0f, 0f, 1f});
            quad.flush(sw, sh);
        });
        // 第一层：较大范围的暗光晕（原版 addBlur）
        blur.drawGlow(new float[]{0f, 0f, 0f, 0.5f * a}, sw, sh);
        // 第二层：更紧凑的暗光晕（原版 addBloom，用 captureAndBlurTransparentTight 做更强扩散）
        blur.captureAndBlurTransparentTight(() -> {
            quad.addRect(islandX, islandY, islandW, islandH, cornerR, new float[]{0f, 0f, 0f, 1f});
            quad.flush(sw, sh);
        });
        blur.drawGlow(new float[]{0f, 0f, 0f, 0.3f * a}, sw, sh);

        // 3. 黑色半透明背景（原版 new Color(0,0,0,130) ≈ 0.51 alpha）
        quad.addRect(islandX, islandY, islandW, islandH, cornerR, new float[]{0f, 0f, 0f, 0.51f * a});
        quad.flush(sw, sh);

        // 3. 裁剪区域
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        int scissorX = (int) islandX;
        int scissorY = (int) (screenH - islandY - islandH);
        int scissorW = (int) islandW;
        int scissorH = (int) islandH;
        GLES20.glScissor(scissorX, scissorY, scissorW, scissorH);

        // 4. 内容渲染
        if (hasContent) {
            float contentY = islandY + 5f * s;
            for (IslandContent ic : contents) {
                TimedContent tc = ic.timedContent;
                // 动画方向已在上面处理，这里只取输出值
                double animProgress = tc.animation.getOutput(tc.timer.getTime(), tc.time);
                float animAlpha = (float) animProgress;

                // 图标区域
                float iconSize = 24f * s;
                float iconX = islandX + 7f * s;
                float iconY = contentY + 8f * s;

                // 根据类型绘制不同图标
                float[] iconColor;
                switch (tc.type) {
                    case MODULE:
                        // 模块开关：圆形动画
                        float modAnim = (float) animProgress;
                        // 外圈
                        quad.addRect(iconX, iconY, iconSize, iconSize, iconSize / 2f,
                                new float[]{0.2f, 0.2f, 0.2f, 0.9f * a * animAlpha});
                        // 内圈：颜色随开关状态变化
                        float r = tc.enabled ? 0.3f : 0.5f;
                        float g = tc.enabled ? 0.56f : 0.5f;
                        float b = tc.enabled ? 0.33f : 0.5f;
                        float innerSize = iconSize * 0.7f * (0.3f + 0.7f * modAnim);
                        float innerX = iconX + (iconSize - innerSize) / 2f;
                        float innerY2 = iconY + (iconSize - innerSize) / 2f;
                        quad.addRect(innerX, innerY2, innerSize, innerSize, innerSize / 2f,
                                new float[]{r, g, b, 0.9f * a * animAlpha});
                        break;
                    case SUCCESS:
                        iconColor = new float[]{0.37f, 0.56f, 0.31f, 0.78f * a * animAlpha};
                        quad.addRect(iconX, iconY, iconSize, iconSize, 7f * s, iconColor);
                        // 对号（简化为白色小矩形）
                        float checkProg = Math.min(1f, (float) animProgress * 1.5f);
                        float checkSize = iconSize * 0.4f * checkProg;
                        quad.addRect(iconX + iconSize * 0.25f, iconY + iconSize * 0.45f, checkSize, 2f * s, 1f * s,
                                new float[]{1f, 1f, 1f, a * animAlpha});
                        break;
                    case WARNING:
                        iconColor = new float[]{0.56f, 0.31f, 0.31f, 0.78f * a * animAlpha};
                        quad.addRect(iconX, iconY, iconSize, iconSize, 7f * s, iconColor);
                        // 感叹号
                        float exProg = Math.min(1f, (float) animProgress * 1.5f);
                        float exH = iconSize * 0.5f * exProg;
                        quad.addRect(iconX + iconSize * 0.42f, iconY + iconSize * 0.2f, 2.5f * s, exH, 1f * s,
                                new float[]{1f, 1f, 1f, a * animAlpha});
                        quad.addRect(iconX + iconSize * 0.42f, iconY + iconSize * 0.72f, 2.5f * s, 2.5f * s, 1f * s,
                                new float[]{1f, 1f, 1f, a * animAlpha});
                        break;
                    case INFO:
                        iconColor = new float[]{0.19f, 0.46f, 0.58f, 0.78f * a * animAlpha};
                        quad.addRect(iconX, iconY, iconSize, iconSize, 7f * s, iconColor);
                        // i 字母（竖线 + 点）
                        float iProg = Math.min(1f, (float) animProgress * 1.5f);
                        float iH = iconSize * 0.4f * iProg;
                        quad.addRect(iconX + iconSize * 0.42f, iconY + iconSize * 0.15f, 2.5f * s, 2.5f * s, 1f * s,
                                new float[]{1f, 1f, 1f, a * animAlpha});
                        quad.addRect(iconX + iconSize * 0.42f, iconY + iconSize * 0.35f, 2.5f * s, iH, 1f * s,
                                new float[]{1f, 1f, 1f, a * animAlpha});
                        break;
                }

                // 标题文字
                text.draw(tc.title, islandX + 38f * s, contentY + 8f * s, 14f * s,
                        new float[]{1f, 1f, 1f, a * animAlpha}, sw, sh);

                // 描述文字
                text.draw(tc.description, islandX + 38f * s, contentY + 23f * s, 12f * s,
                        new float[]{0.8f, 0.8f, 0.8f, a * animAlpha}, sw, sh);

                contentY += ic.height;
            }
        } else {
            // 默认状态：客户端名称 + FPS + Ping
            String nameStr = "Rlin";
            StringBuilder defText = new StringBuilder();
            if (showFpsOpt != null && showFpsOpt.boolValue) defText.append(" | ").append(currentFps).append("fps");
            if (showPingOpt != null && showPingOpt.boolValue) defText.append(" | ").append(currentPing).append("ms");
            String def = defText.toString();

            float fontSize = 14f * s;
            float defSize = 12f * s;
            float nameW = text.measure(nameStr, fontSize);
            float defW = def.length() > 0 ? text.measure(def, defSize) : 0f;
            float totalTextW = nameW + defW;

            float startX = islandX + (islandW - totalTextW) / 2f;
            float textY = islandY + (islandH - fontSize) / 2f + 1f * s;

            // 客户端名（白色粗体）
            text.draw(nameStr, startX, textY, fontSize,
                    new float[]{1f, 1f, 1f, a}, sw, sh);
            // FPS/Ping（灰色）
            if (def.length() > 0) {
                text.draw(def, startX + nameW, textY + 1f * s, defSize,
                        new float[]{0.7f, 0.7f, 0.7f, a}, sw, sh);
            }
        }

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        quad.flush(sw, sh);
    }

    /** 指数平滑（参考 AnimationUtil.smooth）— 帧率无关，速度更快 */
    private float smoothAnim(float current, float target, float speed, float dt) {
        // 帧率无关的指数平滑：speed 越大越快
        // 每秒收敛比例 = 1 - e^(-speed * 10)，speed=0.5 时约 99..3%/秒
        float factor = 1f - (float) Math.exp(-speed * 10f * dt);
        return current + (target - current) * Math.max(factor, 0.1f); // 最小每帧 10%
    }

    // ---- 内部类 ----

    private static class IslandContent {
        final TimedContent timedContent;
        final float width;
        final float height;
        final int weight;

        IslandContent(TimedContent tc, float width, float height, int weight) {
            this.timedContent = tc;
            this.width = width;
            this.height = height;
            this.weight = weight;
        }
    }

    private static class TimedContent {
        final ContentType type;
        final String title;
        String description;
        final String moduleName;
        boolean enabled;
        long time;
        boolean exiting = false; // 是否正在退场动画中
        final EaseOutExpoAnim animation;
        final MSTimer timer;

        TimedContent(ContentType type, String title, String description, String moduleName, boolean enabled, long timeMs) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.time = timeMs;
            this.animation = new EaseOutExpoAnim(300);
            this.timer = new MSTimer();
            this.animation.setDirection(AnimDir.FORWARDS); // 入场动画
        }
    }

    /** 方向枚举 */
    private enum AnimDir {
        FORWARDS, BACKWARDS;

        AnimDir opposite() {
            return this == FORWARDS ? BACKWARDS : FORWARDS;
        }

        boolean isForward() {
            return this == FORWARDS;
        }
    }

    /** EaseOutExpo 动画实现（参考原版 EaseOutExpo + Animation）
     *  关键：反向播放时，要从当前进度倒退，而不是从0开始重新播放 */
    private static class EaseOutExpoAnim {
        private final int duration;
        private AnimDir direction = AnimDir.FORWARDS;
        private long startTime;
        // 反向时的起始进度（方向切换瞬间的进度值），用于从当前进度平滑倒退
        private float reverseStartProgress = 1f;
        private long reverseStartTime;

        EaseOutExpoAnim(int durationMs) {
            this.duration = durationMs;
            this.startTime = System.currentTimeMillis();
            this.reverseStartTime = this.startTime;
        }

        void reset() {
            this.startTime = System.currentTimeMillis();
            this.reverseStartTime = this.startTime;
            this.reverseStartProgress = 0f;
        }

        void setDirection(AnimDir dir) {
            if (this.direction != dir) {
                // 记录切换瞬间的当前进度
                float currentProgress = (float) getOutputRaw(System.currentTimeMillis() - startTime);
                this.reverseStartProgress = currentProgress;
                this.reverseStartTime = System.currentTimeMillis();
                this.direction = dir;
            }
        }

        boolean isForward() {
            return direction.isForward();
        }

        /** 原始进度（不考虑方向） */
        private double getOutputRaw(long elapsed) {
            double t = Math.min(1.0, (double) elapsed / duration);
            return easeOutExpo(t);
        }

        double getOutput(long elapsed, long totalTime) {
            if (direction.isForward()) {
                return getOutputRaw(elapsed);
            } else {
                // 反向：从切换瞬间的进度平滑过渡到0
                long reverseElapsed = System.currentTimeMillis() - reverseStartTime;
                double reverseT = Math.min(1.0, (double) reverseElapsed / duration);
                double eased = easeOutExpo(reverseT);
                // 从 reverseStartProgress 倒退到 0
                return reverseStartProgress * (1.0 - eased);
            }
        }

        private static double easeOutExpo(double x) {
            if (x >= 1.0) return 1.0;
            return 1 - Math.pow(2, -10 * x);
        }
    }

    /** 毫秒计时器 */
    private static class MSTimer {
        private long lastMS = System.currentTimeMillis();

        long getTime() {
            return System.currentTimeMillis() - lastMS;
        }

        void reset() {
            lastMS = System.currentTimeMillis();
        }
    }
}
