package com.zhiyun.demo;

import android.app.Application;

import com.zhiyun.sdk.ZYDeviceSDK;

/**
 * @author lize
 * @date 2018/1/22 下午4:13
 * @description Copyright (c) 2017 桂林智神信息技术有限公司. All rights reserved.
 */
public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // only assets file
        // Please contact Zhiyun to obtain a certificate
        ZYDeviceSDK.init(this, "cert.json");
        // or
        // ZYDeviceSDK.init(this, Constants.APP_ID, Constants.KEY, Constants.CERT);

    }
}
