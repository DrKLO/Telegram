package org.telegram.ui.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** Explicit web-resource approvals for one live top-level document. No Android grants live here. */
final class WebViewPermissionSession {
    static final String AUDIO = "android.webkit.resource.AUDIO_CAPTURE";
    static final String VIDEO = "android.webkit.resource.VIDEO_CAPTURE";
    static final String LOCATION = "telegram.webview.resource.GEOLOCATION";

    private final Map<String, Set<String>> approvals = new HashMap<>();
    private long generation;

    static String originKey(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                return null;
            }
            int port = uri.getPort();
            if (port < -1 || port > 65535) {
                return null;
            }
            if ("https".equals(scheme) && port == 443 || "http".equals(scheme) && port == 80) {
                port = -1;
            }
            // Keep IPv6 brackets so a host suffix cannot collide with another origin's port.
            if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
                host = "[" + host + "]";
            }
            return scheme + "://" + host + (port == -1 ? "" : ":" + port);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    long generation() {
        return generation;
    }

    static boolean supported(String[] resources) {
        if (resources == null || resources.length == 0) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (String resource : resources) {
            if ((!AUDIO.equals(resource) && !VIDEO.equals(resource) && !LOCATION.equals(resource))
                    || !seen.add(resource)) {
                return false;
            }
        }
        return true;
    }

    boolean hasAll(String origin, String[] resources) {
        if (origin == null || origin.isEmpty() || !supported(resources)) {
            return false;
        }
        Set<String> granted = approvals.get(origin);
        if (granted == null) {
            return false;
        }
        for (String resource : resources) {
            if (!granted.contains(resource)) {
                return false;
            }
        }
        return true;
    }

    String[] missing(String origin, String[] resources) {
        ArrayList<String> missing = new ArrayList<>();
        Set<String> granted = approvals.get(origin);
        for (String resource : resources) {
            if (granted == null || !granted.contains(resource)) {
                missing.add(resource);
            }
        }
        return missing.toArray(new String[0]);
    }

    boolean allow(long expectedGeneration, String origin, String[] resources) {
        if (expectedGeneration != generation || origin == null || origin.isEmpty() || !supported(resources)) {
            return false;
        }
        Set<String> granted = approvals.get(origin);
        if (granted == null) {
            granted = new HashSet<>();
            approvals.put(origin, granted);
        }
        for (String resource : resources) {
            granted.add(resource);
        }
        return true;
    }

    void deny(String origin, String[] resources) {
        Set<String> granted = approvals.get(origin);
        if (granted == null || resources == null) {
            return;
        }
        for (String resource : resources) {
            granted.remove(resource);
        }
        if (granted.isEmpty()) {
            approvals.remove(origin);
        }
    }

    /** Revoke only resources whose OS grant is absent; OS grants never add web consent. */
    void retainSystemResources(String[] availableResources) {
        Set<String> available = new HashSet<>();
        if (availableResources != null) {
            available.addAll(Arrays.asList(availableResources));
        }
        Iterator<Set<String>> iterator = approvals.values().iterator();
        while (iterator.hasNext()) {
            Set<String> granted = iterator.next();
            granted.retainAll(available);
            if (granted.isEmpty()) {
                iterator.remove();
            }
        }
    }

    void clear() {
        generation++;
        approvals.clear();
    }
}
