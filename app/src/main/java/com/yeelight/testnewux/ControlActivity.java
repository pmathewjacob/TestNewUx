package com.yeelight.testnewux;

import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.Socket;


public class ControlActivity extends AppCompatActivity {
    private String TAG = "Control";

    private static final int MSG_CONNECT_SUCCESS = 0;
    private static final int MSG_CONNECT_FAILURE = 1;
    //private static final String CMD_TOGGLE = "{\"id\":%id,\"method\":\"toggle\",\"params\":[]}\r\n" ;
    private static final String CMD_ON = "{\"id\":%id,\"method\":\"set_power\",\"params\":[\"on\",\"smooth\",500]}\r\n";
    private static final String CMD_OFF = "{\"id\":%id,\"method\":\"set_power\",\"params\":[\"off\",\"smooth\",500]}\r\n";
    private static final String CMD_CT = "{\"id\":%id,\"method\":\"set_ct_abx\",\"params\":[%value, \"smooth\", 500]}\r\n";
    private static final String CMD_HSV = "{\"id\":%id,\"method\":\"set_hsv\",\"params\":[%value, 100, \"smooth\", 200]}\r\n";
    private static final String CMD_BRIGHTNESS = "{\"id\":%id,\"method\":\"set_bright\",\"params\":[%value, \"smooth\", 200]}\r\n";
    //private static final String CMD_BRIGHTNESS_SCENE = "{\"id\":%id,\"method\":\"set_bright\",\"params\":[%value, \"smooth\", 500]}\r\n";
    //private static final String CMD_COLOR_SCENE = "{\"id\":%id,\"method\":\"set_scene\",\"params\":[\"cf\",1,0,\"100,1,%color,1\"]}\r\n";

    private int mCmdId;
    private Socket mSocket;
    private String mBulbIP;
    private int mBulbPort;
    private ProgressDialog mProgressDialog;
    //private Button mBtnMusic;
    private BufferedOutputStream mBos;
    private BufferedReader mReader;
    
    private static class MessageHandler extends Handler {
        private final WeakReference<ControlActivity> mActivity;

        MessageHandler(ControlActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ControlActivity activity = mActivity.get();
            super.handleMessage(msg);
            if (activity != null) {
                switch (msg.what) {
                    case MSG_CONNECT_FAILURE:
                        activity.mProgressDialog.dismiss();
                        break;
                    case MSG_CONNECT_SUCCESS:
                        activity.mProgressDialog.dismiss();
                        break;
                }
            }
        }
    }

    private final MessageHandler mHandler = new MessageHandler(this);
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        mBulbIP = getIntent().getStringExtra("ip");
        mBulbPort = Integer.parseInt(getIntent().getStringExtra("port"));
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("Connecting...");
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        SeekBar mBrightness = findViewById(R.id.brightness);
        SeekBar mColor = findViewById(R.id.color);
        SeekBar mCT = findViewById(R.id.ct);
        mCT.setMax(4800);
        mColor.setMax(360);
        mBrightness.setMax(100);

        mBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                write(parseBrightnessCmd(seekBar.getProgress()));
            }
        });
        mCT.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                write(parseCTCmd(seekBar.getProgress() + 1700));
            }
        });
        mColor.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                write(parseColorCmd(seekBar.getProgress()));
            }
        });
        Button mBtnOn = findViewById(R.id.btn_on);
        Button mBtnOff = findViewById(R.id.btn_off);
        mBtnOn.setOnClickListener(v -> write(parseSwitch(true)));
        mBtnOff.setOnClickListener(v -> write(parseSwitch(false)));
        connect();
    }

    private boolean cmd_run = true;

    private void connect() {
        new Thread(() -> {
            try {
                cmd_run = true;
                mSocket = new Socket(mBulbIP, mBulbPort);
                mSocket.setKeepAlive(true);
                mBos = new BufferedOutputStream(mSocket.getOutputStream());
                mHandler.sendEmptyMessage(MSG_CONNECT_SUCCESS);
                mReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                while (cmd_run) {
                    try {
                        String value = mReader.readLine();
                        Log.d(TAG, "value = " + value);
                    } catch (Exception e) {
                        Log.e(ControlActivity.class.getSimpleName(), "error read:: " + e.getMessage());
                    }

                }
            } catch (Exception e) {
                Log.e(ControlActivity.class.getSimpleName(), "error connect:: " + e.getMessage());
                mHandler.sendEmptyMessage(MSG_CONNECT_FAILURE);
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            cmd_run = false;
            if (mSocket != null)
                mSocket.close();
        } catch (Exception e) {
            Log.e(ControlActivity.class.getSimpleName(), "error:: onDestroy");
        }

    }

    private String parseSwitch(boolean on) {
        String cmd;
        if (on) {
            cmd = CMD_ON.replace("%id", String.valueOf(++mCmdId));
        } else {
            cmd = CMD_OFF.replace("%id", String.valueOf(++mCmdId));
        }
        return cmd;
    }

    private String parseCTCmd(int ct) {
        return CMD_CT.replace("%id", String.valueOf(++mCmdId)).replace("%value", String.valueOf(ct + 1700));
    }

    private String parseColorCmd(int color) {
        return CMD_HSV.replace("%id", String.valueOf(++mCmdId)).replace("%value", String.valueOf(color));
    }

    private String parseBrightnessCmd(int brightness) {
        return CMD_BRIGHTNESS.replace("%id", String.valueOf(++mCmdId)).replace("%value", String.valueOf(brightness));
    }

    private void write(String cmd) {
        if (mBos != null && mSocket.isConnected()) {
            try {
                mBos.write(cmd.getBytes());
                mBos.flush();
            } catch (Exception e) {
                Log.e(ControlActivity.class.getSimpleName(), "error write:: " + e.getMessage());
            }
        } else {
            Log.d(TAG, "mBos = null or mSocket is closed");
        }
    }

}
