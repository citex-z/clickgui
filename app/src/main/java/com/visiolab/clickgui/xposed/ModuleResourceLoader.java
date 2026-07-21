package com.visiolab.clickgui.xposed;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

/**
 * 模块资源加载器。
 *
 * 在 Xposed/LSPatch 注入环境下，宿主 Context.getAssets() 返回的是 Minecraft 的 assets，
 * 不是我们模块的。这个类通过模块自己的 APK 路径创建独立的 AssetManager。
 *
 * 新增：将模块 APK 路径注入宿主 AssetManager（addAssetPath），
 * 这样 SoundManager 等通过 context.getAssets() 也能访问模块资源。
 *

 *
 * APK 路径查找策略（按优先级）：
 * 1. ClassLoader 来源 — 从模块类的 ProtectionDomain 获取 APK 路径（最可靠）
 * 2. PackageManager 查找 — 通过模块包名查找（Local 模式下模块已安装）
 * 3. LSPatch 嵌入路径 — 从宿主 assets/lspatch/modules/ 提取（Integrated 模式）
 * 4. DexClassLoader 反射 — 从 ClassLoader 的 pathList.dexElements 获取
 * 5. ApplicationInfo.sourceDir — 从宿主 ApplicationInfo 获取（LSPatch 模式）
 */
public class ModuleResourceLoader {

    private static final String TAG = "ModuleResourceLoader";
    private static AssetManager moduleAssets = null;
    private static Resources moduleResources = null;
    private static String moduleApkPath = null;
    private static boolean injectedToHost = false;
    private static final String MODULE_PACKAGE = "com.visiolab.clickgui";

    public static synchronized void init(Context hostContext) {
        if (moduleAssets != null) return;

        Log.i(TAG, "Initializing module resource loader...");

        // 策略1：从 ClassLoader 获取 APK 路径（最可靠）
        String apkPath = getApkPathFromClassLoader();
        if (apkPath != null) {
            Log.i(TAG, "Strategy 1 (ClassLoader): " + apkPath);
            if (tryLoad(apkPath, hostContext)) return;
        }

        // 策略2：PackageManager 查找（Local 模式）
        apkPath = getApkPathFromPackageManager(hostContext);
        if (apkPath != null) {
            Log.i(TAG, "Strategy 2 (PackageManager): " + apkPath);
            if (tryLoad(apkPath, hostContext)) return;
        }

        // 策略3：LSPatch 嵌入路径（Integrated 模式）
        apkPath = getApkPathFromLSPatch(hostContext);
        if (apkPath != null) {
            Log.i(TAG, "Strategy 3 (LSPatch embedded): " + apkPath);
            if (tryLoad(apkPath, hostContext)) return;
        }

        // 策略4：从 DexClassLoader 反射获取
        apkPath = getApkPathFromDexElements();
        if (apkPath != null) {
            Log.i(TAG, "Strategy 4 (DexElements): " + apkPath);
            if (tryLoad(apkPath, hostContext)) return;
        }

        // 策略5：从宿主 ApplicationInfo 获取（LSPatch 模式下宿主 APK 包含模块）
        apkPath = getApkPathFromApplicationInfo(hostContext);
        if (apkPath != null) {
            Log.i(TAG, "Strategy 5 (ApplicationInfo): " + apkPath);
            if (tryLoad(apkPath, hostContext)) return;
        }

        Log.e(TAG, "All strategies failed! Falling back to host assets.");
        moduleAssets = hostContext.getAssets();
    }

    /**
     * 将模块 APK 路径注入宿主的 AssetManager。
     * 注入后，宿主 context.getAssets() 也能访问模块的 assets（字体、音频等）。
     *
    
     */
    public static boolean injectIntoHost(Context hostContext) {
        if (injectedToHost) return true;
        if (moduleApkPath == null) {
            // 先尝试初始化
            init(hostContext);
            if (moduleApkPath == null) {
                Log.w(TAG, "Cannot inject: module APK path not found");
                return false;
            }
        }

        try {
            AssetManager hostAm = hostContext.getResources().getAssets();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = (int) addAssetPath.invoke(hostAm, moduleApkPath);
            if (cookie > 0) {
                injectedToHost = true;
                Log.i(TAG, "Module resources injected into host AssetManager (cookie=" + cookie + ")");
                return true;
            } else {
                Log.w(TAG, "addAssetPath returned 0 for host injection");
                return false;
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to inject into host: " + e.getMessage());
            return false;
        }
    }

    /** 检查是否已注入宿主 */
    public static boolean isInjectedToHost() { return injectedToHost; }

    /** 策略1：从模块类的 ProtectionDomain 获取 APK 路径 */
    private static String getApkPathFromClassLoader() {
        try {
            Class<?> clazz = null;
            try {
                clazz = Class.forName("com.visiolab.clickgui.xposed.RlinModule");
            } catch (Throwable e) {
                clazz = ModuleResourceLoader.class;
            }
            java.security.ProtectionDomain pd = clazz.getProtectionDomain();
            if (pd != null && pd.getCodeSource() != null) {
                String path = pd.getCodeSource().getLocation().getPath();
                if (path != null && path.endsWith(".apk")) {
                    path = java.net.URLDecoder.decode(path, "UTF-8");
                    if (new File(path).exists()) return path;
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "ClassLoader strategy failed: " + e.getMessage());
        }
        return null;
    }

    /** 策略2：通过 PackageManager 查找模块 APK */
    private static String getApkPathFromPackageManager(Context hostContext) {
        try {
            PackageManager pm = hostContext.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(MODULE_PACKAGE, 0);
            String path = appInfo.sourceDir;
            if (path != null && new File(path).exists()) return path;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Module package not found via PackageManager");
        } catch (Throwable e) {
            Log.w(TAG, "PackageManager strategy failed: " + e.getMessage());
        }
        return null;
    }

    /** 策略3：从 LSPatch 嵌入路径提取 */
    private static String getApkPathFromLSPatch(Context hostContext) {
        String[] paths = {
            "lspatch/modules/" + MODULE_PACKAGE + ".apk",
            "lspatch/assets/modules/" + MODULE_PACKAGE + ".apk",
            "assets/lspatch/modules/" + MODULE_PACKAGE + ".apk",
        };
        for (String path : paths) {
            try {
                InputStream is = hostContext.getAssets().open(path);
                is.close();
                // 提取到临时文件
                File tmpFile = new File(hostContext.getCacheDir(), "rlin_module.apk");
                is = hostContext.getAssets().open(path);
                FileOutputStream fos = new FileOutputStream(tmpFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                fos.close();
                is.close();
                Log.i(TAG, "Extracted LSPatch module APK to: " + tmpFile.getAbsolutePath());
                return tmpFile.getAbsolutePath();
            } catch (Exception e) {
                // 继续尝试
            }
        }
        return null;
    }

    /** 策略4：从 DexClassLoader 的 pathList.dexElements 反射获取 */
    private static String getApkPathFromDexElements() {
        try {
            ClassLoader cl = ModuleResourceLoader.class.getClassLoader();
            Class<?> baseDexClass = Class.forName("dalvik.system.BaseDexClassLoader");
            java.lang.reflect.Field pathListField = baseDexClass.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(cl);
            if (pathList == null) return null;

            java.lang.reflect.Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] dexElements = (Object[]) dexElementsField.get(pathList);
            if (dexElements == null || dexElements.length == 0) return null;

            // 查找包含我们模块的 dex element
            for (Object element : dexElements) {
                try {
                    java.lang.reflect.Field pathField = element.getClass().getDeclaredField("path");
                    pathField.setAccessible(true);
                    Object pathObj = pathField.get(element);
                    if (pathObj != null) {
                        String path = pathObj.toString();
                        if (path.endsWith(".apk") && new File(path).exists()) {
                            if (path.contains("rlin") || path.contains("visiolab") || path.contains("clickgui")) {
                                return path;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 如果没找到匹配的，返回第一个 APK
            for (Object element : dexElements) {
                try {
                    java.lang.reflect.Field pathField = element.getClass().getDeclaredField("path");
                    pathField.setAccessible(true);
                    Object pathObj = pathField.get(element);
                    if (pathObj != null) {
                        String path = pathObj.toString();
                        if (path.endsWith(".apk") && new File(path).exists()) {
                            return path;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable e) {
            Log.w(TAG, "DexElements strategy failed: " + e.getMessage());
        }
        return null;
    }

    /** 策略5：从宿主 ApplicationInfo 获取（LSPatch 模式下宿主 APK 包含模块） */
    private static String getApkPathFromApplicationInfo(Context hostContext) {
        try {
            ApplicationInfo info = hostContext.getApplicationInfo();
            String path = info.sourceDir;
            if (path != null && new File(path).exists()) {
                // 在 LSPatch 模式下，模块类被合并到宿主 APK 中
                // 所以宿主 APK 本身就包含模块资源
                return path;
            }
        } catch (Throwable e) {
            Log.w(TAG, "ApplicationInfo strategy failed: " + e.getMessage());
        }
        return null;
    }

    /** 尝试用指定 APK 路径创建 AssetManager + 注入宿主 */
    private static boolean tryLoad(String apkPath, Context hostContext) {
        try {
            AssetManager am = createAssetManager(apkPath);
            if (am == null) return false;

            // 验证：尝试打开一个已知资源
            try {
                InputStream test = am.open("fonts/Comfortaa-Regular.ttf");
                test.close();
                Log.i(TAG, "Resource verification OK: fonts/Comfortaa-Regular.ttf");
            } catch (Exception e) {
                Log.w(TAG, "Resource verification failed for " + apkPath + ", trying next strategy");
                return false;
            }

            // 创建模块 Resources
            Resources hostRes = hostContext.getResources();
            moduleResources = new Resources(am, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
            moduleAssets = am;
            moduleApkPath = apkPath;

            // 关键：将模块 APK 路径注入宿主 AssetManager
            // 这样 SoundManager 等通过 context.getAssets() 也能访问模块资源
            injectIntoHost(hostContext);

            Log.i(TAG, "Module resources loaded successfully from: " + apkPath);
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "tryLoad failed for " + apkPath + ": " + e.getMessage());
            return false;
        }
    }

    /** 创建独立的 AssetManager 并添加 APK 路径 */
    private static AssetManager createAssetManager(String apkPath) {
        try {
            // Android 9+ 用无参构造
            AssetManager am = AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            int cookie = (int) addAssetPath.invoke(am, apkPath);
            Log.i(TAG, "addAssetPath cookie: " + cookie + " for " + apkPath);
            if (cookie == 0) {
                Log.w(TAG, "addAssetPath returned 0");
                return null;
            }
            return am;
        } catch (Throwable e1) {
            // 旧 API 用 newInstance()
            try {
                AssetManager am = AssetManager.class.newInstance();
                Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                addAssetPath.setAccessible(true);
                addAssetPath.invoke(am, apkPath);
                return am;
            } catch (Throwable e2) {
                Log.e(TAG, "Failed to create AssetManager: " + e2.getMessage());
                return null;
            }
        }
    }

    public static AssetManager getAssets() { return moduleAssets; }
    public static Resources getResources() { return moduleResources; }
    public static boolean isInitialized() { return moduleAssets != null; }

    public static InputStream openAsset(String path) throws java.io.IOException {
        if (moduleAssets != null) return moduleAssets.open(path);
        throw new java.io.IOException("ModuleResourceLoader not initialized");
    }
}
