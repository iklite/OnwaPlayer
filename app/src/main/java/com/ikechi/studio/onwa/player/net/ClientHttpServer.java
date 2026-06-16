package com.ikechi.studio.onwa.player.net;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * HTTP server that runs on a client when it wants to share its own media.
 * <p>
 * Accepts both legacy {@link File} and modern {@link Uri} (content:// or file://).
 * When a {@link Uri} is set (via {@link #clientSetMediaUri(Uri)}), it uses
 * {@link Context#getContentResolver()} to open the stream and query metadata.
 * Range requests (seeking) are supported for both types.
 * </p>
 */
public class ClientHttpServer {

    private static final String TAG = "ClientHttpServer";
    private static final int BUFFER_SIZE = 16384;
    private static final int ACCEPT_TIMEOUT_MS = 1000;
    private static final int CLIENT_TIMEOUT_MS = 30000;
    private static final int THREAD_POOL_SIZE = 4;
    private static final int SHUTDOWN_TIMEOUT_SEC = 3;

    private final int mPort;
    private final Context mContext;                  // needed for Uri access
    private volatile File mMediaFile;                // legacy file (nullable)
    private volatile Uri  mMediaUri;                 // modern URI (nullable)
    private ServerSocket mServerSocket;
    private volatile boolean mRunning;
    private boolean mPreparedListenerCalled = false;
    private ExecutorService mExecutor;
    private Thread mAcceptThread;
    private OnServerPreparedListener mPreparedListener;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Creates a server on the given port, <b>without</b> a {@link Context}.
     * Use this constructor only when you plan to serve via {@link #clientSetMediaFile(File)}.
     * For {@link #clientSetMediaUri(Uri)} you must use {@link #ClientHttpServer(int, Context)}.
     */
    public ClientHttpServer(int port) {
        this(port, null);
    }

    /**
     * Creates a server on the given port with a {@link Context} for Uri access.
     *
     * @param port    The TCP port to listen on.
     * @param context Application context (for ContentResolver); may be {@code null}
     *                if you never call {@link #clientSetMediaUri(Uri)}.
     */
    public ClientHttpServer(int port, Context context) {
        mPort    = port;
        mContext = context != null ? context.getApplicationContext() : null;
    }

    // ── Media setters ────────────────────────────────────────────────────────

    /** @deprecated Use {@link #clientSetMediaUri(Uri)} for better scoped‑storage compatibility. */
    @Deprecated
    public synchronized void clientSetMediaFile(File file) {
        mMediaFile = file;
        mMediaUri  = null;   // clear Uri so file takes precedence
        Log.d(TAG, "Media file set: " + (file != null ? file.getName() : "null"));
    }

    /**
     * Sets the media {@link Uri} to be served.
     * <p><b>Important:</b> You must have created the server with
     * {@link #ClientHttpServer(int, Context)} before calling this method.</p>
     *
     * @param uri A content:// or file:// Uri pointing to the media.
     * @throws IllegalStateException if no Context is available.
     */
    public synchronized void clientSetMediaUri(Uri uri) {
        if (uri != null && mContext == null) {
            throw new IllegalStateException(
                "ClientHttpServer must be constructed with a Context to serve Uri. " +
                "Use ClientHttpServer(port, context).");
        }
        mMediaUri  = uri;
        mMediaFile = null;   // clear file so Uri takes precedence
        Log.d(TAG, "Media Uri set: " + (uri != null ? uri.toString() : "null"));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public synchronized void clientStart() throws IOException {
        if (mRunning) {
            Log.w(TAG, "Server already running");
            return;
        }

        mServerSocket = new ServerSocket(mPort);
        mServerSocket.setReuseAddress(true);
        mServerSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);
        mRunning = true;
        mExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        mAcceptThread = new Thread(new Runnable() {
				@Override
				public void run() {
					Log.d(TAG, "ClientHttpServer started on port " + mPort);
					while (mRunning) {
						try {
							final Socket client = mServerSocket.accept();
							if (client != null && mRunning) {
								client.setSoTimeout(CLIENT_TIMEOUT_MS);
								mExecutor.submit(new Runnable() {
										@Override
										public void run() {
											clientHandleClient(client);
											// Inform listener after first successful call to handleClient
											if (!mPreparedListenerCalled && mPreparedListener != null) {
												mPreparedListener.onPrepared();
												mPreparedListenerCalled = true;
											}
										}
									});
							}
						} catch (SocketTimeoutException ignored) {
						} catch (IOException e) {
							if (mRunning) Log.e(TAG, "accept() failed", e);
						}
					}
					Log.d(TAG, "Accept loop ended");
				}
			}, "ClientHttpAccept");
        mAcceptThread.start();
        Log.d(TAG, "ClientHttpServer started successfully");
    }

    public synchronized void clientStop() {
        if (!mRunning) return;
        Log.d(TAG, "Stopping ClientHttpServer");
        mRunning = false;

        try {
            if (mServerSocket != null) {
                mServerSocket.close();
                mServerSocket = null;
            }
        } catch (IOException e) {
            Log.w(TAG, "Error closing server socket", e);
        }

        if (mAcceptThread != null) {
            try {
                mAcceptThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mAcceptThread = null;
        }

        if (mExecutor != null) {
            mExecutor.shutdownNow();
            try {
                if (!mExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mExecutor = null;
        }
        mPreparedListenerCalled = false;
        Log.d(TAG, "ClientHttpServer stopped");
    }

    public boolean clientIsRunning() {
        return mRunning;
    }

    // ── Request handler ───────────────────────────────────────────────────────

    private void clientHandleClient(Socket client) {
        InputStream in = null;
        OutputStream out = null;
        Object closeable = null;   // will be either FileInputStream or AssetFileDescriptor

        try {
            in = client.getInputStream();
            out = client.getOutputStream();

            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, "UTF-8"));
            String requestLine = reader.readLine();
            if (requestLine == null) return;

            boolean isHead = requestLine.startsWith("HEAD ");
            boolean isGet  = requestLine.startsWith("GET ");
            if (!isGet && !isHead) {
                clientSendError(out, 405, "Method Not Allowed");
                return;
            }

            // Parse Range header
            long rangeStart = -1, rangeEnd = -1;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("range:")) {
                    String rangeVal = line.substring(6).trim();
                    if (rangeVal.startsWith("bytes=")) {
                        String[] parts = rangeVal.substring(6).split("-", 2);
                        try {
                            if (parts.length > 0 && !parts[0].isEmpty())
                                rangeStart = Long.parseLong(parts[0].trim());
                            if (parts.length > 1 && !parts[1].isEmpty())
                                rangeEnd = Long.parseLong(parts[1].trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // ── Determine media source and metadata ───────────────────────────
            long   fileLen  = 0;
            String fileName = "media";

            if (mMediaUri != null) {
                // ── Modern Uri path ───────────────────────────────────────────
                Uri uri = mMediaUri;
                fileName = uri.getLastPathSegment();
                if (fileName == null) fileName = "media";

                AssetFileDescriptor afd = mContext.getContentResolver()
                    .openAssetFileDescriptor(uri, "r");
                if (afd == null) { clientSendError(out, 404, "Not Found"); return; }
                closeable = afd;

                fileLen = afd.getLength();
                if (fileLen == AssetFileDescriptor.UNKNOWN_LENGTH || fileLen <= 0) {
                    clientSendError(out, 204, "No Content");
                    afd.close();
                    return;
                }

                InputStream mediaStream = afd.createInputStream();

                // Range validation
                boolean isRangeRequest = (rangeStart >= 0);
                long serveFrom, serveTo;
                if (isRangeRequest) {
                    serveFrom = Math.max(0, rangeStart);
                    serveTo   = (rangeEnd >= 0) ? Math.min(rangeEnd, fileLen - 1) : (fileLen - 1);
                    if (serveFrom > serveTo || serveFrom >= fileLen) {
                        clientSendError(out, 416, "Range Not Satisfiable");
                        afd.close();
                        return;
                    }
                    // Skip to range start
                    if (serveFrom > 0) {
                        long skipped = mediaStream.skip(serveFrom);
                        if (skipped != serveFrom) {
                            Log.w(TAG, "Could not skip to requested position");
                        }
                    }
                } else {
                    serveFrom = 0;
                    serveTo   = fileLen - 1;
                }
                long contentLength = serveTo - serveFrom + 1;

                // Write headers
                String mime = clientGetMimeType(fileName);
                StringBuilder headers = new StringBuilder(256);
                if (isRangeRequest) {
                    headers.append("HTTP/1.1 206 Partial Content\r\n");
                    headers.append("Content-Range: bytes ")
                        .append(serveFrom).append('-').append(serveTo)
                        .append('/').append(fileLen).append("\r\n");
                } else {
                    headers.append("HTTP/1.1 200 OK\r\n");
                }
                headers.append("Content-Type: ").append(mime).append("\r\n");
                headers.append("Content-Length: ").append(contentLength).append("\r\n");
                headers.append("Accept-Ranges: bytes\r\n");
                headers.append("Connection: close\r\n\r\n");
                out.write(headers.toString().getBytes("UTF-8"));
                out.flush();

                if (!isHead) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    long remaining = contentLength;
                    int n;
                    while (mRunning && remaining > 0) {
                        int toRead = (int) Math.min(buf.length, remaining);
                        n = mediaStream.read(buf, 0, toRead);
                        if (n < 0) break;
                        out.write(buf, 0, n);
                        remaining -= n;
                    }
                    out.flush();
                }
                Log.d(TAG, "Served (Uri): " + fileName +
                      (isRangeRequest ? " [range " + serveFrom + "-" + serveTo + "]" : " [full]"));

            } else if (mMediaFile != null) {
                // ── Legacy File path ───────────────────────────────────────────
                File file = mMediaFile;
                fileName = file.getName();
                if (!file.exists() || !file.canRead()) {
                    clientSendError(out, 404, "Not Found");
                    return;
                }

                fileLen = file.length();
                if (fileLen == 0) {
                    clientSendError(out, 204, "No Content");
                    return;
                }

                boolean isRangeRequest = (rangeStart >= 0);
                long serveFrom, serveTo;
                if (isRangeRequest) {
                    serveFrom = Math.max(0, rangeStart);
                    serveTo   = (rangeEnd >= 0) ? Math.min(rangeEnd, fileLen - 1) : (fileLen - 1);
                    if (serveFrom > serveTo || serveFrom >= fileLen) {
                        clientSendError(out, 416, "Range Not Satisfiable");
                        return;
                    }
                } else {
                    serveFrom = 0;
                    serveTo   = fileLen - 1;
                }
                long contentLength = serveTo - serveFrom + 1;

                String mime = clientGetMimeType(fileName);
                StringBuilder headers = new StringBuilder(256);
                if (isRangeRequest) {
                    headers.append("HTTP/1.1 206 Partial Content\r\n");
                    headers.append("Content-Range: bytes ")
                        .append(serveFrom).append('-').append(serveTo)
                        .append('/').append(fileLen).append("\r\n");
                } else {
                    headers.append("HTTP/1.1 200 OK\r\n");
                }
                headers.append("Content-Type: ").append(mime).append("\r\n");
                headers.append("Content-Length: ").append(contentLength).append("\r\n");
                headers.append("Accept-Ranges: bytes\r\n");
                headers.append("Connection: close\r\n\r\n");
                out.write(headers.toString().getBytes("UTF-8"));
                out.flush();

                if (!isHead) {
                    FileInputStream fis = new FileInputStream(file);
                    closeable = fis;
                    if (serveFrom > 0) {
                        long skipped = fis.skip(serveFrom);
                        if (skipped != serveFrom) {
                            Log.w(TAG, "Could not skip to requested position");
                        }
                    }

                    byte[] buf = new byte[BUFFER_SIZE];
                    long remaining = contentLength;
                    int n;
                    while (mRunning && remaining > 0) {
                        int toRead = (int) Math.min(buf.length, remaining);
                        n = fis.read(buf, 0, toRead);
                        if (n < 0) break;
                        out.write(buf, 0, n);
                        remaining -= n;
                    }
                    out.flush();
                }

                Log.d(TAG, "Served (File): " + fileName +
                      (isRangeRequest ? " [range]" : " [full]"));

            } else {
                clientSendError(out, 404, "No media set");
            }

        } catch (IOException e) {
            if (mRunning) Log.e(TAG, "Error handling client", e);
        } finally {
            // Close everything
            try { if (closeable instanceof AssetFileDescriptor) ((AssetFileDescriptor) closeable).close(); }
            catch (IOException ignored) {}
            try { if (closeable instanceof FileInputStream) ((FileInputStream) closeable).close(); }
            catch (IOException ignored) {}
            try { if (out != null) out.close(); } catch (IOException ignored) {}
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            try { if (!client.isClosed()) client.close(); } catch (IOException ignored) {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clientSendError(OutputStream out, int code, String message) {
        try {
            String body = "<html><body><h1>" + code + " " + message + "</h1></body></html>";
            String response = "HTTP/1.1 " + code + " " + message + "\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "Connection: close\r\n\r\n"
                + body;
            out.write(response.getBytes("UTF-8"));
            out.flush();
        } catch (IOException ignored) {}
    }

    private String clientGetMimeType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    public void setOnPreparedListener(OnServerPreparedListener listener) {
        mPreparedListener = listener;
    }

    public interface OnServerPreparedListener {
        void onPrepared();
    }
}
