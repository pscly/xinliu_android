package cc.pscly.onememos.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import cc.pscly.onememos.domain.model.ThemeTexture
import cc.pscly.onememos.ui.accessibility.PaperInkFocusIndicator
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.LocalThemeTexture

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    contentPadding: PaddingValues = PaddingValues(InkSpacing.CardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = InkShape.Card
    val texture = LocalThemeTexture.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val clickable = onClick != null || onLongClick != null
    // 清简质感：仅发丝细描边（更弱一级），聚焦只换描边色、不再叠第二层边框
    val idleOutlineAlpha =
        if (texture == ThemeTexture.MINIMAL) InkBorder.OutlineSoft else InkBorder.OutlineStrong
    val borderColor =
        when {
            focused && clickable -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline.copy(alpha = idleOutlineAlpha)
        }
    val border = BorderStroke(InkBorder.Hairline, borderColor)

    val interactiveModifier =
        when {
            onClick != null && onLongClick != null -> {
                Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = InkSpacing.TouchTargetMin)
                    .combinedClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
            }
            onClick != null -> {
                Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = InkSpacing.TouchTargetMin)
                    .clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
            }
            else -> Modifier
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(interactiveModifier)
                .semantics(mergeDescendants = true) {
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                    }
                    if (clickable) {
                        role = Role.Button
                    }
                    if (clickable && !enabled) {
                        disabled()
                    }
                }
                .then(
                    // 清简质感：聚焦仅换主描边色（上方 borderColor），不再叠第二层焦点环
                    if (focused && clickable && texture != ThemeTexture.MINIMAL) {
                        with(PaperInkFocusIndicator) {
                            Modifier.paperInkFocusBorder(focused = true, shape = shape)
                        }
                    } else {
                        Modifier
                    },
                ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = border,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
