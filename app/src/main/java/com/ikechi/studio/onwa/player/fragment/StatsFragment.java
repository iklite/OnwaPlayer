package com.ikechi.studio.onwa.player.fragment;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.ikechi.studio.IkLog;
import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.utils.MediaDatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatsFragment extends Fragment {

    private static final String TAG = "StatsFragment";

    private TextView tvTotalTracks, tvTotalPlays, tvListeningTime;
    private TextView tvTopArtist, tvTopTrack, tvTopAlbum;
    private TextView tvStreakCurrent, tvStreakLongest, tvPeakHour, tvPeakDay;
    private BarChart hourlyChart;
    private PieChart artistPieChart;
    private LinearLayout loadingOverlay;
    private ScrollView contentContainer;

    private final SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        IkLog.setInstantFlush(true);
        IkLog.d(TAG, "onCreateView start");
        try {
            View view = inflater.inflate(R.layout.fragment_stats, container, false);
            bindViews(view);
            setupCharts();
            loadStats();
            IkLog.d(TAG, "onCreateView success");
            return view;
        } catch (Exception e) {
            IkLog.e(TAG, "Fatal error in onCreateView", e);
            return new View(getActivity());
        } finally {
            IkLog.setInstantFlush(false);
        }
    }

    private void bindViews(View view) {
        tvTotalTracks   = view.findViewById(R.id.stat_total_tracks);
        tvTotalPlays    = view.findViewById(R.id.stat_total_plays);
        tvListeningTime = view.findViewById(R.id.stat_listening_time);
        tvTopArtist     = view.findViewById(R.id.stat_top_artist);
        tvTopTrack      = view.findViewById(R.id.stat_top_track);
        tvTopAlbum      = view.findViewById(R.id.stat_top_album);
        tvStreakCurrent = view.findViewById(R.id.stat_streak_current);
        tvStreakLongest = view.findViewById(R.id.stat_streak_longest);
        tvPeakHour      = view.findViewById(R.id.stat_peak_hour);
        tvPeakDay       = view.findViewById(R.id.stat_peak_day);
        hourlyChart     = view.findViewById(R.id.chart_hourly_activity);
        artistPieChart  = view.findViewById(R.id.chart_top_artists);
        loadingOverlay  = view.findViewById(R.id.loading_overlay);
        contentContainer = view.findViewById(R.id.content_container);
    }

    private void setupCharts() {
        if (hourlyChart != null) {
            hourlyChart.getDescription().setEnabled(false);
            hourlyChart.setFitBars(true);
            hourlyChart.setPinchZoom(false);
            hourlyChart.setScaleEnabled(false);
            hourlyChart.setDrawGridBackground(false);
            hourlyChart.setDrawBarShadow(false);
            hourlyChart.setHighlightFullBarEnabled(false);

            String[] hours = new String[24];
            for (int i = 0; i < 24; i++) hours[i] = String.format("%02d", i);
            hourlyChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(hours));
            hourlyChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
            hourlyChart.getXAxis().setGranularity(1f);
            hourlyChart.getXAxis().setLabelCount(12);
            hourlyChart.getXAxis().setTextColor(Color.DKGRAY);
            hourlyChart.getXAxis().setTextSize(10f);
            hourlyChart.getAxisLeft().setAxisMinimum(0f);
            hourlyChart.getAxisLeft().setTextColor(Color.DKGRAY);
            hourlyChart.getAxisLeft().setTextSize(10f);
            hourlyChart.getAxisRight().setEnabled(false);
            hourlyChart.getLegend().setEnabled(false);
            hourlyChart.setNoDataText("Loading...");
            hourlyChart.setNoDataTextColor(Color.GRAY);
        }

        if (artistPieChart != null) {
            artistPieChart.getDescription().setEnabled(false);
            artistPieChart.setDrawHoleEnabled(true);
            artistPieChart.setHoleRadius(58f);
            artistPieChart.setTransparentCircleRadius(62f);
            artistPieChart.setHoleColor(Color.TRANSPARENT);
            artistPieChart.setCenterText("Top\nArtists");
            artistPieChart.setCenterTextSize(14f);
            artistPieChart.setCenterTextColor(Color.DKGRAY);
            artistPieChart.setEntryLabelColor(Color.DKGRAY);
            artistPieChart.setEntryLabelTextSize(11f);
            artistPieChart.setUsePercentValues(true);
            artistPieChart.getLegend().setEnabled(false);
            artistPieChart.setNoDataText("Not enough data");
            artistPieChart.setNoDataTextColor(Color.GRAY);
        }
    }

    private void loadStats() {
        final Activity activity = getActivity();
        if (activity == null) { IkLog.w(TAG, "Activity null"); return; }

        IkLog.d(TAG, "Starting stats database load");
        showLoading(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MediaDatabaseHelper db = MediaDatabaseHelper.getInstance(activity);
                    final int totalPlays              = db.getTotalPlayCount();
                    final long totalTimeMs            = db.getTotalListeningTimeMs();
                    final String topArtist            = db.getTopArtist();
                    final String topAlbum             = db.getTopAlbum();
                    final String topTrack             = db.getTopTrack();
                    final int distinctTracks          = db.getTotalDistinctTracks();
                    final int[] hourlyActivity        = db.getHourlyActivity();
                    final MediaDatabaseHelper.StreakInfo streakInfo = db.getListeningStreak();
                    final int peakHour                = db.getPeakListeningHour();
                    final int peakDay                 = db.getPeakListeningDay();
                    final List<MediaDatabaseHelper.ArtistCount> topArtists = db.getTopArtists(5);

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (!isAdded()) return;
                                showLoading(false);

                                setText(tvTotalTracks,   formatNumber(distinctTracks));
                                setText(tvTotalPlays,    formatNumber(totalPlays));
                                setText(tvListeningTime, formatDurationFull(totalTimeMs));
                                setText(tvTopArtist,     topArtist != null ? topArtist : "—");
                                setText(tvTopAlbum,      topAlbum  != null ? topAlbum  : "—");
                                setText(tvTopTrack,      topTrack  != null ? topTrack  : "—");
                                setText(tvStreakCurrent, streakInfo != null
                                        ? streakInfo.currentStreak + " day" + (streakInfo.currentStreak != 1 ? "s" : "")
                                        : "0 days");
                                setText(tvStreakLongest, streakInfo != null
                                        ? streakInfo.longestStreak + " day" + (streakInfo.longestStreak != 1 ? "s" : "")
                                        : "0 days");
                                setText(tvPeakHour, peakHour >= 0
                                        ? String.format(Locale.getDefault(), "%02d:00 – %02d:59", peakHour, peakHour)
                                        : "—");
                                setText(tvPeakDay, peakDay >= 1
                                        ? dayFormat.format(getDateForDayOfWeek(peakDay))
                                        : "—");

                                if (hourlyChart != null && hourlyActivity != null)
                                    updateHourlyChart(hourlyActivity);
                                if (artistPieChart != null && topArtists != null && !topArtists.isEmpty())
                                    updateArtistChart(topArtists);

                                IkLog.d(TAG, "Stats displayed successfully");
                            } catch (Exception e) {
                                IkLog.e(TAG, "Error updating stats UI", e);
                            }
                        }
                    });
                } catch (Exception e) {
                    IkLog.e(TAG, "Error loading stats from database", e);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() { showLoading(false); }
                    });
                }
            }
        }).start();
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (contentContainer != null)
            contentContainer.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
    }

    private void updateHourlyChart(int[] hourlyActivity) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 24; i++) entries.add(new BarEntry(i, hourlyActivity[i]));

        BarDataSet dataSet = new BarDataSet(entries, "Plays per hour");
        dataSet.setColors(generateHourlyColors());
        dataSet.setValueTextSize(10f);
        dataSet.setDrawValues(false);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.8f);
        hourlyChart.setData(barData);
        hourlyChart.animateY(800);
        hourlyChart.invalidate();
    }

    private void updateArtistChart(List<MediaDatabaseHelper.ArtistCount> topArtists) {
        List<PieEntry> entries = new ArrayList<>();
        for (MediaDatabaseHelper.ArtistCount ac : topArtists) {
            entries.add(new PieEntry(ac.count, ac.artist));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(generatePieColors(topArtists.size()));
        dataSet.setValueFormatter(new PercentFormatter(artistPieChart));
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(5f);

        PieData pieData = new PieData(dataSet);
        artistPieChart.setData(pieData);
        artistPieChart.animateY(800);
        artistPieChart.invalidate();
    }

    private List<Integer> generateHourlyColors() {
        List<Integer> colors = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            float hue;
            if      (hour < 6)  hue = 240f + (hour / 6f) * 20f;
            else if (hour < 12) hue = 180f - ((hour - 6)  / 6f) * 60f;
            else if (hour < 18) hue = 60f  - ((hour - 12) / 6f) * 60f;
            else                hue = 300f - ((hour - 18) / 6f) * 40f;
            colors.add(Color.HSVToColor(new float[]{hue, 1f, 1f}));
        }
        return colors;
    }

    private List<Integer> generatePieColors(int count) {
        int[] palette = {
            0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF03A9F4, 0xFF009688,
            0xFFFF5722, 0xFF795548, 0xFF607D8B, 0xFFFFC107, 0xFF8BC34A
        };
        List<Integer> colors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) colors.add(palette[i % palette.length]);
        return colors;
    }

    private void setText(TextView tv, String text) {
        if (tv != null && text != null) tv.setText(text);
    }

    private String formatNumber(int num) {
        if (num >= 1_000_000) return String.format(Locale.getDefault(), "%.1fM", num / 1_000_000f);
        if (num >= 1_000)     return String.format(Locale.getDefault(), "%.1fK", num / 1_000f);
        return String.valueOf(num);
    }

    private String formatDurationFull(long millis) {
        long totalSeconds = millis / 1000;
        long days    = totalSeconds / 86400;
        long hours   = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0)            sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m");
        return sb.toString();
    }

    private Date getDateForDayOfWeek(int dayOfWeek) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        return cal.getTime();
    }
}