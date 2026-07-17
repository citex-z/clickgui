package com.visiolab.clickgui.gl;

import android.opengl.GLES20;

import com.visiolab.clickgui.model.Category;
import com.visiolab.clickgui.model.ModuleItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 灵动岛渲染器——仿照 Apple Dynamic Island + Jonqwq 的 MC 版本实现。
 * MIT 协议，原作者 @Jon_awa
 *
 * 核心行为（1:1 还原原版）：
 * - 默认状态：屏幕顶部居中显示一个小药丸，包含客户端名 + FPS + Ping
 * - 有内容时：平滑展开为更大的圆角矩形，显示内容
 * - 内容类型：MODULE（模块开关动画）、SUCCESS、WARNING、INFO
 * - MODULE 动画：跟随模块实际开关状态，不是入场/退场
 * - SUCCESS/WARNING/INFO 动画：800ms EaseOutExpo，在 time-200 时开始退场
 * - 内容移除：timer 到期即移除（不是等退场动画完成）
 * - 平滑过渡：AnimationUtil.smooth（线性比例平滑）
 */
public class DynamicIslandRenderer {

    private final QuadBatch quad;
    private final TextRenderer text;
    private final BlurPipeline blur;

    // 灵动岛状态
    private float renderX = 0f;
    private float currentWidth = 0f;
    private float currentHeight = 0f;

    // 原版常量
    private static final float ISLAND_Y = 15f;       // 距顶部偏移（原版固定值）
    private static final float CORNER_RADIUS = 12f; // 圆角（调大）
    private static final float CONTENT_WIDTH_BASE = 38f; // 图标区+padding（调大）
    private static final float CONTENT_HEIGHT = 44f;  // 单条内容高度（调大）
    private static final float DEFAULT_TEXT_PAD = 28f; // 默认状态左右内边距（调大）

    // 编辑模式偏移量
    private float offsetX = 0, offsetY = 0;
    // 编辑模式边界框
    private float[] editBounds = null;

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
    private boolean fpsFromGame = false; // 是否使用游戏侧提供的FPS
    private boolean pingFromGame = false;

    /** 设置 FPS（供 native 层调用） */
    public void setFps(int fps) {
        this.currentFps = fps;
        this.fpsFromGame = true;
    }

    /** 设置 Ping（供 native 层调用） */
    public void setPing(int ping) {
        this.currentPing = ping;
        this.pingFromGame = true;
    }
    private long lastFpsUpdate = 0;
    private int frameCount = 0;

    // 原版 deltaTime（毫秒），用于 AnimationUtil.smooth
    private long animDelta = 16L;

    public enum ContentType {
        MODULE,
        SUCCESS,
        WARNING,
        INFO,
        PROGRESS
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
        // 原版 addContent(Module module)：如果已有同模块内容，刷新时间
        for (TimedContent tc : timedContents) {
            if (tc.moduleName != null && tc.moduleName.equals(moduleName)) {
                tc.time = tc.timer.getTime() + 2000; // 原版固定 2000ms
                tc.enabled = enabled;
                return;
            }
        }
        timedContents.add(new TimedContent(ContentType.MODULE, "Module Toggled",
                moduleName + (enabled ? " has been §2Enabled!" : " has been §4Disabled!"),
                moduleName, enabled, 2000)); // 原版固定 2000ms
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

    /** 添加进度条内容（如 Scaffold 搭路进度） */
    public void addProgress(String title, String description, float progress) {
        // 如果已有同标题的进度内容，更新进度
        for (TimedContent tc : timedContents) {
            if (tc.type == ContentType.PROGRESS && tc.title.equals(title)) {
                tc.description = description;
                tc.progress = progress;
                // 更新颜色
                tc.iconColor = getProgressColor(progress);
                return;
            }
        }
        TimedContent tc = new TimedContent(ContentType.PROGRESS, title, description, null, true, 99999999);
        tc.progress = progress;
        tc.iconColor = getProgressColor(progress);
        timedContents.add(tc);
    }

    /** 移除进度条内容（触发淡出动画，不立即移除） */
    public void removeProgress(String title) {
        for (int i = timedContents.size() - 1; i >= 0; i--) {
            TimedContent tc = timedContents.get(i);
            if (tc.type == ContentType.PROGRESS && tc.title.equals(title)) {
                // 触发淡出动画，update 中检测到动画完成后自动移除
                tc.animation.setDirection(Direction.BACKWARDS);
                return;
            }
        }
    }

    /** 根据进度返回颜色（INFO 蓝色） */
    private float[] getProgressColor(float progress) {
        // INFO 蓝色 Color(48, 117, 147)
        return new float[]{48f / 255f, 117f / 255f, 147f / 255f, 1f};
    }

    private long getDurationMs() {
        return durationOpt != null ? (long) (2000 + durationOpt.sliderValue * 6000) : 3000;
    }

    /** 每帧更新和渲染 */
    /** 设置编辑偏移量 */
    public void setEditOffset(float x, float y) { this.offsetX = x; this.offsetY = y; }

    /** 获取编辑模式边界框 */
    public float[] getEditBounds() { return editBounds; }

    public void update(float dt, float screenW, float screenH, float uiScale) {
        if (islandModule == null) return;
        float a = Math.max(0f, Math.min(1f, islandModule.hudAnim.get()));
        if (a <= 0.01f) return;

        int sw = (int) screenW;
        int sh = (int) screenH;

        // 更新 deltaTime（毫秒），供 smooth 使用
        animDelta = Math.max(1L, (long) (dt * 1000));

        // FPS 计数（仅当游戏侧未提供时使用 UI 侧模拟值）
        frameCount++;
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastFpsUpdate > 1000) {
            if (!fpsFromGame) {
                currentFps = (int) (frameCount * 1000f / (nowMs - lastFpsUpdate));
            }
            if (!pingFromGame) {
                currentPing = 10 + (int) (Math.random() * 15);
            }
            frameCount = 0;
            lastFpsUpdate = nowMs;
        }

        float scale = scaleOpt != null ? (0.7f + scaleOpt.sliderValue * 0.8f) : 1f;
        float s = uiScale * scale;

        // ---- 处理 timedContents（原版逻辑 + 退场淡出）----
        // PROGRESS 类型始终置顶：排序时 PROGRESS 排在最前面
        contents.clear();
        boolean timedEmpty = true;
        // 先收集 PROGRESS，再收集其他（确保进度条在最上方渲染）
        java.util.List<TimedContent> ordered = new java.util.ArrayList<>();
        for (TimedContent tc : timedContents) {
            if (tc.type == ContentType.PROGRESS) ordered.add(tc);
        }
        for (TimedContent tc : timedContents) {
            if (tc.type != ContentType.PROGRESS) ordered.add(tc);
        }
        // 正序构建 contents，同时移除过期的
        java.util.List<TimedContent> toRemove = new java.util.ArrayList<>();
        for (TimedContent tc : ordered) {
            long elapsed = tc.timer.getTime();
            int exitDuration = 300;
            long totalLife = tc.time + exitDuration;
            if (elapsed > totalLife) {
                toRemove.add(tc);
                continue;
            }
            timedEmpty = false;

            // 更新动画方向
            switch (tc.type) {
                case MODULE:
                    if (tc.enabled) {
                        tc.animation.setDirection(Direction.FORWARDS);
                    } else {
                        tc.animation.setDirection(Direction.BACKWARDS);
                    }
                    break;
                case SUCCESS:
                case WARNING:
                case INFO:
                    if (elapsed >= tc.time - 200) {
                        tc.animation.setDirection(Direction.BACKWARDS);
                    }
                    break;
                case PROGRESS:
                    // 如果正在淡出且动画完成，移除
                    if (tc.animation.getDirection() == Direction.BACKWARDS && tc.animation.getProgress() <= 0.01f) {
                        toRemove.add(tc);
                        continue;
                    }
                    // 否则保持当前方向（FORWARDS 或由 removeProgress 设为 BACKWARDS）
                    break;
            }

            // 计算内容尺寸
            float titleW = text.measure(tc.title, 14f * s);
            float descW = text.measure(tc.description, 12f * s);
            float textW = Math.max(titleW, descW);
            float contentW = CONTENT_WIDTH_BASE * s + textW + 8f * s;
            float contentH = (tc.type == ContentType.PROGRESS ? 56f : CONTENT_HEIGHT) * s;
            contents.add(new IslandContent(tc, contentW, contentH));
        }
        timedContents.removeAll(toRemove);

        // ---- 计算目标尺寸（原版逻辑）----
        float targetWidth, targetHeight;
        if (contents.isEmpty() && timedEmpty) {
            // 默认状态
            String nameStr = "Rlin";
            StringBuilder defaultText = new StringBuilder();
            if (showFpsOpt != null && showFpsOpt.boolValue) defaultText.append(" | ").append(currentFps).append("fps");
            if (showPingOpt != null && showPingOpt.boolValue) defaultText.append(" | ").append(currentPing).append("ms");
            float nameW = text.measure(nameStr, 14f * s);
            float defW = defaultText.length() > 0 ? text.measure(defaultText.toString(), 12f * s) : 0f;
            // 轮椅图标宽度
            float defIconW = text.getAtlas().isIconFontLoaded()
                    ? text.measureIcon('f', 16f * s) + 4f * s : 0f;
            // 不再双重缩放：文字已经是 *s 后的值，padding 只加一次
            targetWidth = nameW + defW + defIconW + DEFAULT_TEXT_PAD * s;
            targetHeight = 30f * s; // 默认药丸高度（调大）
        } else {
            // 有内容：取最大宽度 + 总高度（退出时高度逐渐缩小，上方平滑跟随）
            float maxW = 0f;
            float totalH = 0f;
            for (IslandContent ic : contents) {
                TimedContent tc = ic.timedContent;
                if (ic.width > maxW) maxW = ic.width;
                // 退出淡出时高度逐渐缩小
                long elapsed = tc.timer.getTime();
                float hFactor = 1f;
                if (elapsed > tc.time) {
                    float exitProgress = (elapsed - tc.time) / 300f;
                    hFactor = Math.max(0f, 1f - exitProgress);
                }
                totalH += ic.height * hFactor;
            }
            targetWidth = maxW;
            targetHeight = totalH;
        }

        // 平滑过渡（原版 AnimationUtil.smooth，speed=0.2f）
        currentWidth = smooth(currentWidth, targetWidth, 0.2f);
        currentHeight = smooth(currentHeight, targetHeight, 0.2f);

        // 位置居中（原版 AnimationUtil.smooth）+ 编辑偏移
        float targetX = (screenW - currentWidth) / 2f + offsetX;
        renderX = smooth(renderX, targetX, 0.2f);

        // ---- 渲染（原版三层结构）----
        float islandX = renderX;
        float islandY = ISLAND_Y * s + offsetY;
        float islandW = currentWidth;
        float islandH = currentHeight;
        float cornerR = CORNER_RADIUS * s;

        // 1. 背景模糊（可选）
        boolean blurBg = blurBgOpt != null && blurBgOpt.boolValue;
        if (blurBg) {
            blur.captureAndBlur(worldSceneDrawer);
            blur.drawGlassPanel(islandX, islandY, islandW, islandH, cornerR,
                    new float[]{0.02f, 0.02f, 0.02f, 0.5f}, a, sw, sh);
        }

        // 2. 边缘模糊 (bloom)：原版 addBlur + addBloom
        blur.captureAndBlurTransparent(() -> {
            quad.addRect(islandX, islandY, islandW, islandH, cornerR, new float[]{0f, 0f, 0f, 1f});
            quad.flush(sw, sh);
        });
        blur.drawGlow(new float[]{0f, 0f, 0f, 0.5f * a}, sw, sh);
        blur.captureAndBlurTransparentTight(() -> {
            quad.addRect(islandX, islandY, islandW, islandH, cornerR, new float[]{0f, 0f, 0f, 1f});
            quad.flush(sw, sh);
        });
        blur.drawGlow(new float[]{0f, 0f, 0f, 0.3f * a}, sw, sh);

        // 3. 黑色半透明背景（原版 new Color(0,0,0,130) ≈ 0.51 alpha）
        quad.addRect(islandX, islandY, islandW, islandH, cornerR, new float[]{0f, 0f, 0f, 0.51f * a});
        quad.flush(sw, sh);

        // 4. 裁剪区域
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor((int) islandX, (int) (screenH - islandY - islandH),
                (int) islandW, (int) islandH);

        // 5. 内容渲染
        if (contents.isEmpty() && timedEmpty) {
            // ---- 默认状态 ----
            String nameStr = "Rlin";
            StringBuilder defText = new StringBuilder();
            if (showFpsOpt != null && showFpsOpt.boolValue) defText.append(" | ").append(currentFps).append("fps");
            if (showPingOpt != null && showPingOpt.boolValue) defText.append(" | ").append(currentPing).append("ms");
            String def = defText.toString();

            // 原版：图标在 renderX，名称在 renderX+22，默认文字在 renderX+39
            float fontSize = 14f * s;
            float defSize = 12f * s;
            float nameW = text.measure(nameStr, fontSize);
            float defW = def.length() > 0 ? text.measure(def, defSize) : 0f;

            // 轮椅图标宽度
            float iconSize = 16f * s;
            float iconPad = 4f * s;
            float iconW = text.getAtlas().isIconFontLoaded()
                    ? text.measureIcon('f', iconSize) : 0f;

            // 居中：图标 + 名称 + 默认文字
            float totalW = iconW + iconPad + nameW + defW;
            float startX = islandX + (islandW - totalW) / 2f;
            // 垂直居中
            float textY = islandY + (islandH - fontSize) / 2f - 1f * s;

            // 绘制轮椅图标
            if (text.getAtlas().isIconFontLoaded()) {
                text.drawIcon('f', startX, islandY + (islandH - iconSize) / 2f,
                        iconSize, new float[]{0.7f, 0.7f, 0.7f, a}, sw, sh);
            }
            float nameStartX = startX + iconW + iconPad;
            text.drawBold(nameStr, nameStartX, textY, fontSize,
                    new float[]{1f, 1f, 1f, a}, sw, sh);
            if (def.length() > 0) {
                text.draw(def, nameStartX + nameW, textY, defSize,
                        new float[]{0.7f, 0.7f, 0.7f, a}, sw, sh);
            }
        } else {
            // ---- 有内容状态 ----
            // 原版：每条内容用 translate(renderX, renderY, 0) 定位，内部坐标从 (0,0) 开始
            float contentY = islandY;
            for (IslandContent ic : contents) {
                TimedContent tc = ic.timedContent;
                double anim = tc.animation.getOutput();
                float animF = (float) anim;

                // 退场淡出：timer 到期后 300ms 内 alpha 从 1→0
                long elapsed = tc.timer.getTime();
                float fadeAlpha = 1f;
                if (elapsed > tc.time) {
                    float exitProgress = (elapsed - tc.time) / 300f;
                    fadeAlpha = Math.max(0f, 1f - exitProgress);
                }
                float contentAlpha = a * fadeAlpha;

                // 内部坐标基准（相当于原版的 translate 偏移）
                float baseX = islandX;
                float baseY = contentY;

                // ---- 图标渲染（原版坐标）----
                // 原版图标在 (7, 8) 大小 24×24
                float iconX = baseX + 7f * s;
                float iconY = baseY + 8f * s;
                float iconSize = 24f * s;
                float iconR = 7f * s;

                switch (tc.type) {
                    case MODULE: {
                        // 原版 MODULE 开关动画（iOS toggle switch 风格）
                        // 外圈：(6, 13, 26, 14, 6.5, DARK_GRAY)
                        float trackX = baseX + 6f * s;
                        float trackY = baseY + 13f * s;
                        float trackW = 26f * s;
                        float trackH = 14f * s;
                        float trackR = 6.5f * s;
                        quad.addRect(trackX, trackY, trackW, trackH, trackR,
                                new float[]{0.25f, 0.25f, 0.25f, contentAlpha}); // DARK_GRAY

                        // 内圈颜色：从 gray(128,128,128) → blue(48,117,147)
                        // 原版: Color((int)(128-80*a), (int)(128-11*a), (int)(128+19*a), 255)
                        // animF=0: rgb(128,128,128) 灰色  animF=1: rgb(48,117,147) 蓝色
                        float r = (128f - 80f * animF) / 255f;
                        float g = (128f - 11f * animF) / 255f;
                        float b = (128f + 19f * animF) / 255f;
                        float innerX = baseX + 7f * s;
                        float innerY = baseY + 14f * s;
                        float innerW = 24f * s;
                        float innerH = 12f * s;
                        float innerR = 6f * s;
                        quad.addRect(innerX, innerY, innerW, innerH, innerR,
                                new float[]{r, g, b, contentAlpha});

                        // 开关旋钮：(8 + 12*animation, 15, 10, 10, 4.5, DARK_GRAY)
                        float knobX = baseX + (8f + 12f * animF) * s;
                        float knobY = baseY + 15f * s;
                        float knobSize = 10f * s;
                        float knobR = 4.5f * s;
                        quad.addRect(knobX, knobY, knobSize, knobSize, knobR,
                                new float[]{0.25f, 0.25f, 0.25f, contentAlpha});
                        break;
                    }
                    case SUCCESS: {
                        // 绿色圆形 + 白色对号动画
                        // Color(95, 143, 80, 200)
                        quad.addRect(iconX, iconY, iconSize, iconSize, iconR,
                                new float[]{95f / 255f, 143f / 255f, 80f / 255f, (200f / 255f) * contentAlpha});
                        // 对号动画：两条线段组成 √ 形状
                        // 原版用 stencil 裁剪，这里用渐进绘制简化
                        // 第一段（短）：从左下到中下
                        float p1 = Math.min(1f, animF * 2f); // 前半段
                        if (p1 > 0) {
                            float x1 = iconX + 5f * s;
                            float y1 = iconY + 12f * s;
                            float x2 = iconX + 10f * s;
                            float y2 = iconY + 17f * s;
                            // 简化：画一个细矩形旋转近似对号第一段
                            float dx = x2 - x1;
                            float dy = y2 - y1;
                            float len = (float) Math.sqrt(dx * dx + dy * dy) * p1;
                            float angle = (float) Math.atan2(dy, dx);
                            // 用小矩形模拟线段
                            quad.addRect(x1, y1 - 1f * s, len, 2.5f * s, 0f,
                                    new float[]{1f, 1f, 1f, contentAlpha});
                        }
                        // 第二段（长）：从中下到右上
                        if (animF > 0.5f) {
                            float p2 = (animF - 0.5f) * 2f;
                            float x1 = iconX + 10f * s;
                            float y1 = iconY + 17f * s;
                            float x2 = iconX + 19f * s;
                            float y2 = iconY + 8f * s;
                            float dx = x2 - x1;
                            float dy = y2 - y1;
                            float len = (float) Math.sqrt(dx * dx + dy * dy) * p2;
                            quad.addRect(x1, y1 - 1f * s, len, 2.5f * s, 0f,
                                    new float[]{1f, 1f, 1f, contentAlpha});
                        }
                        break;
                    }
                    case WARNING: {
                        // 红色圆形 + 白色感叹号动画
                        // Color(143, 80, 80, 200)
                        quad.addRect(iconX, iconY, iconSize, iconSize, iconR,
                                new float[]{143f / 255f, 80f / 255f, 80f / 255f, (200f / 255f) * contentAlpha});
                        // 感叹号竖线（从上往下生长）
                        float barH = 12f * s * animF;
                        if (barH > 0) {
                            quad.addRect(iconX + 11f * s, iconY + 3f * s, 2f * s, barH, 1f * s,
                                    new float[]{1f, 1f, 1f, contentAlpha});
                        }
                        // 感叹号底部圆点（动画完成后出现）
                        if (animF > 0.7f) {
                            float dotA = (animF - 0.7f) / 0.3f;
                            quad.addRect(iconX + 10.5f * s, iconY + 17f * s, 3f * s, 3f * s, 1.5f * s,
                                    new float[]{1f, 1f, 1f, contentAlpha * dotA});
                        }
                        break;
                    }
                    case INFO: {
                        // 蓝色圆形 + 白色十字/加号动画
                        // Color(48, 117, 147, 200)
                        quad.addRect(iconX, iconY, iconSize, iconSize, iconR,
                                new float[]{48f / 255f, 117f / 255f, 147f / 255f, (200f / 255f) * contentAlpha});
                        // 竖线（从中心向上下扩展）
                        float vBarH = 14f * s * animF;
                        if (vBarH > 0) {
                            quad.addRect(iconX + 11f * s, iconY + 5f * s, 2f * s, vBarH, 1f * s,
                                    new float[]{1f, 1f, 1f, contentAlpha});
                        }
                        // 横线（从中心向左右扩展）
                        float hBarW = 14f * s * animF;
                        if (hBarW > 0) {
                            quad.addRect(iconX + 5f * s, iconY + 11f * s, hBarW, 2f * s, 1f * s,
                                    new float[]{1f, 1f, 1f, contentAlpha});
                        }
                        break;
                    }
                    case PROGRESS: {
                        // 图标：蓝色圆形背景 + 白色感叹号（居中对齐）
                        float pb_r = 48f / 255f, pb_g = 117f / 255f, pb_b = 147f / 255f;
                        quad.addRect(iconX, iconY, iconSize, iconSize, iconR,
                                new float[]{pb_r, pb_g, pb_b, (200f / 255f) * contentAlpha});

                        // 感叹号竖线（居中对齐，从上往下生长）
                        float exBarW = 2f * s;
                        float exBarH = 12f * s * animF;
                        float exBarX = iconX + (iconSize - exBarW) / 2f; // 水平居中
                        float exBarY = iconY + 3f * s;
                        if (exBarH > 0) {
                            quad.addRect(exBarX, exBarY, exBarW, exBarH, 1f * s,
                                    new float[]{1f, 1f, 1f, contentAlpha});
                        }
                        // 感叹号底部圆点（居中）
                        if (animF > 0.7f) {
                            float dotA = (animF - 0.7f) / 0.3f;
                            float dotSize = 3f * s;
                            float dotX = iconX + (iconSize - dotSize) / 2f; // 水平居中
                            quad.addRect(dotX, iconY + 17f * s, dotSize, dotSize, 1.5f * s,
                                    new float[]{1f, 1f, 1f, contentAlpha * dotA});
                        }

                        // 进度条：贴在内容区底部，蓝色填充（加粗加大）
                        float barPad = 5f * s;
                        float barX = baseX + barPad;
                        float barH = 10f * s; // 加粗到 10f
                        float barY = baseY + ic.height - barPad - barH; // 贴底
                        float barW = ic.width - barPad * 2f;
                        float barR = 5f * s; // 圆角同步增大
                        // 背景轨道
                        quad.addRect(barX, barY, barW, barH, barR,
                                new float[]{0.15f, 0.15f, 0.15f, contentAlpha * 0.8f});
                        // 填充
                        float fillW = barW * tc.progress;
                        if (fillW > 0) {
                            quad.addRect(barX, barY, fillW, barH, barR,
                                    new float[]{pb_r, pb_g, pb_b, contentAlpha});
                        }
                        break;
                    }
                }

                // ---- 标题文字 ----
                float titleFontSize = 14f * s;
                float titleX = baseX + 38f * s;
                text.drawBold(tc.title, titleX, baseY + 9f * s, titleFontSize,
                        new float[]{1f, 1f, 1f, contentAlpha}, sw, sh);

                // ---- 描述文字（原版 38, 24, regular 18）----
                // 原版用 MC 颜色代码: §2=DarkGreen(0,170,0) §4=DarkRed(170,0,0)
                if (tc.type == ContentType.MODULE) {
                    String prefix = tc.moduleName + " has been ";
                    String status = tc.enabled ? "Enabled!" : "Disabled!";
                    // MC §2 = rgb(0,170,0), §4 = rgb(170,0,0)
                    float[] statusColor = tc.enabled
                            ? new float[]{0f, 170f / 255f, 0f, contentAlpha}      // §2 Dark Green
                            : new float[]{170f / 255f, 0f, 0f, contentAlpha};     // §4 Dark Red
                    float prefixW = text.measure(prefix, 12f * s);
                    text.draw(prefix, baseX + 38f * s, baseY + 24f * s, 12f * s,
                            new float[]{0.85f, 0.85f, 0.85f, contentAlpha}, sw, sh);
                    text.draw(status, baseX + 38f * s + prefixW, baseY + 24f * s, 12f * s,
                            statusColor, sw, sh);
                } else if (tc.type == ContentType.PROGRESS) {
                    // PROGRESS: 标题在第一行，描述在第二行（包含进度信息）
                    text.draw(tc.description, baseX + 38f * s, baseY + 24f * s, 12f * s,
                            new float[]{0.7f, 0.7f, 0.7f, contentAlpha}, sw, sh);
                } else {
                    text.draw(tc.description, baseX + 38f * s, baseY + 24f * s, 12f * s,
                            new float[]{0.85f, 0.85f, 0.85f, contentAlpha}, sw, sh);
                }

                // 退出时高度逐渐缩小，上方内容平滑跟随
                contentY += ic.height * fadeAlpha;
            }
        }

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        quad.flush(sw, sh);

        // 记录编辑模式边界框
        editBounds = new float[]{islandX, islandY, islandW, islandH};
    }

    // ---- 原版 AnimationUtil.smooth 精确还原 ----

    /**
     * 原版 AnimationUtil.smooth：
     * speed = |target - current| * speed
     * smoothing = max(speed * (deltaTime / 16), 0.15)
     * if diff > speed: current = max(current - smoothing, target)
     * else if diff < -speed: current = min(current + smoothing, target)
     * else: current = target
     */
    private float smooth(float current, float target, float speed) {
        long deltaTime = animDelta;
        if (deltaTime < 1L) deltaTime = 1L;

        speed = Math.abs(target - current) * speed;
        float difference = current - target;
        float smoothing = Math.max(speed * (deltaTime / 16F), 0.15F);

        if (difference > speed) {
            current = Math.max(current - smoothing, target);
        } else if (difference < -speed) {
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

        IslandContent(TimedContent tc, float width, float height) {
            this.timedContent = tc;
            this.width = width;
            this.height = height;
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
        float progress = 0f;        // PROGRESS 类型专用
        float[] iconColor = null;   // PROGRESS 类型专用

        TimedContent(ContentType type, String title, String description, String moduleName, boolean enabled, long timeMs) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.time = timeMs;
            // 原版：MODULE 用 300ms，SUCCESS/WARNING/INFO 用 800ms
            int duration = (type == ContentType.MODULE) ? 300 : 800;
            this.animation = new EaseOutExpoAnim(duration);
            this.timer = new MSTimer();
        }
    }

    /** 方向枚举（原版 Direction） */
    private enum Direction {
        FORWARDS, BACKWARDS;

        Direction opposite() {
            return this == FORWARDS ? BACKWARDS : FORWARDS;
        }

        boolean forwards() {
            return this == FORWARDS;
        }
    }

    /**
     * EaseOutExpo 动画实现（1:1 还原原版 Animation + EaseOutExpo）
     *
     * 原版关键逻辑：
     * - setDirection(dir): 如果方向改变，调整 timer 使其从剩余时间开始
     *   timerUtil.setTime(now - (duration - min(duration, elapsed)))
     * - getOutput() FORWARDS: equation(t) * endPoint, t = elapsed / duration
     * - getOutput() BACKWARDS: (1 - equation(t)) * endPoint
     * - easeOutExpo(x) = x >= 1 ? 1 : 1 - 2^(-10*x)
     */
    private static class EaseOutExpoAnim {
        private final int duration;
        private final double endPoint = 1.0;
        private Direction direction = Direction.FORWARDS;
        private long lastMS; // AnimTimeUtil.lastMS

        EaseOutExpoAnim(int durationMs) {
            this.duration = durationMs;
            this.lastMS = System.currentTimeMillis();
        }

        /** 原版 reset() */
        void reset() {
            this.lastMS = System.currentTimeMillis();
        }

        /**
         * 原版 setDirection(Direction):
         * 如果方向改变：
         *   timerUtil.setTime(now - (duration - min(duration, getTime())))
         * 这使得反向动画从"当前进度的对称点"开始
         */
        void setDirection(Direction dir) {
            if (this.direction != dir) {
                this.direction = dir;
                long elapsed = getTime();
                long newTime = System.currentTimeMillis() - ((long) duration - Math.min(duration, elapsed));
                this.lastMS = newTime;
            }
        }

        /** 原版 AnimTimeUtil.getTime() */
        private long getTime() {
            return System.currentTimeMillis() - lastMS;
        }

        /** 原版 AnimTimeUtil.hasTimeElapsed(duration) */
        private boolean isDone() {
            return getTime() > duration;
        }

        /**
         * 原版 Animation.getOutput():
         * FORWARDS: if done return endPoint; else equation(t) * endPoint
         * BACKWARDS: if done return 0; else (1 - equation(t)) * endPoint
         * t = elapsed / duration
         */
        /** 获取当前动画值（0~1） */
        double getOutput() {
            if (direction.forwards()) {
                if (isDone()) return endPoint;
                double t = (double) getTime() / (double) duration;
                return easeOutExpo(t) * endPoint;
            } else {
                if (isDone()) return 0.0;
                double t = (double) getTime() / (double) duration;
                return (1.0 - easeOutExpo(t)) * endPoint;
            }
        }

        Direction getDirection() { return direction; }

        float getProgress() { return (float) getOutput(); }

        /** 原版 EaseOutExpo.getEquation(x) */
        private static double easeOutExpo(double x) {
            if (x >= 1.0) return 1.0;
            return 1 - Math.pow(2, -10 * x);
        }
    }

    /** 毫秒计时器（原版 MSTimer） */
    private static class MSTimer {
        private long lastMS = System.currentTimeMillis();

        /** 原版 hasTimePassed(time) */
        boolean hasTimePassed(long time) {
            return System.currentTimeMillis() - lastMS > time;
        }

        /** 原版 getPassedTime() / getTime() */
        long getTime() {
            return System.currentTimeMillis() - lastMS;
        }

        void reset() {
            lastMS = System.currentTimeMillis();
        }
    }
}
