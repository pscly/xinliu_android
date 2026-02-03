package cc.pscly.onememos.ui.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TagCompletionTest {
    @Test
    fun `findTagPrefix - basic`() {
        val text = "hello #wo"
        val v = TextFieldValue(text = text, selection = TextRange(text.length))
        val p = TagCompletion.findTagPrefix(v)
        assertNotNull(p)
        assertEquals("wo", p!!.prefix)
    }

    @Test
    fun `applySuggestion - inserts full tag`() {
        val text = "hello #wo"
        val v = TextFieldValue(text = text, selection = TextRange(text.length))
        val p = TagCompletion.findTagPrefix(v)!!
        val out = TagCompletion.applySuggestion(v, tag = "work", prefix = p)
        assertEquals("hello #work", out.text)
    }

    @Test
    fun `findTagPrefix - chinese adjacency`() {
        val text = "今天#心"
        val v = TextFieldValue(text = text, selection = TextRange(text.length))
        val p = TagCompletion.findTagPrefix(v)
        assertNotNull(p)
        assertEquals("心", p!!.prefix)
    }
}

