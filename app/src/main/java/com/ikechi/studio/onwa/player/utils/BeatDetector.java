package com.ikechi.studio.onwa.player.utils;

import android.util.Log;
import java.util.Arrays;

/**
 * Enhanced BeatDetector with multi-band detection and real audio analysis
 */
public class BeatDetector {

    private static final String TAG = "BeatDetector";

    // Detection bands
    private static final int BASS_BAND = 0;
    private static final int MID_BAND = 1;
    private static final int TREBLE_BAND = 2;

    // Default settings
    private static final float DEFAULT_THRESHOLD = 0.3f;
    private static final float DEFAULT_DECAY = 0.85f;
    private static final int HISTORY_SIZE = 43; // About 1 second at ~43 FPS

    // Energy history for each band
    private float[][] energyHistory = new float[3][HISTORY_SIZE];
    private float[] energyAverages = new float[3];
    private int historyIndex = 0;

    // Current detection state
    private boolean[] beatDetected = new boolean[3];
    private float[] beatIntensity = new float[3];
    private float overallBeatIntensity = 0.0f;
    private float phase = 0.0f;

    // Configuration
    private float sensitivity = 1.5f;
    private float threshold = DEFAULT_THRESHOLD;
    private float decay = DEFAULT_DECAY;
    private float bpm = 120.0f;

    // Statistical tracking
    private float[] energyVariance = new float[3];
    private int framesProcessed = 0;

    // Beat history for pattern recognition
    private float[] beatHistory = new float[60]; // ~1 second of beat history
    private int beatHistoryIndex = 0;
    private float averageBeatInterval = 0.5f; // Default 120 BPM

    public BeatDetector() {
        // Initialize arrays
        Arrays.fill(energyAverages, 0.0f);
        Arrays.fill(beatIntensity, 0.0f);
        Arrays.fill(beatHistory, 0.0f);

        // Initialize history arrays
        for (int i = 0; i < 3; i++) {
            Arrays.fill(energyHistory[i], 0.0f);
        }

        Log.d(TAG, "BeatDetector initialized with enhanced multi-band detection");
    }

    /**
     * Enhanced beat detection with multi-band analysis
     */
    public boolean detect(float[] spectrum) {
        if (spectrum == null || spectrum.length < 8) {
            return false;
        }

        // Split spectrum into bands
        float bassEnergy = calculateBandEnergy(spectrum, 0, spectrum.length / 8);     // 0-12.5%
        float midEnergy = calculateBandEnergy(spectrum, spectrum.length / 8, spectrum.length / 2); // 12.5-50%
        float trebleEnergy = calculateBandEnergy(spectrum, spectrum.length / 2, spectrum.length); // 50-100%

        // Store energies
        float[] currentEnergies = {bassEnergy, midEnergy, trebleEnergy};

        // Update history
        for (int i = 0; i < 3; i++) {
            energyHistory[i][historyIndex] = currentEnergies[i];
        }
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;

        // Update energy averages
        updateEnergyAverages();

        // Reset beat flags
        Arrays.fill(beatDetected, false);
        float maxIntensity = 0.0f;

        // Detect beats in each band
        for (int i = 0; i < 3; i++) {
            float currentEnergy = currentEnergies[i];
            float averageEnergy = energyAverages[i];

            // Calculate intensity (how much above average)
            float intensity = 0.0f;
            if (averageEnergy > 0.001f) {
                intensity = (currentEnergy - averageEnergy) / averageEnergy;
                intensity *= sensitivity;
            }

            // Check if beat is detected
            if (intensity > threshold) {
                beatDetected[i] = true;
                beatIntensity[i] = Math.min(intensity, 1.0f);

                if (intensity > maxIntensity) {
                    maxIntensity = intensity;
                }

                // Reset phase for visual effects
                if (i == BASS_BAND) {
                    phase = 0.0f;
                }
            } else {
                beatIntensity[i] *= decay;
            }
        }

        // Calculate overall beat intensity (weighted by band importance)
        overallBeatIntensity = 
            beatIntensity[BASS_BAND] * 0.5f + 
            beatIntensity[MID_BAND] * 0.3f + 
            beatIntensity[TREBLE_BAND] * 0.2f;

        // Update beat history for BPM calculation
        if (beatDetected[BASS_BAND]) {
            updateBeatHistory();
        }

        // Update frames processed
        framesProcessed++;

        return beatDetected[BASS_BAND] || beatDetected[MID_BAND];
    }

    /**
     * Calculate energy for a specific frequency band
     */
    private float calculateBandEnergy(float[] spectrum, int start, int end) {
        if (start >= end || start >= spectrum.length) {
            return 0.0f;
        }

        end = Math.min(end, spectrum.length);
        float energy = 0.0f;

        for (int i = start; i < end; i++) {
            float amplitude = Math.abs(spectrum[i]);
            energy += amplitude * amplitude;
        }

        return energy / (end - start);
    }

    /**
     * Update energy averages from history
     */
    private void updateEnergyAverages() {
        for (int band = 0; band < 3; band++) {
            float sum = 0.0f;
            for (int i = 0; i < HISTORY_SIZE; i++) {
                sum += energyHistory[band][i];
            }
            energyAverages[band] = sum / HISTORY_SIZE;

            // Calculate variance for dynamic thresholding
            if (framesProcessed > HISTORY_SIZE) {
                float variance = 0.0f;
                for (int i = 0; i < HISTORY_SIZE; i++) {
                    float diff = energyHistory[band][i] - energyAverages[band];
                    variance += diff * diff;
                }
                energyVariance[band] = variance / HISTORY_SIZE;
            }
        }
    }

    /**
     * Update beat history and calculate BPM
     */
    private void updateBeatHistory() {
        // Record current time (in frames)
        beatHistory[beatHistoryIndex] = framesProcessed;
        beatHistoryIndex = (beatHistoryIndex + 1) % beatHistory.length;

        // Calculate average beat interval
        int validBeats = 0;
        float totalInterval = 0.0f;

        for (int i = 1; i < beatHistory.length; i++) {
            int prevIdx = (beatHistoryIndex - i + beatHistory.length) % beatHistory.length;
            int currIdx = (beatHistoryIndex - i + 1 + beatHistory.length) % beatHistory.length;

            if (beatHistory[prevIdx] > 0 && beatHistory[currIdx] > 0) {
                float interval = beatHistory[prevIdx] - beatHistory[currIdx];
                if (interval > 10 && interval < 120) { // Valid interval range (0.5-6 seconds at 60 FPS)
                    totalInterval += interval;
                    validBeats++;
                }
            }
        }

        if (validBeats > 2) {
            averageBeatInterval = totalInterval / validBeats;
            bpm = (60.0f * 60.0f) / averageBeatInterval; // Convert frames to BPM (assuming 60 FPS)
            bpm = Math.max(60.0f, Math.min(180.0f, bpm)); // Clamp to reasonable range
        }
    }

    /**
     * Update animation phase based on BPM
     */
    public void updatePhase(float deltaTime) {
        phase += (bpm / 60.0f) * deltaTime;
        if (phase > 1.0f) {
            phase -= 1.0f;
        }
    }

    /**
     * Get specific band beat information
     */
    public boolean isBandBeatDetected(int band) {
        if (band >= 0 && band < 3) {
            return beatDetected[band];
        }
        return false;
    }

    public float getBandIntensity(int band) {
        if (band >= 0 && band < 3) {
            return beatIntensity[band];
        }
        return 0.0f;
    }

    // Getters and setters
    public boolean isBeatDetected() {
        return beatDetected[BASS_BAND] || beatDetected[MID_BAND];
    }

    public float getBeatIntensity() {
        return overallBeatIntensity;
    }

    public float getPhase() {
        return phase;
    }

    public float getBPM() {
        return bpm;
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0.1f, Math.min(5.0f, sensitivity));
    }

    public void setThreshold(float threshold) {
        this.threshold = Math.max(0.01f, Math.min(1.0f, threshold));
    }

    public void setDecay(float decay) {
        this.decay = Math.max(0.5f, Math.min(0.99f, decay));
    }

    public void reset() {
        historyIndex = 0;
        framesProcessed = 0;
        beatHistoryIndex = 0;
        overallBeatIntensity = 0.0f;
        Arrays.fill(beatDetected, false);
        Arrays.fill(beatIntensity, 0.0f);
        Arrays.fill(beatHistory, 0.0f);

        for (int i = 0; i < 3; i++) {
            Arrays.fill(energyHistory[i], 0.0f);
            energyAverages[i] = 0.0f;
        }
    }

    /**
     * Get debug information about current detection state
     */
    public String getDebugInfo() {
        return String.format(
            "BPM: %.1f | Bass: %.2f | Mid: %.2f | Treble: %.2f | Overall: %.2f",
            bpm, beatIntensity[BASS_BAND], beatIntensity[MID_BAND], beatIntensity[TREBLE_BAND], overallBeatIntensity
        );
    }
}
