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
 * M2.7 新引擎：mikepenz multiplatform-markdown-renderer 的纸墨皮肤封装。
 *
 * 与旧 [cc.pscly.onememos.ui.component.MarkdownPaper] 并存；由 AppSettings.useNewMarkdownEngine
 *（默认 true）在调用点分发。渲染矩阵（标题/粗斜体/链接/代码/待办/表格/图片/嵌套引用）
 * 全绿后可退役旧实现。
 *
 * 颜色全部取自 MaterialTheme.colorScheme；正文行高对齐 InkSpacing.LinePitch（30sp）。
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
