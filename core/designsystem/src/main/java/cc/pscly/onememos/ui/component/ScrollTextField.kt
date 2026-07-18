package cc.pscly.onememos.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import cc.pscly.onememos.ui.theme.InkSpacing

/**
 * 国漫风“信纸/奏折”输入框：
 * - 使用 BasicTextField 自绘背景横线与左侧朱砂竖线
 * - 保持可滚动；适配深色/浅色主题
 */
@Composable
fun ScrollTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String = "",
) {
    // “信纸横线”的行距：略大一些更有留白感
    val lineHeight = InkSpacing.LinePitch

    val textStyle =
        MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = lineHeight,
        )

    ScrollPaper(
        modifier = modifier,
        lineHeight = lineHeight,
    ) { contentModifier ->
        if (readOnly) {
            Box(modifier = contentModifier) {
                if (value.text.isBlank()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = value.text,
                            style = textStyle,
                        )
                    }
                }
            }
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                readOnly = false,
                modifier = contentModifier,
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                keyboardActions = KeyboardActions(),
                singleLine = false,
                maxLines = Int.MAX_VALUE,
                decorationBox = { innerTextField ->
                    if (value.text.isBlank()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}
