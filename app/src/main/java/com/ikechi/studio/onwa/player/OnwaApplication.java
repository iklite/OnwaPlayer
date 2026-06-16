package com.ikechi.studio.onwa.player;

import android.app.Application;
import com.ikechi.studio.onwa.player.utils.MediaUtils;
import com.ikechi.studio.IkLog;

public class OnwaApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate(); 
		IkLog.init(this);
        MediaUtils.initDatabase(this);
    }
}