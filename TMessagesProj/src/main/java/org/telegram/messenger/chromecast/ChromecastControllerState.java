package org.telegram.messenger.chromecast;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.messenger.Utilities;

import java.io.File;

class ChromecastControllerState {
    private ChromecastFileServer server;
    private ChromecastMediaVariations media;
    private ChromecastController.RemoteMediaClientHandler client;

    public void setMedia(ChromecastMediaVariations m) {
        if (client != null && m != null) {
            addToFileServer(m);
        }

        if (client != null && media != null) {
            removeFromFileServer(media);
        }

        if (m != null && m.getVariationsCount() > 0 && !m.getVariation(0).mimeType.startsWith("audio/")) {
            if (server != null) {
                server.setCoverFile(null, null);
            }
        }

        if (client != null && m != null) {
            client.load(m);
        }

        media = m;
    }

    public String setCoverFile(File file) {
        if (file != null && server != null && server.getCoverFile() != null && TextUtils.equals(server.getCoverFile().getAbsolutePath(), file.getAbsolutePath())) {
            return server.getCoverPath();
        }
        final String path = "/file" + Utilities.fastRandom.nextLong();
        if (server == null) {
            server = new ChromecastFileServer();
        }
        server.setCoverFile(path, file);
        return path;
    }

    @Nullable
    public ChromecastMediaVariations getMedia() {
        return media;
    }

    public void setClient(ChromecastController.RemoteMediaClientHandler c) {
        if (media != null && client == null && c != null) {
            addToFileServer(media);
        }

        if (client != null && media != null && c == null) {
            removeFromFileServer(media);
        }

        if (client != null) {
            client.unregister();
        }

        if (c != null) {
            c.register();

            if (media != null) {
                c.load(media);
            }
        }

        client = c;
    }

    @Nullable
    public ChromecastController.RemoteMediaClientHandler getClient() {
        return client;
    }

    private void addToFileServer(ChromecastMediaVariations media) {
        if (server == null) {
            server = new ChromecastFileServer();
        }

        for (int a = 0; a < media.getVariationsCount(); a++) {
            server.addFileToCast(media.getVariation(a));
        }
    }

    private void removeFromFileServer(ChromecastMediaVariations media) {
        if (server == null) {
            return;
        }

        for (int a = 0; a < media.getVariationsCount(); a++) {
            server.removeFileFromCast(media.getVariation(a));
        }
    }
}
