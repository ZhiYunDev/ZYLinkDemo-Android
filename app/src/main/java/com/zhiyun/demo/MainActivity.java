package com.zhiyun.demo;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;
import com.zhiyun.protocol.constants.Model;
import com.zhiyun.sdk.DeviceManager;
import com.zhiyun.sdk.RxSender;
import com.zhiyun.sdk.callbaks.Callback;
import com.zhiyun.sdk.device.Device;
import com.zhiyun.sdk.device.DeviceControl;
import com.zhiyun.sdk.util.BTUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

/**
 * 自定义蓝牙数据收发，需要自行实现收发队列。Demo中使用开源蓝牙框架RxAndroidBle来实现
 * https://github.com/Polidea/RxAndroidBle
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    boolean isCanning = false;
    MenuItem mScanningMenu;
    MenuItem mScanMenu;
    TextView angleTxt;

    private ListView mDevices;
    private BleAdapter bleAdapter;
    private List<Device> mConnectDevices = new ArrayList<>();
    //是否为用户自行实现收发数据,需手动改变
    private final boolean clientUsed = true;

    private RxBleClient bleClient;
    private Disposable notificationDisposable;
    private StatusListener mStatusListener;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        angleTxt = findViewById(R.id.angle_txt);
        setView();
        if (clientUsed) {
            bleClient = RxBleClient.create(getApplicationContext());
            setListener();
        }
    }

    public void setListener() {
        findViewById(R.id.cancel_move_btn).setOnClickListener(this::cancelMove);
        findViewById(R.id.show_angle_btn).setOnClickListener(this::showAngle);
        findViewById(R.id.move_btn).setOnClickListener(this::moveTo);
    }

    private void showAngle(View view) {
        if (control != null) {
            control.getAngle((pitch, roll, yaw) -> {
                angleTxt.setText(getString(R.string.angle, pitch, roll, yaw));
            });
        }
    }

    private void moveTo(View view) {
        if (control != null) {
            control.getAngle((pitch, roll, yaw) -> {
                // yaw move to yaw+30
                MainActivity.this.move(pitch, roll, yaw + 60);
            });
        }
    }

    private void move(float pitch, float roll, float yaw) {
        int durationMs = 2000;
        if (control != null) {
            control.moveTo(pitch, roll, yaw, durationMs, new Callback<Boolean>() {
                @Override
                public void call(Boolean completed) {
                    Log.d(TAG, completed ? "Move completed" : "Move failed");
                }
            });
        }
    }

    private void cancelMove(View view) {
        if (control != null) {
            control.cancelMove();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mScanningMenu = menu.findItem(R.id.scanning);
        mScanMenu = menu.findItem(R.id.scan);
        mScanningMenu.setActionView(R.layout.progress).setVisible(false);
        return true;
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
                    stopScanBle(clientUsed);
                } else {
                    startScanBle(clientUsed);
                }

                break;
            }
        }
        return true;
    }

    /**
     * Disconnected status
     */
    public static final int NO_CONNECTION = 0;

    /**
     * Connecting status
     */
    public static final int CONNECTING = 1;

    /**
     * Connected status
     */
    public static final int TO_BE_CONNECTED = 2;

    /**
     * Disconnecting status
     */
    public static final int TO_BE_MISSED = 3;

    @IntDef({NO_CONNECTION, CONNECTING, TO_BE_CONNECTED, TO_BE_MISSED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DeviceStatus {
    }

    private int transformConnectionState(RxBleConnection.RxBleConnectionState state) {
        switch (state) {
            case CONNECTING:
                return CONNECTING;
            case CONNECTED:
                return TO_BE_CONNECTED;
            case DISCONNECTING:
                return TO_BE_MISSED;
            case DISCONNECTED:
            default:
                return NO_CONNECTION;
        }
    }

    protected void onConnectionStateChanged(int state) {
        if (state == TO_BE_CONNECTED) {
            enableNotification();
            RxSender sender = new RxSender() {
                @Override
                public byte[] send(byte[] data) {
                    return new byte[0];
                }

                @Override
                public Single<byte[]> rxSend(byte[] data) {
                    return writeCharacteristic(Constants.WRITE_UUID, data);
                }
            };
            control.setSender(sender);
        } else if (state == NO_CONNECTION) {
            disableNotification();
        }
    }

    public Observable<byte[]> setStarsNotification() {
        return setupNotification(Constants.STARS_UUID)
                .onErrorReturnItem(new byte[0]);
    }

    public Observable<byte[]> setSimpleNotification() {
        return setupNotification(Constants.SIMPLE_UUID)
                .onErrorReturnItem(new byte[0]);
    }

    public Observable<byte[]> setupNotification(UUID uuid) {
        return mConnection.setupNotification(uuid);
    }

    private void enableNotification() {
        Observable<Pair<Boolean, byte[]>> stars = setStarsNotification()
                .map(bytes -> Pair.create(false, bytes));
        Observable<Pair<Boolean, byte[]>> simple = setSimpleNotification()
                .map(bytes -> Pair.create(true, bytes));

        notificationDisposable = Observable.merge(stars, simple)
                .subscribe(this::onNotificationChanged, this::onNotificationFailure);
    }

    private void onNotificationChanged(Pair<Boolean, byte[]> pair) {
        control.addData(pair.first, pair.second);
    }

    private void onNotificationFailure(Throwable t) {
        Log.w(TAG, "onNotificationFailure", t);
    }

    private void disableNotification() {
        if (notificationDisposable != null) {
            notificationDisposable.dispose();
            notificationDisposable = null;
        }
    }

    protected void doConnectionStateChanged(int state) {
        Log.d(TAG, "doConnectionStateChanged: " + state);

        switch (state) {
            case CONNECTING:
                //noinspection connecting
                break;
            case TO_BE_CONNECTED:
                break;
            case TO_BE_MISSED:
                //noinspection disconnectin
                break;
            case NO_CONNECTION:
            default:
                compositeDisposable.clear();
                break;
        }
        onConnectionStateChanged(state);
        notifyStatus(state);
    }

    /**
     * 通知连接状态改变
     *
     * @param status
     */
    protected void notifyStatus(int status) {
        if (mStatusListener != null) {
            mStatusListener.onStateChanged(status);
        }
    }

    /**
     * Device connection status monitor
     */
    public interface StatusListener {

        /**
         * Connection status changed
         *
         * @param state new state
         */
        void onStateChanged(@DeviceStatus int state);
    }

    /**
     * Register device connection status monitor.
     *
     * @param listener {@link Device.StatusListener}
     */
    public void setStateListener(StatusListener listener) {
        mStatusListener = listener;
    }

    private void observeConnectionState() {
        Disposable disposable = mDevice.observeConnectionStateChanges()
                .map(this::transformConnectionState)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::doConnectionStateChanged, this::onConnectionFailure);
    }

    public void connect() {
        connect(false);
    }

    public void connect(boolean auto) {
        Disposable d = mConnection.connect(auto, 5 * 1000)
                .subscribe(this::onConnectionFinished, this::onConnectionFailure);
        compositeDisposable.add(d);
    }

    private void onConnectionFinished(RxBleConnection conn) {
        Log.d(TAG, "onConnectionFinished: ");
    }

    private void onConnectionFailure(Throwable t) {
        t.printStackTrace();
    }

    public void rxDisconnect() {
        mConnection.disconnect();
    }

    public Single<byte[]> writeCharacteristic(UUID uuid, byte[] data) {
        return mConnection.writeCharacteristic(uuid, data);
    }

    private void setView() {
        adapter = new Adapter() {
            @Override
            void onConnectClicked(Button view, RxBleDevice device) {
                mDevice = device;
                observeConnectionState();
                mConnection = new Connection(device);
                connect();
                stopScanBle(true);
                setStateListener(state -> updateConnectionState(state, view));
            }
        };

        bleAdapter = new BleAdapter() {
            @Override
            void onConnectClicked(final Button view, Device device) {
                if (!mConnectDevices.contains(device)) {
                    mConnectDevices.add(device);
                }
                if (device.isConnected()) {
                    device.disconnect();
                    //BleStabilizer stb = (BleStabilizer) device;
//                    stb.getAngle(null);
                    //stb.moveTo();
                } else {
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
                                    Log.d(TAG, msg);
                                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });

                    // stop scan
                    stopScanBle(clientUsed);
                    // Connect to the device
                    device.connect();
                }
            }
        };
        mDevices = findViewById(R.id.devices);
        if (clientUsed) {
            //mDevices.setAdapter(mBleAdapter);
            mDevices.setAdapter(adapter);
        } else {
            mDevices.setAdapter(bleAdapter);

        }
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
                key = "press1s";
                break;
            case Device.KEY_EVENT_PRESS_3S:
                key = "press2s";
                break;
            default:
                key = "Failed";
        }
        return key;
    }

    private void updateConnectionState(Device device, int state, Button view) {
        switch (state) {
            case Device.NO_CONNECTION:
                view.setText(R.string.connect);
                break;
            case Device.TO_BE_CONNECTED:
                view.setText(R.string.disconnect);
                OptionalActivity.startActivity(this, device.getIdentifier());
                break;
            case Device.TO_BE_MISSED:
            case Device.CONNECTING:
            default:
                break;
        }
    }

    private void updateConnectionState(int state, Button view) {
        switch (state) {
            case NO_CONNECTION:
                view.setText(R.string.connect);
                break;
            case TO_BE_CONNECTED:
                view.setText("Connected");
                break;
            case TO_BE_MISSED:
            case CONNECTING:
            default:
                break;
        }
    }

    private void toSetting() {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(intent);
    }

    private void startScanBle(boolean clientUsed) {

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

        if (clientUsed) {
            scan(new ScanCallback() {
                @Override
                public void callback(RxBleDevice device) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.add(device);
                            adapter.notifyDataSetChanged();

                        }
                    });
                }
            });
        } else {
            // start scan
            DeviceManager.getInstance().setScanCallback(new DeviceManager.ScanCallback() {
                @Override
                public void onCallback(Device device) {
                    bleAdapter.add(device);
                    MainActivity.this.runOnUiThread(() -> bleAdapter.notifyDataSetChanged());
                }
            });
            DeviceManager.getInstance().scan(DeviceManager.DeviceType.BLE);
        }
        // update ui
        mScanningMenu.setVisible(true);
        mScanMenu.setTitle(R.string.stop);
        isCanning = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clientUsed) {
            rxDisconnect();
        }
    }

    private void stopScanBle(boolean clientUsed) {
        // update ui
        mScanningMenu.setVisible(false);
        mScanMenu.setTitle(R.string.scan);
        isCanning = false;
        // cancel scan
        if (clientUsed) {
            cancelScan();
        } else {
            DeviceManager.getInstance().cancelScan();
        }
    }

    /**
     * 扫描回调接口
     */
    interface ScanCallback {

        /**
         * 扫描到设备的回调方法
         *
         * @param device 扫描到的设备
         */
        void callback(RxBleDevice device);
    }

    private Disposable scanDisposable;
    private DeviceControl control;
    private Adapter adapter;
    private RxBleDevice mDevice;
    private Connection mConnection;


    private boolean isScanning() {
        return scanDisposable != null;
    }

    public void cancelScan() {
        if (scanDisposable != null) {
            scanDisposable.dispose();
            scanDisposable = null;
        }
    }

    /**
     * 检查是否是智云设备
     */
    private boolean checkMatchResult(ScanResult result) {
        byte[] data = result.getScanRecord().getManufacturerSpecificData(0x0509);
        return data != null;
    }

    /**
     * 蓝牙扫描
     */
    public void scan(final ScanCallback callback) {
        if (!isScanning() && BTUtil.isSupportBle()) {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .build();
            scanDisposable = bleClient.scanBleDevices(settings)
                    .filter(this::checkMatchResult)
                    .map(result -> {
                        byte[] specificData = result.getScanRecord().getManufacturerSpecificData().valueAt(0);
                        Model model = DeviceControl.toModel(specificData);
                        control = new DeviceControl(model);
                        return result.getBleDevice();
                    })
                    .subscribe(callback::callback, throwable -> Log.w(TAG, throwable));
        }
    }
}

