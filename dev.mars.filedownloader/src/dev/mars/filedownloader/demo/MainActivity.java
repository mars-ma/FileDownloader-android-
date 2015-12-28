package dev.mars.filedownloader.demo;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import dev.mars.filedownloader.DownloadUtil;
import dev.mars.filedownloader.DownloadUtil.DownloadCallBack;
import dev.mars.filedownloader.R;
import dev.mars.filedownloader.R.id;
import dev.mars.filedownloader.R.layout;

public class MainActivity extends Activity implements OnClickListener {

	Button btnDownload;
	private Handler mHandler = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		btnDownload = (Button) findViewById(R.id.btnDownload);
		btnDownload.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnDownload:
			try {
				downloadFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		}
	}

	private void downloadFile() throws IOException {
		File destFile = File.createTempFile("" + System.currentTimeMillis(), null, getCacheDir());
		DownloadUtil.download(
				"http://dlsw.baidu.com/sw-search-sp/soft/b1/17483/QQPhoneManager_5.6.1.4951.1449216051.exe",
				destFile.getPath(), true, new DownloadCallBack() {

					@Override
					public void onUpdateProgress(final int progress) {
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								btnDownload.setText("下载进度 " + progress + " %");
							}
						});
					}

					@Override
					public void onStart() {
						mHandler.post(new Runnable() {

							@Override
							public void run() {
								btnDownload.setEnabled(false);
								btnDownload.setText("开始下载...");
							}
						});
					}

					@Override
					public void onFinished() {
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								btnDownload.setText("下载完成");
								btnDownload.setEnabled(true);
							}
						});
					}

					@Override
					public void onError(Exception e) {
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								btnDownload.setText("下载失败");
								btnDownload.setEnabled(true);
							}
						});
					}
				},false);
	}

}
