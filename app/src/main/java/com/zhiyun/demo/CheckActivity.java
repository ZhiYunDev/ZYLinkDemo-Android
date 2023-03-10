package com.zhiyun.demo;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zhiyun.sdk.DeviceManager;
import com.zhiyun.sdk.device.Device;
import com.zhiyun.sdk.util.BTUtil;

/**
 * @author sjw
 * @date 2023-03-08
 * @description
 */
public class CheckActivity extends AppCompatActivity {
    private static final String TAG = "CheckActivity";

    boolean isCanning = false;
    MenuItem mScanningMenu;
    MenuItem mScanMenu;

    private ListView mDevices;
    private BleAdapter mBleAdapter;

    private Device currentDevice;
    private boolean increase;

    //用户主动断开连接或者连接其他设备置true，不进行自动重连
    private boolean hasConnectOtherDevice;
    //应用是否在前台
    private boolean isForeGround;
    //是否在连接中，连接中点击连接无效
    private boolean isConnecting;
    //是否在重连中，由于重连中，isConnecting为true，此时应允许连接其他设备连接，故设置此变量
    private boolean isRetrying;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check);
        setView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeGround = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeGround = false;
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
                connect(device, view);
            }
        };
        mDevices = findViewById(R.id.lv_devices);
        mDevices.setAdapter(mBleAdapter);
        findViewById(R.id.btn_move).setOnClickListener(this::moveTo);
        findViewById(R.id.btn_photo).setOnClickListener(this::takePhoto);
    }

    private void connect(Device device, Button view) {
        if (isConnecting && !isRetrying) {
            return;
        }
        //用户试图连接其他设备
        if (!device.equals(currentDevice)) {
            hasConnectOtherDevice = true;
        }
        if (currentDevice != null && currentDevice.isConnected() ) {
            currentDevice.disconnect();
        }
        //用户主动断开连接
        if (device.isConnected()) {
            device.disconnect();
            hasConnectOtherDevice = true;
        } else {
            // Subscribe to the connection status
            device.setStateListener(state -> {
                if (view != null) {
                    updateConnectionState(device, state, view);
                }
            });

            // stop scan
            stopScanBle();
            // Connect to the device
            device.connect();
        }
    }
    private void updateConnectionState(Device device, int state, Button view) {
        switch (state) {
            case Device.NO_CONNECTION:
                view.setText(R.string.connect);
                findViewById(R.id.sv_func).setVisibility(View.GONE);
                if (!hasConnectOtherDevice && isForeGround) {
                    device.connect();
                    isRetrying = true;
                }
                isConnecting = false;
                break;
            case Device.TO_BE_CONNECTED:
                isRetrying = false;
                hasConnectOtherDevice = false;
                view.setText(R.string.disconnect);
                showFuncView(Utils.kindOfDevice(device.getModelName()), findViewById(R.id.sv_func), findViewById(R.id.btn_photo));
                currentDevice = device;
                isConnecting = false;
                break;
            case Device.TO_BE_MISSED:
            case Device.CONNECTING:
                isConnecting = true;
                break;
            default:
                break;
        }
    }

    private void showFuncView(Utils.DeviceKind deviceKind, View allView, View photoView) {
        switch (deviceKind) {
            case PHONE:
                allView.setVisibility(View.VISIBLE);
                photoView.setVisibility(View.GONE);
                break;
            case CAMERA:
                allView.setVisibility(View.VISIBLE);
                photoView.setVisibility(View.VISIBLE);
                break;
            case OTHERS:
                allView.setVisibility(View.GONE);
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

    private void moveTo(View view) {
        currentDevice.getAngle((pitch, roll, yaw) -> {
            // yaw move to yaw+30
            move(pitch, roll, increase ? yaw + 30 : yaw - 30);
        });
    }

    private void move(float pitch, float roll, float yaw) {
        int durationMs = 5000;
        currentDevice.moveTo(pitch, roll, yaw, durationMs, completed -> {
            Log.d(TAG, completed ? "Move completed" : "Move failed");
            if (completed) {
                increase = !increase;
            }
        });
    }

    private void takePhoto(View view) {
        // TODO: 2023/3/10 拍照
    }
}
