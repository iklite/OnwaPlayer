package com.ikechi.studio.onwa.player.utils;

import java.util.Arrays;

/**
 * Fast Fourier Transform (FFT) processor for audio visualization
 * Optimized for real-time audio processing
 */
public class FFTProcessor {

    // Constants
    private static final float TWO_PI = (float) (2.0 * Math.PI);
    private static final int MIN_FFT_SIZE = 64;
    private static final int MAX_FFT_SIZE = 8192;

    // Configuration
    private int fftSize;
    private int sampleRate;
    private int numBands;
    private int bandScaleType;

    // Cached frequency values that depend on sample rate
    private float frequencyResolution;
    private float nyquistFrequency;

    // Window types
    public static final int WINDOW_HANN = 0;
    public static final int WINDOW_HAMMING = 1;
    public static final int WINDOW_BLACKMAN = 2;
    public static final int WINDOW_NONE = 3;

    // Band scale types
    public static final int SCALE_LINEAR = 0;
    public static final int SCALE_LOGARITHMIC = 1;
    public static final int SCALE_MEL = 2;

    // Processing buffers
    private float[] window;
    private float[] realPart;
    private float[] imagPart;
    private float[] magnitude;
    private float[] previousMagnitude;
    private float[] bands;

    // Precomputed values
    private float[] cosTable;
    private float[] sinTable;
    private int[] bitReverseTable;
    private int logN;

    // Filter coefficients
    private float smoothingFactor = 0.3f;
    private float noiseFloor = 0.001f;
    private float compressionFactor = 0.5f;

    // Statistics
    private float averageMagnitude = 0;
    private float peakMagnitude = 0;
    private float dynamicRange = 60.0f; // dB

    /**
     * Constructor with default FFT size 1024
     * @param sampleRate Audio sample rate in Hz
     */
    public FFTProcessor(int sampleRate) {
        this(sampleRate, 1024);
    }

    /**
     * Constructor with custom FFT size
     * @param sampleRate Audio sample rate in Hz
     * @param fftSize FFT size (must be power of 2)
     */
    public FFTProcessor(int sampleRate, int fftSize) {
        setSamplingRate(sampleRate); // Use setter to ensure proper initialization
        setFFTSize(fftSize);
        setWindowType(WINDOW_HANN);
        setBandScaleType(SCALE_LOGARITHMIC);
        setNumBands(64);
    }

    /**
     * Set sampling rate and update all dependent calculations
     * @param rate Sampling rate in Hz (must be positive)
     */
    public synchronized void setSamplingRate(int rate) {
        if (rate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }

        if (rate == sampleRate) {
            return; // No change needed
        }

        this.sampleRate = rate;

        // Recalculate cached frequency values
        recalculateFrequencyValues();
    }

    /**
     * Recalculate all values that depend on sample rate and FFT size
     */
    private void recalculateFrequencyValues() {
        // Update cached frequency calculations
        frequencyResolution = (float) sampleRate / fftSize;
        nyquistFrequency = (float) sampleRate / 2;

        // Note: FFT tables (cosTable, sinTable) do NOT depend on sample rate,
        // only on FFT size, so they don't need to be recalculated here
    }

    /**
     * Set FFT size (must be power of 2)
     */
    public void setFFTSize(int size) {
        // Find next power of 2
        int power = 1;
        while (power < size && power < MAX_FFT_SIZE) {
            power <<= 1;
        }

        // Ensure within bounds
        if (power < MIN_FFT_SIZE) power = MIN_FFT_SIZE;
        if (power > MAX_FFT_SIZE) power = MAX_FFT_SIZE;

        if (power != fftSize) {
            fftSize = power;
            initializeTables();
            allocateBuffers();
            recalculateFrequencyValues(); // This depends on both sample rate AND FFT size
        }
    }

    private void initializeTables() {
        // Calculate log2 of FFT size
        logN = 0;
        int n = fftSize;
        while (n > 1) {
            n >>= 1;
            logN++;
        }

        // Initialize bit reversal table
        bitReverseTable = new int[fftSize];
        for (int i = 0; i < fftSize; i++) {
            bitReverseTable[i] = reverseBits(i, logN);
        }

        // Initialize trig tables for FFT
        cosTable = new float[fftSize];
        sinTable = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            float angle = -TWO_PI * i / fftSize;
            cosTable[i] = (float) Math.cos(angle);
            sinTable[i] = (float) Math.sin(angle);
        }
    }

    private void allocateBuffers() {
        realPart = new float[fftSize];
        imagPart = new float[fftSize];
        magnitude = new float[fftSize / 2]; // Only need half for real FFT
        previousMagnitude = new float[fftSize / 2];
        bands = new float[numBands];
        window = new float[fftSize];
    }

    /**
     * Set window function for FFT
     * @param windowType Window type constant
     */
    public void setWindowType(int windowType) {
        switch (windowType) {
            case WINDOW_HANN:
                applyHannWindow();
                break;
            case WINDOW_HAMMING:
                applyHammingWindow();
                break;
            case WINDOW_BLACKMAN:
                applyBlackmanWindow();
                break;
            case WINDOW_NONE:
                applyRectangularWindow();
                break;
        }
    }

    private void applyHannWindow() {
        for (int i = 0; i < fftSize; i++) {
            window[i] = 0.5f * (1.0f - (float) Math.cos(TWO_PI * i / (fftSize - 1)));
        }
    }

    private void applyHammingWindow() {
        for (int i = 0; i < fftSize; i++) {
            window[i] = 0.54f - 0.46f * (float) Math.cos(TWO_PI * i / (fftSize - 1));
        }
    }

    private void applyBlackmanWindow() {
        for (int i = 0; i < fftSize; i++) {
            float a0 = 0.42659f;
            float a1 = 0.49656f;
            float a2 = 0.076849f;
            window[i] = a0 - a1 * (float) Math.cos(TWO_PI * i / (fftSize - 1)) 
				+ a2 * (float) Math.cos(2 * TWO_PI * i / (fftSize - 1));
        }
    }

    private void applyRectangularWindow() {
        Arrays.fill(window, 1.0f);
    }

    /**
     * Set number of frequency bands for output
     */
    public void setNumBands(int bands) {
        numBands = Math.max(8, Math.min(256, bands));
        this.bands = new float[numBands];
    }

    /**
     * Set frequency band scaling type
     */
    public void setBandScaleType(int scaleType) {
        bandScaleType = scaleType;
    }

    /**
     * Process raw audio samples (16-bit PCM)
     * @param samples Audio samples (16-bit PCM, interleaved if stereo)
     * @param numSamples Number of samples to process
     * @param isStereo Whether audio is stereo
     * @return Processed frequency bands
     */
    public synchronized float[] process(byte[] samples, int numSamples, boolean isStereo) {
        if (samples == null || numSamples == 0) {
            return getFallbackBands();
        }

        // Convert bytes to float and apply window
        convertToFloat(samples, numSamples, isStereo);

        // Perform FFT
        performFFT();

        // Calculate magnitudes
        calculateMagnitudes();

        // Apply post-processing
        postProcessMagnitudes();

        // Convert to frequency bands
        convertToBands();

        return bands;
    }

    /**
     * Process float audio samples (already normalized to -1 to 1)
     * @param samples Float audio samples
     * @return Processed frequency bands
     */
    public synchronized float[] process(float[] samples) {
        if (samples == null || samples.length == 0) {
            return getFallbackBands();
        }

        // Copy samples to real part (with windowing)
        int copyLength = Math.min(samples.length, fftSize);
        for (int i = 0; i < copyLength; i++) {
            realPart[i] = samples[i] * window[i];
            imagPart[i] = 0;
        }

        // Zero pad if needed
        for (int i = copyLength; i < fftSize; i++) {
            realPart[i] = 0;
            imagPart[i] = 0;
        }

        // Perform FFT
        performFFT();

        // Calculate magnitudes
        calculateMagnitudes();

        // Apply post-processing
        postProcessMagnitudes();

        // Convert to frequency bands
        convertToBands();

        return bands;
    }

    private void convertToFloat(byte[] samples, int numSamples, boolean isStereo) {
		int stride = isStereo ? 4 : 2; // 16-bit PCM
		int fftIndex = 0;

		for (int i = 0;
			 i + 1 < samples.length && fftIndex < fftSize;
		i += stride) {

			int lo = samples[i] & 0xFF;
			int hi = samples[i + 1] & 0xFF;
			int sample = (hi << 8) | lo;

			realPart[fftIndex] = (sample / 32768.0f) * window[fftIndex];
			imagPart[fftIndex] = 0f;
			fftIndex++;
		}

		// Zero pad
		for (int i = fftIndex; i < fftSize; i++) {
			realPart[i] = 0f;
			imagPart[i] = 0f;
		}
	}

    /**
     * Perform in-place FFT using Cooley-Tukey algorithm
     */
    private void performFFT() {
        // Bit reversal permutation
        for (int i = 0; i < fftSize; i++) {
            int j = bitReverseTable[i];
            if (i < j) {
                // Swap real parts
                float temp = realPart[i];
                realPart[i] = realPart[j];
                realPart[j] = temp;

                // Swap imaginary parts
                temp = imagPart[i];
                imagPart[i] = imagPart[j];
                imagPart[j] = temp;
            }
        }

        // FFT computation
        for (int stage = 1; stage <= logN; stage++) {
            int blockSize = 1 << stage;
            int halfBlock = blockSize >> 1;

            for (int block = 0; block < fftSize; block += blockSize) {
                for (int k = 0; k < halfBlock; k++) {
                    int evenIndex = block + k;
                    int oddIndex = evenIndex + halfBlock;

                    // Twiddle factor
                    int twiddleIndex = k * (fftSize >> stage);
                    float cosTheta = cosTable[twiddleIndex];
                    float sinTheta = sinTable[twiddleIndex];

                    // Butterfly operation
                    float tReal = cosTheta * realPart[oddIndex] - sinTheta * imagPart[oddIndex];
                    float tImag = sinTheta * realPart[oddIndex] + cosTheta * imagPart[oddIndex];

                    // Update odd element
                    realPart[oddIndex] = realPart[evenIndex] - tReal;
                    imagPart[oddIndex] = imagPart[evenIndex] - tImag;

                    // Update even element
                    realPart[evenIndex] += tReal;
                    imagPart[evenIndex] += tImag;
                }
            }
        }
    }

    private void calculateMagnitudes() {
        // Only need first half (Nyquist frequency)
        averageMagnitude = 0;
        peakMagnitude = 0;

        for (int i = 0; i < magnitude.length; i++) {
            float real = realPart[i];
            float imag = imagPart[i];

            // Calculate magnitude (sqrt(real² + imag²))
            // Using approximation for speed: sqrt(x² + y²) ≈ max(|x|, |y|) + 0.5*min(|x|, |y|)
            float absReal = Math.abs(real);
            float absImag = Math.abs(imag);
            float max = Math.max(absReal, absImag);
            float min = Math.min(absReal, absImag);

            magnitude[i] = max + 0.5f * min;

            // Update statistics
            averageMagnitude += magnitude[i];
            if (magnitude[i] > peakMagnitude) {
                peakMagnitude = magnitude[i];
            }
        }

        averageMagnitude /= magnitude.length;
    }

    private void postProcessMagnitudes() {
        // Apply noise floor and compression
        for (int i = 0; i < magnitude.length; i++) {
            // Apply noise floor
            magnitude[i] = Math.max(magnitude[i], noiseFloor);

            // Logarithmic compression (simulating dB scale)
            if (compressionFactor > 0) {
                magnitude[i] = (float) Math.log1p(magnitude[i] * 10) * compressionFactor;
            }

            // Smoothing with previous frame
            if (smoothingFactor > 0) {
                magnitude[i] = magnitude[i] * (1 - smoothingFactor) + previousMagnitude[i] * smoothingFactor;
                previousMagnitude[i] = magnitude[i];
            }

            // Normalize based on dynamic range
            if (peakMagnitude > 0) {
                magnitude[i] /= peakMagnitude;
            }

            // Clamp to 0-1 range
            magnitude[i] = Math.max(0, Math.min(1, magnitude[i]));
        }
    }

    private void convertToBands() {
        switch (bandScaleType) {
            case SCALE_LOGARITHMIC:
                convertToLogBands();
                break;
            case SCALE_MEL:
                convertToMelBands();
                break;
            default: // SCALE_LINEAR
                convertToLinearBands();
                break;
        }
    }

    private void convertToLinearBands() {
        int bandWidth = magnitude.length / numBands;

        for (int band = 0; band < numBands; band++) {
            float sum = 0;
            int start = band * bandWidth;
            int end = Math.min(start + bandWidth, magnitude.length);

            for (int i = start; i < end; i++) {
                sum += magnitude[i];
            }

            bands[band] = sum / (end - start);
        }
    }

    private void convertToLogBands() {
        // Logarithmic frequency distribution (more bands at low frequencies)
        float minFreq = 20.0f; // Hz
        float maxFreq = nyquistFrequency; // Use cached value - CRITICAL FIX!

        // Create logarithmic bins
        float logMin = (float) Math.log10(minFreq);
        float logMax = (float) Math.log10(maxFreq);
        float logRange = logMax - logMin;

        // Frequency per FFT bin - use cached frequencyResolution
        float binFreqWidth = frequencyResolution;

        for (int band = 0; band < numBands; band++) {
            // Calculate frequency range for this band
            float bandLogMin = logMin + (logRange * band / numBands);
            float bandLogMax = logMin + (logRange * (band + 1) / numBands);

            float bandFreqMin = (float) Math.pow(10, bandLogMin);
            float bandFreqMax = (float) Math.pow(10, bandLogMax);

            // Convert to bin indices
            int binStart = (int) (bandFreqMin / binFreqWidth);
            int binEnd = (int) (bandFreqMax / binFreqWidth);

            binStart = Math.max(0, Math.min(binStart, magnitude.length - 1));
            binEnd = Math.max(binStart + 1, Math.min(binEnd, magnitude.length - 1));

            // Average magnitude in this band
            float sum = 0;
            for (int i = binStart; i < binEnd; i++) {
                sum += magnitude[i];
            }

            bands[band] = sum / (binEnd - binStart);
        }
    }

    private void convertToMelBands() {
        // Mel scale distribution (perceptual frequency scale)
        float minMel = hzToMel(20.0f);
        float maxMel = hzToMel(nyquistFrequency); // Use cached value - CRITICAL FIX!
        float melWidth = (maxMel - minMel) / numBands;

        // Frequency per FFT bin - use cached frequencyResolution
        float binFreqWidth = frequencyResolution;

        for (int band = 0; band < numBands; band++) {
            float melStart = minMel + (melWidth * band);
            float melEnd = melStart + melWidth;

            float hzStart = melToHz(melStart);
            float hzEnd = melToHz(melEnd);

            int binStart = (int) (hzStart / binFreqWidth);
            int binEnd = (int) (hzEnd / binFreqWidth);

            binStart = Math.max(0, Math.min(binStart, magnitude.length - 1));
            binEnd = Math.max(binStart + 1, Math.min(binEnd, magnitude.length - 1));

            float sum = 0;
            for (int i = binStart; i < binEnd; i++) {
                sum += magnitude[i];
            }

            bands[band] = sum / (binEnd - binStart);
        }
    }

    private float[] getFallbackBands() {
        // Return random/fallback data when no audio
        for (int i = 0; i < numBands; i++) {
            bands[i] = (float) (Math.random() * 0.1);
        }
        return bands;
    }

    /**
     * Convert Hz to Mel scale
     */
    private float hzToMel(float hz) {
        return 2595.0f * (float) Math.log10(1 + hz / 700.0f);
    }

    /**
     * Convert Mel to Hz scale
     */
    private float melToHz(float mel) {
        return 700.0f * ((float) Math.pow(10, mel / 2595.0f) - 1);
    }

    /**
     * Reverse bits for FFT bit reversal
     */
    private int reverseBits(int n, int bits) {
        int reversed = 0;
        for (int i = 0; i < bits; i++) {
            reversed = (reversed << 1) | (n & 1);
            n >>= 1;
        }
        return reversed;
    }

    /**
     * Get current FFT size
     */
    public int getFFTSize() {
        return fftSize;
    }

    /**
     * Get number of output bands
     */
    public int getNumBands() {
        return numBands;
    }

    /**
     * Get frequency resolution (Hz per bin)
     */
    public float getFrequencyResolution() {
        return frequencyResolution; // Return cached value
    }

    /**
     * Get Nyquist frequency (maximum detectable frequency)
     */
    public float getNyquistFrequency() {
        return nyquistFrequency; // Return cached value
    }

    /**
     * Get current sampling rate
     */
    public int getSamplingRate() {
        return sampleRate;
    }

    /**
     * Set smoothing factor (0 = no smoothing, 1 = maximum smoothing)
     */
    public void setSmoothingFactor(float factor) {
        smoothingFactor = Math.max(0, Math.min(1, factor));
    }

    /**
     * Set noise floor (minimum magnitude)
     */
    public void setNoiseFloor(float floor) {
        noiseFloor = Math.max(0, floor);
    }

    /**
     * Set compression factor (0 = linear, >0 = logarithmic)
     */
    public void setCompressionFactor(float factor) {
        compressionFactor = Math.max(0, Math.min(2, factor));
    }

    /**
     * Set dynamic range in decibels
     */
    public void setDynamicRange(float range) {
        dynamicRange = Math.max(20, Math.min(120, range));
    }

    /**
     * Reset all buffers and statistics
     */
    public void reset() {
        if (realPart != null) Arrays.fill(realPart, 0);
        if (imagPart != null) Arrays.fill(imagPart, 0);
        if (magnitude != null) Arrays.fill(magnitude, 0);
        if (previousMagnitude != null) Arrays.fill(previousMagnitude, 0);
        if (bands != null) Arrays.fill(bands, 0);

        averageMagnitude = 0;
        peakMagnitude = 0;
    }

    /**
     * Get raw magnitude spectrum (for advanced processing)
     */
    public float[] getMagnitudeSpectrum() {
        return magnitude.clone();
    }

    /**
     * Get frequency for a specific bin
     */
    public float getFrequencyForBin(int bin) {
        if (bin < 0 || bin >= magnitude.length) return 0;
        return bin * frequencyResolution; // Use cached value
    }

    /**
     * Find peak frequency in the spectrum
     */
    public float findPeakFrequency() {
        if (magnitude == null || magnitude.length == 0) return 0;

        int peakBin = 0;
        float peakValue = 0;

        for (int i = 0; i < magnitude.length; i++) {
            if (magnitude[i] > peakValue) {
                peakValue = magnitude[i];
                peakBin = i;
            }
        }

        return getFrequencyForBin(peakBin);
    }

    /**
     * Calculate spectral centroid (brightness measure)
     */
    public float calculateSpectralCentroid() {
        if (magnitude == null || magnitude.length == 0) return 0;

        float weightedSum = 0;
        float totalMagnitude = 0;

        for (int i = 0; i < magnitude.length; i++) {
            weightedSum += magnitude[i] * i;
            totalMagnitude += magnitude[i];
        }

        if (totalMagnitude > 0) {
            return (weightedSum / totalMagnitude) * frequencyResolution; // Use cached value
        }
        return 0;
    }
}
