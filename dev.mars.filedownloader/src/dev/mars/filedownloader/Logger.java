package dev.mars.filedownloader;

import android.util.Log;

public class Logger {
	public static boolean DEBUG = BuildConfig.DEBUG;
	private static final String TAG = Logger.class.getSimpleName();

	public static void e(String str) {
		if (DEBUG)
			Log.e(TAG, str);
	}
	
	public static void e(String tag,String str) {
		if (DEBUG)
			Log.e(tag, str);
	}
	
}
