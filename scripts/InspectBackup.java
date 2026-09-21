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

    /**
     * Stream bounds for the deserialization filter.
     *
     * Sized well above anything a real backup produces - the committed fixture is under 2 KB, and
     * the structure is a flat map of at most nine byte arrays - while still preventing a crafted
     * file from declaring a huge array or a deeply nested graph and exhausting memory locally.
     */
    private static final long MAX_ARRAY_LENGTH = 64L * 1024 * 1024;
    private static final long MAX_DEPTH = 20;
    private static final long MAX_REFERENCES = 10_000;

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
            // The allowlist is one line and turns "pointed at the wrong file" into a named failure
            // instead of arbitrary construction; the app's RestoreDataWorker overrides resolveClass
            // for the same reason. There is no size or DoS guard here on purpose - the .bak files
            // this reads are ones we make ourselves and are a few kilobytes.
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

        // Without salt and IV no blob can be decrypted. Reporting here keeps the header dump above
        // (which is the useful diagnostic for a corrupt file) and avoids an opaque NPE from
        // decrypt() that would obscure the real problem.
        if (salt == null || iv == null) {
            System.err.println("Error: backup is missing its salt or IV; cannot decrypt any record data.");
            System.exit(1);
        }

        int attempted = 0;
        int failed = 0;

        for (Map.Entry<String, String> entry : DATA_KEYS.entrySet()) {
            String key = entry.getKey();
            // Absent and present-but-null mean very different things and must not be conflated.
            // BackupDataWorker stores null when a record type has no rows, so a v3 backup with no
            // authenticator records has key "8" present holding null. Reporting that as "absent"
            // would make it look like a pre-TOTP v2 file, which is exactly the distinction the
            // upgrade-test fixture depends on.
            if (!map.containsKey(key)) {
                System.out.println(pad(entry.getValue()) + " : key \"" + key
                    + "\" absent - this backup version predates the type");
                continue;
            }
            byte[] blob = map.get(key);
            if (blob == null) {
                System.out.println(pad(entry.getValue()) + " : key \"" + key
                    + "\" present but null - the type is supported, there were no records");
                continue;
            }

            attempted++;
            try {
                String json = new String(decrypt(password, blob, salt, iv), StandardCharsets.UTF_8);
                if (!looksLikeRecordArray(json)) {
                    // A damaged IV does not throw. In CBC mode it corrupts only the first
                    // plaintext block, so padding still validates and decrypt() returns happily.
                    // Without this check the tool printed the mangled text, let the brace counter
                    // report "0 record(s)" off the broken quote parity, and exited 0 - presenting
                    // a corrupt backup as a readable but empty one.
                    failed++;
                    System.out.println(pad(entry.getValue()) + " : CORRUPT - decrypted without"
                        + " error but the result is not a record array; the shared IV or this"
                        + " payload is damaged");
                    System.out.println(indent(preview(json)));
                } else {
                    System.out.println(pad(entry.getValue()) + " : " + countRecords(json) + " record(s)");
                    System.out.println(indent(json));
                }
            } catch (Exception e) {
                // Keep going rather than propagating. The whole point of this tool is inspecting
                // damaged files, and aborting on the first bad payload hides every type after it -
                // including the readable ones that tell you how much of the backup survived.
                // getMessage() is routinely null for BadPaddingException, hence the class name.
                failed++;
                System.out.println(pad(entry.getValue()) + " : DECRYPTION FAILED - "
                    + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            }
            System.out.println();
        }

        // Exit non-zero so a caller or CI step cannot mistake a failed inspection for a clean one.
        // Whether *everything* failed is the diagnostic that matters, but it narrows the cause to
        // the shared inputs rather than to the password alone: password, salt and IV all feed every
        // type, so corrupt-but-present salt or IV bytes fail uniformly too. Only the per-type
        // payloads are excluded.
        if (failed > 0) {
            if (failed == attempted) {
                System.err.println("Error: all " + failed + " record type(s) failed to decrypt."
                    + " Password, salt and IV are shared by every type, so the cause is one of"
                    + " those - either a wrong backup password or corrupt salt/IV bytes - rather"
                    + " than damage to the individual payloads.");
            } else {
                System.err.println("Error: " + failed + " of " + attempted + " record type(s) failed"
                    + " to decrypt. The others decoded, so the password, salt and IV are all"
                    + " correct and those specific payloads are damaged.");
            }
            System.exit(1);
        }
    }

    /**
     * Restricts deserialization to the shape a Safe-Box backup actually has.
     *
     * Called by ObjectInputStream for every class in the stream, and also for the stream-wide
     * depth, array-length and reference-count checks. Those latter calls carry a null serialClass
     * and are bounded explicitly here, because answering UNDECIDED leaves them *unlimited* rather
     * than falling back to a default.
     *
     * @param info Filter callback carrying the class under consideration, if any.
     * @return REJECTED if a stream resource limit is exceeded or the class is not allowlisted,
     *     ALLOWED for the backup container's own types, UNDECIDED for non-class checks within
     *     the limits.
     */
    private static ObjectInputFilter.Status filterBackupClasses(ObjectInputFilter.FilterInfo info) {
        // Bound the stream before looking at classes. These checks arrive with a null serialClass,
        // and answering UNDECIDED leaves them *unlimited* rather than defaulted, so a crafted file
        // could exhaust memory during readObject() without ever naming a disallowed class.
        if (info.depth() > MAX_DEPTH
            || info.references() > MAX_REFERENCES
            || info.arrayLength() > MAX_ARRAY_LENGTH) {
            System.err.println("Rejected backup: exceeds a stream resource limit"
                + " (depth=" + info.depth()
                + ", references=" + info.references()
                + ", arrayLength=" + info.arrayLength() + ")");
            return ObjectInputFilter.Status.REJECTED;
        }

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

    /**
     * Reports whether decrypted plaintext has the shape every record payload must have.
     *
     * BackupDataWorker always serialises a JSON array, so anything else means the bytes are not
     * what they claim to be even though the cipher accepted them. This is the only signal for a
     * damaged IV: CBC corrupts just the first plaintext block, leaving the padding in the final
     * block intact, so decrypt() succeeds and returns partly-garbage text.
     *
     * @param json Decrypted plaintext.
     * @return True when the text is a JSON array and can be counted and printed.
     */
    private static boolean looksLikeRecordArray(String json) {
        String trimmed = json.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    /**
     * Truncates text for display, so a corrupt payload shows its damage without flooding output.
     *
     * @param text Text to abbreviate.
     * @return At most 120 characters, with an ellipsis when truncated.
     */
    private static String preview(String text) {
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
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
