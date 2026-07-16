package com.visiolab.clickgui.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import com.visiolab.clickgui.model.Category;
import com.visiolab.clickgui.model.ModuleItem;
import com.visiolab.clickgui.ui.widget.SwitchView;
import com.visiolab.clickgui.ui.widget.SliderView;

import java.util.List;

/**
 * ClickGUI 面板 — 原生 View 实现。
 *
 * 布局结构：
 * ┌──────────────────────────────────────────┐
 * │  半透明遮罩 (长按关闭)                      │
 * │  ┌──────────────────────────────────────┐ │
 * │  │  主面板 (圆角+渐变背景+elevation)      │ │
 * │  │ ┌──────────────┐ ┌──────────────────┐ │ │
 * │  │ │  模块列表     │ │  分类导航         │ │ │
 * │  │ │  ScrollView  │ │  Combat          │ │ │
 * │  │ │  [Module]    │ │  Movement        │ │ │
 * │  │ │  [Module]    │ │  Player          │ │ │
 * │  │ │  ...         │ │  Visual          │ │ │
 * │  │ │              │ │  Misc            │ │ │
 * │  │ └──────────────┘ └──────────────────┘ │ │
 * │  └──────────────────────────────────────┘ │
 * └──────────────────────────────────────────┘
 */
public class ClickGuiPanel {

    private static final String TAG = "ClickGuiPanel";

    // 主题色
    private static final int ACCENT_1 = Color.parseColor("#1A73E8");
    private static final int ACCENT_2 = Color.parseColor("#00BCD4");
    private static final int PANEL_BG = Color.parseColor("#1C1B1F");
    private static final int CARD_BG = Color.parseColor("#2A292E");
    private static final int TEXT_PRIMARY = Color.parseColor("#FFFFFF");
    private static final int TEXT_SECONDARY = Color.parseColor("#9AA0A6");

    private final Activity activity;
    private PopupWindow popup;
    private List<Category> categories;
    private int currentCategoryIndex = 0;

    private OnCloseListener closeListener;
    private OnModuleChangedListener moduleChangedListener;

    // 字体
    private Typeface comfortaaRegular;
    private Typeface comfortaaBold;

    public interface OnCloseListener { void onClose(); }
    public interface OnModuleChangedListener { void onModuleChanged(String name, boolean enabled); }

    public ClickGuiPanel(Activity activity) {
        this.activity = activity;
        this.categories = Category.buildDefault();

        // 加载字体
        try {
            java.lang.reflect.Method getAssets = Class.forName("com.visiolab.clickgui.xposed.ModuleResourceLoader")
                .getMethod("getAssets");
            java.lang.reflect.Method isInit = Class.forName("com.visiolab.clickgui.xposed.ModuleResourceLoader")
                .getMethod("isInitialized");
            if ((boolean) isInit.invoke(null)) {
                android.content.res.AssetManager am = (android.content.res.AssetManager) getAssets.invoke(null);
                comfortaaRegular = Typeface.createFromAsset(am, "fonts/Comfortaa-Regular.ttf");
                comfortaaBold = Typeface.createFromAsset(am, "fonts/Comfortaa-Bold.ttf");
            }
        } catch (Throwable e) {
            Log.w(TAG, "Font loading via ModuleResourceLoader failed, using default", e);
        }
    }

    public void setOnCloseListener(OnCloseListener listener) { this.closeListener = listener; }
    public void setOnModuleChangedListener(OnModuleChangedListener listener) { this.moduleChangedListener = listener; }

    public void show() {
        activity.runOnUiThread(() -> {
            View content = buildPanelView();
            int screenW = activity.getResources().getDisplayMetrics().widthPixels;
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;

            popup = new PopupWindow(content, screenW, screenH, true);
            popup.setClippingEnabled(false);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popup.setFocusable(true);
            popup.setAnimationStyle(0); // 无系统动画，自己做

            // 点击外部关闭
            content.setOnClickListener(v -> close());

            popup.setOnDismissListener(() -> {
                if (closeListener != null) closeListener.onClose();
            });

            popup.showAtLocation(activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0);

            // 入场动画
            animateOpen(content);
        });
    }

    public void dismiss() {
        if (popup != null && popup.isShowing()) {
            animateClose(popup.getContentView());
        }
    }

    public boolean isShowing() {
        return popup != null && popup.isShowing();
    }

    private void close() {
        dismiss();
    }

    // ==================== 构建面板 ====================

    private View buildPanelView() {
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;

        // 根容器：全屏，点击空白关闭
        FrameLayout root = new FrameLayout(activity);
        root.setLayoutParams(new ViewGroup.LayoutParams(screenW, screenH));

        // 半透明背景遮罩
        View dim = new View(activity);
        dim.setBackgroundColor(Color.argb(140, 0, 0, 0));
        dim.setOnClickListener(v -> close());
        root.addView(dim, new FrameLayout.LayoutParams(screenW, screenH));

        // 主面板容器
        int panelW = (int) (screenW * 0.82);
        int panelH = (int) (screenH * 0.82);
        FrameLayout panelContainer = new FrameLayout(activity);
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(panelW, panelH);
        panelLp.gravity = Gravity.CENTER;
        panelContainer.setLayoutParams(panelLp);

        // 面板背景：圆角+渐变+阴影
        GradientDrawable panelBg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor("#1E1D22"), Color.parseColor("#252329")}
        );
        panelBg.setCornerRadius(dp(16));
        panelBg.setStroke(dp(1), Color.parseColor("#33312E"));
        panelContainer.setBackground(panelBg);
        panelContainer.setElevation(dp(20));

        // 左右分栏
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainLayout.setPadding(dp(16), dp(16), dp(16), dp(16));

        // 左侧：模块列表
        View leftPanel = buildModuleList(panelW, panelH);
        mainLayout.addView(leftPanel, new LinearLayout.LayoutParams(
            (int)(panelW * 0.60), ViewGroup.LayoutParams.MATCH_PARENT, 1f
        ));

        // 分割线
        View divider = new View(activity);
        divider.setBackgroundColor(Color.parseColor("#33312E"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT);
        divLp.setMarginEnd(dp(12));
        mainLayout.addView(divider, divLp);

        // 右侧：分类导航
        View rightPanel = buildCategoryNav(panelW);
        mainLayout.addView(rightPanel, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        ));

        panelContainer.addView(mainLayout);
        root.addView(panelContainer);
        return root;
    }

    /** 构建模块列表区域 */
    private View buildModuleList(int panelW, int panelH) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);

        // 标题
        TextView title = new TextView(activity);
        title.setText("Modules");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        if (comfortaaBold != null) title.setTypeface(comfortaaBold);
        title.setPadding(dp(4), dp(4), dp(4), dp(8));
        container.addView(title);

        // ScrollView 包裹模块卡片
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setFillViewport(true);

        LinearLayout moduleList = new LinearLayout(activity);
        moduleList.setOrientation(LinearLayout.VERTICAL);

        // 添加当前分类的模块
        refreshModuleList(moduleList);

        scrollView.addView(moduleList);
        container.addView(scrollView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        // 标记 tag 用于刷新
        moduleList.setTag("moduleList");
        container.setTag("moduleListContainer");

        return container;
    }

    /** 刷新模块列表 */
    private void refreshModuleList(LinearLayout moduleList) {
        moduleList.removeAllViews();
        if (currentCategoryIndex < 0 || currentCategoryIndex >= categories.size()) return;

        Category cat = categories.get(currentCategoryIndex);
        for (ModuleItem module : cat.modules) {
            moduleList.addView(createModuleCard(module));
        }
    }

    /** 创建单个模块卡片 */
    private View createModuleCard(ModuleItem module) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        // 卡片背景
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(10));
        cardBg.setColor(CARD_BG);
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.bottomMargin = dp(6);
        card.setLayoutParams(cardLp);

        // 标题行：模块名 + Switch
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(activity);
        name.setText(module.name);
        name.setTextColor(TEXT_PRIMARY);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (comfortaaRegular != null) name.setTypeface(comfortaaRegular);
        header.addView(name, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        // Switch
        SwitchView sw = new SwitchView(activity, module.enabled);
        sw.setOnCheckedChangedListener((v, checked) -> {
            module.enabled = checked;
            // 更新卡片背景色
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(checked ? Color.parseColor("#2D3B4A") : CARD_BG);
            card.setBackground(bg);
            // 通知监听器
            if (moduleChangedListener != null) {
                moduleChangedListener.onModuleChanged(module.name, checked);
            }
        });
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(48), dp(24));
        swLp.setMarginStart(dp(8));
        header.addView(sw, swLp);

        card.addView(header);

        // 设置项（遍历 ConfigOption 列表）
        if (module.options != null && !module.options.isEmpty()) {
            for (ModuleItem.ConfigOption opt : module.options) {
                if (opt.type == ModuleItem.ConfigOption.TYPE_SWITCH) {
                    addSettingRow(card, opt.label, opt.boolValue, (checked) -> {
                        opt.boolValue = checked;
                    });
                } else if (opt.type == ModuleItem.ConfigOption.TYPE_SLIDER) {
                    addSliderRow(card, opt.label, opt.sliderMin, opt.sliderMax,
                        opt.getRealSliderValue(), opt.sliderUnit != null ? opt.sliderUnit : "",
                        (val) -> {
                            opt.sliderValue = (val - opt.sliderMin) / (opt.sliderMax - opt.sliderMin);
                        });
                }
            }
        }
        // 模块自身的滑块
        if (module.hasSlider) {
            addSliderRow(card, "Intensity", module.sliderMin, module.sliderMax,
                module.getRealValue(), module.sliderUnit != null ? module.sliderUnit : "",
                (val) -> { module.sliderValue = (val - module.sliderMin) / (module.sliderMax - module.sliderMin); });
        }

        return card;
    }

    /** 添加布尔设置行 */
    private void addSettingRow(LinearLayout parent, String name,
                                boolean initialValue,
                                java.util.function.Consumer<Boolean> setter) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(6));

        TextView label = new TextView(activity);
        label.setText(name);
        label.setTextColor(TEXT_SECONDARY);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        row.addView(label, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        SwitchView sw = new SwitchView(activity, initialValue);
        sw.setOnCheckedChangedListener((v, checked) -> setter.accept(checked));
        row.addView(sw, new LinearLayout.LayoutParams(dp(40), dp(20)));

        parent.addView(row);
    }

    /** 添加滑块设置行 */
    private void addSliderRow(LinearLayout parent, String name, float min, float max,
                               float value, String suffix,
                               java.util.function.Consumer<Float> setter) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(6));

        TextView label = new TextView(activity);
        label.setText(name);
        label.setTextColor(TEXT_SECONDARY);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        row.addView(label, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        SliderView slider = new SliderView(activity, min, max, value, suffix);
        slider.setOnProgressChangedListener((v, progress, val) -> setter.accept(val));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(120), dp(28));
        row.addView(slider, slp);

        parent.addView(row);
    }

    /** 构建分类导航区域 */
    private View buildCategoryNav(int panelW) {
        LinearLayout nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setPadding(dp(8), dp(4), dp(4), dp(4));

        // 标题
        TextView title = new TextView(activity);
        title.setText("Rlin B1");
        title.setTextColor(ACCENT_2);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (comfortaaBold != null) title.setTypeface(comfortaaBold);
        title.setPadding(dp(4), dp(4), dp(4), dp(16));
        nav.addView(title);

        // 分类按钮
        String[] catNames = {"Combat", "Movement", "Player", "Visual", "Misc"};
        for (int i = 0; i < catNames.length; i++) {
            final int idx = i;
            TextView btn = new TextView(activity);
            btn.setText(catNames[i]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            btn.setPadding(dp(12), dp(10), dp(12), dp(10));
            btn.setGravity(Gravity.CENTER);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(dp(8));
            if (i == currentCategoryIndex) {
                btnBg.setColor(ACCENT_1);
                btn.setTextColor(TEXT_PRIMARY);
            } else {
                btnBg.setColor(Color.parseColor("#2A292E"));
                btn.setTextColor(TEXT_SECONDARY);
            }
            btn.setBackground(btnBg);

            btn.setOnClickListener(v -> {
                currentCategoryIndex = idx;
                // 找到 moduleList 并刷新
                View root = popup.getContentView();
                View found = findViewWithTag(root, "moduleList");
                if (found instanceof LinearLayout) {
                    refreshModuleList((LinearLayout) found);
                }
                // 更新分类按钮颜色
                updateCategoryColors(nav, idx);
            });

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            btnLp.bottomMargin = dp(6);
            nav.addView(btn, btnLp);
        }

        return nav;
    }

    /** 更新分类按钮颜色 */
    private void updateCategoryColors(LinearLayout nav, int selected) {
        for (int i = 0; i < nav.getChildCount(); i++) {
            View child = nav.getChildAt(i);
            if (child instanceof TextView && i > 0) { // 跳过标题
                int btnIndex = i - 1; // 第一个是标题
                TextView btn = (TextView) child;
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(8));
                if (btnIndex == selected) {
                    bg.setColor(ACCENT_1);
                    btn.setTextColor(TEXT_PRIMARY);
                } else {
                    bg.setColor(Color.parseColor("#2A292E"));
                    btn.setTextColor(TEXT_SECONDARY);
                }
                btn.setBackground(bg);
            }
        }
    }

    // ==================== 动画 ====================

    private void animateOpen(View content) {
        // 找到面板容器
        View panel = findPanelContainer(content);
        if (panel == null) return;

        panel.setAlpha(0f);
        panel.setScaleX(0.9f);
        panel.setScaleY(0.9f);
        panel.setPivotX(panel.getWidth() / 2f);
        panel.setPivotY(panel.getHeight() / 2f);

        panel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(250)
            .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
            .start();
    }

    private void animateClose(View content) {
        View panel = findPanelContainer(content);
        if (panel == null) { popup.dismiss(); return; }

        panel.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(150)
            .withEndAction(() -> popup.dismiss())
            .start();
    }

    private View findPanelContainer(View root) {
        if (root instanceof FrameLayout) {
            FrameLayout fl = (FrameLayout) root;
            for (int i = 0; i < fl.getChildCount(); i++) {
                View child = fl.getChildAt(i);
                if (child.getElevation() > 0) return child; // 面板有 elevation
            }
        }
        return null;
    }

    private View findViewWithTag(View root, String tag) {
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View found = findViewWithTag(vg.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ==================== 工具 ====================

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
