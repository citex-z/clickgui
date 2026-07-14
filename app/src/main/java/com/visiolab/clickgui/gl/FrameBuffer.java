package com.visiolab.clickgui.gl;

import android.opengl.GLES20;

public class FrameBuffer {

    public int fbo, texture;
    public int width, height;

    public void create(int w, int h) {
        release();
        width = w; height = h;

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        texture = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        int[] fb = new int[1];
        GLES20.glGenFramebuffers(1, fb, 0);
        fbo = fb[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public void bindAndSetViewport() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glViewport(0, 0, width, height);
    }

    public void release() {
        if (fbo != 0) {
            int[] fb = {fbo};
            GLES20.glDeleteFramebuffers(1, fb, 0);
            fbo = 0;
        }
        if (texture != 0) {
            int[] tex = {texture};
            GLES20.glDeleteTextures(1, tex, 0);
            texture = 0;
        }
    }

    public static void bindScreen(int screenW, int screenH) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, screenW, screenH);
    }
}
