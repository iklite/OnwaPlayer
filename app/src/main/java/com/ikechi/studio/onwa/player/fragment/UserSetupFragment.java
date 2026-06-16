package com.ikechi.studio.onwa.player.fragment;

import androidx.fragment.app.Fragment; // AndroidX migration: was android.app.Fragment
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;

import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.player.utils.IkBeautifulDialog;
import com.ikechi.studio.onwa.player.utils.PrefUtils;

public class UserSetupFragment extends Fragment {

    // Callback interface to notify the activity when setup is complete
    public interface OnSetupCompleteListener {
        void onSetupComplete();
    }

    private EditText etUsername, etRealName;
    private Button btnSave;
    private WebView mWebview;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_setup, container, false);

        etUsername = view.findViewById(R.id.et_username);
        etRealName = view.findViewById(R.id.et_real_name);
        btnSave = view.findViewById(R.id.btn_save);
        mWebview = view.findViewById(R.id.user_setup_info_webview);

        try {
            WebSettings settings = mWebview.getSettings();
            settings.setBuiltInZoomControls(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setDomStorageEnabled(true);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }

            mWebview.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            mWebview.loadUrl("file:///android_asset/wifi_p2p_welcome_screen_info.html");
        } catch (Exception e) {
            e.printStackTrace();
        }

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String username = etUsername.getText().toString().trim();
                final String realName = etRealName.getText().toString().trim();

                if (TextUtils.isEmpty(username) || TextUtils.isEmpty(realName)) {
                    new IkBeautifulDialog(getActivity())
                            .setMessage("Please fill in both username and real name fields")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }

                new IkBeautifulDialog(getActivity())
                        .setMessage("Save your details?")
                        .setPositiveButton("Save", new IkBeautifulDialog.OnPositiveClickListener() {
                            @Override
                            public void onClick() {
                                PrefUtils.saveUser(getActivity(), username, realName);
                                // Notify the activity that setup is complete
                                if (getActivity() instanceof OnSetupCompleteListener) {
                                    ((OnSetupCompleteListener) getActivity()).onSetupComplete();
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        return view;
    }
}