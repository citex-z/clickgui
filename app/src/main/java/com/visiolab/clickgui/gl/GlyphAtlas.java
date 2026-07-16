package com.visiolab.clickgui.gl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 真正的 glyph-atlas：一张纹理，按需把用到的字符（ASCII / 中文都行）
 * 打包进去，用 shelf-packing（一行行摆放，摆不下就换行）。
 * 新字形用 glTexSubImage2D 只上传那一小块，不用重传整张纹理。
 */
public class GlyphAtlas {

    public static class Glyph {
        float u0, v0, u1, v1; // 归一化 UV
        float width, height;  // 像素尺寸
        float advance;        // 下一个字符的前进量
        float bearingY;       // 基线到字形顶部的偏移
    }

    private static final int ATLAS_W = 2048;
    private static final int ATLAS_H = 2048;

    private int texture;
    private int shelfX = 1, shelfY = 1, shelfRowHeight = 0;

    private final Map<String, Glyph> glyphs = new HashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint iconPaint; // 图标字体 Paint（Tenacity Icons）
    private Typeface iconTypeface;
    private boolean iconFontLoaded = false;
    private boolean boldFontLoaded = false;
    private Paint watermarkPaint; // 水印字体 Paint（Pacifico）
    private boolean watermarkFontLoaded = false;

    /** 加载主字体（Regular + Bold） */
    public void loadMainFont(android.content.res.AssetManager assets, String regularPath, String boldPath) {
        try {
            Typeface regular = Typeface.createFromAsset(assets, regularPath);
            if (regular != null) {
                paint.setTypeface(regular);
                android.util.Log.i("GlyphAtlas", "Main font loaded: " + regularPath);
            }
        } catch (Exception e) {
            android.util.Log.e("GlyphAtlas", "Failed to load main font " + regularPath + ": " + e.getMessage());
        }
        try {
            Typeface bold = Typeface.createFromAsset(assets, boldPath);
            if (bold != null) {
                boldPaint.setTypeface(bold);
                boldFontLoaded = true;
                android.util.Log.i("GlyphAtlas", "Bold font loaded: " + boldPath);
            }
        } catch (Exception e) {
            android.util.Log.e("GlyphAtlas", "Failed to load bold font " + boldPath + ": " + e.getMessage());
        }
    }

    /** 加载水印字体（Pacifico） */
    public void loadWatermarkFont(android.content.res.AssetManager assets, String path) {
        try {
            Typeface wm = Typeface.createFromAsset(assets, path);
            if (wm != null) {
                watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                watermarkPaint.setTypeface(wm);
                watermarkPaint.setColor(Color.WHITE);
                watermarkFontLoaded = true;
                android.util.Log.i("GlyphAtlas", "Watermark font loaded: " + path);
            }
        } catch (Exception e) {
            android.util.Log.e("GlyphAtlas", "Failed to load watermark font: " + e.getMessage());
        }
    }

    /** 用水印字体生成字形 */
    public Glyph ensureWatermarkGlyph(char c, float sizePx) {
        if (!watermarkFontLoaded) return ensureGlyph(c, sizePx);
        int sizeBucket = Math.round(sizePx / 4f) * 4;
        if (sizeBucket < 4) sizeBucket = 4;
        String key = "wm#" + c + "#" + sizeBucket;
        Glyph g = glyphs.get(key);
        if (g != null) return g;

        watermarkPaint.setTextSize(sizeBucket);
        watermarkPaint.setColor(Color.WHITE);
        String s = String.valueOf(c);

        Paint.FontMetrics fm = watermarkPaint.getFontMetrics();
        int w = Math.max(1, (int) Math.ceil(watermarkPaint.measureText(s)) + 2);
        int h = Math.max(1, (int) Math.ceil(fm.bottom - fm.top) + 2);
        float advance = watermarkPaint.measureText(s);

        if (shelfX + w + 2 > ATLAS_W) { shelfX = 1; shelfY += shelfRowHeight + 2; shelfRowHeight = 0; }
        if (shelfY + h + 2 > ATLAS_H) { resetAtlas(); }

        Bitmap glyphBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(glyphBmp);
        canvas.drawText(s, 1, -fm.top + 1, watermarkPaint);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, shelfX, shelfY, glyphBmp);
        glyphBmp.recycle();

        float inset = 0.5f;
        g = new Glyph();
        g.u0 = (shelfX + inset) / (float) ATLAS_W;
        g.v0 = (shelfY + inset) / (float) ATLAS_H;
        g.u1 = (shelfX + w - inset) / (float) ATLAS_W;
        g.v1 = (shelfY + h - inset) / (float) ATLAS_H;
        g.width = w;
        g.height = h;
        g.advance = advance;
        g.bearingY = -fm.top;
        glyphs.put(key, g);
        shelfX += w + 2;
        shelfRowHeight = Math.max(shelfRowHeight, h);
        return g;
    }

    public float measureWatermark(String text, float sizePx) {
        if (!watermarkFontLoaded) return measure(text, sizePx);
        watermarkPaint.setTextSize(sizePx);
        return watermarkPaint.measureText(text);
    }

    public boolean isWatermarkFontLoaded() { return watermarkFontLoaded; }

    /** 用 Bold 字体生成字形 */
    public Glyph ensureBoldGlyph(char c, float sizePx) {
        if (!boldFontLoaded) return ensureGlyph(c, sizePx); // 降级到 Regular
        int sizeBucket = Math.round(sizePx / 4f) * 4;
        if (sizeBucket < 4) sizeBucket = 4;
        String key = "bold#" + c + "#" + sizeBucket;
        Glyph g = glyphs.get(key);
        if (g != null) return g;

        boldPaint.setTextSize(sizeBucket);
        boldPaint.setColor(Color.WHITE);
        String s = String.valueOf(c);

        Paint.FontMetrics fm = boldPaint.getFontMetrics();
        int w = Math.max(1, (int) Math.ceil(boldPaint.measureText(s)) + 2);
        int h = Math.max(1, (int) Math.ceil(fm.bottom - fm.top) + 2);
        float advance = boldPaint.measureText(s);

        if (shelfX + w + 2 > ATLAS_W) {
            shelfX = 1;
            shelfY += shelfRowHeight + 2;
            shelfRowHeight = 0;
        }
        if (shelfY + h + 2 > ATLAS_H) {
            resetAtlas();
        }

        Bitmap glyphBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(glyphBmp);
        canvas.drawText(s, 1, -fm.top + 1, boldPaint);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, shelfX, shelfY, glyphBmp);
        glyphBmp.recycle();

        float inset = 0.5f;
        g = new Glyph();
        g.u0 = (shelfX + inset) / (float) ATLAS_W;
        g.v0 = (shelfY + inset) / (float) ATLAS_H;
        g.u1 = (shelfX + w - inset) / (float) ATLAS_W;
        g.v1 = (shelfY + h - inset) / (float) ATLAS_H;
        g.width = w;
        g.height = h;
        g.advance = advance;
        g.bearingY = -fm.top;

        glyphs.put(key, g);
        shelfX += w + 2;
        shelfRowHeight = Math.max(shelfRowHeight, h);

        return g;
    }

    /** 测量 Bold 文字宽度 */
    public float measureBold(String text, float sizePx) {
        if (!boldFontLoaded) return measure(text, sizePx);
        boldPaint.setTextSize(sizePx);
        return boldPaint.measureText(text);
    }

    /** 加载图标字体（从 assets） */
    public void loadIconFont(android.content.res.AssetManager assets, String path) {
        try {
            iconTypeface = Typeface.createFromAsset(assets, path);
            if (iconTypeface == null) {
                android.util.Log.e("GlyphAtlas", "createFromAsset returned null for " + path);
                return;
            }
            iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            iconPaint.setTypeface(iconTypeface);
            iconPaint.setColor(Color.WHITE);
            iconFontLoaded = true;
            android.util.Log.i("GlyphAtlas", "Icon font loaded: " + path);
        } catch (Exception e) {
            android.util.Log.e("GlyphAtlas", "Failed to load icon font " + path + ": " + e.getMessage(), e);
        }
    }

    /** 检查图标字体是否已加载 */
    public boolean isIconFontLoaded() {
        return iconFontLoaded;
    }

    /** 获取图标字符对应的码点（Tenacity Icons 映射） */
    public static String categoryIcon(String categoryName) {
        switch (categoryName) {
            case "Client":      return "n"; // wrench
            case "Combat":      return "c"; // bomb
            case "Movement":    return "w"; // up
            case "Player":      return "e"; // adult
            case "Render":      return "d"; // eye
            case "World":       return "s"; // pin
            case "Misc":        return "b"; // th-list
            case "Preferences": return "n"; // wrench
            default:            return null;
        }
    }

    public void init() {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        texture = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // 分配一张空白纹理作为图集底图
        // 注意：这里特意用 ARGB_8888 而不是 ALPHA_8——
        // Android 在硬件加速环境下用 Canvas 往 ALPHA_8 位图画字有个老毛病，
        // 抗锯齿算法会画出重叠的"雪花笔画"（乱码的真正原因），ARGB_8888 是标准安全路径。
        Bitmap blank = Bitmap.createBitmap(ATLAS_W, ATLAS_H, Bitmap.Config.ARGB_8888);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, blank, 0);
        blank.recycle();
    }

    public int getTexture() {
        return texture;
    }

    /** 图集彻底装不下了：清空重来。之前的兜底是硬把新字形塞到"最后一行"的固定位置，
     *  跟已经打包在那里的旧字形互相覆盖，导致不同字符的UV映射全部错乱——
     *  这正是那种"文字变成重叠雪花状乱码"的真正原因。清空重建虽然会让当帧短暂丢字，
     *  但比永久性的图集数据错乱好得多，而且后面加大了图集尺寸，实际很少会真的触发。 */
    private void resetAtlas() {
        glyphs.clear();
        shelfX = 1; shelfY = 1; shelfRowHeight = 0;
        Bitmap blank = Bitmap.createBitmap(ATLAS_W, ATLAS_H, Bitmap.Config.ARGB_8888);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, blank, 0);
        blank.recycle();
    }

    public Glyph ensureGlyph(char c, float sizePx) {
        // 字号量化得粗一点（4px一档），滑块连续拖动时不会疯狂产生新的字形变体撑爆图集
        int sizeBucket = Math.round(sizePx / 4f) * 4;
        if (sizeBucket < 4) sizeBucket = 4;
        String key = c + "#" + sizeBucket;
        Glyph g = glyphs.get(key);
        if (g != null) return g;

        paint.setTextSize(sizeBucket);
        paint.setColor(Color.WHITE);
        String s = String.valueOf(c);

        Paint.FontMetrics fm = paint.getFontMetrics();
        int w = Math.max(1, (int) Math.ceil(paint.measureText(s)) + 2);
        int h = Math.max(1, (int) Math.ceil(fm.bottom - fm.top) + 2);
        float advance = paint.measureText(s);

        if (shelfX + w + 2 > ATLAS_W) {
            shelfX = 1;
            shelfY += shelfRowHeight + 2;
            shelfRowHeight = 0;
        }
        if (shelfY + h + 2 > ATLAS_H) {
            // 真的装不下了：清空整个图集重新打包，而不是硬挤到一个固定位置覆盖旧数据
            resetAtlas();
        }

        Bitmap glyphBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(glyphBmp);
        canvas.drawText(s, 1, -fm.top + 1, paint);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, shelfX, shelfY, glyphBmp);
        glyphBmp.recycle();

        // UV 向内收缩半个纹素，避免双线性过滤时采到相邻字形的像素（俗称"串色"）
        float inset = 0.5f;
        g = new Glyph();
        g.u0 = (shelfX + inset) / (float) ATLAS_W;
        g.v0 = (shelfY + inset) / (float) ATLAS_H;
        g.u1 = (shelfX + w - inset) / (float) ATLAS_W;
        g.v1 = (shelfY + h - inset) / (float) ATLAS_H;
        g.width = w;
        g.height = h;
        g.advance = advance;
        g.bearingY = -fm.top;

        glyphs.put(key, g);

        shelfX += w + 2;
        shelfRowHeight = Math.max(shelfRowHeight, h);

        return g;
    }

    /** 用图标字体生成字形（Tenacity Icons） */
    public Glyph ensureIconGlyph(char c, float sizePx) {
        if (!iconFontLoaded) return ensureGlyph(c, sizePx); // 降级
        int sizeBucket = Math.round(sizePx / 4f) * 4;
        if (sizeBucket < 4) sizeBucket = 4;
        String key = "icon#" + c + "#" + sizeBucket;
        Glyph g = glyphs.get(key);
        if (g != null) return g;

        iconPaint.setTextSize(sizeBucket);
        iconPaint.setColor(Color.WHITE);
        String s = String.valueOf(c);

        Paint.FontMetrics fm = iconPaint.getFontMetrics();
        int w = Math.max(1, (int) Math.ceil(iconPaint.measureText(s)) + 2);
        int h = Math.max(1, (int) Math.ceil(fm.bottom - fm.top) + 2);
        float advance = iconPaint.measureText(s);

        if (shelfX + w + 2 > ATLAS_W) {
            shelfX = 1;
            shelfY += shelfRowHeight + 2;
            shelfRowHeight = 0;
        }
        if (shelfY + h + 2 > ATLAS_H) {
            resetAtlas();
        }

        Bitmap glyphBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(glyphBmp);
        canvas.drawText(s, 1, -fm.top + 1, iconPaint);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, shelfX, shelfY, glyphBmp);
        glyphBmp.recycle();

        float inset = 0.5f;
        g = new Glyph();
        g.u0 = (shelfX + inset) / (float) ATLAS_W;
        g.v0 = (shelfY + inset) / (float) ATLAS_H;
        g.u1 = (shelfX + w - inset) / (float) ATLAS_W;
        g.v1 = (shelfY + h - inset) / (float) ATLAS_H;
        g.width = w;
        g.height = h;
        g.advance = advance;
        g.bearingY = -fm.top;

        glyphs.put(key, g);

        shelfX += w + 2;
        shelfRowHeight = Math.max(shelfRowHeight, h);

        return g;
    }

    /** 测量图标文字宽度 */
    public float measureIcon(String text, float sizePx) {
        if (!iconFontLoaded) return measure(text, sizePx);
        iconPaint.setTextSize(sizePx);
        return iconPaint.measureText(text);
    }

    public float measure(String text, float sizePx) {
        paint.setTextSize(sizePx);
        return paint.measureText(text);
    }
}
