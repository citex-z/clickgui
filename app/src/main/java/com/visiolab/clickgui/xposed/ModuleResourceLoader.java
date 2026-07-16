package com.visiolab.clickgui.xposed;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.InputStream;

/**
 * 模块资源加载器。
 *
 * 在 Xposed 注入环境下，宿主 Context.getAssets() 返回的是 Minecraft 的 assets，
 * 不是我们模块的。这个类通过模块自己的 APK 路径创建独立的 AssetManager，
 * 从而正确加载模块的字体、图片等资源。
 */
public class ModuleResourceLoader {

    private static final String TAG = "ModuleResourceLoader";
    private static AssetManager moduleAssets = null;
    private static Resources moduleResources = null;
    private static final String MODULE_PACKAGE = "com.visiolab.clickgui";

    /**
     * 初始化模块资源。
     * 在 OverlayManager.attach 中调用，使用宿主 Context 查找模块 APK 路径。
     */
    public static synchronized void init(Context hostContext) {
        if (moduleAssets != null) return;

        try {
            // 通过 PackageManager 查找模块 APK 路径
            PackageManager pm = hostContext.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(MODULE_PACKAGE, 0);
            String apkPath = appInfo.sourceDir;
            Log.i(TAG, "Module APK path: " + apkPath);

            // 创建独立的 AssetManager
            moduleAssets = AssetManager.class.newInstance();
            // 反射调用 addAssetPath
            java.lang.reflect.Method addAssetPath = AssetManager.class
                .getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int result = (int) addAssetPath.invoke(moduleAssets, apkPath);
            Log.i(TAG, "addAssetPath result: " + result);

            // 创建 Resources
            Resources hostRes = hostContext.getResources();
            moduleResources = new Resources(
                moduleAssets,
                hostRes.getDisplayMetrics(),
                hostRes.getConfiguration()
            );

            Log.i(TAG, "Module resources initialized successfully");

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Module package not found: " + MODULE_PACKAGE, e);
            // LSPatch Integrated 模式下，模块 APK 嵌入在宿主内
            // 尝试从宿主的 assets/lspatch/modules/ 加载
            initFromLSPatch(hostContext);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to init module resources", e);
            initFromLSPatch(hostContext);
        }
    }

    /**
     * LSPatch Integrated 模式下的备用加载方式。
     * 模块 APK 嵌入在宿主的 assets/lspatch/modules/ 目录中。
     */
    private static void initFromLSPatch(Context hostContext) {
        try {
            // 尝试从 LSPatch 嵌入路径加载
            AssetManager hostAssets = hostContext.getAssets();
            String[] paths = {
                "lspatch/modules/" + MODULE_PACKAGE + ".apk",
                "lspatch/assets/modules/" + MODULE_PACKAGE + ".apk",
            };

            for (String path : paths) {
                try {
                    InputStream is = hostAssets.open(path);
                    is.close();
                    Log.i(TAG, "Found module APK at: " + path);

                    // 提取到临时文件
                    java.io.File tmpFile = new java.io.File(
                        hostContext.getCacheDir(), "rlin_module.apk");
                    is = hostAssets.open(path);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                    fos.close();
                    is.close();

                    // 用临时文件创建 AssetManager
                    moduleAssets = AssetManager.class.newInstance();
                    java.lang.reflect.Method addAssetPath = AssetManager.class
                        .getDeclaredMethod("addAssetPath", String.class);
                    addAssetPath.setAccessible(true);
                    addAssetPath.invoke(moduleAssets, tmpFile.getAbsolutePath());

                    Resources hostRes = hostContext.getResources();
                    moduleResources = new Resources(
                        moduleAssets,
                        hostRes.getDisplayMetrics(),
                        hostRes.getConfiguration()
                    );

                    Log.i(TAG, "Module resources loaded from LSPatch: " + path);
                    return;
                } catch (Exception e) {
                    // 继续尝试下一个路径
                }
            }

            // 最后兜底：直接用宿主 assets 尝试加载（如果模块资源被合并）
            Log.w(TAG, "Falling back to host assets");
            moduleAssets = hostAssets;

        } catch (Throwable e) {
            Log.e(TAG, "LSPatch fallback also failed", e);
        }
    }

    /**
     * 获取模块的 AssetManager。
     * 如果初始化失败，返回 null（调用方应处理）。
     */
    public static AssetManager getAssets() {
        return moduleAssets;
    }

    /**
     * 获取模块的 Resources。
     */
    public static Resources getResources() {
        return moduleResources;
    }

    /**
     * 是否已初始化。
     */
    public static boolean isInitialized() {
        return moduleAssets != null;
    }

    /**
     * 打开模块 assets 中的文件。
     */
    public static InputStream openAsset(String path) throws java.io.IOException {
        if (moduleAssets != null) {
            return moduleAssets.open(path);
        }
        throw new java.io.IOException("ModuleResourceLoader not initialized");
    }
}
