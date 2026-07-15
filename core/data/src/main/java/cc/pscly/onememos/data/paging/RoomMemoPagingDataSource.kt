package cc.pscly.onememos.data.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.data.mapper.toDomain
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.repository.MemoBrowseScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomMemoPagingDataSource @Inject constructor(
    private val memoDao: MemoDao,
) : MemoPagingDataSource {
    override fun active(scope: MemoBrowseScope): Flow<PagingData<Memo>> =
        Pager(
            config =
                PagingConfig(
                    pageSize = 50,
                    prefetchDistance = 20,
                    enablePlaceholders = false,
                ),
            pagingSourceFactory = {
                when (scope) {
                    MemoBrowseScope.All -> memoDao.pagingActiveAll()
                    MemoBrowseScope.LocalOnly -> memoDao.pagingActiveLocalOnly()
                    is MemoBrowseScope.Creator -> memoDao.pagingActiveForCreator(scope.creator)
                }
            },
        )
            .flow
            .map { pagingData -> pagingData.map { it.toDomain() } }

    override fun archived(scope: MemoBrowseScope): Flow<PagingData<Memo>> =
        Pager(
            config =
                PagingConfig(
                    pageSize = 50,
                    prefetchDistance = 20,
                    enablePlaceholders = false,
                ),
            pagingSourceFactory = {
                when (scope) {
                    MemoBrowseScope.All -> memoDao.pagingArchivedAll()
                    MemoBrowseScope.LocalOnly -> memoDao.pagingArchivedLocalOnly()
                    is MemoBrowseScope.Creator -> memoDao.pagingArchivedForCreator(scope.creator)
                }
            },
        )
            .flow
            .map { pagingData -> pagingData.map { it.toDomain() } }
}
