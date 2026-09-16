package org.telegram.proxy;

import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

import java.io.UnsupportedEncodingException;
import java.net.IDN;
import java.net.URLEncoder;
import java.util.Objects;

public final class ProxySettings {
    public static final ProxySettings EMPTY = builder().build();
    public enum Type {
        SOCKS5,
        MTPROTO,
        WEB
    }

    private final @NonNull Type type;
    private final @NonNull String address;
    private final int port;
    private final @NonNull String user;
    private final @NonNull String password;
    private final @NonNull String secret;

    private ProxySettings(Builder builder) {
        this.type = builder.type;
        this.address = builder.address;

        if (type == Type.WEB) {
            secret = builder.secret;
            port = 0;
            user = "";
            password = "";
        } else if (type == Type.MTPROTO) {
            secret = builder.secret;
            port = builder.port;
            user = "";
            password = "";
        } else if (type == Type.SOCKS5) {
            secret = "";
            port = builder.port;
            user = builder.user;
            password = builder.password;
        } else {
            throw new IllegalArgumentException();
        }
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @NonNull
    public String getUser() {
        return user;
    }

    @NonNull
    public String getPassword() {
        return password;
    }

    @NonNull
    public String getSecret() {
        return secret;
    }

    public boolean isValid() {
        if (TextUtils.isEmpty(address)) {
            return false;
        }
        return type == Type.WEB || port > 0;
    }

    public String getLink() {
        StringBuilder url;
        switch (type) {
            case MTPROTO:
                url = new StringBuilder("https://t.me/proxy?");
                break;
            case WEB:
                url = new StringBuilder("https://t.me/webproxy?");
                break;
            case SOCKS5:
            default:
                url = new StringBuilder("https://t.me/socks?");
                break;
        }

        try {
            url.append("server=").append(URLEncoder.encode(address, "UTF-8"));
            if (type != Type.WEB) {
                url.append("&port=").append(port);
            }
            if (!TextUtils.isEmpty(user)) {
                url.append("&user=").append(URLEncoder.encode(user, "UTF-8"));
            }
            if (!TextUtils.isEmpty(password)) {
                url.append("&pass=").append(URLEncoder.encode(password, "UTF-8"));
            }
            if (!TextUtils.isEmpty(secret)) {
                url.append("&secret=").append(URLEncoder.encode(secret, "UTF-8"));
            }
        } catch (UnsupportedEncodingException ignored) {}
        return url.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProxySettings)) {
            return false;
        }

        ProxySettings that = (ProxySettings) o;
        return port == that.port
                && type == that.type
                && Objects.equals(address, that.address)
                && Objects.equals(user, that.user)
                && Objects.equals(password, that.password)
                && Objects.equals(secret, that.secret);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, address, port, user, password, secret);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ProxySettings fromSharedPreferences(SharedPreferences preferences) {
        final String proxyAddress = preferences.getString("proxy_ip", "");
        final String proxyUsername = preferences.getString("proxy_user", "");
        final String proxyPassword = preferences.getString("proxy_pass", "");
        final String proxySecret = preferences.getString("proxy_secret", "");
        final int proxyPort = preferences.getInt("proxy_port", 1080);
        final ProxySettings.Type proxyType = ProxySettings.intToType(preferences.getInt("proxy_type", ProxySettings.typeToInt(TextUtils.isEmpty(proxySecret)
            ? ProxySettings.Type.SOCKS5
            : ProxySettings.Type.MTPROTO)));

        return builder()
            .setAddress(proxyAddress)
            .setUser(proxyUsername)
            .setPassword(proxyPassword)
            .setSecret(proxySecret)
            .setPort(proxyPort)
            .setType(proxyType)
            .build();
    }

    public void toSharedPreferences(SharedPreferences.Editor editor) {
        editor.putInt("proxy_type", ProxySettings.typeToInt(type));
        editor.putString("proxy_ip", address);
        switch (type) {
            case SOCKS5:
                editor.putInt("proxy_port", port);
                editor.remove("proxy_secret");
                if (TextUtils.isEmpty(password)) {
                    editor.remove("proxy_pass");
                } else {
                    editor.putString("proxy_pass", password);
                }
                if (TextUtils.isEmpty(user)) {
                    editor.remove("proxy_user");
                } else {
                    editor.putString("proxy_user", user);
                }
                break;
            case MTPROTO:
                editor.putString("proxy_secret", secret);
                editor.putInt("proxy_port", port);
                editor.remove("proxy_pass");
                editor.remove("proxy_user");
                break;
            case WEB:
                editor.putString("proxy_secret", secret);
                editor.remove("proxy_port");
                editor.remove("proxy_pass");
                editor.remove("proxy_user");
                break;
        }
    }

    public static ProxySettings fromUri(Uri uri) {
        if (uri == null) {
            return null;
        }

        try {
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }

            Type type;

            if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) {
                String host = uri.getHost();
                if (host == null) {
                    return null;
                }

                host = host.toLowerCase();
                if (!host.equals("telegram.me") && !host.equals("t.me") && !host.equals("telegram.dog")) {
                    return null;
                }

                String path = uri.getPath();
                if (path == null) {
                    return null;
                }

                if (path.startsWith("/socks")) {
                    type = Type.SOCKS5;
                } else if (path.startsWith("/proxy")) {
                    type = Type.MTPROTO;
                } else if (path.startsWith("/webproxy")) {
                    type = Type.WEB;
                } else {
                    return null;
                }
            } else if (scheme.equalsIgnoreCase("tg")) {
                String url = uri.toString();

                if (url.startsWith("tg://socks") || url.startsWith("tg:socks")) {
                    type = Type.SOCKS5;
                } else if (url.startsWith("tg://proxy") || url.startsWith("tg:proxy")) {
                    type = Type.MTPROTO;
                } else if (url.startsWith("tg://webproxy") || url.startsWith("tg:webproxy")) {
                    type = Type.WEB;
                } else {
                    return null;
                }

                int queryIndex = url.indexOf('?');
                if (queryIndex < 0) {
                    return null;
                }

                uri = Uri.parse("tg://telegram.org/" + url.substring(queryIndex));
            } else {
                return null;
            }

            String address = uri.getQueryParameter("server");
            if (address == null) {
                address = uri.getQueryParameter("host");
            }
            if (AndroidUtilities.checkHostForPunycode(address)) {
                address = IDN.toASCII(address, IDN.ALLOW_UNASSIGNED);
            }

            int port = 0;
            String portValue = uri.getQueryParameter("port");
            if (!TextUtils.isEmpty(portValue)) {
                try {
                    port = Integer.parseInt(portValue);
                } catch (NumberFormatException ignored) {}
            }

            return builder()
                .setType(type)
                .setAddress(address)
                .setPort(port)
                .setUser(uri.getQueryParameter("user"))
                .setPassword(uri.getQueryParameter("pass"))
                .setSecret(uri.getQueryParameter("secret"))
                .build();
        } catch (Exception ignore) {
            return null;
        }
    }

    public static final class Builder {

        private @NonNull Type type = Type.SOCKS5;
        private @NonNull String address = "";
        private int port;
        private @NonNull String user = "";
        private @NonNull String password = "";
        private @NonNull String secret = "";

        private Builder() {
        }

        public Builder setType(Type type) {
            this.type = type != null ? type : Type.SOCKS5;
            return this;
        }

        public Builder setAddress(String address) {
            this.address = address != null ? address : "";
            return this;
        }

        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        public Builder setUser(String user) {
            this.user = user != null ? user : "";
            return this;
        }

        public Builder setPassword(String password) {
            this.password = password != null ? password : "";
            return this;
        }

        public Builder setSecret(String secret) {
            this.secret = secret != null ? secret : "";
            return this;
        }

        public ProxySettings build() {
            return new ProxySettings(this);
        }
    }

    public static int typeToInt(Type type) {
        switch (type) {
            case SOCKS5:
                return 0;
            case MTPROTO:
                return 1;
            case WEB:
                return 2;
        }
        return 0;
    }

    public static Type intToType(int type) {
        switch (type) {
            case 0:
                return Type.SOCKS5;
            case 1:
                return Type.MTPROTO;
            case 2:
                return Type.WEB;
        }
        return Type.SOCKS5;
    }
}