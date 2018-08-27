/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.secretmedia;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.upstream.AssetDataSource;
import com.google.android.exoplayer2.upstream.ContentDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import org.telegram.messenger.FileLoader;

import java.io.IOException;

public final class ExtendedDefaultDataSource implements DataSource {

    private static final String SCHEME_ASSET = "asset";
    private static final String SCHEME_CONTENT = "content";

    private final DataSource baseDataSource;
    private final DataSource fileDataSource;
    private final DataSource encryptedFileDataSource;
    private final DataSource assetDataSource;
    private final DataSource contentDataSource;

    private DataSource dataSource;
    private TransferListener listener;

    /**
     * Constructs a new instance, optionally configured to follow cross-protocol redirects.
     *
     * @param context                     A context.
     * @param listener                    An optional listener.
     * @param userAgent                   The User-Agent string that should be used when requesting remote data.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *                                    to HTTPS and vice versa) are enabled when fetching remote data.
     */
    public ExtendedDefaultDataSource(Context context, TransferListener listener,
                                     String userAgent, boolean allowCrossProtocolRedirects) {
        this(context, listener, userAgent, DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, allowCrossProtocolRedirects);
    }

    /**
     * Constructs a new instance, optionally configured to follow cross-protocol redirects.
     *
     * @param context                     A context.
     * @param listener                    An optional listener.
     * @param userAgent                   The User-Agent string that should be used when requesting remote data.
     * @param connectTimeoutMillis        The connection timeout that should be used when requesting remote
     *                                    data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param readTimeoutMillis           The read timeout that should be used when requesting remote data,
     *                                    in milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *                                    to HTTPS and vice versa) are enabled when fetching remote data.
     */
    public ExtendedDefaultDataSource(Context context, TransferListener listener,
                                     String userAgent, int connectTimeoutMillis, int readTimeoutMillis,
                                     boolean allowCrossProtocolRedirects) {
        this(context, listener,
                new DefaultHttpDataSource(userAgent, null, listener, connectTimeoutMillis,
                        readTimeoutMillis, allowCrossProtocolRedirects, null));
    }

    /**
     * Constructs a new instance that delegates to a provided {@link DataSource} for URI schemes other
     * than file, asset and content.
     *
     * @param context        A context.
     * @param listener       An optional listener.
     * @param baseDataSource A {@link DataSource} to use for URI schemes other than file, asset and
     *                       content. This {@link DataSource} should normally support at least http(s).
     */
    public ExtendedDefaultDataSource(Context context, TransferListener listener,
                                     DataSource baseDataSource) {
        this.baseDataSource = Assertions.checkNotNull(baseDataSource);
        this.fileDataSource = new FileDataSource(listener);
        this.encryptedFileDataSource = new EncryptedFileDataSource(listener);
        this.assetDataSource = new AssetDataSource(context, listener);
        this.contentDataSource = new ContentDataSource(context, listener);
        this.listener = listener;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Assertions.checkState(dataSource == null);
        // Choose the correct source for the scheme.
        String scheme = dataSpec.uri.getScheme();
        if (Util.isLocalFileUri(dataSpec.uri)) {
            if (dataSpec.uri.getPath().startsWith("/android_asset/")) {
                dataSource = assetDataSource;
            } else {
                if (dataSpec.uri.getPath().endsWith(".enc")) {
                    dataSource = encryptedFileDataSource;
                } else {
                    dataSource = fileDataSource;
                }
            }
        } else if ("tg".equals(scheme)) {
            dataSource = FileLoader.getStreamLoadOperation(listener);
        } else if (SCHEME_ASSET.equals(scheme)) {
            dataSource = assetDataSource;
        } else if (SCHEME_CONTENT.equals(scheme)) {
            dataSource = contentDataSource;
        } else {
            dataSource = baseDataSource;
        }
        // Open the source and return.
        return dataSource.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return dataSource.read(buffer, offset, readLength);
    }

    @Override
    public Uri getUri() {
        return dataSource == null ? null : dataSource.getUri();
    }

    @Override
    public void close() throws IOException {
        if (dataSource != null) {
            try {
                dataSource.close();
            } finally {
                dataSource = null;
            }
        }
    }
}
