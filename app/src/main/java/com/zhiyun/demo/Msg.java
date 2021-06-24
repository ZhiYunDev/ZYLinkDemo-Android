package com.zhiyun.demo;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.StringRes;

/**
 * Created by liz on 2021/5/17.
 */
public class Msg {

    public static void show(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    public static void show(Context context, @StringRes int text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }


}
