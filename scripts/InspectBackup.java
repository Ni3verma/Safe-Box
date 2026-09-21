import java.io.FileInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

    /**
     * Classes permitted while reading the backup container.
     *
     * Derived by instrumenting a real .bak rather than copied from the app: an ObjectInputFilter
     * and RestoreDataWorker's resolveClass override are consulted on *different* sets of classes,
     * so the two allowlists cannot be identical.
     *
     * - LinkedHashMap is the actual top-level type. BackupDataWorker builds the container with
     *   Kotlin's mutableMapOf(), which is a LinkedHashMap, not a HashMap.
     * - HashMap appears as LinkedHashMap's superclass descriptor.
     * - [Ljava.util.Map$Entry; is reached through the class descriptors and is seen by a filter but
     *   not by resolveClass, which is why the app's list omits it. Dropping it here fails on every
     *   valid file.
     * - java.lang.String is deliberately absent: strings are written as TC_STRING with no class
     *   descriptor, so the filter is never consulted for them.
     */
    private static final Set<String> ALLOWED_CLASS_NAMES = Set.of(
        "java.util.LinkedHashMap",
        "java.util.HashMap",
        "[Ljava.util.Map$Entry;",
        "[B"
    );

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
        if (args.length < 2) {
            System.err.println("Usage: java InspectBackup <file.bak> <backupPassword>");
            System.exit(1);
        }
        String path = args[0];
        char[] password = args[1].toCharArray();

        HashMap<String, byte[]> map;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
            // readObject() instantiates whatever the stream names, before the cast below ever runs.
            // This tool exists to inspect *suspect* backup files, so a hostile .bak is squarely in
            // its threat model. Restrict the stream to the three types a backup can legitimately
            // contain; the app's own RestoreDataWorker allowlists resolveClass for the same reason.
            in.setObjectInputFilter(InspectBackup::filterBackupClasses);
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
        System.out.println("creationDate    : " + formatCreationDate(creationDate));
        System.out.println();

        // Without salt and IV no blob can be decrypted. Returning here keeps the header dump above
        // (which is the useful diagnostic for a corrupt file) and avoids an opaque NPE from
        // decrypt() that would obscure the real problem.
        if (salt == null || iv == null) {
            System.err.println("Error: backup is missing its salt or IV; cannot decrypt any record data.");
            return;
        }

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

    /**
     * Restricts deserialization to the shape a Safe-Box backup actually has.
     *
     * Called by ObjectInputStream for every class in the stream, and also for the stream-wide
     * depth, array-length and reference-count checks. Those latter calls carry a null serialClass
     * and are answered UNDECIDED so the JVM's built-in limits continue to apply.
     *
     * @param info Filter callback carrying the class under consideration, if any.
     * @return ALLOWED for the backup container's own types, UNDECIDED for non-class checks,
     *     REJECTED otherwise.
     */
    private static ObjectInputFilter.Status filterBackupClasses(ObjectInputFilter.FilterInfo info) {
        Class<?> serialClass = info.serialClass();
        if (serialClass == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        // Matched on the serialised name so this reads identically to the app's allowlist; byte[]
        // arrives as "[B", which is the form RestoreDataWorker lists too.
        if (ALLOWED_CLASS_NAMES.contains(serialClass.getName())) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        System.err.println("Rejected unexpected class in backup stream: " + serialClass.getName());
        return ObjectInputFilter.Status.REJECTED;
    }

    /**
     * Renders the backup's creation timestamp.
     *
     * BackupDataWorker writes this as ByteBuffer.allocate(Long.SIZE_BYTES).putLong(currentTimeMillis),
     * so it is 8 raw big-endian bytes, not text. Decoding it as UTF-8 prints control characters and
     * looks like corruption when the file is in fact fine.
     *
     * BACKUP_VERSION 1 stored it as a single byte instead, and RestoreDataWorker still reads both
     * widths, so this mirrors that branch rather than rejecting an old but valid file.
     *
     * @param creationDate Raw value of map key "3", may be null or either supported width.
     * @return Human-readable local timestamp, or a diagnostic string when it cannot be decoded.
     */
    private static String formatCreationDate(byte[] creationDate) {
        if (creationDate == null) {
            return "MISSING";
        }
        long epochMillis;
        if (creationDate.length >= Long.BYTES) {
            epochMillis = ByteBuffer.wrap(creationDate).getLong();
        } else if (creationDate.length == 1) {
            // BACKUP_VERSION 1 shape; the value is not a real epoch, so report it as-is.
            return "v1 single-byte value: " + creationDate[0];
        } else {
            return "UNEXPECTED LENGTH " + creationDate.length;
        }
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
            + "  (epochMillis=" + epochMillis + ")";
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
