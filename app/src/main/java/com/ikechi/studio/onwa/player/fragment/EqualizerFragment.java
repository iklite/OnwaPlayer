package com.ikechi.studio.onwa.player.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.utils.PrefUtils;

import java.util.Locale;

public class EqualizerFragment extends Fragment {

    private static final String TAG = "EqualizerFragment";

    private Equalizer equalizer;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private PresetReverb reverb;

    private LinearLayout equalizerContainer;

    private Slider bassBoostSlider;
    private Slider virtualizerSlider;
    private Slider reverbSlider;

    private ChipGroup presetChipGroup;

    private MaterialToolbar toolbar;

    private View btnReset;
    private View btnApply;

    private int audioSessionId = 0;

    private BroadcastReceiver sessionReceiver;

    private boolean receiverRegistered = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        IkLog.setInstantFlush(true);
        IkLog.d(TAG, "onCreateView called");

        try {

            View view = inflater.inflate(
                R.layout.fragment_equalizer,
                container,
                false
            );

            IkLog.d(TAG, "Layout inflated successfully");

            return view;

        } catch (Exception e) {

            IkLog.e(TAG, "Layout inflation failed", e);

            TextView errorView = new TextView(requireContext());
            errorView.setText("Failed to load Equalizer");
            errorView.setGravity(Gravity.CENTER);
            errorView.setTextSize(18f);

            Toast.makeText(
                requireContext(),
                "Failed to load equalizer",
                Toast.LENGTH_SHORT
            ).show();

            return errorView;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        IkLog.d(TAG, "onViewCreated");

        if (view instanceof TextView) {
            IkLog.w(TAG, "Fallback error view active");
            return;
        }

        try {

            setupToolbar(view);

            initViews(view);

            loadSavedValues();

            setupListeners();

            startListeningForSession();

            IkLog.d(TAG, "EqualizerFragment initialized");

        } catch (Exception e) {

            IkLog.e(TAG, "Error during initialization", e);
        }
    }

    private void setupToolbar(View view) {

        toolbar = view.findViewById(R.id.toolbar);

        if (toolbar == null) {
            IkLog.e(TAG, "Toolbar not found");
            return;
        }

        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.post(new Runnable() {
                    @Override
                    public void run() {
                        getParentFragmentManager().popBackStack();
                    }
                });
            }
        });

        IkLog.d(TAG, "Toolbar setup complete");
    }

    private void initViews(View view) {

        equalizerContainer = view.findViewById(R.id.equalizer_container);
        bassBoostSlider = view.findViewById(R.id.bass_boost_slider);
        virtualizerSlider = view.findViewById(R.id.virtualizer_slider);
        reverbSlider = view.findViewById(R.id.reverb_slider);
        presetChipGroup = view.findViewById(R.id.preset_chip_group);
        btnReset = view.findViewById(R.id.btn_reset);
        btnApply = view.findViewById(R.id.btn_apply);

        if (equalizerContainer == null) {
            IkLog.e(TAG, "equalizerContainer missing");
        }
        if (bassBoostSlider == null) {
            IkLog.e(TAG, "bassBoostSlider missing");
        }
        if (virtualizerSlider == null) {
            IkLog.e(TAG, "virtualizerSlider missing");
        }
        if (reverbSlider == null) {
            IkLog.e(TAG, "reverbSlider missing");
        }
        if (presetChipGroup == null) {
            IkLog.e(TAG, "presetChipGroup missing");
        }

        IkLog.d(TAG, "initViews complete");
    }

    private void loadSavedValues() {

        try {

            int savedStrength = PrefUtils.getBassBoost(requireContext());

            if (bassBoostSlider != null) {
                bassBoostSlider.setValue(savedStrength / 100f);
            }

            IkLog.d(TAG, "Bass boost restored: " + savedStrength);

        } catch (Exception e) {

            IkLog.e(TAG, "Failed loading saved values", e);
        }
    }

    private void setupListeners() {

        if (bassBoostSlider != null) {
            bassBoostSlider.addOnChangeListener(
                new Slider.OnChangeListener() {
                    @Override
                    public void onValueChange(Slider slider, float value, boolean fromUser) {
                        if (!fromUser || bassBoost == null) return;
                        try {
                            short strength = (short) (value * 100);
                            bassBoost.setStrength(strength);
                            PrefUtils.setBassBoost(requireContext(), strength);
                        } catch (Exception e) {
                            IkLog.e(TAG, "BassBoost update failed", e);
                        }
                    }
                });
        }

        if (virtualizerSlider != null) {
            virtualizerSlider.addOnChangeListener(
                new Slider.OnChangeListener() {
                    @Override
                    public void onValueChange(Slider slider, float value, boolean fromUser) {
                        if (!fromUser || virtualizer == null) return;
                        try {
                            short strength = (short) (value * 100);
                            virtualizer.setStrength(strength);
                        } catch (Exception e) {
                            IkLog.e(TAG, "Virtualizer update failed", e);
                        }
                    }
                });
        }

        if (reverbSlider != null) {
            reverbSlider.addOnChangeListener(
                new Slider.OnChangeListener() {
                    @Override
                    public void onValueChange(Slider slider, float value, boolean fromUser) {
                        if (!fromUser || reverb == null) return;
                        try {
                            short preset = mapSliderToReverbPreset(value);
                            reverb.setPreset(preset);
                        } catch (Exception e) {
                            IkLog.e(TAG, "Reverb update failed", e);
                        }
                    }
                });
        }

        if (btnReset != null) {
            btnReset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    resetEqualizer();
                }
            });
        }

        if (btnApply != null) {
            btnApply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.post(new Runnable() {
                        @Override
                        public void run() {
                            getParentFragmentManager().popBackStack();
                        }
                    });
                }
            });
        }
    }

    private void startListeningForSession() {

        try {

            if (getActivity() instanceof MainActivity) {

                int currentId = ((MainActivity) getActivity()).getAudioSessionId();

                IkLog.d(TAG, "Current audio session ID: " + currentId);

                if (currentId > 0 && currentId != audioSessionId) {
                    audioSessionId = currentId;
                    setupEqualizer();
                    return;
                }
            }

            sessionReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int id = intent.getIntExtra("audio_session_id", 0);
                    IkLog.d(TAG, "Received audio session ID: " + id);
                    if (id > 0 && id != audioSessionId) {
                        audioSessionId = id;
                        setupEqualizer();
                    }
                }
            };

            IntentFilter filter = new IntentFilter("AUDIO_SESSION_ID_UPDATED");

            if (Build.VERSION.SDK_INT >= 33) {
                requireActivity().registerReceiver(sessionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireActivity().registerReceiver(sessionReceiver, filter);
            }

            receiverRegistered = true;
            IkLog.d(TAG, "Receiver registered successfully");

        } catch (Exception e) {
            IkLog.e(TAG, "Failed starting session listener", e);
        }
    }

    private void setupEqualizer() {

        IkLog.d(TAG, "setupEqualizer: session=" + audioSessionId);

        releaseEffects();

        try {
            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(true);
            IkLog.d(TAG, "Equalizer initialized");
        } catch (Exception e) {
            IkLog.e(TAG, "Equalizer unsupported", e);
            showEqualizerNotSupported();
            return;
        }

        try {
            bassBoost = new BassBoost(0, audioSessionId);
            bassBoost.setEnabled(true);
        } catch (Exception e) {
            IkLog.e(TAG, "BassBoost failed", e);
        }

        try {
            virtualizer = new Virtualizer(0, audioSessionId);
            virtualizer.setEnabled(true);
        } catch (Exception e) {
            IkLog.e(TAG, "Virtualizer failed", e);
        }

        try {
            reverb = new PresetReverb(0, audioSessionId);
            reverb.setEnabled(true);
        } catch (Exception e) {
            IkLog.e(TAG, "Reverb failed", e);
        }

        setupEqualizerBands();
        setupPresets();
        setupAudioEffects();
    }

    private void setupEqualizerBands() {

        if (equalizer == null || equalizerContainer == null) {
            return;
        }

        equalizerContainer.removeAllViews();

        short numberOfBands = equalizer.getNumberOfBands();
        short minLevel = equalizer.getBandLevelRange()[0];
        short maxLevel = equalizer.getBandLevelRange()[1];

        IkLog.d(TAG, "Bands=" + numberOfBands);

        for (short band = 0; band < numberOfBands; band++) {

            View bandView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_equalizer_band, equalizerContainer, false);

            TextView freqLabel = bandView.findViewById(R.id.freq_label);
            Slider bandSlider = bandView.findViewById(R.id.band_slider);
            TextView dbLabel = bandView.findViewById(R.id.db_label);

            int centerFreq = equalizer.getCenterFreq(band) / 1000;
            String freqText;
            if (centerFreq < 1000) {
                freqText = centerFreq + " Hz";
            } else {
                freqText = (centerFreq / 1000) + " kHz";
            }
            freqLabel.setText(freqText);

            bandSlider.setValueFrom(minLevel / 100f);
            bandSlider.setValueTo(maxLevel / 100f);

            short savedLevel = getBandLevelPref(band, (short) 0);
            bandSlider.setValue(savedLevel / 100f);
            equalizer.setBandLevel(band, savedLevel);

            updateDbLabel(dbLabel, savedLevel);

            final short currentBand = band;

            bandSlider.addOnChangeListener(
                new Slider.OnChangeListener() {
                    @Override
                    public void onValueChange(Slider slider, float value, boolean fromUser) {
                        if (!fromUser || equalizer == null) {
                            return;
                        }
                        try {
                            short level = (short) (value * 100);
                            equalizer.setBandLevel(currentBand, level);
                            updateDbLabel(dbLabel, level);
                            saveBandLevelPref(currentBand, level);
                        } catch (Exception e) {
                            IkLog.e(TAG, "Band update failed", e);
                        }
                    }
                });

            equalizerContainer.addView(bandView);
        }
    }

    private void setupPresets() {

        if (equalizer == null || presetChipGroup == null) {
            return;
        }

        presetChipGroup.removeAllViews();

        short numPresets = equalizer.getNumberOfPresets();

        for (short i = 0; i < numPresets; i++) {

            String presetName = equalizer.getPresetName(i);

            Chip chip = new Chip(requireContext());
            chip.setText(presetName);
            chip.setCheckable(true);
            chip.setChipIconVisible(false);
            chip.setCloseIconVisible(false);

            final short presetIndex = i;

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPreset(presetIndex);
                }
            });

            presetChipGroup.addView(chip);
        }
    }

    private void applyPreset(int presetIndex) {

        if (equalizer == null) {
            return;
        }

        try {
            equalizer.usePreset((short) presetIndex);
            refreshEqualizerBands();
            PrefUtils.setEqualizerPreset(requireContext(), presetIndex);
            IkLog.d(TAG, "Preset applied: " + presetIndex);
        } catch (Exception e) {
            IkLog.e(TAG, "Preset application failed", e);
        }
    }

    private void refreshEqualizerBands() {

        if (equalizer == null || equalizerContainer == null) {
            return;
        }

        for (int i = 0; i < equalizerContainer.getChildCount(); i++) {

            View bandView = equalizerContainer.getChildAt(i);
            Slider bandSlider = bandView.findViewById(R.id.band_slider);
            TextView dbLabel = bandView.findViewById(R.id.db_label);

            short level = equalizer.getBandLevel((short) i);
            bandSlider.setValue(level / 100f);
            updateDbLabel(dbLabel, level);
            saveBandLevelPref((short) i, level);
        }
    }

    private void setupAudioEffects() {

        try {
            if (bassBoost != null && bassBoostSlider != null) {
                int strength = PrefUtils.getBassBoost(requireContext());
                bassBoost.setStrength((short) strength);
                bassBoostSlider.setValue(strength / 100f);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "BassBoost setup failed", e);
        }

        try {
            if (virtualizer != null && virtualizerSlider != null) {
                virtualizerSlider.setValue(virtualizer.getRoundedStrength() / 100f);
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Virtualizer setup failed", e);
        }

        try {
            if (reverb != null && reverbSlider != null) {
                reverbSlider.setValue(mapReverbPresetToSlider(reverb.getPreset()));
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Reverb setup failed", e);
        }
    }

    private void updateDbLabel(TextView label, short level) {

        String text = String.format(Locale.getDefault(), "%+.1f dB", level / 100f);
        label.setText(text);

        if (level > 0) {
            label.setTextColor(getColorRes(R.color.equalizer_positive));
        } else if (level < 0) {
            label.setTextColor(getColorRes(R.color.equalizer_negative));
        } else {
            label.setTextColor(getColorRes(android.R.color.white));
        }
    }

    private short mapSliderToReverbPreset(float value) {

        if (value < 20) {
            return PresetReverb.PRESET_NONE;
        }
        if (value < 40) {
            return PresetReverb.PRESET_SMALLROOM;
        }
        if (value < 60) {
            return PresetReverb.PRESET_MEDIUMROOM;
        }
        if (value < 80) {
            return PresetReverb.PRESET_LARGEROOM;
        }
        return PresetReverb.PRESET_LARGEHALL;
    }

    private float mapReverbPresetToSlider(short preset) {

        switch (preset) {
            case PresetReverb.PRESET_NONE:
                return 0f;
            case PresetReverb.PRESET_SMALLROOM:
                return 30f;
            case PresetReverb.PRESET_MEDIUMROOM:
                return 50f;
            case PresetReverb.PRESET_LARGEROOM:
                return 70f;
            case PresetReverb.PRESET_LARGEHALL:
                return 90f;
            default:
                return 0f;
        }
    }

    private void resetEqualizer() {

        try {
            if (equalizer != null) {
                equalizer.usePreset((short) 0);
                refreshEqualizerBands();
                PrefUtils.setEqualizerPreset(requireContext(), 0);
            }

            if (bassBoost != null && bassBoostSlider != null) {
                bassBoost.setStrength((short) 0);
                bassBoostSlider.setValue(0);
                PrefUtils.setBassBoost(requireContext(), 0);
            }

            if (virtualizer != null && virtualizerSlider != null) {
                virtualizer.setStrength((short) 0);
                virtualizerSlider.setValue(0);
            }

            if (reverb != null && reverbSlider != null) {
                reverb.setPreset(PresetReverb.PRESET_NONE);
                reverbSlider.setValue(0);
            }

            if (presetChipGroup != null) {
                presetChipGroup.clearCheck();
            }

            IkLog.d(TAG, "Equalizer reset complete");

        } catch (Exception e) {
            IkLog.e(TAG, "Reset failed", e);
        }
    }

    private void showEqualizerNotSupported() {

        IkLog.w(TAG, "Equalizer unsupported");

        if (equalizerContainer == null) {
            return;
        }

        TextView errorText = new TextView(requireContext());
        errorText.setText("Equalizer is not supported on this device");
        errorText.setTextSize(16f);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTextColor(getColorRes(android.R.color.white));

        equalizerContainer.removeAllViews();
        equalizerContainer.addView(errorText);

        if (bassBoostSlider != null) {
            bassBoostSlider.setEnabled(false);
        }
        if (virtualizerSlider != null) {
            virtualizerSlider.setEnabled(false);
        }
        if (reverbSlider != null) {
            reverbSlider.setEnabled(false);
        }
        if (presetChipGroup != null) {
            presetChipGroup.setEnabled(false);
        }
    }

    private void saveBandLevelPref(short band, short level) {

        requireContext()
            .getSharedPreferences("eq_bands", Context.MODE_PRIVATE)
            .edit()
            .putInt("band_" + band, level)
            .apply();
    }

    private short getBandLevelPref(short band, short defaultVal) {

        return (short) requireContext()
            .getSharedPreferences("eq_bands", Context.MODE_PRIVATE)
            .getInt("band_" + band, defaultVal);
    }

    private int getColorRes(int colorRes) {

        return ContextCompat.getColor(requireContext(), colorRes);
    }

    private void releaseEffects() {

        try {
            if (equalizer != null) {
                equalizer.release();
                equalizer = null;
            }
            if (bassBoost != null) {
                bassBoost.release();
                bassBoost = null;
            }
            if (virtualizer != null) {
                virtualizer.release();
                virtualizer = null;
            }
            if (reverb != null) {
                reverb.release();
                reverb = null;
            }
            IkLog.d(TAG, "Audio effects released");
        } catch (Exception e) {
            IkLog.e(TAG, "Effect release failed", e);
        }
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();
        releaseEffects();
        equalizerContainer = null;
        bassBoostSlider = null;
        virtualizerSlider = null;
        reverbSlider = null;
        presetChipGroup = null;
        toolbar = null;
        btnReset = null;
        btnApply = null;
        IkLog.d(TAG, "onDestroyView");
    }

    @Override
    public void onDestroy() {

        super.onDestroy();
        try {
            if (sessionReceiver != null && receiverRegistered) {
                requireActivity().unregisterReceiver(sessionReceiver);
                receiverRegistered = false;
            }
        } catch (Exception e) {
            IkLog.e(TAG, "Receiver unregister failed", e);
        }
        releaseEffects();
        IkLog.d(TAG, "onDestroy");
    }
}