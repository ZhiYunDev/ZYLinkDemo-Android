package com.zhiyun.demo;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.zhiyun.sdk.device.Device;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lize
 * @date 2018/1/23 上午10:27
 * @description Copyright (c) 2017 桂林智神信息技术有限公司. All rights reserved.
 */
public class BleAdapter extends BaseAdapter {

    private List<Device> devices = new ArrayList<>();

    public void add(Device device) {
        if (!devices.contains(device)) {
            this.devices.add(device);
        }
    }

    public void addAll(List<Device> devices) {
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

        final Device device = devices.get(position);

        name.setText(device.getModelName());
        mac.setText(device.getIdentifier());
        rssi.setText(String.valueOf(device.getRSSI()));

        connect.setOnClickListener(v -> onConnectClicked((Button)v, device));
        return convertView;
    }

    void onConnectClicked(Button view, Device device) {

    }
}
