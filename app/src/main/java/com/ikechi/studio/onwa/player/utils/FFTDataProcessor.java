package com.ikechi.studio.onwa.player.utils;

import android.util.Log;

/**
 * Processes raw FFT data from Android Visualizer.
 * Converts byte array to normalized band magnitudes with optional smoothing.
 */
public class FFTDataProcessor
{

    private static final String TAG = "FFTDataProcessor";
    private static final int DEFAULT_NUM_BANDS = 64;
    private static final float DEFAULT_SMOOTHING = 0.3f; // exponential smoothing factor

    private final int numBands;
    private final float smoothingFactor;
    private float[] smoothedBands;
    private boolean firstFrame = true;

    public FFTDataProcessor()
	{
        this(DEFAULT_NUM_BANDS, DEFAULT_SMOOTHING);
    }

    public FFTDataProcessor(int numBands, float smoothingFactor)
	{
        this.numBands = numBands;
        this.smoothingFactor = Math.max(0, Math.min(1, smoothingFactor));
        this.smoothedBands = new float[numBands];
    }

    /**
     * Process raw FFT bytes and return normalized band magnitudes.
     * @param fftBytes Raw FFT data from Visualizer (size = captureSize/2 + 1)
     * @param captureSize The capture size used by Visualizer (must be known)
     * @return float array of length numBands with values 0-1
     */
    public float[] process(byte[] fftBytes, int captureSize)
	{
        if (fftBytes == null || fftBytes.length < 2)
		{
            return getFallbackBands();
        }

        // Convert to magnitude spectrum (real FFT yields pairs: real, imag)
        int spectrumSize = fftBytes.length / 2; // number of frequency bins
        float[] magnitudes = new float[spectrumSize];

        for (int i = 0; i < spectrumSize; i++)
		{
            byte realByte = fftBytes[2 * i];
            byte imagByte = fftBytes[2 * i + 1];
            short real = (short) ((realByte & 0xFF) | 0);
            short imag = (short) ((imagByte & 0xFF) | 0);
            float magnitude = (float) Math.sqrt(real * real + imag * imag);
            // Normalize roughly (depends on capture size, but we'll normalize per band)
            magnitudes[i] = magnitude;
        }

        // Reduce to desired number of bands (simple averaging)
        float[] bands = new float[numBands];
        int binsPerBand = spectrumSize / numBands;
        if (binsPerBand == 0) binsPerBand = 1;

        for (int band = 0; band < numBands; band++)
		{
            int start = band * binsPerBand;
            int end = Math.min(start + binsPerBand, spectrumSize);
            float sum = 0;
            for (int j = start; j < end; j++)
			{
                sum += magnitudes[j];
            }
            bands[band] = sum / (end - start);
        }

        // Normalize bands to 0-1 range
        float max = 0;
        for (float v : bands)
		{
            if (v > max) max = v;
        }
        if (max > 0)
		{
            for (int i = 0; i < numBands; i++)
			{
                bands[i] /= max;
            }
        }

        // Apply exponential smoothing
        if (firstFrame)
		{
            System.arraycopy(bands, 0, smoothedBands, 0, numBands);
            firstFrame = false;
        }
		else
		{
            for (int i = 0; i < numBands; i++)
			{
                smoothedBands[i] = smoothedBands[i] * smoothingFactor + bands[i] * (1 - smoothingFactor);
            }
        }

        return smoothedBands.clone();
    }

    private float[] getFallbackBands()
	{
        float[] fallback = new float[numBands];
        for (int i = 0; i < numBands; i++)
		{
            fallback[i] = (float) Math.random() * 0.1f;
        }
        return fallback;
    }

    public void reset()
	{
        firstFrame = true;
    }
}
