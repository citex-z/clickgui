package com.visiolab.nativebridge;

import com.visiolab.clickgui.gl.DynamicIslandRenderer;

/**
 * 游戏回调接口：供 native 层（游戏引擎侧）调用，将游戏实时数据推送到 UI。
 *
 * 这是一个 JNI 友好的静态方法接口，native 层可以通过 JNI 调用这些方法
 * 来实时更新灵动岛进度条、HUD 数据等。
 *
 * 使用方式（native 侧 C++ 示例）：
 *   JNIEnv* env;
 *   jclass cls = env->FindClass("com/visiolab/nativebridge/GameCallback");
 *   jmethodID mid = env->GetStaticMethodID(cls, "updateScaffoldProgress", "(F)V");
 *   env->CallStaticVoidMethod(cls, mid, 0.65f);
 */
public class GameCallback {

    private static DynamicIslandRenderer dynamicIsland;

    /** 绑定灵动岛实例（在 UIRenderer 初始化时调用） */
    public static void setDynamicIsland(DynamicIslandRenderer island) {
        dynamicIsland = island;
    }

    // ========== 搭路进度 ==========

    /**
     * 更新搭路进度条（0.0 ~ 1.0）。
     * native 侧在每帧或每次放置方块后调用。
     *
     * @param progress 当前进度 0.0~1.0
     */
    public static void updateScaffoldProgress(float progress) {
        if (dynamicIsland != null) {
            dynamicIsland.addProgress("Scaffold", "Placing blocks...", progress);
        }
    }

    /**
     * 更新搭路进度条（带自定义描述文字）。
     *
     * @param progress 当前进度 0.0~1.0
     * @param description 描述文字，如 "55 blocks left - 5.54 block/s"
     */
    public static void updateScaffoldProgress(float progress, String description) {
        if (dynamicIsland != null) {
            dynamicIsland.addProgress("Scaffold", description, progress);
        }
    }

    /** 搭路完成，移除进度条 */
    public static void finishScaffold() {
        if (dynamicIsland != null) {
            dynamicIsland.removeProgress("Scaffold");
        }
    }

    // ========== 通用进度条 ==========

    /**
     * 更新/创建一个自定义进度条。
     *
     * @param key 进度条唯一标识（同 key 会更新而非新建）
     * @param title 显示标题
     * @param description 显示描述
     * @param progress 进度 0.0~1.0
     */
    public static void updateProgress(String key, String title, String description, float progress) {
        if (dynamicIsland != null) {
            dynamicIsland.addProgress(title, description, progress);
        }
    }

    /** 移除一个进度条 */
    public static void removeProgress(String key) {
        if (dynamicIsland != null) {
            dynamicIsland.removeProgress(key);
        }
    }

    // ========== 灵动岛通知 ==========

    /** 显示成功提示 */
    public static void showSuccess(String title, String description) {
        if (dynamicIsland != null) {
            dynamicIsland.addSuccess(title, description);
        }
    }

    /** 显示警告提示 */
    public static void showWarning(String title, String description) {
        if (dynamicIsland != null) {
            dynamicIsland.addWarning(title, description);
        }
    }

    /** 显示信息提示 */
    public static void showInfo(String title, String description) {
        if (dynamicIsland != null) {
            dynamicIsland.addInfo(title, description);
        }
    }

    // ========== 游戏数据 ==========

    /**
     * 更新 FPS（native 侧每秒调用一次）。
     * 如果 native 侧有更准确的 FPS 来源，可以覆盖 UI 侧的模拟值。
     */
    public static void updateFps(int fps) {
        if (dynamicIsland != null) {
            dynamicIsland.setFps(fps);
        }
    }

    /**
     * 更新 Ping（native 侧在网络延迟变化时调用）。
     */
    public static void updatePing(int ping) {
        if (dynamicIsland != null) {
            dynamicIsland.setPing(ping);
        }
    }
}
