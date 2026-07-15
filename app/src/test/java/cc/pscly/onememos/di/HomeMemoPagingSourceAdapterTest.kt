package cc.pscly.onememos.di

import androidx.paging.PagingData
import cc.pscly.onememos.data.paging.MemoPagingDataSource
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.repository.MemoBrowseScope
import junit.framework.TestCase.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * HomeMemoPagingSourceAdapter 契约测试。
 *
 * 断言 adapter 对三种 MemoBrowseScope 原样转发 scope、Flow 与 PagingData 实例，
 * 不复制分页策略。
 */
class HomeMemoPagingSourceAdapterTest {

    // ── Fake 实现 ──────────────────────────────────────────

    private class FakeMemoPagingDataSource(
        private val activeData: PagingData<Memo> = PagingData.empty(),
        private val archivedData: PagingData<Memo> = PagingData.empty(),
    ) : MemoPagingDataSource {
        override fun active(scope: MemoBrowseScope): Flow<PagingData<Memo>> = flowOf(activeData)
        override fun archived(scope: MemoBrowseScope): Flow<PagingData<Memo>> = flowOf(archivedData)
    }

    @Test
    fun active_all_scope_forwards_pagingData() = runBlocking {
        val expected = PagingData.empty<Memo>()
        val dataSource = FakeMemoPagingDataSource(activeData = expected)
        val adapter = HomeMemoPagingSourceAdapter(dataSource)

        val scope = MemoBrowseScope.All
        val result = adapter.active(scope).first()

        assertEquals("PagingData 必须原样转发，不复制分页策略", expected, result)
    }

    @Test
    fun archived_all_scope_forwards_pagingData() = runBlocking {
        val expected = PagingData.empty<Memo>()
        val dataSource = FakeMemoPagingDataSource(archivedData = expected)
        val adapter = HomeMemoPagingSourceAdapter(dataSource)

        val scope = MemoBrowseScope.All
        val result = adapter.archived(scope).first()

        assertEquals("PagingData 必须原样转发，不复制分页策略", expected, result)
    }

    // ── LocalOnly scope ────────────────────────────────────

    @Test
    fun active_localOnly_scope_forwards_pagingData() = runBlocking {
        val expected = PagingData.empty<Memo>()
        val dataSource = FakeMemoPagingDataSource(activeData = expected)
        val adapter = HomeMemoPagingSourceAdapter(dataSource)

        val scope = MemoBrowseScope.LocalOnly
        val result = adapter.active(scope).first()

        assertEquals("PagingData 必须原样转发，不复制分页策略", expected, result)
    }

    @Test
    fun archived_localOnly_scope_forwards_pagingData() = runBlocking {
        val expected = PagingData.empty<Memo>()
        val dataSource = FakeMemoPagingDataSource(archivedData = expected)
        val adapter = HomeMemoPagingSourceAdapter(dataSource)

        val scope = MemoBrowseScope.LocalOnly
        val result = adapter.archived(scope).first()

        assertEquals("PagingData 必须原样转发，不复制分页策略", expected, result)
    }

    // ── Creator scope ──────────────────────────────────────

    @Test
    fun active_creator_scope_forwards_pagingData() = runBlocking {
        val expected = PagingData.empty<Memo>()
        val dataSource = FakeMemoPagingDataSource(activeData = expected)
        val adapter = HomeMemoPagingSourceAdapter(dataSource)

        val scope = MemoBrowseScope.Creator("test-user")
        val result = adapter.active(scope).first()

        assertEquals("PagingData 必须原样转发，不复制分页策略", expected, result)
    }

    @Test
    fun archived_creator_scope_forwards_pagingData() = runBlocking {
        val expected = PagingData.empty<Memo>()
        val dataSource = FakeMemoPagingDataSource(archivedData = expected)
        val adapter = HomeMemoPagingSourceAdapter(dataSource)

        val scope = MemoBrowseScope.Creator("test-user")
        val result = adapter.archived(scope).first()

        assertEquals("PagingData 必须原样转发，不复制分页策略", expected, result)
    }
}
