package com.zhiyun.demo;

public class HexStrings {

    private static char byte2HexChar(int data) {
        if (data >= 0 && data <= 16) {
            if (data >= 10) {
                return (char) (data - 10 + 'A');
            } else {
                return (char) (data + '0');
            }
        }
        return '0';
    }

    private static byte hexChar2Byte(char c) {
        if (c >= '0' && c <= '9') {
            return (byte) (c - '0');
        } else if (c >= 'A' && c <= 'G') {
            return (byte) (c - 'A' + 10);
        } else if (c >= 'a' && c <= 'g') {
            return (byte) (c - 'a' + 10);
        }
        return 0;
    }

    /**
     * byte[]转换成十六进制数据字符串
     */
    public static String byteArr2HexStr(byte[] data) {
        char[] c = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            c[i * 2] = byte2HexChar((data[i] & 0x0f0) >> 4);
            c[i * 2 + 1] = byte2HexChar(data[i] & 0x0f);
        }
        return new String(c);
    }

    /**
     * 十六进制数据字符串转换成byte[]
     */
    public static byte[] hexStr2ByteArr(String hexStr) {
        char[] chars = hexStr.toCharArray();
        byte[] bytes = new byte[chars.length / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = hexChar2Byte(chars[i * 2]);
            int low = hexChar2Byte(chars[i * 2 + 1]);
            bytes[i] = (byte) (((high << 4) & 0x0f0) + low);
        }
        return bytes;
    }
}