package cc.pscly.onememos.ui.feature.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchInputStateTest {
    @Test
    fun `initialSearchFieldValue 光标在末尾`() {
        val value = initialSearchFieldValue("hello")

        assertEquals("hello", value.text)
        assertEquals(TextRange(5), value.selection)
    }

    @Test
    fun `syncSearchFieldValueWithExternalQuery 文本一致时复用对象`() {
        val current = TextFieldValue(text = "abc", selection = TextRange(1))

        val next = syncSearchFieldValueWithExternalQuery(current = current, query = "abc")

        assertSame(current, next)
    }

    @Test
    fun `syncSearchFieldValueWithExternalQuery 文本变化时重置为末尾光标`() {
        val current = TextFieldValue(text = "abc", selection = TextRange(1))

        val next = syncSearchFieldValueWithExternalQuery(current = current, query = "abcd")

        assertEquals("abcd", next.text)
        assertEquals(TextRange(4), next.selection)
    }
}

