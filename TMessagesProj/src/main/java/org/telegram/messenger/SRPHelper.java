package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.math.BigInteger;

public class SRPHelper {

    public static byte[] getBigIntegerBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(bytes, 1, correctedAuth, 0, 256);
            return correctedAuth;
        } else if (bytes.length < 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(bytes, 0, correctedAuth, 256 - bytes.length, bytes.length);
            for (int a = 0; a < 256 - bytes.length; a++) {
                correctedAuth[a] = 0;
            }
            return correctedAuth;
        }
        return bytes;
    }

    public static byte[] getX(byte[] passwordBytes, TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo) {
        byte[] x_bytes = Utilities.computeSHA256(algo.salt1, passwordBytes, algo.salt1);
        x_bytes = Utilities.computeSHA256(algo.salt2, x_bytes, algo.salt2);
        x_bytes = Utilities.computePBKDF2(x_bytes, algo.salt1);
        return Utilities.computeSHA256(algo.salt2, x_bytes, algo.salt2);
    }

    public static BigInteger getV(byte[] passwordBytes, TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo) {
        BigInteger g = BigInteger.valueOf(algo.g);
        byte[] g_bytes = getBigIntegerBytes(g);
        BigInteger p = new BigInteger(1, algo.p);

        byte[] x_bytes = getX(passwordBytes, algo);
        BigInteger x = new BigInteger(1, x_bytes);
        return g.modPow(x, p);
    }

    public static byte[] getVBytes(byte[] passwordBytes, TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo) {
        if (!Utilities.isGoodPrime(algo.p, algo.g)) {
            return null;
        }
        return getBigIntegerBytes(getV(passwordBytes, algo));
    }

    public static TLRPC.TL_inputCheckPasswordSRP startCheck(byte[] x_bytes, long srp_id, byte[] srp_B, TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo) {
        if (x_bytes == null || srp_B == null || srp_B.length == 0 || !Utilities.isGoodPrime(algo.p, algo.g)) {
            return null;
        }
        BigInteger g = BigInteger.valueOf(algo.g);
        byte[] g_bytes = getBigIntegerBytes(g);
        BigInteger p = new BigInteger(1, algo.p);

        byte[] k_bytes = Utilities.computeSHA256(algo.p, g_bytes);
        BigInteger k = new BigInteger(1, k_bytes);

        BigInteger x = new BigInteger(1, x_bytes);

        byte[] a_bytes = new byte[256];
        Utilities.random.nextBytes(a_bytes);
        BigInteger a = new BigInteger(1, a_bytes);

        BigInteger A = g.modPow(a, p);
        byte[] A_bytes = getBigIntegerBytes(A);

        BigInteger B = new BigInteger(1, srp_B);
        if (B.compareTo(BigInteger.ZERO) <= 0 || B.compareTo(p) >= 0) {
            return null;
        }
        byte[] B_bytes = getBigIntegerBytes(B);

        byte[] u_bytes = Utilities.computeSHA256(A_bytes, B_bytes);
        BigInteger u = new BigInteger(1, u_bytes);
        if (u.compareTo(BigInteger.ZERO) == 0) {
            return null;
        }

        BigInteger B_kgx = B.subtract(k.multiply(g.modPow(x, p)).mod(p));
        if (B_kgx.compareTo(BigInteger.ZERO) < 0) {
            B_kgx = B_kgx.add(p);
        }
        if (!Utilities.isGoodGaAndGb(B_kgx, p)) {
            return null;
        }

        BigInteger S = B_kgx.modPow(a.add(u.multiply(x)), p);
        byte[] S_bytes = getBigIntegerBytes(S);

        byte[] K_bytes = Utilities.computeSHA256(S_bytes);

        byte[] p_hash = Utilities.computeSHA256(algo.p);
        byte[] g_hash = Utilities.computeSHA256(g_bytes);
        for (int i = 0; i < p_hash.length; i++) {
            p_hash[i] = (byte) (g_hash[i] ^ p_hash[i]);
        }

        TLRPC.TL_inputCheckPasswordSRP result = new TLRPC.TL_inputCheckPasswordSRP();
        result.M1 = Utilities.computeSHA256(p_hash, Utilities.computeSHA256(algo.salt1), Utilities.computeSHA256(algo.salt2), A_bytes, B_bytes, K_bytes);
        result.A = A_bytes;
        result.srp_id = srp_id;
        return result;
    }
}
