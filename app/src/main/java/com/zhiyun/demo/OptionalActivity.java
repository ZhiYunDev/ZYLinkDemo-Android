package com.zhiyun.demo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zhiyun.protocol.utils.Arrays;
import com.zhiyun.protocol.utils.CRC16;
import com.zhiyun.protocol.utils.Ids;
import com.zhiyun.sdk.DeviceManager;
import com.zhiyun.sdk.callbaks.Callback;
import com.zhiyun.sdk.device.Device;

public class OptionalActivity extends AppCompatActivity {

    private static final String TAG = "OptionalActivity";

    private static final String EXTRA_IDENTIFIER = "extra_identifier";
    private Device device;

    public static void startActivity(Context context, String identifier) {
        Intent intent = new Intent(context, OptionalActivity.class);
        intent.putExtra(EXTRA_IDENTIFIER, identifier);
        context.startActivity(intent);
    }

    private TextView angle;
    private TextView connection;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_optional);
        angle = findViewById(R.id.angle);
        connection = findViewById(R.id.connection);

        findViewById(R.id.show_angle).setOnClickListener(this::showAngle);
        findViewById(R.id.move_to).setOnClickListener(this::moveTo);
        findViewById(R.id.cancel_move).setOnClickListener(this::cancelMove);
        // Only for Crane M2
        findViewById(R.id.phone_mode).setOnClickListener(this::setPhoneMode);
        findViewById(R.id.camera_model).setOnClickListener(this::setCameraMode);
        findViewById(R.id.send_data_set_L_model).setOnClickListener(this::setLMode);
        findViewById(R.id.send_data_switch_to_horizontal).setOnClickListener(this::setHorizontal);
        findViewById(R.id.send_data_switch_to_vertical).setOnClickListener(this::setVertical);

        String identifier = getIntent().getStringExtra(EXTRA_IDENTIFIER);
        device = DeviceManager.getInstance().queryDevice(identifier);
        if (device != null) {
            device.setStateListener(this::updateConnectionState);
            boolean connected = device.isConnected();
            updateConnectionState(connected ? Device.TO_BE_CONNECTED : Device.NO_CONNECTION);
        } else {
            Toast.makeText(this, "Device not connected", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (device != null && !device.isConnected()) {
            device.connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (device != null) {
            device.disconnect();
        }
    }

    private void updateConnectionState(int state) {
        switch (state) {
            case Device.CONNECTING:
                connection.setText(R.string.connecting);
                break;
            case Device.TO_BE_CONNECTED:
                connection.setText(R.string.connected);
                break;
            case Device.TO_BE_MISSED:
                connection.setText(R.string.disconnecting);
                break;
            case Device.NO_CONNECTION:
            default:
                connection.setText(R.string.disconnected);
                Msg.show(this, R.string.disconnected);
                finish();
                break;
        }
    }

    private void showAngle(View view) {
        device.getAngle((pitch, roll, yaw) -> {
            angle.setText(getString(R.string.angle, pitch, roll, yaw));
        });
    }

    private void moveTo(View view) {
        device.getAngle((pitch, roll, yaw) -> {
            // yaw move to yaw+30
            move(pitch, roll, yaw + 30);
        });
    }

    private void move(float pitch, float roll, float yaw) {
        int durationMs = 5000;
        device.moveTo(pitch, roll, yaw, durationMs, new Callback<Boolean>() {
            @Override
            public void call(Boolean completed) {
                Log.d(TAG, completed ? "Move completed" : "Move failed");
            }
        });
    }

    private void cancelMove(View view) {
        device.cancelMove();
    }

    // Only for Crane M2
    private void setPhoneMode(View view) {
        device.setPhoneMode();
    }

    // Only for Crane M2
    private void setCameraMode(View view) {
        device.setCameraMode();
    }

    /**
     * Sending data to stabilizer (processing CRC and splicing data).
     *
     * @param head    head data
     * @param content body data
     */
    private void sendData(byte[] head, byte[] content) {
        // Take content as the parameter to obtain content+CRC.
        byte[] contentAndCrc = CRC16.crc(content, false);
        // Splice data. The data after splicing is head+content+CRC
        byte[] sendData = Arrays.concat(head, contentAndCrc);
        Log.i(TAG, "send Data: " + Arrays.toHexString(sendData));
        // send data
        device.send(sendData);
    }

    /**
     * Set to Lock Mode.
     */
    private void setLMode(View view) {
        byte index = Ids.provideNextBlId();
        // lock mode
        byte mode = 0x01;

        byte[] head = {0x24, 0x3c, 0x08, 0x00};
        byte[] content = {0x18, 0x12, index, 0x01, 0x27, (byte) 0x80, mode, 0x00};
        sendData(head, content);
    }

    /**
     * Set the direction to horizontal.
     */
    private void setHorizontal(View view) {
        setOrientation((byte) 0xaa, (byte) 0x55);
    }

    /**
     * Set the direction to vertical.
     */
    private void setVertical(View view) {
        setOrientation((byte) 0xa5, (byte) 0xa5);
    }

    /**
     * Set shooting direction.
     *
     * @param orientationHigh High bit of direction parameter
     * @param orientationLow  Low order of direction parameter
     */
    private void setOrientation(byte orientationHigh, byte orientationLow) {
        byte index = Ids.provideNextBlId();
        byte[] head = {0x24, 0x3c, 0x08, 0x00};
        byte[] content = {0x18, 0x12, index, 0x01, (byte) 0xa1, (byte) 0xc0, orientationHigh, orientationLow};
        sendData(head, content);
    }
}
