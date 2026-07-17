package com.visiolab.clickgui.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * 音效管理器：使用 SoundPool 播放短音效。
 * 支持 enable/disable 音效开关，以及音量控制。
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
        // 优先使用模块资源加载器（Xposed 注入模式）
        android.content.res.AssetManager am = getModuleAssets();
        try {
            AssetFileDescriptor afd = am.openFd(assetPath);
            int soundId = soundPool.load(afd, 1);
            soundMap.put(key, soundId);
            afd.close();
            Log.i("SoundManager", "Loaded " + assetPath + " -> id=" + soundId);
        } catch (Exception e) {
            Log.e("SoundManager", "Failed to load " + assetPath + ": " + e.getMessage());
        }
    }

    /** 获取模块 AssetManager（Xposed 注入模式下使用 ModuleResourceLoader） */
    private android.content.res.AssetManager getModuleAssets() {
        try {
            Class<?> mrl = Class.forName("com.visiolab.clickgui.xposed.ModuleResourceLoader");
            java.lang.reflect.Method getAssets = mrl.getMethod("getAssets");
            java.lang.reflect.Method isInit = mrl.getMethod("isInitialized");
            if ((boolean) isInit.invoke(null)) {
                android.content.res.AssetManager am = (android.content.res.AssetManager) getAssets.invoke(null);
                if (am != null) return am;
            }
        } catch (Throwable e) { /* 非注入模式 */ }
        return context.getAssets();
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
