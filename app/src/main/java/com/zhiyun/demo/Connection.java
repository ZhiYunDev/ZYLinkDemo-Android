package com.zhiyun.demo;

import android.os.Build;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;

import com.jakewharton.rx.ReplayingShare;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.Timeout;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleGattException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;

/**
 * 基于RxAndroidBle连接设备的封装。
 * 包含了蓝牙连接、数据收发等方法
 */
public class Connection {

    private static final String TAG = "Connection";

    private final PublishSubject<Boolean> disconnectTriggerSubject = PublishSubject.create();
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final RxBleDevice device;

    private Observable<RxBleConnection> conn;

    public Connection(RxBleDevice device) {
        this.device = device;
    }

    public Observable<RxBleConnection> connect(boolean auto, long timeout) {
        conn = prepareConnectionObservable(auto, timeout);
        return conn;
    }

    public void disconnect() {
        disconnectTriggerSubject.onNext(false);
    }

    public Single<Integer> readRssi() {
        return getConn().flatMapSingle(RxBleConnection::readRssi)
                .singleOrError();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Single<Integer> requestMtu(@IntRange(from = 23, to = 517) int mtu) {
        return getConn().flatMapSingle(o -> requestMtu(o, mtu))
                .singleOrError();
    }

    public Single<byte[]> writeCharacteristic(UUID uuid, byte[] data) {
        return getConn().flatMapSingle(o -> o.writeCharacteristic(uuid, data))
                .firstOrError();
    }

    public Observable<byte[]> setupNotification(UUID uuid) {
        return getConn().flatMap(o -> o.setupNotification(uuid))
                .flatMap(o -> o);
    }

    private Observable<RxBleConnection> getConn() {
        if (device.getConnectionState() != RxBleConnection.RxBleConnectionState.CONNECTED) {
            return Observable.error(BleDisconnectedException.adapterDisabled(device.getMacAddress()));
        } else {
            return conn;
        }
    }

    private Observable<RxBleConnection> prepareConnectionObservable(boolean auto, long timeout) {
        return device
                .establishConnection(auto, new Timeout(timeout, TimeUnit.MILLISECONDS))
                .takeUntil(disconnectTriggerSubject)
                .compose(ReplayingShare.instance())
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.w(TAG, throwable);
                    }
                });
    }

    /**
     * 判断是否是{@code GATT_INVALID_PDU}引发的异常.
     * <p>
     * {@code GATT_INVALID_PDU(4)}来自Android BLE堆栈的底层
     * <p>
     * HID设备通过系统设置连接后, APP第二次(含重启)发起MTU协商时会抛出该状态.
     * 但接收到的MTU是正确的, 因此忽略该异常状态
     * @param t 异常
     * @return 是否 {@code GATT_INVALID_PDU} 引发的异常
     */
    private boolean isGattInvalidPdu(Throwable t) {
        return (t instanceof BleGattException) && (((BleGattException)t).getStatus() == 4);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private SingleSource<Integer> requestMtu(RxBleConnection connection, int mtu) {
        final int oldMtu = connection.getMtu();
        return connection.requestMtu(mtu)
                .onErrorResumeNext(t -> isGattInvalidPdu(t)
                        ? Single.just(oldMtu)
                        : Single.error(t)
                );

    }
}