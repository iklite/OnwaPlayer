package com.ikechi.studio.onwa.player.listeners;

public interface SpectrumDataCaptureListener
{
	void onWaveFormDataCapture(byte[] waveform, int samplingRate);
	
	void onFftDataCapture(float[] spectrumData, int samplingRate);
}
