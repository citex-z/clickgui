package com.visiolab.clickgui.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 背景图片渲染器：将 assets/bg.png 加载为 OpenGL 纹理，并全屏渲染。
 * 支持 GL_REPEAT 平铺和 GL_CLAMP_TO_EDGE 拉伸两种模式，
 * 配合毛玻璃模糊管线、scissor镂空、透明背景渲染等场景。
 */
public class BackgroundRenderer {

    private int textureId = 0;
    private int program;
    private int aPos, aUV, uTex, uScreenSize, uAlpha, uRepeat;
    private boolean loaded = false;
    private float texScaleU = 1f, texScaleV = 1f;
    private int imgW = 1, imgH = 1;

    private static final String VERT =
            "precision mediump float;\n" +
            "uniform vec2 uScreenSize;\n" +
            "attribute vec2 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec2 ndc = vec2((aPos.x/uScreenSize.x)*2.0-1.0, 1.0-(aPos.y/uScreenSize.y)*2.0);\n" +
            "  gl_Position = vec4(ndc,0.0,1.0);\n" +
            "  vUV = aUV;\n" +
            "}";

    private static final String FRAG =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform float uAlpha;\n" +
            "uniform float uRepeat;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec2 uv = uRepeat > 0.5 ? fract(vUV) : clamp(vUV, 0.0, 1.0);\n" +
            "  gl_FragColor = texture2D(uTex, uv) * uAlpha;\n" +
            "}";

    private FloatBuffer quadBuffer;

    /**
     * 从 assets 加载图片纹理
     */
    public void load(Context ctx, String assetPath) {
        if (textureId != 0) return; // 只加载一次

        try {
            InputStream is = ctx.getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            if (bitmap == null) {
                android.util.Log.e("Background", "Failed to decode bitmap from " + assetPath);
                return;
            }

            imgW = bitmap.getWidth();
            imgH = bitmap.getHeight();

            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            textureId = tex[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();

            loaded = true;

            // 计算纹理缩放比例（保持宽高比覆盖全屏）
            android.util.Log.i("Background", "Loaded bg.png: " + imgW + "x" + imgH);
        } catch (Exception e) {
            android.util.Log.e("Background", "Error loading background texture", e);
        }
    }

    public void init() {
        program = GLUtil.buildProgram(VERT, FRAG);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aUV = GLES20.glGetAttribLocation(program, "aUV");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
        uScreenSize = GLES20.glGetUniformLocation(program, "uScreenSize");
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha");
        uRepeat = GLES20.glGetUniformLocation(program, "uRepeat");

        ByteBuffer bb = ByteBuffer.allocateDirect(6 * 4 * 4);
        bb.order(ByteOrder.nativeOrder());
        quadBuffer = bb.asFloatBuffer();

        loaded = false;
        textureId = 0;
    }

    /**
     * 全屏绘制背景图片，使用 GL_CLAMP_TO_EDGE 拉伸适配屏幕
     */
    public void draw(int screenW, int screenH, float alpha) {
        if (!loaded || textureId == 0) return;
        if (program == 0) return;

        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uScreenSize, screenW, screenH);
        GLES20.glUniform1f(uAlpha, alpha);
        GLES20.glUniform1f(uRepeat, 0f); // clamp模式，拉伸铺满

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(uTex, 0);

        // 全屏四边形，UV从0到1
        float[] verts = {
                0,    0,    0, 0,
                screenW, 0,    1, 0,
                0,    screenH, 0, 1,
                screenW, 0,    1, 0,
                screenW, screenH, 1, 1,
                0,    screenH, 0, 1
        };
        quadBuffer.clear();
        quadBuffer.put(verts);
        quadBuffer.position(0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        int stride = 4 * 4; // 4 floats * 4 bytes
        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, stride, quadBuffer);

        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, stride, quadBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
    }

    /**
     * 快速绘制背景（alpha=1.0）
     */
    public void draw(int screenW, int screenH) {
        draw(screenW, screenH, 1f);
    }

    /**
     * 使用GL_REPEAT平铺模式绘制（用于某些特殊渲染场景）
     */
    public void drawRepeated(int screenW, int screenH, float repeatU, float repeatV, float alpha) {
        if (!loaded || textureId == 0) return;
        if (program == 0) return;

        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uScreenSize, screenW, screenH);
        GLES20.glUniform1f(uAlpha, alpha);
        GLES20.glUniform1f(uRepeat, 1f); // repeat模式

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(uTex, 0);

        float[] verts = {
                0,    0,    0, 0,
                screenW, 0,    repeatU, 0,
                0,    screenH, 0, repeatV,
                screenW, 0,    repeatU, 0,
                screenW, screenH, repeatU, repeatV,
                0,    screenH, 0, repeatV
        };
        quadBuffer.clear();
        quadBuffer.put(verts);
        quadBuffer.position(0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        int stride = 4 * 4;
        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, stride, quadBuffer);

        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, stride, quadBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
    }

    /**
     * 判断纹理是否已成功加载
     */
    public boolean isLoaded() {
        return loaded && textureId != 0;
    }

    /**
     * 获取原始图片宽度
     */
    public int getImageWidth() {
        return imgW;
    }

    /**
     * 获取原始图片高度
     */
    public int getImageHeight() {
        return imgH;
    }

    /**
     * 释放纹理资源
     */
    public void release() {
        if (textureId != 0) {
            int[] tex = {textureId};
            GLES20.glDeleteTextures(1, tex, 0);
            textureId = 0;
        }
        loaded = false;
    }
}
