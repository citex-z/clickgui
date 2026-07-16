package com.visiolab.nativebridge;

/**
 * JNI 桥接层：MC PE 端模块注入的 native 接口预留。
 *
 * 后期将通过 JNI 加载 .so 库，实现以下功能：
 * - 读取/修改 Minecraft PE 游戏内存中的玩家坐标、速度等
 * - Hook 渲染管线实现 ESP、Tracers 等
 * - Hook 输入系统实现 KillAura、AutoClicker 等
 * - 读取游戏世界数据实现 Radar、Scaffold 等
 *
 * 使用方式：
 * 1. 在模块的 onEnable/onDisable 中调用对应的 native 方法
 * 2. native 方法在 C++ 层注入游戏函数
 * 3. 配置面板的 Slider/Switch 通过 JNI 回调到 native 层
 */
public class NativeBridge {

    static {
        // 预留：后续加载 native 库
        // System.loadLibrary("rlin_native");
    }

    private static boolean nativeLoaded = false;

    /** 检查 native 库是否已加载 */
    public static boolean isNativeLoaded() {
        return nativeLoaded;
    }

    /** 初始化 native 层（游戏注入完成后调用） */
    public static native void nativeInit();

    // ========== Combat 模块 ==========

    /** KillAura：自动攻击附近实体 */
    public static native void setKillAuraEnabled(boolean enabled, float range, float speed);

    /** AutoClicker：自动连点 */
    public static native void setAutoClickerEnabled(boolean enabled, int cps, boolean onlyLeftClick);

    /** Criticals：每次攻击触发暴击 */
    public static native void setCriticalsEnabled(boolean enabled);

    /** TriggerBot：准星对准实体时自动攻击 */
    public static native void setTriggerBotEnabled(boolean enabled, float range);

    // ========== Movement 模块 ==========

    /** Fly：飞行 */
    public static native void setFlyEnabled(boolean enabled, float speed);

    /** Speed：移动速度提升 */
    public static native void setSpeedEnabled(boolean enabled, float speed);

    /** NoFall：无掉落伤害 */
    public static native void setNoFallEnabled(boolean enabled);

    /** AutoJump：自动跳跃 */
    public static native void setAutoJumpEnabled(boolean enabled);

    /** SprintToggle：自动冲刺 */
    public static native void setSprintToggleEnabled(boolean enabled);

    /** AirJump：空中跳跃 */
    public static native void setAirJumpEnabled(boolean enabled);

    /** Phase：穿墙 */
    public static native void setPhaseEnabled(boolean enabled);

    // ========== Render 模块 ==========

    /** ESP：实体透视 */
    public static native void setEspEnabled(boolean enabled, int mode, float[] color);

    /** Tracers：连线追踪 */
    public static native void setTracersEnabled(boolean enabled, float[] color);

    /** FullBright：全亮 */
    public static native void setFullBrightEnabled(boolean enabled, float level);

    /** NameTags：放大名牌 */
    public static native void setNameTagsEnabled(boolean enabled, float scale);

    /** Chams：透视实体模型 */
    public static native void setChamsEnabled(boolean enabled, float[] color);

    /** XRay：透视矿物 */
    public static native void setXrayEnabled(boolean enabled, int[] blockIds);

    /** Trajectories：投掷物轨迹预测 */
    public static native void setTrajectoriesEnabled(boolean enabled);

    // ========== Player 模块 ==========

    /** FastEat：快速进食 */
    public static native void setFastEatEnabled(boolean enabled, float speed);

    /** AutoRespawn：自动重生 */
    public static native void setAutoRespawnEnabled(boolean enabled);

    /** Reach：增大交互距离 */
    public static native void setReachEnabled(boolean enabled, float range);

    /** Freecam：自由视角 */
    public static native void setFreecamEnabled(boolean enabled);

    /** NoClip：穿墙移动 */
    public static native void setNoClipEnabled(boolean enabled);

    // ========== World 模块 ==========

    /** Nuker：范围破坏方块 */
    public static native void setNukerEnabled(boolean enabled, float range);

    /** AutoMine：自动挖矿 */
    public static native void setAutoMineEnabled(boolean enabled);

    /** Scaffold：自动搭桥 */
    public static native void setScaffoldEnabled(boolean enabled, float range);

    /** FastPlace：快速放置 */
    public static native void setFastPlaceEnabled(boolean enabled, float speed);

    /** Timer：游戏速度修改 */
    public static native void setTimerEnabled(boolean enabled, float speed);

    // ========== HUD / 信息获取 ==========

    /** 获取当前 FPS */
    public static native int getFps();

    /** 获取当前 Ping */
    public static native int getPing();

    /** 获取当前 CPS */
    public static native int getCps();

    /** 获取玩家坐标 */
    public static native float[] getPlayerPos();

    /** 获取玩家速度 */
    public static native float[] getPlayerMotion();

    /** 获取玩家血量 */
    public static native float getHealth();

    /** 获取玩家饥饿值 */
    public static native float getHunger();

    /** 获取当前生物群系 */
    public static native String getBiome();

    /** 获取服务器 TPS */
    public static native float getTps();

    /** 获取附近实体列表 */
    public static native Object[] getNearbyEntities(float range);

    // ========== 辅助功能 ==========

    /** 发送聊天消息 */
    public static native void sendChatMessage(String msg);

    /** 发送命令 */
    public static native void sendCommand(String cmd);

    /** 模拟点击 */
    public static native void simulateClick(boolean leftClick);

    /** 设置玩家速度 */
    public static native void setPlayerMotion(float x, float y, float z);

    /** 设置玩家位置（传送） */
    public static native void setPlayerPos(float x, float y, float z);

    /** 设置游戏速度 */
    public static native void setGameSpeed(float speed);

    /** 设置亮度等级 */
    public static native void setBrightness(float level);

    /** 设置交互距离 */
    public static native void setReach(float range);

    /**
     * 模块状态变更通知：当用户在 ClickGUI 中切换模块时调用
     * @param moduleName 模块名称
     * @param enabled 是否启用
     * @param params 配置参数（键值对）
     */
    public static native void onModuleStateChanged(String moduleName, boolean enabled, String[] params);

    /**
     * 配置项变更通知：当用户在 ClickGUI 中修改配置时调用
     * @param moduleName 模块名称
     * @param configName 配置项名称
     * @param value 配置值
     */
    public static native void onConfigChanged(String moduleName, String configName, String value);

    // ========== 游戏数据回调（native → Java） ==========
    // 以下方法由 native 层主动调用，将游戏实时数据推送到 UI
    // native 侧通过 JNI 调用 GameCallback 的静态方法即可

    /**
     * 请求 native 侧开始推送搭路进度。
     * 搭路开始时调用，native 侧之后应定期调用 GameCallback.updateScaffoldProgress()
     */
    public static native void requestScaffoldProgressUpdates();

    /**
     * 请求 native 侧停止推送搭路进度。
     * 搭路结束时调用。
     */
    public static native void stopScaffoldProgressUpdates();
}
