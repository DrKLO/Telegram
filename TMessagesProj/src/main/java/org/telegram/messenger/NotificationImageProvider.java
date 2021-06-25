package org.telegram.messenger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class NotificationImageProvider extends ContentProvider implements NotificationCenter.NotificationCenterDelegate {

	public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".notification_image_provider";

	private static final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

	static {
		matcher.addURI(AUTHORITY, "msg_media_raw/#/*", 1); // content://org.telegram..../msg_media_raw/account/filename.ext
	}

	private HashSet<String> waitingForFiles = new HashSet<>();
	private final Object sync = new Object();
	private HashMap<String, Long> fileStartTimes = new HashMap<>();

	@Override
	public boolean onCreate() {
		for (int i = 0; i < UserConfig.getActivatedAccountsCount(); i++) {
			NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.fileLoaded);
		}
		return true;
	}

	@Override
	public void shutdown() {
		for (int i = 0; i < UserConfig.getActivatedAccountsCount(); i++) {
			NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.fileLoaded);
		}
	}

	@Nullable
	@Override
	public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
		return null;
	}

	@Nullable
	@Override
	public String getType(@NonNull Uri uri) {
		return null;
	}

	@Nullable
	@Override
	public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
		return null;
	}

	@Override
	public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
		return 0;
	}

	@Override
	public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
		return 0;
	}

	@Nullable
	@Override
	public String[] getStreamTypes(@NonNull Uri uri, @NonNull String mimeTypeFilter) {
		if (mimeTypeFilter.startsWith("*/") || mimeTypeFilter.startsWith("image/")) {
			return new String[]{"image/jpeg", "image/png", "image/webp"};
		}
		return null;
	}

	@Nullable
	@Override
	public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
		if (!"r".equals(mode)) {
			throw new SecurityException("Can only open files for read");
		}
		if (matcher.match(uri) == 1) {
			List<String> path = uri.getPathSegments();
			int account = Integer.parseInt(path.get(1));
			String name = path.get(2);
			String finalPath = uri.getQueryParameter("final_path");
			String fallbackPath = uri.getQueryParameter("fallback");
			File finalFile = new File(finalPath);
			ApplicationLoader.postInitApplication();
			if (AndroidUtilities.isInternalUri(Uri.fromFile(finalFile))) {
				throw new SecurityException("trying to read internal file");
			}
			if (!finalFile.exists()) {
				Long _startTime = fileStartTimes.get(name);
				long startTime = _startTime != null ? _startTime : System.currentTimeMillis();
				if (_startTime == null) {
					fileStartTimes.put(name, startTime);
				}
				while (!finalFile.exists()) {
					if (System.currentTimeMillis() - startTime >= 3000) {
						if (BuildVars.LOGS_ENABLED) {
							FileLog.w("Waiting for " + name + " to download timed out");
						}
						if (TextUtils.isEmpty(fallbackPath)) {
							throw new FileNotFoundException("Download timed out");
						}
						File file = new File(fallbackPath);
						if (AndroidUtilities.isInternalUri(Uri.fromFile(file))) {
							throw new SecurityException("trying to read internal file");
						}
						return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
					}
					synchronized (sync) {
						waitingForFiles.add(name);
						try {
							sync.wait(1000);
						} catch (InterruptedException ignore) {
						}
					}
				}
				if (AndroidUtilities.isInternalUri(Uri.fromFile(finalFile))) {
					throw new SecurityException("trying to read internal file");
				}
			}
			return ParcelFileDescriptor.open(finalFile, ParcelFileDescriptor.MODE_READ_ONLY);
		}
		throw new FileNotFoundException("Invalid URI");
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.fileLoaded) {
			synchronized (sync) {
				String name = (String) args[0];
				if (waitingForFiles.remove(name)) {
					fileStartTimes.remove(name);
					sync.notifyAll();
				}
			}
		}
	}
}
