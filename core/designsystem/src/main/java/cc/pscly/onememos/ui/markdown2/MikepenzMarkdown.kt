package cc.pscly.onememos.ui.markdown2

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.ui.component.ScrollPaper
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * 编辑页唯一全量 Markdown 阅读渲染器：mikepenz multiplatform-markdown-renderer 的纸墨皮肤封装。
 *
 * 列表/卡片预览仍由 [cc.pscly.onememos.ui.component.MarkdownPreview]（commonmark）承担；
 * 本组件仅服务编辑页阅读态（单栏阅览 / 双栏右栏 / 只读查看）。
 *
 * 颜色全部取自 MaterialTheme.colorScheme；正文行高对齐 InkSpacing.LinePitch（30sp）。
 * 正文可选中复制：最内层以 SelectionContainer 包裹 Mikepenz Markdown 内容。
 */
@Composable
fun MikepenzMarkdown(
    markdownText: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    ScrollPaper(
        modifier = modifier,
        lineHeight = InkSpacing.LinePitch,
    ) { contentModifier ->
        if (markdownText.isBlank()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = InkSpacing.LinePitch),
                color = MaterialTheme.colorScheme.outline,
                modifier = contentModifier,
            )
            return@ScrollPaper
        }

        val scheme = MaterialTheme.colorScheme
        val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = InkSpacing.LinePitch)
        val codeBody =
            MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = InkSpacing.CodeLineHeight,
            )

        val colors =
            markdownColor(
                text = scheme.onSurface,
                codeBackground = scheme.surfaceVariant,
                inlineCodeBackground = scheme.surfaceVariant,
                dividerColor = scheme.outline.copy(alpha = InkBorder.OutlineSoft),
                tableBackground = scheme.surfaceVariant.copy(alpha = InkBorder.TableHeaderFill),
            )

        val typography =
            markdownTypography(
                h1 = MaterialTheme.typography.titleLarge.copy(color = scheme.onSurface),
                h2 = MaterialTheme.typography.titleMedium.copy(color = scheme.onSurface),
                h3 = MaterialTheme.typography.titleSmall.copy(color = scheme.onSurface),
                h4 = MaterialTheme.typography.titleSmall.copy(color = scheme.onSurface),
                h5 = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                h6 = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                text = body.copy(color = scheme.onSurface),
                code = codeBody.copy(color = scheme.onSurfaceVariant),
                inlineCode =
                    body.copy(
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurfaceVariant,
                    ),
                quote =
                    body.copy(
                        color = scheme.onSurface.copy(alpha = InkBorder.QuoteText),
                        fontStyle = FontStyle.Italic,
                    ),
                paragraph = body.copy(color = scheme.onSurface),
                ordered = body.copy(color = scheme.onSurface),
                bullet = body.copy(color = scheme.onSurface),
                list = body.copy(color = scheme.onSurface),
                textLink =
                    TextLinkStyles(
                        style =
                            SpanStyle(
                                color = scheme.primary,
                                textDecoration = TextDecoration.Underline,
                            ),
                    ),
                table = body.copy(color = scheme.onSurface),
            )

        val padding =
            markdownPadding(
                block = InkSpacing.MarkdownBlockGap,
                list = InkSpacing.X8,
                listItemTop = InkSpacing.X6,
                listItemBottom = InkSpacing.X6,
                listIndent = InkSpacing.X16,
                codeBlock = PaddingValues(InkSpacing.CodeBlockPadding),
                blockQuote = PaddingValues(horizontal = InkSpacing.QuoteGap, vertical = 0.dp),
                blockQuoteText = PaddingValues(vertical = InkSpacing.X6),
                blockQuoteBar =
                    PaddingValues.Absolute(
                        left = 0.dp,
                        top = InkSpacing.X6,
                        right = InkSpacing.QuoteGap,
                        bottom = InkSpacing.X6,
                    ),
            )

        val dimens =
            markdownDimens(
                dividerThickness = InkBorder.Hairline,
                codeBackgroundCornerSize = InkShape.RadiusM,
                blockQuoteThickness = InkSpacing.QuoteBarWidth,
                tableCellWidth = InkSpacing.TableCellMinWidth,
                tableCellPadding = InkSpacing.TableCellPaddingH,
                tableCornerSize = InkShape.RadiusM,
            )

        val markdownState =
            rememberMarkdownState(
                content = markdownText,
            )

        SelectionContainer {
            Markdown(
                markdownState = markdownState,
                colors = colors,
                typography = typography,
                padding = padding,
                dimens = dimens,
                imageTransformer = Coil2ImageTransformerImpl,
                modifier = contentModifier.fillMaxWidth(),
            )
        }
    }
}
