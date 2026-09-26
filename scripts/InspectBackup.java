import java.io.FileInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
 * It deliberately does not reuse the app's own decryption: the upgrade and restore tests compare
 * two decoded backups, and a bug shared by both sides of that comparison would cancel out.
 *
 * Usage:
 *   java InspectBackup <file.bak> <backupPassword>              human-readable dump
 *   java InspectBackup --canonical <file.bak> <backupPassword>  sorted canonical lines
 *   java InspectBackup --rows <file.bak> <backupPassword>       sorted TYPE<tab>title lines
 *   java InspectBackup --header <file.bak>                      the file's BACKUP_VERSION
 *   java InspectBackup --supported-version                      highest version this tool reads
 */
public class InspectBackup {

    private static final int KEY_ITERATION_COUNT = 1324;
    private static final int KEY_LENGTH = 256;
    private static final String KEY_FACTORY_ALGO = "PBKDF2WithHmacSHA1";
    private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";

    /**
     * The newest BACKUP_VERSION this tool is known to decode correctly.
     *
     * scripts/tests/backup-format-test.sh fails a PR whose BACKUP_VERSION is higher, so a format
     * change cannot land without someone confirming (and, where needed, teaching) this decoder.
     * Raise it in the same change that bumps BACKUP_VERSION.
     */
    static final int SUPPORTED_BACKUP_VERSION = 3;

    private static final String NULL_TOKEN = "null";

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

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--supported-version")) {
            System.out.println(SUPPORTED_BACKUP_VERSION);
            return;
        }
        if (args.length == 2 && args[0].equals("--header")) {
            runMachineMode(() -> printHeaderVersion(readContainer(args[1])));
            return;
        }
        if (args.length == 3 && args[0].equals("--canonical")) {
            runMachineMode(() -> printCanonical(readContainer(args[1]), args[2].toCharArray()));
            return;
        }
        if (args.length == 3 && args[0].equals("--rows")) {
            runMachineMode(() -> printRows(readContainer(args[1]), args[2].toCharArray()));
            return;
        }
        if (args.length != 2 || args[0].startsWith("--")) {
            System.err.println("Usage: java InspectBackup <file.bak> <backupPassword>");
            System.err.println("       java InspectBackup --canonical <file.bak> <backupPassword>");
            System.err.println("       java InspectBackup --rows <file.bak> <backupPassword>");
            System.err.println("       java InspectBackup --header <file.bak>");
            System.err.println("       java InspectBackup --supported-version");
            System.exit(1);
        }
        String path = args[0];
        char[] password = args[1].toCharArray();

        Map<String, byte[]> map = readContainer(path);

        System.out.println("map keys present: " + new TreeMap<>(map).keySet());

        byte[] versionBytes = map.get("0");
        byte[] salt = map.get("1");
        byte[] iv = map.get("2");
        byte[] creationDate = map.get("3");

        // Length-checked like formatCreationDate below: a damaged length field can decode to a
        // zero-byte array, and this tool's job is to diagnose such a file, not die on it.
        System.out.println("BACKUP_VERSION  : " + (versionBytes == null ? "MISSING"
            : versionBytes.length == 0 ? "EMPTY" : versionBytes[0]));
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
     * Reads the backup container: a serialised map from key to byte array.
     *
     * readObject() instantiates whatever the stream names, before the cast below ever runs. The
     * allowlist turns "pointed at the wrong file" into a named failure instead of arbitrary
     * construction; the app's RestoreDataWorker overrides resolveClass for the same reason.
     *
     * @param path the .bak file
     * @return the container, keyed as in CommonConstants
     */
    @SuppressWarnings("unchecked")
    private static Map<String, byte[]> readContainer(String path) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
            in.setObjectInputFilter(InspectBackup::filterBackupClasses);
            return (HashMap<String, byte[]>) in.readObject();
        }
    }

    /** An action of a machine-readable mode; may throw anything reading or decrypting can. */
    private interface MachineAction {
        void run() throws Exception;
    }

    /**
     * Runs a machine-readable mode so that every failure, expected or not, ends as one "Error:"
     * line on stderr and exit status 1. Callers (the harness scripts and PR checks) rely on the
     * exit status alone, and a stack trace from a wrong password is noise in a CI log.
     *
     * @param action the mode's work
     */
    private static void runMachineMode(MachineAction action) {
        try {
            action.run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            System.exit(1);
        }
    }

    /**
     * Prints the file's BACKUP_VERSION byte, which is stored unencrypted, so no password is
     * needed. Used by the PR checks to compare the committed seed and fixtures with the code.
     *
     * @param map the backup container
     */
    private static void printHeaderVersion(Map<String, byte[]> map) {
        byte[] versionBytes = map.get("0");
        if (versionBytes == null || versionBytes.length == 0) {
            fail("the backup has no BACKUP_VERSION byte");
        }
        System.out.println(versionBytes[0]);
    }

    /**
     * Prints every record field as one sorted line, so two backups can be compared with diff.
     *
     * Line shape: {@code TYPE|"title"|creationDate|field=value}, where title and value are JSON
     * literals (strings quoted and escaped, numbers bare). One line per field keeps a diff
     * pointing at exactly the field that changed, and the type, title and creationDate prefix
     * identify the record even when two records share a title.
     *
     * Normalisation, both deliberate and both covered by scripts/tests/inspect-backup-test.sh:
     * - A type key that is absent (an older format predating the type), present but null (no
     *   records), or an empty array all print nothing, so they compare equal.
     * - A field that is null or absent prints nothing, so a field a newer format added compares
     *   equal to an older file lacking it, as long as the newer build left it null. An empty
     *   string is not null and does print: the difference is real data.
     *
     * The header (version, salt, IV, creation time) is not printed; it differs between any two
     * backups of the same data. Any decryption or parsing failure exits non-zero.
     *
     * @param map the backup container
     * @param password the backup password
     */
    private static void printCanonical(Map<String, byte[]> map, char[] password) throws Exception {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> record : decodeRecords(map, password)) {
            lines.addAll(canonicalLines(record.getKey(), record.getValue()));
        }
        Collections.sort(lines);
        for (String line : lines) {
            System.out.println(line);
        }
    }

    /**
     * Prints one {@code TYPE<tab>title} line per record, sorted, with the title decoded to the
     * text the app displays. The harness passes these to the instrumentation as the rows the
     * records list must show, so no script has to parse JSON.
     *
     * @param map the backup container
     * @param password the backup password
     */
    private static void printRows(Map<String, byte[]> map, char[] password) throws Exception {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> record : decodeRecords(map, password)) {
            lines.add(rowLine(record.getKey(), record.getValue()));
        }
        Collections.sort(lines);
        for (String line : lines) {
            System.out.println(line);
        }
    }

    /**
     * Turns one decoded record into its records-list row.
     *
     * @param type the record type, e.g. LOGIN
     * @param record field name to JSON literal, as parsed
     * @return {@code TYPE<tab>title}, the title decoded from its JSON literal
     */
    static String rowLine(String type, Map<String, String> record) {
        String literal = record.get("title");
        if (literal == null || !literal.startsWith("\"")) {
            fail(type + " record without a string title: " + record);
        }
        String title = new JsonCursor(literal).readString();
        if (title.contains("\t") || title.contains("\n")) {
            fail(type + " title " + literal + " holds a tab or newline, which the row list cannot carry");
        }
        return type + "\t" + title;
    }

    /**
     * Decrypts and parses every record in a backup, after checking the header can be read.
     *
     * @param map the backup container
     * @param password the backup password
     * @return (type, record) pairs; the record maps field name to JSON literal
     */
    private static List<Map.Entry<String, Map<String, String>>> decodeRecords(
        Map<String, byte[]> map,
        char[] password
    ) throws Exception {
        byte[] versionBytes = map.get("0");
        if (versionBytes == null || versionBytes.length == 0) {
            fail("the backup has no BACKUP_VERSION byte");
        }
        if (versionBytes[0] > SUPPORTED_BACKUP_VERSION) {
            fail("BACKUP_VERSION " + versionBytes[0] + " is newer than this tool supports ("
                + SUPPORTED_BACKUP_VERSION + "); teach it the new format first");
        }
        byte[] salt = map.get("1");
        byte[] iv = map.get("2");
        if (salt == null || iv == null) {
            fail("the backup is missing its salt or IV");
        }
        List<Map.Entry<String, Map<String, String>>> records = new ArrayList<>();
        for (Map.Entry<String, String> entry : DATA_KEYS.entrySet()) {
            byte[] blob = map.get(entry.getKey());
            if (blob == null) {
                continue;
            }
            String json = new String(decrypt(password, blob, salt, iv), StandardCharsets.UTF_8);
            if (!looksLikeRecordArray(json)) {
                fail(entry.getValue() + " decrypted to something that is not a record array");
            }
            for (Map<String, String> record : parseFlatObjectArray(json)) {
                records.add(Map.entry(entry.getValue(), record));
            }
        }
        return records;
    }

    /**
     * Turns one decoded record into its canonical lines.
     *
     * @param type the record type, e.g. LOGIN
     * @param record field name to JSON literal, as parsed
     * @return one line per non-null field
     */
    static List<String> canonicalLines(String type, Map<String, String> record) {
        String title = record.get("title");
        String creationDate = record.get("creationDate");
        if (title == null || NULL_TOKEN.equals(title) || creationDate == null) {
            fail(type + " record without a title or creationDate: " + record);
        }
        String prefix = type + "|" + title + "|" + creationDate + "|";
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> field : new TreeMap<>(record).entrySet()) {
            if (!NULL_TOKEN.equals(field.getValue())) {
                lines.add(prefix + field.getKey() + "=" + field.getValue());
            }
        }
        return lines;
    }

    /**
     * Parses a JSON array of flat objects, keeping every value as its JSON literal.
     *
     * Hand-written because this is a single-file tool with no dependencies. It accepts exactly
     * what BackupDataWorker writes (kotlinx.serialization of the Export* data classes): an array
     * of objects whose values are strings, numbers, booleans or null. A nested object or array
     * is rejected by name, because it would mean the format grew a shape this comparison has
     * never been checked against.
     *
     * String literals are re-emitted in a normalised escaping, so two encoders that escape the
     * same character differently still compare equal.
     *
     * @param json the decrypted payload of one record type
     * @return one map per record, field name to JSON literal
     */
    static List<Map<String, String>> parseFlatObjectArray(String json) {
        JsonCursor cursor = new JsonCursor(json);
        List<Map<String, String>> records = new ArrayList<>();
        cursor.expect('[');
        if (cursor.peekSkippingSpace() == ']') {
            cursor.expect(']');
            cursor.expectEnd();
            return records;
        }
        while (true) {
            records.add(cursor.readFlatObject());
            char next = cursor.nextSkippingSpace();
            if (next == ']') {
                break;
            }
            if (next != ',') {
                fail("expected ',' or ']' in record array at offset " + (cursor.position - 1));
            }
        }
        cursor.expectEnd();
        return records;
    }

    /** Minimal cursor over a JSON string, sufficient for {@link #parseFlatObjectArray}. */
    private static final class JsonCursor {
        private final String text;
        private int position;

        JsonCursor(String text) {
            this.text = text;
        }

        Map<String, String> readFlatObject() {
            Map<String, String> fields = new TreeMap<>();
            expect('{');
            if (peekSkippingSpace() == '}') {
                expect('}');
                return fields;
            }
            while (true) {
                skipSpace();
                String name = readString();
                expect(':');
                skipSpace();
                fields.put(name, readScalar());
                char next = nextSkippingSpace();
                if (next == '}') {
                    return fields;
                }
                if (next != ',') {
                    fail("expected ',' or '}' in record at offset " + (position - 1));
                }
            }
        }

        /** Reads a string, number, boolean or null and returns it as a normalised literal. */
        private String readScalar() {
            char c = peek();
            if (c == '"') {
                return quote(readString());
            }
            if (c == '{' || c == '[') {
                fail("nested JSON value at offset " + position + "; the backup format has changed shape");
            }
            int start = position;
            while (position < text.length() && ",}] \t\r\n".indexOf(text.charAt(position)) < 0) {
                position++;
            }
            String literal = text.substring(start, position);
            if (!literal.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?|true|false|null")) {
                fail("unexpected JSON literal '" + literal + "' at offset " + start);
            }
            return literal;
        }

        /** Reads a quoted JSON string and returns its decoded value. */
        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (true) {
                if (position >= text.length()) {
                    fail("unterminated JSON string");
                }
                char c = text.charAt(position++);
                if (c == '"') {
                    return value.toString();
                }
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                char escape = text.charAt(position++);
                switch (escape) {
                    case '"': value.append('"'); break;
                    case '\\': value.append('\\'); break;
                    case '/': value.append('/'); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u':
                        value.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default: fail("bad escape \\" + escape + " at offset " + (position - 1));
                }
            }
        }

        char nextSkippingSpace() {
            skipSpace();
            if (position >= text.length()) {
                fail("unexpected end of JSON");
            }
            return text.charAt(position++);
        }

        char peekSkippingSpace() {
            skipSpace();
            return peek();
        }

        void expect(char expected) {
            char actual = nextSkippingSpace();
            if (actual != expected) {
                fail("expected '" + expected + "' but found '" + actual + "' at offset " + (position - 1));
            }
        }

        void expectEnd() {
            skipSpace();
            if (position != text.length()) {
                fail("trailing content after the record array at offset " + position);
            }
        }

        private char peek() {
            if (position >= text.length()) {
                fail("unexpected end of JSON");
            }
            return text.charAt(position);
        }

        private void skipSpace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }
    }

    /**
     * Re-encodes a decoded string as a JSON literal with one fixed escaping: quote, backslash and
     * control characters escaped, everything else (including non-ASCII) kept as-is so the diff
     * stays readable.
     */
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    /**
     * Raised for any input the machine-readable modes refuse. {@link #main} turns it into a
     * message on stderr and exit status 1; unit tests assert on it directly.
     */
    static final class BackupFormatException extends RuntimeException {
        BackupFormatException(String message) {
            super(message);
        }
    }

    private static void fail(String message) {
        throw new BackupFormatException(message);
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
