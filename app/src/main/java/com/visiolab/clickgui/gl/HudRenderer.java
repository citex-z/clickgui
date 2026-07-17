package com.visiolab.clickgui.gl;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.visiolab.clickgui.anim.Anim;
import com.visiolab.clickgui.model.Category;
import com.visiolab.clickgui.model.ModuleItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 精简版 GLSurfaceView.Renderer，只负责渲染 HUD 元素。
 *
 * 这是混合架构（OverlayManager）中的第 1 层：一个永远 FLAG_NOT_TOUCHABLE 的
 * GLSurfaceView，只画不拦截触摸。ClickGUI 面板与悬浮球由独立的窗口承载，
 * 因此本渲染器不包含：
 *   - ClickGUI 面板（侧栏 / 卡片网格 / 配置页 / 面板开合动画）
 *   - 悬浮球
 *   - 登录界面
 *   - 触摸事件处理
 *   - ScreenCapture / 高斯模糊背景（截屏底图）
 *
 * 每帧只渲染以下 HUD 元素：
 *   - 水印 drawWatermark()
 *   - ArrayList HUD drawArrayListHud()
 *   - 灵动岛 dynamicIsland.update()
 *   - 快捷键（已由原生 View FloatingHotkeyView 接管）
 *   - 按键显示 drawKeystrokes()
 *   - Toast drawToasts()
 *
 * 模块状态由 categories 维护；外部（ClickGUI 面板）切换模块时通过
 * {@link #onModuleChanged(String, boolean)} 同步状态并触发视觉反馈。
 */
public class HudRenderer implements GLSurfaceView.Renderer {

    // ================================================================
    //  颜色常量
    // ================================================================

    private static final float[] WORLD_BG = {0f, 0f, 0f, 0f}; // 透明背景（透出游戏画面）
    private static final float[] KNOB_COLOR = {1f, 1f, 1f, 1f}; // 白色拖钮/滑块圆点

    // 通知左侧彩色竖线：启用=绿色 #039855，禁用=红色 #D92020
    private static final float[] TOAST_GREEN = {0.012f, 0.596f, 0.333f, 1f}; // #039855 启用
    private static final float[] TOAST_RED   = {0.851f, 0.125f, 0.125f, 1f}; // #D92020 禁用

    // ---- 主题色（运行时由 applyThemeColors() 更新） ----
    private float[] ACCENT_1 = {0.50f, 0.50f, 0.50f, 1f}; // #808080 灰色
    private float[] ACCENT_2 = {0.40f, 0.40f, 0.40f, 1f}; // #666666 深灰
    private int selectedThemeIndex = 0;

    /** 每个主题的节点颜色列表（按色相顺序）。Custom 主题运行时由 customThemeColors 提供。 */
    private static final float[][][] THEME_COLORS = {
        // 0: Blue (真实南方客户端 #1452CC)
        new float[][]{
            hexToColor("#1452CC"), hexToColor("#1A5FE0"), hexToColor("#2A6FEA"), hexToColor("#3A7FF4"),
            hexToColor("#1452CC"), hexToColor("#1042B8"), hexToColor("#0C38A0"), hexToColor("#082E88")
        },
        // 1: Spaced（青蓝系渐变）
        new float[][]{
            hexToColor("#4DFFF0"), hexToColor("#3AE8E0"), hexToColor("#28D0D0"), hexToColor("#16B8C0"),
            hexToColor("#10A0B0"), hexToColor("#0A88A0"), hexToColor("#047090"), hexToColor("#005880")
        },
        // 2: Trans（跨性别旗：浅蓝→粉红→白→粉红→浅蓝）
        new float[][]{
            hexToColor("#5BCEFA"), hexToColor("#F5A9B8"), hexToColor("#FFFFFF"), hexToColor("#F5A9B8"),
            hexToColor("#5BCEFA"), hexToColor("#4BBDE8"), hexToColor("#48B5E0"), hexToColor("#45ADD8")
        },
        // 3: Rainbow（全色相：红→橙→黄→绿→蓝→紫→青→粉）
        new float[][]{
            hexToColor("#FF4444"), hexToColor("#FF8844"), hexToColor("#FFCC44"), hexToColor("#44FF44"),
            hexToColor("#4488FF"), hexToColor("#8844FF"), hexToColor("#44FFCC"), hexToColor("#FF44CC")
        },
        // 4: Watermelon（红绿对比）
        new float[][]{
            hexToColor("#FF4444"), hexToColor("#FF6666"), hexToColor("#FF8888"), hexToColor("#FFFFFF"),
            hexToColor("#FF8888"), hexToColor("#FF6666"), hexToColor("#44FF44"), hexToColor("#22CC22")
        },
        // 5: Sunset（橙红渐变）
        new float[][]{
            hexToColor("#FF8844"), hexToColor("#FF7733"), hexToColor("#FF6622"), hexToColor("#FF5511"),
            hexToColor("#FF4444"), hexToColor("#EE3333"), hexToColor("#DD2222"), hexToColor("#CC1111")
        },
        // 6: Poison（纯绿毒系）
        new float[][]{
            hexToColor("#33FF66"), hexToColor("#22EE55"), hexToColor("#11DD44"), hexToColor("#00CC33"),
            hexToColor("#00BB22"), hexToColor("#00AA11"), hexToColor("#009900"), hexToColor("#008800")
        },
        // 7: Custom（用户自定义，默认为 ACCENT_1/ACCENT_2，运行时动态更新）
        null
    };
    // Custom 主题的运行时自定义颜色（8个渐变节点）
    private static float[][] customThemeColors = new float[][]{
        hexToColor("#68D5EE"), hexToColor("#8CC7E0"), hexToColor("#AAB8D0"), hexToColor("#C0D0E0"),
        hexToColor("#D0E0F0"), hexToColor("#E0F0FF"), hexToColor("#B0C0D8"), hexToColor("#98A8C8")
    };

    /** 从当前主题取两个端色用于强调色渐变 */
    private float[] themeAccent1() {
        float[][] nodes = THEME_COLORS[selectedThemeIndex];
        if (nodes == null) nodes = customThemeColors;
        return nodes[0];
    }
    private float[] themeAccent2() {
        float[][] nodes = THEME_COLORS[selectedThemeIndex];
        if (nodes == null) nodes = customThemeColors;
        return nodes[Math.min(1, nodes.length - 1)];
    }

    // ================================================================
    //  模块状态管理
    // ================================================================

    private final List<Category> categories = Category.buildDefault();

    // ---- Category 中既有的 HUD 模块引用（每帧刷新） ----
    private ModuleItem darkThemeToggle;       // Setting / "Dark Theme"
    private ModuleItem blurEnabledToggle;     // Setting / "Blur Background"
    private ModuleItem globalGradientSpeedSlider; // Setting / "Gradient Speed"
    private ModuleItem notificationToggle;    // Client / "Notifications"
    private ModuleItem arrayListToggle;       // Client / "ArrayList"
    private ModuleItem watermarkToggle;       // Home  / "Watermark"

    // ---- ArrayList 自定义配置项（不放进 Category，只在 HUD 渲染时读取） ----
    private ModuleItem arrayListGlassToggle, arrayListSortToggle;
    private ModuleItem arrayListFontSizeSlider, arrayListBgOpacitySlider, arrayListShadowSizeSlider;
    private ModuleItem arrayListShadowXSlider, arrayListShadowYSlider;
    private ModuleItem arrayListFlowSpeedSlider;
    private ModuleItem arrayListLineToggle;
    private ModuleItem.ConfigOption arrayListHeightOpt;
    // ArrayList 配置项引用
    private ModuleItem.ConfigOption arrayBgOpt, arrayHideRenderOpt, arrayScaleOpt, arrayShowChatOpt, arrayShowF3Opt;

    // ---- 水印自定义配置项 ----
    private ModuleItem watermarkSyncToggle, watermarkUnderlineToggle, watermarkShadowToggle;
    private ModuleItem watermarkFontSizeSlider, watermarkXSlider, watermarkYSlider, watermarkHueSlider;
    // Watermark 配置项引用
    private ModuleItem.ConfigOption wmShowVerOpt, wmShowFpsOpt, wmShowPingOpt, wmScaleOpt, wmBgOpt;

    // ---- 通知自定义配置项 ----
    private ModuleItem notificationSizeSlider, notificationFontSizeSlider, notificationSpacingSlider, notificationDurationSlider;
    // Notifications 配置项引用
    private ModuleItem.ConfigOption notifDurOpt, notifShowChatOpt, notifFontSizeOpt, notifSoundOpt;

    // ---- Keystrokes 配置项引用 ----
    private ModuleItem.ConfigOption ksShowMouseOpt, ksSizeOpt;

    // ================================================================
    //  Toast 提示系统
    // ================================================================

    // 开关功能提示：镂空玻璃长条 + 底部黑色倒计时条，右下角平滑滑入/滑出，多条堆叠
    private final List<ToastNotice> toasts = new CopyOnWriteArrayList<>();
    private static final float TOAST_ENTER = 0.32f, TOAST_HOLD = 2.0f, TOAST_EXIT = 0.32f;

    private class ToastNotice {
        final String text;
        final boolean enabled; // 启用=绿色竖线，禁用=红色竖线
        float age = 0f;
        boolean exiting = false;
        final Anim posY;
        final Anim reveal = new Anim(0f, 4.5f);
        ToastNotice(String text, float startY, boolean enabled) {
            this.text = text;
            this.enabled = enabled;
            this.posY = new Anim(startY, 9f);
            this.reveal.setTarget(1f);
        }
    }

    // 用来把 spawnModuleToast 的启用状态传给 spawnToast
    private boolean pendingToastEnabled = true;

    private void spawnToast(String text) {
        if (notificationToggle != null && !notificationToggle.enabled) return; // 总开关关掉就不生成
        float slotY0 = screenH - 46f * uiScale; // 贴近屏幕底部
        toasts.add(new ToastNotice(text, slotY0, pendingToastEnabled));
        pendingToastEnabled = true; // 复位
    }

    /** 模块开关提示：显示 "某某某模块已启用/已禁用" */
    private void spawnModuleToast(String moduleName, boolean enabled) {
        String text = moduleName + (enabled ? " enabled" : " disabled");
        pendingToastEnabled = enabled;
        spawnToast(text);
        // 播放音效
        com.visiolab.clickgui.audio.SoundManager.getInstance().playToggle(enabled);
    }

    /** 检查模块冲突，触发灵动岛警告。只在切换 KillAura 或 Scaffold 时调用 */
    private void checkModuleConflicts(String toggledModuleName) {
        if (dynamicIsland == null) return;
        if (!"KillAura".equals(toggledModuleName) && !"Scaffold".equals(toggledModuleName)) return;
        boolean scaffoldOn = false, killAuraOn = false;
        for (Category c : categories) {
            for (ModuleItem m : c.modules) {
                if (m.name.equals("Scaffold") && m.enabled) scaffoldOn = true;
                if (m.name.equals("KillAura") && m.enabled) killAuraOn = true;
            }
        }
        if (scaffoldOn && killAuraOn) {
            dynamicIsland.addWarning("Conflict Detected", "Scaffold and KillAura may conflict");
        }
    }

    /** 登录成功时调用，触发灵动岛成功提示 */
    public void onLoginSuccess() {
        if (dynamicIsland != null) {
            dynamicIsland.addSuccess("Login Success", "Welcome back to Rlin");
        }
    }

    /** 配置保存成功时调用 */
    public void onConfigSaved() {
        if (dynamicIsland != null) {
            dynamicIsland.addSuccess("Config Saved", "Module settings saved");
        }
    }

    /** 配置加载成功时调用 */
    public void onConfigLoaded() {
        if (dynamicIsland != null) {
            dynamicIsland.addSuccess("Config Loaded", "Module settings loaded");
        }
    }

    // ================================================================
    //  配置持久化
    // ================================================================

    /** 保存所有模块配置到 SharedPreferences */
    private void saveModuleConfig() {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("soar_config", android.content.Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            for (Category c : categories) {
                for (ModuleItem m : c.modules) {
                    editor.putBoolean("mod_" + m.name + "_enabled", m.enabled);
                    editor.putFloat("mod_" + m.name + "_sliderValue", m.sliderValue);
                    for (ModuleItem.ConfigOption opt : m.options) {
                        if (opt.type == ModuleItem.ConfigOption.TYPE_SWITCH) {
                            editor.putBoolean("opt_" + m.name + "_" + opt.label, opt.boolValue);
                        } else if (opt.type == ModuleItem.ConfigOption.TYPE_SLIDER) {
                            editor.putFloat("opt_" + m.name + "_" + opt.label, opt.sliderValue);
                        } else if (opt.type == ModuleItem.ConfigOption.TYPE_DROPDOWN) {
                            editor.putInt("opt_" + m.name + "_" + opt.label, opt.dropdownIndex);
                        }
                    }
                }
            }
            editor.apply();
        } catch (Exception e) {
            // ignore
        }
    }

    /** 从 SharedPreferences 加载所有模块配置 */
    private void loadModuleConfig() {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("soar_config", android.content.Context.MODE_PRIVATE);
            for (Category c : categories) {
                for (ModuleItem m : c.modules) {
                    m.enabled = prefs.getBoolean("mod_" + m.name + "_enabled", m.enabled);
                    m.sliderValue = prefs.getFloat("mod_" + m.name + "_sliderValue", m.sliderValue);
                    for (ModuleItem.ConfigOption opt : m.options) {
                        if (opt.type == ModuleItem.ConfigOption.TYPE_SWITCH) {
                            opt.boolValue = prefs.getBoolean("opt_" + m.name + "_" + opt.label, opt.boolValue);
                        } else if (opt.type == ModuleItem.ConfigOption.TYPE_SLIDER) {
                            opt.sliderValue = prefs.getFloat("opt_" + m.name + "_" + opt.label, opt.sliderValue);
                        } else if (opt.type == ModuleItem.ConfigOption.TYPE_DROPDOWN) {
                            opt.dropdownIndex = prefs.getInt("opt_" + m.name + "_" + opt.label, opt.dropdownIndex);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // ================================================================
    //  渲染器字段
    // ================================================================

    private final QuadBatch quad = new QuadBatch();
    private final TextRenderer text = new TextRenderer();
    private final BlurPipeline blur = new BlurPipeline();
    private DynamicIslandRenderer dynamicIsland;

    private float sceneTime = 0f;
    private float frameDt = 0.016f; // 当前帧的 deltaTime，供子方法使用

    private int screenW, screenH;
    private long lastFrameNs = 0;
    private float uiScale = 1f; // 整体控件/字号缩放系数

    // ArrayList 颜色流动偏移量：持续递增、永不重置
    private float flowOffset = 0f;

    // 中英文语言系统
    private boolean langZh = true;

    private final Context context;

    public HudRenderer(Context context) {
        this.context = context;
        com.visiolab.clickgui.hotkey.HotkeyManager.getInstance().init(context);
    }

    /**
     * 获取模块的 AssetManager。
     * 在 Xposed 注入模式下，优先使用 ModuleResourceLoader 加载模块资源。
     * 正常模式下直接用 context.getAssets()。
     */
    private android.content.res.AssetManager getModuleAssets() {
        try {
            Class<?> mrl = Class.forName("com.visiolab.clickgui.xposed.ModuleResourceLoader");
            java.lang.reflect.Method getAssets = mrl.getMethod("getAssets");
            java.lang.reflect.Method isInit = mrl.getMethod("isInitialized");
            if ((boolean) isInit.invoke(null)) {
                android.content.res.AssetManager am = (android.content.res.AssetManager) getAssets.invoke(null);
                if (am != null) return am;
            }
        } catch (Throwable e) {
            // 非注入模式，忽略
        }
        return context.getAssets();
    }

    // ================================================================
    //  GLSurfaceView.Renderer
    // ================================================================

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 背景透明：alpha=0，透出游戏画面
        GLES20.glClearColor(WORLD_BG[0], WORLD_BG[1], WORLD_BG[2], 0f);
        quad.init();
        text.init();
        blur.init();
        // 初始化音效
        com.visiolab.clickgui.audio.SoundManager.getInstance().init(context);

        // 获取 AssetManager：优先使用模块资源加载器（Xposed 注入模式）
        android.content.res.AssetManager am = getModuleAssets();
        // 加载图标字体
        text.getAtlas().loadIconFont(am, "fonts/Tenacity-Icons.ttf");
        // 加载 Comfortaa 字体（Regular 用于普通文字，Bold 用于标题/强调）
        text.getAtlas().loadMainFont(am,
                "fonts/Comfortaa-Regular.ttf", "fonts/Comfortaa-Bold.ttf");
        // 加载 Pacifico 字体（水印专用）
        text.getAtlas().loadWatermarkFont(am, "fonts/Pacifico.ttf");
        lastFrameNs = System.nanoTime();

        // ---- 查找 Category 中既有的 HUD 模块引用 ----
        for (Category c : categories) {
            if (c.name.equals("Setting")) {
                for (ModuleItem m : c.modules) {
                    if (m.name.equals("Dark Theme")) darkThemeToggle = m;
                    if (m.name.equals("Blur Background")) blurEnabledToggle = m;
                    if (m.name.equals("Gradient Speed")) globalGradientSpeedSlider = m;
                    if (m.name.equals("Notification")) notificationToggle = m;
                    if (m.name.equals("ArrayList")) arrayListToggle = m;
                }
            }
            if (c.name.equals("Home")) {
                for (ModuleItem m : c.modules) {
                    if (m.name.equals("Watermark")) watermarkToggle = m;
                }
            }
        }

        // ---- ArrayList 渐变配置（不放进 Category，只在 HUD 渲染时读取） ----
        arrayListGlassToggle = new ModuleItem("玻璃镂空效果", "条形内部露出真实背景，只留边缘发散的阴影光晕", true);
        arrayListSortToggle = new ModuleItem("按宽度排序", "关闭后按固定顺序排列，不再从长到短", true);
        arrayListFontSizeSlider = new ModuleItem("字号", "列表文字大小", false, true, 0.3f, 0, 1, "");
        arrayListBgOpacitySlider = new ModuleItem("背景不透明度", "关闭玻璃镂空后，条形背景的不透明度", false, true, 0.4f, 0, 1, "");
        arrayListShadowSizeSlider = new ModuleItem("阴影扩散范围", "阴影模糊柔化的范围大小", false, true, 0.5f, 0, 1, "");
        arrayListShadowXSlider = new ModuleItem("阴影X偏移", "阴影往左/右偏移的位置", false, true, 0.667f, 0, 1, "");
        arrayListShadowYSlider = new ModuleItem("阴影Y偏移", "阴影往上/下偏移的位置", false, true, 0.667f, 0, 1, "");
        arrayListFlowSpeedSlider = new ModuleItem("渐变流动速度", "颜色渐变滚动的快慢", false, true, 0.4f, 0, 1, "");
        arrayListLineToggle = new ModuleItem("右侧竖线连接", "在ArrayList右边画一条带渐变的竖线，连接所有开启的模块", true);

        // ---- 水印自定义配置 ----
        watermarkSyncToggle = new ModuleItem("动态渐变", "左上到右下颜色渐变（类似ArrayList）", false);
        watermarkUnderlineToggle = new ModuleItem("显示下划线", "文字下方的强调色细线", true);
        watermarkFontSizeSlider = new ModuleItem("字号", "水印文字大小", false, true, 0.45f, 0, 1, "");
        watermarkXSlider = new ModuleItem("X 位置", "水印距离左边的距离", false, true, 0.1f, 0, 1, "");
        watermarkYSlider = new ModuleItem("Y 位置", "水印距离顶部的距离", false, true, 0.1f, 0, 1, "");
        watermarkHueSlider = new ModuleItem("自定义色相", "关闭跟随主题渐变后生效", false, true, 0.55f, 0, 1, "");
        watermarkShadowToggle = new ModuleItem("周围发散阴影", "文字周围轻微发散的柔光", true);

        // ---- 通知自定义配置 ----
        notificationSizeSlider = new ModuleItem("大小", "提示条整体大小", false, true, 0.35f, 0, 1, "");
        notificationFontSizeSlider = new ModuleItem("字号", "提示文字大小", false, true, 0.35f, 0, 1, "");
        notificationSpacingSlider = new ModuleItem("间距", "多条提示堆叠的间距", false, true, 0.3f, 0, 1, "");
        notificationDurationSlider = new ModuleItem("停留时间", "提示在滑出前停留的时长", false, true, 0.4f, 0, 1, "");

        // 主题色预设：默认选中 Custom（索引 0）
        selectedThemeIndex = 0;
        applyThemeColors();

        // 加载持久化的模块配置
        loadModuleConfig();

        // ---- 初始化灵动岛 ----
        dynamicIsland = new DynamicIslandRenderer(quad, text, blur, this::drawWorldScene);
        com.visiolab.nativebridge.GameCallback.setDynamicIsland(dynamicIsland);

        // 注意：悬浮快捷键已由原生 View (FloatingHotkeyView) 接管，不再在 GL 层渲染
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        screenW = width;
        screenH = height;
        GLES20.glViewport(0, 0, width, height);
        blur.resize(width, height);

        // uiScale 沿用 UIRenderer 的面板高度基准，保证 HUD 元素尺寸与原版一致：
        // 面板尺寸按屏幕比例自适应（南方客户端 960:600 = 1.6:1 比例）
        float pW = Math.min(width * 0.92f, height * 1.6f);
        float pH = pW * (600f / 960f);
        if (pH > height * 0.9f) {
            pH = height * 0.9f;
        }
        uiScale = pH / 600f;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000f);
        frameDt = dt;
        lastFrameNs = now;

        sceneTime += dt;
        float globalSpeedMul = globalGradientSpeedSlider != null ? (0.3f + globalGradientSpeedSlider.sliderValue * 4f) : 1f;
        float flowSpeedMul = arrayListFlowSpeedSlider != null ? (0.3f + arrayListFlowSpeedSlider.sliderValue * 4f) : 1f;
        flowOffset += dt * 0.15f * globalSpeedMul * flowSpeedMul; // 缓慢持续流动，不重置

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        // 背景透明：每帧清空，避免 HUD 残留累积
        GLES20.glClearColor(WORLD_BG[0], WORLD_BG[1], WORLD_BG[2], 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // ---- 刷新模块引用 ----
        refreshHudModuleRefs();
        if (dynamicIsland != null) {
            dynamicIsland.refreshModuleRefs(categories);
        }

        // ---- HUD 动画更新 ----
        updateHudAnimations(dt);
        updateToasts(dt);

        // ---- 渲染 HUD 元素 ----
        drawToasts();          // 通知提示在底层
        drawArrayListHud();
        drawWatermark();
        drawKeystrokes();
        // 灵动岛：最上层
        if (dynamicIsland != null) {
            dynamicIsland.update(frameDt, screenW, screenH, uiScale);
        }
    }

    // ================================================================
    //  HUD 模块引用刷新
    // ================================================================

    private void refreshHudModuleRefs() {
        for (Category c : categories) {
            if (!c.name.equals("Client")) continue;
            for (ModuleItem m : c.modules) {
                switch (m.name) {
                    case "ArrayList":
                        arrayListToggle = m;
                        for (ModuleItem.ConfigOption opt : m.options) {
                            switch (opt.label) {
                                case "Height": arrayListHeightOpt = opt; break;
                                case "Background": arrayBgOpt = opt; break;
                                case "Hide Render Modules": arrayHideRenderOpt = opt; break;
                                case "Scale": arrayScaleOpt = opt; break;
                                case "Show in Chat": arrayShowChatOpt = opt; break;
                                case "Show in F3": arrayShowF3Opt = opt; break;
                            }
                        }
                        break;
                    case "Watermark":
                        watermarkToggle = m;
                        for (ModuleItem.ConfigOption opt : m.options) {
                            switch (opt.label) {
                                case "Show Version": wmShowVerOpt = opt; break;
                                case "Show FPS": wmShowFpsOpt = opt; break;
                                case "Show Ping": wmShowPingOpt = opt; break;
                                case "Scale": wmScaleOpt = opt; break;
                                case "Background": wmBgOpt = opt; break;
                            }
                        }
                        break;
                    case "Notifications":
                        notificationToggle = m;
                        for (ModuleItem.ConfigOption opt : m.options) {
                            switch (opt.label) {
                                case "Duration": notifDurOpt = opt; break;
                                case "Show in Chat": notifShowChatOpt = opt; break;
                                case "Font Size": notifFontSizeOpt = opt; break;
                                case "Sound": notifSoundOpt = opt; break;
                            }
                        }
                        break;
                    case "Keystrokes":
                        for (ModuleItem.ConfigOption opt : m.options) {
                            switch (opt.label) {
                                case "Show Mouse": ksShowMouseOpt = opt; break;
                                case "Size": ksSizeOpt = opt; break;
                            }
                        }
                        break;
                }
            }
        }
    }

    /** 每帧更新所有模块的 HUD 滑入/淡出动画 */
    private void updateHudAnimations(float dt) {
        for (Category c : categories) {
            for (ModuleItem m : c.modules) {
                m.hudAnim.setTarget(m.enabled ? 1f : 0f);
                m.hudAnim.update(dt);
                // 音效开关
                if (m.name.equals("Sound Effects")) {
                    com.visiolab.clickgui.audio.SoundManager.getInstance().setEnabled(m.enabled);
                }
            }
        }
    }

    // ================================================================
    //  世界背景（透明清屏，供玻璃镂空效果 scissor 后重画）
    // ================================================================

    private void drawWorldScene() {
        GLES20.glClearColor(WORLD_BG[0], WORLD_BG[1], WORLD_BG[2], 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    // ================================================================
    //  ArrayList HUD
    // ================================================================

    /** ArrayList 每一行的颜色——经典灰白渐变，亮度在顶部和底部之间缓慢流动。 */
    private float[] arrayListRowColor(int i, int count) {
        float t = count <= 1 ? 0f : i / (float) (count - 1);
        float phase = t + flowOffset;
        phase = phase - (float) Math.floor(phase); // 取模到 0..1，持续滚动

        float topLight = 0.95f;
        float botLight = 0.62f;
        float mixAmt = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);
        float light = topLight + (botLight - topLight) * mixAmt;
        return new float[]{light, light, light * 1.02f, 1f}; // 极轻微偏冷调
    }

    private void drawArrayListHud() {
        if (arrayListToggle != null && !arrayListToggle.enabled) return; // 总开关关掉，整个列表不画

        // 扫描所有分类，收集所有开启中的模块
        List<ModuleItem> allEnabled = new ArrayList<>();
        for (Category c : categories) {
            if (c.name.equals("Preferences")) continue;
            boolean hideRender = arrayHideRenderOpt != null && arrayHideRenderOpt.boolValue;
            if (hideRender && c.name.equals("Render")) continue;
            for (ModuleItem m : c.modules) {
                if (m.enabled || m.hudAnim.get() > 0.01f) allEnabled.add(m);
            }
        }
        if (allEnabled.isEmpty()) return;

        float userScale = arrayScaleOpt != null ? (0.5f + arrayScaleOpt.sliderValue * 1.5f) : 1f;
        float fontSize = (11f + (arrayListFontSizeSlider != null ? arrayListFontSizeSlider.sliderValue : 0.3f) * 9f) * uiScale * userScale;
        float heightScale = arrayListHeightOpt != null ? (0.5f + arrayListHeightOpt.sliderValue * 1.5f) : 1f;
        float barH = (fontSize + 10f * uiScale * userScale) * heightScale;
        float rowH = barH;
        float marginTop = screenH / 8f;
        boolean showLine = arrayListLineToggle == null || arrayListLineToggle.enabled;
        float lineW = 2.5f * uiScale * userScale;
        float marginRight = showLine ? (3f * uiScale + lineW) : 3f * uiScale;
        float textPad = 10f * uiScale * userScale;
        boolean showBackground = arrayBgOpt == null || arrayBgOpt.boolValue;
        boolean glassMode = showBackground && (arrayListGlassToggle == null || arrayListGlassToggle.enabled);
        boolean sortByWidth = arrayListSortToggle == null || arrayListSortToggle.enabled;
        float bgOpacity = arrayListBgOpacitySlider != null ? (0.15f + arrayListBgOpacitySlider.sliderValue * 0.55f) : 0.38f;
        float shadowSpread = (2f + (arrayListShadowSizeSlider != null ? arrayListShadowSizeSlider.sliderValue : 0.5f) * 10f) * uiScale;

        List<ModuleItem> visible = allEnabled;
        if (visible.isEmpty()) return;

        final float measureFontSize = fontSize;
        List<String> lineList = new ArrayList<>();
        for (ModuleItem m : visible) {
            String dispName = Category.localizedName(m.name, langZh);
            lineList.add(m.hasSlider ? (dispName + " " + String.format("%.0f%s", m.getRealValue(), m.sliderUnit)) : dispName);
        }

        // 排序：按真实像素宽度从长到短
        Integer[] order = new Integer[visible.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        if (sortByWidth) {
            java.util.Arrays.sort(order, (a, b) -> Float.compare(
                    text.measure(lineList.get(b), measureFontSize), text.measure(lineList.get(a), measureFontSize)));
        }

        List<ModuleItem> sortedModules = new ArrayList<>();
        List<String> sortedLines = new ArrayList<>();
        for (int idx : order) {
            sortedModules.add(visible.get(idx));
            sortedLines.add(lineList.get(idx));
        }

        // ---- 阴影：所有条形整体画进透明贴图模糊一遍 ----
        final float shadowShiftX = arrayListShadowXSlider != null ? (-15f + arrayListShadowXSlider.sliderValue * 30f) * uiScale : 5f * uiScale;
        final float shadowShiftY = arrayListShadowYSlider != null ? (-15f + arrayListShadowYSlider.sliderValue * 30f) * uiScale : 5f * uiScale;
        final List<ModuleItem> shadowModules = sortedModules;
        final List<String> shadowLines = sortedLines;
        final float sFontSize = fontSize, sRowH = rowH, sBarH = barH, sMarginTop = marginTop, sMarginRight = marginRight, sTextPad = textPad;
        final float sSpread = shadowSpread;
        blur.captureAndBlurTransparent(() -> {
            float cumY = sMarginTop;
            for (int i = 0; i < shadowModules.size(); i++) {
                ModuleItem m = shadowModules.get(i);
                float a = Math.max(0f, Math.min(1f, m.hudAnim.get()));
                if (a <= 0.01f) { continue; }
                float rowY = cumY;
                float tw = text.measureBold(shadowLines.get(i), sFontSize);
                float slideOffset = (1f - Anim.backEaseOut(a)) * (m.enabled ? 24f : 120f) * uiScale;
                float barW = tw + sTextPad * 2f;
                float barX = screenW - sMarginRight - barW + slideOffset;
                quad.addRect(barX + shadowShiftX - sSpread, rowY + shadowShiftY - sSpread,
                        barW + sSpread * 2f, sBarH + sSpread * 2f, sSpread, new float[]{1f, 1f, 1f, a});
                cumY += m.enabled ? sRowH * a : sRowH * a;
            }
            quad.flush(screenW, screenH);
        });
        blur.drawGlow(new float[]{0f, 0f, 0f, 0.7f}, screenW, screenH);

        // ---- 条形本体 ----
        float cumY = marginTop;
        float[] rowYs = new float[sortedModules.size()];
        for (int i = 0; i < sortedModules.size(); i++) {
            ModuleItem m = sortedModules.get(i);
            float a = Math.max(0f, Math.min(1f, m.hudAnim.get()));
            rowYs[i] = cumY;
            cumY += rowH * a;
        }
        for (int i = 0; i < sortedModules.size(); i++) {
            ModuleItem m = sortedModules.get(i);
            float a = Math.max(0f, Math.min(1f, m.hudAnim.get()));
            if (a <= 0.01f) continue;
            float rowY = rowYs[i];
            float tw = text.measureBold(sortedLines.get(i), fontSize);
            float slideOffset = (1f - Anim.backEaseOut(a)) * (m.enabled ? 24f : 120f) * uiScale;
            float barW = tw + textPad * 2f;
            float barX = screenW - marginRight - barW + slideOffset;

            if (glassMode) {
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
                GLES20.glScissor((int) barX, (int) (screenH - (rowY + barH)), (int) barW, (int) barH);
                drawWorldScene();
                GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            } else {
                quad.addRect(barX, rowY, barW, barH, 0f,
                        new float[]{0.92f, 0.93f, 0.95f, bgOpacity * a});
            }
        }
        if (!glassMode) quad.flush(screenW, screenH);

        // ---- 竖线连接 ----
        if (showLine) {
            float lineTop = marginTop;
            float lineBottom = cumY;
            if (lineBottom > lineTop) {
                float lineX = screenW - marginRight;
                float lineH = Math.max(2f * uiScale, lineBottom - lineTop);
                int visCount = 0;
                for (int i = 0; i < sortedModules.size(); i++) {
                    ModuleItem m = sortedModules.get(i);
                    float a = Math.max(0f, Math.min(1f, m.hudAnim.get()));
                    if (a > 0.01f) visCount++;
                }
                if (visCount > 0) {
                    float[] topCol = arrayListRowColor(0, visCount);
                    float[] botCol = arrayListRowColor(Math.max(0, visCount - 1), visCount);
                    quad.addRectGradient(lineX, lineTop, lineW, lineH, 1.2f * uiScale,
                            topCol, topCol, botCol, botCol);
                    quad.flush(screenW, screenH);
                }
            }
        }

        // ---- 文字 ----
        for (int i = 0; i < sortedModules.size(); i++) {
            ModuleItem m = sortedModules.get(i);
            float a = Math.max(0f, Math.min(1f, m.hudAnim.get()));
            if (a <= 0.01f) continue;

            float rowY = rowYs[i];
            String line = sortedLines.get(i);
            float tw = text.measureBold(line, fontSize);
            float slideOffset = (1f - Anim.backEaseOut(a)) * (m.enabled ? 24f : 120f) * uiScale;
            float tx = screenW - marginRight - textPad - tw + slideOffset;
            float ty = rowY + (barH - fontSize) / 2f;

            float[] col = arrayListRowColor(i, sortedModules.size());
            text.drawBold(line, tx, ty, fontSize, new float[]{col[0], col[1], col[2], a}, screenW, screenH);
        }
    }

    // ================================================================
    //  水印
    // ================================================================

    private void drawWatermark() {
        if (watermarkToggle == null) return;
        float a = Math.max(0f, Math.min(1f, watermarkToggle.hudAnim.get()));
        if (a <= 0.01f) return;

        String text2 = "Rlin B1";
        float wmFontBase = 20f + (watermarkFontSizeSlider != null ? watermarkFontSizeSlider.sliderValue : 0.5f) * 28f;
        float fontSize = wmFontBase * uiScale * (wmScaleOpt != null ? (0.5f + wmScaleOpt.sliderValue * 1.5f) : 1f);
        float tx = (watermarkXSlider != null ? watermarkXSlider.sliderValue : 0.1f) * (screenW * 0.4f);
        float ty = (watermarkYSlider != null ? watermarkYSlider.sliderValue : 0.1f) * (screenH * 0.3f);

        boolean useGradient = watermarkSyncToggle != null && watermarkSyncToggle.enabled;
        float[] defaultWhite = new float[]{1f, 1f, 1f, a};
        float[] col;
        if (useGradient) {
            col = defaultWhite;
        } else {
            col = hsv2rgb(watermarkHueSlider != null ? watermarkHueSlider.sliderValue : 0.0f, 0.0f, 1f);
        }

        float tw = text.measureWatermark(text2, fontSize);

        // 文字周围轻微发散的阴影
        if (watermarkShadowToggle == null || watermarkShadowToggle.enabled) {
            final float sTx = tx, sTy = ty, sFontSize = fontSize;
            final String sText = text2;
            blur.captureAndBlurTransparentTight(() -> {
                text.drawWatermark(sText, sTx, sTy, sFontSize, new float[]{1f, 1f, 1f, a}, screenW, screenH);
            });
            blur.drawGlow(new float[]{0f, 0f, 0f, 0.55f * a}, screenW, screenH);
        }

        if (useGradient) {
            // 动态渐变：逐字符左上→右下颜色渐变
            float penX = tx;
            for (int i = 0; i < text2.length(); i++) {
                char c = text2.charAt(i);
                if (c == ' ') {
                    penX += fontSize * 0.3f;
                    continue;
                }
                float progress = (float) i / Math.max(1, text2.length() - 1);
                float flow = (progress + flowOffset) % 1f;
                float[] gc = mixColor(ACCENT_1, ACCENT_2, flow);
                float charW = text.drawWatermark(String.valueOf(c), penX, ty, fontSize,
                        new float[]{gc[0], gc[1], gc[2], a}, screenW, screenH);
                penX += charW;
            }
        } else {
            text.drawWatermark(text2, tx, ty, fontSize, new float[]{col[0], col[1], col[2], a}, screenW, screenH);
        }

        // 文字下方一条细细的强调色下划线
        if (watermarkUnderlineToggle == null || watermarkUnderlineToggle.enabled) {
            float underlineY = ty + fontSize + 2f * uiScale;
            if (useGradient) {
                quad.addRectGradient(tx, underlineY, tw, 2f * uiScale, 1f * uiScale,
                        withAlpha(ACCENT_1, a), withAlpha(ACCENT_2, a),
                        withAlpha(ACCENT_1, a), withAlpha(ACCENT_2, a));
            } else {
                quad.addRectGradient(tx, underlineY, tw, 2f * uiScale, 1f * uiScale,
                        withAlpha(col, a), withAlpha(col, a),
                        withAlpha(col, a), withAlpha(col, a));
            }
            quad.flush(screenW, screenH);
        }
    }

    // ================================================================
    //  Keystrokes HUD
    // ================================================================

    private void drawKeystrokes() {
        ModuleItem ksModule = null;
        for (Category c : categories) {
            if (!c.name.equals("Client")) continue;
            for (ModuleItem m : c.modules) {
                if (m.name.equals("Keystrokes")) { ksModule = m; break; }
            }
        }
        if (ksModule == null || !ksModule.enabled) return;
        float a = Math.max(0f, Math.min(1f, ksModule.hudAnim.get()));
        if (a <= 0.01f) return;

        float sizeMul = ksSizeOpt != null ? (0.5f + ksSizeOpt.sliderValue * 1.5f) : 1f;
        boolean showMouse = ksShowMouseOpt == null || ksShowMouseOpt.boolValue;
        float keySize = 30f * uiScale * sizeMul;
        float gap = 3f * uiScale * sizeMul;
        float fontSize = 12f * uiScale * sizeMul;

        float startX = 20f * uiScale;
        float startY = screenH - keySize * 4 - gap * 3 - 20f * uiScale;

        // W
        drawKey(startX + keySize + gap, startY, keySize, keySize, "W", false, a, fontSize);
        // A S D
        drawKey(startX, startY + keySize + gap, keySize, keySize, "A", false, a, fontSize);
        drawKey(startX + keySize + gap, startY + keySize + gap, keySize, keySize, "S", false, a, fontSize);
        drawKey(startX + (keySize + gap) * 2f, startY + keySize + gap, keySize, keySize, "D", false, a, fontSize);

        if (showMouse) {
            drawKey(startX, startY + (keySize + gap) * 2f, keySize * 1.5f, keySize, "LMB", false, a, fontSize);
            drawKey(startX + keySize * 1.5f + gap, startY + (keySize + gap) * 2f, keySize * 1.5f, keySize, "RMB", false, a, fontSize);
        }
    }

    private void drawKey(float x, float y, float w, float h, String label, boolean pressed, float alpha, float fontSize) {
        float[] bg = pressed ? new float[]{0.4f, 0.4f, 0.4f, 0.8f * alpha} : new float[]{0.15f, 0.15f, 0.15f, 0.5f * alpha};
        quad.addRect(x, y, w, h, 4f * uiScale, bg);
        quad.flush(screenW, screenH);
        float tw = text.measure(label, fontSize);
        text.draw(label, x + (w - tw) / 2f, y + (h - fontSize) / 2f, fontSize,
                new float[]{1f, 1f, 1f, alpha}, screenW, screenH);
    }

    // ================================================================
    //  Toast 提示
    // ================================================================

    private float notifRectH() {
        float sizeMul = notificationSizeSlider != null ? (0.7f + notificationSizeSlider.sliderValue * 0.6f) : 0.85f;
        return 36f * uiScale * sizeMul;
    }

    private float notifRectW() {
        float sizeMul = notificationSizeSlider != null ? (0.7f + notificationSizeSlider.sliderValue * 0.6f) : 0.85f;
        return 190f * uiScale * sizeMul;
    }

    private void updateToasts(float dt) {
        float slotY0 = screenH - 46f * uiScale;
        float spacingMul = notificationSpacingSlider != null ? (0.5f + notificationSpacingSlider.sliderValue * 1.0f) : 0.8f;
        float rowSpacing = notifRectH() + 8f * uiScale * spacingMul;
        float holdDuration = notificationDurationSlider != null ? (1.0f + notificationDurationSlider.sliderValue * 3.0f) : TOAST_HOLD;
        if (notifDurOpt != null) holdDuration = 1.0f + notifDurOpt.sliderValue * 4.0f;
        for (int i = 0; i < toasts.size(); i++) {
            ToastNotice t = toasts.get(i);
            t.age += dt;
            t.posY.setTarget(slotY0 - i * rowSpacing);
            t.posY.update(dt);
            if (!t.exiting && t.age > TOAST_ENTER + holdDuration) {
                t.exiting = true;
                t.reveal.setTarget(0f);
            }
            t.reveal.update(dt);
        }
        for (ToastNotice t : toasts) {
            if (t.exiting && t.reveal.get() < 0.01f) {
                toasts.remove(t);
            }
        }
    }

    private void drawToasts() {
        if (toasts.isEmpty()) return;

        float rectW = notifRectW(), rectH = notifRectH();
        float baseX = screenW - 10f * uiScale - rectW;
        float offDist = Math.max(screenW, screenH) * 0.35f;

        // ---- 阴影：所有提示的形状整体画进透明贴图模糊一遍 ----
        final float fRectW = rectW, fRectH = rectH, fBaseX = baseX, fOffDist = offDist;
        blur.captureAndBlurTransparent(() -> {
            for (ToastNotice t : toasts) {
                float rv = Math.max(0f, Math.min(1f, t.reveal.get()));
                if (rv <= 0.01f) continue;
                float slide = (1f - smoothstep(rv)) * fOffDist;
                float dx = fBaseX + slide, dy = t.posY.get() + slide;
                quad.addRect(dx, dy, fRectW, fRectH, 10f * uiScale, new float[]{1f, 1f, 1f, rv});
            }
            quad.flush(screenW, screenH);
        });
        blur.drawGlow(new float[]{0f, 0f, 0f, 0.6f}, screenW, screenH);

        // ---- 镂空玻璃本体：scissor裁剪只在提示范围内重画一遍干净背景 ----
        for (ToastNotice t : toasts) {
            float rv = Math.max(0f, Math.min(1f, t.reveal.get()));
            if (rv <= 0.01f) continue;
            float slide = (1f - smoothstep(rv)) * offDist;
            float dx = baseX + slide, dy = t.posY.get() + slide;
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor((int) dx, (int) (screenH - (dy + rectH)), (int) rectW, (int) rectH);
            drawWorldScene();
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        }

        float holdDuration = notificationDurationSlider != null ? (1.0f + notificationDurationSlider.sliderValue * 3.0f) : TOAST_HOLD;
        if (notifDurOpt != null) holdDuration = 1.0f + notifDurOpt.sliderValue * 4.0f;
        float fontSize = 12f * uiScale * (notificationFontSizeSlider != null ? (0.75f + notificationFontSizeSlider.sliderValue * 0.7f) : 1f);
        if (notifFontSizeOpt != null) fontSize = 12f * uiScale * (0.5f + notifFontSizeOpt.sliderValue * 1.0f);

        // ---- 底部黑色倒计时条 + 文字 ----
        for (ToastNotice t : toasts) {
            float rv = Math.max(0f, Math.min(1f, t.reveal.get()));
            if (rv <= 0.01f) continue;
            float slide = (1f - smoothstep(rv)) * offDist;
            float dx = baseX + slide, dy = t.posY.get() + slide;

            float frac = Math.max(0f, Math.min(1f, 1f - (t.age - TOAST_ENTER) / holdDuration));
            float barH = 2.5f * uiScale;
            quad.addRect(dx, dy + rectH - barH, rectW * frac, barH, 1.2f * uiScale, new float[]{0f, 0f, 0f, 0.85f * rv});
            quad.flush(screenW, screenH);

            float tw = text.measure(t.text, fontSize);
            float tx = dx + rectW / 2f - tw / 2f;
            float ty = dy + rectH / 2f - fontSize / 2f - 2f * uiScale;
            text.draw(t.text, tx + 1f * uiScale, ty + 1.2f * uiScale, fontSize, new float[]{0f, 0f, 0f, 0.5f * rv}, screenW, screenH);
            text.draw(t.text, tx, ty, fontSize, new float[]{1f, 1f, 1f, rv}, screenW, screenH);
        }
    }

    private float smoothstep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    // ================================================================
    //  外部接口：模块状态同步
    // ================================================================

    /** 获取分类列表（与 ClickGuiPanel 共享同一实例，实现数据双向同步） */
    public List<Category> getCategories() { return categories; }

    /**
     * 外部调用：当 ClickGUI 面板切换模块时，同步状态到 HUD 并触发视觉反馈。
     * <p>
     * HudRenderer 维护自己的一份 categories，与面板的 categories 相互独立，
     * 因此面板切换模块后必须通过此方法把新状态同步过来，ArrayList HUD 等元素
     * 才会正确显示/隐藏对应模块。
     *
     * @param name    模块名称
     * @param enabled 是否启用
     */
    public void onModuleChanged(String name, boolean enabled) {
        ModuleItem target = null;
        for (Category c : categories) {
            for (ModuleItem m : c.modules) {
                if (m.name.equals(name)) { target = m; break; }
            }
            if (target != null) break;
        }
        if (target == null) return;

        target.enabled = enabled;
        // 触发 HUD 视觉反馈（与 UIRenderer 的开关逻辑保持一致）
        spawnModuleToast(name, enabled);
        com.visiolab.nativebridge.ModuleHook.onToggle(target);
        if (dynamicIsland != null) dynamicIsland.addModuleToggle(name, enabled);
        checkModuleConflicts(name);
        // Scaffold 搭路：开关时显示进度条提示
        if (name.equals("Scaffold") && dynamicIsland != null) {
            if (enabled) {
                dynamicIsland.addProgress("Scaffold Toggled", "Placing blocks...", 0.3f);
            } else {
                dynamicIsland.removeProgress("Scaffold Toggled");
            }
        }
    }

    // ================================================================
    //  主题与颜色工具
    // ================================================================

    /** 切换主题时调用：更新 ACCENT_1/ACCENT_2 */
    private void applyThemeColors() {
        ACCENT_1 = themeAccent1();
        ACCENT_2 = themeAccent2();
    }

    private float[] withAlpha(float[] c, float mulAlpha) {
        return new float[]{c[0], c[1], c[2], c[3] * mulAlpha};
    }

    private float[] mixColor(float[] a, float[] b, float t) {
        return new float[]{
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t,
                a[3] + (b[3] - a[3]) * t,
        };
    }

    /** 解析 #RRGGBB 字符串为 {r,g,b,1} */
    private static float[] hexToColor(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return new float[]{
                Integer.parseInt(h.substring(0, 2), 16) / 255f,
                Integer.parseInt(h.substring(2, 4), 16) / 255f,
                Integer.parseInt(h.substring(4, 6), 16) / 255f,
                1f
        };
    }

    /** h,s,v 均为 0..1，返回 {r,g,b,1} */
    private float[] hsv2rgb(float h, float s, float v) {
        float r, g, b;
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return new float[]{r, g, b, 1f};
    }
}
