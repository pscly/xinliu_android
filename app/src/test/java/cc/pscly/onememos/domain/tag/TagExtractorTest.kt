package cc.pscly.onememos.domain.tag

import org.junit.Assert.assertEquals
import org.junit.Test

class TagExtractorTest {
    @Test
    fun `extract tags - basic`() {
        assertEquals(
            listOf("work", "life"),
            TagExtractor.extractAll("hello #work #life"),
        )
    }

    @Test
    fun `extract tags - chinese adjacency`() {
        assertEquals(
            listOf("心情"),
            TagExtractor.extractAll("今天#心情 很好"),
        )
    }

    @Test
    fun `extract tags - ignore markdown heading`() {
        assertEquals(
            emptyList<String>(),
            TagExtractor.extractAll("# 标题"),
        )
    }

    @Test
    fun `extract tags - ignore C sharp`() {
        assertEquals(
            emptyList<String>(),
            TagExtractor.extractAll("C# is a language"),
        )
    }

    @Test
    fun `stripTagTokens - keeps plain text`() {
        assertEquals(
            "hello world",
            TagExtractor.stripTagTokens("hello #work world #心情"),
        )
    }
}

