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
    private ModuleItem.ConfigOption showFpsOpt, showPingOpt, durationOpt, scaleOpt;

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

    public DynamicIslandRenderer(QuadBatch quad, TextRenderer text, BlurPipeline blur) {
        this.quad = quad;
        this.text = text;
        this.blur = blur;
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
        return durationOpt != null ? (long) (1000 + durationOpt.sliderValue * 4000) : 2000;
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
        contents.clear();
        for (int i = timedContents.size() - 1; i >= 0; i--) {
            TimedContent tc = timedContents.get(i);
            if (tc.timer.getTime() >= tc.time) {
                timedContents.remove(i);
                continue;
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

        // 平滑过渡
        currentWidth = smoothAnim(currentWidth, targetWidth, 0.2f, dt);
        currentHeight = smoothAnim(currentHeight, targetHeight, 0.2f, dt);

        // 位置居中
        targetX = (screenW - currentWidth) / 2f;
        renderX = smoothAnim(renderX, targetX, 0.2f, dt);

        // ---- 渲染 ----
        float islandX = renderX;
        float islandY = ISLAND_Y * s;
        float islandW = currentWidth;
        float islandH = currentHeight;
        float cornerR = CORNER_RADIUS * s;

        // 1. 背景模糊
        blur.captureAndBlurTransparent(() -> {
            quad.addRect(islandX, islandY, islandW, islandH, cornerR, new float[]{1f, 1f, 1f, 1f});
            quad.flush(sw, sh);
        });
        blur.drawGlow(new float[]{0f, 0f, 0f, 0.3f * a}, sw, sh);

        // 2. 黑色半透明背景
        quad.addRect(islandX, islandY, islandW, islandH, cornerR, new float[]{0f, 0f, 0f, 0.55f * a});
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
                // 更新动画
                long elapsed = tc.timer.getTime();
                long remain = tc.time - elapsed;
                // 最后 300ms 开始退场动画
                if (remain < 300) {
                    tc.animation.setDirection(AnimDir.BACKWARDS);
                } else {
                    tc.animation.setDirection(AnimDir.FORWARDS);
                }
                double animProgress = tc.animation.getOutput(elapsed, tc.time);
                float animAlpha = (float) (tc.animation.isForward() ? animProgress : animProgress);

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

    /** 指数平滑（参考 AnimationUtil.smooth） */
    private float smoothAnim(float current, float target, float speed, float dt) {
        float diff = Math.abs(target - current) * speed;
        float smoothing = Math.max(diff * (dt * 60f / 16f), 0.15f);
        if (current > target) {
            current = Math.max(current - smoothing, target);
        } else if (current < target) {
            current = Math.min(current + smoothing, target);
        } else {
            current = target;
        }
        return current;
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

    /** EaseOutExpo 动画实现（参考原版 EaseOutExpo + Animation） */
    private static class EaseOutExpoAnim {
        private final int duration;
        private AnimDir direction = AnimDir.FORWARDS;
        private long startTime;

        EaseOutExpoAnim(int durationMs) {
            this.duration = durationMs;
            this.startTime = System.currentTimeMillis();
        }

        void reset() {
            this.startTime = System.currentTimeMillis();
        }

        void setDirection(AnimDir dir) {
            if (this.direction != dir) {
                this.direction = dir;
                // 反向时调整起始时间
                long elapsed = System.currentTimeMillis() - startTime;
                long remaining = duration - Math.min(duration, elapsed);
                this.startTime = System.currentTimeMillis() - remaining;
            }
        }

        boolean isForward() {
            return direction.isForward();
        }

        double getOutput(long elapsed, long totalTime) {
            double t = Math.min(1.0, (double) elapsed / duration);
            if (direction.isForward()) {
                return easeOutExpo(t);
            } else {
                return 1.0 - easeOutExpo(t);
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
