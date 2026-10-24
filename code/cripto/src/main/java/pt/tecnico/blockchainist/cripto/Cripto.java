package pt.tecnico.blockchainist.cripto;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class Cripto {
    
    public static PrivateKey loadPrivateKey(String resource) throws Exception {
        byte[] keyBytes = readResource(resource);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public static PublicKey loadPublicKey(String resource) throws Exception {
        byte[] keyBytes = readResource(resource);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public static byte[] sign(byte[] data, PrivateKey key) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(data);
        return sig.sign();
    }

    public static boolean verify(byte[] data, byte[] signature, PublicKey key) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(key);
        sig.update(data);
        return sig.verify(signature);
    }

    private static byte[] readResource(String path) throws Exception {
        try (InputStream is = Cripto.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("File not found: " + path);
            return is.readAllBytes();
        }
    }
}
