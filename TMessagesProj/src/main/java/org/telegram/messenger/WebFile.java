package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

public class WebFile extends TLObject {

    public TLRPC.InputGeoPoint geo_point;
    public TLRPC.InputPeer peer;
    public int msg_id;
    public int w;
    public int h;
    public int zoom;
    public int scale;
    public String url;
    public TLRPC.InputWebFileLocation location;

    public ArrayList<TLRPC.DocumentAttribute> attributes;
    public int size;
    public String mime_type;

    public static WebFile createWithGeoPoint(TLRPC.GeoPoint point, int w, int h, int zoom, int scale) {
        return createWithGeoPoint(point.lat, point._long, point.access_hash, w, h, zoom, scale);
    }

    public static WebFile createWithGeoPoint(double lat, double _long, long access_hash, int w, int h, int zoom, int scale) {
        WebFile webFile = new WebFile();
        TLRPC.TL_inputWebFileGeoPointLocation location = new TLRPC.TL_inputWebFileGeoPointLocation();
        webFile.location = location;
        location.geo_point = webFile.geo_point = new TLRPC.TL_inputGeoPoint();
        location.access_hash = access_hash;
        webFile.geo_point.lat = lat;
        webFile.geo_point._long = _long;
        location.w = webFile.w = w;
        location.h = webFile.h = h;
        location.zoom = webFile.zoom = zoom;
        location.scale = webFile.scale = scale;
        webFile.mime_type = "image/png";
        webFile.url = String.format(Locale.US, "maps_%.6f_%.6f_%d_%d_%d_%d.png", lat, _long, w, h, zoom, scale);
        webFile.attributes = new ArrayList<>();
        return webFile;
    }

    public static WebFile createWithWebDocument(TLRPC.WebDocument webDocument) {
        WebFile webFile = new WebFile();
        TLRPC.TL_webDocument document = (TLRPC.TL_webDocument) webDocument;
        TLRPC.TL_inputWebFileLocation location = new TLRPC.TL_inputWebFileLocation();
        webFile.location = location;
        location.url = webFile.url = webDocument.url;
        location.access_hash = document.access_hash;
        webFile.size = document.size;
        webFile.mime_type = document.mime_type;
        webFile.attributes = document.attributes;
        return webFile;
    }
}
