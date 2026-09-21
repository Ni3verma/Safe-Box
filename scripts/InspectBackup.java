import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Standalone verifier for a Safe-Box .bak file.
 *
 * Mirrors the parameters in PasswordBasedEncryptionImpl exactly:
 * PBKDF2WithHmacSHA1, 1324 iterations, 256-bit key, AES/CBC/PKCS5Padding.
 *
 * Usage: java InspectBackup <file.bak> <backupPassword>
 */
public class InspectBackup {

    private static final int KEY_ITERATION_COUNT = 1324;
    private static final int KEY_LENGTH = 256;
    private static final String KEY_FACTORY_ALGO = "PBKDF2WithHmacSHA1";
    private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";

    // Keys as defined in CommonConstants.
    private static final Map<String, String> DATA_KEYS = new TreeMap<>();

    static {
        DATA_KEYS.put("4", "LOGIN");
        DATA_KEYS.put("5", "BANK_ACCOUNT");
        DATA_KEYS.put("6", "BANK_CARD");
        DATA_KEYS.put("7", "SECURE_NOTE");
        DATA_KEYS.put("8", "AUTHENTICATOR");
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String path = args[0];
        char[] password = args[1].toCharArray();

        HashMap<String, byte[]> map;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
            map = (HashMap<String, byte[]>) in.readObject();
        }

        System.out.println("map keys present: " + new TreeMap<>(map).keySet());

        byte[] versionBytes = map.get("0");
        byte[] salt = map.get("1");
        byte[] iv = map.get("2");
        byte[] creationDate = map.get("3");

        System.out.println("BACKUP_VERSION  : " + (versionBytes == null ? "MISSING" : versionBytes[0]));
        System.out.println("salt length     : " + (salt == null ? "MISSING" : salt.length));
        System.out.println("iv length       : " + (iv == null ? "MISSING" : iv.length));
        if (creationDate != null) {
            System.out.println("creationDate    : " + new String(creationDate, StandardCharsets.UTF_8));
        }
        System.out.println();

        for (Map.Entry<String, String> entry : DATA_KEYS.entrySet()) {
            byte[] blob = map.get(entry.getKey());
            if (blob == null) {
                System.out.println(pad(entry.getValue()) + " : key absent from file");
                continue;
            }
            String json = new String(decrypt(password, blob, salt, iv), StandardCharsets.UTF_8);
            System.out.println(pad(entry.getValue()) + " : " + countRecords(json) + " record(s)");
            System.out.println(indent(json));
            System.out.println();
        }
    }

    private static String pad(String value) {
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < 14) {
            builder.append(' ');
        }
        return builder.toString();
    }

    /** Counts top level JSON objects by tracking brace depth outside of string literals. */
    private static int countRecords(String json) {
        int depth = 0;
        int count = 0;
        boolean inString = false;
        boolean escaped = false;
        for (char c : json.toCharArray()) {
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    if (depth == 0) {
                        count++;
                    }
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
        }
        return count;
    }

    private static String indent(String json) {
        return "    " + json.replace("},{", "},\n    {");
    }

    private static byte[] decrypt(char[] password, byte[] data, byte[] salt, byte[] iv)
            throws Exception {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, KEY_ITERATION_COUNT, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGO);
        SecretKeySpec aesKey = new SecretKeySpec(factory.generateSecret(keySpec).getEncoded(), "AES");
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }
}
