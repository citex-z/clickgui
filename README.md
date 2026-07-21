# Soar Client (Rlin Client)

一个基于 Xposed 框架的 Minecraft Bedrock Edition 客户端，提供 ClickGUI、HUD 渲染、模块管理等功能。

## 功能特性

### 核心功能
- **ClickGUI 面板** — 模块分类浏览、搜索、配置项编辑（开关/滑块/下拉框）
- **HUD 系统** — ArrayList、Keystrokes、Toast 通知、Dynamic Island 灵动岛
- **HUD 编辑器** — 拖拽调整 HUD 元素位置，位置持久化保存
- **悬浮球** — 快捷打开 ClickGUI
- **悬浮快捷键** — 自定义模块快捷开关，支持拖拽定位

### HUD 元素
- **Dynamic Island（灵动岛）** — 模块开关动画、进度条、成功/警告/信息提示、目标玩家信息、会话计时
- **ArrayList** — 模块列表，渐变色主题
- **Keystrokes** — WASD + 鼠标按键显示
- **Potion HUD** — 药水效果显示
- **Target HUD** — 目标玩家信息（两版本：动态岛弹出 / 固定面板）
- **Toast 通知** — 模块切换提示，右下角动画

### 渲染引擎
- **OpenGL ES 2.0** — 自研渲染管线
- **高斯模糊** — 毛玻璃面板效果
- **Bloom 辉光** — 灵动岛边缘发光
- **字体渲染** — 自定义 GlyphAtlas 字体纹理图集
- **批量绘制** — QuadBatch 矩形批绘优化

## 技术栈

| 技术 | 用途 |
|------|------|
| Xposed Framework | Hook Minecraft Bedrock 的 Activity 生命周期，注入渲染层 |
| OpenGL ES 2.0 | HUD 渲染（Shader、FrameBuffer、模糊、辉光） |
| Android WindowManager | 多窗口分层架构（HUD / 悬浮球 / 快捷键 / 面板） |
| JNI | Java ↔ C 双向通信（数据拉取 + 回调推送） |
| SharedPreferences | 配置持久化、HUD 位置保存、快捷键布局 |

## 架构设计

```
┌─────────────────────────────────────────────┐
│              Minecraft Bedrock              │
│           (com.mojang.minecraftpe)          │
├─────────────────────────────────────────────┤
│            Xposed Hook 层                   │
│         RlinModule.java (Hook Activity)     │
├─────────────────────────────────────────────┤
│           OverlayManager (窗口管理)          │
│  ┌─────────┬──────────┬──────────┬────────┐ │
│  │HUD GL层 │ 悬浮球层  │ 快捷键层  │ 面板层 │ │
│  │(穿透)   │(区域拦截) │(区域拦截) │(全屏)  │ │
│  └─────────┴──────────┴──────────┴────────┘ │
├─────────────────────────────────────────────┤
│            GL 渲染管线                       │
│  HudRenderer → DynamicIslandRenderer         │
│  TextRenderer / BlurPipeline / QuadBatch    │
├─────────────────────────────────────────────┤
│          Native Bridge (JNI)                │
│  NativeBridge (Java→C) / GameCallback (C→Java)│
└─────────────────────────────────────────────┘
```

### 窗口分层

| 层 | 触摸行为 | 说明 |
|----|---------|------|
| HUD GL 层 | 完全穿透 | `FLAG_NOT_TOUCHABLE`，只画不拦截 |
| 悬浮球 | 区域拦截 | `FLAG_NOT_TOUCH_MODAL`，仅球区域拦截 |
| 快捷键 | 区域拦截 | 每个快捷键独立窗口 |
| ClickGUI 面板 | 全屏拦截 | 打开时接管触摸，关闭时释放 |
| HUD 编辑层 | 全屏拦截 | 编辑模式时激活，退出时移除 |

## 项目结构

```
app/src/main/java/com/visiolab/
├── clickgui/
│   ├── gl/              # OpenGL 渲染
│   │   ├── HudRenderer.java          # HUD 主渲染器
│   │   ├── DynamicIslandRenderer.java # 灵动岛
│   │   ├── UIRenderer.java           # GL 版 ClickGUI
│   │   ├── BlurPipeline.java         # 高斯模糊
│   │   ├── TextRenderer.java         # 字体渲染
│   │   ├── QuadBatch.java            # 矩形批绘
│   │   ├── GlyphAtlas.java           # 字体纹理图集
│   │   ├── FrameBuffer.java          # FBO 离屏渲染
│   │   ├── ScreenCapture.java       # 屏幕截取
│   │   └── GLUtil.java               # GL 工具
│   ├── ui/              # Android View UI
│   │   ├── ClickGuiPanel.java        # ClickGUI 面板
│   │   ├── FloatingBallView.java     # 悬浮球
│   │   ├── FloatingHotkeyView.java   # 悬浮快捷键
│   │   ├── HudEditOverlayView.java   # HUD 编辑拦截层
│   │   └── widget/                   # 自定义控件
│   ├── xposed/          # Xposed 注入
│   │   ├── RlinModule.java          # Xposed 入口
│   │   ├── OverlayManager.java      # 窗口管理
│   │   └── ModuleResourceLoader.java # 资源加载
│   ├── model/           # 数据模型
│   │   ├── Category.java             # 分类定义
│   │   ├── ModuleItem.java           # 模块项
│   │   └── ModCategory.java          # 模块分类
│   ├── hud/             # HUD 功能
│   │   └── HudPositionManager.java  # 位置持久化
│   ├── hotkey/          # 快捷键
│   │   ├── HotkeyItem.java           # 快捷键项
│   │   └── HotkeyManager.java        # 快捷键管理
│   ├── anim/            # 动画
│   │   └── Anim.java                 # 动画工具
│   └── audio/           # 音效
│       └── SoundManager.java         # 音效管理
├── nativebridge/        # JNI 桥接
│   ├── NativeBridge.java             # Java→C 调用
│   ├── GameCallback.java            # C→Java 回调
│   └── ModuleHook.java              # 模块钩子
└── MainActivity.java    # 配置入口
```

## 开发环境

| 项目 | 值 |
|------|------|
| 语言 | Java 8 |
| 构建 | Gradle 7.5 |
| minSdk | 24 (Android 7.0) |
| targetSdk | 33 (Android 13) |
| 目标应用 | Minecraft Bedrock Edition |

## 使用方式

### 前置条件
1. 已 Root 的 Android 设备
2. 已安装 LSPosed（或 EdXposed）框架
3. Minecraft Bedrock Edition（基岩版）

### 安装
1. 编译生成 APK
2. 在 LSPosed 中启用本模块
3. 作用域勾选 Minecraft
4. 重启 Minecraft

### 操作
- **打开 ClickGUI**：点击屏幕悬浮球
- **搜索模块**：面板顶部搜索栏
- **编辑 HUD 位置**：面板侧栏 → 编辑HUD → 拖拽元素
- **快捷键**：长按悬浮快捷键拖拽，短按切换

## 模块列表

### Client（客户端）
ArrayList、ClickGUI、Notifications、Keystrokes、DynamicIsland、PotionHUD、TargetHUD、HUD Editor

### Combat（战斗）
KillAura、Velocity、Criticals、Reach、AutoClicker

### Movement（移动）
Fly、Speed、Scaffold、NoFall、HighJump

### Render（渲染）
Fullbright、ESP、Tracers、NameTags

### Player（玩家）
AutoEat、FastUse、Timer

---

> 本项目仅供学习交流使用。
