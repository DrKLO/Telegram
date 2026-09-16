/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.secretmedia;

import android.content.Context;
import android.net.Uri;
import android.util.LongSparseArray;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.TransferListener;

@OptIn(markerClass = UnstableApi.class)
public final class ExtendedDefaultDataSourceFactory implements DataSource.Factory {

    private final Context context;
    private final TransferListener listener;
    private final DataSource.Factory baseDataSourceFactory;

    /**
     * @param context A context.
     * @param userAgent The User-Agent string that should be used.
     */
    public ExtendedDefaultDataSourceFactory(Context context, String userAgent) {
        this(context, userAgent, null);
    }

    /**
     * @param context A context.
     * @param userAgent The User-Agent string that should be used.
     * @param listener An optional listener.
     */
    public ExtendedDefaultDataSourceFactory(Context context, String userAgent,
                                    TransferListener listener) {
        this(context, listener, new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setTransferListener(listener)
                .setAllowCrossProtocolRedirects(true));
    }

    /**
     * @param context A context.
     * @param listener An optional listener.
     * @param baseDataSourceFactory A {@link DataSource.Factory} to be used to create a base {@link DataSource}
     *     for {@link DefaultDataSource}.
     * @see DefaultDataSource#DefaultDataSource(Context, TransferListener, DataSource)
     */
    public ExtendedDefaultDataSourceFactory(Context context, TransferListener listener,
                                    DataSource.Factory baseDataSourceFactory) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.baseDataSourceFactory = baseDataSourceFactory;
    }

    private final LongSparseArray<Uri> mtprotoUris = new LongSparseArray<>();

    @Override
    public ExtendedDefaultDataSource createDataSource() {
        return new ExtendedDefaultDataSource(context, listener, baseDataSourceFactory.createDataSource(), mtprotoUris);
    }

    public void putDocumentUri(long docId, Uri uri) {
        mtprotoUris.put(docId, uri);
    }

}