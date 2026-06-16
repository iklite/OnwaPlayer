package com.ikechi.studio;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * File‑based logging utility that writes to the public Downloads directory.
 * Usage is identical to android.util.Log: IkLog.d(TAG, "message");
 *
 * <p><b>Self‑initialisation:</b> If {@link #init(Context)} is not called,
 * the first log line will initialise automatically using the application
 * context obtained via reflection (or you can still call init later).</p>
 *
 * <p><b>Disable:</b> Call {@link #disable()} to stop all background work
 * and stop writing to the log file. After that, logs only appear in Logcat.</p>
 *
 * <p><b>Single‑file rotation:</b> The log file is limited to ~5 MB. When
 * it exceeds that size, the current file is deleted and a new one with
 * the same name is created automatically. Only one file exists at a time;
 * the user is expected to review and delete logs themselves.</p>
 */
public class IkLog {

    private static final String TAG = "IkLog";
    private static final String LOG_FILE_NAME = "onwa_debug_log.txt";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;   // 5 MB
    private static final SimpleDateFormat sTimeFormat =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static final Object sBufferLock = new Object();
    private static final int DEFAULT_FLUSH_THRESHOLD = 20;

    private static volatile boolean sEnabled = true;
    private static Context sAppContext;
    private static HandlerThread sLogThread;
    private static Handler sLogHandler;
    private static Uri sCurrentLogUri;              // scoped storage (API 29+)
    private static long sCurrentFileSize = 0;

    private static final StringBuilder sBuffer = new StringBuilder();
    private static int sPendingCount = 0;
    private static volatile boolean sInstantFlush = false;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Optional explicit initialisation. If not called, the first log line will
     * initialise automatically.
     */
    public static void init(Context appContext) {
        if (sAppContext != null) return;
        sAppContext = appContext.getApplicationContext();
        startThread();
    }

    /**
     * Disables file logging completely. All subsequent calls will only print
     * to Logcat. Call this when your app is shutting down or you no longer
     * need the file output.
     */
    public static void disable() {
        sEnabled = false;
        if (sLogHandler != null) {
            sLogHandler.post(new Runnable() {
                @Override
                public void run() {
                    flushBuffer();
                    if (sLogThread != null) {
                        sLogThread.quitSafely();
                        sLogThread = null;
                    }
                    sLogHandler = null;
                }
            });
        }
    }

    /**
     * When set to {@code true}, every log line is flushed to disk immediately.
     * When false (default), lines are buffered and flushed asynchronously.
     */
    public static void setInstantFlush(boolean instant) {
        sInstantFlush = instant;
    }

    // ── Public logging methods ───────────────────────────────────────────────

    public static void d(String tag, String msg) { log(Log.DEBUG, tag, msg); }
    public static void v(String tag, String msg) { log(Log.VERBOSE, tag, msg); }
    public static void i(String tag, String msg) { log(Log.INFO, tag, msg); }
    public static void w(String tag, String msg) { log(Log.WARN, tag, msg); }
    public static void e(String tag, String msg) { log(Log.ERROR, tag, msg); }

    public static void e(String tag, String msg, Throwable tr) {
        log(Log.ERROR, tag, msg + "\n" + getStackTraceString(tr));
    }

    public static void wtf(String tag, String msg) {
        log(Log.ASSERT, tag, msg);
    }

    // ── Internal log method ──────────────────────────────────────────────────

    private static void log(int priority, String tag, String msg) {
        // Always print to Logcat first
        android.util.Log.println(priority, tag, msg);

        if (!sEnabled) return;
        ensureInitialized();
        if (sLogHandler == null) return;

        String level = levelChar(priority);
        String timestamp = sTimeFormat.format(new Date());
        String line = timestamp + " " + level + "/" + tag + ": " + msg + "\n";

        synchronized (sBufferLock) {
            sBuffer.append(line);
            sPendingCount++;
        }

        if (sInstantFlush || sPendingCount >= DEFAULT_FLUSH_THRESHOLD) {
            sLogHandler.post(new Runnable() {
                @Override
                public void run() {
                    flushBuffer();
                }
            });
        }
    }

    private static void ensureInitialized() {
        if (sAppContext != null) return;

        // Fallback: try to get the application context via reflection
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Context context = (Context) activityThreadClass.getMethod("getApplication").invoke(activityThread);
            if (context != null) {
                sAppContext = context.getApplicationContext();
            }
        } catch (Exception ignored) {}
        if (sAppContext == null) return;

        startThread();
    }

    private static void startThread() {
        sLogThread = new HandlerThread("IkLogThread");
        sLogThread.start();
        sLogHandler = new Handler(sLogThread.getLooper());

        sLogHandler.post(new Runnable() {
            @Override
            public void run() {
                openLogFile();
            }
        });

        // Periodic buffer flush (every second)
        sLogHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                flushBuffer();
                if (sLogHandler != null) {
                    sLogHandler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    // ── Buffer flushing ──────────────────────────────────────────────────────

    private static void flushBuffer() {
        if (!sEnabled) return;
        final String data;
        synchronized (sBufferLock) {
            if (sPendingCount == 0) return;
            data = sBuffer.toString();
            sBuffer.setLength(0);
            sPendingCount = 0;
        }
        if (data.isEmpty()) return;
        try {
            writeToFile(data);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to write log", e);
        }
    }

    // ── File writing ─────────────────────────────────────────────────────────

    private static void writeToFile(String text) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeScoped(text);
        } else {
            writeLegacy(text);
        }
    }

    private static void writeScoped(String text) throws Exception {
        if (sCurrentLogUri == null) {
            openLogFile();
            if (sCurrentLogUri == null) return;
        }
        OutputStream os = sAppContext.getContentResolver().openOutputStream(
                sCurrentLogUri, "wa");
        if (os != null) {
            byte[] bytes = text.getBytes("UTF-8");
            os.write(bytes);
            os.close();
            sCurrentFileSize += bytes.length;
            if (sCurrentFileSize > MAX_FILE_SIZE) {
                rotateLog();
            }
        }
    }

    private static void writeLegacy(String text) throws Exception {
        File logFile = getLegacyLogFile();
        if (!logFile.exists() && !logFile.createNewFile()) {
            android.util.Log.e(TAG, "Cannot create log file");
            return;
        }
        byte[] bytes = text.getBytes("UTF-8");
        FileOutputStream fos = new FileOutputStream(logFile, true);
        fos.write(bytes);
        fos.close();
        sCurrentFileSize = logFile.length();   // accurate after appending
        if (sCurrentFileSize > MAX_FILE_SIZE) {
            rotateLog();
        }
    }

    // ── Log rotation (delete old, create new with same name) ─────────────────

    private static void rotateLog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (sCurrentLogUri != null) {
                sAppContext.getContentResolver().delete(sCurrentLogUri, null, null);
            }
        } else {
            File oldFile = getLegacyLogFile();
            if (oldFile.exists()) oldFile.delete();
        }
        sCurrentLogUri = null;
        sCurrentFileSize = 0;
        openLogFile();   // creates a brand new file with the same static name
    }

    // ── Open a new log file (always the same name) ───────────────────────────

    private static void openLogFile() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage: insert a new file with the static name.
                // If an old file with the same name already exists (e.g. after a
                // rotation), we first delete it to avoid MediaStore renaming.
                deleteExistingScopedFile();

                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, LOG_FILE_NAME);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS);
                Uri uri = sAppContext.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    sCurrentLogUri = uri;
                    sCurrentFileSize = 0;
                } else {
                    android.util.Log.e(TAG, "Failed to create log file in Downloads");
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File logFile = new File(dir, LOG_FILE_NAME);
                if (!logFile.exists()) logFile.createNewFile();
                sCurrentLogUri = Uri.fromFile(logFile);
                sCurrentFileSize = logFile.length();
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "openLogFile failed", e);
        }
    }

    /**
     * Deletes any existing scoped‑storage file that matches our static name.
     * This ensures that after a rotation, the old file is gone and we can
     * re‑insert the same name without MediaStore appending a suffix.
     */
    private static void deleteExistingScopedFile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        Cursor cursor = null;
        try {
            cursor = sAppContext.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Downloads._ID},
                    MediaStore.Downloads.DISPLAY_NAME + " = ?",
                    new String[]{LOG_FILE_NAME},
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                Uri uri = Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.valueOf(id));
                sAppContext.getContentResolver().delete(uri, null, null);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error cleaning up old scoped log file", e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static File getLegacyLogFile() {
        File dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        return new File(dir, LOG_FILE_NAME);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String levelChar(int priority) {
        switch (priority) {
            case Log.VERBOSE: return "V";
            case Log.DEBUG:   return "D";
            case Log.INFO:    return "I";
            case Log.WARN:    return "W";
            case Log.ERROR:   return "E";
            case Log.ASSERT:  return "A";
            default:          return "?";
        }
    }

    private static String getStackTraceString(Throwable tr) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}