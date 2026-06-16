package com.ikechi.studio.onwa.player.utils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

/**
 * Audio capture class for recording and processing audio in real-time
 */
public class AudioCapture {

    // Audio configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // Processing
    private AudioRecord audioRecord;
    private FFTProcessor fftProcessor;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private int bufferSize;

    // Callback
    private VisualizerCallback callback;
    private Handler mainHandler;

    // Thread for audio processing
    private Thread captureThread;

    /**
     * Interface for receiving processed audio data
     */
    public interface VisualizerCallback {
        void onFrequencyBandsUpdated(float[] bands);
        void onRawAudioData(byte[] data);
        void onError(String message);
    }

    public AudioCapture() {
        // Calculate minimum buffer size
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        // Double buffer size for safety
        if (bufferSize != AudioRecord.ERROR && bufferSize != AudioRecord.ERROR_BAD_VALUE) {
            bufferSize *= 2;
        } else {
            bufferSize = 4096; // Fallback
        }

        // Initialize FFT processor
        fftProcessor = new FFTProcessor(SAMPLE_RATE, 1024);

        // Create handler for main thread callbacks
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Start audio capture
     */
    public boolean start(VisualizerCallback callback) {
        this.callback = callback;

        try {
            // Initialize AudioRecord
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                notifyError("Failed to initialize AudioRecord");
                return false;
            }

            // Start recording
            audioRecord.startRecording();
            isRecording = true;
            isPaused = false;

            // Start capture thread
            captureThread = new Thread(new CaptureRunnable());
            captureThread.start();

            return true;

        } catch (SecurityException e) {
            notifyError("Microphone permission required");
            return false;
        } catch (Exception e) {
            notifyError("Failed to start audio capture: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pause audio capture
     */
    public void pause() {
        isPaused = true;
    }

    /**
     * Resume audio capture
     */
    public void resume() {
        isPaused = false;
    }

    /**
     * Stop audio capture
     */
    public void stop() {
        isRecording = false;
        isPaused = false;

        if (captureThread != null) {
            try {
                captureThread.interrupt();
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }

        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                // Ignore release errors
            }
            audioRecord = null;
        }

        callback = null;
    }

    /**
     * Set FFT processor configuration
     */
    public void setFFTConfig(int fftSize, int numBands, int windowType) {
        if (fftProcessor != null) {
            fftProcessor.setFFTSize(fftSize);
            fftProcessor.setNumBands(numBands);
            fftProcessor.setWindowType(windowType);
        }
    }

    /**
     * Set FFT post-processing parameters
     */
    public void setFFTParams(float smoothing, float noiseFloor, float compression) {
        if (fftProcessor != null) {
            fftProcessor.setSmoothingFactor(smoothing);
            fftProcessor.setNoiseFloor(noiseFloor);
            fftProcessor.setCompressionFactor(compression);
        }
    }

    /**
     * Get current FFT processor
     */
    public FFTProcessor getFFTProcessor() {
        return fftProcessor;
    }

    /**
     * Check if recording
     */
    public boolean isRecording() {
        return isRecording && !isPaused;
    }

    /**
     * Get sample rate
     */
    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    /**
     * Get buffer size
     */
    public int getBufferSize() {
        return bufferSize;
    }

    private class CaptureRunnable implements Runnable {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

            byte[] buffer = new byte[bufferSize];

            while (isRecording) {
                if (isPaused) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }

                try {
                    // Read audio data
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                    if (bytesRead > 0) {
                        // Notify raw data if callback exists
                        if (callback != null) {
                            final byte[] dataCopy = new byte[bytesRead];
                            System.arraycopy(buffer, 0, dataCopy, 0, bytesRead);

                            mainHandler.post(new Runnable() {
									public void run() {
										if (callback != null) {
											callback.onRawAudioData(dataCopy);
										}
									}
								});
                        }

                        // Process with FFT
                        if (fftProcessor != null) {
                            final float[] bands = fftProcessor.process(buffer, bytesRead, false);

                            // Notify frequency bands on main thread
                            if (callback != null && bands != null) {
                                mainHandler.post(new Runnable() {
										public void run() {
											if (callback != null) {
												callback.onFrequencyBandsUpdated(bands);
											}
										}
									});
                            }
                        }
                    }

                } catch (Exception e) {
                    notifyError("Audio capture error: " + e.getMessage());
                    break;
                }
            }
        }
    }

    private void notifyError(final String message) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
					public void run() {
						if (callback != null) {
							callback.onError(message);
						}
					}
				});
        }
    }
}
