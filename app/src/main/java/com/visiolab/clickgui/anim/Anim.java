package com.visiolab.clickgui.anim;

/**
 * 对应 Soar 源码里的 Animation / SimpleAnimation 工具类。
 * 这里用两种：
 *  - smooth()：指数平滑，用于滚动、悬停高亮、开关滑块等常规过渡
 *  - backEase()：还原 GuiModMenu 打开时使用的 EaseBackIn 回弹效果
 */
public class Anim {

    private float value;
    private float target;
    private final float speed; // 越大过渡越快

    public Anim(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public float getTarget() {
        return target;
    }

    /** 每帧调用一次，dt 为秒 */
    public float update(float dt) {
        float factor = 1f - (float) Math.exp(-speed * dt);
        value += (target - value) * factor;
        if (Math.abs(target - value) < 0.006f) value = target;
        return value;
    }

    public float get() {
        return value;
    }

    public void snapTo(float v) {
        value = v;
        target = v;
    }

    /**
     * EaseBackIn 风格：0..1 输入 t，输出带轻微回弹的插值系数。
     * 对应 GuiModMenu 面板弹出动画。
     */
    public static float backEaseOut(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        t = Math.max(0f, Math.min(1f, t));
        float p = t - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }
}
