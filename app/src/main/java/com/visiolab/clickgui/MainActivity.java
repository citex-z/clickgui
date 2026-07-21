package com.visiolab.clickgui;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.visiolab.clickgui.gl.UIRenderer;

/**
 * Rlin Client ClickGUI 的 Android demo 宿主。
 * 只负责创建 GLSurfaceView(OpenGL ES 2.0)并把触摸事件转发给 UIRenderer。
 */
public class MainActivity extends Activity {

    private GLSurfaceView glView;
    private UIRenderer renderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        // 需要半透明面板 + 模糊阴影效果，打开 alpha 通道
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        renderer = new UIRenderer(this);
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        setContentView(glView);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        renderer.onTouch(event);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        glView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
    }
}
