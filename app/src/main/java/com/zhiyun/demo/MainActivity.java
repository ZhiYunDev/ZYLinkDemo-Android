package com.zhiyun.demo;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;

import com.zhiyun.demo.databinding.ActivityMainBinding;
import com.zhiyun.sdk.DeviceManager;
import com.zhiyun.sdk.device.Device;
import com.zhiyun.sdk.util.BTUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    boolean isCanning = false;
    MenuItem mScanningMenu;
    MenuItem mScanMenu;

    private BleAdapter mBleAdapter;
    private final List<Device> mConnectDevices = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBleAdapter.clear();
        mBleAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mScanningMenu = menu.findItem(R.id.scanning);
        mScanMenu = menu.findItem(R.id.scan);
        mScanningMenu.setActionView(R.layout.progress).setVisible(false);
        startScanBle();
        return true;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startScanBle();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        switch (itemId) {
            case R.id.setting:
                toSetting();
                break;
            case R.id.scan: {
                if (isCanning) {
                    stopScanBle();
                } else {
                    startScanBle();
                }

                break;
            }
        }
        return true;
    }

    private void setView() {
        mBleAdapter = new BleAdapter() {

            @Override
            void onConnectClicked(final Button view, Device device) {
                if (!mConnectDevices.contains(device)) {
                    mConnectDevices.add(device);
                }
                if (device.isConnected()) {
                    device.disconnect();
                }
                else {
                    binding.progress.setVisibility(View.VISIBLE);
                    // Subscribe to the connection status
                    device.setStateListener(new Device.StatusListener() {
                        @Override
                        public void onStateChanged(final int state) {
                            updateConnectionState(device, state, view);
                        }
                    });

                    // Subscribe to key events
                    device.setKeyListener(new Device.KeyListener() {
                        @Override
                        public void onKeyEvent(final int keyType, final int keyEvent, int originalKeyValue) {
                            // Non ui thread
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String msg = translateKeyType(keyType) + "  " + translateKeyEvent(keyEvent);
                                    Log.d(TAG,  msg);
                                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });

                    // Subscribe to func events
                    device.setFuncListener(new Device.FuncListener() {

                        /**
                         * The device has a button to press.
                         *
                         * @param code code
                         * @param param param
                         */
                        @Override
                        public void onFuncEvent(int code, int param) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String msg = translateFuncEvent(code, param);
                                    Log.d(TAG,  msg);
                                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        /**
                         * The device has a button to press.
                         *
                         * @param events {@link Pair#first} is code, Pair#second is param
                         */
                        @Override
                        public void onFuncEvent(@NonNull List<Pair<Integer, Integer>> events) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                String collect = events.stream()
                                        .map(Pair::toString)
                                        .collect(Collectors.joining(", ", "[", "]"));
                                Log.d(TAG, "onFuncEvent: " + collect);
                            }
                        }
                    });

                    // stop scan
                    stopScanBle();
                    // Connect to the device
                    device.connect();
                }
            }
        };
        binding.devices.setAdapter(mBleAdapter);
    }

    private String translateKeyType(@Device.KeyType int keyType) {
        String key;
        switch (keyType) {
            case Device.KEY_TYPE_UP:
                key = "up";
                break;
            case Device.KEY_TYPE_DOWN:
                key = "down";
                break;
            case Device.KEY_TYPE_LEFT:
                key = "left";
                break;
            case Device.KEY_TYPE_RIGHT:
                key = "right";
                break;
            case Device.KEY_TYPE_MODE:
                key = "mode";
                break;
            case Device.KEY_TYPE_PHOTOS:
                key = "photos";
                break;
            case Device.KEY_TYPE_FN:
                key = "fn";
                break;
            case Device.KEY_TYPE_T:
                key = "t";
                break;
            case Device.KEY_TYPE_W:
                key = "w";
                break;
            case Device.KEY_TYPE_CW:
                key = "cw";
                break;
            case Device.KEY_TYPE_CCW:
                key = "ccw";
                break;
            case Device.KEY_TYPE_MENU:
                key = "menu";
                break;
            case Device.KEY_TYPE_DISP:
                key = "disp";
                break;
            case Device.KEY_TYPE_FLASH:
                key = "flash";
                break;
            case Device.KEY_TYPE_SWITCH:
                key = "switch";
                break;
            case Device.KEY_TYPE_RECORD:
                key = "record";
                break;
            case Device.KEY_TYPE_SIDE_CW:
                key = "side cw";
                break;
            case Device.KEY_TYPE_SIDE_CCW:
                key = "side ccw";
                break;
            case Device.KEY_TYPE_ZOOM_CW:
                key = "zoom cw";
                break;
            case Device.KEY_TYPE_ZOOM_CCW:
                key = "zoom ccw";
                break;
            case Device.KEY_TYPE_FOCUS_CW:
                key = "focus cw";
                break;
            case Device.KEY_TYPE_FOCUS_CCW:
                key = "focus ccw";
                break;
            default:
                key = "Failed";
                break;
        }
        return key;
    }

    private String translateKeyEvent(int keyEvent) {
        String key;
        switch (keyEvent) {
            case Device.KEY_EVENT_CLICKED:
                key = "Clicked";
                break;
            case Device.KEY_EVENT_PRESSED:
                key = "Pressed";
                break;
            case Device.KEY_EVENT_RELEASED:
                key = "Released";
                break;
            case Device.KEY_EVENT_PRESS_1S:
                key = "press 1s";
                break;
            case Device.KEY_EVENT_PRESS_3S:
                key = "press 2s";
                break;
            default:
                key = "Failed";
        }
        return key;
    }

    private String translateFuncEvent(int code, int param) {
        return String.format(Locale.getDefault(),"code: %X  param: %d", code, param);
    }

    private void updateConnectionState(Device device, int state, Button view) {
        switch (state) {
            case Device.NO_CONNECTION:
                progress(false);
                view.setText(R.string.connect);
                break;
            case Device.TO_BE_CONNECTED:
                progress(false);
                view.setText(R.string.disconnect);

                OptionalActivity.startActivity(this, device.getIdentifier());

                break;
            case Device.TO_BE_MISSED:
            case Device.CONNECTING:
            default:
                progress(true);
                break;
        }
    }

    private void toSetting() {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(intent);
    }

    private void startScanBle() {

        if (!BTUtil.isSupportBle()) {
            Toast.makeText(this, "Not support ble ", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!BTUtil.isOpened()) {
            Toast.makeText(this, "Please turn on Bluetooth ", Toast.LENGTH_SHORT).show();
            return;
        }

        // Android 6.0 need open location service and grant location permission
        // See https://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-hardware-id
        if (!BTUtil.isLocationProviderOk(getApplicationContext())) {
            Toast.makeText(this, "Please open the location service ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!BTUtil.isLocationPermissionOk(getApplicationContext())) {
            Toast.makeText(this, "Please grant location permissions ", Toast.LENGTH_SHORT).show();
            return;
        }

        // update ui
        mScanningMenu.setVisible(true);
        mScanMenu.setTitle(R.string.stop);
        isCanning = true;
        // start scan
        DeviceManager.getInstance().setScanCallback(new DeviceManager.ScanCallback() {
            @Override
            public void onCallback(Device device) {
                mBleAdapter.add(device);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mBleAdapter.notifyDataSetChanged();
                    }
                });
            }
        });

        DeviceManager.getInstance().scan(DeviceManager.DeviceType.BLE);
    }

    private void stopScanBle() {
        // update ui
        mScanningMenu.setVisible(false);
        mScanMenu.setTitle(R.string.scan);
        isCanning = false;
        // cancel scan
        DeviceManager.getInstance().cancelScan();
    }

    private void progress(boolean showing) {
        int visibility = showing ? View.VISIBLE : View.GONE;
        binding.progress.setVisibility(visibility);
    }
}

