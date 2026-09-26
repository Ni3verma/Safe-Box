import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Unit tests for the canonical dump and the row lines in scripts/InspectBackup.java.
 *
 * The upgrade and restore tests pass or fail on a diff of two canonical dumps, so the
 * normalisation rules inside it decide what counts as data loss, and the row lines decide which
 * titles the records list must show. Each rule is pinned here. No framework: the tool is a single
 * dependency-free file, and so is its test.
 *
 * Run through scripts/tests/inspect-backup-test.sh, which compiles both files together.
 */
public class InspectBackupTest {

    private static int failures = 0;

    public static void main(String[] args) {
        nullAndAbsentFieldsPrintNothing();
        emptyStringIsKeptDistinctFromNull();
        linesArePrefixedWithTypeTitleAndCreationDate();
        stringsAreReEscapedIdentically();
        emptyArrayYieldsNoRecords();
        nestedValueIsRejected();
        recordWithoutTitleIsRejected();
        trailingContentIsRejected();
        rowLineDecodesTheTitle();
        rowLineRejectsTabInTitle();
        if (failures > 0) {
            System.err.println(failures + " assertion(s) failed");
            System.exit(1);
        }
        System.out.println("InspectBackupTest: all passed");
    }

    /** A field a newer format added, left null, must compare equal to an older file lacking it. */
    private static void nullAndAbsentFieldsPrintNothing() {
        List<String> withNull = linesOf("[{\"title\":\"a\",\"creationDate\":1,\"url\":null}]");
        List<String> absent = linesOf("[{\"title\":\"a\",\"creationDate\":1}]");
        assertEquals("null and absent fields", absent, withNull);
    }

    /** The format-2 fixture deliberately mixes "" and null; the dump must not merge them. */
    private static void emptyStringIsKeptDistinctFromNull() {
        List<String> lines = linesOf("[{\"title\":\"a\",\"creationDate\":1,\"url\":\"\"}]");
        assertTrue("empty string printed", lines.contains("LOGIN|\"a\"|1|url=\"\""));
    }

    private static void linesArePrefixedWithTypeTitleAndCreationDate() {
        List<String> lines = linesOf("[{\"title\":\"t|x\",\"creationDate\":42,\"userId\":\"u\"}]");
        assertEquals(
            "prefix",
            List.of(
                "LOGIN|\"t|x\"|42|creationDate=42",
                "LOGIN|\"t|x\"|42|title=\"t|x\"",
                "LOGIN|\"t|x\"|42|userId=\"u\""
            ),
            lines
        );
    }

    /** Two encoders escaping the same character differently must still compare equal. */
    private static void stringsAreReEscapedIdentically() {
        List<String> unicodeEscaped = linesOf(
            "[{\"title\":\"a\",\"creationDate\":1,\"notes\":\"caf\\u00e9\\u000a\\/\"}]");
        List<String> literal = linesOf("[{\"title\":\"a\",\"creationDate\":1,\"notes\":\"café\\n/\"}]");
        assertEquals("escaping", literal, unicodeEscaped);
        assertTrue("newline escaped", literal.contains("LOGIN|\"a\"|1|notes=\"café\\n/\""));
    }

    private static void emptyArrayYieldsNoRecords() {
        assertEquals("empty array", List.of(), InspectBackup.parseFlatObjectArray(" [ ] "));
    }

    private static void nestedValueIsRejected() {
        assertRejected("nested", "[{\"title\":\"a\",\"creationDate\":1,\"x\":{\"y\":1}}]");
    }

    private static void recordWithoutTitleIsRejected() {
        assertRejected("no title", "[{\"creationDate\":1}]");
    }

    private static void trailingContentIsRejected() {
        assertRejected("trailing", "[]x");
    }

    /** The list shows the decoded title, so escapes in the file must not reach the harness. */
    private static void rowLineDecodesTheTitle() {
        Map<String, String> record =
            InspectBackup.parseFlatObjectArray("[{\"title\":\"caf\\u00e9 \\\"x\\\"\",\"creationDate\":1}]").get(0);
        assertEquals("row line", "SECURE_NOTE\tcafé \"x\"", InspectBackup.rowLine("SECURE_NOTE", record));
    }

    /** A tab or newline would split one row into two in the harness's line format. */
    private static void rowLineRejectsTabInTitle() {
        Map<String, String> record =
            InspectBackup.parseFlatObjectArray("[{\"title\":\"a\\tb\",\"creationDate\":1}]").get(0);
        try {
            InspectBackup.rowLine("LOGIN", record);
            failures++;
            System.err.println("FAIL tab in title: expected BackupFormatException");
        } catch (InspectBackup.BackupFormatException expected) {
            // The rule held.
        }
    }

    private static List<String> linesOf(String json) {
        List<String> lines = new ArrayList<>();
        for (Map<String, String> record : InspectBackup.parseFlatObjectArray(json)) {
            lines.addAll(InspectBackup.canonicalLines("LOGIN", new TreeMap<>(record)));
        }
        return lines;
    }

    private static void assertRejected(String name, String json) {
        try {
            linesOf(json);
            failures++;
            System.err.println("FAIL " + name + ": expected BackupFormatException");
        } catch (InspectBackup.BackupFormatException expected) {
            // The rule held.
        }
    }

    private static void assertEquals(String name, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures++;
            System.err.println("FAIL " + name + ":\n  expected " + expected + "\n  actual   " + actual);
        }
    }

    private static void assertTrue(String name, boolean condition) {
        if (!condition) {
            failures++;
            System.err.println("FAIL " + name);
        }
    }
}
