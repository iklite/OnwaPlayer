package com.ikechi.studio.onwa.player.view;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;
import com.ikechi.studio.onwa.player.view.core.VisualizerConfig;
import com.ikechi.studio.onwa.player.utils.BeatDetector;

public class GLVisualizerView extends GLSurfaceView {

    private GLVisualizerRenderer renderer;
    private BeatDetector beatDetector;

    // Saved rotation speed so we can restore it after resume
    private float savedRotationSpeed = 0.3f;

    public GLVisualizerView(Context context) {
        super(context);
        init(context);
    }

    public GLVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        VisualizerConfig config = new VisualizerConfig();
        config.resetToDefaults();

        // GLES 3.0 requires minSdk 18 (Android 4.3).
        setEGLContextClientVersion(3);
        // Request an 8-bit RGBA surface with a 16-bit depth buffer.
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        renderer = new GLVisualizerRenderer(context, config);
        setRenderer(renderer);

        beatDetector = new BeatDetector();
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    /** Call from your audio processing thread with the latest FFT data. */
    public void updateAudioData(float[] audioData) {
        if (renderer == null || audioData == null) return;
        boolean beatDetectedFlag = beatDetector.detect(audioData);
        renderer.updateAudioData(audioData, beatDetectedFlag, beatDetector.getBeatIntensity());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (renderer != null && renderer.handleTouchEvent(event)) return true;
        return super.onTouchEvent(event);
    }

    public GLVisualizerRenderer getRenderer() {
        return renderer;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (renderer != null) renderer.resume(savedRotationSpeed);
    }

    @Override
    public void onPause() {
        if (renderer != null) {
            savedRotationSpeed = renderer.getRotationSpeed();
            renderer.pause();
        }
        super.onPause();
    }

    /** Release all OpenGL resources. Call when the hosting Activity is destroyed. */
    public void release() {
        if (renderer != null) {
            renderer.release();
            renderer = null;
        }
    }

    public float getBeatIntensity() {
        return renderer != null ? renderer.getBeatIntensity() : 0.0f;
    }
}
