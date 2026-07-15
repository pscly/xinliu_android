package cc.pscly.onememos.di

import androidx.paging.PagingData
import cc.pscly.onememos.data.paging.MemoPagingDataSource
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.repository.MemoBrowseScope
import cc.pscly.onememos.ui.feature.home.HomeMemoPagingSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class HomeMemoPagingSourceAdapter @Inject constructor(
    private val dataSource: MemoPagingDataSource,
) : HomeMemoPagingSource {

    override fun active(scope: MemoBrowseScope): Flow<PagingData<Memo>> =
        dataSource.active(scope)

    override fun archived(scope: MemoBrowseScope): Flow<PagingData<Memo>> =
        dataSource.archived(scope)
}
