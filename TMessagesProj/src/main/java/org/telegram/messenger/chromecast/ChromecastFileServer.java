package org.telegram.messenger.chromecast;

import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.AssetDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.common.images.WebImage;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.secretmedia.ExtendedDefaultDataSourceFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

public class ChromecastFileServer extends NanoHTTPD {
    private static final String TAG = "CAST_SERVER";
    private static final int PORT = 61578;

    public static final ChromecastMedia ASSET_FALLBACK_FILE = ChromecastMedia.Builder.fromUri(Uri.parse("file:///android_asset/cast/default.png"), "/assets/default", ChromecastMedia.IMAGE_PNG).build();

    private static final ChromecastMedia[] ASSET_FILES = { ASSET_FALLBACK_FILE };
    private static final HashMap<String, ChromecastMedia> ASSET_FILES_MAP = new HashMap<>();
    static {
        for (ChromecastMedia m : ASSET_FILES) {
            ASSET_FILES_MAP.put(m.externalPath, m);
        }
    }


    private final DataSource.Factory mediaDataSourceFactory;
    private final DataSource.Factory assetDataSourceFactory;
    private final DataSource.Factory fileDataSourceFactory;

    public ChromecastFileServer() {
        super(PORT);

        assetDataSourceFactory = () -> new AssetDataSource(ApplicationLoader.applicationContext);
        fileDataSourceFactory = new FileDataSource.Factory();
        mediaDataSourceFactory = new ExtendedDefaultDataSourceFactory(ApplicationLoader.applicationContext, "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)");
    }

    private final HashMap<String, ChromecastMedia> castedFiles = new HashMap<>();
    private Pair<String, File> coverFile = null;


    private boolean started = false;

    public void addFileToCast (ChromecastMedia file) {
        castedFiles.put(file.externalPath, file);
        check();
    }

    public void removeFileFromCast (ChromecastMedia file) {
        castedFiles.remove(file.externalPath);
        check();
    }

    public void setCoverFile(String externalPath, File file) {
        if (externalPath == null || file == null) {
            if (coverFile != null && coverFile.second.exists()) {
                try {
                    coverFile.second.delete();
                } catch (Exception ignore) {}
            }
            coverFile = null;
        } else {
            coverFile = new Pair<>(externalPath, file);
        }
        check();
    }

    public File getCoverFile() {
        if (coverFile == null)
            return null;
        return coverFile.second;
    }

    public String getCoverPath() {
        if (coverFile == null)
            return null;
        return coverFile.first;
    }

    private void check () {
        if (castedFiles.isEmpty()) {
            if (started) {
                stop();
                started = false;
            }
        } else {
            if (!started) {
                try {
                    start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
                    started = true;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private final AtomicInteger reqId = new AtomicInteger();

    @Override
    public Response serve(IHTTPSession session) {
        final int reqId = this.reqId.incrementAndGet();

        Log.d(TAG, "Request " + reqId + " " + session.getMethod() + " " + session.getUri() + " " + session.getHeaders().get("range"));
        try {
            // Log.d(TAG, "Response " + reqId);
            return addCorsHeaders(serveImpl(session));
        } catch (Throwable e) {
            Log.d(TAG, "Error " + reqId);
            return addCorsHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error reading file"));
        }
    }

    private Response serveImpl(IHTTPSession session) throws Exception {
        final Map<String, String> headers = session.getHeaders();
        final String host = headers.get("host");
        final Uri uri = Uri.parse("http://" + host + session.getUri());
        final String path = uri.getPath();
        assert path != null;

        if (NanoHTTPD.Method.OPTIONS.equals(session.getMethod())) {
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "");
        }

        if (TextUtils.equals(path, "/")) {
            return serveAvailableRoutes(host);
        }

        final ChromecastMedia mediaFile = getFile(path);
        if (mediaFile != null) {
            return serveFileImpl(session, mediaFile);
        }

        final File file = coverFile != null && coverFile.first.equalsIgnoreCase(path) ? coverFile.second : null;
        if (file != null) {
            return serveFileImpl(session, file);
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "file not found");
    }

    private Response serveAvailableRoutes(String host) {
        StringBuilder sb = new StringBuilder();

        if (coverFile != null) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(getUrlToSource(host, coverFile.first));
        }
        for (int a = 0; a < 2; a++) {
            final Map<String, ChromecastMedia> map = a == 0 ? ASSET_FILES_MAP : castedFiles;
            for (Map.Entry<String, ChromecastMedia> media : map.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(getUrlToSource(host, media.getKey()));

                final MediaMetadata mediaMetadata = media.getValue().mediaMetadata;
                if (mediaMetadata == null) continue;

                final String title = mediaMetadata.getString(MediaMetadata.KEY_TITLE);
                final String subtitle = mediaMetadata.getString(MediaMetadata.KEY_SUBTITLE);
                if (title != null) {
                    sb.append(' ');
                    sb.append(title);
                }
                if (subtitle != null) {
                    sb.append(" [");
                    sb.append(subtitle);
                    sb.append(']');
                }
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", sb.toString());
    }

    private Response serveFileImpl (IHTTPSession session, ChromecastMedia file) throws Exception {
        final Map<String, String> headers = session.getHeaders();
        final String host = headers.get("host");

        if (file.internalUri.toString().startsWith("data:application/x-mpegurl;base64,")) {
            final String base64 = file.internalUri.toString().substring("data:application/x-mpegurl;base64,".length());
            final byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

            return newFixedLengthResponse(Response.Status.OK, file.mimeType, fixHlsManifest(new String(bytes), host));
        }


        final DataSource source = getDataSourceFactory(file).createDataSource();
        final DataSpec.Builder dataSpecBuilder = new DataSpec.Builder().setUri(file.internalUri);
        final long fileSize = source.open(dataSpecBuilder.build());
        source.close();

        final boolean isHlsManifest = TextUtils.equals(file.mimeType, ChromecastMedia.APPLICATION_X_MPEG_URL);
        final Range range = !isHlsManifest ? parseRangeHeader(session.getHeaders().get("range"), fileSize) : null;
        final long readSize = (range != null) ? (range.end - range.start + 1) : fileSize;

        if (range != null) {
            dataSpecBuilder.setPosition(range.start);
            dataSpecBuilder.setLength(readSize);
        }

        /* * */

        if (isHlsManifest) {
            final byte[] readBuffer = new byte[(int) readSize];
            source.open(dataSpecBuilder.build());
            source.read(readBuffer, 0, (int) readSize);
            source.close();

            return newFixedLengthResponse(Response.Status.OK, file.mimeType, fixHlsManifest(new String(readBuffer), host));
        }

        Response response;
        if (readSize != 0) {
            final InputStream dataInputStream = new DataSourceInputStream(source, dataSpecBuilder.build());
            response = newFixedLengthResponse(range != null ? Response.Status.PARTIAL_CONTENT : Response.Status.OK, file.mimeType, dataInputStream, readSize);
        } else {
            response = newFixedLengthResponse(Response.Status.NO_CONTENT, file.mimeType, "");
        }

        if (range != null) {
            response.addHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + fileSize);
        }

        return response;
    }

    private Response serveFileImpl (IHTTPSession session, File file) throws Exception {
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", new BufferedInputStream(new FileInputStream(file)), file.length());
    }

    private ChromecastMedia getFile (String path) {
        ChromecastMedia m = ASSET_FILES_MAP.get(path);
        if (m == null) {
            m = castedFiles.get(path);
        }
        return m;
    }

    private DataSource.Factory getDataSourceFactory (ChromecastMedia file) {
        final String uri = file.internalUri.toString();

        if (uri.startsWith("file://")) {
            if (file.internalUri.toString().startsWith("file:///android_asset/")) {
                return assetDataSourceFactory;
            }

            return fileDataSourceFactory;
        }

        return mediaDataSourceFactory;
    }



    /* * */

    private static Response addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Max-Age", "3628800");
        response.addHeader("Access-Control-Allow-Methods", "*");
        response.addHeader("Access-Control-Allow-Headers", "*");

        return response;
    }

    private static String fixHlsManifest (String manifest, String host) {
        return manifest.replaceAll("mtproto:", getUrlToSource(host, "/mtproto_"));
    }

    private static Range parseRangeHeader (String rangeHeader, long fileLength) {
        if (TextUtils.isEmpty(rangeHeader)) {
            return null;
        }

        long start, end;
        String rangeValue = rangeHeader.trim().substring("bytes=".length());

        if (rangeValue.startsWith("-")) {
            end = fileLength - 1;
            start = fileLength - 1 - Long.parseLong(rangeValue.substring("-".length()));
        } else {
            String[] range = rangeValue.split("-");
            start = Long.parseLong(range[0]);
            end = range.length > 1 ? Long.parseLong(range[1]) : fileLength - 1;
        }
        if (end > fileLength - 1) {
            end = fileLength - 1;
        }

        return new Range(start, end);
    }

    private static class Range {
        final long start;
        final long end;

        public Range (long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    public static String getUrlToSource(String host, String path) {
        return "http://" + host + path;
    }

    public static String getHost() {
        final int ip = getMyLocalIp();
        return formatIp4(ip) + ":" + PORT;
    }

    private static String formatIp4 (int ip) {
        return String.valueOf(ip & 0xff) +
            '.' + (ip >> 8 & 0xff) +
            '.' + (ip >> 16 & 0xff) +
            '.' + (ip >> 24 & 0xff);
    }

    private static int getMyLocalIp () {
        final WifiManager wifiManager = (WifiManager) ApplicationLoader.applicationContext.getSystemService(Context.WIFI_SERVICE);
        int ip = wifiManager.getConnectionInfo().getIpAddress();

        if (ip == 0) { // for hotspot
            try {
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                    NetworkInterface networkInterface = en.nextElement();
                    for (Enumeration<InetAddress> enumIpAddress = networkInterface.getInetAddresses(); enumIpAddress.hasMoreElements();) {
                        InetAddress inetAddress = enumIpAddress.nextElement();

                        if (inetAddress.isSiteLocalAddress()) {
                            byte[] address = inetAddress.getAddress();
                            ip = (((address[0] + 256) % 256)) +
                                    (((address[1] + 256) % 256) << 8) +
                                    (((address[2] + 256) % 256) << 16) +
                                    (((address[3] + 256) % 256) << 24);
                        }
                    }
                }
            } catch (SocketException ex) {
                FileLog.e(ex);
            }
        }

        return ip;
    }



    private static class DataSourceInputStream extends InputStream {
        private final DataSource dataSource;
        private final byte[] tmpByte = new byte[1];
        private long availableBytes;

        public DataSourceInputStream (DataSource dataSource, DataSpec spec) {
            this.dataSource = dataSource;
            try {
                availableBytes = dataSource.open(spec);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int available() {
            return (int) availableBytes;
        }

        @Override
        public int read() throws IOException {
            int r = dataSource.read(tmpByte, 0, 1);
            availableBytes -= 1;
            return r == C.RESULT_END_OF_INPUT ? -1 : (tmpByte[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }

            int r = dataSource.read(b, off, len);
            availableBytes -= r;

            return r;
        }

        @Override
        public void close() throws IOException {
            dataSource.close();
        }
    }
}
