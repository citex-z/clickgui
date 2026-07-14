package com.visiolab.clickgui.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 场景在低分辨率(1/4)下渲染 -> 横向9-tap高斯 -> 纵向9-tap高斯 -> 得到模糊纹理。
 * 再用 glassPanel shader 把这张模糊纹理按圆角矩形SDF裁剪、叠一层半透明色调，
 * 做出真正的毛玻璃(frosted glass)面板背景，替代上一版的分层阴影近似。
 */
public class BlurPipeline {

    private final FrameBuffer pingA = new FrameBuffer();
    private final FrameBuffer pingB = new FrameBuffer();
    private int downsample = 4; // 数值越大越模糊（改为4，减小模糊纹理与全屏的映射偏差）

    private int blurProgram;
    private int bAPos, bAUV, bUTex, bUDir, bUTexel;

    private int glassProgram;
    private int gAPos, gUTex, gUScreenSize, gURectPos, gURectHalf, gURadius, gUTint;

    private FloatBuffer fullscreenQuad;
    private FloatBuffer panelQuad;

    private static final String BLUR_VERT =
            "precision mediump float;\n" +
            "attribute vec2 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main(){ gl_Position = vec4(aPos,0.0,1.0); vUV = aUV; }";

    private static final String BLUR_FRAG =
            "precision mediump float;\n" +
            "varying vec2 vUV;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec2 uDir;\n" +
            "uniform vec2 uTexel;\n" +
            "void main(){\n" +
            "  vec4 sum = texture2D(uTex, vUV) * 0.227027;\n" +
            "  vec2 off1 = uDir * uTexel * 1.3846153846;\n" +
            "  vec2 off2 = uDir * uTexel * 3.2307692308;\n" +
            "  sum += texture2D(uTex, vUV + off1) * 0.3162162162;\n" +
            "  sum += texture2D(uTex, vUV - off1) * 0.3162162162;\n" +
            "  sum += texture2D(uTex, vUV + off2) * 0.0702702703;\n" +
            "  sum += texture2D(uTex, vUV - off2) * 0.0702702703;\n" +
            "  gl_FragColor = sum;\n" +
            "}";

    // 圆角矩形SDF + 采样模糊背景 + 色调叠加，做出玻璃质感面板
    private static final String GLASS_VERT =
            "precision mediump float;\n" +
            "uniform vec2 uScreenSize;\n" +
            "attribute vec2 aPos;\n" +
            "varying vec2 vScreenPos;\n" +
            "void main(){\n" +
            "  vec2 ndc = vec2((aPos.x/uScreenSize.x)*2.0-1.0, 1.0-(aPos.y/uScreenSize.y)*2.0);\n" +
            "  gl_Position = vec4(ndc,0.0,1.0); vScreenPos = aPos;\n" +
            "}";

    private static final String GLASS_FRAG =
            "precision mediump float;\n" +
            "varying vec2 vScreenPos;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec2 uScreenSize;\n" +
            "uniform vec2 uRectPos;\n" +   // 面板中心
            "uniform vec2 uRectHalf;\n" +  // 面板半宽高
            "uniform float uRadius;\n" +
            "uniform vec4 uTint;\n" +
            "uniform float uOverallAlpha;\n" + // 面板开关淡入淡出的整体透明度，跟色调混合比例分开控制
            "float sdRoundBox(vec2 p, vec2 b, float r){\n" +
            "  vec2 q = abs(p) - b + r;\n" +
            "  return length(max(q,0.0)) + min(max(q.x,q.y),0.0) - r;\n" +
            "}\n" +
            "void main(){\n" +
            "  vec2 local = vScreenPos - uRectPos;\n" +
            "  float d = sdRoundBox(local, uRectHalf, uRadius);\n" +
            "  float shapeAlpha = 1.0 - smoothstep(-1.5, 1.5, d);\n" +
            // 关键修复：FBO渲染出的纹理，V=0对应视觉上的“底部”而不是“顶部”（OpenGL纹理坐标系约定），
            // 之前直接用 vScreenPos/uScreenSize 取样导致模糊后的背景整体上下颠倒/错位，这里翻转回来。
            "  vec2 uv = vec2(vScreenPos.x / uScreenSize.x, 1.0 - vScreenPos.y / uScreenSize.y);\n" +
            "  vec3 bg = texture2D(uTex, uv).rgb;\n" +
            "  vec3 col = mix(bg, uTint.rgb, uTint.a);\n" +
            "  gl_FragColor = vec4(col, shapeAlpha * uOverallAlpha);\n" +
            "}";

    // 合成模糊后的"发光"阴影：直接用纹理自身的alpha做柔和衰减，而不是外部SDF裁剪出来的硬边
    private static final String GLOW_FRAG =
            "precision mediump float;\n" +
            "varying vec2 vScreenPos;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec2 uScreenSize;\n" +
            "uniform vec4 uColor;\n" +
            "void main(){\n" +
            "  vec2 uv = vec2(vScreenPos.x / uScreenSize.x, 1.0 - vScreenPos.y / uScreenSize.y);\n" +
            "  float a = texture2D(uTex, uv).a;\n" +
            "  gl_FragColor = vec4(uColor.rgb, uColor.a * a);\n" +
            "}";

    // 真正的"多彩辉光"合成：直接输出模糊纹理自身的颜色(已经是premultiplied alpha)，
    // 不再套用外部统一色调——之前 drawGlowColor 图省事复用了 GLOW_FRAG，
    // 那个shader只会输出外部传入的uColor纯色，完全无视纹理本身的颜色，
    // 而且我把"强度"当成uColor传了进去(1.8会被钳到1.0，也就是纯白)，
    // 叠加混合再糊满全屏，这就是"开辉光背景变白"的真正原因。
    private static final String GLOW_COLOR_FRAG =
            "precision mediump float;\n" +
            "varying vec2 vScreenPos;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec2 uScreenSize;\n" +
            "uniform float uIntensity;\n" +
            "void main(){\n" +
            "  vec2 uv = vec2(vScreenPos.x / uScreenSize.x, 1.0 - vScreenPos.y / uScreenSize.y);\n" +
            "  vec4 t = texture2D(uTex, uv);\n" +
            "  gl_FragColor = vec4(t.rgb * uIntensity, t.a * uIntensity);\n" +
            "}";

    private int glowColorProgram;
    private int gcAPos, gcUTex, gcUScreenSize, gcUIntensity;

    private int glowProgram;
    private int wAPos, wUTex, wUScreenSize, wUColor;
    private int gUOverallAlpha;

    public void init() {
        blurProgram = GLUtil.buildProgram(BLUR_VERT, BLUR_FRAG);
        bAPos = GLES20.glGetAttribLocation(blurProgram, "aPos");
        bAUV = GLES20.glGetAttribLocation(blurProgram, "aUV");
        bUTex = GLES20.glGetUniformLocation(blurProgram, "uTex");
        bUDir = GLES20.glGetUniformLocation(blurProgram, "uDir");
        bUTexel = GLES20.glGetUniformLocation(blurProgram, "uTexel");

        glassProgram = GLUtil.buildProgram(GLASS_VERT, GLASS_FRAG);
        gAPos = GLES20.glGetAttribLocation(glassProgram, "aPos");
        gUTex = GLES20.glGetUniformLocation(glassProgram, "uTex");
        gUScreenSize = GLES20.glGetUniformLocation(glassProgram, "uScreenSize");
        gURectPos = GLES20.glGetUniformLocation(glassProgram, "uRectPos");
        gURectHalf = GLES20.glGetUniformLocation(glassProgram, "uRectHalf");
        gURadius = GLES20.glGetUniformLocation(glassProgram, "uRadius");
        gUTint = GLES20.glGetUniformLocation(glassProgram, "uTint");
        gUOverallAlpha = GLES20.glGetUniformLocation(glassProgram, "uOverallAlpha");

        glowProgram = GLUtil.buildProgram(GLASS_VERT, GLOW_FRAG);
        wAPos = GLES20.glGetAttribLocation(glowProgram, "aPos");
        wUTex = GLES20.glGetUniformLocation(glowProgram, "uTex");
        wUScreenSize = GLES20.glGetUniformLocation(glowProgram, "uScreenSize");
        wUColor = GLES20.glGetUniformLocation(glowProgram, "uColor");

        glowColorProgram = GLUtil.buildProgram(GLASS_VERT, GLOW_COLOR_FRAG);
        gcAPos = GLES20.glGetAttribLocation(glowColorProgram, "aPos");
        gcUTex = GLES20.glGetUniformLocation(glowColorProgram, "uTex");
        gcUScreenSize = GLES20.glGetUniformLocation(glowColorProgram, "uScreenSize");
        gcUIntensity = GLES20.glGetUniformLocation(glowColorProgram, "uIntensity");

        float[] fq = {
                -1, -1, 0, 0,
                1, -1, 1, 0,
                -1, 1, 0, 1,
                1, -1, 1, 0,
                1, 1, 1, 1,
                -1, 1, 0, 1,
        };
        fullscreenQuad = makeBuffer(fq);
        panelQuad = makeBuffer(new float[6 * 2]); // 占位，drawGlassPanel时每次重填
    }

    private FloatBuffer makeBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(data);
        fb.position(0);
        return fb;
    }

    public void resize(int screenW, int screenH) {
        int w = Math.max(1, screenW / downsample);
        int h = Math.max(1, screenH / downsample);
        pingA.create(w, h);
        pingB.create(w, h);
    }

    /** 场景绘制回调:调用方在这个回调里用现有的低分辨率viewport画背景内容 */
    public interface SceneDrawer {
        void draw();
    }

    /** 渲染场景到低分辨率纹理，再做两遍模糊，最终模糊结果留在 pingA.texture */
    /** 跟 captureAndBlur 一样，但用透明背景清屏——用来单独模糊出一个"形状+柔和alpha"的发光贴图，
     *  而不是整屏内容，这样才能叠出真正柔和的阴影光晕而不是硬边分层色块。 */
    public void captureAndBlurTransparent(SceneDrawer scene) {
        pingA.bindAndSetViewport();
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        scene.draw();

        for (int i = 0; i < 2; i++) {
            blurPass(pingA.texture, pingB, 1f, 0f);
            blurPass(pingB.texture, pingA, 0f, 1f);
        }
    }

    /** 把 captureAndBlurTransparent 模糊出来的柔和alpha贴图，按指定颜色画成一层发光阴影 */
    public void drawGlow(float[] color, int screenW, int screenH) {
        FrameBuffer.bindScreen(screenW, screenH);
        GLES20.glUseProgram(glowProgram);
        GLES20.glUniform2f(wUScreenSize, screenW, screenH);
        GLES20.glUniform4f(wUColor, color[0], color[1], color[2], color[3]);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pingA.texture);
        GLES20.glUniform1i(wUTex, 0);

        float[] verts = {
                0, 0, screenW, 0, 0, screenH,
                screenW, 0, screenW, screenH, 0, screenH,
        };
        panelQuad.clear();
        panelQuad.put(verts);
        panelQuad.position(0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glEnableVertexAttribArray(wAPos);
        GLES20.glVertexAttribPointer(wAPos, 2, GLES20.GL_FLOAT, false, 8, panelQuad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(wAPos);
    }

    /** 跟 captureAndBlurTransparent 一样，但专门给小号文字用——半径调成"强扩散"，
     *  文字周围要有明显散开的光晕，而不是紧贴轮廓的一圈薄边。 */
    public void captureAndBlurTransparentTight(SceneDrawer scene) {
        pingA.bindAndSetViewport();
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        scene.draw();

        for (int i = 0; i < 3; i++) {
            blurPass(pingA.texture, pingB, 1f, 0f);
            blurPass(pingB.texture, pingA, 0f, 1f);
        }
    }

    /** 辉光叠加合成：直接采样模糊纹理本身的RGB（每一行文字画的时候用的是它自己的颜色，
     *  模糊时颜色跟着一起糊开，所以这里不需要再传一个统一色调——每行发光颜色天然就是对的），
     *  用叠加混合(ADDITIVE)而不是普通alpha混合，这样多行文字挨着的时候辉光是"越亮"而不是"变灰变脏"，
     *  视觉上更接近真实客户端那种霓虹发光感，而不是一坨半透明黑影糊在文字周围。 */
    public void drawGlowColor(float intensity, int screenW, int screenH) {
        FrameBuffer.bindScreen(screenW, screenH);
        GLES20.glUseProgram(glowColorProgram);
        GLES20.glUniform2f(gcUScreenSize, screenW, screenH);
        GLES20.glUniform1f(gcUIntensity, intensity);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pingA.texture);
        GLES20.glUniform1i(gcUTex, 0);

        float[] verts2 = {
                0, 0, screenW, 0, 0, screenH,
                screenW, 0, screenW, screenH, 0, screenH,
        };
        panelQuad.clear();
        panelQuad.put(verts2);
        panelQuad.position(0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE); // 叠加混合，越叠越亮，而不是变灰变脏

        GLES20.glEnableVertexAttribArray(gcAPos);
        GLES20.glVertexAttribPointer(gcAPos, 2, GLES20.GL_FLOAT, false, 8, panelQuad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(gcAPos);

        // 恢复成正常的alpha混合，避免影响后续其他绘制
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }

    public void captureAndBlur(SceneDrawer scene) {
        pingA.bindAndSetViewport();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        scene.draw();

        // 两遍横+纵向模糊，模糊半径更大（比单轮明显柔和很多）
        for (int i = 0; i < 2; i++) {
            blurPass(pingA.texture, pingB, 1f, 0f);
            blurPass(pingB.texture, pingA, 0f, 1f);
        }
    }

    private void blurPass(int srcTex, FrameBuffer dst, float dirX, float dirY) {
        dst.bindAndSetViewport();
        // 关键修复：必须先关掉混合，否则这次模糊结果会跟dst里上一次残留的内容做alpha混合，
        // 而不是干净地覆盖。阴影的模糊和背景的模糊在同一帧里共用这两张FBO，
        // 混合没关掉就会互相串色，表现为背景被阴影颜色铺满。
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(blurProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, srcTex);
        GLES20.glUniform1i(bUTex, 0);
        GLES20.glUniform2f(bUDir, dirX, dirY);
        GLES20.glUniform2f(bUTexel, 1f / dst.width, 1f / dst.height);

        fullscreenQuad.position(0);
        GLES20.glEnableVertexAttribArray(bAPos);
        GLES20.glVertexAttribPointer(bAPos, 2, GLES20.GL_FLOAT, false, 16, fullscreenQuad);
        fullscreenQuad.position(2);
        GLES20.glEnableVertexAttribArray(bAUV);
        GLES20.glVertexAttribPointer(bAUV, 2, GLES20.GL_FLOAT, false, 16, fullscreenQuad);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        GLES20.glDisableVertexAttribArray(bAPos);
        GLES20.glDisableVertexAttribArray(bAUV);
    }

    /** 在主屏幕上画毛玻璃面板：从 pingA 模糊纹理里按屏幕UV采样并做圆角裁剪+色调叠加
     *  overallAlpha: 面板整体淡入淡出用的透明度（独立于tintRgba里的色调混合比例），
     *  开关面板动画时必须传这个，否则面板形状本身不会跟着淡出，只是颜色变浅——
     *  这正是之前"关闭时面板还会停留一下"的根因。 */
    public void drawGlassPanel(float x, float y, float w, float h, float radius, float[] tintRgba,
                                float overallAlpha, int screenW, int screenH) {
        FrameBuffer.bindScreen(screenW, screenH);
        GLES20.glUseProgram(glassProgram);
        GLES20.glUniform2f(gUScreenSize, screenW, screenH);
        GLES20.glUniform2f(gURectPos, x + w / 2f, y + h / 2f);
        GLES20.glUniform2f(gURectHalf, w / 2f, h / 2f);
        GLES20.glUniform1f(gURadius, radius);
        GLES20.glUniform4f(gUTint, tintRgba[0], tintRgba[1], tintRgba[2], tintRgba[3]);
        GLES20.glUniform1f(gUOverallAlpha, overallAlpha);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pingA.texture);
        GLES20.glUniform1i(gUTex, 0);

        float pad = radius + 4f; // 略微扩大绘制范围，避免SDF边缘被顶点裁掉
        float[] verts = {
                x - pad, y - pad,
                x + w + pad, y - pad,
                x - pad, y + h + pad,
                x + w + pad, y - pad,
                x + w + pad, y + h + pad,
                x - pad, y + h + pad,
        };
        panelQuad.clear();
        panelQuad.put(verts);
        panelQuad.position(0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glEnableVertexAttribArray(gAPos);
        GLES20.glVertexAttribPointer(gAPos, 2, GLES20.GL_FLOAT, false, 8, panelQuad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDisableVertexAttribArray(gAPos);
    }
}
