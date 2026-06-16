package com.ikechi.studio.onwa.player.bases;
import android.app.*;
import android.os.*;
import android.view.*;

public class BaseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyImmersiveMode();
    }

	/**
     * Hides the status bar and navigation bar for a truly full-screen video experience.
     * Called on every resume and configuration change.
     */
    private void applyImmersiveMode() {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		if (getActionBar() != null) {
            getActionBar().hide();
        }
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // API 19+: true immersive sticky
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            // API 18: fullscreen only
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    /** Restores normal system UI when leaving the video player. */
    private void clearImmersiveMode() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
	}
	
	
}
