package com.walid.rxretrofit.utils;

import android.util.Log;

import com.walid.rxretrofit.BuildConfig;

/**
 * Created by walid on 16/8/10.
 * log 日志
 */
public class Logger {

    private static final String TAG = "shuidihuzhu";

    public static void d(String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, buildMessage(msg));
        }
    }

    public static void d(Object obj) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, buildMessage(String.valueOf(obj)));
        }
    }

    public static void v(String msg) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, buildMessage(msg));
        }
    }

    public static void i(String msg) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, buildMessage(msg));
        }
    }

    public static void w(String msg) {
        Log.w(TAG, buildMessage(msg));
    }

    public static void e(String msg) {
        if (BuildConfig.DEBUG) {
            String errorMsg = buildMessage(msg);
            Log.e(TAG, errorMsg);
        }
    }

    private static String buildMessage(String msg) {
        StackTraceElement caller = new Throwable().fillInStackTrace().getStackTrace()[2];
        return "### " + caller.toString() + msg;
    }

}
