package com.ikechi.studio.onwa.player;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.ikechi.studio.IkLog;

import java.util.ArrayList;
import java.util.List;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String PREF_FILE = "onwa_prefs";
    private static final String PREF_FIRST_RUN = "first_run";
    private static final long SPLASH_DELAY_MS = 5000;

    private static final String[] REQUIRED_PERMISSIONS;
    private static final String[] OPTIONAL_PERMISSIONS;

    static {
        if (Build.VERSION.SDK_INT >= 33) {
            REQUIRED_PERMISSIONS = new String[]{
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.RECORD_AUDIO
            };
            OPTIONAL_PERMISSIONS = new String[]{
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.NEARBY_WIFI_DEVICES,
                android.Manifest.permission.BLUETOOTH_CONNECT
            };
        } else if (Build.VERSION.SDK_INT >= 31) {
            REQUIRED_PERMISSIONS = new String[]{
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            };
            OPTIONAL_PERMISSIONS = new String[]{
                android.Manifest.permission.BLUETOOTH_CONNECT
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            REQUIRED_PERMISSIONS = new String[]{
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            };
            OPTIONAL_PERMISSIONS = new String[]{
                android.Manifest.permission.BLUETOOTH
            };
        } else {
            REQUIRED_PERMISSIONS = new String[0];
            OPTIONAL_PERMISSIONS = new String[0];
        }
    }

    private SharedPreferences prefs;
    private boolean isFirstRun;
    private Handler handler;
    private Runnable splashRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Initialize IkLog as early as possible (uses self-init fallback too)
            IkLog.init(getApplicationContext());
            IkLog.d(TAG, "SplashActivity onCreate");

            setContentView(R.layout.activity_splash);

            prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
            isFirstRun = prefs.getBoolean(PREF_FIRST_RUN, true);
            handler = new Handler();

            splashRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        IkLog.d(TAG, "Splash delay elapsed, checking app state");
                        checkAppState();
                    } catch (Exception e) {
                        IkLog.e(TAG, "Error in splash runnable", e);
                    }
                }
            };
            handler.postDelayed(splashRunnable, SPLASH_DELAY_MS);
            IkLog.d(TAG, "Splash delay started: " + SPLASH_DELAY_MS + "ms");
        } catch (Exception e) {
            IkLog.e(TAG, "Fatal error in SplashActivity onCreate", e);
            Toast.makeText(this, "App initialization failed", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void checkAppState() {
        try {
            boolean hasPerms = hasRequiredPermissions();
            IkLog.d(TAG, "checkAppState: hasRequiredPermissions=" + hasPerms + ", isFirstRun=" + isFirstRun);
            if (!hasPerms) {
                if (isFirstRun) {
                    IkLog.d(TAG, "First run & no permissions -> showing explanation dialog");
                    showPermissionExplanationDialog();
                } else {
                    IkLog.d(TAG, "Not first run but missing permissions -> requesting");
                    requestNeededPermissions();
                }
            } else {
                IkLog.d(TAG, "Permissions OK -> launching MainActivity");
                markFirstRunComplete();
                launchMainActivity();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error checking app state", e);
        }
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            IkLog.d(TAG, "No runtime permissions needed (API < 23)");
            return true;
        }
        boolean hasMedia = hasMediaPermission();
        IkLog.d(TAG, "hasMediaPermission: " + hasMedia);
        return hasMedia;
    }

    private boolean hasMediaPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error checking media permission", e);
        }
        return true;
    }

    private void showPermissionExplanationDialog() {
        try {
            IkLog.d(TAG, "Showing permission explanation dialog");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permissions Required");

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_permission, null);
            builder.setView(dialogView);

            TextView tvAbout = (TextView) dialogView.findViewById(R.id.tvAbout);
            TextView tvPrivacy = (TextView) dialogView.findViewById(R.id.tvPrivacy);
            Button btnGrant = (Button) dialogView.findViewById(R.id.btnGrant);
            Button btnExit = (Button) dialogView.findViewById(R.id.btnExit);

            String aboutText = "<b>Onwa Media Player Pro</b><br><br>"
                + "This app requires the following permissions:<br>"
                + "&#8226; Media: To scan and play your music, videos, photos<br>"
                + "&#8226; Microphone: For audio spectrum visualizer<br>"
                + "&#8226; Notifications: To show playback controls<br>"
                + "&#8226; Wi-Fi/Bluetooth: For local file sharing<br><br>"
                + "All processing is done locally on your device.";
            tvAbout.setText(Html.fromHtml(aboutText, Html.FROM_HTML_MODE_LEGACY));

            String privacyText = "<b>Privacy Assurance:</b><br>"
                + "&#8226; No data collection or sharing<br>"
                + "&#8226; All data remains on your device<br>"
                + "&#8226; Complete offline functionality";
            tvPrivacy.setText(Html.fromHtml(privacyText, Html.FROM_HTML_MODE_LEGACY));
            tvPrivacy.setMovementMethod(LinkMovementMethod.getInstance());

            final AlertDialog dialog = builder.create();
            dialog.setCancelable(false);

            btnGrant.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    IkLog.d(TAG, "User accepted permission request");
                    dialog.dismiss();
                    markFirstRunComplete();
                    requestNeededPermissions();
                }
            });

            btnExit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    IkLog.d(TAG, "User exited app at permission dialog");
                    finish();
                }
            });

            dialog.show();
        } catch (Exception e) {
            IkLog.e(TAG, "Error showing permission dialog", e);
        }
    }

    private void requestNeededPermissions() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                IkLog.d(TAG, "No runtime permissions to request");
                launchMainActivity();
                return;
            }

            List<String> toRequest = new ArrayList<>();

            for (String perm : REQUIRED_PERMISSIONS) {
                if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    toRequest.add(perm);
                }
            }

            for (String perm : OPTIONAL_PERMISSIONS) {
                if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                        toRequest.add(perm);
                    } else {
                        IkLog.d(TAG, "Skipping optional permission (rationale showing): " + perm);
                    }
                }
            }

            IkLog.d(TAG, "Requesting permissions: " + toRequest.toString());
            if (!toRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this, toRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            } else {
                IkLog.d(TAG, "All permissions already granted");
                launchMainActivity();
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error requesting permissions", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        try {
            if (requestCode != PERMISSION_REQUEST_CODE) return;

            StringBuilder resultDetail = new StringBuilder();
            for (int i = 0; i < permissions.length; i++) {
                resultDetail.append(permissions[i]).append("=")
                        .append(grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied").append(", ");
            }
            IkLog.d(TAG, "onRequestPermissionsResult: " + resultDetail);

            if (hasMediaPermission()) {
                IkLog.d(TAG, "Media permission granted -> launching MainActivity");
                launchMainActivity();
            } else {
                IkLog.w(TAG, "Media permission denied — exiting");
                Toast.makeText(this, "Media access is required for the app to work. Exiting...", Toast.LENGTH_LONG).show();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                }, 2500);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Error in onRequestPermissionsResult", e);
        }
    }

    private void markFirstRunComplete() {
        try {
            prefs.edit().putBoolean(PREF_FIRST_RUN, false).apply();
            isFirstRun = false;
            IkLog.d(TAG, "First run flag set to false");
        } catch (Exception e) {
            IkLog.e(TAG, "Error marking first run complete", e);
        }
    }

    private void launchMainActivity() {
        try {
            IkLog.d(TAG, "Launching MainActivity");
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            IkLog.e(TAG, "Error launching MainActivity", e);
            // Fallback exit
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (handler != null && splashRunnable != null) {
                handler.removeCallbacks(splashRunnable);
            }
            handler = null;
            splashRunnable = null;
            IkLog.d(TAG, "onDestroy");
        } catch (Exception e) {
            // can't log if IkLog already shut down? Still try.
            IkLog.e(TAG, "Error in onDestroy", e);
        }
    }
}