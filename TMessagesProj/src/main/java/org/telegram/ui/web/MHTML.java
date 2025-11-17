package org.telegram.ui.web;

import android.util.Base64;
import android.util.Base64InputStream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

public class MHTML {

    public final File file;
    public final HashMap<String, HeaderValue> headers = new HashMap<>();
    public final String boundary;

    public final ArrayList<Entry> entries = new ArrayList<>();
    public final HashMap<String, Entry> entriesByLocation = new HashMap<>();
    private final long[] filePos = new long[1];

    public MHTML(File file) throws FileNotFoundException, IOException {
        this.file = file;

        final FileInputStream fileInputStream = new FileInputStream(file);
        final BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));

        this.headers.putAll(parseHeaders(reader));
        this.boundary = HeaderValue.getProp(headers.get("content-type"), "boundary");
        if (this.boundary != null) {
            parseEntries(reader, fileInputStream);
        }

        reader.close();
    }

    private void parseEntries(BufferedReader reader, FileInputStream stream) throws IOException {
        String line;
        Entry currentEntry = null;
        final int boundarylen = 2 + boundary.length();
        while ((line = reader.readLine()) != null) {
            filePos[0] += line.getBytes().length + 2; // 2 = CRLF, TODO(@dkaraush): support proper offset calc
            if (line.length() == boundarylen && line.substring(2).equals(boundary)) {
                if (currentEntry != null) {
                    currentEntry.end = filePos[0] - boundarylen - 2;
                    entries.add(currentEntry);
                    entriesByLocation.put(currentEntry.getLocation(), currentEntry);
                }
                currentEntry = new Entry();
                currentEntry.file = file;
                currentEntry.headers.putAll(parseHeaders(reader));
                currentEntry.start = filePos[0];
            }
        }
        if (currentEntry != null && currentEntry.start != 0 && currentEntry.end != 0) {
            entries.add(currentEntry);
            entriesByLocation.put(currentEntry.getLocation(), currentEntry);
        }
    }

    private HashMap<String, HeaderValue> parseHeaders(BufferedReader reader) throws IOException {
        final HashMap<String, HeaderValue> headers = new HashMap<>();
        String line;
        String currentHeader = null;
        StringBuilder currentValue = null;
        while ((line = reader.readLine()) != null) {
            filePos[0] += line.getBytes().length + 2; // 2 = CRLF, TODO(@dkaraush): support proper offset calc
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }

            if (currentHeader != null && currentValue != null) {
                currentValue.append(line);
                if (!line.endsWith(";")) {
                    appendHeader(currentHeader, currentValue.toString(), headers);
                    currentHeader = null;
                    currentValue = null;
                }
            } else {
                int index = line.indexOf(':');
                if (index < 0) {
                    continue;
                }

                final String name = line.substring(0, index).trim();
                final String value = line.substring(index + 1).trim();
                if (value.endsWith(";")) {
                    currentHeader = name;
                    currentValue = new StringBuilder();
                    currentValue.append(value);
                } else {
                    appendHeader(name, value, headers);
                }
            }
        }
        if (currentHeader != null && currentValue != null) {
            appendHeader(currentHeader, currentValue.toString(), headers);
        }
        return headers;
    }

    private static void appendHeader(String nameString, String valueString, final HashMap<String, HeaderValue> headers) {
        final HeaderValue headerValue = new HeaderValue();
        final String[] parts = valueString.split(";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (int i = 0; i < parts.length; ++i) {
            final String part = parts[i].trim();
            if (part.isEmpty()) continue;
            int index = part.indexOf('=');
            if (i == 0 || index < 0) {
                headerValue.value = part;
                continue;
            }
            String name = part.substring(0, index).trim();
            String value = part.substring(index + 1).trim();
            if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
                value = value.substring(1, value.length() - 1);
            headerValue.props.put(name, value);
        }
        headers.put(nameString.trim().toLowerCase(), headerValue);
    }

    public static class Entry {
        public final HashMap<String, HeaderValue> headers = new HashMap<>();

        public File file;
        public long start, end;

        private Entry() {}

        public String getType() {
            return HeaderValue.getValue(headers.get("content-type"));
        }

        public String getLocation() {
            return HeaderValue.getValue(headers.get("content-location"));
        }

        public String getTransferEncoding() {
            return HeaderValue.getValue(headers.get("content-transfer-encoding"));
        }

        public String getId() {
            return HeaderValue.getValue(headers.get("content-id"));
        }

        public InputStream getRawInputStream() throws IOException {
            return new BoundedInputStream(file, start, end);
        }

        public InputStream getInputStream() throws IOException {
            InputStream stream = new BufferedInputStream(getRawInputStream());
            if ("base64".equals(getTransferEncoding())) {
                return new Base64InputStream(stream, Base64.DEFAULT);
            } else if ("quoted-printable".equalsIgnoreCase(getTransferEncoding())) {
                return new QuotedPrintableInputStream(stream);
            } else {
                return stream;
            }
        }

        public String getData() throws IOException {
            final InputStream inputStream = getInputStream();
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream), 8192 * 4)) {
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                    stringBuilder.append(System.lineSeparator());
                }
            }
            inputStream.close();
            return stringBuilder.toString();
        }
    }

    public static class HeaderValue {
        public String value;
        public final HashMap<String, String> props = new HashMap<>();

        private HeaderValue() {}

        public static String getValue(HeaderValue headerValue) {
            if (headerValue == null) return null;
            return headerValue.value;
        }

        public static String getProp(HeaderValue headerValue, String name) {
            if (headerValue == null) return null;
            return headerValue.props.get(name);
        }
    }

    public static class QuotedPrintableInputStream extends FilterInputStream {

        public QuotedPrintableInputStream(InputStream stream) {
            super(stream);
        }

        @Override
        public int read() throws IOException {
            int c = in.read();
            if (c == '=') {
                int next1 = in.read();
                int next2 = in.read();
                if (next1 == -1 || next2 == -1) {
                    return -1;
                }
                if (next1 == '\r' && next2 == '\n') {
                    return read();
                } else if (next1 == '\n' || next2 == '\n') {
                    return next2;
                } else {
                    return hexToByte(next1, next2);
                }
            }
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytesRead = 0;
            for (int i = 0; i < len; i++) {
                int c = read();
                if (c == -1) {
                    if (bytesRead == 0) {
                        return -1; // End of stream
                    }
                    break; // Partial read
                }
                b[off + i] = (byte) c;
                bytesRead++;
            }
            return bytesRead;
        }

        private int hexToByte(int high, int low) {
            return (hexDigitToInt(high) << 4) | hexDigitToInt(low);
        }

        private int hexDigitToInt(int digit) {
            if (digit >= '0' && digit <= '9') {
                return digit - '0';
            } else if (digit >= 'A' && digit <= 'F') {
                return digit - 'A' + 10;
            } else if (digit >= 'a' && digit <= 'f') {
                return digit - 'a' + 10;
            } else {
                return 0;
            }
        }
    }

    public static class BoundedInputStream extends FileInputStream {
        private final long endOffset;
        private long bytesRead = 0;

        public BoundedInputStream(File file, long startOffset, long endOffset) throws FileNotFoundException, IOException {
            super(file);
            this.endOffset = endOffset;
            if (startOffset > 0) {
                long skipped = skip(startOffset);
                if (skipped != startOffset) {
                    throw new RuntimeException("BoundedInputStream failed to skip");
                }
            }
        }

        @Override
        public int read() throws IOException {
            if (getChannel().position() >= endOffset) {
                return -1;
            }
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (getChannel().position() >= endOffset) {
                return -1;
            }
            long maxLen = endOffset - getChannel().position();
            if (len > maxLen) {
                len = (int) maxLen;
            }
            return super.read(b, off, len);
        }
    }

}
