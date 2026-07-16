package com.visiolab.clickgui.xposed;

import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Rlin Client Xposed 模块入口。
 *
 * 目标：com.mojang.minecraftpe（Minecraft 基岩版）
 * 策略：Hook Minecraft 的 Activity onCreate，注入 WindowManager 悬浮窗覆盖层。
 *
 * 兼容：
 * - LSPosed（需 root）
 * - LSPatch（免 root，通过 patch APK 注入）
 */
public class RlinModule implements IXposedHookLoadPackage {

    private static final String TAG = "RlinModule";
    private static final String TARGET_PACKAGE = "com.mojang.minecraftpe";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        Log.i(TAG, "Rlin Client loaded into " + lpparam.packageName);

        // Minecraft 基岩版主 Activity 类名（不同版本可能不同）
        // 常见：com.mojang.minecraftpe.MainActivity
        String[] activityNames = {
            "com.mojang.minecraftpe.MainActivity",
            "com.mojang.minecraftpe.IntroActivity",
        };

        for (String activityName : activityNames) {
            try {
                Class<?> activityClass = XposedHelpers.findClass(activityName, lpparam.classLoader);
                hookActivityOnCreate(activityClass, lpparam.classLoader);
                Log.i(TAG, "Hooked " + activityName);
                break;
            } catch (Throwable e) {
                Log.w(TAG, "Activity not found: " + activityName + ", trying next");
            }
        }

        // 兜底：hook 所有 Activity 的 onCreate
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity", lpparam.classLoader,
                "onCreate", "android.os.Bundle",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.app.Activity activity = (android.app.Activity) param.thisObject;
                        if (activity.getClass().getName().contains("minecraftpe")
                                || activity.getClass().getName().contains("Minecraft")) {
                            OverlayManager.attach(activity);
                        }
                    }
                }
            );
        } catch (Throwable e) {
            Log.e(TAG, "Failed to hook Activity.onCreate", e);
        }
    }

    private void hookActivityOnCreate(Class<?> activityClass, ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(activityClass, "onCreate",
            android.os.Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    android.app.Activity activity = (android.app.Activity) param.thisObject;
                    Log.i(TAG, "Minecraft Activity onCreate: " + activity.getClass().getName());
                    OverlayManager.attach(activity);
                }
            }
        );
    }
}
