package com.ikechi.studio.onwa.player.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.ikechi.studio.onwa.player.MainActivity;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.utils.SettingsManager;
import com.ikechi.studio.onwa.player.view.core.GLVisualizerRenderer;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    // Visualizer Settings
    private Spinner spinnerVisualizerStyle, spinnerColorScheme, spinnerPerformanceLevel;
    private SeekBar seekBarSensitivity, seekBarBarCount, seekBarRotationSpeed;
    private SeekBar seekBarParticleCount, seekBarGlassBallCount;
    private SeekBar seekBarStyleRotationInterval, seekBarBeatDecayRate;
    private CheckBox checkWireframe, checkUseVBO, checkUseLighting;
    private CheckBox checkAutoRotateStyles, checkTouchInteraction, checkShowBeatIndicator;
    private TextView tvVisualizerStyle, tvColorScheme, tvPerformanceLevel;
    private TextView tvSensitivityValue, tvBarCountValue, tvRotationSpeedValue;
    private TextView tvParticleCountValue, tvGlassBallCountValue;
    private TextView tvStyleRotationIntervalValue, tvBeatDecayRateValue;

    // Player Settings
    private CheckBox checkAutoPlay, checkGaplessPlayback, checkCrossfade;
    private CheckBox checkKeepScreenOn, checkHighQualityAudio, checkRealTimeProcessing;
    private CheckBox checkShowNoMediaFiles;
    private Spinner spinnerDefaultTab;
    private SeekBar seekBarCrossfadeDuration, seekBarVisualizerUpdateRate;
    private TextView tvCrossfadeDuration, tvVisualizerUpdateRate;

    // Other
    private Button btnClearCache, btnResetSettings, btnExportSettings, btnImportSettings;

    private SettingsManager settingsManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        settingsManager = SettingsManager.getInstance(getActivity());
        initViews(view);
        loadCurrentSettings();
        setupListeners();
        return view;
    }

    private void initViews(View view) {
        // Visualizer
        tvVisualizerStyle = view.findViewById(R.id.tv_visualizer_style);
        tvColorScheme = view.findViewById(R.id.tv_color_scheme);
        tvPerformanceLevel = view.findViewById(R.id.tv_performance_level);
        tvSensitivityValue = view.findViewById(R.id.tv_sensitivity_value);
        tvBarCountValue = view.findViewById(R.id.tv_bar_count_value);
        tvRotationSpeedValue = view.findViewById(R.id.tv_rotation_speed_value);
        tvParticleCountValue = view.findViewById(R.id.tv_particle_count_value);
        tvGlassBallCountValue = view.findViewById(R.id.tv_glass_ball_count_value);
        tvStyleRotationIntervalValue = view.findViewById(R.id.tv_style_rotation_interval_value);
        tvBeatDecayRateValue = view.findViewById(R.id.tv_beat_decay_rate_value);

        spinnerVisualizerStyle = view.findViewById(R.id.spinner_visualizer_style);
        spinnerColorScheme = view.findViewById(R.id.spinner_color_scheme);
        spinnerPerformanceLevel = view.findViewById(R.id.spinner_performance_level);
        seekBarSensitivity = view.findViewById(R.id.seekbar_sensitivity);
        seekBarBarCount = view.findViewById(R.id.seekbar_bar_count);
        seekBarRotationSpeed = view.findViewById(R.id.seekbar_rotation_speed);
        seekBarParticleCount = view.findViewById(R.id.seekbar_particle_count);
        seekBarGlassBallCount = view.findViewById(R.id.seekbar_glass_ball_count);
        seekBarStyleRotationInterval = view.findViewById(R.id.seekbar_style_rotation_interval);
        seekBarBeatDecayRate = view.findViewById(R.id.seekbar_beat_decay_rate);
        checkWireframe = view.findViewById(R.id.check_wireframe);
        checkUseVBO = view.findViewById(R.id.check_use_vbo);
        checkUseLighting = view.findViewById(R.id.check_use_lighting);
        checkAutoRotateStyles = view.findViewById(R.id.check_auto_rotate_styles);
        checkTouchInteraction = view.findViewById(R.id.check_touch_interaction);
        checkShowBeatIndicator = view.findViewById(R.id.check_show_beat_indicator);

        // Player
        checkAutoPlay = view.findViewById(R.id.check_auto_play);
        checkGaplessPlayback = view.findViewById(R.id.check_gapless_playback);
        checkCrossfade = view.findViewById(R.id.check_crossfade);
        checkKeepScreenOn = view.findViewById(R.id.check_keep_screen_on);
        checkHighQualityAudio = view.findViewById(R.id.check_high_quality_audio);
        checkRealTimeProcessing = view.findViewById(R.id.check_real_time_processing);
        checkShowNoMediaFiles = view.findViewById(R.id.check_show_no_media_files);
        spinnerDefaultTab = view.findViewById(R.id.spinner_default_tab);
        seekBarCrossfadeDuration = view.findViewById(R.id.seekbar_crossfade_duration);
        seekBarVisualizerUpdateRate = view.findViewById(R.id.seekbar_visualizer_update_rate);
        tvCrossfadeDuration = view.findViewById(R.id.tv_crossfade_duration);
        tvVisualizerUpdateRate = view.findViewById(R.id.tv_visualizer_update_rate);

        // Other
        btnClearCache = view.findViewById(R.id.btn_clear_cache);
        btnResetSettings = view.findViewById(R.id.btn_reset_settings);
        btnExportSettings = view.findViewById(R.id.btn_export_settings);
        btnImportSettings = view.findViewById(R.id.btn_import_settings);

        setupSpinners();
    }

    private void setupSpinners() {
        // Visualizer styles (23 total)
        String[] styles = new String[23];
        if (settingsManager != null) {
            for (int i = 0; i < styles.length; i++) {
                styles[i] = settingsManager.getVisualizerStyleName(i);
            }
            ArrayAdapter<String> styleAdapter = new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_spinner_item, styles);
            styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerVisualizerStyle.setAdapter(styleAdapter);
        }

        String[] colorSchemes = {
            "Rainbow", "Fire", "Ocean", "Forest", "Monochrome", "Cosmic", "Neon", "Aurora"
        };
        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(
            getActivity(), android.R.layout.simple_spinner_item, colorSchemes);
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerColorScheme.setAdapter(colorAdapter);

        String[] performanceLevels = {"Low", "Medium", "High"};
        ArrayAdapter<String> perfAdapter = new ArrayAdapter<>(
            getActivity(), android.R.layout.simple_spinner_item, performanceLevels);
        perfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPerformanceLevel.setAdapter(perfAdapter);

        String[] defaultTabs = {"Audio Player", "Video Player", "Photo Viewer", "Media Share", "Settings"};
        ArrayAdapter<String> tabAdapter = new ArrayAdapter<>(
            getActivity(), android.R.layout.simple_spinner_item, defaultTabs);
        tabAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDefaultTab.setAdapter(tabAdapter);
    }

    private void loadCurrentSettings() {
        SettingsManager.VisualizerSettings viz = settingsManager.getVisualizerSettings();
        SettingsManager.PlayerSettings player = settingsManager.getPlayerSettings();
        if (viz == null || player == null) {
            Log.e(TAG, "Failed to load settings");
            return;
        }

        // Visualizer
        spinnerVisualizerStyle.setSelection(viz.visualizerStyle);
        tvVisualizerStyle.setText("Style: " + settingsManager.getVisualizerStyleName(viz.visualizerStyle));
        spinnerColorScheme.setSelection(viz.colorScheme);
        tvColorScheme.setText("Color: " + settingsManager.getColorSchemeName(viz.colorScheme));
        spinnerPerformanceLevel.setSelection(viz.performanceLevel);
        tvPerformanceLevel.setText("Performance: " + getPerformanceLevelName(viz.performanceLevel));
        seekBarSensitivity.setProgress((int)(viz.sensitivity * 20));
        tvSensitivityValue.setText(String.format("%.2f", viz.sensitivity));
        seekBarBarCount.setProgress(viz.barCount - 8);
        tvBarCountValue.setText(String.valueOf(viz.barCount));
        seekBarRotationSpeed.setProgress((int)(viz.rotationSpeed * 50));
        tvRotationSpeedValue.setText(String.format("%.2f", viz.rotationSpeed));
        seekBarParticleCount.setProgress(viz.particleCount / 10);
        tvParticleCountValue.setText(String.valueOf(viz.particleCount));
        seekBarGlassBallCount.setProgress(viz.glassBallCount);
        tvGlassBallCountValue.setText(String.valueOf(viz.glassBallCount));
        seekBarStyleRotationInterval.setProgress(viz.styleRotationInterval / 1000);
        tvStyleRotationIntervalValue.setText(viz.styleRotationInterval / 1000 + " seconds");
        seekBarBeatDecayRate.setProgress((int)(viz.beatDecayRate * 100));
        tvBeatDecayRateValue.setText(String.format("%.2f", viz.beatDecayRate));
        checkWireframe.setChecked(viz.wireframe);
        checkUseVBO.setChecked(viz.useVBO);
        checkUseLighting.setChecked(viz.useLighting);
        checkAutoRotateStyles.setChecked(viz.autoRotateStyles);
        checkTouchInteraction.setChecked(viz.touchInteraction);
        checkShowBeatIndicator.setChecked(viz.showBeatIndicator);

        // Player
        checkAutoPlay.setChecked(player.autoPlay);
        checkGaplessPlayback.setChecked(player.gaplessPlayback);
        checkCrossfade.setChecked(player.crossfade);
        checkKeepScreenOn.setChecked(player.keepScreenOn);
        checkHighQualityAudio.setChecked(player.highQualityAudio);
        checkRealTimeProcessing.setChecked(player.realTimeProcessing);
        checkShowNoMediaFiles.setChecked(player.showNoMediaFiles);
        spinnerDefaultTab.setSelection(player.defaultTab);
        seekBarCrossfadeDuration.setProgress(player.crossfadeDuration);
        tvCrossfadeDuration.setText(player.crossfadeDuration + " seconds");
        seekBarCrossfadeDuration.setEnabled(player.crossfade);
        seekBarVisualizerUpdateRate.setProgress(player.visualizerUpdateRate / 10);
        tvVisualizerUpdateRate.setText(player.visualizerUpdateRate + " FPS");
    }

    private void setupListeners() {
        // Visualizer Style
        spinnerVisualizerStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                if (s != null) { s.visualizerStyle = position; settingsManager.saveVisualizerSettings(s); 
                    tvVisualizerStyle.setText("Style: " + settingsManager.getVisualizerStyleName(position)); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerColorScheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                if (s != null) { s.colorScheme = position; settingsManager.saveVisualizerSettings(s);
                    tvColorScheme.setText("Color: " + settingsManager.getColorSchemeName(position)); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerPerformanceLevel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                if (s != null) { s.performanceLevel = position; settingsManager.saveVisualizerSettings(s);
                    tvPerformanceLevel.setText("Performance: " + getPerformanceLevelName(position)); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // SeekBars - Visualizer
        seekBarSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = progress / 20.0f; tvSensitivityValue.setText(String.format("%.2f", v));
                if (fromUser) { SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                    if (s != null) { s.sensitivity = v; settingsManager.saveVisualizerSettings(s); } }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarBarCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = progress + 8; tvBarCountValue.setText(String.valueOf(v));
                if (fromUser) { SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                    if (s != null) { s.barCount = v; settingsManager.saveVisualizerSettings(s); } }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarRotationSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = progress / 50.0f; tvRotationSpeedValue.setText(String.format("%.2f", v));
                if (fromUser) { SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                    if (s != null) { s.rotationSpeed = v; settingsManager.saveVisualizerSettings(s); } }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarParticleCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = progress * 10; tvParticleCountValue.setText(String.valueOf(v));
                if (fromUser) { SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                    if (s != null) { s.particleCount = v; settingsManager.saveVisualizerSettings(s); } }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarGlassBallCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvGlassBallCountValue.setText(String.valueOf(progress));
                if (fromUser) { SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                    if (s != null) { s.glassBallCount = progress; settingsManager.saveVisualizerSettings(s); } }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarStyleRotationInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvStyleRotationIntervalValue.setText(progress + " seconds");
                if (fromUser) { SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                    if (s != null) { s.styleRotationInterval = progress * 1000; settingsManager.saveVisualizerSettings(s); } }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarBeatDecayRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = progress / 100.0f; tvBeatDecayRateValue.setText(String.format("%.2f", v));
                if (fromUser) { SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
                    if (s != null) { s.beatDecayRate = v; settingsManager.saveVisualizerSettings(s); } }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // CheckBoxes - Visualizer
        checkWireframe.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
            if (s != null) { s.wireframe = isChecked; settingsManager.saveVisualizerSettings(s); }
        });
        checkUseVBO.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
            if (s != null) { s.useVBO = isChecked; settingsManager.saveVisualizerSettings(s); }
        });
        checkUseLighting.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
            if (s != null) { s.useLighting = isChecked; settingsManager.saveVisualizerSettings(s); }
        });
        checkAutoRotateStyles.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
            if (s != null) { s.autoRotateStyles = isChecked; settingsManager.saveVisualizerSettings(s); }
        });
        checkTouchInteraction.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
            if (s != null) { s.touchInteraction = isChecked; settingsManager.saveVisualizerSettings(s); }
        });
        checkShowBeatIndicator.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.VisualizerSettings s = settingsManager.getVisualizerSettings();
            if (s != null) { s.showBeatIndicator = isChecked; settingsManager.saveVisualizerSettings(s); }
        });

        // CheckBoxes - Player
        checkAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
            if (s != null) { s.autoPlay = isChecked; settingsManager.savePlayerSettings(s); }
        });
        checkGaplessPlayback.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
            if (s != null) { s.gaplessPlayback = isChecked; settingsManager.savePlayerSettings(s);
                notifyPlayerSettingChanged(); }
        });
        checkCrossfade.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
            if (s != null) { s.crossfade = isChecked; settingsManager.savePlayerSettings(s);
                seekBarCrossfadeDuration.setEnabled(isChecked); notifyPlayerSettingChanged(); }
        });
        checkKeepScreenOn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
            if (s != null) { s.keepScreenOn = isChecked; settingsManager.savePlayerSettings(s); }
        });
        checkHighQualityAudio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
            if (s != null) { s.highQualityAudio = isChecked; settingsManager.savePlayerSettings(s);
                notifyPlayerSettingChanged(); }
        });
        checkRealTimeProcessing.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
            if (s != null) { s.realTimeProcessing = isChecked; settingsManager.savePlayerSettings(s); }
        });
        checkShowNoMediaFiles.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
            if (s != null) { s.showNoMediaFiles = isChecked; settingsManager.savePlayerSettings(s);
                Toast.makeText(getActivity(), isChecked ? "Rescan to include hidden media folders"
                               : "Rescan to hide .nomedia folders", Toast.LENGTH_SHORT).show(); }
        });

        spinnerDefaultTab.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
                if (s != null) { s.defaultTab = position; settingsManager.savePlayerSettings(s); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        seekBarCrossfadeDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCrossfadeDuration.setText(progress + " seconds");
                if (fromUser) { SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
                    if (s != null) { s.crossfadeDuration = progress; settingsManager.savePlayerSettings(s);
                        notifyPlayerSettingChanged(); } }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarVisualizerUpdateRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = progress * 10; tvVisualizerUpdateRate.setText(v + " FPS");
                if (fromUser) { SettingsManager.PlayerSettings s = settingsManager.getPlayerSettings();
                    if (s != null) { s.visualizerUpdateRate = v; settingsManager.savePlayerSettings(s); } }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Buttons
        btnClearCache.setOnClickListener(v -> clearCache());
        btnResetSettings.setOnClickListener(v -> resetSettings());
        btnExportSettings.setOnClickListener(v -> Toast.makeText(getActivity(), "Export coming soon", Toast.LENGTH_SHORT).show());
        btnImportSettings.setOnClickListener(v -> Toast.makeText(getActivity(), "Import coming soon", Toast.LENGTH_SHORT).show());
    }

    private void notifyPlayerSettingChanged() {
        android.app.Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            main.applyPlayerSettings();
        }
    }

    private void clearCache() {
        com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper dbHelper =
            com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper.getInstance(getActivity());
        dbHelper.clearAudio();
        dbHelper.clearVideos();
        Toast.makeText(getActivity(), "Cache cleared. Rescan to rebuild.", Toast.LENGTH_SHORT).show();
    }

    private String getPerformanceLevelName(int level) {
        switch (level) {
            case GLVisualizerRenderer.PERFORMANCE_LOW: return "Low";
            case GLVisualizerRenderer.PERFORMANCE_HIGH: return "High";
            default: return "Medium";
        }
    }

    private void resetSettings() {
        SettingsManager.VisualizerSettings defaultViz = new SettingsManager.VisualizerSettings();
        defaultViz.visualizerStyle = SettingsManager.STYLE_FLAME;
        defaultViz.colorScheme = GLVisualizerRenderer.COLOR_SCHEME_FIRE;
        defaultViz.performanceLevel = GLVisualizerRenderer.PERFORMANCE_MEDIUM;
        defaultViz.sensitivity = 1.5f;
        defaultViz.barCount = 64;
        defaultViz.rotationSpeed = 0.5f;
        defaultViz.wireframe = false;
        defaultViz.useVBO = true;
        defaultViz.useLighting = true;
        defaultViz.autoRotateStyles = true;
        defaultViz.styleRotationInterval = 10000;
        defaultViz.particleCount = 500;
        defaultViz.glassBallCount = 15;
        defaultViz.touchInteraction = true;
        defaultViz.beatDecayRate = 0.85f;
        defaultViz.showBeatIndicator = true;

        SettingsManager.PlayerSettings defaultPlayer = new SettingsManager.PlayerSettings();
        defaultPlayer.autoPlay = false;
        defaultPlayer.gaplessPlayback = false;
        defaultPlayer.crossfade = false;
        defaultPlayer.keepScreenOn = false;
        defaultPlayer.defaultTab = 0;
        defaultPlayer.crossfadeDuration = 3;
        defaultPlayer.visualizerUpdateRate = 60;
        defaultPlayer.highQualityAudio = true;
        defaultPlayer.realTimeProcessing = true;
        defaultPlayer.showNoMediaFiles = false;

        settingsManager.saveVisualizerSettings(defaultViz);
        settingsManager.savePlayerSettings(defaultPlayer);
        loadCurrentSettings();
        notifyPlayerSettingChanged();
        Toast.makeText(getActivity(), "Settings reset to defaults", Toast.LENGTH_SHORT).show();
    }
}