# Southside ClickGUI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the ClickGUI main panel from "left sidebar + right module list" to Southside b13 style: grouped sidebar + header bar + module card grid + inline expanded settings panel with capsule toggles and value sliders.

**Architecture:** All changes are confined to 3 files: `ModuleItem.java` (add expanded/tab state), `Category.java` (add section grouping), and `UIRenderer.java` (rewrite sidebar, add header bar, convert list to card grid, add inline expansion with new controls). The existing render pipeline (onDrawFrame flow), BlurPipeline, QuadBatch, TextRenderer, and Anim system are reused without modification. ArrayList HUD, Watermark, Toast, Floating ball, and LoginRenderer are untouched.

**Tech Stack:** Android, OpenGL ES 2.0, Java 8, no third-party libraries.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/com/visiolab/clickgui/model/ModuleItem.java` | Modify | Add `expanded`, `expandAnim`, `activeTab` fields |
| `app/src/main/java/com/visiolab/clickgui/model/Category.java` | Modify | Add `section` field, update `buildDefault()` with section assignments |
| `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java` | Modify | Rewrite sidebar, add header bar, card grid, expanded card panel, new controls, touch handling |

Base path: `/workspace/soar_client_extracted/`

---

### Task 1: Update Data Models

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/model/ModuleItem.java`
- Modify: `app/src/main/java/com/visiolab/clickgui/model/Category.java`

- [ ] **Step 1: Add expanded state and animation to ModuleItem**

In `ModuleItem.java`, add these fields after the existing `hudAnim` field (line 23):

```java
// 卡片展开/收起状态：点击 ▾ 箭头切换，内联展开设置面板
public boolean expanded = false;
public final Anim expandAnim = new Anim(0f, 14f); // 0=收起, 1=展开
public int activeTab = 0; // 当前标签页索引
```

- [ ] **Step 2: Add section field to Category**

In `Category.java`, add a `section` field and update the constructor:

```java
public final String section;

public Category(String name, String icon, String section) {
    this.name = name;
    this.icon = icon;
    this.section = section;
}
```

- [ ] **Step 3: Update buildDefault() with section assignments**

Replace the entire `buildDefault()` method in `Category.java`:

```java
public static List<Category> buildDefault() {
    List<Category> list = new ArrayList<>();

    Category home = new Category("Home", "\u2302", "MAIN");
    home.add(new ModuleItem("Watermark", "Display client watermark", true));
    home.add(new ModuleItem("Notifications", "Toast notifications on toggle", true));
    list.add(home);

    Category modulesCat = new Category("Module", "\u2694", "MAIN");
    modulesCat.add(new ModuleItem("KillAura", "Auto-attack nearby entities", false,
            true, 0.6f, 1, 8, "blocks"));
    modulesCat.add(new ModuleItem("Sprint", "Auto sprint", true));
    modulesCat.add(new ModuleItem("Scaffold", "Auto place blocks", false));
    modulesCat.add(new ModuleItem("Speed", "Movement speed boost", false,
            true, 0.3f, 1, 10, "level"));
    modulesCat.add(new ModuleItem("FullBright", "Maximum brightness", true));
    modulesCat.add(new ModuleItem("Freecam", "Free camera view", false));
    list.add(modulesCat);

    Category cosmetics = new Category("Cosmetics", "\u2726", "FEATURES");
    cosmetics.add(new ModuleItem("Cape", "Custom cape", true));
    cosmetics.add(new ModuleItem("Particles", "Custom particle effects", false));
    list.add(cosmetics);

    Category music = new Category("Music", "\u266A", "FEATURES");
    music.add(new ModuleItem("Background Music", "Menu background music", true,
            true, 0.5f, 0, 1, "%"));
    list.add(music);

    Category profile = new Category("Profile", "\u263A", "FEATURES");
    profile.add(new ModuleItem("Show Skin", "Show skin preview", true));
    list.add(profile);

    Category screenshot = new Category("Screenshot", "\u2710", "FEATURES");
    screenshot.add(new ModuleItem("Auto Upload", "Auto upload screenshots", false));
    list.add(screenshot);

    Category setting = new Category("Setting", "\u2699", "SYSTEM");
    setting.add(new ModuleItem("Dark Theme", "Dark color scheme", true));
    setting.add(new ModuleItem("Blur Background", "Blur panel background", false));
    setting.add(new ModuleItem("ArrayList", "Show enabled modules list", true));
    setting.add(new ModuleItem("Shadow Enabled", "Panel edge shadow", false));
    ModuleItem gradientSpeedItem = new ModuleItem("Gradient Speed", "Gradient flow speed", false,
            true, 0.4f, 0, 1, "");
    gradientSpeedItem.showToggle = false;
    setting.add(gradientSpeedItem);
    setting.add(new ModuleItem("Notification", "Toggle notifications", true));
    list.add(setting);

    return list;
}
```

- [ ] **Step 4: Verify it compiles**

Run: `cd /workspace/soar_client_extracted && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (or at least no errors in the two modified files)

- [ ] **Step 5: Commit**

```bash
cd /workspace/soar_client_extracted
git add app/src/main/java/com/visiolab/clickgui/model/ModuleItem.java app/src/main/java/com/visiolab/clickgui/model/Category.java
git commit -m "feat: add expanded state to ModuleItem and section grouping to Category"
```

---

### Task 2: Update Default Accent Color to Blue

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java` (lines 50-51)

- [ ] **Step 1: Change default ACCENT_1 and ACCENT_2 to Southside blue**

Find lines 50-51 in `UIRenderer.java`:

```java
private float[] ACCENT_1 = {0.15f, 0.65f, 0.60f, 1f};
private float[] ACCENT_2 = {0.10f, 0.55f, 0.65f, 1f};
```

Replace with:

```java
// 默认强调色：南方客户端风格蓝色 #3B82F6
private float[] ACCENT_1 = {0.231f, 0.510f, 0.965f, 1f}; // #3B82F6
private float[] ACCENT_2 = {0.231f, 0.510f, 0.965f, 1f}; // 同色，主题切换时由 applyThemeColors 更新
```

- [ ] **Step 2: Update Bubblegum theme (index 0) to blue-based default**

Since the default theme (index 0, "Bubblegum") should produce the blue accent, update its color nodes. Find the THEME_COLORS array (around line 71) and replace the first theme entry (index 0, Bubblegum). Change the theme name from "Bubblegum" to "Blue" in THEME_NAMES:

In `THEME_NAMES` array (line 68-70):
```java
private static final String[] THEME_NAMES = {
    "Blue", "Spaced", "Trans", "Rainbow", "Watermelon", "Sunset", "Poison", "Custom"
};
```

Replace the THEME_COLORS[0] entry (Bubblegum pink nodes) with blue nodes:

```java
// 0: Blue (南方客户端默认蓝色系)
new float[][]{
    hexToColor("#3B82F6"), hexToColor("#4B6CFA"), hexToColor("#5A7DFF"), hexToColor("#6B8EFF"),
    hexToColor("#3B82F6"), hexToColor("#2B72E6"), hexToColor("#1B62D6"), hexToColor("#0B52C6")
},
```

- [ ] **Step 3: Update default customHue to blue**

Find line 54:
```java
private float customHue = 0.5f, customSat = 0.8f, customVal = 0.8f;
```

Replace with:
```java
private float customHue = 0.6f, customSat = 0.85f, customVal = 0.96f; // 默认蓝色 #3B82F6
```

- [ ] **Step 4: Verify compilation**

Run: `cd /workspace/soar_client_extracted && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
cd /workspace/soar_client_extracted
git add app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java
git commit -m "feat: change default accent color to Southside blue #3B82F6"
```

---

### Task 3: Add New Color Constants for Southside Style

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java` (after line 46)

- [ ] **Step 1: Add Southside-specific color constants**

After the `KNOB_COLOR` definition (line 46), add:

```java
// ---- 南方客户端风格的额外颜色常量 ----
private static final float[] HDR_BG = {0.118f, 0.118f, 0.157f, 0.95f};     // #1E1E28 标题栏/底栏
private static final float[] SOUTH_SIDEBAR_BG = {0.086f, 0.086f, 0.118f, 0.95f}; // #16161E 侧栏
private static final float[] SOUTH_CARD_BORDER = {0.165f, 0.165f, 0.220f, 1f}; // #2A2A38 卡片边框
private static final float[] SOUTH_CARD_BORDER_SEL = {0.231f, 0.510f, 0.965f, 1f}; // #3B82F6 选中边框
private static final float[] SOUTH_SEARCH_BG = {0.137f, 0.137f, 0.188f, 1f}; // #232330 搜索框
private static final float[] SOUTH_TRACK_OFF = {0.200f, 0.200f, 0.250f, 1f}; // #333340 关闭轨道
private static final float[] SOUTH_TAB_OFF = {0.533f, 0.533f, 0.627f, 1f};  // #8888A0 未选标签
private static final float[] SOUTH_SECTION_LBL = {0.333f, 0.333f, 0.376f, 1f}; // #555560 分组标题
private static final float[] SOUTH_SLIDER_TRACK = {0.165f, 0.165f, 0.220f, 1f}; // #2A2A38 滑块轨道
```

- [ ] **Step 2: Add search state fields**

After line 296 (the `flowOffset` field), add:

```java
// ---- 搜索框状态 ----
private String searchQuery = "";
private boolean searchActive = false;
```

- [ ] **Step 3: Verify compilation**

Run: `cd /workspace/soar_client_extracted && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
cd /workspace/soar_client_extracted
git add app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java
git commit -m "feat: add Southside color constants and search state fields"
```

---

### Task 4: Rewrite Sidebar with Grouped Sections

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java` — method `drawSidebarAndHeader()` (line 1329)

- [ ] **Step 1: Rewrite drawSidebarAndHeader() with grouped sections**

Replace the entire `drawSidebarAndHeader` method (lines 1329-1372) with:

```java
private void drawSidebarAndHeader(float x, float y, float w, float h, float alpha) {
    float sbW = sidebarW * (w / panelW);
    float[] sbg = withAlpha(SOUTH_SIDEBAR_BG, alpha);
    quad.addRect(x, y, sbW, h, radius, sbg);
    // 补右边让侧栏和主面板拼接自然
    quad.addRect(x + sbW - radius, y, radius, h, 0f, sbg);

    categoryHitRects.clear();

    // ---- 分组侧栏：MAIN / FEATURES / SYSTEM ----
    float pad = 8f * uiScale * (h / panelH);
    float rowH = 28f * uiScale * (h / panelH);
    float iconSize = 12f * uiScale;
    float txtSize = 12f * uiScale;
    float sectionLblSize = 9f * uiScale;
    float sectionGap = 16f * uiScale * (h / panelH);
    float curY = y + pad + 6f * uiScale * (h / panelH);

    String prevSection = null;
    for (int i = 0; i < categories.size(); i++) {
        Category c = categories.get(i);
        // 新分组标题
        if (prevSection == null || !c.section.equals(prevSection)) {
            curY += (prevSection != null ? sectionGap : 0f);
            // 分组标题不画背景，只留文字（在 drawTextLayer 中绘制）
            curY += 18f * uiScale * (h / panelH); // 预留分组标题空间
            prevSection = c.section;
        }
        float ry = curY;
        categoryHitRects.add(new float[]{x, ry, sbW, rowH});
        if (i == currentCategoryIndex) {
            highlightY.setTarget(ry - y);
        }
        curY += rowH + 4f * uiScale * (h / panelH);
    }

    // 流动的高亮条：蓝色半透明背景 + 左侧蓝色竖条
    float[] hlBg = withAlpha(ACCENT_1, alpha * 0.15f);
    quad.addRect(x, y + highlightY.get(), sbW, rowH, 0f, hlBg);
    quad.addRect(x, y + highlightY.get(), 3f * uiScale, rowH, 0f, withAlpha(ACCENT_1, alpha));

    // ---- 内容区标题 + 裁剪区域 ----
    float contentX = x + sbW + pad;
    float contentW = w - sbW - pad * 2;
    float titleH = 34f * uiScale * (h / panelH);
    float headerH = 44f * uiScale * (h / panelH);

    // 裁剪区域：标题行下方到底栏上方
    clipX = contentX - 2f;
    clipY = y + headerH + titleH;
    clipW = contentW + 4f;
    clipH = (y + h - pad) - clipY;

    // 滚动范围钳制
    Category cat = categories.get(currentCategoryIndex);
    float cardH = 70f * uiScale * (h / panelH);
    float cardGap = 12f * uiScale * (h / panelH);
    float cols = 2f;
    float cardW = (contentW - cardGap) / cols;
    int rows = (int) Math.ceil(cat.modules.size() / cols);
    float totalContentH = rows * (cardH + cardGap) - cardGap;
    float settingExtraH = cat.name.equals("Setting") ? accentCardH(h) + cardGap : 0f;
    maxScroll = Math.max(0f, totalContentH + settingExtraH + pad + titleH - h + headerH);
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /workspace/soar_client_extracted && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (may have unused variable warnings, that's OK)

- [ ] **Step 3: Commit**

```bash
cd /workspace/soar_client_extracted
git add app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java
git commit -m "feat: rewrite sidebar with grouped sections (MAIN/FEATURES/SYSTEM)"
```

---

### Task 5: Add Header Bar with Search and Close

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java`

- [ ] **Step 1: Add drawHeaderBar() method**

Add this new method right after `drawSidebarAndHeader()`:

```java
private float[] searchBoxRect = null;
private float[] closeBtnRect = null;

private void drawHeaderBar(float x, float y, float w, float h, float alpha) {
    float sbW = sidebarW * (w / panelW);
    float hdrH = 44f * uiScale * (h / panelH);
    float hdrX = x + sbW;
    float hdrW = w - sbW;

    // 标题栏背景
    quad.addRect(hdrX, y, hdrW, hdrH, 0f, withAlpha(HDR_BG, alpha));
    // 补左上圆角：标题栏和面板顶部对齐
    quad.addRect(hdrX, y, hdrW, radius, 0f, withAlpha(HDR_BG, alpha));

    // 右侧搜索框
    float searchW = 120f * uiScale * (w / panelW);
    float searchH = 24f * uiScale * (h / panelH);
    float searchX = x + w - searchW - 36f * uiScale * (w / panelW);
    float searchY = y + (hdrH - searchH) / 2f;
    quad.addRect(searchX, searchY, searchW, searchH, searchH / 2f,
            withAlpha(SOUTH_SEARCH_BG, alpha));
    quad.addRect(searchX, searchY, searchW, searchH, searchH / 2f,
            withAlpha(SOUTH_CARD_BORDER, alpha * 0.5f));
    searchBoxRect = new float[]{searchX, searchY, searchW, searchH};

    // 关闭按钮 ×
    float closeSize = 20f * uiScale * (h / panelH);
    float closeX = x + w - closeSize - 12f * uiScale * (w / panelW);
    float closeY = y + (hdrH - closeSize) / 2f;
    closeBtnRect = new float[]{closeX, closeY, closeSize, closeSize};
}
```

- [ ] **Step 2: Call drawHeaderBar() from onDrawFrame**

In `onDrawFrame`, after the call to `drawSidebarAndHeader(drawX, drawY, drawW, drawH, panelAlpha);` (line 919), add:

```java
drawHeaderBar(drawX, drawY, drawW, drawH, panelAlpha);
quad.flush(screenW, screenH);
```

- [ ] **Step 3: Add header text to drawTextLayer()**

In `drawTextLayer()` (line 1573), at the very start of the method body, add:

```java
// ---- 标题栏文字 ----
float hdrH = 44f * uiScale * (h / panelH);
float sbW = sidebarW * (w / panelW);
float pad = 8f * uiScale * (h / panelH);
// 品牌名
text.draw("SOAR b1", x + sbW + 14f * uiScale, y + hdrH / 2f - 6f * uiScale,
        13f * uiScale, withAlpha(TEXT_MAIN, alpha), screenW, screenH);
// 面包屑导航
Category curCat = categories.get(currentCategoryIndex);
String breadcrumb = "\u2190 \u2192 " + curCat.name;
text.draw(breadcrumb, x + sbW + 80f * uiScale, y + hdrH / 2f - 5f * uiScale,
        11f * uiScale, withAlpha(TEXT_DIM, alpha), screenW, screenH);
// 搜索框占位文字
if (searchBoxRect != null) {
    String searchText = searchActive ? searchQuery : "\u2315 search...";
    text.draw(searchText, searchBoxRect[0] + 12f * uiScale,
            searchBoxRect[1] + searchBoxRect[3] / 2f - 4f * uiScale,
            10f * uiScale, withAlpha(TEXT_DIM, alpha), screenW, screenH);
}
// 关闭按钮
if (closeBtnRect != null) {
    text.draw("\u00D7", closeBtnRect[0] + 4f * uiScale,
            closeBtnRect[1] + 2f * uiScale,
            14f * uiScale, withAlpha(TEXT_DIM, alpha), screenW, screenH);
}
```

- [ ] **Step 4: Add sidebar section labels and category text to drawTextLayer()**

In `drawTextLayer()`, after the header text block added above, replace the existing sidebar text loop (lines 1578-1587) with:

```java
// ---- 分组侧栏文字 ----
float rowH = 28f * uiScale * (h / panelH);
float sPad = 8f * uiScale * (h / panelH);
float catY = y + sPad + 6f * uiScale * (h / panelH);
float iconSize = 12f * uiScale;
float txtSize = 12f * uiScale;
float sectionLblSize = 9f * uiScale;
float sectionGap = 16f * uiScale * (h / panelH);
float curY = catY;
String prevSection = null;
for (int i = 0; i < categories.size(); i++) {
    Category c = categories.get(i);
    if (prevSection == null || !c.section.equals(prevSection)) {
        curY += (prevSection != null ? sectionGap : 0f) + 18f * uiScale * (h / panelH);
        // 分组标题
        text.draw(c.section, x + 14f * uiScale, curY - 8f * uiScale * (h / panelH),
                sectionLblSize, withAlpha(SOUTH_SECTION_LBL, alpha), screenW, screenH);
        prevSection = c.section;
    }
    float ry = curY;
    boolean sel = i == currentCategoryIndex;
    float[] col = withAlpha(sel ? KNOB_COLOR : TEXT_DIM, alpha);
    text.draw(c.icon, x + 14f * uiScale, ry + rowH / 2f - 6f * uiScale, iconSize, col, screenW, screenH);
    text.draw(c.name, x + 32f * uiScale, ry + rowH / 2f - 5f * uiScale, txtSize, col, screenW, screenH);
    curY += rowH + 4f * uiScale * (h / panelH);
}

// 内容区标题
Category cat = categories.get(currentCategoryIndex);
float contentX = x + sbW + pad;
text.draw(cat.name + " Modules", contentX, y + hdrH + 10f * uiScale * (h / panelH),
        14f * uiScale, withAlpha(TEXT_MAIN, alpha), screenW, screenH);
```

- [ ] **Step 5: Verify compilation**

Run: `cd /workspace/soar_client_extracted && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
cd /workspace/soar_client_extracted
git add app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java
git commit -m "feat: add header bar with brand name, breadcrumb, search box, close button"
```

---

### Task 6: Convert Module List to Card Grid

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java` — method `drawModuleCards()` (line 1407)

- [ ] **Step 1: Rewrite drawModuleCards() as a 2-column card grid**

Replace the entire `drawModuleCards` method (lines 1407-1572) with:

```java
private void drawModuleCards(float x, float y, float w, float h, float alpha) {
    moduleHitRects.clear();
    float sbW = sidebarW * (w / panelW);
    float pad = 8f * uiScale * (h / panelH);
    float contentX = x + sbW + pad;
    float contentW = w - sbW - pad * 2;
    float hdrH = 44f * uiScale * (h / panelH);
    float titleH = 34f * uiScale * (h / panelH);
    float startY = y + hdrH + titleH - contentScroll.get();

    Category cat = categories.get(currentCategoryIndex);

    // 卡片网格参数
    float cardGap = 12f * uiScale * (h / panelH);
    int cols = 2;
    float cardW = (contentW - cardGap * (cols - 1)) / cols;
    float cardH = 70f * uiScale * (h / panelH);

    // 过滤搜索结果
    java.util.List<ModuleItem> visibleModules = new java.util.ArrayList<>();
    for (ModuleItem m : cat.modules) {
        if (searchQuery.isEmpty() || m.name.toLowerCase().contains(searchQuery.toLowerCase())
                || m.description.toLowerCase().contains(searchQuery.toLowerCase())) {
            visibleModules.add(m);
        }
    }

    int rows = (int) Math.ceil(visibleModules.size() / (float) cols);
    float totalContentH = 0;
    for (ModuleItem m : visibleModules) {
        totalContentH += cardH + cardGap;
        if (m.expanded) totalContentH += 120f * uiScale * (h / panelH); // 展开高度
    }
    float settingExtraH = cat.name.equals("Setting") ? accentCardH(h) + cardGap : 0f;
    maxScroll = Math.max(0f, totalContentH + settingExtraH + pad + titleH - h + hdrH);

    float curY = startY;
    for (int i = 0; i < visibleModules.size(); i++) {
        ModuleItem m = visibleModules.get(i);
        int col = i % cols;
        float cardX = contentX + col * (cardW + cardGap);

        if (curY + cardH < clipY || curY > clipY + clipH) {
            curY += cardH + cardGap;
            if (m.expanded) curY += 120f * uiScale * (h / panelH);
            continue;
        }

        float reveal = rowRevealEase(alpha, i);
        if (reveal <= 0.003f) {
            curY += cardH + cardGap;
            if (m.expanded) curY += 120f * uiScale * (h / panelH);
            continue;
        }
        float rowAlpha = alpha * reveal;
        float drawY = curY + (1f - reveal) * 14f * uiScale;

        // 展开动画
        m.expandAnim.setTarget(m.expanded ? 1f : 0f);
        m.expandAnim.update(0.016f);
        float expandT = m.expandAnim.get();
        float expandH = expandT * 120f * uiScale * (h / panelH);
        float actualCardH = cardH + expandH;

        // 注册热区：整个卡片区域
        moduleHitRects.add(new Object[]{new float[]{cardX, drawY, cardW, actualCardH}, "CARD", m});

        // 展开箭头热区（右下角 28x28）
        float arrowSize = 28f * uiScale * (h / panelH);
        moduleHitRects.add(new Object[]{
            new float[]{cardX + cardW - arrowSize, drawY + cardH - arrowSize, arrowSize, arrowSize},
            "ARROW", m});

        // 卡片背景
        float[] borderColor = m.expanded ? SOUTH_CARD_BORDER_SEL : SOUTH_CARD_BORDER;
        quad.addRect(cardX, drawY, cardW, actualCardH, 8f * uiScale,
                withAlpha(CARD_BG, rowAlpha));
        // 卡片边框（用细矩形模拟）
        quad.addRect(cardX, drawY, cardW, 1f * uiScale, 0f, withAlpha(borderColor, rowAlpha));
        quad.addRect(cardX, drawY + actualCardH - 1f * uiScale, cardW, 1f * uiScale, 0f, withAlpha(borderColor, rowAlpha));
        quad.addRect(cardX, drawY, 1f * uiScale, actualCardH, 0f, withAlpha(borderColor, rowAlpha));
        quad.addRect(cardX + cardW - 1f * uiScale, drawY, 1f * uiScale, actualCardH, 0f, withAlpha(borderColor, rowAlpha));

        // 底部启用横条（3px蓝色，模块启用时显示）
        if (m.enabled) {
            quad.addRect(cardX, drawY + cardH - 3f * uiScale, cardW, 3f * uiScale, 0f,
                    withAlpha(ACCENT_1, rowAlpha));
        }

        // 展开的设置面板内容
        if (expandT > 0.01f) {
            float expandAlpha = rowAlpha * expandT;
            drawExpandedCardContent(cardX, drawY + cardH, cardW, expandH, expandAlpha, m);
        }

        curY += cardH + cardGap;
        if (m.expanded) curY += 120f * uiScale * (h / panelH);
    }

    // Setting 分类的取色器面板（保持原有逻辑）
    if (cat.name.equals("Setting")) {
        float cy = curY;
        float accH = accentCardH(h);
        if (cy + accH >= clipY && cy <= clipY + clipH) {
            quad.addRect(contentX, cy, contentW, accH, 12f * uiScale, withAlpha(CARD_BG, alpha));
            drawSettingAccentPicker(contentX, cy, contentW, alpha);
        }
    }
}
```

- [ ] **Step 2: Add drawExpandedCardContent() stub**

Add this placeholder method after `drawModuleCards()`:

```java
/** 展开卡片内的设置面板内容：标签页 + 控件 */
private void drawExpandedCardContent(float x, float y, float w, float h, float alpha, ModuleItem m) {
    // 暂时只画一个分隔线和占位，Task 7 中填充标签页和控件
    quad.addRect(x + 12f * uiScale, y, w - 24f * uiScale, 1f * uiScale, 0f,
            withAlpha(SOUTH_CARD_BORDER, alpha));
}
```

- [ ] **Step 3: Extract setting accent picker to separate method**

The old `drawModuleCards` contained inline code for the Setting category's color picker (lines ~1459-1571). Extract this into a new method `drawSettingAccentPicker(float contentX, float cy, float contentW, float alpha)`. Copy the code from the original `if (cat.name.equals("Setting"))` block (starting at the SV square) into this new method. This is a code move, not new logic.

Add this method after `drawExpandedCardContent`:

```java
private void drawSettingAccentPicker(float contentX, float cy, float contentW, float alpha) {
    float innerX = contentX + 16f * uiScale;
    float innerW = contentW - 32f * uiScale;

    // SV方块
    float svSize = 80f * uiScale;
    float svX = innerX;
    float svY = cy + 8f * uiScale;
    svBoxX = svX; svBoxY = svY; svBoxW = svSize; svBoxH = svSize;
    float[] hsv = hsv2rgb(customHue, 1f, 1f);
    float[][] svGrad = diagGradient(new float[]{1f,1f,1f,1f}, hsv, alpha);
    quad.addRectGradient(svX, svY, svSize, svSize, 6f * uiScale,
            svGrad[0], svGrad[1], svGrad[2], svGrad[3]);
    quad.addRectGradient(svX, svY, svSize, svSize, 0f,
            withAlpha(new float[]{0f,0f,0f,1f}, 0f * alpha),
            withAlpha(new float[]{0f,0f,0f,1f}, 0f * alpha),
            withAlpha(new float[]{0f,0f,0f,1f}, 1f * alpha),
            withAlpha(new float[]{0f,0f,0f,1f}, 1f * alpha));
    float dotX = svX + customSat * svSize;
    float dotY = svY + (1f - customVal) * svSize;
    quad.addRect(dotX - 4f, dotY - 4f, 8f, 8f, 4f, withAlpha(KNOB_COLOR, alpha));

    // 色相条
    float hueX = svX + svSize + 8f * uiScale;
    float hueW = innerW - svSize - 8f * uiScale;
    float hueY = svY;
    float hueH = 14f * uiScale;
    hueBarX = hueX; hueBarW = hueW; hueBarY = hueY; hueBarH = hueH;
    for (int hi = 0; hi < 6; hi++) {
        float t0 = hi / 6f, t1 = (hi + 1) / 6f;
        float padSeg = 2f * uiScale;
        float segX = hueX + hi * (hueW - padSeg) / 6f;
        float segW = (hueW - padSeg) / 6f;
        float[] colL = hsv2rgb(t0, 1f, 1f);
        float[] colR = hsv2rgb(t1, 1f, 1f);
        quad.addRectGradient(segX, hueY, segW, hueH, 3f * uiScale,
                withAlpha(colL, alpha), withAlpha(colR, alpha),
                withAlpha(colL, alpha), withAlpha(colR, alpha));
    }
    float hueKnobX = hueX + customHue * hueW - 4f;
    quad.addRect(hueKnobX, hueY - 2f, 8f, hueH + 4f, 3f, withAlpha(KNOB_COLOR, alpha));

    // 预设色块
    float swatchSize = (innerW - 3f * 6f * uiScale) / 4f;
    float swatchY = svY + svSize + 8f * uiScale + hueH + 8f * uiScale;
    paletteHitRects.clear();
    for (int pi = 0; pi < presetSwatches.length; pi++) {
        int col = pi % 4;
        int row = pi / 4;
        float sx = innerX + col * (swatchSize + 6f * uiScale);
        float sy = swatchY + row * (swatchSize + 6f * uiScale);
        boolean sel = pi == selectedSwatchIndex;
        quad.addRect(sx, sy, swatchSize, swatchSize, 4f * uiScale,
                withAlpha(presetSwatches[pi], alpha));
        if (sel) {
            quad.addRect(sx - 2f, sy - 2f, swatchSize + 4f, swatchSize + 4f,
                    6f * uiScale, withAlpha(KNOB_COLOR, alpha * 0.9f));
            quad.addRect(sx, sy, swatchSize, swatchSize, 4f * uiScale,
                    withAlpha(presetSwatches[pi], alpha));
        }
        paletteHitRects.add(new float[]{sx, sy, swatchSize, swatchSize});
    }

    // 主题色区域
    float pickerBottom = swatchY + ((presetSwatches.length + 3) / 4) * (swatchSize + 6f * uiScale);
    float themeLabelY = cy + pickerBottom + 12f * uiScale - cy + 8f * uiScale;
    accentLabelY = themeLabelY;

    if (accentThemeExpanded) {
        float btnW = (contentW - 32f * uiScale) * 0.5f - 4f * uiScale;
        float bx = innerX;
        float by = themeLabelY + 30f * uiScale;
        themeThemeRects = new float[THEME_NAMES.length * 4];
        for (int ti = 0; ti < THEME_NAMES.length; ti++) {
            float[][] nodes = THEME_COLORS[ti];
            float[] col0 = nodes[0];
            float[] col1 = nodes.length > 1 ? nodes[1] : nodes[0];
            boolean selected = ti == selectedThemeIndex;
            float rowX = bx + (ti % 2) * (btnW + 8f * uiScale);
            float rowY = by + (ti / 2) * (28f * uiScale + 8f * uiScale);
            quad.addRectGradient(rowX, rowY, btnW, 28f * uiScale, 6f * uiScale,
                    withAlpha(col0, alpha), withAlpha(col1, alpha),
                    withAlpha(col0, alpha), withAlpha(col1, alpha));
            if (selected) {
                quad.addRect(rowX - 2, rowY - 2, btnW + 4, 28f * uiScale + 4, 8f * uiScale,
                        withAlpha(KNOB_COLOR, alpha * 0.9f));
            }
            int base = ti * 4;
            themeThemeRects[base] = rowX;
            themeThemeRects[base+1] = rowY;
            themeThemeRects[base+2] = btnW;
            themeThemeRects[base+3] = 28f * uiScale;
        }
    }
}
```

- [ ] **Step 4: Update drawModuleCardText() for card grid**

Replace the entire `drawModuleCardText` method (lines 1596-1627) with:

```java
private void drawModuleCardText(float x, float y, float w, float h, float alpha) {
    float sbW = sidebarW * (w / panelW);
    float pad = 8f * uiScale * (h / panelH);
    float contentX = x + sbW + pad;
    float contentW = w - sbW - pad * 2;
    float hdrH = 44f * uiScale * (h / panelH);
    float titleH = 34f * uiScale * (h / panelH);
    float startY = y + hdrH + titleH - contentScroll.get();

    Category cat = categories.get(currentCategoryIndex);
    float cardGap = 12f * uiScale * (h / panelH);
    int cols = 2;
    float cardW = (contentW - cardGap * (cols - 1)) / cols;
    float cardH = 70f * uiScale * (h / panelH);

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

        if (curY + cardH < clipY || curY > clipY + clipH) {
            curY += cardH + cardGap;
            if (m.expanded) curY += 120f * uiScale * (h / panelH);
            continue;
        }
        float reveal = rowRevealEase(alpha, i);
        if (reveal <= 0.003f) {
            curY += cardH + cardGap;
            if (m.expanded) curY += 120f * uiScale * (h / panelH);
            continue;
        }
        float rowAlpha = alpha * reveal;
        float drawY = curY + (1f - reveal) * 14f * uiScale;

        // 模块名
        text.draw(m.name, cardX + 12f * uiScale, drawY + 10f * uiScale,
                12f * uiScale, withAlpha(TEXT_MAIN, rowAlpha), screenW, screenH);
        // 描述
        text.draw(m.description, cardX + 12f * uiScale, drawY + 26f * uiScale,
                9f * uiScale, withAlpha(TEXT_DIM, rowAlpha), screenW, screenH);

        // 展开箭头
        text.draw(m.expanded ? "\u25BE" : "\u25B8",
                cardX + cardW - 18f * uiScale, drawY + cardH - 16f * uiScale,
                10f * uiScale, withAlpha(TEXT_DIM, rowAlpha), screenW, screenH);

        // 展开面板内的文字
        float expandT = m.expandAnim.get();
        if (expandT > 0.01f) {
            float expandAlpha = rowAlpha * expandT;
            // 标签页文字
            float tabY = drawY + cardH + 8f * uiScale;
            String[] tabs = {"General", "Behavior"};
            float tabX = cardX + 12f * uiScale;
            for (int ti = 0; ti < tabs.length; ti++) {
                float[] tCol = ti == m.activeTab ? withAlpha(KNOB_COLOR, expandAlpha)
                        : withAlpha(SOUTH_TAB_OFF, expandAlpha);
                text.draw(tabs[ti], tabX, tabY, 11f * uiScale, tCol, screenW, screenH);
                tabX += 70f * uiScale;
            }

            // 设置项文字（仅 General 标签页）
            if (m.activeTab == 0 && m.hasSlider) {
                float setY = tabY + 24f * uiScale;
                text.draw("Value", cardX + 12f * uiScale, setY,
                        11f * uiScale, withAlpha(TEXT_DIM, expandAlpha), screenW, screenH);
                // 数值显示
                String valStr = String.format("%.1f", m.getRealValue()) + (m.sliderUnit.isEmpty() ? "" : " " + m.sliderUnit);
                text.draw(valStr, cardX + cardW - 50f * uiScale, setY,
                        11f * uiScale, withAlpha(TEXT_DIM, expandAlpha), screenW, screenH);
            }

            // 开关标签
            if (m.showToggle) {
                float setY = tabY + 24f * uiScale + (m.hasSlider ? 24f * uiScale : 0f);
                text.draw("Enabled", cardX + 12f * uiScale, setY,
                        11f * uiScale, withAlpha(TEXT_DIM, expandAlpha), screenW, screenH);
            }
        }

        curY += cardH + cardGap;
        if (m.expanded) curY += 120f * uiScale * (h / panelH);
    }

    if (cat.name.equals("Setting")) {
        float cy = curY;
        if (cy + accentCardH(h) >= clipY && cy <= clipY + clipH) {
            text.draw("\u4e3b\u9898\u8272", contentX + 16f * uiScale, cy + 12f * uiScale,
                    15f * uiScale, withAlpha(TEXT_MAIN, alpha), screenW, screenH);
            if (accentThemeExpanded) {
                float btnW = (contentW - 32f * uiScale) * 0.5f - 4f * uiScale;
                float bx = contentX + 16f * uiScale;
                float by = cy + accentCardH(h) - 30f * uiScale;
                for (int ti = 0; ti < THEME_NAMES.length; ti++) {
                    float rowX = bx + (ti % 2) * (btnW + 8f * uiScale);
                    float rowY = by - ((THEME_NAMES.length - 1 - ti) / 2) * (28f * uiScale + 8f * uiScale);
                    if (ti < themeThemeRects != null ? themeThemeRects.length / 4 : 0) {
                        text.draw(THEME_NAMES[ti], rowX + 8f * uiScale, rowY + 6f * uiScale,
                                11f * uiScale, withAlpha(TEXT_MAIN, alpha), screenW, screenH);
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd /workspace/soar_client_extracted && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL (fix any compilation errors that arise from the refactor)

- [ ] **Step 6: Commit**

```bash
cd /workspace/soar_client_extracted
git add app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java
git commit -m "feat: convert module list to 2-column card grid with expand/collapse"
```

---

### Task 7: Add Expanded Card Controls (Toggle Switch, Slider, Tabs)

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java`

- [ ] **Step 1: Implement drawExpandedCardContent() with full controls**

Replace the stub `drawExpandedCardContent` method with:

```java
/** 展开卡片内的设置面板内容：标签页 + 胶囊开关 + 带数值滑块 */
private void drawExpandedCardContent(float x, float y, float w, float h, float alpha, ModuleItem m) {
    float pad = 12f * uiScale;
    float innerX = x + pad;
    float innerW = w - pad * 2;

    // 分隔线
    quad.addRect(innerX, y, innerW, 1f * uiScale, 0f, withAlpha(SOUTH_CARD_BORDER, alpha));

    // ---- 标签页栏 ----
    float tabH = 20f * uiScale;
    float tabY = y + 8f * uiScale;
    float tabGap = 4f * uiScale;
    float tabW = 56f * uiScale;
    String[] tabs = {"General", "Behavior"};
    for (int ti = 0; ti < tabs.length; ti++) {
        float tabX = innerX + ti * (tabW + tabGap);
        if (ti == m.activeTab) {
            quad.addRect(tabX, tabY, tabW, tabH, tabH / 2f, withAlpha(ACCENT_1, alpha));
        }
    }

    // ---- 设置项（仅 General 标签页） ----
    if (m.activeTab == 0) {
        float setY = tabY + tabH + 12f * uiScale;

        // 滑块（如果模块有滑块）
        if (m.hasSlider) {
            // 标签已在 drawModuleCardText 中绘制，这里只画控件
            float sliderTrackW = 100f * uiScale;
            float sliderTrackH = 3f * uiScale;
            float sliderX = innerX + innerW - sliderTrackW - 30f * uiScale;
            float sliderY = setY + 2f * uiScale;
            // 轨道
            quad.addRect(sliderX, sliderY, sliderTrackW, sliderTrackH, sliderTrackH / 2f,
                    withAlpha(SOUTH_SLIDER_TRACK, alpha));
            // 填充
            quad.addRect(sliderX, sliderY, sliderTrackW * m.sliderValue, sliderTrackH,
                    sliderTrackH / 2f, withAlpha(ACCENT_1, alpha));
            // 拖钮
            float knobR = 4f * uiScale;
            float knobX = sliderX + sliderTrackW * m.sliderValue - knobR;
            quad.addRect(knobX, sliderY - knobR + sliderTrackH / 2f, knobR * 2, knobR * 2,
                    knobR, withAlpha(KNOB_COLOR, alpha));
            setY += 24f * uiScale;
        }

        // 胶囊开关（如果模块有开关）
        if (m.showToggle) {
            m.toggleAnim.setTarget(m.enabled ? 1f : 0f);
            m.toggleAnim.update(0.016f);
            float t = m.toggleAnim.get();

            float tglW = 28f * uiScale;
            float tglH = 14f * uiScale;
            float tglX = innerX + innerW - tglW - 12f * uiScale;
            float tglY = setY + 2f * uiScale;
            // 轨道
            float[] trackCol = mixColor(SOUTH_TRACK_OFF, ACCENT_1, t);
            quad.addRect(tglX, tglY, tglW, tglH, tglH / 2f, withAlpha(trackCol, alpha));
            // 滑钮
            float knobD = tglH - 4f * uiScale;
            float knobX = tglX + 2f * uiScale + (tglW - knobD - 4f * uiScale) * t;
            quad.addRect(knobX, tglY + 2f * uiScale, knobD, knobD, knobD / 2f,
                    withAlpha(KNOB_COLOR, alpha));
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /workspace/soar_client_extracted && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd /workspace/soar_client_extracted
git add app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java
git commit -m "feat: add expanded card controls (capsule toggle, value slider, tab pages)"
```

---

### Task 8: Update Touch Handling for Card Grid and New Controls

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java` — method `onTouch()` (line 1629)

- [ ] **Step 1: Add new touch state fields**

After the search state fields added in Task 3, add:

```java
// ---- 卡片网格触摸状态 ----
private boolean draggingCardSlider = false;
private ModuleItem sliderDragModule = null;
private float[] cardSliderRect = null;
```

- [ ] **Step 2: Add touch handling for header bar (search + close)**

In `onTouch()`, in the `case MotionEvent.ACTION_DOWN:` section (around line 1920), **before** the existing "1) 取色器交互" block, add:

```java
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
// 点击搜索框外面时取消搜索激活
if (searchActive && (searchBoxRect == null || !inside(ex, ey, searchBoxRect))) {
    searchActive = false;
}
```

- [ ] **Step 3: Replace module touch handling (toggle + row) with card-based handling**

Find the existing "2) 开关点击" and "3) 整行长按" blocks (lines ~1966-1988) and replace them with:

```java
// 2) 卡片交互：点击卡片主体切换开关，点击箭头展开/收起
for (int idx = 0; idx < moduleHitRects.size(); idx++) {
    Object[] hit = moduleHitRects.get(idx);
    float[] r = (float[]) hit[0];
    String type = (String) hit[1];
    ModuleItem m = (ModuleItem) hit[2];

    if (!inside(ex, ey, r)) continue;

    if ("ARROW".equals(type)) {
        // 点击展开箭头：切换展开状态
        m.expanded = !m.expanded;
        return;
    }
    if ("CARD".equals(type)) {
        // 检查是否点击了展开面板内的控件
        if (m.expanded && m.activeTab == 0) {
            float expandTop = r[1] + 70f * uiScale * (h / panelH); // cardH
            float expandH = 120f * uiScale * (h / panelH);
            // 标签页点击
            float tabY = expandTop + 8f * uiScale;
            float tabW = 56f * uiScale;
            float tabGap = 4f * uiScale;
            for (int ti = 0; ti < 2; ti++) {
                float tabX = r[0] + 12f * uiScale + ti * (tabW + tabGap);
                if (ex >= tabX && ex <= tabX + tabW && ey >= tabY && ey <= tabY + 20f * uiScale) {
                    m.activeTab = ti;
                    return;
                }
            }
            // 滑块拖拽
            if (m.hasSlider) {
                float sliderTrackW = 100f * uiScale;
                float sliderX = r[0] + r[2] - 12f * uiScale - sliderTrackW - 30f * uiScale;
                float sliderY = tabY + 20f * uiScale + 12f * uiScale + 2f * uiScale;
                if (ex >= sliderX - 8f && ex <= sliderX + sliderTrackW + 8f
                        && ey >= sliderY - 8f && ey <= sliderY + 8f) {
                    draggingCardSlider = true;
                    sliderDragModule = m;
                    cardSliderRect = new float[]{sliderX, sliderY, sliderTrackW, 3f * uiScale};
                    float v = (ex - sliderX) / sliderTrackW;
                    m.sliderValue = Math.max(0f, Math.min(1f, v));
                    return;
                }
            }
            // 开关点击
            if (m.showToggle) {
                float tglW = 28f * uiScale;
                float tglH = 14f * uiScale;
                float tglX = r[0] + r[2] - 12f * uiScale - tglW;
                float setY = tabY + 20f * uiScale + 12f * uiScale + (m.hasSlider ? 24f * uiScale : 0f);
                float tglY = setY + 2f * uiScale;
                if (ex >= tglX && ex <= tglX + tglW && ey >= tglY && ey <= tglY + tglH) {
                    m.enabled = !m.enabled;
                    spawnToast(m.name + (m.enabled ? " ON" : " OFF"));
                    return;
                }
            }
        }
        // 点击卡片主体（非展开区域）：切换开关
        if (m.showToggle) {
            m.enabled = !m.enabled;
            spawnToast(m.name + (m.enabled ? " ON" : " OFF"));
        } else if (m.hasSlider) {
            // 纯滑块模块：点击展开
            m.expanded = !m.expanded;
        }
        return;
    }
}
```

Note: `h` in the touch handler refers to `panelH`. Since `onTouch` doesn't receive `h` directly, use `panelH` instead. Replace all `h` references in the touch handler with `panelH`.

- [ ] **Step 4: Add slider drag handling in ACTION_MOVE**

In `case MotionEvent.ACTION_MOVE:` (around line 2002), add before the existing scroll handling:

```java
if (draggingCardSlider && sliderDragModule != null && cardSliderRect != null) {
    float v = (ex - cardSliderRect[0]) / cardSliderRect[2];
    sliderDragModule.sliderValue = Math.max(0f, Math.min(1f, v));
    return;
}
```

- [ ] **Step 5: Reset card slider drag in ACTION_UP and ACTION_CANCEL**

In `case MotionEvent.ACTION_UP:` and `case MotionEvent.ACTION_CANCEL:`, add:

```java
draggingCardSlider = false;
sliderDragModule = null;
cardSliderRect = null;
```

- [ ] **Step 6: Remove long-press expand logic**

The old long-press mechanism (lines 883-887 in onDrawFrame, and the longPressCandidate logic in onTouch) is no longer needed since cards now expand via arrow click. Comment out or remove:

In `onDrawFrame` (around line 883-887), comment out:
```java
// if (longPressCandidate != null && !longPressFired
//         && (System.nanoTime() - longPressStartNs) > LONG_PRESS_NS) {
//     toggleConfigPanelFor(longPressCandidate);
//     longPressFired = true;
// }
```

In `onTouch` ACTION_DOWN, remove the longPressCandidate assignment (the old "3) 整行长按" block was already replaced in Step 3).

- [ ] **Step 7: Verify compilation**

Run: `cd /workspace/soar_client_extracted && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
cd /workspace/soar_client_extracted
git add app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java
git commit -m "feat: update touch handling for card grid, expanded controls, search, close button"
```

---

### Task 9: Add Footer Bar and Final Polish

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java`

- [ ] **Step 1: Add footer bar rendering**

Add a new method after `drawHeaderBar()`:

```java
private float[] footerKeybindRect = null;
private float[] footerCloseRect = null;

private void drawFooterBar(float x, float y, float w, float h, float alpha) {
    float sbW = sidebarW * (w / panelW);
    float footerH = 36f * uiScale * (h / panelH);
    float footerY = y + h - footerH;
    float footerW = w - sbW;

    // 底栏背景
    quad.addRect(x + sbW, footerY, footerW, footerH, 0f, withAlpha(HDR_BG, alpha));
    quad.addRect(x + sbW, footerY + footerH - radius, footerW, radius, 0f, withAlpha(HDR_BG, alpha));

    // Close 按钮（蓝色填充）
    float btnW = 60f * uiScale * (w / panelW);
    float btnH = 28f * uiScale * (h / panelH);
    float btnX = x + w - btnW - 12f * uiScale * (w / panelW);
    float btnY = footerY + (footerH - btnH) / 2f;
    quad.addRect(btnX, btnY, btnW, btnH, 6f * uiScale, withAlpha(ACCENT_1, alpha));
    footerCloseRect = new float[]{btnX, btnY, btnW, btnH};

    // KeyBind 占位区域
    float kbW = 160f * uiScale * (w / panelW);
    float kbH = 28f * uiScale * (h / panelH);
    float kbX = x + sbW + 12f * uiScale * (w / panelW);
    float kbY = footerY + (footerH - kbH) / 2f;
    quad.addRect(kbX, kbY, kbW, kbH, 6f * uiScale, withAlpha(CARD_BG, alpha));
    quad.addRect(kbX, kbY, kbW, kbH, 6f * uiScale, withAlpha(SOUTH_CARD_BORDER, alpha * 0.5f));
    footerKeybindRect = new float[]{kbX, kbY, kbW, kbH};
}
```

- [ ] **Step 2: Call drawFooterBar() from onDrawFrame**

In `onDrawFrame`, after `drawHeaderBar()` call and its flush, add:

```java
drawFooterBar(drawX, drawY, drawW, drawH, panelAlpha);
quad.flush(screenW, screenH);
```

- [ ] **Step 3: Add footer text to drawTextLayer()**

At the end of `drawTextLayer()`, add:

```java
// ---- 底栏文字 ----
float footerH = 36f * uiScale * (h / panelH);
float footerY = y + h - footerH;
if (footerKeybindRect != null) {
    text.draw("Click GUI KeyBind", footerKeybindRect[0] + 10f * uiScale,
            footerKeybindRect[1] + 10f * uiScale, 11f * uiScale,
            withAlpha(TEXT_DIM, alpha), screenW, screenH);
    text.draw("RSHIFT", footerKeybindRect[0] + footerKeybindRect[2] - 50f * uiScale,
            footerKeybindRect[1] + 10f * uiScale, 11f * uiScale,
            withAlpha(TEXT_MAIN, alpha), screenW, screenH);
}
if (footerCloseRect != null) {
    text.draw("Close", footerCloseRect[0] + 18f * uiScale,
            footerCloseRect[1] + 10f * uiScale, 11f * uiScale,
            withAlpha(KNOB_COLOR, alpha), screenW, screenH);
}
```

- [ ] **Step 4: Add footer close button touch handling**

In `onTouch` ACTION_DOWN, after the header close button check, add:

```java
// 底栏 Close 按钮
if (footerCloseRect != null && inside(ex, ey, footerCloseRect)) {
    guiOpen = false;
    overlayOpenness.setTarget(0f);
    openness.setTarget(0f);
    return;
}
```

- [ ] **Step 5: Adjust content clip area to account for footer**

In `drawSidebarAndHeader()`, update the clip height calculation to subtract footer height:

Find the line:
```java
clipH = (y + h - pad) - clipY;
```
Replace with:
```java
float footerH = 36f * uiScale * (h / panelH);
clipH = (y + h - pad - footerH) - clipY;
```

- [ ] **Step 6: Verify compilation and build**

Run: `cd /workspace/soar_client_extracted && ./gradlew assembleDebug 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
cd /workspace/soar_client_extracted
git add app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java
git commit -m "feat: add footer bar with keybind display and close button"
```

---

### Task 10: Final Integration and Cleanup

**Files:**
- Modify: `app/src/main/java/com/visiolab/clickgui/gl/UIRenderer.java`

- [ ] **Step 1: Ensure expanded panel (old long-press overlay) is disabled for normal modules**

The old `drawExpandedPanel()` (line 946) handles the modal overlay for config panels (ArrayList, Watermark, etc.). This should remain functional for those specific modules but should NOT trigger for normal module cards (since they now expand inline).

In `drawExpandedPanel()`, add a guard at the top:

```java
private void drawExpandedPanel(float alpha) {
    // 内联展开已经替代了模块配置弹窗，不再绘制旧的模态弹窗
    // 但保留 ArrayList/水印/通知 的专用配置面板（通过 expandedModule 触发）
    // 如果 expandedModule 是普通模块（没有专用配置面板），直接跳过
    if (expandedModule != null && expandedModule == globalGradientSpeedSlider) {
        return; // 纯滑块模块不再弹出
    }
    // ... 原有代码继续 ...
```

Actually, since we removed the long-press trigger, `expandedModule` will only be set by the old `toggleConfigPanelFor()` calls. Since those are no longer triggered from touch, the overlay panel effectively won't appear for normal modules. No change needed — just verify the build works.

- [ ] **Step 2: Clean up unused variables from old list layout**

Remove or comment out any now-unused fields:
- `longPressCandidate`, `longPressStartNs`, `longPressFired`, `LONG_PRESS_NS` — no longer used
- `overlayJustClosed` — no longer used (was for long-press close)

Add `// @deprecated` comments or simply leave them to avoid breaking other code that might reference them.

- [ ] **Step 3: Full build and verify**

Run: `cd /workspace/soar_client_extracted && ./gradlew assembleDebug 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Final commit**

```bash
cd /workspace/soar_client_extracted
git add -A
git commit -m "feat: complete Southside ClickGUI redesign with card grid, header, footer, and new controls"
```

---

## Self-Review Checklist

After completing all tasks, verify:

- [ ] Default accent color is blue (#3B82F6)
- [ ] Sidebar shows 3 grouped sections (MAIN / FEATURES / SYSTEM) with icons
- [ ] Header bar shows brand name, breadcrumb, search box, close button
- [ ] Module cards are in a 2-column grid
- [ ] Cards show module name, description, blue bottom bar when enabled
- [ ] Clicking arrow expands/collapses card inline
- [ ] Expanded card shows tab pages (General / Behavior)
- [ ] Expanded card shows capsule toggle switch and slider with value
- [ ] Clicking card body toggles module on/off
- [ ] Footer bar shows keybind and close button
- [ ] Theme system still works (8 presets + custom)
- [ ] ArrayList HUD, Watermark, Toast, Floating ball unchanged
- [ ] Login screen unchanged
- [ ] Search box filters modules in current category
