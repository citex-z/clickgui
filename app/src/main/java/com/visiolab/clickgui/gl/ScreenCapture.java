package com.visiolab.clickgui.gl;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PixelCopy;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用 PixelCopy API 截取窗口画面（包含 Minecraft 的渲染内容），
 * 转换为 GL 纹理供 BlurPipeline 做高斯模糊。
 *
 * 工作流程：
 * 1. requestCapture() 在 UI 线程调用 PixelCopy.request(Window, ...)
 * 2. 截图完成后，存储 Bitmap
 * 3. 下一帧 GL 线程调用 uploadToGl() 将 Bitmap 上传为 GL 纹理
 * 4. BlurPipeline 对该纹理做高斯模糊
 *
 * 注意：
 * - PixelCopy 是异步的，截图有 1-2 帧延迟
 * - 只在面板开始打开时截一次，之后持续使用该截图做模糊
 * - 截图在面板完全打开后保持，直到面板关闭时清除
 */
public class ScreenCapture {

    private static final String TAG = "ScreenCapture";

    private Activity activity;
    private Bitmap pendingBitmap = null;    // PixelCopy 回调写入
    private int bgTexture = 0;               // GL 纹理
    private int texWidth = 0, texHeight = 0;
    private final AtomicBoolean captureRequested = new AtomicBoolean(false);
    private final AtomicBoolean hasTexture = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    /**
     * 请求截取窗口画面。
     * 在 UI 线程调用，异步完成。
     */
    public void requestCapture() {
        if (activity == null) {
            Log.w(TAG, "Activity not set");
            return;
        }
        if (!captureRequested.compareAndSet(false, true)) return; // 防止重复请求

        mainHandler.post(() -> {
            if (activity == null || activity.isFinishing()) {
                captureRequested.set(false);
                return;
            }
            try {
                doPixelCopy();
            } catch (Throwable e) {
                Log.e(TAG, "PixelCopy failed", e);
                captureRequested.set(false);
            }
        });
    }

    private void doPixelCopy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(TAG, "PixelCopy requires API 24+");
            captureRequested.set(false);
            return;
        }

        int w = activity.getWindow().getDecorView().getWidth();
        int h = activity.getWindow().getDecorView().getHeight();
        if (w <= 0 || h <= 0) {
            captureRequested.set(false);
            return;
        }

        // 降采样以减少内存和上传开销（1/2 分辨率足够模糊用）
        int capW = w / 2;
        int capH = h / 2;
        Bitmap bmp = Bitmap.createBitmap(capW, capH, Bitmap.Config.ARGB_8888);

        try {
            PixelCopy.request(activity.getWindow(), bmp, copyResult -> {
                if (copyResult == PixelCopy.SUCCESS) {
                    Log.i(TAG, "Screen captured: " + capW + "x" + capH);
                    synchronized (this) {
                        pendingBitmap = bmp;
                    }
                } else {
                    Log.w(TAG, "PixelCopy failed: " + copyResult);
                }
                captureRequested.set(false);
            }, mainHandler);
        } catch (Throwable e) {
            Log.e(TAG, "PixelCopy request error", e);
            bmp.recycle();
            captureRequested.set(false);
        }
    }

    /**
     * 在 GL 线程调用：将待处理的 Bitmap 上传为 GL 纹理。
     * @return true 表示有新纹理可用
     */
    public boolean uploadToGl() {
        Bitmap bmp;
        synchronized (this) {
            bmp = pendingBitmap;
            pendingBitmap = null;
        }
        if (bmp == null) return false;

        if (bgTexture == 0) {
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            bgTexture = ids[0];
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTexture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);

        texWidth = bmp.getWidth();
        texHeight = bmp.getHeight();
        bmp.recycle();
        hasTexture.set(true);
        Log.i(TAG, "Texture uploaded: " + texWidth + "x" + texHeight);
        return true;
    }

    /** 获取截屏纹理 ID */
    public int getTexture() { return bgTexture; }
    public int getTexWidth() { return texWidth; }
    public int getTexHeight() { return texHeight; }
    public boolean hasTexture() { return hasTexture.get(); }

    /** 清除纹理（面板关闭时调用） */
    public void clear() {
        if (bgTexture != 0) {
            int[] ids = { bgTexture };
            GLES20.glDeleteTextures(1, ids, 0);
            bgTexture = 0;
        }
        hasTexture.set(false);
        synchronized (this) {
            if (pendingBitmap != null) {
                pendingBitmap.recycle();
                pendingBitmap = null;
            }
        }
    }
}
