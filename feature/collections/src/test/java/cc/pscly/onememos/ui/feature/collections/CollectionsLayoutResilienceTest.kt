package cc.pscly.onememos.ui.feature.collections

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import cc.pscly.onememos.domain.model.CollectionItem
import cc.pscly.onememos.domain.model.CollectionItemType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h250dp-xxhdpi")
class CollectionsLayoutResilienceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val f1 =
        CollectionItem(
            id = "folder-1",
            itemType = CollectionItemType.FOLDER,
            parentId = null,
            name = "收藏夹A",
            color = null,
            refType = null,
            refId = null,
            sortOrder = 0,
            clientUpdatedAtMs = 0L,
            createdAt = "2026-01-01",
            updatedAt = "2026-01-01",
            deletedAt = null,
            localOnly = false,
            refLocalUuid = null,
        )

    private val f2 =
        f1.copy(id = "folder-2", name = "收藏夹B")

    @Test
    fun foldersList_noVerticalOverlap_onNarrowTallSmallScreen() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                CollectionsItemsList(
                    items = listOf(f1, f2),
                    state = rememberLazyListState(),
                    modifier = Modifier.testTag("col_list"),
                ) { item ->
                    CollectionItemCard(
                        modifier = Modifier.testTag("card_${item.id}"),
                        item = item,
                        noteRefTargetId = null,
                        noteRefMemo = null,
                        enableRichPreview = false,
                        showAutoTagLineInHome = false,
                        autoTagKeywords = emptyList(),
                        selectedTags = emptySet(),
                        onToggleTag = null,
                        selected = false,
                        selectionMode = false,
                        reorderMode = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        onMoveUp = {},
                        onMoveDown = {},
                        onClick = {},
                        onLongClick = {},
                    )
                }
            }
        }

        val card1Bounds =
            composeTestRule
                .onNodeWithTag("card_folder-1")
                .fetchSemanticsNode()
                .boundsInRoot

        val card2Bounds =
            composeTestRule
                .onNodeWithTag("card_folder-2")
                .fetchSemanticsNode()
                .boundsInRoot

        val noVerticalOverlap =
            card1Bounds.bottom <= card2Bounds.top ||
                card2Bounds.bottom <= card1Bounds.top

        assertTrue(
            "Cards must not vertically overlap in narrow viewport. " +
                "卡片1 bounds: ${card1Bounds} / 卡片2 bounds: ${card2Bounds}",
            noVerticalOverlap,
        )
    }
}
