package dev.mars.filedownloader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Log;

/**
 * 
 * @author mars-ma 20151227
 *
 */
public class DownloadUtil {

	private static int MultiThreadNum = 16;

	private static ExecutorService singleTaskPool = Executors.newSingleThreadScheduledExecutor();
	private static ExecutorService thread_pool = Executors.newFixedThreadPool(MultiThreadNum); // 固定线程数
																								// 4

	private static int blockSize = 1024 * 1024 ; // 2MB

	public static void download(final String urlAddress, final String path, final boolean cover,
			final DownloadCallBack callback,final boolean noProgress) {
		Runnable download = new Runnable() {
			@Override
			public void run() {
				long startTime = System.currentTimeMillis();
				if (callback != null) {
					callback.onStart();
					if (callback instanceof DownloadCallBackExpand) {
						((DownloadCallBackExpand) callback).onStart(urlAddress, path, cover);
					}
				}
				Logger.e("DownloadUtil", "url " + urlAddress + ";path " + path + ";cover " + cover);

				File file = new File(path);
				// 如果目标文件已经存在，则删除。产生覆盖旧文件的效果
				if (!file.getParentFile().exists()) {
					file.getParentFile().mkdirs();
				}
				if (file.exists()) {
					if (!cover) {
						if (callback != null) {
							callback.onFinished();
						}
						return;
					} else
						file.delete();
				}
				try {
					// 构造URL
					URL url = new URL(urlAddress);
					// 打开连接
					URLConnection con = url.openConnection();
					// 获得文件的长度
					int contentLength = con.getContentLength();
					Logger.e("长度 :" + contentLength / 1024 + "KB");

					if (contentLength > blockSize&&!noProgress) {
						Logger.e("文件长度大于2MB,使用多线程模式 blockSize " + blockSize + " B");
						int threadNum = contentLength % blockSize == 0 ? (contentLength / blockSize)
								: (contentLength / blockSize) + 1;
						Logger.e("需要执行次数 " +threadNum);
						MultiFileDownloadRunnable[] runnables = new MultiFileDownloadRunnable[threadNum];
						for (int i = 0; i < threadNum; i++) {
							// 启动线程，分别下载每个线程需要下载的部分
							int endPos=0;
							if(i==threadNum-1){
								//当总长度被blockSize整除或不整除最后一个线程的结束位置区分
								endPos = contentLength % blockSize == 0 ?((i + 1) * blockSize - 1):(contentLength-1);
							}else{
								endPos = (i + 1) * blockSize - 1;
							}
							runnables[i] = new MultiFileDownloadRunnable(i,url, file, i * blockSize,
									endPos);
							thread_pool.execute(runnables[i]);
						}

						boolean isfinished = false;
						int downloadedAllSize = 0;

						while (!isfinished) {
							isfinished = true;
							// 当前所有线程下载总量
							downloadedAllSize = 0;
							for (int i = 0; i < runnables.length; i++) {
								downloadedAllSize += runnables[i].getDownloadSize();
								if (!runnables[i].isFinished()) {
									Logger.e("threadId "+i+" 未完成 总共 "+ runnables[i].getTotalSize()+" 剩余 "+ runnables[i].getRemainSize());
									isfinished = false;
								}
							}
							if (callback != null) {
								int progress = (int) (((float) downloadedAllSize / contentLength) * 100);
								Logger.e("downloadedAllSize " + downloadedAllSize + " contentLength " + contentLength);
								callback.onUpdateProgress(progress);
								if (callback instanceof DownloadCallBackExpand) {
									((DownloadCallBackExpand) callback).onUpdateProgress(urlAddress, path, cover,
											progress);
								}
							}
							Thread.sleep(100);// 休息1秒后再读取下载进度
						}
						
						if (callback != null) {
							callback.onFinished();
							if (callback instanceof DownloadCallBackExpand) {
								((DownloadCallBackExpand) callback).onFinished(urlAddress, path, cover);
							}
						}
					} else {
						Logger.e("文件长度小于2MB,使用单线程模式");
						// 输入流
						InputStream is = con.getInputStream();
						// 1K的数据缓冲
						byte[] bs = new byte[1024];
						// 读取到的数据长度
						int len;
						// 输出的文件流
						OutputStream os = new FileOutputStream(path);
						// 开始读取
						int downloadedByte = 0;
						while ((len = is.read(bs)) != -1) {
							os.write(bs, 0, len);
							if (callback != null&&!noProgress&&contentLength>0) {
								downloadedByte += len;
								int progress = (int) (((float) downloadedByte / contentLength) * 100);
								Logger.e("downloadedByte " + downloadedByte + " contentLength " + contentLength);
								callback.onUpdateProgress(progress);
								if (callback instanceof DownloadCallBackExpand) {
									((DownloadCallBackExpand) callback).onUpdateProgress(urlAddress, path, cover,
											progress);
								}
							}
						}
						// 完毕，关闭所有链接
						os.close();
						is.close();

						if (callback != null) {
							callback.onFinished();
							if (callback instanceof DownloadCallBackExpand) {
								((DownloadCallBackExpand) callback).onFinished(urlAddress, path, cover);
							}
						}
					}
					Logger.e("下载用时 "+(System.currentTimeMillis()-startTime)/1000+" s");
				} catch (Exception e) {
					e.printStackTrace();
					if (callback != null) {
						callback.onError(e);
						if (callback instanceof DownloadCallBackExpand) {
							((DownloadCallBackExpand) callback).onError(urlAddress, path, e);
						}
					}
				}
			}
		};
		singleTaskPool.execute(download);
	}

	public interface DownloadCallBack {
		public abstract void onUpdateProgress(int progress);

		public abstract void onFinished();

		public abstract void onStart();

		public abstract void onError(Exception e);
	}

	public interface DownloadCallBackExpand extends DownloadCallBack {
		public abstract void onUpdateProgress(String url, String destPath, boolean cover, int progress);

		public abstract void onFinished(String url, String destPath, boolean cover);

		public abstract void onStart(String url, String destPath, boolean cover);

		public abstract void onError(String url, String destPath, Exception e);
	}

	private static class MultiFileDownloadRunnable implements Runnable {
		private static final int BUFFER_SIZE = 1024;
		private URL url;
		private File file;
		private int startPosition;
		private int endPosition;
		private int curPosition;
		// 标识当前线程是否下载完成
		private boolean finished = false;
		private int downloadSize = 0;
		
		int threadId=0;

		public MultiFileDownloadRunnable(URL url, File file, int startPosition, int endPosition) {
			this.url = url;
			this.file = file;
			this.startPosition = startPosition;
			this.curPosition = startPosition;
			this.endPosition = endPosition;
		}

		public MultiFileDownloadRunnable(int i, URL url2, File file2, int j, int endPos) {
			this(url2, file2, j, endPos);
			threadId = i;
			Logger.e("MultiFileDownloadRunnable threadId "+threadId +" 开始 "+j+" 结束 "+endPos);
		}

		@Override
		public void run() {
			BufferedInputStream bis = null;
			RandomAccessFile fos = null;
			byte[] buf = new byte[BUFFER_SIZE];
			URLConnection con = null;
			try {
				con = url.openConnection();
				con.setAllowUserInteraction(true);
				// 设置当前线程下载的起止点
				con.setRequestProperty("Range", "bytes=" + startPosition + "-" + endPosition);
				Log.i("bb", Thread.currentThread().getName() + "  bytes=" + startPosition + "-" + endPosition);
				// 使用java中的RandomAccessFile 对文件进行随机读写操作
				fos = new RandomAccessFile(file, "rw");
				// 设置写文件的起始位置
				fos.seek(startPosition);
				bis = new BufferedInputStream(con.getInputStream());
				// 开始循环以流的形式读写文件
				while (curPosition < endPosition) {
					int len = bis.read(buf, 0, BUFFER_SIZE);
					if (len == -1) {
						break;
					}
					fos.write(buf, 0, len);
					curPosition = curPosition + len;
					if (curPosition > endPosition) {
						downloadSize += len - (curPosition - endPosition) + 1;
					} else {
						downloadSize += len;
					}
				}
				// 下载完成设为true
				Logger.e("MultiFileDownloadRunnable threadId "+threadId+" 完成");
				this.finished = true;
				bis.close();
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public boolean isFinished() {
			return finished;
		}

		public int getDownloadSize() {
			return downloadSize;
		}
		
		public int getTotalSize(){
			return endPosition - startPosition;
		}
		
		public int getRemainSize(){
			return getTotalSize() -getDownloadSize();
		}
	}

}
