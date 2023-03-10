package com.zhiyun.demo;

/**
 * @author sjw
 * @date 2023-03-09
 * @description
 */
public class Utils {
    public enum DeviceKind{
        PHONE,
        CAMERA,
        OTHERS
    }
    public static DeviceKind kindOfDevice(String deviceName) {
        String lowerCase = deviceName.toLowerCase();
        if (lowerCase.startsWith("smooth")) {
            return DeviceKind.PHONE;
        } else if (lowerCase.startsWith("crane") || lowerCase.startsWith("weebill")) {
            return DeviceKind.CAMERA;
        }else {
            return DeviceKind.OTHERS;
        }
    }
}
