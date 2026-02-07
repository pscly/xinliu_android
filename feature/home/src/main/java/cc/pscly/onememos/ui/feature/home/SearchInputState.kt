package cc.pscly.onememos.ui.feature.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal fun initialSearchFieldValue(query: String): TextFieldValue =
    TextFieldValue(
        text = query,
        selection = TextRange(query.length),
    )

internal fun syncSearchFieldValueWithExternalQuery(
    current: TextFieldValue,
    query: String,
): TextFieldValue {
    if (current.text == query) return current
    return initialSearchFieldValue(query)
}

