package com.mooshim.mooshimeter.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.LogFile;
import com.mooshim.mooshimeter.common.MeterReading;
import com.mooshim.mooshimeter.interfaces.MooshimeterControlInterface;
import com.mooshim.mooshimeter.devices.MooshimeterDeviceBase;
import com.mooshim.mooshimeter.interfaces.MooshimeterDelegate;
import com.mooshim.mooshimeter.interfaces.NotifyHandler;
import com.mooshim.mooshimeter.common.Util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class LoggingPreferencesActivity extends PreferencesActivity implements MooshimeterDelegate{
	// BLE
    private MooshimeterDeviceBase mMeter = null;
    private FileListView mLogView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String addr = intent.getStringExtra("addr");
        mMeter = (MooshimeterDeviceBase)getDeviceWithAddress(addr);
        if(mMeter==null) {
            Util.logNullMeterEvent(addr);
            finish();
            return;
        }

        setContentView(R.layout.activity_preference);

        PreferenceGUIBuilder builder = new PreferenceGUIBuilder();

        // Logging on
        builder.add("Logging Enable","With logging enabled, logs will be written to SD card",
            makeSwitch(mMeter.getLoggingOn(), new BooleanRunnable() {
            @Override
            public void run() {
                Util.dispatch(new Runnable() {
                    @Override
                    public void run() {
                        mMeter.setLoggingOn(arg);
                    }
                });
            }}));

        // Logging interval
        final Button log_interval_button = new Button(mContext);
        final int[] ms_options = new int[]{0, 1000, 10000, 60000, 600000};
        final ArrayList<String> option_list = new ArrayList<>(
                Arrays.asList("No wait", "1s", "10s", "1min", "10min"));
        int i=0;
        int interval = mMeter.getLoggingIntervalMS();
        for(int option:ms_options) {
            if(option>=interval) {
                break;
            }
            i++;
        }
        log_interval_button.setText(option_list.get(i));
        log_interval_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Util.generatePopupMenuWithOptions(mContext, option_list, v, new NotifyHandler() {
                    @Override
                    public void onReceived(double timestamp_utc, final Object payload) {
                        mMeter.setLoggingInterval(ms_options[(Integer) payload]);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                log_interval_button.setText(option_list.get((Integer) payload));
                            }
                        });
                    }
                }, null);
            }
        });
        builder.add("Logging Interval", "How long to wait between log samples.", log_interval_button);

        // Scroll view of available logs
        mLogView = new FileListView(mContext);
        builder.add(mLogView);

        final Button tmp = new Button(mContext);
        tmp.setText("Load available logs");
        tmp.setTextSize(32);
        tmp.setGravity(Gravity.CENTER);
        tmp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mMeter.getLoggingStatus() != 0) {
                    Toast.makeText(mContext,"No SD card to load logs from",Toast.LENGTH_LONG).show();
                } else {
                    Util.dispatch(new Runnable() {
                        @Override
                        public void run() {
                            mMeter.pollLogInfo();
                        }
                    });
                    tmp.setEnabled(false);
                }
            }
        });
        mLogView.addView(tmp);
	}

    private class FileListView extends LinearLayout {
        public FileListView(Context context) {
            super(context);
            this.setOrientation(LinearLayout.VERTICAL);
            LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT);
            this.setLayoutParams(lp);
            this.setDividerDrawable(getDrawable(R.drawable.divider));
            this.setShowDividers(SHOW_DIVIDER_MIDDLE);
        }
        public void addFileLine(final LogFile info) {
            LinearLayout row = new LinearLayout(mContext);
            row.setBackground(getDrawable(R.drawable.list_element));
            row.setOrientation(LinearLayout.HORIZONTAL);
            LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            //lp.setMargins(10,50,10,50);
            row.setPadding(10,50,10,50);
            row.setLayoutParams(lp);

            row.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mContext, DownloadLogActivity.class);
                    intent.putExtra("addr", mMeter.getAddress());
                    intent.putExtra("info_index",info.mIndex);
                    startActivityForResult(intent, 0);
                }
            });

            TextView ilabel = new TextView(mContext);
            ilabel.setText(""+info.mIndex);
            ilabel.setTextSize(20);
            ilabel.setGravity(Gravity.CENTER);
            ilabel.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView datelabel = new TextView(mContext);
            Date date = new Date(info.mEndTime*1000);
            SimpleDateFormat format = new SimpleDateFormat("MM.dd HH:mm z");
            datelabel.setText(format.format(date));
            datelabel.setTextSize(20);
            datelabel.setGravity(Gravity.CENTER);
            datelabel.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView sizelabel = new TextView(mContext);
            sizelabel.setText((info.mBytes/1024)+"kB");
            sizelabel.setTextSize(20);
            sizelabel.setGravity(Gravity.CENTER);
            sizelabel.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

            row.addView(ilabel);
            row.addView(datelabel);
            row.addView(sizelabel);
            this.addView(row);
            requestLayout();
        }
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		default:
            finish();
		}
		return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(   mMeter==null ||!mMeter.isConnected()) {
            onBackPressed();
        }
        mMeter.addDelegate(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMeter.removeDelegate(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDisconnect() {}

    @Override
    public void onRssiReceived(int rssi) {}

    @Override
    public void onBatteryVoltageReceived(float voltage) {}

    @Override
    public void onSampleReceived(double timestamp_utc, MooshimeterControlInterface.Channel c, MeterReading val) {}

    @Override
    public void onBufferReceived(double timestamp_utc, MooshimeterControlInterface.Channel c, float dt, float[] val) {}

    @Override
    public void onSampleRateChanged(int i, int sample_rate_hz) {}

    @Override
    public void onBufferDepthChanged(int i, int buffer_depth) {}

    @Override
    public void onLoggingStatusChanged(boolean on, int new_state, String message) {}

    @Override
    public void onRangeChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.RangeDescriptor new_range) {}

    @Override
    public void onInputChange(MooshimeterControlInterface.Channel c, MooshimeterDeviceBase.InputDescriptor descriptor) {}

    @Override
    public void onOffsetChange(MooshimeterControlInterface.Channel c, MeterReading offset) {}

    @Override
    public void onLogInfoReceived(final LogFile log) {
        Util.postToMain(new Runnable() {
            @Override
            public void run() {
                mLogView.addFileLine(log);
            }
        });
    }
    @Override
    public void onLogFileReceived(LogFile log) {}
}
