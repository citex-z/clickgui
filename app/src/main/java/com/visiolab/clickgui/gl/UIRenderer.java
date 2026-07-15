package com.visiolab.clickgui.gl;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import com.visiolab.clickgui.anim.Anim;
import com.visiolab.clickgui.model.Category;
import com.visiolab.clickgui.model.ModuleItem;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 还原 Legacy-SoarClient 的 GuiModMenu 核心结构：
 *   左侧 Category 侧栏(帶滑动高亮条) + 右侧模块列表(Toggle / Slider) + 面板弹入动画 + 阴影。
 */
public class UIRenderer implements GLSurfaceView.Renderer {

    // ---- 主题色：深色 / 浅色两套，"Dark Theme" 开关实际生效切换 ----
    private static final float[] DARK_PANEL_BG = {0.11f, 0.11f, 0.13f, 0.92f};
    private static final float[] DARK_SIDEBAR_BG = {0.07f, 0.07f, 0.09f, 0.95f};
    private static final float[] DARK_CARD_BG = {0.16f, 0.16f, 0.19f, 0.9f};
    private static final float[] DARK_TEXT_MAIN = {1f, 1f, 1f, 1f};
    private static final float[] DARK_TEXT_DIM = {0.7f, 0.7f, 0.75f, 1f};
    private static final float[] DARK_TRACK_OFF = {0.3f, 0.3f, 0.34f, 1f};

    private static final float[] LIGHT_PANEL_BG = {0.94f, 0.94f, 0.97f, 0.92f};
    private static final float[] LIGHT_SIDEBAR_BG = {0.99f, 0.99f, 1.0f, 0.95f};
    private static final float[] LIGHT_CARD_BG = {0.87f, 0.87f, 0.90f, 0.9f};
    private static final float[] LIGHT_TEXT_MAIN = {0.08f, 0.08f, 0.10f, 1f};
    private static final float[] LIGHT_TEXT_DIM = {0.38f, 0.38f, 0.42f, 1f};
    private static final float[] LIGHT_TRACK_OFF = {0.75f, 0.75f, 0.78f, 1f};

    private float[] PANEL_BG = DARK_PANEL_BG;
    private float[] SIDEBAR_BG = DARK_SIDEBAR_BG;
    private float[] CARD_BG = DARK_CARD_BG;
    private float[] TEXT_MAIN = DARK_TEXT_MAIN;
    private float[] TEXT_DIM = DARK_TEXT_DIM;
    private float[] TRACK_OFF = DARK_TRACK_OFF;
    private static final float[] WORLD_BG = {0.04f, 0.05f, 0.06f, 1f};
    private static final float[] KNOB_COLOR = {1f, 1f, 1f, 1f}; // 拖钮/滑块圆点固定用白色，不跟随主题切换（否则浅色主题下会变黑看不清）
    // ---- 南方客户端真实颜色 (from source: Colors.java + SouthsideDarkStyle.java) ----
    private static final float[] HDR_BG = {0.094f, 0.086f, 0.090f, 1f};           // #181818 标题栏 (SouthsideDarkStyle topBar)
    private static final float[] SOUTH_SIDEBAR_BG = {0.082f, 0.082f, 0.090f, 0.945f}; // #151617 95%
    private static final float[] SOUTH_PANEL_BG = {0.082f, 0.082f, 0.090f, 1f};    // #151617
    private static final float[] SOUTH_CARD_BORDER = {0.20f, 0.20f, 0.20f, 1f};    // #333333
    private static final float[] SOUTH_CARD_BORDER_SEL = {0.85f, 0.85f, 0.85f, 1f}; // #D9D9D9 白灰选中
    private static final float[] SOUTH_SEARCH_BG = {0.16f, 0.16f, 0.16f, 1f};      // #292929
    private static final float[] SOUTH_TRACK_OFF = {0.18f, 0.18f, 0.18f, 1f};      // #2E2E2E
    private static final float[] SOUTH_TAB_OFF = {0.35f, 0.35f, 0.35f, 1f};        // #595959
    private static final float[] SOUTH_SECTION_LBL = {0.35f, 0.35f, 0.35f, 1f};     // #595959
    private static final float[] SOUTH_SLIDER_TRACK = {0.16f, 0.16f, 0.16f, 1f};    // #292929
    private static final float[] SOUTH_MODULE_BG = {0.14f, 0.14f, 0.14f, 1f};       // #242424
    private static final float[] SOUTH_CARD_BG = {0.094f, 0.094f, 0.094f, 1f};     // #181818
    private static final float[] SOUTH_TOGGLE_ON = {0.85f, 0.85f, 0.85f, 1f};     // #D9D9D9 白灰（替代蓝色）
    private static final float[] SOUTH_TOGGLE_OFF = {0.18f, 0.18f, 0.18f, 1f};      // #2E2E2E
    private static final float[] SOUTH_TEXT_WHITE = {1f, 1f, 1f, 0.8f};             // WHITE_80
    private static final float[] SOUTH_TEXT_DIM2 = {1f, 1f, 1f, 0.6f};             // WHITE_60
    private static final float[] SOUTH_TEXT_DIM3 = {1f, 1f, 1f, 0.5f};             // WHITE_50
    // 通知左侧彩色竖线：启用=绿色 #039855，禁用=红色 #D92020（南方客户端风格）
    private static final float[] TOAST_GREEN = {0.012f, 0.596f, 0.333f, 1f};        // #039855 启用
    private static final float[] TOAST_RED   = {0.851f, 0.125f, 0.125f, 1f};        // #D92020 禁用
    private ModuleItem darkThemeToggle; // 指向 Setting 分类里的 "Dark Theme" 开关，驱动主题切换

    // ---- 主题色预设（白/黑/灰系，不与南方蓝色完全相同）----
    private float[] ACCENT_1 = {0.85f, 0.85f, 0.85f, 1f}; // #D9D9D9 白灰
    private float[] ACCENT_2 = {0.65f, 0.65f, 0.65f, 1f}; // #A6A6A6 中灰
    private int selectedThemeIndex = 0;          // 当前选中的主题索引
    private float[] themeThemeRects;              // 运行时主题按钮命中区域
    private float customHue = 0.61f, customSat = 0.82f, customVal = 0.80f; // #1452CC
    private int selectedSwatchIndex = -1;
    private float hueBarX, hueBarW, hueBarY, hueBarH;
    private float svBoxX, svBoxY, svBoxW, svBoxH;
    private float[] addBtnRect, removeBtnRect;
    private static final float[][] presetSwatches = new float[][]{
            {1f,0f,0f,1f}, {1f,0.5f,0f,1f}, {1f,1f,0f,1f}, {0.5f,1f,0f,1f},
            {0f,1f,0f,1f}, {0f,1f,0.5f,1f}, {0f,1f,1f,1f}, {0f,0.5f,1f,1f},
            {0f,0f,1f,1f}, {0.5f,0f,1f,1f}, {1f,0f,1f,1f}, {1f,1f,1f,1f}
    };
    private boolean draggingHue = false, draggingSV = false;
    private boolean accentThemeExpanded = false;
    private float accentLabelY;  // 主题色标签Y坐标（用于触摸）

    /** 每个主题的数据：名称 + 节点颜色列表（按色相顺序） */
    private static final String[] THEME_NAMES = {
        "Blue", "Spaced", "Trans", "Rainbow", "Watermelon", "Sunset", "Poison", "Custom"
    };
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
        null  // null 表示这个主题在运行时通过 customThemeColors 字段动态更新
    };
    // Custom 主题的运行时自定义颜色（8个渐变节点）
    private static float[][] customThemeColors = new float[][]{
        hexToColor("#68D5EE"), hexToColor("#8CC7E0"), hexToColor("#AAB8D0"), hexToColor("#C0D0E0"),
        hexToColor("#D0E0F0"), hexToColor("#E0F0FF"), hexToColor("#B0C0D8"), hexToColor("#98A8C8")
    };

    /** 根据 flowOffset 和行索引从当前主题取流动颜色 */
    private float[] themeFlowColor(float flowPhase, int rowIndex) {
        float[][] nodes = THEME_COLORS[selectedThemeIndex];
        // Custom 主题使用运行时自定义颜色
        if (nodes == null) nodes = customThemeColors;
        int n = nodes.length;
        if (n == 0) return new float[]{1f, 1f, 1f, 1f};
        // 用 flowPhase + rowIndex 偏移驱动节点间平滑流动
        float t = flowPhase + rowIndex * 0.15f;
        t = t - (float) Math.floor(t);
        float seg = t * n;
        int idx = (int) Math.floor(seg);
        float frac = seg - idx;
        int next = (idx + 1) % n;
        return mixColor(nodes[idx], nodes[next], frac);
    }

    /** 从当前主题取两个端色用于 diagGradient（用第一个和中间一个） */
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

    // ---- 悬浮球：开关整个 ClickGUI 面板 ----
    private boolean guiOpen = true;
    private final Anim openness = new Anim(1f, 13f); // 0=完全关闭，1=完全展开
    private float ballX = -1, ballY = -1;
    private float ballRadius = 34f;
    private boolean draggingBall = false, ballMoved = false;
    private float ballDownX, ballDownY, ballOffsetX, ballOffsetY;

    // ---- 面板外边缘阴影：由 Setting 分类里的几个开关/滑块驱动 ----
    private ModuleItem shadowEnabledToggle, shadowSyncToggle, shadowHueSlider, shadowSizeSlider, shadowOpacitySlider;
    private ModuleItem globalGradientSpeedSlider;
    private ModuleItem notificationToggle;
    private ModuleItem notificationSizeSlider, notificationFontSizeSlider, notificationSpacingSlider, notificationDurationSlider;
    private float notificationSizeSliderX, notificationSizeSliderW;
    private float notificationFontSizeSliderX, notificationFontSizeSliderW;
    private float notificationSpacingSliderX, notificationSpacingSliderW;
    private float notificationDurationSliderX, notificationDurationSliderW;
    private boolean draggingNotifSize = false, draggingNotifFontSize = false, draggingNotifSpacing = false, draggingNotifDuration = false;
    private ModuleItem arrayListToggle, arrayListSyncToggle, arrayListColor1Slider, arrayListColor2Slider;
    private ModuleItem arrayListGlassToggle, arrayListSortToggle;
    private ModuleItem arrayListFontSizeSlider, arrayListBgOpacitySlider, arrayListShadowSizeSlider;
    private ModuleItem arrayListShadowXSlider, arrayListShadowYSlider;
    private ModuleItem arrayListFlowSpeedSlider;
    private ModuleItem arrayListLineToggle;
    private float arrayListLineToggleRectX, arrayListLineToggleRectY, arrayListLineToggleW, arrayListLineToggleH;
    private float arrayListFlowSpeedSliderX, arrayListFlowSpeedSliderW;
    private boolean draggingArrayListFlowSpeed = false;
    private float arrayListShadowXSliderX, arrayListShadowXSliderW;
    private float arrayListShadowYSliderX, arrayListShadowYSliderW;
    private boolean draggingArrayListShadowX = false, draggingArrayListShadowY = false;
    // 知名客户端 ArrayList 风格预设——一键套用一整套参数组合，而不是逐个开关调
    private static final String[] ARRAYLIST_PRESET_NAMES = {"玻璃质感", "彩虹流光", "极简白字", "经典描边"};
    private int selectedArrayListPreset = 0;
    private final List<float[]> arrayListPresetRects = new java.util.concurrent.CopyOnWriteArrayList<>();
    private float arrayListGlassToggleRectX, arrayListGlassToggleRectY, arrayListGlassToggleW, arrayListGlassToggleH;
    private float arrayListSortToggleRectX, arrayListSortToggleRectY, arrayListSortToggleW, arrayListSortToggleH;
    private float arrayListFontSizeSliderX, arrayListFontSizeSliderW;
    private float arrayListBgOpacitySliderX, arrayListBgOpacitySliderW;
    private float arrayListShadowSizeSliderX, arrayListShadowSizeSliderW;
    private boolean draggingArrayListFontSize = false, draggingArrayListBgOpacity = false, draggingArrayListShadowSize = false;
    private float arrayListSyncToggleRectX, arrayListSyncToggleRectY, arrayListSyncToggleW, arrayListSyncToggleH;
    private float arrayListColor1SliderX, arrayListColor1SliderW, arrayListColor2SliderX, arrayListColor2SliderW;
    private boolean draggingArrayListColor1 = false, draggingArrayListColor2 = false;
    // 阴影参数在配置面板中的触摸区域（滑块拖拽用）
    private float shadowSyncToggleRectX, shadowSyncToggleRectY, shadowSyncToggleW, shadowSyncToggleH;
    private float shadowHueSliderX, shadowHueSliderW;
    private float shadowSizeSliderX, shadowSizeSliderW;
    private float shadowOpacitySliderX, shadowOpacitySliderW;
    private boolean draggingShadowHue = false, draggingShadowSize = false, draggingShadowOpacity = false;
    private ModuleItem blurEnabledToggle;
    private ModuleItem watermarkToggle;
    // Client 分类里的 "ClickGUI" 模块，用来把面板背景阴影透明度跟它关联（每帧刷新引用）
    private ModuleItem clickGuiModule;
    // 开关功能提示：镂空玻璃长条 + 底部黑色倒计时条，右下角平滑滑入/滑出，多条堆叠
    private final List<ToastNotice> toasts = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final float TOAST_ENTER = 0.32f, TOAST_HOLD = 2.0f, TOAST_EXIT = 0.32f;

    private class ToastNotice {
        final String text;
        final boolean enabled; // 启用=绿色竖线，禁用=红色竖线（普通文本提示默认按"启用"配色）
        float age = 0f;
        boolean exiting = false;
        final Anim posY;
        final Anim reveal = new Anim(0f, 4.5f); // 速度放慢一些，滑出的时候才不会显得太突兀
        ToastNotice(String text, float startY, boolean enabled) {
            this.text = text;
            this.enabled = enabled;
            this.posY = new Anim(startY, 9f);
            this.reveal.setTarget(1f);
        }
    }

    // 用来把 spawnModuleToast 的启用状态传给 spawnToast（spawnToast 只接收文本，
    // 但 drawToasts 需要知道启用/禁用才能画对应颜色的左侧竖线）
    private boolean pendingToastEnabled = true;

    private void spawnToast(String text) {
        if (notificationToggle != null && !notificationToggle.enabled) return; // 总开关关掉就不生成
        float slotY0 = screenH - 46f * uiScale; // 贴近屏幕底部
        toasts.add(new ToastNotice(text, slotY0, pendingToastEnabled));
        pendingToastEnabled = true; // 复位：普通文本提示默认按"启用"配色
    }

    /** 模块开关提示：显示 "某某某模块已启用/已禁用"，不再出现 "ON"/"OFF" */
    private void spawnModuleToast(String moduleName, boolean enabled) {
        String text = moduleName + (enabled ? " enabled" : " disabled");
        pendingToastEnabled = enabled;
        spawnToast(text);
    }
    private ModuleItem watermarkSyncToggle, watermarkUnderlineToggle;
    private ModuleItem watermarkShadowToggle;
    private float watermarkShadowToggleRectX, watermarkShadowToggleRectY, watermarkShadowToggleW, watermarkShadowToggleH;
    private ModuleItem watermarkFontSizeSlider, watermarkXSlider, watermarkYSlider, watermarkHueSlider;
    private float watermarkSyncToggleRectX, watermarkSyncToggleRectY, watermarkSyncToggleW, watermarkSyncToggleH;
    private float watermarkUnderlineToggleRectX, watermarkUnderlineToggleRectY, watermarkUnderlineToggleW, watermarkUnderlineToggleH;
    private float watermarkFontSizeSliderX, watermarkFontSizeSliderW;
    private float watermarkXSliderX, watermarkXSliderW;
    private float watermarkYSliderX, watermarkYSliderW;
    private float watermarkHueSliderX, watermarkHueSliderW;
    private boolean draggingWatermarkFontSize = false, draggingWatermarkX = false, draggingWatermarkY = false, draggingWatermarkHue = false;

    private final QuadBatch quad = new QuadBatch();
    private final TextRenderer text = new TextRenderer();
    private final BlurPipeline blur = new BlurPipeline();
    private float sceneTime = 0f;

    // ---- 登录界面 ----
    private final LoginRenderer loginRenderer;
    private boolean loginActive = true; // 启动时先显示登录界面

    private int screenW, screenH;
    private long lastFrameNs = 0;

    private final List<Category> categories = Category.buildDefault();
    private int currentCategoryIndex = 1; // 默认打开 Module 分类，方便展示 Toggle/Slider

    private final Anim highlightY = new Anim(0f, 14f);   // 分类侧栏"流动"高亮条
    private final Anim contentScroll = new Anim(0f, 16f); // 模块列表滚动

    // ---- UI 元素动画 ----
    private final Anim sidebarIndicatorY = new Anim(0f, 14f); // 侧栏选中指示器滑动（~300ms EaseOutExpo）
    private float sidebarIndicatorTargetY = 0f;
    private final Anim configPageAnim = new Anim(0f, 14f); // 配置页滑入动画（0=关闭, 1=打开）
    private float cardHoverAlpha = 0f; // 卡片 hover 透明度

    // ---- 中英文语言系统 ----
    private boolean langZh = true; // 默认中文
    private float[] langBtnRect = null;

    // 布局参数
    private float panelX, panelY;
    private float panelW = 960f;  // 面板基准宽度 (真实南方: 1280, 我们缩小适配移动端)
    private float panelH = 600f;  // 面板基准高度 (真实南方: 800, 我们缩小适配移动端)
    private float sidebarW = 200f; // 侧栏宽度 (真实南方: 244px, 适配移动端)
    private float radius = 20f; // 面板圆角 (真实南方: 20px)
    private float uiScale = 1f; // 整体控件/字号缩放系数，基准是原始 620x400 设计尺寸

    // ---- HUD 样式系统：为后续多种 ArrayList HUD 样式做准备 ----
    // 0=Classic(渐变条+竖线), 1=Modern(纯色背景+圆角), 2=Outline(描边无背景)
    private int arrayListStyle = 0;

    // 内容列表的裁剪区域（屏幕坐标，每帧在 drawSidebarAndHeader 里重新计算）
    private float clipX, clipY, clipW, clipH;
    private float maxScroll = 0f;

    // 触摸命中区域缓存（每帧重建）
    // 这三个列表在 GL 渲染线程里每帧清空重建，又在 UI 触摸线程里读取——
    // 普通 ArrayList 没有任何同步保护，长按刚触发、重建最频繁的那一刻很容易撞上
    // "读到一半列表正被清空"从而抛出越界异常直接崩溃。改用 CopyOnWriteArrayList，
    // 迭代读取的是快照，永远不会因为另一个线程正在修改而抛异常。
    private final List<float[]> categoryHitRects = new java.util.concurrent.CopyOnWriteArrayList<>(); // x,y,w,h
    private final List<Object[]> moduleHitRects = new java.util.concurrent.CopyOnWriteArrayList<>();  // rect,type,module
    private final List<float[]> paletteHitRects = new java.util.concurrent.CopyOnWriteArrayList<>();  // x,y,w,h (每个色块一个)

    // ---- 配置页：点击卡片后内容区切换为配置页（页面切换，非模态弹窗） ----
    private ModuleItem activeConfigModule = null; // null=卡片网格视图, 非null=配置页视图
    private float configScrollOffset = 0f;
    private float configMaxScroll = 0f;
    private float[] configBackRect = null;     // 面包屑返回按钮热区
    private float[] configToggleRect = null;   // 配置页内开关热区
    private float configSliderX, configSliderW, configSliderY, configSliderH;
    private boolean draggingConfigSlider = false;
    // 配置页额外配置项（下拉框/开关/滑块）热区：每个元素为 {float[]{x,y,w,h}, optionIndex, optionType}
    private final List<Object[]> configOptionRects = new java.util.concurrent.CopyOnWriteArrayList<>();
    // 配置页额外滑块拖拽状态
    private int draggingOptionIndex = -1;
    private float draggingOptionSliderX, draggingOptionSliderW;
    // 旧弹窗字段保留兼容（标记为废弃，不再使用）
    @Deprecated private ModuleItem expandedModule = null;
    @Deprecated private final Anim overlayOpenness = new Anim(0f, 14f);
    @Deprecated private float[] overlayRect, overlayCloseRect, overlayToggleRect;
    @Deprecated private float overlaySliderX, overlaySliderW, overlaySliderY, overlaySliderH;
    @Deprecated private boolean draggingOverlaySlider = false;
    @Deprecated private float overlayScrollOffset = 0f;
    @Deprecated private float overlayMaxScroll = 0f;
    @Deprecated private boolean draggingOverlayScroll = false;
    @Deprecated private float overlayScrollDownY = 0f, overlayScrollStartOffset = 0f;

    private float dragStartY = -1;
    private float scrollStartOffset = 0;
    // 滑动误触修正：记录按下坐标，MOVE事件中如果垂直移动超过阈值标记为滑动，UP时跳过EXPAND点击
    private float touchDownX = -1, touchDownY = -1;
    private boolean touchMoved = false;
    // 弹窗关闭标记：当ACTION_DOWN中关闭了弹出式配置面板后，对应的ACTION_UP不再触发EXPAND
    private boolean overlayJustClosed = false;
    // 整行长按展开配置面板：候选模块+按下时间戳，真正触发在 onDrawFrame 里按经过时长判断
    // @deprecated - replaced by inline card expand (Task 8 移除了长按触发机制)
    private ModuleItem longPressCandidate = null; // @deprecated - replaced by inline card expand
    private long longPressStartNs = 0; // @deprecated - replaced by inline card expand
    private boolean longPressFired = false; // @deprecated - replaced by inline card expand
    private static final long LONG_PRESS_NS = 420_000_000L; // @deprecated - replaced by inline card expand (420ms)
    // 模块行错落进场动画的独立计时——不能直接复用面板开合的那条指数曲线，
    // 那条曲线大概0.3秒就饱和了，几行之间的错开延迟(几十毫秒)几乎看不出来。
    // 这里单独走一个"从0开始计时"的秒表，每行按固定延迟+固定时长过一遍缓动，效果才明显。
    private float openElapsedSec = 999f; // 初始给个大值，保证首次启动时所有行都是"已展开"状态
    // ArrayList 颜色流动偏移量：持续递增、永不重置，配合每行按位置算出的固定色相差，
    // 做出"整条渐变缓慢流动"的效果（像红->紫->蓝->白那种色带），而不是每行各自独立闪烁。
    private float flowOffset = 0f;
    // ---- 搜索框状态 ----
    private String searchQuery = "";
    private boolean searchActive = false;
    private float[] searchBoxRect = null;
    private float[] closeBtnRect = null;
    private float[] editHudRect = null;
    private float[] closeSideRect = null;
    private float[] footerKeybindRect = null;
    private float[] footerCloseRect = null;
    // ---- 卡片网格触摸状态 ----
    private boolean draggingCardSlider = false;
    private ModuleItem sliderDragModule = null;
    private float[] cardSliderRect = null;
    private final Context context;
    public UIRenderer(Context ctx) {
        this.context = ctx;
        this.loginRenderer = new LoginRenderer(ctx);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(WORLD_BG[0], WORLD_BG[1], WORLD_BG[2], 1f);
        quad.init();
        text.init();
        blur.init();
        loginRenderer.init(quad, text, blur);
        lastFrameNs = System.nanoTime();

        for (Category c : categories) {
            if (c.name.equals("Setting")) {
                for (ModuleItem m : c.modules) {
                    if (m.name.equals("Dark Theme")) darkThemeToggle = m;
                    if (m.name.equals("Blur Background")) blurEnabledToggle = m;
                    if (m.name.equals("Shadow Enabled")) shadowEnabledToggle = m;
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
        // 阴影参数模块已从Category中移除，改为在配置面板中显示；此处手动创建供配置面板使用
        shadowSyncToggle = new ModuleItem("阴影跟随主题", "阴影颜色是否跟随主题色", true);
        shadowHueSlider = new ModuleItem("阴影色相", "自定义阴影色相", false, true, 0f, 0, 1, "");
        shadowSizeSlider = new ModuleItem("阴影大小", "阴影扩散范围", false, true, 0.4f, 0, 1, "");
        shadowOpacitySlider = new ModuleItem("阴影透明度", "阴影不透明度", false, true, 0.5f, 0, 1, "");

        // ArrayList 渐变配置：同样不放进Category列表，只在它自己的配置面板里显示
        // 默认走单色白->灰渐变（新样式），开关打开后才切换成彩色主题渐变（旧的可选花哨风格）
        arrayListSyncToggle = new ModuleItem("使用彩色主题渐变", "关闭时使用下面两个亮度滑块的单色渐变", false);
        arrayListColor1Slider = new ModuleItem("顶部亮度", "列表顶部文字的亮度", false, true, 0.8f, 0, 1, "");
        arrayListColor2Slider = new ModuleItem("底部亮度", "列表底部文字的亮度", false, true, 0.5f, 0, 1, "");
        arrayListGlassToggle = new ModuleItem("玻璃镂空效果", "条形内部露出真实背景，只留边缘发散的阴影光晕", true);
        arrayListSortToggle = new ModuleItem("按宽度排序", "关闭后按固定顺序排列，不再从长到短", true);
        arrayListFontSizeSlider = new ModuleItem("字号", "列表文字大小", false, true, 0.3f, 0, 1, "");
        arrayListBgOpacitySlider = new ModuleItem("背景不透明度", "关闭玻璃镂空后，条形背景的不透明度", false, true, 0.4f, 0, 1, "");
        arrayListShadowSizeSlider = new ModuleItem("阴影扩散范围", "阴影模糊柔化的范围大小", false, true, 0.5f, 0, 1, "");
        arrayListShadowXSlider = new ModuleItem("阴影X偏移", "阴影往左/右偏移的位置", false, true, 0.667f, 0, 1, "");
        arrayListShadowYSlider = new ModuleItem("阴影Y偏移", "阴影往上/下偏移的位置", false, true, 0.667f, 0, 1, "");
        arrayListFlowSpeedSlider = new ModuleItem("渐变流动速度", "颜色渐变滚动的快慢", false, true, 0.4f, 0, 1, "");
        arrayListLineToggle = new ModuleItem("右侧竖线连接", "在ArrayList右边画一条带渐变的竖线，连接所有开启的模块", true);

        // 水印自定义配置：不放进Category列表，长按水印开关的那一行时展开
        watermarkSyncToggle = new ModuleItem("跟随主题渐变", "关闭后使用下面的自定义颜色", true);
        watermarkUnderlineToggle = new ModuleItem("显示下划线", "文字下方的强调色细线", true);
        watermarkFontSizeSlider = new ModuleItem("字号", "水印文字大小", false, true, 0.45f, 0, 1, "");
        watermarkXSlider = new ModuleItem("X 位置", "水印距离左边的距离", false, true, 0.1f, 0, 1, "");
        watermarkYSlider = new ModuleItem("Y 位置", "水印距离顶部的距离", false, true, 0.1f, 0, 1, "");
        watermarkHueSlider = new ModuleItem("自定义色相", "关闭跟随主题渐变后生效", false, true, 0.55f, 0, 1, "");
        watermarkShadowToggle = new ModuleItem("周围发散阴影", "文字周围轻微发散的柔光", true);

        // 开关提示自定义配置：不放进Category列表，长按 "Notification" 那一行展开
        notificationSizeSlider = new ModuleItem("大小", "提示条整体大小", false, true, 0.35f, 0, 1, "");
        notificationFontSizeSlider = new ModuleItem("字号", "提示文字大小", false, true, 0.35f, 0, 1, "");
        notificationSpacingSlider = new ModuleItem("间距", "多条提示堆叠的间距", false, true, 0.3f, 0, 1, "");
        notificationDurationSlider = new ModuleItem("停留时间", "提示在滑出前停留的时长", false, true, 0.4f, 0, 1, "");

        // 主题色预设：默认选中 Custom（索引 0）
        selectedThemeIndex = 0;
        applyThemeColors();
    }

    /** 根据 "Dark Theme" 开关状态，切换深色/浅色两套主题色 */
    private void updateTheme() {
        boolean light = darkThemeToggle != null && !darkThemeToggle.enabled;
        PANEL_BG = light ? LIGHT_PANEL_BG : DARK_PANEL_BG;
        SIDEBAR_BG = light ? LIGHT_SIDEBAR_BG : DARK_SIDEBAR_BG;
        CARD_BG = light ? LIGHT_CARD_BG : DARK_CARD_BG;
        TEXT_MAIN = light ? LIGHT_TEXT_MAIN : DARK_TEXT_MAIN;
        TEXT_DIM = light ? LIGHT_TEXT_DIM : DARK_TEXT_DIM;
        TRACK_OFF = light ? LIGHT_TRACK_OFF : DARK_TRACK_OFF;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        screenW = width;
        screenH = height;
        GLES20.glViewport(0, 0, width, height);
        blur.resize(width, height);

        // 面板尺寸按屏幕比例自适应，而不是写死像素值——
        // 之前 620x400 的固定值在高分辨率手机上占屏幕比例太小。
        // 面板尺寸按屏幕比例自适应（南方客户端 960:600 = 1.6:1 比例）
        panelW = Math.min(width * 0.92f, height * 1.6f);
        panelH = panelW * (600f / 960f);
        if (panelH > height * 0.9f) {
            panelH = height * 0.9f;
            panelW = panelH * (960f / 600f);
        }
        sidebarW = panelW * (200f / 960f);
        uiScale = panelH / 600f;
        radius = 20f * uiScale;
        ballRadius = 22f * uiScale;
    if (ballX < 0) {
            ballX = width - ballRadius - 24f;
            ballY = height - ballRadius - 40f;
        }
    }

    /**
    /** 模拟一个有层次感的"游戏世界"背景（天空渐变 + 漂浮色块），用来验证毛玻璃模糊效果 */
    private void drawWorldScene() {
        float t = sceneTime;
        quad.addRectGradient(0, 0, screenW, screenH, 0,
                new float[]{0.10f, 0.14f, 0.22f, 1f}, new float[]{0.10f, 0.14f, 0.22f, 1f},
                new float[]{0.02f, 0.03f, 0.05f, 1f}, new float[]{0.02f, 0.03f, 0.05f, 1f});
        for (int i = 0; i < 8; i++) {
            float bx = (screenW * (i / 8f) + t * 40f * (i % 2 == 0 ? 1 : -1)) % (screenW + 200) - 100;
            float by = screenH * 0.2f + (float) Math.sin(t * 0.6 + i) * 60f + i * 40f;
            float size = 50f + (i % 3) * 20f;
            float[] col = (i % 2 == 0) ? ACCENT_1 : ACCENT_2;
            quad.addRect(bx, by, size, size, 10f, new float[]{col[0], col[1], col[2], 0.5f});
        }
        quad.flush(screenW, screenH);
    }

    /** 每帧更新所有模块的HUD滑入/淡出动画——故意不依赖ClickGUI面板是否打开，
     *  这样面板收起后HUD该显示的还继续显示、该淡出的还继续淡出，不会卡住。 */
    /** 套用知名客户端风格的 ArrayList 预设——一次性设好一整套参数组合。
     *  0 玻璃质感：镂空玻璃条 + 单色渐变（默认这版的风格）
     *  1 彩虹流光：不要背景条，纯文字 + 跟随主题色的彩色流动渐变（类似 Sigma 一类的风格）
     *  2 极简白字：不要背景条，接近纯白的文字，阴影收到最小（类似 Rise/Novoline 一类的简洁风格）
     *  3 经典描边：不要背景条，白色文字配更明显的投影，靠阴影去区分文字和背景（类似 Wurst 一类的风格） */
    private void applyArrayListPreset(int idx) {
        selectedArrayListPreset = idx;
        switch (idx) {
            case 0:
                arrayListGlassToggle.enabled = true;
                arrayListSyncToggle.enabled = false;
                arrayListBgOpacitySlider.sliderValue = 0.4f;
                arrayListShadowSizeSlider.sliderValue = 0.5f;
                arrayListColor1Slider.sliderValue = 0.8f;
                arrayListColor2Slider.sliderValue = 0.5f;
                break;
            case 1:
                arrayListGlassToggle.enabled = false;
                arrayListSyncToggle.enabled = true;
                arrayListBgOpacitySlider.sliderValue = 0.02f;
                arrayListShadowSizeSlider.sliderValue = 0.35f;
                break;
            case 2:
                arrayListGlassToggle.enabled = false;
                arrayListSyncToggle.enabled = false;
                arrayListBgOpacitySlider.sliderValue = 0.0f;
                arrayListShadowSizeSlider.sliderValue = 0.15f;
                arrayListColor1Slider.sliderValue = 1.0f;
                arrayListColor2Slider.sliderValue = 0.9f;
                break;
            default:
                arrayListGlassToggle.enabled = false;
                arrayListSyncToggle.enabled = false;
                arrayListBgOpacitySlider.sliderValue = 0.0f;
                arrayListShadowSizeSlider.sliderValue = 0.7f;
                arrayListColor1Slider.sliderValue = 1.0f;
                arrayListColor2Slider.sliderValue = 1.0f;
                break;
        }
    }

    // ---- 中英文翻译 ----
    /** 翻译：中文模式返回 zh，英文模式返回 en */
    private String t(String zh, String en) {
        return langZh ? zh : en;
    }

    /** 切换语言 */
    private void toggleLanguage() {
        langZh = !langZh;
        spawnToast(langZh ? "已切换为中文" : "Switched to English");
    }

    /** 点击卡片：切换到该模块的配置页（页面切换，非弹窗） */
    private void toggleConfigPanelFor(ModuleItem targetM) {
        activeConfigModule = targetM;
        configScrollOffset = 0f;
        contentScroll.snapTo(0);
        configPageAnim.snapTo(0f);
        configPageAnim.setTarget(1f);
    }

    /** 返回卡片网格视图 */
    private void backToCardGrid() {
        activeConfigModule = null;
        configScrollOffset = 0f;
        configPageAnim.setTarget(0f);
    }

    /** 模块行的错落进场动画：面板刚展开时，越往下的行越晚出现，带一点回弹和轻微上浮位移。
     *  面板关闭时反过来越往下的行越早消失。这样整个ClickGUI不只是外框缩放，内容本身也在动。 */
    private float rowRevealEase(float panelOpenT, int rowIndex) {
        if (!guiOpen) {
            // 正在关闭：不做错落，跟着面板透明度统一淡出就好，避免关闭途中还有行"没消失完"的怪异感
            return 1f;
        }
        float delay = rowIndex * 0.055f; // 每往下一行多等 55ms
        float dur = 0.26f;               // 每行自己的过渡时长
        float t = (openElapsedSec - delay) / dur;
        t = Math.max(0f, Math.min(1f, t));
        return Anim.backEaseOut(t);
    }

    /** 每帧从 Client 分类刷新 HUD 相关模块引用。
     *  原来这些引用只在 onSurfaceCreated 里按旧的 "Setting"/"Home" 分类名查找一次，
     *  分类名对不上时引用一直是 null，用户在配置页切换开关后 HUD 也不会响应。
     *  现在改为每帧按 Client 分类重新查找，保证开关状态实时生效。
     *  注意：保留原有变量声明与 onSurfaceCreated 里的初始化代码，这里只是每帧覆盖刷新。 */
    private void refreshHudModuleRefs() {
        for (Category c : categories) {
            if (!c.name.equals("Client")) continue;
            for (ModuleItem m : c.modules) {
                switch (m.name) {
                    case "ArrayList": arrayListToggle = m; break;
                    case "Watermark": watermarkToggle = m; break;
                    case "Notifications": notificationToggle = m; break;
                    case "ClickGUI": clickGuiModule = m; break;
                }
            }
        }
    }

    private void updateHudAnimations(float dt) {
        for (Category c : categories) {
            for (ModuleItem m : c.modules) {
                m.hudAnim.setTarget(m.enabled ? 1f : 0f);
                m.hudAnim.update(dt);
            }
        }
    }

    /** ArrayList 每一行的颜色——不管是单色模式还是彩色主题模式，都必须是动态流动的，
     *  用 flowOffset（持续递增、永不重置）驱动，而不是按位置写死的静态渐变。
     *  默认（arrayListSyncToggle 关）：亮度在"顶部亮度"和"底部亮度"之间缓慢流动的单色渐变。
     *  开启 arrayListSyncToggle 后：换成跟随主题强调色的彩色流动渐变（可选的花哨风格）。 */
    private float[] arrayListRowColor(int i, int count) {
        boolean colorful = arrayListSyncToggle != null && arrayListSyncToggle.enabled;
        float t = count <= 1 ? 0f : i / (float) (count - 1);
        float phase = t + flowOffset;
        phase = phase - (float) Math.floor(phase); // 取模到 0..1，持续滚动

        if (colorful) {
            return themeFlowColor(phase, i);
        }
        // 单色渐变：两个滑块分别控制顶部/底部的亮度（灰度），用同一个流动相位驱动
        float topLight = arrayListColor1Slider != null ? (0.75f + arrayListColor1Slider.sliderValue * 0.25f) : 0.95f;
        float botLight = arrayListColor2Slider != null ? (0.45f + arrayListColor2Slider.sliderValue * 0.35f) : 0.62f;
        float mixAmt2 = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);
        float light = topLight + (botLight - topLight) * mixAmt2;
        return new float[]{light, light, light * 1.02f, 1f}; // 极轻微偏冷调，跟纯灰区分开
    }

    private void drawArrayListHud() {
        if (arrayListToggle != null && !arrayListToggle.enabled) return; // 总开关关掉，整个列表不画

        // 扫描所有分类，收集所有开启中的模块（不再只看单个 "Module" 分类）
        List<ModuleItem> allEnabled = new ArrayList<>();
        for (Category c : categories) {
            if (c.name.equals("Preferences")) continue; // 偏好设置不算功能模块
            for (ModuleItem m : c.modules) {
                if (m.enabled || m.hudAnim.get() > 0.01f) allEnabled.add(m);
            }
        }
        if (allEnabled.isEmpty()) return;

        float fontSize = (11f + (arrayListFontSizeSlider != null ? arrayListFontSizeSlider.sliderValue : 0.3f) * 9f) * uiScale;
        float barH = fontSize + 10f * uiScale;  // 条形本身就是一整行，稍微高一点，容得下文字上下留白
        float rowH = barH;                        // 行高=条形高度，让条形彼此相连、不留缝隙、也不重叠
        float marginTop = screenH / 8f;           // 顶部起始位置：屏幕顶部往下 1/8 处
        boolean showLine = arrayListLineToggle == null || arrayListLineToggle.enabled;
        float lineW = 2.5f * uiScale;
        float marginRight = showLine ? (3f * uiScale + lineW) : 3f * uiScale; // 竖线紧贴条形右侧，中间不留缝隙
        float textPad = 10f * uiScale;            // 文字到条形边缘的内边距，确保不会溢出
        boolean glassMode = arrayListGlassToggle == null || arrayListGlassToggle.enabled;
        boolean sortByWidth = arrayListSortToggle == null || arrayListSortToggle.enabled;
        float bgOpacity = arrayListBgOpacitySlider != null ? (0.15f + arrayListBgOpacitySlider.sliderValue * 0.55f) : 0.38f;
        float shadowSpread = (2f + (arrayListShadowSizeSlider != null ? arrayListShadowSizeSlider.sliderValue : 0.5f) * 10f) * uiScale;

        // visible 直接复用 allEnabled（已过滤过 enabled/hudAnim）
        List<ModuleItem> visible = allEnabled;
        if (visible.isEmpty()) return;

        // 每一行要显示的完整文字：有参数的模块在名字后面跟一个数值（比如 "Speed 3"），
        // 纯开关模块只显示名字本身
        final float measureFontSize = fontSize;
        List<String> lineList = new ArrayList<>();
        for (ModuleItem m : visible) {
            String dispName = Category.localizedName(m.name, langZh);
            lineList.add(m.hasSlider ? (dispName + " " + String.format("%.0f%s", m.getRealValue(), m.sliderUnit)) : dispName);
        }

        // 排序：默认按“肉眼可见的像素宽度”从长到短排序，不能用字符数/字节数排序——
        // 中英文混排时字符数跟实际显示宽度完全不是一回事，用 text.measure 量出来的真实宽度来排。
        // 关掉"按宽度排序"后就用固定的注册顺序。
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

        // ---- 阴影：跟 ClickGUI 面板边缘阴影用的是同一套技术——把所有条形当成一个整体
        // 画进透明背景的贴图，过一遍真正的高斯模糊，透明度从贴近条形的地方往外逐渐变浅。
        // 因为是整体一起模糊的，转折处（宽度变化的地方）自然是圆润连续的，不会有生硬的接缝。
        final float shadowShiftX = arrayListShadowXSlider != null ? (-15f + arrayListShadowXSlider.sliderValue * 30f) * uiScale : 5f * uiScale;
        final float shadowShiftY = arrayListShadowYSlider != null ? (-15f + arrayListShadowYSlider.sliderValue * 30f) * uiScale : 5f * uiScale;
        final List<ModuleItem> shadowModules = sortedModules;
        final List<String> shadowLines = sortedLines;
        final float sFontSize = fontSize, sRowH = rowH, sBarH = barH, sMarginTop = marginTop, sMarginRight = marginRight, sTextPad = textPad;
        final float sSpread = shadowSpread;
        blur.captureAndBlurTransparent(() -> {
            for (int i = 0; i < shadowModules.size(); i++) {
                ModuleItem m = shadowModules.get(i);
                float a = Math.max(0f, Math.min(1f, m.hudAnim.get()));
                if (a <= 0.01f) continue;
                float rowY = sMarginTop + i * sRowH;
                float tw = text.measure(shadowLines.get(i), sFontSize);
                float slideOffset = (1f - Anim.backEaseOut(a)) * 24f * uiScale;
                float barW = tw + sTextPad * 2f;
                float barX = screenW - sMarginRight - barW + slideOffset;
                // 扩散范围：把源形状往四周扩大一点再模糊，散得更开；X/Y偏移单独控制阴影的整体位置
                quad.addRect(barX + shadowShiftX - sSpread, rowY + shadowShiftY - sSpread,
                        barW + sSpread * 2f, sBarH + sSpread * 2f, sSpread, new float[]{1f, 1f, 1f, a});
            }
            quad.flush(screenW, screenH);
        });
        blur.drawGlow(new float[]{0f, 0f, 0f, 0.7f}, screenW, screenH);

        // ---- 条形本体 ----
        // 玻璃镂空模式：不画任何遮色，而是用 scissor 只在每条的范围内重新画一遍干净的背景场景，
        // 把刚才那层阴影"擦掉"露出真实背景——这样条形内部完全通透无色，只有边缘的模糊阴影可见，
        // 是真正的"镂空玻璃"效果，不是拿半透明白色去冒充。
        // 关掉之后退回传统的半透明毛玻璃条（不透明度可调）。
        for (int i = 0; i < sortedModules.size(); i++) {
            ModuleItem m = sortedModules.get(i);
            float a = Math.max(0f, Math.min(1f, m.hudAnim.get()));
            if (a <= 0.01f) continue;
            float rowY = marginTop + i * rowH;
            float tw = text.measure(sortedLines.get(i), fontSize);
            float slideOffset = (1f - Anim.backEaseOut(a)) * 24f * uiScale;
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

        // ---- 竖线连接：紧贴在所有条形的右侧，画一条带渐变的竖线，把开启的模块串起来 ----
        if (showLine) {
            int visCount = 0;
            for (int i = 0; i < sortedModules.size(); i++) {
                ModuleItem m = sortedModules.get(i);
                float a = Math.max(0f, Math.min(1f, m.hudAnim.get()));
                if (a <= 0.01f) continue;
                visCount++;
            }
            if (visCount > 0) {
                float lineX = screenW - marginRight; // 紧贴条形右边缘，中间零间隙
                float lineTop = marginTop; // 跟第一条的顶部对齐
                float lineBottom = marginTop + visCount * rowH; // 跟最后一条的底部对齐
                float lineH = Math.max(2f * uiScale, lineBottom - lineTop);
                float[] topCol = arrayListRowColor(0, visCount);
                float[] botCol = arrayListRowColor(Math.max(0, visCount - 1), visCount);
                quad.addRectGradient(lineX, lineTop, lineW, lineH, 1.2f * uiScale,
                        topCol, topCol, botCol, botCol);
                quad.flush(screenW, screenH);
            }
        }

        // ---- 文字：条形内垂直居中，右对齐并留出内边距，绝不会超出条形范围 ----
        for (int i = 0; i < sortedModules.size(); i++) {
            ModuleItem m = sortedModules.get(i);
            float a = Math.max(0f, Math.min(1f, m.hudAnim.get()));
            if (a <= 0.01f) continue;

            float rowY = marginTop + i * rowH;
            String line = sortedLines.get(i);
            float tw = text.measure(line, fontSize);
            float slideOffset = (1f - Anim.backEaseOut(a)) * 24f * uiScale;
            float tx = screenW - marginRight - textPad - tw + slideOffset;
            float ty = rowY + (barH - fontSize) / 2f; // 垂直居中

            float[] col = arrayListRowColor(i, sortedModules.size());
            text.draw(line, tx, ty, fontSize, new float[]{col[0], col[1], col[2], a}, screenW, screenH);
        }
    }

    /** 水印："Rlin" 固定显示在左上角，跟 Home 分类里的 "Watermark" 开关联动。
     *  只做淡入淡出，不带滑动位移——参考简约风格客户端水印那种干净利落的观感，
     *  文字下面配一条跟随强调色流动的细下划线做点缀。 */
    /** 每帧更新所有提示的年龄/堆叠位置/滑入滑出进度。
     *  堆叠规则：最早创建的排在最下面(index 0)，新来的堆在上面；
     *  最下面那条倒计时结束、开始滑出之后，上面的会因为 index 变小而自动被 posY 的
     *  指数平滑追到新的目标位置，效果就是"上方平滑滑到下方"，不需要额外写位移逻辑。 */
    /** 提示条的实际宽高（跟随"大小"滑块），updateToasts 和 drawToasts 共用同一份计算，
     *  这样堆叠间距才能跟实际尺寸对得上，不会出现"间距算的是旧尺寸"的错位。 */
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
        float rowSpacing = notifRectH() + 8f * uiScale * spacingMul; // 条形本身高度 + 8px 基准间隙（南方风格），"间距"滑块可调
        float holdDuration = notificationDurationSlider != null ? (1.0f + notificationDurationSlider.sliderValue * 3.0f) : TOAST_HOLD;
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

    /** 通知提示：初版玻璃模糊背景+阴影，单行文字，底部进度条。
     *  从屏幕右侧滑入、贴近右下角；多条垂直堆叠；显示约 2 秒后淡出滑出。 */
    private void drawToasts() {
        if (toasts.isEmpty()) return;

        float rectW = notifRectW(), rectH = notifRectH();
        float baseX = screenW - 10f * uiScale - rectW;
        float offDist = Math.max(screenW, screenH) * 0.35f;

        float holdDuration = notificationDurationSlider != null ? (1.0f + notificationDurationSlider.sliderValue * 3.0f) : TOAST_HOLD;
        float fontSize = 14f * uiScale * (notificationFontSizeSlider != null ? (0.75f + notificationFontSizeSlider.sliderValue * 0.7f) : 1f);
        float cornerR = 10f * uiScale;
        float padX = 14f * uiScale;
        float[] glassTint = {0f, 0f, 0f, 0.45f}; // 深色半透明玻璃

        for (ToastNotice t : toasts) {
            float rv = Math.max(0f, Math.min(1f, t.reveal.get()));
            if (rv <= 0.01f) continue;
            float slide = (1f - smoothstep(rv)) * offDist;
            float dx = baseX + slide, dy = t.posY.get() + slide;

            // 1) 玻璃模糊背景 + 阴影（初版样式）
            blur.drawGlassPanel(dx, dy, rectW, rectH, cornerR, glassTint, rv, screenW, screenH);

            // 2) 文字：单行，白色 + 黑色阴影
            float textY = dy + rectH / 2f - fontSize / 2f;
            text.draw(t.text, dx + padX + 1f * uiScale, textY + 1.2f * uiScale, fontSize, new float[]{0f, 0f, 0f, 0.5f * rv}, screenW, screenH);
            text.draw(t.text, dx + padX, textY, fontSize, new float[]{1f, 1f, 1f, rv}, screenW, screenH);

            // 3) 底部进度条
            float frac = Math.max(0f, Math.min(1f, 1f - (t.age - TOAST_ENTER) / holdDuration));
            float pbH = 2.5f * uiScale;
            quad.addRect(dx, dy + rectH - pbH, rectW * frac, pbH, 0f, withAlpha(ACCENT_1, 0.7f * rv));
            quad.flush(screenW, screenH);
        }
    }

    private float smoothstep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private void drawWatermark() {
        if (watermarkToggle == null) return;
        float a = Math.max(0f, Math.min(1f, watermarkToggle.hudAnim.get()));
        if (a <= 0.01f) return;

        String text2 = "Rlin";
        float fontSize = (14f + (watermarkFontSizeSlider != null ? watermarkFontSizeSlider.sliderValue : 0.45f) * 20f) * uiScale;
        float tx = (watermarkXSlider != null ? watermarkXSlider.sliderValue : 0.1f) * (screenW * 0.4f);
        float ty = (watermarkYSlider != null ? watermarkYSlider.sliderValue : 0.1f) * (screenH * 0.3f);

        boolean colorful = watermarkSyncToggle == null || watermarkSyncToggle.enabled;
        float[] col = colorful
                ? mixColor(ACCENT_1, ACCENT_2, 0.5f + 0.5f * (float) Math.sin(flowOffset * Math.PI * 2.0))
                : hsv2rgb(watermarkHueSlider != null ? watermarkHueSlider.sliderValue : 0.55f, 0.75f, 1f);

        float tw = text.measure(text2, fontSize);

        // 文字周围轻微发散的阴影：复用跟ArrayList一样的紧凑模糊管线——
        // 把文字整体画到透明背景贴图上模糊一遍，再叠加成柔光，可以在配置面板里关掉
        if (watermarkShadowToggle == null || watermarkShadowToggle.enabled) {
            final float sTx = tx, sTy = ty, sFontSize = fontSize;
            final String sText = text2;
            blur.captureAndBlurTransparentTight(() -> {
                text.draw(sText, sTx, sTy, sFontSize, new float[]{1f, 1f, 1f, a}, screenW, screenH);
            });
            blur.drawGlow(new float[]{0f, 0f, 0f, 0.55f * a}, screenW, screenH);
        }

        // 简单的“加粗”技巧：同一行文字用极小的偏移多画一次，笔画更扎实一点
        text.draw(text2, tx + 1.2f * uiScale, ty + 1.8f * uiScale, fontSize, new float[]{0f, 0f, 0f, 0.5f * a}, screenW, screenH);
        text.draw(text2, tx + 0.6f * uiScale, ty, fontSize, new float[]{col[0], col[1], col[2], a}, screenW, screenH);
        text.draw(text2, tx, ty, fontSize, new float[]{col[0], col[1], col[2], a}, screenW, screenH);

        // 文字下方一条细细的强调色下划线，跟着渐变一起流动，作为点缀——可以关掉
        if (watermarkUnderlineToggle == null || watermarkUnderlineToggle.enabled) {
            float underlineY = ty + fontSize + 2f * uiScale;
            quad.addRectGradient(tx, underlineY, tw, 2f * uiScale, 1f * uiScale,
                    withAlpha(colorful ? ACCENT_1 : col, a), withAlpha(colorful ? ACCENT_2 : col, a),
                    withAlpha(colorful ? ACCENT_1 : col, a), withAlpha(colorful ? ACCENT_2 : col, a));
            quad.flush(screenW, screenH);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000f);
        lastFrameNs = now;

        // ---- 登录界面阶段 ----
        if (loginActive) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            loginRenderer.onDrawFrame(dt, screenW, screenH);
            if (loginRenderer.isFadeOutComplete()) {
                loginActive = false; // 淡出完成，切换到主界面
                // 初始状态：面板关闭，悬浮球在左上角
                guiOpen = false;
                openness.snapTo(0f);
                ballX = ballRadius + 24f;
                ballY = ballRadius + 24f;
            } else if (loginRenderer.isLoginDone()) {
                // loginDone 但淡出尚未完成，继续渲染登录界面（淡出中）
            }
            return; // 登录界面期间不渲染主面板
        }

        sceneTime += dt;
        float globalSpeedMul = globalGradientSpeedSlider != null ? (0.3f + globalGradientSpeedSlider.sliderValue * 4f) : 1f;
        float flowSpeedMul = arrayListFlowSpeedSlider != null ? (0.3f + arrayListFlowSpeedSlider.sliderValue * 4f) : 1f;
        flowOffset += dt * 0.15f * globalSpeedMul * flowSpeedMul; // 缓慢持续流动，不重置；全局速度 × ArrayList自己的速度
        if (guiOpen) openElapsedSec += dt;
        highlightY.update(dt);
        contentScroll.update(dt);
        openness.update(dt);
        overlayOpenness.update(dt);
        updateTheme();

        float t = Math.max(0f, Math.min(1f, openness.get()));
        float easedT = Anim.backEaseOut(t);
        float panelAlpha = t;

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(WORLD_BG[0], WORLD_BG[1], WORLD_BG[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        panelX = screenW / 2f - panelW / 2f;
        panelY = screenH / 2f - panelH / 2f;

        // 面板开关动画：从 0.85 缩放到 1.0，中心不变，带轻微回弹；openness=0 时完全不画
        float scale = 0.85f + 0.15f * easedT;
        float drawW = panelW * scale;
        float drawH = panelH * scale;
        float drawX = screenW / 2f - drawW / 2f;
        float drawY = screenH / 2f - drawH / 2f;

        // 1) 背景"世界"正常画到屏幕上（可见部分）
        drawWorldScene();

        // 1.2) ArrayList HUD：跟ClickGUI面板是否打开完全无关，游戏里随时都该显示
        refreshHudModuleRefs();
        updateHudAnimations(dt);
        // UI 元素动画更新
        sidebarIndicatorY.setTarget(sidebarIndicatorTargetY);
        sidebarIndicatorY.update(dt);
        configPageAnim.update(dt);
        drawArrayListHud();
        drawWatermark();
        updateToasts(dt);
        drawToasts();

        // 长按展开已由卡片箭头点击替代，不再需要
        // if (longPressCandidate != null && !longPressFired
        //         && (System.nanoTime() - longPressStartNs) > LONG_PRESS_NS) {
        //     toggleConfigPanelFor(longPressCandidate);
        //     longPressFired = true;
        // }

        if (panelAlpha > 0.003f) {
            // 1.5) 面板外边缘阴影：真正做高斯模糊出来的柔光，而不是分层矩形硬边；带总开关
            boolean shadowOn = shadowEnabledToggle == null || shadowEnabledToggle.enabled;
            if (shadowOn) {
                boolean sync = shadowSyncToggle == null || shadowSyncToggle.enabled;
                float[] shadowColor = sync ? ACCENT_1 : hsv2rgb(shadowHueSlider != null ? shadowHueSlider.sliderValue : 0f, 0.6f, 0.55f);
                float spreadPx = (4f + (shadowSizeSlider != null ? shadowSizeSlider.sliderValue : 0.4f) * 40f) * uiScale;
                float opacity = 0.20f + (shadowOpacitySlider != null ? shadowOpacitySlider.sliderValue : 0.5f) * 0.55f;
                float sx = drawX - spreadPx, sy = drawY - spreadPx, sw = drawW + spreadPx * 2, sh = drawH + spreadPx * 2, sr = radius + spreadPx;
                blur.captureAndBlurTransparent(() -> {
                    quad.addRect(sx, sy, sw, sh, sr, new float[]{1f, 1f, 1f, 1f});
                    quad.flush(screenW, screenH);
                });
                // ClickGUI 模块开关联动面板背景阴影：模块关闭时阴影透明度降到 0.3
                // （ClickGUI 模块实际始终 enabled，这里主要是逻辑上的关联）
                float clickGuiShadowMul = (clickGuiModule != null && !clickGuiModule.enabled) ? 0.3f : 1f;
                blur.drawGlow(new float[]{shadowColor[0], shadowColor[1], shadowColor[2], opacity * panelAlpha * clickGuiShadowMul}, screenW, screenH);
            }

            // 2) 背景模糊：可以整体关掉，关掉后走纯色面板兜底路径（不再截取/模糊场景）
            boolean blurOn = blurEnabledToggle == null || blurEnabledToggle.enabled;
            // 关闭GUI时快速跳过模糊渲染避免残余面板滞留
            boolean nearClosed = t < 0.08f;
            if (blurOn && !nearClosed) {
                blur.captureAndBlur(this::drawWorldScene);
                float[] glassTint = {PANEL_BG[0], PANEL_BG[1], PANEL_BG[2], 0.55f * panelAlpha};
                blur.drawGlassPanel(drawX, drawY, drawW, drawH, radius, glassTint, panelAlpha, screenW, screenH);
            } else {
                quad.addRect(drawX, drawY, drawW, drawH, radius, withAlpha(PANEL_BG, panelAlpha));
                quad.flush(screenW, screenH);
            }

            // 4) 侧栏/分类/调色板等不裁剪的前景元素
            drawSidebarAndHeader(drawX, drawY, drawW, drawH, panelAlpha);
            quad.flush(screenW, screenH);
            drawHeaderBar(drawX, drawY, drawW, drawH, panelAlpha);
            quad.flush(screenW, screenH);
            drawFooterBar(drawX, drawY, drawW, drawH, panelAlpha);
            quad.flush(screenW, screenH);

            // 5) 内容区：根据状态切换卡片网格或配置页
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor((int) clipX, (int) (screenH - (clipY + clipH)), (int) clipW, (int) clipH);
            float pageSlide = Anim.backEaseOut(configPageAnim.get()) * 20f * uiScale; // 配置页从右侧滑入
            float pageAlpha = configPageAnim.get();
            if (activeConfigModule != null || configPageAnim.get() > 0.01f) {
                drawConfigPage(drawX + pageSlide, drawY, drawW, drawH, panelAlpha * pageAlpha);
            }
            if (activeConfigModule == null && configPageAnim.get() < 0.99f) {
                // 配置页关闭动画期间，卡片网格淡入
                drawModuleCards(drawX, drawY, drawW, drawH, panelAlpha);
            } else if (activeConfigModule == null) {
                drawModuleCards(drawX, drawY, drawW, drawH, panelAlpha);
            }
            quad.flush(screenW, screenH);
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

            drawTextLayer(drawX, drawY, drawW, drawH, panelAlpha);

            // 6) 文字层同样要在裁剪区域内画
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor((int) clipX, (int) (screenH - (clipY + clipH)), (int) clipW, (int) clipH);
            if (activeConfigModule != null) {
                drawConfigPageText(drawX, drawY, drawW, drawH, panelAlpha);
            } else {
                drawModuleCardText(drawX, drawY, drawW, drawH, panelAlpha);
            }
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        }

        // 7) 配置页已在内容区内绘制（页面切换，非弹窗）

        // 8) 悬浮球始终画在最上层，用来开关整个面板
        drawFloatingBall();
    }

    /** 配置页：在内容区内绘制模块配置选项（页面切换，非模态弹窗） */
    private void drawConfigPage(float x, float y, float w, float h, float alpha) {
        ModuleItem m = activeConfigModule;
        if (m == null) return;

        float sbW = sidebarW * (w / panelW);
        float pad = 16f * uiScale * (h / panelH);
        float contentX = x + sbW + pad;
        float contentW = w - sbW - pad * 2;
        float hdrH = 72f * uiScale * (h / panelH);
        float titleH = 30f * uiScale * (h / panelH);
        float startY = y + hdrH + titleH - configScrollOffset;

        float rowH = 48f * uiScale * (h / panelH);
        float rowGap = 8f * uiScale * (h / panelH);
        float cornerR = 10f * uiScale;
        float trackX = contentX;
        float trackW = contentW;

        // 清空配置页热区
        configToggleRect = null;
        configSliderX = 0; configSliderW = 0; configSliderY = 0; configSliderH = 0;
        configOptionRects.clear();
        // 清空调色板坐标（仅 Theme Color 模块会重新设置）
        hueBarX = 0; hueBarW = 0; hueBarY = 0; hueBarH = 0;
        svBoxX = 0; svBoxY = 0; svBoxW = 0; svBoxH = 0;
        paletteHitRects.clear();

        float curY = startY;

        // --- "Enabled" 开关行 ---
        quad.addRect(trackX, curY, trackW, rowH, cornerR, withAlpha(SOUTH_MODULE_BG, alpha));
        float toggleW = 44f * uiScale, toggleH = 22f * uiScale;
        float toggleX = trackX + trackW - pad - toggleW;
        float toggleY = curY + rowH / 2f - toggleH / 2f;
        configToggleRect = new float[]{toggleX, toggleY, toggleW, toggleH};
        m.toggleAnim.setTarget(m.enabled ? 1f : 0f);
        m.toggleAnim.update(0.016f);
        float tst = m.toggleAnim.get();
        float[] tc = mixColor(SOUTH_TOGGLE_OFF, SOUTH_TOGGLE_ON, tst);
        quad.addRect(toggleX, toggleY, toggleW, toggleH, toggleH / 2f, withAlpha(tc, alpha));
        float tknobD = toggleH - 6f * uiScale;
        float tknobX = toggleX + 3f * uiScale + (toggleW - tknobD - 6f * uiScale) * tst;
        quad.addRect(tknobX, toggleY + 3f * uiScale, tknobD, tknobD, tknobD / 2f, withAlpha(KNOB_COLOR, alpha));
        curY += rowH + rowGap;

        // --- 模块额外配置项列表（下拉框 / 额外开关 / 额外滑块） ---
        for (int oi = 0; oi < m.options.size(); oi++) {
            ModuleItem.ConfigOption opt = m.options.get(oi);
            if (opt.type == ModuleItem.ConfigOption.TYPE_DROPDOWN) {
                // 下拉框行：整行背景 + 右侧可点击区域（点击循环切换选项）
                quad.addRect(trackX, curY, trackW, rowH, cornerR, withAlpha(SOUTH_MODULE_BG, alpha));
                configOptionRects.add(new Object[]{new float[]{trackX, curY, trackW, rowH}, oi, ModuleItem.ConfigOption.TYPE_DROPDOWN});
                curY += rowH + rowGap;
            } else if (opt.type == ModuleItem.ConfigOption.TYPE_SWITCH) {
                // 额外开关行：与 Enabled 开关行相同样式
                quad.addRect(trackX, curY, trackW, rowH, cornerR, withAlpha(SOUTH_MODULE_BG, alpha));
                float stToggleW = 44f * uiScale, stToggleH = 22f * uiScale;
                float stToggleX = trackX + trackW - pad - stToggleW;
                float stToggleY = curY + rowH / 2f - stToggleH / 2f;
                configOptionRects.add(new Object[]{new float[]{trackX, curY, trackW, rowH}, oi, ModuleItem.ConfigOption.TYPE_SWITCH});
                float stT = opt.boolValue ? 1f : 0f;
                float[] stTc = mixColor(SOUTH_TOGGLE_OFF, SOUTH_TOGGLE_ON, stT);
                quad.addRect(stToggleX, stToggleY, stToggleW, stToggleH, stToggleH / 2f, withAlpha(stTc, alpha));
                float stKnobD = stToggleH - 6f * uiScale;
                float stKnobX = stToggleX + 3f * uiScale + (stToggleW - stKnobD - 6f * uiScale) * stT;
                quad.addRect(stKnobX, stToggleY + 3f * uiScale, stKnobD, stKnobD, stKnobD / 2f, withAlpha(KNOB_COLOR, alpha));
                curY += rowH + rowGap;
            } else if (opt.type == ModuleItem.ConfigOption.TYPE_SLIDER) {
                // 额外滑块行：与主滑块行相同样式
                quad.addRect(trackX, curY, trackW, rowH, cornerR, withAlpha(SOUTH_MODULE_BG, alpha));
                float osTrackH = 5f * uiScale;
                float osTrackX = trackX + 14f * uiScale;
                float osTrackW = trackW - 28f * uiScale;
                float osTrackY = curY + rowH / 2f - osTrackH / 2f;
                quad.addRect(osTrackX, osTrackY, osTrackW, osTrackH, osTrackH / 2f, withAlpha(SOUTH_SLIDER_TRACK, alpha));
                float osFillW = osTrackW * opt.sliderValue;
                float[][] osDiag = diagGradient(ACCENT_1, ACCENT_2, alpha);
                quad.addRectGradient(osTrackX, osTrackY, Math.max(osTrackH, osFillW), osTrackH, osTrackH / 2f, osDiag[0], osDiag[1], osDiag[2], osDiag[3]);
                float osKnobD = 12f * uiScale;
                float osKnobX = osTrackX + osFillW - osKnobD / 2f;
                quad.addRect(osKnobX, osTrackY + osTrackH / 2f - osKnobD / 2f, osKnobD, osKnobD, osKnobD / 2f, withAlpha(KNOB_COLOR, alpha));
                // 热区用滑块轨道区域，并记录拖拽坐标基准
                configOptionRects.add(new Object[]{new float[]{osTrackX, osTrackY, osTrackW, osTrackH}, oi, ModuleItem.ConfigOption.TYPE_SLIDER});
                curY += rowH + rowGap;
            }
        }

        // --- 滑块行（仅有滑块的模块） ---
        if (m.hasSlider) {
            quad.addRect(trackX, curY, trackW, rowH, cornerR, withAlpha(SOUTH_MODULE_BG, alpha));
            float sTrackH = 5f * uiScale;
            float sTrackX = trackX + 14f * uiScale;
            float sTrackW = trackW - 28f * uiScale;
            float sTrackY = curY + rowH / 2f - sTrackH / 2f;
            quad.addRect(sTrackX, sTrackY, sTrackW, sTrackH, sTrackH / 2f, withAlpha(SOUTH_SLIDER_TRACK, alpha));
            float fillW = sTrackW * m.sliderValue;
            float[][] diag = diagGradient(ACCENT_1, ACCENT_2, alpha);
            quad.addRectGradient(sTrackX, sTrackY, Math.max(sTrackH, fillW), sTrackH, sTrackH / 2f, diag[0], diag[1], diag[2], diag[3]);
            float knobD = 12f * uiScale;
            float knobX = sTrackX + fillW - knobD / 2f;
            quad.addRect(knobX, sTrackY + sTrackH / 2f - knobD / 2f, knobD, knobD, knobD / 2f, withAlpha(KNOB_COLOR, alpha));
            configSliderX = sTrackX; configSliderW = sTrackW;
            configSliderY = sTrackY; configSliderH = sTrackH;
            curY += rowH + rowGap;
        }

        // --- 描述区 ---
        float descH = 80f * uiScale * (h / panelH);
        quad.addRect(trackX, curY, trackW, descH, cornerR, withAlpha(SOUTH_MODULE_BG, alpha));
        curY += descH + rowGap;

        // --- 调色板（仅 Preferences 分类下的 Theme 模块） ---
        if (m.name.equals("Theme") || m.name.equals("Theme Color")) {
            float pickerH = accentCardH(h);
            quad.addRect(trackX, curY, trackW, pickerH, cornerR, withAlpha(SOUTH_MODULE_BG, alpha));
            drawColorPicker(trackX, curY, trackW, alpha, h);
            curY += pickerH + rowGap;
        }

        // 计算滚动范围
        float totalContentH = curY - startY;
        float visibleH = clipH;
        configMaxScroll = Math.max(0f, totalContentH - visibleH);
        configScrollOffset = Math.max(0f, Math.min(configScrollOffset, configMaxScroll));
    }

    /** 配置页文字层 */
    private void drawConfigPageText(float x, float y, float w, float h, float alpha) {
        ModuleItem m = activeConfigModule;
        if (m == null) return;

        float sbW = sidebarW * (w / panelW);
        float pad = 16f * uiScale * (h / panelH);
        float contentX = x + sbW + pad;
        float contentW = w - sbW - pad * 2;
        float hdrH = 72f * uiScale * (h / panelH);
        float titleH = 30f * uiScale * (h / panelH);
        float startY = y + hdrH + titleH - configScrollOffset;

        float rowH = 48f * uiScale * (h / panelH);
        float rowGap = 8f * uiScale * (h / panelH);
        float curY = startY;

        // Enabled 行
        text.draw(t("启用", "Enabled"), contentX + 14f * uiScale, curY + rowH / 2f - 14f * uiScale * 0.5f, 14f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
        curY += rowH + rowGap;

        // 模块额外配置项文字
        for (int oi = 0; oi < m.options.size(); oi++) {
            ModuleItem.ConfigOption opt = m.options.get(oi);
            if (opt.type == ModuleItem.ConfigOption.TYPE_DROPDOWN) {
                // label 左侧
                String optLabel = Category.localizedName(opt.label, langZh);
                text.draw(optLabel, contentX + 14f * uiScale, curY + rowH / 2f - 14f * uiScale * 0.5f,
                        14f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
                // 当前选中项 + 下拉箭头 ▼ 右侧
                String curChoice = (opt.dropdownChoices != null && opt.dropdownIndex >= 0
                        && opt.dropdownIndex < opt.dropdownChoices.length) ? opt.dropdownChoices[opt.dropdownIndex] : "";
                String dropText = curChoice + " \u25BC";
                float dropSize = 13f * uiScale;
                float dropW = text.measure(dropText, dropSize);
                text.draw(dropText, contentX + contentW - 14f * uiScale - dropW,
                        curY + rowH / 2f - dropSize * 0.5f, dropSize, withAlpha(SOUTH_TEXT_DIM2, alpha), screenW, screenH);
                curY += rowH + rowGap;
            } else if (opt.type == ModuleItem.ConfigOption.TYPE_SWITCH) {
                // 开关：值由图形层显示，这里只画 label
                String optLabel = Category.localizedName(opt.label, langZh);
                text.draw(optLabel, contentX + 14f * uiScale, curY + rowH / 2f - 14f * uiScale * 0.5f,
                        14f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
                curY += rowH + rowGap;
            } else if (opt.type == ModuleItem.ConfigOption.TYPE_SLIDER) {
                // label 左侧 + 当前数值右侧
                String optLabel = Category.localizedName(opt.label, langZh);
                text.draw(optLabel, contentX + 14f * uiScale, curY + rowH / 2f - 14f * uiScale * 0.5f,
                        14f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
                String unit = opt.sliderUnit != null ? opt.sliderUnit : "";
                String valStr = String.format("%.1f%s", opt.getRealSliderValue(), unit);
                float valSize = 13f * uiScale;
                float valW = text.measure(valStr, valSize);
                text.draw(valStr, contentX + contentW - 14f * uiScale - valW,
                        curY + rowH / 2f - valSize * 0.5f, valSize, withAlpha(SOUTH_TEXT_DIM2, alpha), screenW, screenH);
                curY += rowH + rowGap;
            }
        }

        // 滑块行
        if (m.hasSlider) {
            String valStr = String.format("%.1f%s", m.getRealValue(), m.sliderUnit);
            float valW = text.measure(valStr, 13f * uiScale);
            text.draw(t("数值", "Value"), contentX + 14f * uiScale, curY + 12f * uiScale, 14f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
            text.draw(valStr, contentX + contentW - 14f * uiScale - valW, curY + 12f * uiScale, 13f * uiScale, withAlpha(SOUTH_TEXT_DIM2, alpha), screenW, screenH);
            curY += rowH + rowGap;
        }

        // 描述区
        float descH = 80f * uiScale * (h / panelH);
        text.draw(t("描述", "Description"), contentX + 14f * uiScale, curY + 14f * uiScale, 12f * uiScale, withAlpha(SOUTH_TEXT_DIM2, alpha), screenW, screenH);
        text.draw(m.description, contentX + 14f * uiScale, curY + 34f * uiScale, 13f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
        curY += descH + rowGap;

        // 调色板文字
        if (m.name.equals("Theme") || m.name.equals("Theme Color")) {
            text.draw(t("调色板", "Color Picker"), contentX + 14f * uiScale, curY + 14f * uiScale, 14f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
        }
    }

    /** 悬浮球：拖拽移动位置，单击（未拖动）时开关整个 ClickGUI 面板 */
private void drawFloatingBall() {
    if (ballX < 0) return;
    float r = ballRadius;

    // 判断当前是否为深色主题
    boolean isDark = darkThemeToggle == null || darkThemeToggle.enabled;
    // 外层大球与内层小球颜色相同：深色主题用白色，浅色主题用黑色
    float[] pureColor = isDark ? new float[]{1f, 1f, 1f, 1f} : new float[]{0f, 0f, 0f, 1f};

    // ---- 三层嵌套样式（无图标）----
    // 固定渐变：深紫色 → 浅紫色
    float[] DEEP_PURPLE = {0.40f, 0.15f, 0.60f, 1f};
    float[] LIGHT_PURPLE = {0.70f, 0.40f, 0.85f, 1f};

    // 1) 外层：纯色大球（黑/白根据主题）
    quad.addRect(ballX - r, ballY - r, r * 2, r * 2, r, pureColor);
    quad.flush(screenW, screenH);

    // 2) 中层：固定深紫→浅紫渐变球（比外层略小，留出外圈纯色环）
    float midR = r * 0.78f;
    float[][] midDiag = diagGradient(DEEP_PURPLE, LIGHT_PURPLE, 1f);
    quad.addRectGradient(ballX - midR, ballY - midR, midR * 2, midR * 2, midR,
            midDiag[0], midDiag[1], midDiag[2], midDiag[3]);
    quad.flush(screenW, screenH);

    // 3) 内层：纯色小球（与外层大球颜色相同）
    float innerR = r * 0.40f;
    quad.addRect(ballX - innerR, ballY - innerR, innerR * 2, innerR * 2, innerR, pureColor);
    quad.flush(screenW, screenH);
    // 没有图标，单纯三层嵌套
}

    private void drawSidebarAndHeader(float x, float y, float w, float h, float alpha) {
        float sbW = sidebarW * (w / panelW);
        float hdrH = 72f * uiScale * (h / panelH);

        // 侧栏背景
        quad.addRect(x, y, sbW, h, radius, withAlpha(SOUTH_SIDEBAR_BG, alpha));
        quad.addRect(x + sbW - radius, y, radius, h, 0f, withAlpha(SOUTH_SIDEBAR_BG, alpha));

        // 内容区背景
        float contentBgX = x + sbW;
        float contentBgW = w - sbW;
        quad.addRect(contentBgX, y, contentBgW, h, radius, withAlpha(SOUTH_PANEL_BG, alpha));
        quad.addRect(contentBgX, y + h - radius, contentBgW, radius, 0f, withAlpha(SOUTH_PANEL_BG, alpha));

        // 分隔线
        quad.addRect(x + sbW, y, 1f * uiScale, h, 0f, withAlpha(SOUTH_CARD_BORDER, alpha));
        // 顶部分隔线
        quad.addRect(contentBgX, y + hdrH, contentBgW, 1f * uiScale, 0f, withAlpha(SOUTH_CARD_BORDER, alpha));

        categoryHitRects.clear();

        // 分组侧栏
        float pad = 16f * uiScale * (h / panelH);
        float rowH = 36f * uiScale * (h / panelH);
        float bottomBtnH = 36f * uiScale * (h / panelH);
        float sectionLblH = 24f * uiScale * (h / panelH);
        float sectionGap = 12f * uiScale * (h / panelH);

        float curY = y + pad + 8f * uiScale * (h / panelH);
        String prevSection = null;

        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            if (prevSection == null || !c.section.equals(prevSection)) {
                curY += (prevSection != null ? sectionGap : 0f) + sectionLblH;
                prevSection = c.section;
            }
            float ry = curY;
            float btnX = x + 8f * uiScale * (w / panelW);
            float btnW = sbW - 16f * uiScale * (w / panelW);
            categoryHitRects.add(new float[]{btnX, ry, btnW, rowH});
            if (i == currentCategoryIndex) {
                highlightY.setTarget(ry - y);
                sidebarIndicatorTargetY = ry - y;
            }
            curY += rowH + 4f * uiScale * (h / panelH);
        }

        // 高亮条
        float hlRowH = rowH;
        float[] hlBg = withAlpha(SOUTH_CARD_BORDER_SEL, alpha * 0.15f);
        float hlX = x + 8f * uiScale * (w / panelW);
        float hlW = sbW - 16f * uiScale * (w / panelW);
        quad.addRect(hlX, y + highlightY.get(), hlW, hlRowH, 12f * uiScale, hlBg);

        // 底部按钮：Edit HUD + Close
        float bottomY = y + h - pad - bottomBtnH * 2 - 8f * uiScale * (h / panelH);
        editHudRect = new float[]{x + 8f * uiScale * (w / panelW), bottomY, sbW - 16f * uiScale * (w / panelW), bottomBtnH};
        closeSideRect = new float[]{x + 8f * uiScale * (w / panelW), bottomY + bottomBtnH + 4f * uiScale * (h / panelH), sbW - 16f * uiScale * (w / panelW), bottomBtnH};
        quad.addRect(editHudRect[0], editHudRect[1], editHudRect[2], editHudRect[3], 8f * uiScale, withAlpha(SOUTH_MODULE_BG, alpha));
        quad.addRect(closeSideRect[0], closeSideRect[1], closeSideRect[2], closeSideRect[3], 8f * uiScale, withAlpha(SOUTH_MODULE_BG, alpha));

        // 裁剪区域
        float contentX = x + sbW + pad;
        float contentW = w - sbW - pad * 2;
        float titleH = 30f * uiScale * (h / panelH);

        clipX = contentX - 2f;
        clipY = y + hdrH + titleH;
        clipW = contentW + 4f;
        clipH = (y + h - pad) - clipY;

        // 滚动范围
        Category cat = categories.get(currentCategoryIndex);
        float cardH = 110f * uiScale * (h / panelH);
        float cardGap = 16f * uiScale * (h / panelH);
        int cols = 3;
        float cardW = (contentW - cardGap * (cols - 1)) / cols;
        int rows = (int) Math.ceil(cat.modules.size() / (float) cols);
        float totalContentH = rows * (cardH + cardGap) - cardGap;
        maxScroll = Math.max(0f, totalContentH + pad + titleH - h + hdrH);
    }

    private void drawHeaderBar(float x, float y, float w, float h, float alpha) {
        float sbW = sidebarW * (w / panelW);
        float hdrH = 72f * uiScale * (h / panelH);
        float hdrX = x + sbW;
        float hdrW = w - sbW;

        // 标题栏背景
        quad.addRect(hdrX, y, hdrW, hdrH, 0f, withAlpha(HDR_BG, alpha));
        quad.addRect(hdrX, y, hdrW, radius, 0f, withAlpha(HDR_BG, alpha));

        // 面包屑返回按钮热区（仅在配置页模式下生效；卡片网格模式下置空）
        if (activeConfigModule != null) {
            float backW = 220f * uiScale * (w / panelW);
            configBackRect = new float[]{hdrX, y, backW, hdrH};
        } else {
            configBackRect = null;
        }

        // 搜索框 (右侧)
        float searchW = 160f * uiScale * (w / panelW);
        float searchH = 36f * uiScale * (h / panelH);
        float closeSize = 36f * uiScale * (h / panelH);
        float closeGap = 8f * uiScale * (w / panelW);
        float rightPad = 16f * uiScale * (w / panelW);
        float searchX = x + w - searchW - rightPad;
        float searchY = y + (hdrH - searchH) / 2f;
        quad.addRect(searchX, searchY, searchW, searchH, searchH / 2f, withAlpha(SOUTH_SEARCH_BG, alpha));
        quad.addRect(searchX, searchY, searchW, searchH, searchH / 2f, withAlpha(SOUTH_CARD_BORDER, alpha * 0.5f));
        searchBoxRect = new float[]{searchX, searchY, searchW, searchH};

        // 关闭按钮 (× 在搜索框左侧)
        float closeX = searchX - closeSize - closeGap;
        float closeY = y + (hdrH - closeSize) / 2f;
        if (closeX > hdrX + 8f * uiScale) {
            closeBtnRect = new float[]{closeX, closeY, closeSize, closeSize};
            quad.addRect(closeX, closeY, closeSize, closeSize, closeSize / 2f, withAlpha(SOUTH_SEARCH_BG, alpha));
        } else {
            closeBtnRect = null;
        }

        // 语言切换按钮（在关闭按钮左侧）
        float langW = 52f * uiScale * (w / panelW);
        float langH = closeSize;
        float langX = closeBtnRect != null ? closeX - langW - closeGap : searchX - langW - closeGap;
        float langY = y + (hdrH - langH) / 2f;
        if (langX > hdrX + 8f * uiScale) {
            langBtnRect = new float[]{langX, langY, langW, langH};
            quad.addRect(langX, langY, langW, langH, langH / 2f, withAlpha(SOUTH_SEARCH_BG, alpha));
            quad.addRect(langX, langY, langW, langH, langH / 2f, withAlpha(SOUTH_CARD_BORDER, alpha * 0.5f));
        } else {
            langBtnRect = null;
        }
    }

    private void drawFooterBar(float x, float y, float w, float h, float alpha) {
        // 南方客户端无底栏 — Close 在侧栏底部
        footerKeybindRect = null;
        footerCloseRect = null;
    }
    /** 主题色预设选择器的布局计算，绘制/触摸共用 */
    private static class AccentLayout {
        float labelY, btnStartY, btnH, btnGap, totalH;
        // 取色器布局
        float pickerTop, pickerH, svSize, hueH, swatchY, swatchSize;
    }
    private AccentLayout accentLayout(float contentW) {
        AccentLayout l = new AccentLayout();
        // 新版调色板布局：HSB 选色框 + 垂直色相条 + 当前色预览 + 预设色块(2行x6列)
        l.pickerTop = 8f * uiScale;
        l.svSize = 120f * uiScale;
        l.hueH = l.svSize; // 垂直色相条高度与选色框等高
        l.swatchSize = 24f * uiScale;
        int swatchCols = 6;
        float swatchGap = 6f * uiScale;
        int swatchRows = (int) Math.ceil(presetSwatches.length / (float) swatchCols);
        l.swatchY = l.pickerTop + l.svSize + 12f * uiScale;
        float pickerBottom = l.swatchY + swatchRows * (l.swatchSize + swatchGap);
        l.totalH = pickerBottom + 8f * uiScale;
        l.pickerH = pickerBottom - l.pickerTop;
        return l;
    }

    private float accentCardH(float h) {
        return accentLayout(1000).totalH;
    }
    private void drawModuleCards(float x, float y, float w, float h, float alpha) {
        moduleHitRects.clear();
        // 清空调色板坐标，避免卡片网格模式下命中残留的调色板热区
        hueBarX = 0; hueBarW = 0; hueBarY = 0; hueBarH = 0;
        svBoxX = 0; svBoxY = 0; svBoxW = 0; svBoxH = 0;
        paletteHitRects.clear();
        float sbW = sidebarW * (w / panelW);
        float pad = 16f * uiScale * (h / panelH);
        float contentX = x + sbW + pad;
        float contentW = w - sbW - pad * 2;
        float hdrH = 72f * uiScale * (h / panelH);
        float titleH = 30f * uiScale * (h / panelH);
        float startY = y + hdrH + titleH - contentScroll.get();

        Category cat = categories.get(currentCategoryIndex);
        float cardGap = 16f * uiScale * (h / panelH);
        int cols = 3;
        float cardW = (contentW - cardGap * (cols - 1)) / cols;
        float cardH = 110f * uiScale * (h / panelH);
        float toggleBarH = 32f * uiScale * (h / panelH);
        float iconAreaH = cardH - toggleBarH;

        java.util.List<ModuleItem> visibleModules = new java.util.ArrayList<>();
        for (ModuleItem m : cat.modules) {
            if (searchQuery.isEmpty() || m.name.toLowerCase().contains(searchQuery.toLowerCase())
                    || m.description.toLowerCase().contains(searchQuery.toLowerCase())) {
                visibleModules.add(m);
            }
        }

        float curY = startY;
        for (int i = 0; i < visibleModules.size(); i++) {
            ModuleItem m = visibleModules.get(i);
            int col = i % cols;
            float cardX = contentX + col * (cardW + cardGap);

            if (curY + cardH < clipY - 20f || curY > clipY + clipH + 20f) {
                if ((i + 1) % cols == 0) curY += cardH + cardGap;
                continue;
            }

            float reveal = rowRevealEase(alpha, i);
            if (reveal <= 0.003f) {
                if ((i + 1) % cols == 0) curY += cardH + cardGap;
                continue;
            }
            float rowAlpha = alpha * reveal;
            float drawY = curY + (1f - reveal) * 14f * uiScale;

            // 注册热区: 上方区域=切换开关，下方区域=展开配置面板
            moduleHitRects.add(new Object[]{new float[]{cardX, drawY, cardW, iconAreaH}, "TOGGLE", m});
            moduleHitRects.add(new Object[]{new float[]{cardX, drawY + iconAreaH, cardW, toggleBarH}, "CARD", m});

            float cornerR = 12f * uiScale;
            // 上半部分: 开关区 (圆角顶部) — 颜色随开关状态变化
            float[] toggleCol = m.enabled ? withAlpha(SOUTH_TOGGLE_ON, rowAlpha) : withAlpha(SOUTH_TOGGLE_OFF, rowAlpha);
            quad.addRect(cardX, drawY, cardW, iconAreaH, cornerR, toggleCol);
            // 补底部直角
            quad.addRect(cardX, drawY + iconAreaH - cornerR, cardW, cornerR, 0f, toggleCol);

            // 下半部分: 配置栏 (圆角底部) — 深色背景
            quad.addRect(cardX, drawY + iconAreaH, cardW, toggleBarH, cornerR, withAlpha(SOUTH_MODULE_BG, rowAlpha));
            // 补顶部直角
            quad.addRect(cardX, drawY + iconAreaH, cardW, cornerR, 0f, withAlpha(SOUTH_MODULE_BG, rowAlpha));

            // 分隔线
            quad.addRect(cardX, drawY + iconAreaH, cardW, 2f * uiScale, 0f, withAlpha(SOUTH_TAB_OFF, rowAlpha * 0.5f));

            if ((i + 1) % cols == 0) curY += cardH + cardGap;
        }

        // 调色板已迁移至配置页（drawConfigPage 内绘制），卡片网格不再绘制取色器
    }
    private void drawExpandedCardContent(float x, float y, float w, float h, float alpha, ModuleItem m, float panelHeight) {
        // 南方客户端卡片不内联展开，点击卡片打开独立配置页
    }
    private void drawColorPicker(float contentX, float cy, float contentW, float alpha, float h) {
        float innerX = contentX + 16f * uiScale;
        float innerW = contentW - 32f * uiScale;

        // HSB 选色框（正方形，横向=饱和度，纵向=亮度）
        float svSize = 120f * uiScale;
        float svX = innerX;
        float svY = cy + 8f * uiScale;
        svBoxX = svX; svBoxY = svY; svBoxW = svSize; svBoxH = svSize;
        // 底色：左上白、右下当前色相纯色（横向饱和度），再叠加纵向黑色（上亮下暗=亮度）
        float[] hsv = hsv2rgb(customHue, 1f, 1f);
        float[][] svGrad = diagGradient(new float[]{1f,1f,1f,1f}, hsv, alpha);
        quad.addRectGradient(svX, svY, svSize, svSize, 6f * uiScale,
                svGrad[0], svGrad[1], svGrad[2], svGrad[3]);
        quad.addRectGradient(svX, svY, svSize, svSize, 0f,
                withAlpha(new float[]{0f,0f,0f,1f}, 0f * alpha),
                withAlpha(new float[]{0f,0f,0f,1f}, 0f * alpha),
                withAlpha(new float[]{0f,0f,0f,1f}, 1f * alpha),
                withAlpha(new float[]{0f,0f,0f,1f}, 1f * alpha));
        // 选色圆点
        float dotX = svX + customSat * svSize;
        float dotY = svY + (1f - customVal) * svSize;
        quad.addRect(dotX - 4f, dotY - 4f, 8f, 8f, 4f, withAlpha(KNOB_COLOR, alpha));

        // 色相滑块（垂直，在 HSB 框右侧，约 14px 宽，与选色框等高）
        float hueGap = 8f * uiScale;
        float hueW = 14f * uiScale;
        float hueX = svX + svSize + hueGap;
        float hueY = svY;
        float hueH = svSize;
        hueBarX = hueX; hueBarW = hueW; hueBarY = hueY; hueBarH = hueH;
        for (int hi = 0; hi < 6; hi++) {
            float t0 = hi / 6f, t1 = (hi + 1) / 6f;
            float segY = hueY + hi * hueH / 6f;
            float segH = hueH / 6f;
            float[] colTop = hsv2rgb(t0, 1f, 1f);
            float[] colBot = hsv2rgb(t1, 1f, 1f);
            quad.addRectGradient(hueX, segY, hueW, segH, 3f * uiScale,
                    withAlpha(colTop, alpha), withAlpha(colTop, alpha),
                    withAlpha(colBot, alpha), withAlpha(colBot, alpha));
        }
        float hueKnobH = 8f;
        float hueKnobY = hueY + customHue * hueH - hueKnobH / 2f;
        quad.addRect(hueX - 2f, hueKnobY, hueW + 4f, hueKnobH, 3f, withAlpha(KNOB_COLOR, alpha));

        // 当前色预览（小方块，显示当前选中色）
        float previewGap = 8f * uiScale;
        float previewSize = 28f * uiScale;
        float previewX = hueX + hueW + previewGap;
        float previewY = svY;
        float[] curColor = hsv2rgb(customHue, customSat, customVal);
        quad.addRect(previewX, previewY, previewSize, previewSize, 4f * uiScale, withAlpha(curColor, alpha));
        quad.addRect(previewX - 1f, previewY - 1f, previewSize + 2f, previewSize + 2f, 5f * uiScale, withAlpha(SOUTH_CARD_BORDER, alpha * 0.5f));

        // 预设色块（2行 x 6列 = 12个，点击直接应用）
        float swatchSize = 24f * uiScale;
        float swatchGap = 6f * uiScale;
        int swatchCols = 6;
        float swatchY = svY + svSize + 12f * uiScale;
        paletteHitRects.clear();
        for (int pi = 0; pi < presetSwatches.length; pi++) {
            int col = pi % swatchCols;
            int row = pi / swatchCols;
            float sx = innerX + col * (swatchSize + swatchGap);
            float sy = swatchY + row * (swatchSize + swatchGap);
            boolean sel = pi == selectedSwatchIndex;
            quad.addRect(sx, sy, swatchSize, swatchSize, 4f * uiScale, withAlpha(presetSwatches[pi], alpha));
            if (sel) {
                quad.addRect(sx - 2f, sy - 2f, swatchSize + 4f, swatchSize + 4f, 6f * uiScale, withAlpha(KNOB_COLOR, alpha * 0.9f));
                quad.addRect(sx, sy, swatchSize, swatchSize, 4f * uiScale, withAlpha(presetSwatches[pi], alpha));
            }
            paletteHitRects.add(new float[]{sx, sy, swatchSize, swatchSize});
        }
    }
    private void drawTextLayer(float x, float y, float w, float h, float alpha) {
        float pad = 16f * uiScale * (h / panelH);
        float sbW = sidebarW * (w / panelW);
        float hdrH = 72f * uiScale * (h / panelH);

        // ---- 标题栏文字 ----
        Category curCat = categories.get(currentCategoryIndex);
        if (activeConfigModule != null) {
            // 配置页：显示面包屑导航 "← 分类名 > 模块名"，左侧返回箭头对应 configBackRect 热区
            String breadcrumb = "\u2190 " + Category.localizedCategory(curCat.name, langZh) + " > " + Category.localizedName(activeConfigModule.name, langZh);
            text.draw(breadcrumb, x + sbW + 14f * uiScale, y + hdrH / 2f - 13f * uiScale * 0.5f,
                    13f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
        } else {
            text.draw(t("Rlin", "Rlin") + " b13", x + sbW + 14f * uiScale, y + hdrH / 2f - 13f * uiScale * 0.5f,
                    13f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
            text.draw("\u2190 \u2192 " + Category.localizedCategory(curCat.name, langZh), x + sbW + 80f * uiScale, y + hdrH / 2f - 11f * uiScale * 0.5f,
                    11f * uiScale, withAlpha(SOUTH_TEXT_DIM2, alpha), screenW, screenH);
        }
        if (searchBoxRect != null) {
            String searchText = searchActive ? searchQuery : t("\u2315 搜索...", "\u2315 search...");
            text.draw(searchText, searchBoxRect[0] + 12f * uiScale,
                    searchBoxRect[1] + searchBoxRect[3] / 2f - 10f * uiScale * 0.5f,
                    10f * uiScale, withAlpha(SOUTH_TEXT_DIM2, alpha), screenW, screenH);
        }
        if (closeBtnRect != null) {
            text.draw("\u00D7", closeBtnRect[0] + closeBtnRect[2] / 2f - 5f * uiScale,
                    closeBtnRect[1] + closeBtnRect[3] / 2f - 18f * uiScale * 0.5f,
                    18f * uiScale, withAlpha(SOUTH_TEXT_DIM2, alpha), screenW, screenH);
        }

        // 语言切换按钮文字
        if (langBtnRect != null) {
            String langText = langZh ? "中/EN" : "EN/中";
            float langTextSize = 11f * uiScale;
            float langTextW = text.measure(langText, langTextSize);
            text.draw(langText, langBtnRect[0] + langBtnRect[2] / 2f - langTextW / 2f,
                    langBtnRect[1] + langBtnRect[3] / 2f - langTextSize * 0.5f,
                    langTextSize, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
        }

        // ---- 分组侧栏文字 ----
        float rowH = 36f * uiScale * (h / panelH);
        float sectionLblSize = 11f * uiScale;
        float sectionLblH = 24f * uiScale * (h / panelH);
        float sectionGap = 12f * uiScale * (h / panelH);
        float btnPadX = 8f * uiScale * (w / panelW);
        float curY = y + pad + 8f * uiScale * (h / panelH);
        String prevSection = null;
        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            if (prevSection == null || !c.section.equals(prevSection)) {
                curY += (prevSection != null ? sectionGap : 0f) + sectionLblH;
                String sectionLabel = c.section.equals("MOD CONFIG")
                        ? t("模组配置", "MOD CONFIG")
                        : t("个性化", "PERSONALIZATION");
                text.draw(sectionLabel, x + btnPadX + 8f * uiScale,
                        curY - sectionLblH + 8f * uiScale * (h / panelH),
                        sectionLblSize, withAlpha(SOUTH_SECTION_LBL, alpha), screenW, screenH);
                prevSection = c.section;
            }
            float ry = curY;
            boolean sel = i == currentCategoryIndex;
            float[] col = withAlpha(sel ? SOUTH_TEXT_WHITE : SOUTH_TEXT_DIM2, alpha);
            text.draw(c.icon, x + btnPadX + 8f * uiScale, ry + rowH / 2f - 13f * uiScale * 0.5f, 13f * uiScale, col, screenW, screenH);
            text.draw(Category.localizedCategory(c.name, langZh), x + btnPadX + 30f * uiScale, ry + rowH / 2f - 13f * uiScale * 0.5f, 13f * uiScale, col, screenW, screenH);
            curY += rowH + 4f * uiScale * (h / panelH);
        }

        // 侧栏底部按钮文字
        if (editHudRect != null) {
            text.draw(t("编辑HUD", "Edit HUD"), editHudRect[0] + 12f * uiScale,
                    editHudRect[1] + editHudRect[3] / 2f - 12f * uiScale * 0.5f,
                    12f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
        }
        if (closeSideRect != null) {
            text.draw(t("关闭", "Close"), closeSideRect[0] + 12f * uiScale,
                    closeSideRect[1] + closeSideRect[3] / 2f - 12f * uiScale * 0.5f,
                    12f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
        }

        // 内容区标题
        Category cat = categories.get(currentCategoryIndex);
        float contentX = x + sbW + pad;
        float titleH = 30f * uiScale * (h / panelH);
        text.draw(cat.name + " Modules", contentX, y + hdrH + 10f * uiScale * (h / panelH),
                14f * uiScale, withAlpha(SOUTH_TEXT_WHITE, alpha), screenW, screenH);
        int enabledCount = 0;
        for (ModuleItem mod : cat.modules) if (mod.enabled) enabledCount++;
        text.draw(cat.modules.size() + " modules \u00B7 " + enabledCount + " enabled",
                contentX, y + hdrH + 28f * uiScale * (h / panelH),
                11f * uiScale, withAlpha(SOUTH_TEXT_DIM2, alpha), screenW, screenH);
    }

    /** 卡片名称/描述文字单独拆出来，方便配合 scissor 裁剪，滑动溢出时不会画到面板外 */
    private void drawModuleCardText(float x, float y, float w, float h, float alpha) {
        float sbW = sidebarW * (w / panelW);
        float pad = 16f * uiScale * (h / panelH);
        float contentX = x + sbW + pad;
        float contentW = w - sbW - pad * 2;
        float hdrH = 72f * uiScale * (h / panelH);
        float titleH = 30f * uiScale * (h / panelH);
        float startY = y + hdrH + titleH - contentScroll.get();

        Category cat = categories.get(currentCategoryIndex);
        float cardGap = 16f * uiScale * (h / panelH);
        int cols = 3;
        float cardW = (contentW - cardGap * (cols - 1)) / cols;
        float cardH = 110f * uiScale * (h / panelH);
        float toggleBarH = 32f * uiScale * (h / panelH);
        float iconAreaH = cardH - toggleBarH;

        java.util.List<ModuleItem> visibleModules = new java.util.ArrayList<>();
        for (ModuleItem m : cat.modules) {
            if (searchQuery.isEmpty() || m.name.toLowerCase().contains(searchQuery.toLowerCase())
                    || m.description.toLowerCase().contains(searchQuery.toLowerCase())) {
                visibleModules.add(m);
            }
        }

        float curY = startY;
        for (int i = 0; i < visibleModules.size(); i++) {
            ModuleItem m = visibleModules.get(i);
            int col = i % cols;
            float cardX = contentX + col * (cardW + cardGap);

            if (curY + cardH < clipY - 20f || curY > clipY + clipH + 20f) {
                if ((i + 1) % cols == 0) curY += cardH + cardGap;
                continue;
            }
            float reveal = rowRevealEase(alpha, i);
            if (reveal <= 0.003f) {
                if ((i + 1) % cols == 0) curY += cardH + cardGap;
                continue;
            }
            float rowAlpha = alpha * reveal;
            float drawY = curY + (1f - reveal) * 14f * uiScale;

            // 上方开关区: 模块名居中
            float nameSize = 16f * uiScale;
            String dispName = Category.localizedName(m.name, langZh);
            float nameW = text.measure(dispName, nameSize);
            text.draw(dispName, cardX + cardW / 2f - nameW / 2f, drawY + iconAreaH / 2f - nameSize * 0.5f,
                    nameSize, withAlpha(SOUTH_TEXT_WHITE, rowAlpha), screenW, screenH);

            // 下方配置栏: "Configure" 文字
            float cfgSize = 11f * uiScale;
            String cfgText = "\u2699 " + t("配置", "Configure");
            float cfgW = text.measure(cfgText, cfgSize);
            text.draw(cfgText, cardX + cardW / 2f - cfgW / 2f, drawY + iconAreaH + toggleBarH / 2f - cfgSize * 0.5f,
                    cfgSize, withAlpha(SOUTH_TEXT_DIM2, rowAlpha), screenW, screenH);

            if ((i + 1) % cols == 0) curY += cardH + cardGap;
        }

        // 调色板文字已迁移至配置页（drawConfigPageText 内绘制），卡片网格不再绘制"主题色"标签
    }

    // ---------------- 触摸交互 ----------------

    public void onTouch(MotionEvent event) {
        float ex = event.getX(), ey = event.getY();
        int action = event.getActionMasked();

        // 登录界面期间，触摸事件全部交给 LoginRenderer
        if (loginActive) {
            loginRenderer.onTouch(event, screenW, screenH);
            return;
        }

        // 悬浮球：拖动改变位置；如果没怎么移动就当成一次点击，用来开关整个面板
        if (action == MotionEvent.ACTION_DOWN && ballX >= 0
                && Math.hypot(ex - ballX, ey - ballY) <= ballRadius * 1.3f) {
            draggingBall = true;
            ballMoved = false;
            ballDownX = ex; ballDownY = ey;
            ballOffsetX = ex - ballX; ballOffsetY = ey - ballY;
            return;
        }
        if (draggingBall) {
            if (action == MotionEvent.ACTION_MOVE) {
                ballX = ex - ballOffsetX;
                ballY = ey - ballOffsetY;
                if (Math.hypot(ex - ballDownX, ey - ballDownY) > 14f) ballMoved = true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (!ballMoved) {
                    guiOpen = !guiOpen;
                    if (!guiOpen) {
                        overlayOpenness.setTarget(0f); // 关闭整个面板时同时关闭弹窗
                    } else {
                        openElapsedSec = 0f; // 重新打开：错落进场动画从头开始播
                    }
                    openness.setTarget(guiOpen ? 1f : 0f);
                }
                draggingBall = false;
            }
            return;
        }

        // 面板一旦标记为关闭，无论淡出动画播完没有，其余区域立刻停止响应触摸——
        // 之前是等透明度淡到接近0才屏蔽，淡出尾巴那段时间面板显得"卡住没关掉"。
        if (!guiOpen) return;

        // 配置页视图下的触摸处理（页面切换架构：activeConfigModule != null 时独占触摸）
        if (activeConfigModule != null) {
            if (action == MotionEvent.ACTION_DOWN) {
                // 检查返回按钮
                if (configBackRect != null && inside(ex, ey, configBackRect)) {
                    backToCardGrid();
                    return;
                }
                // 检查关闭按钮（标题栏右上角）
                if (closeBtnRect != null && inside(ex, ey, closeBtnRect)) {
                    guiOpen = false;
                    overlayOpenness.setTarget(0f);
                    openness.setTarget(0f);
                    return;
                }
                // 检查语言切换按钮
                if (langBtnRect != null && inside(ex, ey, langBtnRect)) {
                    toggleLanguage();
                    return;
                }
                if (searchBoxRect != null && inside(ex, ey, searchBoxRect)) {
                    backToCardGrid();
                    return;
                }
                // 检查 Enabled 开关
                if (configToggleRect != null && inside(ex, ey, configToggleRect)) {
                    activeConfigModule.enabled = !activeConfigModule.enabled;
                    spawnModuleToast(activeConfigModule.name, activeConfigModule.enabled);
                    return;
                }
                // 检查滑块拖拽
                if (configSliderW > 0 && ex >= configSliderX && ex <= configSliderX + configSliderW + 20f &&
                    ey >= configSliderY - 10f && ey <= configSliderY + configSliderH + 10f) {
                    draggingConfigSlider = true;
                    float t = (ex - configSliderX) / configSliderW;
                    activeConfigModule.sliderValue = Math.max(0f, Math.min(1f, t));
                    return;
                }
                // 调色板交互（仅 Theme Color 模块配置页内）
                if (hueBarW > 0 && ex >= hueBarX - 6 && ex <= hueBarX + hueBarW + 6
                        && ey >= hueBarY && ey <= hueBarY + hueBarH) {
                    draggingHue = true;
                    updateHueFromY(ey);
                    return;
                }
                if (svBoxW > 0 && ex >= svBoxX && ex <= svBoxX + svBoxW
                        && ey >= svBoxY && ey <= svBoxY + svBoxH) {
                    draggingSV = true;
                    updateSVFromXY(ex, ey);
                    return;
                }
                for (int pi = 0; pi < paletteHitRects.size(); pi++) {
                    if (inside(ex, ey, paletteHitRects.get(pi))) {
                        selectedSwatchIndex = pi;
                        applyCurrentColor();
                        return;
                    }
                }
                // 检查配置项列表（下拉框 / 额外开关 / 额外滑块）
                for (int oi = 0; oi < configOptionRects.size(); oi++) {
                    Object[] entry = configOptionRects.get(oi);
                    float[] rect = (float[]) entry[0];
                    if (inside(ex, ey, rect)) {
                        int optIdx = (int) entry[1];
                        int optType = (int) entry[2];
                        ModuleItem.ConfigOption opt = activeConfigModule.options.get(optIdx);
                        if (optType == ModuleItem.ConfigOption.TYPE_DROPDOWN) {
                            int len = opt.dropdownChoices != null ? opt.dropdownChoices.length : 0;
                            if (len > 0) opt.dropdownIndex = (opt.dropdownIndex + 1) % len;
                        } else if (optType == ModuleItem.ConfigOption.TYPE_SWITCH) {
                            opt.boolValue = !opt.boolValue;
                        } else if (optType == ModuleItem.ConfigOption.TYPE_SLIDER) {
                            draggingOptionIndex = optIdx;
                            draggingOptionSliderX = rect[0];
                            draggingOptionSliderW = rect[2];
                            float t = (ex - rect[0]) / rect[2];
                            opt.sliderValue = Math.max(0f, Math.min(1f, t));
                        }
                        return;
                    }
                }
            }
            if (action == MotionEvent.ACTION_MOVE && draggingConfigSlider) {
                float t = (ex - configSliderX) / configSliderW;
                activeConfigModule.sliderValue = Math.max(0f, Math.min(1f, t));
                return;
            }
            if (action == MotionEvent.ACTION_MOVE && draggingOptionIndex >= 0) {
                ModuleItem.ConfigOption opt = activeConfigModule.options.get(draggingOptionIndex);
                float t = (ex - draggingOptionSliderX) / draggingOptionSliderW;
                opt.sliderValue = Math.max(0f, Math.min(1f, t));
                return;
            }
            if (action == MotionEvent.ACTION_MOVE && draggingHue) {
                updateHueFromY(ey);
                return;
            }
            if (action == MotionEvent.ACTION_MOVE && draggingSV) {
                updateSVFromXY(ex, ey);
                return;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                draggingConfigSlider = false;
                draggingHue = false;
                draggingSV = false;
                draggingOptionIndex = -1;
            }

            // 在配置页模式下，侧栏分类点击仍然生效（切换分类后返回卡片视图）
            for (int i = 0; i < categoryHitRects.size(); i++) {
                if (inside(ex, ey, categoryHitRects.get(i))) {
                    if (currentCategoryIndex != i) {
                        currentCategoryIndex = i;
                        activeConfigModule = null; // 切换分类时返回卡片视图
                        contentScroll.snapTo(0);
                        configScrollOffset = 0f;
                    }
                    return;
                }
            }

            // 配置页滚动（垂直滑动）
            if (action == MotionEvent.ACTION_DOWN) {
                dragStartY = ey;
                scrollStartOffset = configScrollOffset;
            } else if (action == MotionEvent.ACTION_MOVE) {
                float dy = ey - dragStartY;
                configScrollOffset = Math.max(0f, Math.min(configMaxScroll, scrollStartOffset - dy));
                return;
            }
            return; // 配置页模式下不继续处理卡片热区
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // 记录按下坐标用于UP时判断点击vs滑动
                touchDownX = ex; touchDownY = ey; touchMoved = false;
                // 0) 标题栏：搜索框 + 关闭按钮
                if (searchBoxRect != null && inside(ex, ey, searchBoxRect)) {
                    searchActive = true;
                    return;
                }
                if (closeBtnRect != null && inside(ex, ey, closeBtnRect)) {
                    guiOpen = false;
                    overlayOpenness.setTarget(0f);
                    openness.setTarget(0f);
                    return;
                }
                // 语言切换按钮
                if (langBtnRect != null && inside(ex, ey, langBtnRect)) {
                    toggleLanguage();
                    return;
                }
                if (searchActive && (searchBoxRect == null || !inside(ex, ey, searchBoxRect))) {
                    searchActive = false;
                }
                // 底栏 Close 按钮
                if (footerCloseRect != null && inside(ex, ey, footerCloseRect)) {
                    guiOpen = false;
                    overlayOpenness.setTarget(0f);
                    openness.setTarget(0f);
                    return;
                }
                // 侧栏底部按钮
                if (closeSideRect != null && inside(ex, ey, closeSideRect)) {
                    guiOpen = false;
                    overlayOpenness.setTarget(0f);
                    openness.setTarget(0f);
                    return;
                }
                if (editHudRect != null && inside(ex, ey, editHudRect)) {
                    // Edit HUD: TODO — 暂时只切换某个状态或忽略
                    spawnToast("HUD Editor coming soon");
                    return;
                }
                // 1) 取色器交互：色相滑块拖拽（垂直）
                if (hueBarW > 0 && ex >= hueBarX - 6 && ex <= hueBarX + hueBarW + 6
                        && ey >= hueBarY && ey <= hueBarY + hueBarH) {
                    draggingHue = true;
                    updateHueFromY(ey);
                    return;
                }
                // HSB 选色框拖拽
                if (svBoxW > 0 && ex >= svBoxX && ex <= svBoxX + svBoxW
                        && ey >= svBoxY && ey <= svBoxY + svBoxH) {
                    draggingSV = true;
                    updateSVFromXY(ex, ey);
                    return;
                }
                // 预设色块点击
                for (int pi = 0; pi < paletteHitRects.size(); pi++) {
                    if (inside(ex, ey, paletteHitRects.get(pi))) {
                        selectedSwatchIndex = pi;
                        applyCurrentColor();
                        return;
                    }
                }
                // 搜索文本输入：GLSurfaceView 不便接入 InputMethodManager，
                // 暂不处理键盘输入，搜索框仅作视觉元素；searchQuery 可通过代码设置。
                // 2) 卡片交互：点击开关栏切换模块，点击卡片其他区域暂不处理
                for (int idx = 0; idx < moduleHitRects.size(); idx++) {
                    Object[] hit = moduleHitRects.get(idx);
                    float[] r = (float[]) hit[0];
                    String type = (String) hit[1];
                    ModuleItem m = (ModuleItem) hit[2];

                    if (!inside(ex, ey, r)) continue;

                    if ("TOGGLE".equals(type)) {
                        // 点击开关栏：切换模块开关
                        m.enabled = !m.enabled;
                        spawnModuleToast(m.name, m.enabled);
                        return;
                    }
                    if ("CARD".equals(type)) {
                        // 点击图标区：打开模块配置面板（与真实南方一致）
                        toggleConfigPanelFor(m);
                        return;
                    }
                }
                // 4) 分类点击
                for (int i = 0; i < categoryHitRects.size(); i++) {
                    if (inside(ex, ey, categoryHitRects.get(i))) {
                        currentCategoryIndex = i;
                        contentScroll.snapTo(0);
                        return;
                    }
                }
                // 4) 否则准备滚动内容区
                dragStartY = ey;
                scrollStartOffset = contentScroll.get();
                break;

            case MotionEvent.ACTION_MOVE:
                // 卡片内联滑块已移除（双区卡片设计）
                // if (draggingCardSlider && sliderDragModule != null && cardSliderRect != null) {
                //     float v = (ex - cardSliderRect[0]) / cardSliderRect[2];
                //     sliderDragModule.sliderValue = Math.max(0f, Math.min(1f, v));
                //     return;
                // }
                if (draggingHue) {
                    updateHueFromY(ey);
                    return;
                }
                if (draggingSV) {
                    updateSVFromXY(ex, ey);
                    return;
                }
                if (dragStartY >= 0) {
                    float dy = dragStartY - ey;
                    // 垂直移动超过12px标记为滑动，同时取消长按候选（正在滑动就不该触发长按）
                    // 之前阈值只有12px，现代手机分辨率下这点距离基本等于"手指刚碰到屏幕就算滑动"，
                    // 导致长按几乎不可能触发（自然按住不动都会有零点几毫米的抖动）。放宽到有效距离。
                    if (Math.abs(dy) > 26f * uiScale) {
                        touchMoved = true;
                        longPressCandidate = null;
                    }
                    float target = Math.max(0, scrollStartOffset + dy);
                    contentScroll.snapTo(Math.min(target, maxScroll));
                }
                break;

            case MotionEvent.ACTION_UP:
                // 如果本次DOWN刚刚关闭了弹窗，UP时不再触发下方动作
                if (overlayJustClosed) {
                    overlayJustClosed = false;
                    longPressCandidate = null;
                    longPressFired = false;
                    break;
                }
                // 防止开关点击穿透到EXPAND之后的ACTION_UP残留
                draggingHue = false;
                draggingSV = false;
                dragStartY = -1;
                touchMoved = false;
                longPressCandidate = null;
                longPressFired = false;
                draggingCardSlider = false;
                sliderDragModule = null;
                cardSliderRect = null;
                break;
            case MotionEvent.ACTION_CANCEL:
                draggingHue = false;
                draggingSV = false;
                dragStartY = -1;
                touchMoved = false;
                overlayJustClosed = false;
                longPressCandidate = null;
                longPressFired = false;
                draggingCardSlider = false;
                sliderDragModule = null;
                cardSliderRect = null;
                break;
        }
    }



    private void updateHueFromY(float ey) {
        if (hueBarH <= 0) return;
        customHue = Math.max(0f, Math.min(1f, (ey - hueBarY) / hueBarH));
        selectedSwatchIndex = -1;
        applyCurrentColor();
    }

    private void updateSVFromXY(float ex, float ey) {
        if (svBoxW <= 0 || svBoxH <= 0) return;
        customSat = Math.max(0f, Math.min(1f, (ex - svBoxX) / svBoxW));
        customVal = Math.max(0f, Math.min(1f, 1f - (ey - svBoxY) / svBoxH));
        selectedSwatchIndex = -1;
        applyCurrentColor();
    }

    /** 根据当前选中的色块/SV值更新 ACCENT_1/ACCENT_2 */
    private void applyCurrentColor() {
        if (selectedSwatchIndex >= 0 && selectedSwatchIndex < presetSwatches.length) {
            ACCENT_1 = presetSwatches[selectedSwatchIndex];
            ACCENT_2 = ACCENT_1;
        } else {
            float[] rgb = hsv2rgb(customHue, customSat, customVal);
            ACCENT_1 = new float[]{rgb[0], rgb[1], rgb[2], 1f};
            // 副色取色相偏移0.05后的颜色
            float h2 = (customHue + 0.05f) % 1f;
            float[] rgb2 = hsv2rgb(h2, customSat * 0.8f, customVal * 0.9f);
            ACCENT_2 = new float[]{rgb2[0], rgb2[1], rgb2[2], 1f};
        }
    }

    /** 切换主题时调用：更新 ACCENT_1/ACCENT_2 */
    private void applyThemeColors() {
        ACCENT_1 = themeAccent1();
        ACCENT_2 = themeAccent2();
    }

    private float[] rgb2hsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h;
        if (delta < 1e-6f) h = 0f;
        else if (max == r) h = (((g - b) / delta) % 6f) / 6f;
        else if (max == g) h = (((b - r) / delta) + 2f) / 6f;
        else h = (((r - g) / delta) + 4f) / 6f;
        if (h < 0) h += 1f;
        float s = max <= 1e-6f ? 0f : delta / max;
        float v = max;
        return new float[]{h, s, v};
    }

    private boolean inside(float px, float py, float[] r) {
        return px >= r[0] && px <= r[0] + r[2] && py >= r[1] && py <= r[1] + r[3];
    }

    // ---------------- 颜色工具 ----------------

    private float[] withAlpha(float[] c, float mulAlpha) {
        return new float[]{c[0], c[1], c[2], c[3] * mulAlpha};
    }

    /** 强调色两色渐变从左上到右下（对角线方向），而不是之前纯水平的左右方向。
     *  返回 {TL, TR, BL, BR} 四个角的颜色：左上角是c1，右下角是c2，另外两个角用中间混合色过渡。 */
    private float[][] diagGradient(float[] c1, float[] c2, float a) {
        float[] mid = mixColor(c1, c2, 0.5f);
        return new float[][]{withAlpha(c1, a), withAlpha(mid, a), withAlpha(mid, a), withAlpha(c2, a)};
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
                Integer.parseInt(h.substring(0,2), 16) / 255f,
                Integer.parseInt(h.substring(2,4), 16) / 255f,
                Integer.parseInt(h.substring(4,6), 16) / 255f,
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

