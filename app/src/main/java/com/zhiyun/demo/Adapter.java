package com.zhiyun.demo;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.polidea.rxandroidble2.RxBleDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * @author sjw
 * @date 2020-12-11
 * @description
 */
public class Adapter extends BaseAdapter {
    private List<RxBleDevice> devices = new ArrayList<>();

    public void add(RxBleDevice device) {
        if (!devices.contains(device)) {
            this.devices.add(device);
        }
    }

    public void addAll(List<RxBleDevice> devices) {
        this.devices.addAll(devices);
    }

    public void clear() {
        this.devices.clear();
    }


    @Override
    public int getCount() {
        return this.devices.size();
    }

    @Override
    public Object getItem(int position) {
        return this.devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = View.inflate(parent.getContext(), R.layout.item_ble, null);
        }

        TextView name = ViewHolder.get(convertView, R.id.name);
        TextView mac  = ViewHolder.get(convertView, R.id.mac);
        TextView rssi = ViewHolder.get(convertView, R.id.rssi);
        Button connect = ViewHolder.get(convertView, R.id.connect);


        final RxBleDevice device = devices.get(position);

        name.setText(device.getName());
        mac.setText(device.getMacAddress());

        connect.setOnClickListener(v -> onConnectClicked((Button)v, device));
        return convertView;
    }

    void onConnectClicked(Button view, RxBleDevice device) {

    }
}
