package com.visiolab.clickgui.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 音效管理器：使用 SoundPool 播放短音效。
 * 支持 enable/disable 音效开关，以及音量控制。
 *
 * 资源加载策略：
 * 1. 优先用 ModuleResourceLoader.openAsset() 直接读取 InputStream
 *    （SoundPool.load 支持 FileDescriptor，但不直接支持 InputStream，
 *     所以先写到临时文件再用 openFd 加载）
 * 2. 降级：直接用宿主 context.getAssets()
 */
public class SoundManager {
    private static SoundManager instance;
    private SoundPool soundPool;
    private Context context;
    private final Map<String, Integer> soundMap = new HashMap<>();
    private boolean enabled = true;
    private float volume = 0.7f;
    private boolean initialized = false;

    private SoundManager() {}

    public static synchronized SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }

    /** 初始化 SoundPool 并加载音效 */
    public void init(Context ctx) {
        if (initialized) return;
        this.context = ctx.getApplicationContext();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build();
        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) {
                Log.i("SoundManager", "Sound ready: id=" + sampleId);
            } else {
                Log.e("SoundManager", "Sound load failed: id=" + sampleId + " status=" + status);
            }
        });
        // 加载音效
        loadSound("toggle_on", "sfx/celestial_on.wav");
        loadSound("toggle_off", "sfx/celestial_off.wav");
        initialized = true;
        Log.i("SoundManager", "Initialized, sounds loaded: " + soundMap.keySet());
    }

    private void loadSound(String key, String assetPath) {
        // 策略1：通过 ModuleResourceLoader 直接读取 InputStream，写到临时文件后加载
        try {
            InputStream is = loadFromModuleLoader(assetPath);
            if (is != null) {
                File tmpFile = new File(context.getCacheDir(),
                        "sfx_" + key + ".wav");
                FileOutputStream fos = new FileOutputStream(tmpFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                fos.close();
                is.close();

                AssetFileDescriptor afd = context.getResources().getAssets().openNonAssetFd(tmpFile.getAbsolutePath());
                int soundId = soundPool.load(afd, 1);
                soundMap.put(key, soundId);
                afd.close();
                Log.i("SoundManager", "Loaded (module loader) " + assetPath + " -> id=" + soundId);
                return;
            }
        } catch (Exception e) {
            Log.w("SoundManager", "Module loader strategy failed: " + e.getMessage());
        }

        // 策略2：直接用宿主 AssetManager（addAssetPath 注入后可用）
        try {
            AssetFileDescriptor afd = context.getAssets().openFd(assetPath);
            int soundId = soundPool.load(afd, 1);
            soundMap.put(key, soundId);
            afd.close();
            Log.i("SoundManager", "Loaded (host assets) " + assetPath + " -> id=" + soundId);
            return;
        } catch (Exception e) {
            Log.w("SoundManager", "Host assets strategy failed: " + e.getMessage());
        }

        // 策略3：用模块 AssetManager 的 openFd
        try {
            AssetManager moduleAm = getModuleAssetManager();
            if (moduleAm != null) {
                AssetFileDescriptor afd = moduleAm.openFd(assetPath);
                int soundId = soundPool.load(afd, 1);
                soundMap.put(key, soundId);
                afd.close();
                Log.i("SoundManager", "Loaded (module AM) " + assetPath + " -> id=" + soundId);
                return;
            }
        } catch (Exception e) {
            Log.w("SoundManager", "Module AM strategy failed: " + e.getMessage());
        }

        Log.e("SoundManager", "All strategies failed for " + assetPath);
    }

    /** 通过反射调用 ModuleResourceLoader.openAsset() 读取 InputStream */
    private InputStream loadFromModuleLoader(String assetPath) {
        try {
            Class<?> mrl = Class.forName("com.visiolab.clickgui.xposed.ModuleResourceLoader");
            java.lang.reflect.Method isInit = mrl.getMethod("isInitialized");
            if ((boolean) isInit.invoke(null)) {
                java.lang.reflect.Method openAsset = mrl.getMethod("openAsset", String.class);
                return (InputStream) openAsset.invoke(null, assetPath);
            }
        } catch (Throwable e) {
            Log.w("SoundManager", "ModuleResourceLoader not available: " + e.getMessage());
        }
        return null;
    }

    /** 获取模块 AssetManager */
    private AssetManager getModuleAssetManager() {
        try {
            Class<?> mrl = Class.forName("com.visiolab.clickgui.xposed.ModuleResourceLoader");
            java.lang.reflect.Method getAssets = mrl.getMethod("getAssets");
            java.lang.reflect.Method isInit = mrl.getMethod("isInitialized");
            if ((boolean) isInit.invoke(null)) {
                return (AssetManager) getAssets.invoke(null);
            }
        } catch (Throwable e) { /* 非注入模式 */ }
        return null;
    }

    /** 播放开启音效 */
    public void playToggleOn() {
        play("toggle_on");
    }

    /** 播放关闭音效 */
    public void playToggleOff() {
        play("toggle_off");
    }

    /** 根据开关状态播放对应音效 */
    public void playToggle(boolean enabled) {
        if (enabled) {
            playToggleOn();
        } else {
            playToggleOff();
        }
    }

    private void play(String key) {
        if (!initialized || !enabled) return;
        Integer soundId = soundMap.get(key);
        if (soundId == null) return;
        soundPool.play(soundId, volume, volume, 1, 0, 1f);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        initialized = false;
    }
}
