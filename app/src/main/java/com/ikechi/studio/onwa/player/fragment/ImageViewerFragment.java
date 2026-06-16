package com.ikechi.studio.onwa.player.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.ikechi.studio.onwa.player.R;

public class ImageViewerFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_image_viewer, container, false);

        // Hide the GridView and show a TextView with "Gallery Coming Soon"
        GridView gridView = view.findViewById(R.id.imageGridView);
        gridView.setVisibility(View.GONE);

        TextView comingSoonText = new TextView(getActivity());
        comingSoonText.setText("Gallery Coming Soon");
        comingSoonText.setTextSize(20);
        comingSoonText.setGravity(Gravity.CENTER);
        comingSoonText.setLayoutParams(new LinearLayout.LayoutParams(
                                           LinearLayout.LayoutParams.MATCH_PARENT,
                                           LinearLayout.LayoutParams.MATCH_PARENT
                                       ));

        ((LinearLayout) view).addView(comingSoonText);

        return view;
    }
}