package com.ikechi.studio.onwa.player.widget;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.service.AudioPlayerService;

import java.io.File;

public class OnwaWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "OnwaWidgetProvider";
    private static final String PREFS_PLAYBACK = "widget_playback_state";
    private static final String KEY_LAST_URI = "last_uri";
    private static final String KEY_LAST_POSITION = "last_position";
    private static final String KEY_WIDGET_CLICKS = "widget_clicks";

    private static long sLastClickTime = 0;
    private static final long CLICK_THROTTLE_MS = 300;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        try {
            IkLog.d(TAG, "onUpdate called for " + appWidgetIds.length + " widgets");
            WidgetDataHelper.WidgetData data = WidgetDataHelper.getLastData(context);
            for (int id : appWidgetIds) {
                try {
                    RemoteViews views = buildRemoteViews(context, data);
                    setAllButtonIntents(context, views, data.isPlaying);
                    appWidgetManager.updateAppWidget(id, views);
                    IkLog.d(TAG, "Widget " + id + " updated");
                } catch (Throwable t) {
                    IkLog.e(TAG, "Error updating widget " + id + ", using fallback", t);
                    RemoteViews fallback = buildFallbackViews(context);
                    setAllButtonIntents(context, fallback, false);
                    appWidgetManager.updateAppWidget(id, fallback);
                }
            }
        } catch (Throwable t) {
            IkLog.e(TAG, "Fatal error in onUpdate", t);
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        IkLog.d(TAG, "Widget enabled - initializing");
        resetClickCount(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        IkLog.d(TAG, "Widget disabled - logging stats");
        logWidgetStats(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        try {
            String action = intent.getAction();
            if (action == null) return;

            // Throttle rapid clicks
            if (isWidgetAction(action)) {
                if (!throttleClick()) {
                    IkLog.d(TAG, "Click throttled: " + action);
                    return;
                }
                incrementClickCount(context);
            }

            IkLog.d(TAG, "onReceive: " + action);

            switch (action) {
                case Intent.ACTION_BOOT_COMPLETED:
                    handleBootCompleted(context);
                    break;

                case Intent.ACTION_MY_PACKAGE_REPLACED:
                    handleAppUpdated(context);
                    break;

                case "PLAY_PAUSE":
                    handlePlayPause(context);
                    break;

                case "NEXT":
                    handleNext(context);
                    break;

                case "PREVIOUS":
                    handlePrevious(context);
                    break;

                case AudioPlayerService.ACTION_METADATA_UPDATED:
                case AudioPlayerService.ACTION_WIDGET_DATA:
                case AudioPlayerService.ACTION_PLAYBACK_STARTED:
                case AudioPlayerService.ACTION_PLAYBACK_RESUMED:
                case AudioPlayerService.ACTION_PLAYBACK_STOPPED:
                case AudioPlayerService.ACTION_PLAYBACK_PAUSED:
                case AudioPlayerService.ACTION_PLAYBACK_COMPLETED:
                    updateWidget(context);
                    break;

                case AudioPlayerService.ACTION_PLAYBACK_ERROR:
                    handlePlaybackError(context, intent);
                    break;

                default:
                    break;
            }
        } catch (Throwable t) {
            IkLog.e(TAG, "Error in onReceive", t);
        }
    }

    private boolean isWidgetAction(String action) {
        return "PLAY_PAUSE".equals(action) || "NEXT".equals(action) || "PREVIOUS".equals(action);
    }

    private boolean throttleClick() {
        long now = System.currentTimeMillis();
        if (now - sLastClickTime < CLICK_THROTTLE_MS) {
            return false;
        }
        sLastClickTime = now;
        return true;
    }

    private void handleBootCompleted(Context context) {
        IkLog.d(TAG, "Boot completed - restoring widget state");
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, OnwaWidgetProvider.class));
            if (ids == null || ids.length == 0) {
                IkLog.d(TAG, "No widgets to restore after boot");
                return;
            }

            WidgetDataHelper.WidgetData data = WidgetDataHelper.getLastData(context);

            // Verify that the last played file still exists
            SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
            String lastUri = prefs.getString(KEY_LAST_URI, null);
            if (lastUri != null && !isMediaAccessible(context, lastUri)) {
                IkLog.w(TAG, "Last played media no longer accessible: " + lastUri);
                clearPlaybackState(context);
                data = new WidgetDataHelper.WidgetData();
            }

            WidgetDataHelper.save(context, data.title, data.artist, data.art, false);

            RemoteViews views = buildRemoteViews(context, data);
            setAllButtonIntents(context, views, false);
            mgr.updateAppWidget(new ComponentName(context, OnwaWidgetProvider.class), views);
            IkLog.d(TAG, "Widget restored after boot");
        } catch (Throwable t) {
            IkLog.e(TAG, "Error restoring widget after boot", t);
        }
    }

    private void handleAppUpdated(Context context) {
        IkLog.d(TAG, "App updated - refreshing widget");
        updateWidget(context);
    }

    private void handlePlaybackError(Context context, Intent intent) {
        String errorMsg = intent.getStringExtra("error_message");
        IkLog.e(TAG, "Playback error: " + errorMsg);

        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            WidgetDataHelper.WidgetData data = WidgetDataHelper.getLastData(context);
            WidgetDataHelper.save(context, data.title, data.artist, data.art, false);

            views.setTextViewText(R.id.widget_title, errorMsg != null ? errorMsg : "Playback Error");
            views.setTextViewText(R.id.widget_artist, "Tap to retry");
            setAllButtonIntents(context, views, false);
            mgr.updateAppWidget(new ComponentName(context, OnwaWidgetProvider.class), views);
        } catch (Throwable t) {
            IkLog.e(TAG, "Error showing playback error on widget", t);
        }
    }

    private void handlePlayPause(Context context) {
        WidgetDataHelper.WidgetData data = WidgetDataHelper.getLastData(context);

        if (data.isPlaying) {
            Intent pauseIntent = new Intent("PAUSE");
            pauseIntent.setPackage(context.getPackageName());
            context.sendBroadcast(pauseIntent);
        } else {
            if (isServiceRunning(context)) {
                Intent playIntent = new Intent("PLAY");
                playIntent.setPackage(context.getPackageName());
                context.sendBroadcast(playIntent);
            } else {
                if (hasLastPlaybackState(context)) {
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
                    String lastUri = prefs.getString(KEY_LAST_URI, null);
                    if (lastUri != null && isMediaAccessible(context, lastUri)) {
                        launchServiceWithLastState(context);
                    } else {
                        showToast(context, "Previous media not available");
                        clearPlaybackState(context);
                        launchMainActivity(context);
                    }
                } else {
                    launchMainActivity(context);
                }
            }
        }
    }

    private void handleNext(Context context) {
        if (isServiceRunning(context)) {
            Intent nextIntent = new Intent("NEXT");
            nextIntent.setPackage(context.getPackageName());
            context.sendBroadcast(nextIntent);
        } else if (hasLastPlaybackState(context)) {
            launchServiceWithSkip(context, "skip_next");
        } else {
            showToast(context, "Nothing to skip");
        }
    }

    private void handlePrevious(Context context) {
        if (isServiceRunning(context)) {
            Intent prevIntent = new Intent("PREVIOUS");
            prevIntent.setPackage(context.getPackageName());
            context.sendBroadcast(prevIntent);
        } else if (hasLastPlaybackState(context)) {
            launchServiceWithSkip(context, "skip_previous");
        } else {
            showToast(context, "Nothing to skip");
        }
    }

    private boolean isMediaAccessible(Context context, String uriString) {
        if (uriString == null) return false;
        try {
            if (uriString.startsWith("/") || uriString.startsWith("file://")) {
                String path = uriString.replace("file://", "");
                File file = new File(path);
                return file.exists() && file.canRead();
            }
            if (uriString.startsWith("content://")) {
                Uri uri = Uri.parse(uriString);
                try {
                    context.getContentResolver().openInputStream(uri).close();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            IkLog.e(TAG, "Error checking media accessibility", t);
            return false;
        }
    }

    private boolean isServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AudioPlayerService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLastPlaybackState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
        String lastUri = prefs.getString(KEY_LAST_URI, null);
        return lastUri != null && !lastUri.isEmpty();
    }

    private void savePlaybackState(Context context, String uri, long position) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_LAST_URI, uri)
            .putLong(KEY_LAST_POSITION, position)
            .putLong("last_update", System.currentTimeMillis())
            .apply();
        IkLog.d(TAG, "Playback state saved: " + uri + " @ " + position);
    }

    private void clearPlaybackState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_LAST_URI)
            .remove(KEY_LAST_POSITION)
            .apply();
        IkLog.d(TAG, "Playback state cleared");
    }

    private void launchServiceWithLastState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
        String lastUri = prefs.getString(KEY_LAST_URI, null);
        long lastPosition = prefs.getLong(KEY_LAST_POSITION, 0);

        Intent resumeIntent = new Intent(context, AudioPlayerService.class);
        resumeIntent.setAction(AudioPlayerService.ACTION_RESUME_LAST);
        resumeIntent.putExtra("last_uri", lastUri);
        resumeIntent.putExtra("last_position", lastPosition);

        startServiceSafe(context, resumeIntent);
        IkLog.d(TAG, "Launching service with last state: " + lastUri);
    }

    private void launchServiceWithSkip(Context context, String skipAction) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
        String lastUri = prefs.getString(KEY_LAST_URI, null);
        long lastPosition = prefs.getLong(KEY_LAST_POSITION, 0);

        Intent serviceIntent = new Intent(context, AudioPlayerService.class);
        serviceIntent.setAction(AudioPlayerService.ACTION_RESUME_LAST);
        serviceIntent.putExtra("last_uri", lastUri);
        serviceIntent.putExtra("last_position", lastPosition);
        serviceIntent.putExtra(skipAction, true);

        startServiceSafe(context, serviceIntent);
        IkLog.d(TAG, "Launching service with skip: " + skipAction);
    }

    private void launchMainActivity(Context context) {
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(openIntent);
    }

    private void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void incrementClickCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
        int clicks = prefs.getInt(KEY_WIDGET_CLICKS, 0);
        prefs.edit().putInt(KEY_WIDGET_CLICKS, clicks + 1).apply();
    }

    private void resetClickCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_WIDGET_CLICKS, 0).apply();
    }

    private void logWidgetStats(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PLAYBACK, Context.MODE_PRIVATE);
        int clicks = prefs.getInt(KEY_WIDGET_CLICKS, 0);
        String lastUri = prefs.getString(KEY_LAST_URI, "none");
        IkLog.d(TAG, "Widget stats - Total clicks: " + clicks + ", Last media: " + lastUri);
    }

    private void startServiceSafe(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable t) {
            IkLog.e(TAG, "Error starting service", t);
            showToast(context, "Unable to start playback");
        }
    }

    private void updateWidget(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, OnwaWidgetProvider.class));
            if (ids == null || ids.length == 0) {
                IkLog.d(TAG, "updateWidget: no widgets to update");
                return;
            }

            WidgetDataHelper.WidgetData data = WidgetDataHelper.getLastData(context);
            RemoteViews views = buildRemoteViews(context, data);
            setAllButtonIntents(context, views, data.isPlaying);
            mgr.updateAppWidget(new ComponentName(context, OnwaWidgetProvider.class), views);
            IkLog.d(TAG, "Widget updated: title=" + data.title + ", playing=" + data.isPlaying);
        } catch (Throwable t) {
            IkLog.e(TAG, "Error updating widget", t);
        }
    }

    private RemoteViews buildRemoteViews(Context context, WidgetDataHelper.WidgetData data) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        String title = (data.title != null && !data.title.isEmpty()) ? data.title : context.getString(R.string.not_playing);
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_artist, data.artist != null ? data.artist : "");

        if (data.art != null && !data.art.isRecycled()) {
            views.setImageViewBitmap(R.id.widget_album_art, data.art);
        } else {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.default_album_art);
        }

        views.setImageViewResource(R.id.widget_play_pause,
                data.isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        return views;
    }

    private RemoteViews buildFallbackViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.widget_title, context.getString(R.string.not_playing));
        views.setTextViewText(R.id.widget_artist, "");
        views.setImageViewResource(R.id.widget_album_art, R.drawable.default_album_art);
        views.setImageViewResource(R.id.widget_play_pause, android.R.drawable.ic_media_play);
        return views;
    }

    private void setAllButtonIntents(Context context, RemoteViews views, boolean isPlaying) {
        views.setOnClickPendingIntent(R.id.widget_play_pause,
                getPlayPausePendingIntent(context, isPlaying));
        views.setOnClickPendingIntent(R.id.widget_next,
                getPendingIntent(context, "NEXT"));
        views.setOnClickPendingIntent(R.id.widget_previous,
                getPendingIntent(context, "PREVIOUS"));

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPI = PendingIntent.getActivity(context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, openPI);
    }

    private PendingIntent getPendingIntent(Context context, String action) {
        Intent intent = new Intent(context, OnwaWidgetProvider.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getPlayPausePendingIntent(Context context, boolean currentlyPlaying) {
        Intent intent = new Intent(context, OnwaWidgetProvider.class);
        intent.setAction("PLAY_PAUSE");
        intent.putExtra("current_playing", currentlyPlaying);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}