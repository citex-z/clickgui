package com.visiolab.clickgui.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.visiolab.clickgui.model.Category;
import com.visiolab.clickgui.model.ModuleItem;
import com.visiolab.clickgui.ui.widget.SliderView;
import com.visiolab.clickgui.ui.widget.SwitchView;
import com.visiolab.clickgui.xposed.ModuleResourceLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI 面板 — 原生 View 实现，1:1 还原原始 GL 面板的视觉规范。
 *
 * 浅色主题（白底黑字），参考南方客户端的 GL UIRenderer 配色：
 * - 面板背景: #F5F5F5
 * - 侧栏背景: #EBEBEB
 * - 卡片/模块行背景: #EBEBEB / #E6E6E6
 * - 主文字: #141414 (87% alpha)
 *
 * 布局结构：
 * ┌──────────┬──────────────────────────────────────┐
 * │          │  Header 栏 (72dp)                     │
 * │          │  [品牌名]  [语言][×][搜索框]            │
 * │  侧栏    ├──────────────────────────────────────┤
 * │ (20.83%) │  内容标题 (30dp) "Xxx Modules"        │
 * │          ├──────────────────────────────────────┤
 * │  分类    │  内容区 (3列卡片网格 / 配置页)          │
 * │  按钮    │  (ScrollView滚动)                     │
 * │          │                                        │
 * │ [EditHUD]│                                        │
 * │ [Close]  │                                        │
 * └──────────┴──────────────────────────────────────┘
 */
public class ClickGuiPanel {

    private static final String TAG = "ClickGuiPanel";

    // ==================== 颜色常量（浅色主题，来自 GL UIRenderer）====================

    private static final int SOUTH_PANEL_BG     = Color.parseColor("#F5F5F5"); // 面板背景
    private static final int SOUTH_SIDEBAR_BG   = Color.parseColor("#EBEBEB"); // 侧栏背景
    private static final int SOUTH_CARD_BG      = Color.parseColor("#EBEBEB"); // 卡片背景
    private static final int SOUTH_CARD_BORDER  = Color.parseColor("#CCCCCC"); // 卡片边框
    private static final int SOUTH_CARD_BORDER_SEL = Color.parseColor("#808080"); // 选中边框
    private static final int SOUTH_SEARCH_BG    = Color.parseColor("#E0E0E0"); // 搜索框背景
    private static final int SOUTH_MODULE_BG    = Color.parseColor("#E6E6E6"); // 配置行背景
    private static final int SOUTH_TOGGLE_ON    = Color.parseColor("#4A90D9"); // 开关开启：柔和蓝色，搭配浅色面板
    private static final int SOUTH_TOGGLE_OFF   = Color.parseColor("#D5D5D5"); // 开关关闭：浅灰
    private static final int SOUTH_SLIDER_TRACK = Color.parseColor("#D9D9D9"); // 滑块轨道
    private static final int KNOB_COLOR         = Color.parseColor("#FFFFFF"); // 旋钮/滑块圆点
    private static final int SOUTH_TEXT_WHITE   = Color.argb(222, 20, 20, 20); // #141414 87%
    private static final int SOUTH_TEXT_DIM2    = Color.argb(153, 20, 20, 20); // #141414 60%
    private static final int SOUTH_TEXT_DIM3    = Color.argb(97, 20, 20, 20);  // #141414 38%
    private static final int SOUTH_SECTION_LBL  = Color.parseColor("#737373"); // 分组标签
    private static final int SOUTH_DIVIDER      = Color.argb(128, 153, 153, 153); // #999999 50%
    private static final int ACCENT_1       = Color.parseColor("#4A90D9"); // 强调色1：蓝色
    private static final int ACCENT_2       = Color.parseColor("#357ABD"); // 强调色2：深蓝

    // ==================== 尺寸常量（dp 值，运行时转换）====================
    // 缩放系数 0.7：进一步减轻面板拥挤感

    private static final float PANEL_RATIO     = 600f / 960f;   // 面板高宽比 0.625
    private static final float SIDEBAR_RATIO   = 200f / 960f;   // 侧栏宽度占比 20.83%
    private static final int PANEL_CORNER      = 20;             // 面板圆角
    private static final int HEADER_H          = (int)(72 * 0.7);  // Header 栏高度
    private static final int TITLE_H           = (int)(30 * 0.7);  // 内容标题高度
    private static final int CARD_H            = (int)(110 * 0.7); // 卡片高度
    private static final int CARD_GAP          = (int)(16 * 0.7);  // 卡片间距
    private static final int CARD_CORNER       = 12;             // 卡片圆角
    private static final int CONFIG_BAR_H      = (int)(32 * 0.7);  // 配置栏高度（卡片下半）
    private static final int CONFIG_ROW_H      = (int)(48 * 0.7);  // 配置行高度
    private static final int CONFIG_ROW_GAP    = (int)(8 * 0.7);   // 配置行间距
    private static final int GROUP_CORNER      = 10;             // 分组容器圆角
    private static final int SWITCH_W          = (int)(44 * 0.7);  // 开关宽度
    private static final int SWITCH_H          = (int)(22 * 0.7);  // 开关高度
    private static final int SIDEBAR_BTN_H     = (int)(36 * 0.7);  // 侧栏按钮高度
    private static final int SIDEBAR_BTN_GAP   = (int)(4 * 0.7);   // 侧栏按钮间距
    private static final int SEARCH_W          = (int)(160 * 0.7); // 搜索框宽度
    private static final int SEARCH_H          = (int)(36 * 0.7);  // 搜索框高度
    private static final int CLOSE_SIZE        = (int)(36 * 0.7);  // 关闭按钮尺寸
    private static final int LANG_W            = (int)(52 * 0.7);  // 语言按钮宽度
    private static final int LANG_H            = (int)(36 * 0.7);  // 语言按钮高度
    private static final int GRID_COLS         = 3;              // 卡片网格列数
    private static final int BOTTOM_BTN_H      = (int)(36 * 0.7);  // 底部按钮高度
    private static final int BOTTOM_BTN_CORNER = 8;              // 底部按钮圆角

    // ==================== 字段 ====================

    private final Activity activity;
    private View rootView;
    private WindowManager wm;
    private WindowManager.LayoutParams wmParams;
    private boolean shown = false;
    private List<Category> categories;
    private int currentCategoryIndex = 0;
    private View panelView;
    private ModuleItem activeConfigModule = null;  // 当前打开配置页的模块（null=卡片网格模式）
    private String searchQuery = "";
    private boolean langZh = false;

    // 动态更新的视图引用
    private FrameLayout contentBody;          // 内容主体（卡片网格 / 配置页切换）
    private TextView contentTitleText;        // 内容区标题 "Xxx Modules"
    private TextView contentSubtitleText;     // 内容区副标题 "N modules · M enabled"
    private TextView headerTitleText;         // Header 标题（品牌名 / 面包屑）
    private TextView headerSubtitleText;      // Header 副标题（分类指示）
    private EditText searchEditText;          // 搜索框
    private LinearLayout sidebarBtnsContainer;   // 侧栏按钮容器
    private ScrollView moduleScrollView;      // 模块卡片 ScrollView
    private ScrollView configScrollView;      // 配置页 ScrollView
    private TextView editHudBtn;              // 侧栏底部 EditHUD 按钮
    private TextView closeSideBtn;            // 侧栏底部 Close 按钮

    // 字体
    private Typeface comfortaaRegular;
    private Typeface comfortaaBold;

    // 监听器
    private OnCloseListener closeListener;
    private OnModuleChangedListener moduleChangedListener;
    private OnLangChangedListener langChangedListener;
    private OnAddHotkeyListener addHotkeyListener;

    // ==================== 接口 ====================

    public interface OnCloseListener {
        void onClose();
    }

    public interface OnModuleChangedListener {
        void onModuleChanged(String name, boolean enabled);
    }

    public interface OnLangChangedListener {
        void onLangChanged(boolean zh);
    }

    public interface OnAddHotkeyListener {
        void onAddHotkey(String moduleName);
    }

    // ==================== 构造 ====================

    /**
     * 构造 ClickGUI 面板，使用默认分类数据。
     */
    public ClickGuiPanel(Activity activity) {
        this(activity, null);
    }

    /**
     * 构造 ClickGUI 面板，接收外部传入的分类数据（与 GL 共享）。
     *
     * @param activity   宿主 Activity
     * @param categories 分类数据列表，若为 null 则使用 Category.buildDefault()
     */
    public ClickGuiPanel(Activity activity, List<Category> categories) {
        this.activity = activity;
        this.categories = categories != null ? categories : Category.buildDefault();
        loadFonts();
    }

    public void setOnCloseListener(OnCloseListener listener) {
        this.closeListener = listener;
    }

    public void setOnModuleChangedListener(OnModuleChangedListener listener) {
        this.moduleChangedListener = listener;
    }

    public void setOnLangChangedListener(OnLangChangedListener listener) {
        this.langChangedListener = listener;
    }

    public void setOnAddHotkeyListener(OnAddHotkeyListener listener) {
        this.addHotkeyListener = listener;
    }

    // ==================== 字体加载 ====================

    /** 通过 ModuleResourceLoader 加载 Comfortaa 字体（Xposed 环境下需要独立 AssetManager） */
    private void loadFonts() {
        try {
            if (ModuleResourceLoader.isInitialized()) {
                comfortaaRegular = Typeface.createFromAsset(
                        ModuleResourceLoader.getAssets(), "fonts/Comfortaa-Regular.ttf");
                comfortaaBold = Typeface.createFromAsset(
                        ModuleResourceLoader.getAssets(), "fonts/Comfortaa-Bold.ttf");
            }
        } catch (Throwable e) {
            Log.w(TAG, "字体加载失败，使用默认字体", e);
        }
    }

    // ==================== 显示 / 关闭 ====================

    public void show() {
        activity.runOnUiThread(() -> {
            if (shown) return;
            View content = buildPanelView();
            int screenW = activity.getResources().getDisplayMetrics().widthPixels;
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;

            wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            wmParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            );
            wmParams.gravity = Gravity.CENTER;
            try {
                wmParams.token = activity.getWindow().getAttributes().token;
            } catch (Throwable ignored) {}

            try {
                wm.addView(content, wmParams);
                shown = true;
                rootView = content;
                animateOpen();
            } catch (Throwable e) {
                Log.e(TAG, "WindowManager addView failed (TYPE_APPLICATION)", e);
                try {
                    wmParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                    wmParams.token = activity.getWindow().getDecorView().getWindowToken();
                    wm.addView(content, wmParams);
                    shown = true;
                    rootView = content;
                    animateOpen();
                } catch (Throwable e2) {
                    Log.e(TAG, "All window types failed", e2);
                }
            }
        });
    }

    public void dismiss() {
        if (!shown) return;
        animateClose();
    }

    public boolean isShowing() {
        return shown;
    }

    private void close() {
        dismiss();
    }

    private void removeFromWindow() {
        if (rootView != null && wm != null) {
            try { wm.removeView(rootView); } catch (Throwable ignored) {}
        }
        shown = false;
        rootView = null;
        if (closeListener != null) closeListener.onClose();
    }

    // ==================== 面板构建 ====================

    /**
     * 构建整个面板视图。
     * 根容器全屏 → 半透明遮罩 → 居中面板。
     */
    private View buildPanelView() {
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;

        // 读取 GUI Scale 配置（从 Client 分类的 ClickGUI 模块）
        float guiScaleMul = getGuiScaleMultiplier();

        // 计算面板尺寸：宽 = min(屏宽*0.92, 屏高*1.6)，高 = 宽 * 0.625
        float basePanelW = Math.min(screenW * 0.92f, screenH * 1.6f);
        float basePanelH = basePanelW * PANEL_RATIO;
        if (basePanelH > screenH * 0.9f) {
            basePanelH = screenH * 0.9f;
            basePanelW = basePanelH / PANEL_RATIO;
        }
        // 应用 GUI Scale（0.5x ~ 2.0x）
        float panelW = basePanelW * guiScaleMul;
        float panelH = basePanelH * guiScaleMul;

        // 根容器：全屏（无遮罩，面板背景本身就是不透明的）
        FrameLayout root = new FrameLayout(activity);
        root.setLayoutParams(new ViewGroup.LayoutParams(screenW, screenH));

        // 面板容器：居中，圆角+阴影
        FrameLayout panelContainer = new FrameLayout(activity);
        panelContainer.setTag("panelContainer");
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(SOUTH_PANEL_BG);
        panelBg.setCornerRadius(dp(PANEL_CORNER));
        panelContainer.setBackground(panelBg);
        panelContainer.setElevation(dp(20));
        // 关键：裁剪子 View 到圆角范围内（修复直角问题）
        panelContainer.setClipToOutline(true);
        panelContainer.setClipChildren(true);
        panelContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(PANEL_CORNER));
            }
        });
        // 消费空白区域点击，避免穿透导致误关闭
        panelContainer.setClickable(true);
        panelContainer.setFocusable(true);
        // 点击面板外部空白区域关闭
        root.setOnClickListener(v -> close());
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                (int) panelW, (int) panelH);
        panelLp.gravity = Gravity.CENTER;
        panelContainer.setLayoutParams(panelLp);
        this.panelView = panelContainer;

        // 主布局：左右分栏（侧栏 + 内容区）
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainLayout.setClickable(true); // 同样消费点击，避免穿透

        // ---- 左侧栏 ----
        int sidebarWidth = (int) (panelW * SIDEBAR_RATIO);
        View sidebar = buildSidebar(sidebarWidth, (int) panelH);
        mainLayout.addView(sidebar, new LinearLayout.LayoutParams(
                sidebarWidth, ViewGroup.LayoutParams.MATCH_PARENT));

        // 侧栏与内容区之间的分隔线
        View divider = new View(activity);
        divider.setBackgroundColor(SOUTH_CARD_BORDER);
        mainLayout.addView(divider, new LinearLayout.LayoutParams(
                dp(1), ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 右侧内容区 ----
        View contentArea = buildContentArea((int) (panelW - sidebarWidth - dp(1)), (int) panelH);
        mainLayout.addView(contentArea, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        panelContainer.addView(mainLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(panelContainer);

        // 初始化内容
        refreshSidebar();
        refreshContentTitle();
        showModuleGrid();

        return root;
    }

    // ==================== 侧栏 ====================

    /**
     * 构建左侧栏：分类按钮（按 section 分组）+ 底部 EditHUD/Close 按钮。
     */
    private View buildSidebar(int width, int height) {
        LinearLayout sidebar = new LinearLayout(activity);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(SOUTH_SIDEBAR_BG);
        sidebar.setPadding(dp(8), dp(16), dp(8), dp(16));

        // 侧栏按钮容器（可滚动区域）
        ScrollView sidebarScroll = new ScrollView(activity);
        sidebarScroll.setVerticalScrollBarEnabled(false);
        sidebarScroll.setFillViewport(true);

        sidebarBtnsContainer = new LinearLayout(activity);
        sidebarBtnsContainer.setOrientation(LinearLayout.VERTICAL);

        // 按分组填充分类按钮
        populateSidebarButtons();

        sidebarScroll.addView(sidebarBtnsContainer);
        sidebar.addView(sidebarScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部按钮区域
        LinearLayout bottomBar = new LinearLayout(activity);
        bottomBar.setOrientation(LinearLayout.VERTICAL);

        // EditHUD 按钮
        editHudBtn = createBottomButton(t("编辑HUD", "Edit HUD"));
        editHudBtn.setOnClickListener(v -> {
            // 暂无实现：可通过回调通知宿主
            Log.i(TAG, "EditHUD clicked");
        });
        LinearLayout.LayoutParams ehLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(BOTTOM_BTN_H));
        ehLp.bottomMargin = dp(SIDEBAR_BTN_GAP);
        bottomBar.addView(editHudBtn, ehLp);

        // Close 按钮
        closeSideBtn = createBottomButton(t("关闭", "Close"));
        closeSideBtn.setOnClickListener(v -> close());
        bottomBar.addView(closeSideBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(BOTTOM_BTN_H)));

        sidebar.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return sidebar;
    }

    /** 创建单个分类按钮 */
    private TextView createCategoryButton(Category c, int idx) {
        TextView btn = new TextView(activity);
        btn.setText(Category.localizedCategory(c.name, langZh));
        btn.setTextColor(SOUTH_TEXT_DIM2);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(13*0.7));
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setPadding(dp(12), 0, dp(8), 0);
        btn.setSingleLine(true);                    // 防止文字换行被裁切
        btn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        applyRegularFont(btn);
        btn.setMinimumHeight(dp(SIDEBAR_BTN_H));

        // 选中高亮背景
        updateCategoryButtonBg(btn, idx == currentCategoryIndex);

        btn.setOnClickListener(v -> {
            if (currentCategoryIndex == idx && activeConfigModule == null) return;
            activeConfigModule = null;
            currentCategoryIndex = idx;
            refreshSidebar();
            refreshContentTitle();
            updateHeader();
            // 平滑切换动画：卡片淡出 → 刷新 → 淡入
            if (moduleScrollView != null) {
                moduleScrollView.animate()
                        .alpha(0f)
                        .translationX(-dp(30))
                        .setDuration(150)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(() -> {
                            switchToModuleGridWithAnim();
                            moduleScrollView.setAlpha(0f);
                            moduleScrollView.setTranslationX(dp(30));
                            moduleScrollView.animate()
                                    .alpha(1f)
                                    .translationX(0f)
                                    .setDuration(250)
                                    .setInterpolator(new OvershootInterpolator(1.2f))
                                    .start();
                        })
                        .start();
            } else {
                switchToModuleGridWithAnim();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(SIDEBAR_BTN_H));
        lp.bottomMargin = dp(SIDEBAR_BTN_GAP);
        btn.setLayoutParams(lp);
        btn.setTag(idx);
        return btn;
    }

    /** 更新分类按钮的背景和文字颜色（选中高亮） */
    private void updateCategoryButtonBg(TextView btn, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (selected) {
            // 选中高亮：#808080 15% alpha
            bg.setColor(Color.argb(38, 128, 128, 128));
            btn.setTextColor(SOUTH_TEXT_WHITE);
        } else {
            bg.setColor(Color.TRANSPARENT);
            btn.setTextColor(SOUTH_TEXT_DIM2);
        }
        btn.setBackground(bg);
    }

    /** 刷新所有分类按钮的选中状态 */
    private void refreshSidebar() {
        if (sidebarBtnsContainer == null) return;
        // 重建按钮（同时更新文字语言和选中状态）
        populateSidebarButtons();
    }

    /** 按分组填充/重建侧栏分类按钮 */
    private void populateSidebarButtons() {
        if (sidebarBtnsContainer == null) return;
        sidebarBtnsContainer.removeAllViews();

        String prevSection = null;
        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            // 新分组标签
            if (prevSection == null || !c.section.equals(prevSection)) {
                if (prevSection != null) {
                    // 分组间距
                    View gap = new View(activity);
                    sidebarBtnsContainer.addView(gap, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
                }
                TextView sectionLabel = new TextView(activity);
                String labelText = c.section.equals("MOD CONFIG")
                        ? t("模组配置", "MOD CONFIG")
                        : t("个性化", "PERSONALIZATION");
                sectionLabel.setText(labelText);
                sectionLabel.setTextColor(SOUTH_SECTION_LBL);
                sectionLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(11*0.7));
                sectionLabel.setPadding(dp(8), dp(8), dp(8), dp(8));
                applyBoldFont(sectionLabel);
                LinearLayout.LayoutParams slLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
                slLp.bottomMargin = dp(4);
                sidebarBtnsContainer.addView(sectionLabel, slLp);
                prevSection = c.section;
            }

            // 分类按钮
            final int idx = i;
            TextView btn = createCategoryButton(c, idx);
            sidebarBtnsContainer.addView(btn);
        }
    }

    /** 创建底部按钮（EditHUD / Close） */
    private TextView createBottomButton(String text) {
        TextView btn = new TextView(activity);
        btn.setText(text);
        btn.setTextColor(SOUTH_TEXT_WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(12*0.7));
        btn.setGravity(Gravity.CENTER);
        applyRegularFont(btn);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SOUTH_MODULE_BG);
        bg.setCornerRadius(dp(BOTTOM_BTN_CORNER));
        btn.setBackground(bg);
        return btn;
    }

    // ==================== 内容区（Header + 标题 + 主体）====================

    /**
     * 构建右侧内容区：Header 栏 + 内容标题 + 内容主体。
     */
    private View buildContentArea(int width, int height) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(SOUTH_PANEL_BG);

        // ---- Header 栏 (72dp) ----
        View header = buildHeaderBar();
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(HEADER_H)));

        // Header 下方分隔线
        View hdrDivider = new View(activity);
        hdrDivider.setBackgroundColor(SOUTH_CARD_BORDER);
        content.addView(hdrDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // ---- 内容标题 (30dp) ----
        LinearLayout titleBar = new LinearLayout(activity);
        titleBar.setOrientation(LinearLayout.VERTICAL);
        titleBar.setPadding(dp(16), dp(6), dp(16), dp(4));

        contentTitleText = new TextView(activity);
        contentTitleText.setTextColor(SOUTH_TEXT_WHITE);
        contentTitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(14*0.7));
        contentTitleText.setSingleLine(true);
        applyRegularFont(contentTitleText);
        titleBar.addView(contentTitleText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contentSubtitleText = new TextView(activity);
        contentSubtitleText.setTextColor(SOUTH_TEXT_DIM2);
        contentSubtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(11*0.7));
        contentSubtitleText.setSingleLine(true);
        applyRegularFont(contentSubtitleText);
        titleBar.addView(contentSubtitleText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(titleBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TITLE_H)));

        // ---- 内容主体（卡片网格 / 配置页切换）----
        contentBody = new FrameLayout(activity);
        content.addView(contentBody, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return content;
    }

    // ==================== Header 栏 ====================

    /**
     * 构建 Header 栏：品牌名/面包屑 + 语言按钮 + 关闭按钮 + 搜索框。
     */
    private View buildHeaderBar() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(SOUTH_PANEL_BG);
        header.setPadding(dp(14), 0, dp(16), 0);

        // 左侧：品牌名/面包屑（点击可返回）
        LinearLayout titleBox = new LinearLayout(activity);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        headerTitleText = new TextView(activity);
        headerTitleText.setText("Rlin B1");
        headerTitleText.setTextColor(SOUTH_TEXT_WHITE);
        headerTitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(13*0.7));
        headerTitleText.setSingleLine(true);
        applyBoldFont(headerTitleText);
        titleBox.addView(headerTitleText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        headerSubtitleText = new TextView(activity);
        headerSubtitleText.setTextColor(SOUTH_TEXT_DIM2);
        headerSubtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(11*0.7));
        headerSubtitleText.setSingleLine(true);
        applyRegularFont(headerSubtitleText);
        titleBox.addView(headerSubtitleText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 点击面包屑返回卡片网格
        titleBox.setOnClickListener(v -> {
            if (activeConfigModule != null) {
                switchToModuleGridWithAnim();
            }
        });

        header.addView(titleBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 右侧按钮区：语言 + 关闭 + 搜索
        // 语言切换按钮 (52x36, 胶囊形)
        TextView langBtn = new TextView(activity);
        langBtn.setText(langZh ? "中/EN" : "EN/中");
        langBtn.setTextColor(SOUTH_TEXT_WHITE);
        langBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(11*0.7));
        langBtn.setGravity(Gravity.CENTER);
        applyRegularFont(langBtn);
        GradientDrawable langBg = pillBackground(SOUTH_SEARCH_BG);
        langBtn.setBackground(langBg);
        langBtn.setOnClickListener(v -> {
            langZh = !langZh;
            langBtn.setText(langZh ? "中/EN" : "EN/中");
            // 更新所有语言相关的文字
            refreshSidebar();
            refreshContentTitle();
            updateHeader();
            if (editHudBtn != null) editHudBtn.setText(t("编辑HUD", "Edit HUD"));
            if (closeSideBtn != null) closeSideBtn.setText(t("关闭", "Close"));
            if (searchEditText != null) {
                searchEditText.setHint(t("\u2315 搜索...", "\u2315 search..."));
            }
            if (activeConfigModule == null) {
                showModuleGrid();
            } else {
                showConfigPage();
            }
            // 通知外部同步语言到 HUD
            if (langChangedListener != null) {
                langChangedListener.onLangChanged(langZh);
            }
        });
        header.addView(langBtn, new LinearLayout.LayoutParams(
                dp(LANG_W), dp(LANG_H)));

        // 间距
        View gap1 = new View(activity);
        header.addView(gap1, new LinearLayout.LayoutParams(dp(8), 1));

        // 关闭按钮 × (36x36, 圆形)
        TextView closeBtn = new TextView(activity);
        closeBtn.setText("\u00D7"); // ×
        closeBtn.setTextColor(SOUTH_TEXT_DIM2);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(18*0.7));
        closeBtn.setGravity(Gravity.CENTER);
        applyRegularFont(closeBtn);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(SOUTH_SEARCH_BG);
        closeBtn.setBackground(closeBg);
        closeBtn.setOnClickListener(v -> close());
        header.addView(closeBtn, new LinearLayout.LayoutParams(
                dp(CLOSE_SIZE), dp(CLOSE_SIZE)));

        // 间距
        View gap2 = new View(activity);
        header.addView(gap2, new LinearLayout.LayoutParams(dp(8), 1));

        // 搜索框 (160x36, 胶囊形)
        searchEditText = new EditText(activity);
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(10*0.7));
        searchEditText.setTextColor(SOUTH_TEXT_DIM2);
        searchEditText.setHintTextColor(SOUTH_TEXT_DIM3);
        searchEditText.setHint(t("\u2315 搜索...", "\u2315 search..."));
        searchEditText.setSingleLine(true);
        searchEditText.setPadding(dp(12), 0, dp(12), 0);
        searchEditText.setGravity(Gravity.CENTER_VERTICAL);
        applyRegularFont(searchEditText);
        searchEditText.setBackground(pillBackground(SOUTH_SEARCH_BG));
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim();
                if (activeConfigModule == null) {
                    showModuleGrid();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        // 阻止点击事件冒泡到根容器（避免点击搜索框关闭面板）
        searchEditText.setOnClickListener(v -> v.requestFocus());
        header.addView(searchEditText, new LinearLayout.LayoutParams(
                dp(SEARCH_W), dp(SEARCH_H)));

        return header;
    }

    /** 更新 Header 标题（品牌名 vs 面包屑） */
    private void updateHeader() {
        if (headerTitleText == null) return;
        if (activeConfigModule != null) {
            // 配置页：面包屑导航 "← 分类 > 模块"
            Category curCat = categories.get(currentCategoryIndex);
            String breadcrumb = "\u2190 " + Category.localizedCategory(curCat.name, langZh)
                    + " > " + Category.localizedName(activeConfigModule.name, langZh);
            headerTitleText.setText(breadcrumb);
            headerSubtitleText.setText("");
        } else {
            // 卡片网格：品牌名 + 分类指示
            headerTitleText.setText("Rlin B1");
            Category curCat = categories.get(currentCategoryIndex);
            headerSubtitleText.setText("\u2190 \u2192 " + Category.localizedCategory(curCat.name, langZh));
        }
    }

    // ==================== 内容标题 ====================

    /** 刷新内容区标题 "Xxx Modules" + "N modules · M enabled" */
    private void refreshContentTitle() {
        if (contentTitleText == null || currentCategoryIndex >= categories.size()) return;
        Category cat = categories.get(currentCategoryIndex);
        contentTitleText.setText(cat.name + " Modules");

        int enabledCount = 0;
        for (ModuleItem m : cat.modules) {
            if (m.enabled) enabledCount++;
        }
        contentSubtitleText.setText(cat.modules.size() + " modules \u00B7 "
                + enabledCount + " enabled");
    }

    // ==================== 内容主体切换 ====================

    /** 显示模块卡片网格 */
    private void showModuleGrid() {
        if (contentBody == null) return;
        contentBody.removeAllViews();

        if (moduleScrollView == null) {
            moduleScrollView = new ScrollView(activity);
            moduleScrollView.setVerticalScrollBarEnabled(false);
            moduleScrollView.setFillViewport(true);
        } else {
            moduleScrollView.removeAllViews();
        }

        // 卡片网格容器
        LinearLayout gridContainer = new LinearLayout(activity);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setPadding(dp(16), dp(4), dp(16), dp(16));

        // 获取过滤后的模块列表
        List<ModuleItem> visibleModules = getFilteredModules();

        // 按行填充卡片（每行 3 列）
        int totalRows = (int) Math.ceil(visibleModules.size() / (float) GRID_COLS);
        for (int row = 0; row < totalRows; row++) {
            LinearLayout rowLayout = new LinearLayout(activity);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);

            for (int col = 0; col < GRID_COLS; col++) {
                int idx = row * GRID_COLS + col;
                if (idx < visibleModules.size()) {
                    ModuleItem m = visibleModules.get(idx);
                    View card = createModuleCard(m);
                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                            0, dp(CARD_H), 1f);
                    if (col < GRID_COLS - 1) {
                        cardLp.setMarginEnd(dp(CARD_GAP));
                    }
                    rowLayout.addView(card, cardLp);
                } else {
                    // 空占位
                    View placeholder = new View(activity);
                    LinearLayout.LayoutParams phLp = new LinearLayout.LayoutParams(
                            0, dp(CARD_H), 1f);
                    if (col < GRID_COLS - 1) {
                        phLp.setMarginEnd(dp(CARD_GAP));
                    }
                    rowLayout.addView(placeholder, phLp);
                }
            }

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(CARD_GAP);
            gridContainer.addView(rowLayout, rowLp);
        }

        moduleScrollView.addView(gridContainer);
        contentBody.addView(moduleScrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 错落进场动画：每行延迟 55ms，260ms 动画
        animateStaggerEntrance(gridContainer, totalRows);
    }

    /** 获取搜索过滤后的模块列表 */
    private List<ModuleItem> getFilteredModules() {
        Category cat = categories.get(currentCategoryIndex);
        List<ModuleItem> result = new ArrayList<>();
        String query = searchQuery.toLowerCase();
        for (ModuleItem m : cat.modules) {
            if (query.isEmpty()
                    || m.name.toLowerCase().contains(query)
                    || m.description.toLowerCase().contains(query)) {
                result.add(m);
            }
        }
        return result;
    }

    // ==================== 模块卡片 ====================

    /**
     * 创建单个模块卡片。
     * 上半部分(78dp)：开关区，点击切换 enabled，背景色随状态动画
     * 下半部分(32dp)：配置栏，点击打开配置页
     */
    private View createModuleCard(ModuleItem module) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(CARD_H)));

        int iconAreaH = CARD_H - CONFIG_BAR_H; // 78dp

        // ---- 上半部分：开关区 ----
        FrameLayout toggleArea = new FrameLayout(activity);
        toggleArea.setTag("toggleArea");

        // 开关区背景（随 enabled 状态变色）
        GradientDrawable toggleBg = new GradientDrawable();
        // 设置圆角：上圆下直
        float corner = dp(CARD_CORNER);
        toggleBg.setCornerRadii(new float[]{
                corner, corner, corner, corner, 0, 0, 0, 0});
        toggleBg.setColor(module.enabled ? SOUTH_TOGGLE_ON : SOUTH_TOGGLE_OFF);
        toggleArea.setBackground(toggleBg);

        // 模块名（居中）
        TextView moduleName = new TextView(activity);
        moduleName.setText(Category.localizedName(module.name, langZh));
        moduleName.setTextColor(SOUTH_TEXT_WHITE);
        moduleName.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(16*0.7));
        moduleName.setGravity(Gravity.CENTER);
        moduleName.setSingleLine(true);
        moduleName.setMaxWidth(dp(100));
        applyRegularFont(moduleName);
        FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        nameLp.gravity = Gravity.CENTER;
        toggleArea.addView(moduleName, nameLp);

        // 点击切换开关
        toggleArea.setOnClickListener(v -> {
            module.enabled = !module.enabled;
            // 颜色动画
            animateToggleColor(toggleArea, module.enabled);
            // 通知监听器
            if (moduleChangedListener != null) {
                moduleChangedListener.onModuleChanged(module.name, module.enabled);
            }
            // 刷新副标题
            refreshContentTitle();
        });

        card.addView(toggleArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(iconAreaH)));

        // ---- 下半部分：配置栏 ----
        FrameLayout configBar = new FrameLayout(activity);
        GradientDrawable configBg = new GradientDrawable();
        // 设置圆角：上直下圆
        configBg.setCornerRadii(new float[]{
                0, 0, 0, 0, corner, corner, corner, corner});
        configBg.setColor(SOUTH_MODULE_BG);
        configBar.setBackground(configBg);

        // 顶部分隔线（2dp, #999999 50% alpha）
        View divider = new View(activity);
        divider.setBackgroundColor(SOUTH_DIVIDER);
        configBar.addView(divider, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2),
                Gravity.TOP));

        // "⚙ 配置" 文字（居中）
        TextView configText = new TextView(activity);
        configText.setText("\u2699 " + t("配置", "Configure"));
        configText.setTextColor(SOUTH_TEXT_DIM2);
        configText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(11*0.7));
        configText.setGravity(Gravity.CENTER);
        applyRegularFont(configText);
        FrameLayout.LayoutParams cfgTextLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cfgTextLp.gravity = Gravity.CENTER;
        configBar.addView(configText, cfgTextLp);

        // 点击打开配置页
        configBar.setOnClickListener(v -> {
            activeConfigModule = module;
            updateHeader();
            showConfigPage();
        });

        card.addView(configBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(CONFIG_BAR_H)));

        return card;
    }

    /** 开关区背景色动画（#CCCCCC ↔ #808080） */
    private void animateToggleColor(View toggleArea, boolean enabled) {
        int fromColor = enabled ? SOUTH_TOGGLE_OFF : SOUTH_TOGGLE_ON;
        int toColor = enabled ? SOUTH_TOGGLE_ON : SOUTH_TOGGLE_OFF;
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofArgb(fromColor, toColor);
        anim.setDuration(260);
        anim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        anim.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            GradientDrawable bg = new GradientDrawable();
            float corner = dp(CARD_CORNER);
            bg.setCornerRadii(new float[]{
                    corner, corner, corner, corner, 0, 0, 0, 0});
            bg.setColor(color);
            toggleArea.setBackground(bg);
        });
        anim.start();
    }

    // ==================== 配置页 ====================

    /** 显示配置页（当前 activeConfigModule 的配置选项） */
    private void showConfigPage() {
        if (contentBody == null || activeConfigModule == null) return;
        contentBody.removeAllViews();

        if (configScrollView == null) {
            configScrollView = new ScrollView(activity);
            configScrollView.setVerticalScrollBarEnabled(false);
            configScrollView.setFillViewport(true);
        } else {
            configScrollView.removeAllViews();
        }

        ModuleItem m = activeConfigModule;

        LinearLayout pageContainer = new LinearLayout(activity);
        pageContainer.setOrientation(LinearLayout.VERTICAL);
        pageContainer.setPadding(dp(16), dp(4), dp(16), dp(16));

        // ---- 分组容器（大圆角矩形，包含所有配置项）----
        LinearLayout groupContainer = new LinearLayout(activity);
        groupContainer.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable groupBg = new GradientDrawable();
        groupBg.setColor(SOUTH_MODULE_BG);
        groupBg.setCornerRadius(dp(GROUP_CORNER));
        groupContainer.setBackground(groupBg);
        groupContainer.setPadding(dp(16), dp(8), dp(16), 0);

        // --- "Enabled" 开关行 ---
        View enabledRow = createConfigSwitchRow(
                t("启用", "Enabled"), m.enabled, (checked) -> {
                    m.enabled = checked;
                    if (moduleChangedListener != null) {
                        moduleChangedListener.onModuleChanged(m.name, checked);
                    }
                    refreshContentTitle();
                });
        groupContainer.addView(enabledRow, configRowParams());

        // --- "添加到快捷键" 按钮（紧接开关下方） ---
        TextView hotkeyBtn = new TextView(activity);
        hotkeyBtn.setText(t("添加到快捷键", "Add to Hotkeys"));
        hotkeyBtn.setTextColor(SOUTH_TEXT_WHITE);
        hotkeyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(12*0.7));
        hotkeyBtn.setGravity(Gravity.CENTER);
        hotkeyBtn.setSingleLine(true);
        applyRegularFont(hotkeyBtn);
        GradientDrawable hkBg = new GradientDrawable();
        hkBg.setColor(ACCENT_1);
        hkBg.setCornerRadius(dp(BOTTOM_BTN_CORNER));
        hotkeyBtn.setBackground(hkBg);
        hotkeyBtn.setOnClickListener(v -> {
            if (addHotkeyListener != null) {
                addHotkeyListener.onAddHotkey(m.name);
            }
            hotkeyBtn.setText(t("已添加/移除", "Added/Removed"));
            hotkeyBtn.postDelayed(() -> {
                hotkeyBtn.setText(t("添加到快捷键", "Add to Hotkeys"));
            }, 1000);
        });
        LinearLayout.LayoutParams hkLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(BOTTOM_BTN_H));
        hkLp.topMargin = dp(8);
        hkLp.bottomMargin = dp(4);
        groupContainer.addView(hotkeyBtn, hkLp);

        // --- 模块额外配置项 ---
        for (ModuleItem.ConfigOption opt : m.options) {
            View optRow = createConfigOptionRow(opt);
            if (optRow != null) {
                groupContainer.addView(optRow, configRowParams());
            }
        }

        // --- 模块自身的滑块（Intensity 等）---
        if (m.hasSlider) {
            View sliderRow = createConfigSliderRow(
                    t("数值", "Value"), m.sliderMin, m.sliderMax,
                    m.getRealValue(), m.sliderUnit != null ? m.sliderUnit : "",
                    (val) -> {
                        m.sliderValue = (m.sliderMin == m.sliderMax) ? 0f
                                : (val - m.sliderMin) / (m.sliderMax - m.sliderMin);
                    });
            groupContainer.addView(sliderRow, configRowParams());
        }

        // --- 描述区 ---
        LinearLayout descBox = new LinearLayout(activity);
        descBox.setOrientation(LinearLayout.VERTICAL);
        descBox.setPadding(0, dp(8), 0, dp(8));

        TextView descLabel = new TextView(activity);
        descLabel.setText(t("描述", "Description"));
        descLabel.setTextColor(SOUTH_TEXT_DIM2);
        descLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(12*0.7));
        applyRegularFont(descLabel);
        descBox.addView(descLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView descText = new TextView(activity);
        descText.setText(m.description);
        descText.setTextColor(SOUTH_TEXT_WHITE);
        descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(13*0.7));
        applyRegularFont(descText);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(6);
        descBox.addView(descText, descLp);

        groupContainer.addView(descBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));

        // --- 底部装饰条（3dp, ACCENT_1 30% alpha）---
        View decorBar = new View(activity);
        // 装饰条在圆角底部，用单独的 drawable 控制底部圆角
        GradientDrawable decorBg = new GradientDrawable();
        decorBg.setColor(Color.argb(77, 128, 128, 128)); // #808080 30%
        decorBg.setCornerRadii(new float[]{
                0, 0, 0, 0, dp(GROUP_CORNER), dp(GROUP_CORNER), dp(GROUP_CORNER), dp(GROUP_CORNER)});
        decorBar.setBackground(decorBg);
        groupContainer.addView(decorBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        pageContainer.addView(groupContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        configScrollView.addView(pageContainer);
        contentBody.addView(configScrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 配置页滑入动画
        configScrollView.setTranslationX(dp(20));
        configScrollView.setAlpha(0f);
        configScrollView.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(260)
                .setInterpolator(new OvershootInterpolator(1.0f))
                .start();
    }

    /** 配置行 LayoutParams（高度 48dp + 8dp 间距） */
    private LinearLayout.LayoutParams configRowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(CONFIG_ROW_H));
        lp.bottomMargin = dp(CONFIG_ROW_GAP);
        return lp;
    }

    /** 创建配置页开关行 */
    private View createConfigSwitchRow(String label, boolean initialValue,
                                        java.util.function.Consumer<Boolean> setter) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(SOUTH_TEXT_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(14*0.7));
        applyRegularFont(labelView);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        SwitchView sw = new SwitchView(activity, initialValue);
        // 浅色主题配色
        sw.setGradientColors(SOUTH_TOGGLE_ON, SOUTH_TOGGLE_ON);
        sw.setOffColor(SOUTH_TOGGLE_OFF);
        sw.setThumbColor(KNOB_COLOR);
        sw.setOnCheckedChangedListener((v, checked) -> setter.accept(checked));
        row.addView(sw, new LinearLayout.LayoutParams(
                dp(SWITCH_W), dp(SWITCH_H)));

        return row;
    }

    /** 创建配置页滑块行 */
    private View createConfigSliderRow(String label, float min, float max,
                                        float value, String suffix,
                                        java.util.function.Consumer<Float> setter) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(SOUTH_TEXT_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(14*0.7));
        applyRegularFont(labelView);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 数值显示
        TextView valueView = new TextView(activity);
        valueView.setTextColor(SOUTH_TEXT_DIM2);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(13*0.7));
        valueView.setSingleLine(true);
        applyRegularFont(valueView);
        String valStr = formatValue(value) + (suffix != null ? suffix : "");
        valueView.setText(valStr);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valLp.setMarginEnd(dp(8));
        row.addView(valueView, valLp);

        // SliderView（轨道 55% 宽度）
        SliderView slider = new SliderView(activity, min, max, value, suffix);
        slider.setGradientColors(ACCENT_1, ACCENT_2);
        slider.setTrackColor(SOUTH_SLIDER_TRACK);
        // thumb 颜色默认为 Color.WHITE，即 KNOB_COLOR，无需额外设置
        slider.setOnProgressChangedListener((v, progress, val) -> {
            setter.accept(val);
            valueView.setText(formatValue(val) + (suffix != null ? suffix : ""));
        });
        row.addView(slider, new LinearLayout.LayoutParams(
                dp(160), dp(32)));

        return row;
    }

    /** 创建配置项行（下拉框 / 开关 / 滑块 / 按钮） */
    private View createConfigOptionRow(ModuleItem.ConfigOption opt) {
        switch (opt.type) {
            case ModuleItem.ConfigOption.TYPE_DROPDOWN:
                return createConfigDropdownRow(opt);
            case ModuleItem.ConfigOption.TYPE_SWITCH:
                return createConfigSwitchRow(
                        Category.localizedName(opt.label, langZh),
                        opt.boolValue,
                        (checked) -> opt.boolValue = checked);
            case ModuleItem.ConfigOption.TYPE_SLIDER:
                return createConfigSliderRow(
                        Category.localizedName(opt.label, langZh),
                        opt.sliderMin, opt.sliderMax,
                        opt.getRealSliderValue(),
                        opt.sliderUnit != null ? opt.sliderUnit : "",
                        (val) -> {
                            if (opt.sliderMax > opt.sliderMin) {
                                opt.sliderValue = (val - opt.sliderMin)
                                        / (opt.sliderMax - opt.sliderMin);
                            }
                        });
            case ModuleItem.ConfigOption.TYPE_BUTTON:
                return createConfigButtonRow(opt);
            default:
                return null;
        }
    }

    /** 创建下拉框配置行（点击循环切换选项） */
    private View createConfigDropdownRow(ModuleItem.ConfigOption opt) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(activity);
        labelView.setText(Category.localizedName(opt.label, langZh));
        labelView.setTextColor(SOUTH_TEXT_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(14*0.7));
        applyRegularFont(labelView);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 当前选项显示框（可点击循环）
        TextView choiceView = new TextView(activity);
        choiceView.setTextColor(SOUTH_TEXT_DIM2);
        choiceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(13*0.7));
        choiceView.setGravity(Gravity.CENTER);
        choiceView.setSingleLine(true);
        choiceView.setPadding(dp(12), dp(6), dp(12), dp(6));
        applyRegularFont(choiceView);
        GradientDrawable choiceBg = new GradientDrawable();
        choiceBg.setColor(SOUTH_SEARCH_BG);
        choiceBg.setCornerRadius(dp(6));
        choiceView.setBackground(choiceBg);
        updateDropdownText(choiceView, opt);

        choiceView.setOnClickListener(v -> {
            if (opt.dropdownChoices != null && opt.dropdownChoices.length > 0) {
                opt.dropdownIndex = (opt.dropdownIndex + 1) % opt.dropdownChoices.length;
                updateDropdownText(choiceView, opt);
                // 淡入动画
                choiceView.setAlpha(0.3f);
                choiceView.animate().alpha(1f).setDuration(200).start();
            }
        });

        row.addView(choiceView, new LinearLayout.LayoutParams(
                dp(120), ViewGroup.LayoutParams.WRAP_CONTENT));

        return row;
    }

    /** 更新下拉框当前选项文字 */
    private void updateDropdownText(TextView view, ModuleItem.ConfigOption opt) {
        if (opt.dropdownChoices != null && opt.dropdownIndex >= 0
                && opt.dropdownIndex < opt.dropdownChoices.length) {
            view.setText(opt.dropdownChoices[opt.dropdownIndex] + " \u25BC");
        }
    }

    /** 创建按钮配置行（全宽按钮，如 Save Config / Load Config） */
    private View createConfigButtonRow(ModuleItem.ConfigOption opt) {
        TextView btn = new TextView(activity);
        btn.setText(Category.localizedName(opt.label, langZh));
        btn.setTextColor(SOUTH_TEXT_WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float)(14*0.7));
        btn.setGravity(Gravity.CENTER);
        applyRegularFont(btn);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SOUTH_SEARCH_BG);
        bg.setCornerRadius(dp(8));
        btn.setBackground(bg);
        btn.setOnClickListener(v -> {
            // 按钮动作（暂无具体实现，仅日志）
            Log.i(TAG, "Config button clicked: " + opt.label);
            // 按下反馈动画
            btn.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                    .withEndAction(() -> btn.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                    .start();
        });
        return btn;
    }

    // ==================== 页面切换动画 ====================

    /** 切换到卡片网格（带动画） */
    private void switchToModuleGridWithAnim() {
        activeConfigModule = null;
        updateHeader();
        refreshContentTitle();

        // 如果有配置页正在显示，淡出后再显示卡片网格
        if (configScrollView != null && configScrollView.getParent() != null) {
            configScrollView.animate()
                    .alpha(0f)
                    .translationX(dp(20))
                    .setDuration(180)
                    .withEndAction(() -> showModuleGrid())
                    .start();
        } else {
            showModuleGrid();
        }
    }

    /** 错落进场动画：每行延迟 55ms，260ms 动画，backEaseOut 风格 */
    private void animateStaggerEntrance(ViewGroup gridContainer, int totalRows) {
        if (gridContainer == null) return;
        OvershootInterpolator interpolator = new OvershootInterpolator(1.2f);
        for (int i = 0; i < gridContainer.getChildCount(); i++) {
            View row = gridContainer.getChildAt(i);
            row.setAlpha(0f);
            row.setTranslationY(dp(14));
            row.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setDuration(260)
                    .setStartDelay(i * 55L)
                    .setInterpolator(interpolator)
                    .start();
        }
    }

    // ==================== 入场 / 退场动画 ====================

    /** 面板入场动画：缩放 0.85→1.0 + 透明度 0→1，OvershootInterpolator(1.2f)，300ms */
    private void animateOpen() {
        if (panelView == null) return;
        panelView.setAlpha(0f);
        panelView.setScaleX(0.85f);
        panelView.setScaleY(0.85f);
        panelView.setPivotX(panelView.getWidth() / 2f);
        panelView.setPivotY(panelView.getHeight() / 2f);

        panelView.post(() -> {
            panelView.setPivotX(panelView.getWidth() / 2f);
            panelView.setPivotY(panelView.getHeight() / 2f);
            panelView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        });
    }

    /** 面板退场动画 */
    private void animateClose() {
        if (panelView == null) { removeFromWindow(); return; }
        panelView.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(180)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    removeFromWindow();
                })
                .start();
    }

    // ==================== 工具方法 ====================

    /** dp 转 px */
    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 从 Client 分类的 ClickGUI 模块读取 GUI Scale 配置 */
    private float getGuiScaleMultiplier() {
        if (categories == null) return 1f;
        for (Category cat : categories) {
            if (!cat.name.equals("Client")) continue;
            for (ModuleItem m : cat.modules) {
                if (!m.name.equals("ClickGUI")) continue;
                for (ModuleItem.ConfigOption opt : m.options) {
                    if (opt.label.equals("GUI Scale")) {
                        // sliderValue 是 0..1，映射到 0.5x~2.0x
                        return 0.5f + opt.sliderValue * 1.5f;
                    }
                }
            }
        }
        return 1f;
    }

    /** 应用 Comfortaa-Regular 字体 */
    private void applyRegularFont(TextView tv) {
        if (comfortaaRegular != null) {
            tv.setTypeface(comfortaaRegular);
        }
    }

    /** 应用 Comfortaa-Bold 字体 */
    private void applyBoldFont(TextView tv) {
        if (comfortaaBold != null) {
            tv.setTypeface(comfortaaBold);
        }
    }

    /** 中英文切换 */
    private String t(String zh, String en) {
        return langZh ? zh : en;
    }

    /** 创建胶囊形（全圆角）背景 */
    private GradientDrawable pillBackground(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(SEARCH_H / 2));
        return bg;
    }

    /** 数值格式化：整数显示整型，否则保留 1 位小数 */
    private String formatValue(float v) {
        if (Math.abs(v - Math.round(v)) < 0.001f) {
            return String.valueOf(Math.round(v));
        }
        return String.format("%.1f", v);
    }
}
