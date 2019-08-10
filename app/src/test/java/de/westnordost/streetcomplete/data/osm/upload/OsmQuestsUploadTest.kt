package de.westnordost.streetcomplete.data.osm.upload

import de.westnordost.osmapi.map.data.Element
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.streetcomplete.any
import de.westnordost.streetcomplete.data.QuestStatus
import de.westnordost.streetcomplete.data.osm.ElementGeometry
import de.westnordost.streetcomplete.data.osm.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.OsmQuest
import de.westnordost.streetcomplete.data.osm.changes.StringMapChanges
import de.westnordost.streetcomplete.data.osm.changes.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.persist.MergedElementDao
import de.westnordost.streetcomplete.data.osm.persist.OsmQuestDao
import de.westnordost.streetcomplete.data.upload.OnUploadedChangeListener
import de.westnordost.streetcomplete.on
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class OsmQuestsUploadTest {
    private lateinit var questDB: OsmQuestDao
    private lateinit var elementDB: MergedElementDao
    private lateinit var changesetManager: OpenQuestChangesetsManager
    private lateinit var singleChangeUpload: SingleOsmElementTagChangesUpload
    private lateinit var uploader: OsmQuestsUpload

    @Before fun setUp() {
        questDB = mock(OsmQuestDao::class.java)
        elementDB = mock(MergedElementDao::class.java)
        on(elementDB.get(any(), anyLong())).thenReturn(mock(Element::class.java))
        changesetManager = mock(OpenQuestChangesetsManager::class.java)
        singleChangeUpload = mock(SingleOsmElementTagChangesUpload::class.java)
        uploader = OsmQuestsUpload(questDB, elementDB, changesetManager, singleChangeUpload)
    }

    @Test fun `cancel upload works`() {
        uploader.upload(AtomicBoolean(true))
        verifyZeroInteractions(changesetManager, singleChangeUpload, elementDB, questDB)
    }

    @Test fun `catches ElementConflict exception`() {
        on(questDB.getAll(null, QuestStatus.ANSWERED)).thenReturn(listOf(createQuest()))
        on(singleChangeUpload.upload(anyLong(), any(), any()))
            .thenThrow(ElementConflictException())

        uploader.upload(AtomicBoolean(false))

        // will not throw ElementConflictException
    }

    @Test fun `discard if element was deleted`() {
        on(questDB.getAll(null, QuestStatus.ANSWERED)).thenReturn(listOf(createQuest()))
        on(elementDB.get(any(), anyLong())).thenReturn(null)

        uploader.uploadedChangeListener = mock(OnUploadedChangeListener::class.java)
        uploader.upload(AtomicBoolean(false))

        verify(uploader.uploadedChangeListener)?.onDiscarded()
    }

    @Test fun `catches ChangesetConflictException exception and tries again once`() {
        on(questDB.getAll(null, QuestStatus.ANSWERED)).thenReturn(listOf(createQuest()))
        on(singleChangeUpload.upload(anyLong(), any(), any()))
            .thenThrow(ChangesetConflictException())
            .thenReturn(mock(Element::class.java))

        uploader.upload(AtomicBoolean(false))

        // will not throw ChangesetConflictException but instead call single upload twice
        verify(changesetManager).getOrCreateChangeset(any(), any())
        verify(changesetManager).createChangeset(any(), any())
        verify(singleChangeUpload, times(2)).upload(anyLong(), any(), any())
    }

    @Test fun `close each uploaded quest in local DB and call listener`() {
        val quests = listOf( createQuest(), createQuest())

        on(questDB.getAll(null, QuestStatus.ANSWERED)).thenReturn(quests)
        on(singleChangeUpload.upload(anyLong(), any(), any()))
            .thenReturn(mock(Element::class.java))

        uploader.uploadedChangeListener = mock(OnUploadedChangeListener::class.java)
        uploader.upload(AtomicBoolean(false))

        for (quest in quests) {
            assertEquals(QuestStatus.CLOSED, quest.status)
        }
        verify(questDB, times(2)).update(any())
        verify(uploader.uploadedChangeListener, times(2))?.onUploaded()
    }

    @Test fun `delete each unsuccessful upload from local DB and call listener`() {
        val quests = listOf( createQuest(), createQuest())

        on(questDB.getAll(null, QuestStatus.ANSWERED)).thenReturn(quests)
        on(singleChangeUpload.upload(anyLong(), any(), any()))
            .thenThrow(ElementConflictException())

        uploader.uploadedChangeListener = mock(OnUploadedChangeListener::class.java)
        uploader.upload(AtomicBoolean(false))

        verify(questDB, times(2)).delete(anyLong())
        verify(uploader.uploadedChangeListener, times(2))?.onDiscarded()
    }
}

private fun createQuest() : OsmQuest {
    val changes = StringMapChanges(listOf(StringMapEntryAdd("surface","asphalt")))
    val geometry = ElementGeometry(OsmLatLon(0.0,0.0))
    val questType = mock(OsmElementQuestType::class.java)
    return OsmQuest(1L, questType, Element.Type.NODE, 1L, QuestStatus.ANSWERED, changes, "survey",
        Date(), geometry)
}
