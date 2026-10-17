package com.jarvis.assistant.timemachine

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeMachineTest {
    @Test fun snapshotJsonRoundTrip() {
        val snapshot = SnapshotStore.Snapshot("s1", "PROMPTS", "label", 123L, "data", "checksum")
        val restored = SnapshotStore.Snapshot.fromJson(snapshot.toJson())
        assertEquals(snapshot.id, restored.id)
        assertEquals(snapshot.data, restored.data)
        assertEquals(snapshot.checksum, restored.checksum)
    }

    @Test fun takeSnapshotPersists() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = SnapshotStore(ctx)
        store.takeSnapshot(SnapshotStore.DataType.PROMPTS, "test", "[]")
        val snapshots = store.getSnapshots(SnapshotStore.DataType.PROMPTS)
        assertEquals(1, snapshots.size)
        assertEquals("test", snapshots[0].label)
        assertEquals("[]", snapshots[0].data)
    }

    @Test fun compareSnapshotsDetectsDifferences() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = SnapshotStore(ctx)
        val s1 = store.takeSnapshot(SnapshotStore.DataType.PROMPTS, "v1", "line1\nline2\nline3")
        val s2 = store.takeSnapshot(SnapshotStore.DataType.PROMPTS, "v2", "line1\nline2\nline4")
        val diff = store.compareSnapshots(s1.id, s2.id)
        assertTrue(diff.contains("ADDED") || diff.contains("REMOVED"))
    }

    @Test fun deleteSnapshotRemovesIt() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = SnapshotStore(ctx)
        val s = store.takeSnapshot(SnapshotStore.DataType.PROMPTS, "test", "data")
        assertEquals(1, store.getSnapshots().size)
        store.deleteSnapshot(s.id)
        assertEquals(0, store.getSnapshots().size)
    }

    @Test fun restoreDataReturnsSnapshotContent() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = SnapshotStore(ctx)
        val s = store.takeSnapshot(SnapshotStore.DataType.DECISIONS, "test", "decision data")
        assertEquals("decision data", store.getRestoreData(s.id))
    }

    @Test fun exportImportBackupRoundTrip() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store1 = SnapshotStore(ctx)
        store1.takeSnapshot(SnapshotStore.DataType.PROMPTS, "test", "data1")
        val backup = store1.exportBackup()
        val store2 = SnapshotStore(ctx)
        assertTrue(store2.importBackup(backup))
        assertTrue(store2.getSnapshots().isNotEmpty())
    }

    @Test fun dataTypeEnumHasFiveTypes() {
        assertEquals(5, SnapshotStore.DataType.values().size)
    }
}
