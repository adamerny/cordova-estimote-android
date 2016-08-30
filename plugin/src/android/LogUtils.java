package com.evothings;

import android.util.Log;

public class LogUtils {
    public static int d(final String tag, String message) {
       return d(tag, message, null);
    }

    public static int d(final String tag, String message, Throwable tr) {
        if (EstimoteBeacons.getVerboseMode()) {
            if (tr != null) return Log.d(tag, message, tr);
            return Log.d(tag, message);
        }
        return 0;
    }

    public static int v(final String tag, String message) {
        return v(tag, message, null);
    }

    public static int v(final String tag, String message, Throwable tr) {
        if (EstimoteBeacons.getVerboseMode()) {
            if (tr != null) return Log.d(tag, message, tr);
            return Log.v(tag, message);
        }
        return 0;
    }

    public static int i(final String tag, String message) {
        return i(tag, message, null);
    }

    public static int i(final String tag, String message, Throwable tr) {
        if (EstimoteBeacons.getVerboseMode()) {
            if (tr != null) return Log.d(tag, message, tr);
            return Log.i(tag, message);
        }
        return 0;
    }

    public static int w(final String tag, String message) {
        return w(tag, message, null);
    }

    public static int w(final String tag, String message, Throwable tr) {
        if (EstimoteBeacons.getVerboseMode()) {
            if (tr != null) return Log.d(tag, message, tr);
            return Log.w(tag, message);
        }
        return 0;
    }

    public static int e(final String tag, String message) {
        return e(tag, message, null);
    }

    public static int e(final String tag, String message, Throwable tr) {
        if (EstimoteBeacons.getVerboseMode()) {
            if (tr != null) return Log.d(tag, message, tr);
            return Log.e(tag, message);
        }
        return 0;
    }

}
