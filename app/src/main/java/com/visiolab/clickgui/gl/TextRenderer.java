package com.visiolab.clickgui.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 基于 GlyphAtlas 的文字渲染：每个字符从图集里取UV，一整段文字拼成一次 draw call。
 * 对外接口(draw/measure)保持不变，方便未来直接替换成更完整的实现。
 */
public class TextRenderer {

    private final GlyphAtlas atlas = new GlyphAtlas();

    private int program;
    private int aPos, aUV, uTex, uColor, uScreenSize;
    private FloatBuffer buffer;
    private static final int MAX_CHARS = 512;

    private static final String VERT =
            "precision highp float;\n" +
            "uniform vec2 uScreenSize;\n" +
            "attribute vec2 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec2 ndc = vec2((aPos.x/uScreenSize.x)*2.0-1.0, 1.0-(aPos.y/uScreenSize.y)*2.0);\n" +
            "  gl_Position = vec4(ndc,0.0,1.0); vUV = aUV;\n" +
            "}";

    private static final String FRAG =
            "precision highp float;\n" +
            "varying vec2 vUV;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uColor;\n" +
            "void main(){\n" +
            "  float a = texture2D(uTex, vUV).a;\n" +
            "  gl_FragColor = vec4(uColor.rgb, uColor.a * a);\n" +
            "}";

    public void init() {
        atlas.init();
        program = GLUtil.buildProgram(VERT, FRAG);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aUV = GLES20.glGetAttribLocation(program, "aUV");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
        uColor = GLES20.glGetUniformLocation(program, "uColor");
        uScreenSize = GLES20.glGetUniformLocation(program, "uScreenSize");

        ByteBuffer bb = ByteBuffer.allocateDirect(MAX_CHARS * 6 * 4 * 4);
        bb.order(ByteOrder.nativeOrder());
        buffer = bb.asFloatBuffer();
    }

    /** 在(x,y)左上角画一行文字（不支持换行，需要多行请多次调用），返回绘制总宽度 */
    public float draw(String text, float x, float y, float sizePx, float[] rgba, int screenW, int screenH) {
        if (text == null || text.isEmpty()) return 0;

        buffer.clear();
        float penX = x;
        int charCount = 0;

        for (int i = 0; i < text.length() && charCount < MAX_CHARS; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                penX += sizePx * 0.28f;
                continue;
            }
            GlyphAtlas.Glyph g = atlas.ensureGlyph(c, sizePx);

            float gx = penX;
            // 文字垂直居中修正：glyph bitmap 包含 ascent 上方的空白
            // bearingY = -fm.top（即从 bitmap 顶部到基线的距离）
            // 要让文字视觉居中于 y 坐标，需要向上偏移 bearingY - height/2
            float gy = y + (g.height - g.bearingY) - g.height; // = y - bearingY + height - height = y - bearingY
            // 简化：gy = y 表示 bitmap 左上角在 y 处
            // 但 bitmap 有顶部空白，所以实际字符顶部在 y + (height - bearingY)
            // 要让字符顶部在 y 处：gy = y - (height - bearingY)
            gy = y - (g.height - g.bearingY);

            float[] verts = {
                    gx, gy, g.u0, g.v0,
                    gx + g.width, gy, g.u1, g.v0,
                    gx, gy + g.height, g.u0, g.v1,
                    gx + g.width, gy, g.u1, g.v0,
                    gx + g.width, gy + g.height, g.u1, g.v1,
                    gx, gy + g.height, g.u0, g.v1,
            };
            buffer.put(verts);
            penX += g.advance;
            charCount++;
        }

        if (charCount == 0) return penX - x;

        buffer.position(0);

        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uScreenSize, screenW, screenH);
        GLES20.glUniform4f(uColor, rgba[0], rgba[1], rgba[2], rgba[3]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlas.getTexture());
        GLES20.glUniform1i(uTex, 0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        int stride = 16;
        buffer.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, stride, buffer);
        buffer.position(2);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, stride, buffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, charCount * 6);

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);

        return penX - x;
    }

    public float measure(String text, float sizePx) {
        return atlas.measure(text, sizePx);
    }
}
