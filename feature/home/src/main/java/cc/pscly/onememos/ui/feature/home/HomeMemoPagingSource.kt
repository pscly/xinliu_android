package cc.pscly.onememos.ui.feature.home

import androidx.paging.PagingData
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.repository.MemoBrowseScope
import kotlinx.coroutines.flow.Flow

interface HomeMemoPagingSource {
    fun active(scope: MemoBrowseScope): Flow<PagingData<Memo>>
    fun archived(scope: MemoBrowseScope): Flow<PagingData<Memo>>
}
