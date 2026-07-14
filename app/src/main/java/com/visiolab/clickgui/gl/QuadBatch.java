package com.visiolab.clickgui.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 统一的圆角矩形/圆形批量渲染器。
 * 用 SDF (signed distance field) 在片元着色器里画圆角矩形，
 * radius == min(halfW,halfH) 时退化成圆形，radius == 0 时是直角矩形。
 * 这样面板背景、模块卡片、分类高亮、开关滑块、圆形拖钮都用同一套 draw call。
 */
public class QuadBatch {

    private static final int FLOATS_PER_VERTEX = 11; // pos(2) local(2) halfSize(2) radius(1) color(4)
    private static final int MAX_QUADS = 2048;

    private final float[] cpu = new float[MAX_QUADS * 6 * FLOATS_PER_VERTEX];
    private int vertexCount = 0;

    private int program;
    private int aPos, aLocal, aHalfSize, aRadius, aColor;
    private int uScreenSize;

    private FloatBuffer buffer;

    private static final String VERT =
            "precision mediump float;\n" +
            "uniform vec2 uScreenSize;\n" +
            "attribute vec2 aPos;\n" +
            "attribute vec2 aLocal;\n" +
            "attribute vec2 aHalfSize;\n" +
            "attribute float aRadius;\n" +
            "attribute vec4 aColor;\n" +
            "varying vec2 vLocal;\n" +
            "varying vec2 vHalfSize;\n" +
            "varying float vRadius;\n" +
            "varying vec4 vColor;\n" +
            "void main(){\n" +
            "  vec2 ndc = vec2((aPos.x / uScreenSize.x) * 2.0 - 1.0, 1.0 - (aPos.y / uScreenSize.y) * 2.0);\n" +
            "  gl_Position = vec4(ndc, 0.0, 1.0);\n" +
            "  vLocal = aLocal; vHalfSize = aHalfSize; vRadius = aRadius; vColor = aColor;\n" +
            "}";

    private static final String FRAG =
            "precision mediump float;\n" +
            "varying vec2 vLocal;\n" +
            "varying vec2 vHalfSize;\n" +
            "varying float vRadius;\n" +
            "varying vec4 vColor;\n" +
            "float sdRoundBox(vec2 p, vec2 b, float r){\n" +
            "  vec2 q = abs(p) - b + r;\n" +
            "  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "void main(){\n" +
            "  vec2 p = vLocal * vHalfSize;\n" +
            "  float d = sdRoundBox(p, vHalfSize, vRadius);\n" +
            "  float alpha = 1.0 - smoothstep(-1.5, 1.5, d);\n" +
            "  gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);\n" +
            "}";

    public void init() {
        program = GLUtil.buildProgram(VERT, FRAG);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aLocal = GLES20.glGetAttribLocation(program, "aLocal");
        aHalfSize = GLES20.glGetAttribLocation(program, "aHalfSize");
        aRadius = GLES20.glGetAttribLocation(program, "aRadius");
        aColor = GLES20.glGetAttribLocation(program, "aColor");
        uScreenSize = GLES20.glGetUniformLocation(program, "uScreenSize");

        ByteBuffer bb = ByteBuffer.allocateDirect(cpu.length * 4);
        bb.order(ByteOrder.nativeOrder());
        buffer = bb.asFloatBuffer();
    }

    /** 纯色圆角矩形 / 圆形(radius=min(w,h)/2) / 直角矩形(radius=0) */
    public void addRect(float x, float y, float w, float h, float radius, float[] rgba) {
        addRectGradient(x, y, w, h, radius, rgba, rgba, rgba, rgba);
    }

    /** 四角可分别指定颜色，用来做类似 AccentColor 双色渐变的效果 */
    public void addRectGradient(float x, float y, float w, float h, float radius,
                                 float[] colTL, float[] colTR, float[] colBL, float[] colBR) {
        float hw = w / 2f, hh = h / 2f;
        float cx = x + hw, cy = y + hh;

        // 四个角的屏幕坐标 + local(-1..1)
        float[][] verts = {
                {cx - hw, cy - hh, -1, -1}, // TL
                {cx + hw, cy - hh, 1, -1},  // TR
                {cx - hw, cy + hh, -1, 1},  // BL
                {cx + hw, cy - hh, 1, -1},  // TR
                {cx + hw, cy + hh, 1, 1},   // BR
                {cx - hw, cy + hh, -1, 1},  // BL
        };
        float[][] cols = {colTL, colTR, colBL, colTR, colBR, colBL};

        for (int i = 0; i < 6; i++) {
            pushVertex(verts[i][0], verts[i][1], verts[i][2], verts[i][3], hw, hh, radius, cols[i]);
        }
    }

    private void pushVertex(float px, float py, float lx, float ly, float hw, float hh, float radius, float[] c) {
        if (vertexCount >= MAX_QUADS * 6) return;
        int o = vertexCount * FLOATS_PER_VERTEX;
        cpu[o] = px; cpu[o + 1] = py;
        cpu[o + 2] = lx; cpu[o + 3] = ly;
        cpu[o + 4] = hw; cpu[o + 5] = hh;
        cpu[o + 6] = radius;
        cpu[o + 7] = c[0]; cpu[o + 8] = c[1]; cpu[o + 9] = c[2]; cpu[o + 10] = c[3];
        vertexCount++;
    }

    public void flush(int screenW, int screenH) {
        if (vertexCount == 0) return;

        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uScreenSize, screenW, screenH);

        buffer.clear();
        buffer.put(cpu, 0, vertexCount * FLOATS_PER_VERTEX);
        buffer.position(0);

        int stride = FLOATS_PER_VERTEX * 4;
        buffer.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, stride, buffer);

        buffer.position(2);
        GLES20.glEnableVertexAttribArray(aLocal);
        GLES20.glVertexAttribPointer(aLocal, 2, GLES20.GL_FLOAT, false, stride, buffer);

        buffer.position(4);
        GLES20.glEnableVertexAttribArray(aHalfSize);
        GLES20.glVertexAttribPointer(aHalfSize, 2, GLES20.GL_FLOAT, false, stride, buffer);

        buffer.position(6);
        GLES20.glEnableVertexAttribArray(aRadius);
        GLES20.glVertexAttribPointer(aRadius, 1, GLES20.GL_FLOAT, false, stride, buffer);

        buffer.position(7);
        GLES20.glEnableVertexAttribArray(aColor);
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, buffer);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aLocal);
        GLES20.glDisableVertexAttribArray(aHalfSize);
        GLES20.glDisableVertexAttribArray(aRadius);
        GLES20.glDisableVertexAttribArray(aColor);

        vertexCount = 0;
    }
}
