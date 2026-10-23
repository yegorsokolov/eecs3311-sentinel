package sentinel.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sentinel.domain.Event;
import sentinel.domain.ImportBatch;
import sentinel.domain.Summary;
import static org.junit.jupiter.api.Assertions.*;

class LogImporterTest {
    @TempDir Path root;
    private final DatasetStore store = new DatasetStore("demo");

    private String event(String id, String timestamp, String outcome) {
        return "{\"eventId\":\"" + id + "\",\"timestamp\":\"" + timestamp
                + "\",\"eventType\":\"login\",\"accountId\":\"alice\",\"outcome\":\"" + outcome + "\"}";
    }

    private String first() { return event("e1", "2026-10-22T08:00:00-04:00", "failure"); }

    private Path file(String text) throws Exception {
        Path file = root.resolve("events.json");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private Summary load(String text) throws Exception {
        return new LogImporter(store).importFile(file(text));
    }

    @Test void validImportNormalizesUtcAndRetainsSourcePositionWithoutEditingInput() throws Exception {
        Path source = file("[" + first() + "]");
        byte[] original = Files.readAllBytes(source);
        Summary result = new LogImporter(store).importFile(source);
        assertTrue(result.successful());
        assertEquals(1, result.accepted());
        assertEquals("v1", result.datasetVersion());
        Event saved = store.events("v1").get(0);
        assertEquals(Instant.parse("2026-10-22T12:00:00Z"), saved.timestamp());
        assertEquals(source.toUri() + "#/0", saved.sourcePosition());
        assertEquals("v1", saved.datasetVersion());
        assertEquals("alice", saved.accountId());
        assertNull(saved.sourceIp());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test void identicalIdsWithinBatchCountOnceAfterUtcNormalization() throws Exception {
        Summary result = load("[" + first() + "," + event("e1", "2026-10-22T12:00:00Z", "failure") + "]");
        assertEquals(1, result.accepted());
        assertEquals(1, result.duplicates());
        assertEquals(1, store.events("v1").size());
    }

    @Test void reimportFromAnotherSourceRetainsFirstProvenanceAndVersion() throws Exception {
        load("[" + first() + "]");
        Event original = store.events("v1").get(0);
        Path other = root.resolve("copy.json");
        Files.writeString(other, "[" + first() + "]");
        Summary result = new LogImporter(store).importFile(other);
        assertEquals(0, result.accepted());
        assertEquals(1, result.duplicates());
        assertEquals("v1", result.datasetVersion());
        assertEquals(original, store.events("v1").get(0));
    }

    @Test void conflictAfterNewRecordRollsBackWholeBatch() throws Exception {
        load("[" + first() + "]");
        List<Event> before = store.events("v1");
        Summary result = load("[" + event("e2", "2026-10-22T12:01:00Z", "success")
                + "," + event("e1", "2026-10-22T12:00:00Z", "success") + "]");
        assertFalse(result.successful());
        assertEquals(0, result.accepted());
        assertEquals(0, result.duplicates());
        assertEquals("v1", store.currentVersion());
        assertEquals(before, store.events("v1"));
        assertThrows(IllegalArgumentException.class, () -> store.events("v2"));
    }

    @Test void conflictingIdsInsideFirstBatchLeaveEmptyStore() throws Exception {
        Summary result = load("[" + first() + "," + event("e1", "2026-10-22T12:00:00Z", "success") + "]");
        assertFalse(result.successful());
        assertEquals("v0", store.currentVersion());
        assertTrue(store.events("v0").isEmpty());
    }

    @Test void versionedViewsAreImmutableAndRetainIntroductionVersions() throws Exception {
        load("[" + first() + "]");
        List<Event> oldView = store.events("v1");
        Summary second = load("[" + event("e2", "2026-10-22T12:01:00Z", "success") + "]");
        assertEquals("v2", second.datasetVersion());
        assertEquals(1, oldView.size());
        assertEquals(2, store.events("v2").size());
        assertEquals("v1", store.events("v2").get(0).datasetVersion());
        assertEquals("v2", store.events("v2").get(1).datasetVersion());
        assertThrows(UnsupportedOperationException.class, () -> oldView.clear());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "{}", "[null]", "[1]", "[[]]", "[{\"eventId\":\"e2\"}]", "[",
            "[{\"eventId\":\"e2\",\"timestamp\":\"2026-10-22T12:00:00\",\"eventType\":\"login\"}]",
            "[{\"eventId\":\"e2\",\"timestamp\":\"2026-02-30T12:00:00Z\",\"eventType\":\"login\"}]",
            "[{\"eventId\":12,\"timestamp\":\"2026-10-22T12:00:00Z\",\"eventType\":\"login\"}]",
            "[{\"eventId\":\"e2\",\"timestamp\":\"2026-10-22T12:00:00Z\",\"eventType\":\"login\",\"hostId\":{}}]",
            "[{\"eventId\":\"e2\",\"eventId\":\"e3\",\"timestamp\":\"2026-10-22T12:00:00Z\",\"eventType\":\"login\"}]",
            "[{\"eventId\":\"e2\",\"timestamp\":\"2026-10-22T12:00:00Z\",\"eventType\":\"login\",\"sourcePosition\":\"forged\"}]",
            "[] []", "[{},]", "[/* comment */]"
    })
    void invalidFilesLeavePriorVersionUntouched(String invalid) throws Exception {
        load("[" + first() + "]");
        Summary result = load(invalid);
        assertFalse(result.successful());
        assertEquals(0, result.accepted());
        assertEquals("v1", store.currentVersion());
        assertEquals(1, store.events("v1").size());
    }

    @Test void lateSchemaFailureDoesNotIngestEarlierValidRecords() throws Exception {
        Summary result = load("[" + first() + ",{}]");
        assertFalse(result.successful());
        assertEquals("v0", store.currentVersion());
    }

    @Test void missingAndDirectoryInputsAreRejected() {
        LogImporter importer = new LogImporter(store);
        assertFalse(importer.importFile(root.resolve("missing.json")).successful());
        assertFalse(importer.importFile(root).successful());
        assertEquals("v0", store.currentVersion());
    }

    @Test void malformedUtf8IsRejected() throws Exception {
        Path source = root.resolve("bad-encoding.json");
        Files.write(source, new byte[]{'[', '"', (byte) 0xC3, (byte) 0x28, '"', ']'});
        assertFalse(new LogImporter(store).importFile(source).successful());
        assertEquals("v0", store.currentVersion());
    }

    @Test void exactByteLimitAcceptedAndOneByteLessRejected() throws Exception {
        Path source = file("[" + first() + "]");
        int length = Files.readAllBytes(source).length;
        assertFalse(new LogImporter(store, length - 1, 10).importFile(source).successful());
        assertEquals("v0", store.currentVersion());
        assertTrue(new LogImporter(store, length, 10).importFile(source).successful());
    }

    @Test void eventLimitIncludesDuplicatesAndRejectsWholeBatch() throws Exception {
        Path source = file("[" + first() + "," + first() + "]");
        assertFalse(new LogImporter(store, 10_000, 1).importFile(source).successful());
        assertEquals("v0", store.currentVersion());
        assertTrue(new LogImporter(store, 10_000, 2).importFile(source).successful());
    }

    @Test void blankOptionalValuesNormalizeToUnknown() throws Exception {
        Summary result = load("[{\"eventId\":\"e1\",\"timestamp\":\"2026-10-22T12:00:00Z\","
                + "\"eventType\":\"login\",\"accountId\":null,\"sourceIp\":\"  \",\"outcome\":\"\"}]");
        assertTrue(result.successful());
        Event saved = store.events("v1").get(0);
        assertNull(saved.accountId());
        assertNull(saved.sourceIp());
        assertNull(saved.outcome());
        assertNull(saved.hostId());
    }

    @Test void emptyArrayAndUtf8BomAreSupportedWithoutNewVersion() throws Exception {
        assertTrue(load("\uFEFF[]").successful());
        assertEquals("v0", store.currentVersion());
        load("[" + first() + "]");
        assertEquals("v1", load("[]").datasetVersion());
    }

    @Test void errorMessagesDoNotEchoLogContentsAndAreImmutable() throws Exception {
        Summary result = load("[{\"SECRET_SENTINEL_VALUE\":\"private\"}]");
        assertFalse(result.errors().toString().contains("SECRET_SENTINEL_VALUE"));
        assertFalse(result.errors().toString().contains("private"));
        assertThrows(UnsupportedOperationException.class, () -> result.errors().clear());
    }

    @Test void directIngestionAlsoValidatesConflictsAtomically() {
        Event first = new Event("e1", Instant.EPOCH, "login", null, null, null, "failure", "file#/0", "pending");
        Event conflict = new Event("e1", Instant.EPOCH, "login", null, null, null, "success", "file#/1", "pending");
        assertFalse(store.ingest(new ImportBatch(List.of(first, conflict))).successful());
        assertEquals("v0", store.currentVersion());
        assertEquals(1, store.ingest(new ImportBatch(List.of(first))).accepted());
    }
}
