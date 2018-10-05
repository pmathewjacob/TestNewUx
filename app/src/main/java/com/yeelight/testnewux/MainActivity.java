package com.yeelight.testnewux;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import android.net.wifi.WifiManager.MulticastLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "APITEST";
    private static final int MSG_SHOWLOG = 0;
    private static final int MSG_FOUND_DEVICE = 1;
    private static final int MSG_DISCOVER_FINISH = 2;
    private static final int MSG_STOP_SEARCH = 3;

    private static final String UDP_HOST = "239.255.255.250";
    private static final int UDP_PORT = 1982;
    private static final String message = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST:239.255.255.250:1982\r\n" +
            "MAN:\"ssdp:discover\"\r\n" +
            "ST:wifi_bulb\r\n";//用于发送的字符串
    private DatagramSocket mDSocket;
    private boolean mSeraching = true;
    private MyAdapter mAdapter;
    List<HashMap<String, String>> mDeviceList = new ArrayList<>();

    private static class MainMessageHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        MainMessageHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            super.handleMessage(msg);
            if (activity != null) {
                switch (msg.what) {
                    case MSG_FOUND_DEVICE:
                        activity.mAdapter.notifyDataSetChanged();
                        break;
                    case MSG_SHOWLOG:
                        Toast.makeText(activity.getApplicationContext(), "" + msg.obj.toString(), Toast.LENGTH_SHORT).show();
                        break;
                    case MSG_STOP_SEARCH:
                        activity.mSearchThread.interrupt();
                        activity.mAdapter.notifyDataSetChanged();
                        activity.mSeraching = false;
                        break;
                    case MSG_DISCOVER_FINISH:
                        activity.mAdapter.notifyDataSetChanged();
                        break;
                }
            }
        }
    }

    private final MainMessageHandler mHandler = new MainMessageHandler(this);

    private MulticastLock multicastLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        WifiManager wm = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            multicastLock = wm.createMulticastLock("test");
            multicastLock.acquire();
        }
        //mTextView = findViewById(R.id.infotext);
        //private TextView mTextView;
        Button mBtnSearch = findViewById(R.id.btn_search);
        mBtnSearch.setOnClickListener(v -> searchDevice());
        ListView mListView = findViewById(R.id.deviceList);
        mAdapter = new MyAdapter(this);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            HashMap<String, String> bulbInfo = mDeviceList.get(position);
            if (bulbInfo != null) {
                String loc = bulbInfo.get("Location");
                if (loc != null && loc.split("//").length > 1) {
                    String ipinfo = loc.split("//")[1];
                    String ip = ipinfo.split(":")[0];
                    String port = ipinfo.split(":")[1];

                    Intent intent = new Intent(MainActivity.this, ControlActivity.class);
                    intent.putExtra("bulbinfo", bulbInfo);
                    intent.putExtra("ip", ip);
                    intent.putExtra("port", port);
                    startActivity(intent);
                }
            }
        });
    }
    private Thread mSearchThread = null;
    private void searchDevice() {

        mDeviceList.clear();
        mAdapter.notifyDataSetChanged();
        mSeraching = true;
        mSearchThread = new Thread(() -> {
            try {
                mDSocket = new DatagramSocket();
                DatagramPacket dpSend = new DatagramPacket(message.getBytes(),
                        message.getBytes().length, InetAddress.getByName(UDP_HOST),
                        UDP_PORT);
                mDSocket.send(dpSend);
                mHandler.sendEmptyMessageDelayed(MSG_STOP_SEARCH,2000);
                while (mSeraching) {
                    byte[] buf = new byte[1024];
                    DatagramPacket dpRecv = new DatagramPacket(buf, buf.length);
                    mDSocket.receive(dpRecv);
                    byte[] bytes = dpRecv.getData();
                    StringBuilder buffer = new StringBuilder();
                    for (int i = 0; i < dpRecv.getLength(); i++) {
                        // parse /r
                        if (bytes[i] == 13) {
                            continue;
                        }
                        buffer.append((char) bytes[i]);
                    }
                    Log.d("socket", "got message:" + buffer.toString());
                    if (!buffer.toString().contains("yeelight")) {
                        mHandler.obtainMessage(MSG_SHOWLOG, "收到一条消息,不是Yeelight灯泡").sendToTarget();
                        return;
                    }
                    String[] info = buffer.toString().split("\n");
                    HashMap<String, String> bulbInfo = new HashMap<>();
                    for (String str : info) {
                        int index = str.indexOf(":");
                        if (index == -1) {
                            continue;
                        }
                        String title = str.substring(0, index);
                        String value = str.substring(index + 1);
                        bulbInfo.put(title, value);
                    }
                    if (isAdded(bulbInfo)){
                        mDeviceList.add(bulbInfo);
                    }

                }
                mHandler.sendEmptyMessage(MSG_DISCOVER_FINISH);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        mSearchThread.start();

    }

    private boolean mNotify = true;
    @Override
    protected void onResume() {
        super.onResume();
        new Thread(() -> {
            try {
                //DatagramSocket socket = new DatagramSocket(UDP_PORT);
                InetAddress group = InetAddress.getByName(UDP_HOST);
                MulticastSocket socket = new MulticastSocket(UDP_PORT);
                socket.setLoopbackMode(true);
                socket.joinGroup(group);
                Log.d(TAG, "join success");
                mNotify = true;
                while (mNotify){
                    byte[] buf = new byte[1024];
                    DatagramPacket receiveDp = new DatagramPacket(buf,buf.length);
                    Log.d(TAG, "waiting device....");
                    socket.receive(receiveDp);
                    byte[] bytes = receiveDp.getData();
                    StringBuilder buffer = new StringBuilder();
                    for (int i = 0; i < receiveDp.getLength(); i++) {
                        // parse /r
                        if (bytes[i] == 13) {
                            continue;
                        }
                        buffer.append((char) bytes[i]);
                    }
                    if (!buffer.toString().contains("yeelight")){
                        Log.d(TAG,"Listener receive msg:" + buffer.toString()+" but not a response");
                        return;
                    }
                    String[] info = buffer.toString().split("\n");
                    HashMap<String, String> bulbInfo = new HashMap<>();
                    for (String str : info) {
                        int index = str.indexOf(":");
                        if (index == -1) {
                            continue;
                        }
                        String title = str.substring(0, index);
                        String value = str.substring(index + 1);
                        Log.d(TAG, "title = " + title + " value = " + value);
                        bulbInfo.put(title, value);
                    }
                    if (isAdded(bulbInfo)){
                        mDeviceList.add(bulbInfo);
                    }
                    mHandler.sendEmptyMessage(MSG_FOUND_DEVICE);
                        Log.d(TAG, "get message:" + buffer.toString());
                }
            }catch (Exception e){
                e.printStackTrace();
            }

        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mNotify = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        multicastLock.release();
    }

    private class MyAdapter extends BaseAdapter implements Serializable {

        private LayoutInflater mLayoutInflater;
        private int mLayoutResource;

        MyAdapter(Context context) {
            mLayoutInflater = LayoutInflater.from(context);
            mLayoutResource = android.R.layout.simple_list_item_2;
        }

        @Override
        public int getCount() {
            return mDeviceList.size();
        }

        @Override
        public Object getItem(int position) {
            return mDeviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            HashMap data = null;
            Object rawData = getItem(position);
            if (rawData instanceof HashMap) {
                data = (HashMap) rawData;
            }

            if (convertView == null) {
                view = mLayoutInflater.inflate(mLayoutResource, parent, false);
            } else {
                view = convertView;
            }
            if (data != null && !data.isEmpty()) {
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setText(String.format("Type = %s", data.get("model")));

                Log.d(TAG, "name = " + textView.getText().toString());
                TextView textSub = view.findViewById(android.R.id.text2);
                textSub.setText(String.format("location = %s", data.get("Location")));
            }
            return view;
        }
    }
    private boolean isAdded(HashMap<String,String> bulbInfo){
        if (bulbInfo != null && bulbInfo.get("Location") != null) {
            for (HashMap<String, String> info : mDeviceList) {
                Log.d(TAG, "location params = " + bulbInfo.get("Location"));
                if (info != null) {
                    String loc = info.get("Location");
                    if (loc != null && loc.equals(bulbInfo.get("Location"))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
