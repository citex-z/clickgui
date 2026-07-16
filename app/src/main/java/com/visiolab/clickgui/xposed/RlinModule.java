package com.visiolab.clickgui.xposed;

import android.app.Activity;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Rlin Client Xposed 模块入口。
 *
 * 目标：com.mojang.minecraftpe（Minecraft 基岩版）
 *
 * 注入策略：
 * 1. Hook MainActivity.onCreate → 获取 Activity 上下文
 * 2. 用 Activity.addContentView() 添加 GLSurfaceView 到 Activity 的 DecorView
 * 3. setZOrderOnTop(true) 确保 GL 层在 Minecraft SurfaceView 之上
 * 4. 面板关闭时触摸穿透（FLAG_NOT_TOUCHABLE）
 * 5. 面板打开时捕获触摸
 *
 * 注意事项：
 * - Minecraft 使用 RenderDragon 引擎，原生 Surface 独立于 Android View 系统
 * - 不能用 WindowManager + TYPE_APPLICATION_OVERLAY（LSPatch 下无 SYSTEM_ALERT_WINDOW 权限）
 * - addContentView 是应用内操作，不需要额外权限
 * - MainActivity 类名：com.mojang.minecraftpe.MainActivity
 *   有静态字段 currentMainActivity 可获取 Activity 实例
 */
public class RlinModule implements IXposedHookLoadPackage {

    private static final String TAG = "RlinModule";
    private static final String TARGET_PACKAGE = "com.mojang.minecraftpe";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        Log.i(TAG, "=== Rlin Client loading into " + lpparam.packageName + " ===");

        // Minecraft 基岩版主 Activity
        String mainActivityName = "com.mojang.minecraftpe.MainActivity";

        try {
            Class<?> activityClass = XposedHelpers.findClass(mainActivityName, lpparam.classLoader);
            Log.i(TAG, "Found " + mainActivityName);

            // Hook onCreate
            XposedHelpers.findAndHookMethod(activityClass, "onCreate",
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        Log.i(TAG, "MainActivity.onCreate called, injecting overlay...");

                        // 在 GL thread 中注入（延迟到 Activity 完全创建后）
                        activity.runOnUiThread(() -> {
                            try {
                                OverlayManager.attach(activity);
                            } catch (Throwable e) {
                                Log.e(TAG, "Failed to attach overlay", e);
                            }
                        });
                    }
                }
            );

            // 也 Hook onResume，确保 Activity 恢复时 overlay 还在
            XposedHelpers.findAndHookMethod(activityClass, "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        if (!OverlayManager.isAttached()) {
                            Log.i(TAG, "onResume: re-attaching overlay");
                            activity.runOnUiThread(() -> {
                                try {
                                    OverlayManager.attach(activity);
                                } catch (Throwable e) {
                                    Log.e(TAG, "Failed to re-attach overlay", e);
                                }
                            });
                        }
                    }
                }
            );

            Log.i(TAG, "Hooks installed successfully");

        } catch (Throwable e) {
            Log.e(TAG, "Failed to hook MainActivity, trying fallback...", e);

            // 兜底：hook 通用 Activity.onCreate
            XposedHelpers.findAndHookMethod(
                "android.app.Activity", lpparam.classLoader,
                "onCreate", "android.os.Bundle",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        String className = activity.getClass().getName();
                        if (className.contains("minecraftpe") || className.contains("Minecraft")) {
                            Log.i(TAG, "Fallback hook: " + className);
                            activity.runOnUiThread(() -> {
                                try {
                                    OverlayManager.attach(activity);
                                } catch (Throwable e) {
                                    Log.e(TAG, "Fallback attach failed", e);
                                }
                            });
                        }
                    }
                }
            );
        }
    }
}
