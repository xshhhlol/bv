package dev.aaa1115910.bv.viewmodel.pgc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.pgc.PgcItem
import dev.aaa1115910.biliapi.entity.pgc.PgcRankItem
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.entity.pgc.index.Area
import dev.aaa1115910.biliapi.entity.pgc.index.Copyright
import dev.aaa1115910.biliapi.entity.pgc.index.IndexOrder
import dev.aaa1115910.biliapi.entity.pgc.index.IndexOrderType
import dev.aaa1115910.biliapi.entity.pgc.index.IsFinish
import dev.aaa1115910.biliapi.entity.pgc.index.PgcIndexData
import dev.aaa1115910.biliapi.entity.pgc.index.Producer
import dev.aaa1115910.biliapi.entity.pgc.index.ReleaseDate
import dev.aaa1115910.biliapi.entity.pgc.index.SeasonMonth
import dev.aaa1115910.biliapi.entity.pgc.index.SeasonStatus
import dev.aaa1115910.biliapi.entity.pgc.index.SeasonVersion
import dev.aaa1115910.biliapi.entity.pgc.index.SpokenLanguage
import dev.aaa1115910.biliapi.entity.pgc.index.Style
import dev.aaa1115910.biliapi.entity.pgc.index.Year
import dev.aaa1115910.biliapi.repositories.PgcRepository
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class PgcMovieViewModel(
    override val pgcRepository: PgcRepository
) : PgcViewModel(
    pgcRepository = pgcRepository,
    pgcType = PgcType.Movie
) {
    companion object {
        private val logger = KotlinLogging.logger("PgcMovieViewModel")

        /** 首页每一栏展示的数量，看完整内容需要进排行榜或索引 */
        const val SHELF_SIZE = 20
    }

    /**
     * 热播榜，首页只展示前 [SHELF_SIZE] 个
     */
    val rankItems = mutableStateListOf<PgcRankItem>()
    var rankLoading by mutableStateOf(true)
        private set

    /**
     * 首页的各个片单
     */
    val shelfItems: Map<MovieShelf, SnapshotStateList<PgcItem>> =
        MovieShelf.entries.associateWith { mutableStateListOf() }
    val shelfLoading = mutableStateMapOf<MovieShelf, Boolean>().apply {
        MovieShelf.entries.forEach { put(it, true) }
    }

    private var shelvesRequested = false
    private var shelvesJob: Job? = null

    /**
     * 进入电影页时才加载热播榜和片单，打开 PGC 板块时不会因为电影页多发请求
     */
    fun loadShelvesIfNeeded() {
        if (shelvesRequested) return
        loadShelves()
    }

    override fun reloadAll() {
        super.reloadAll()
        loadShelves()
    }

    private fun loadShelves() {
        logger.fInfo { "Load movie shelves" }
        shelvesRequested = true
        shelvesJob?.cancel()
        rankItems.clear()
        shelfItems.values.forEach { it.clear() }
        rankLoading = true
        MovieShelf.entries.forEach { shelfLoading[it] = true }
        shelvesJob = viewModelScope.launch(Dispatchers.IO) {
            launch { updateRank() }
            MovieShelf.entries.forEach { shelf ->
                launch { updateShelf(shelf) }
            }
        }
    }

    private suspend fun updateRank() {
        runCatching {
            pgcRepository.getRank(pgcType = PgcType.Movie)
        }.onSuccess { data ->
            logger.fInfo { "Find movie rank items, size: ${data.items.size}" }
            withContext(Dispatchers.Main) { rankItems.addAll(data.items.take(SHELF_SIZE)) }
        }.onFailure {
            if (it is CancellationException) throw it
            logger.fWarn { "Update movie rank failed: ${it.stackTraceToString()}" }
        }
        withContext(Dispatchers.Main) { rankLoading = false }
    }

    private suspend fun updateShelf(shelf: MovieShelf) {
        runCatching {
            pgcRepository.getPgcIndex(
                pgcType = PgcType.Movie,
                indexOrder = shelf.indexOrder,
                indexOrderType = IndexOrderType.Desc,
                seasonVersion = SeasonVersion.All,
                spokenLanguage = SpokenLanguage.All,
                area = Area.All,
                isFinish = IsFinish.All,
                copyright = Copyright.All,
                seasonStatus = shelf.seasonStatus,
                seasonMonth = SeasonMonth.All,
                producer = Producer.All,
                year = Year.All,
                releaseDate = ReleaseDate.All,
                style = Style.All,
                page = PgcIndexData.PgcIndexPage(pageSize = SHELF_SIZE)
            )
        }.onSuccess { data ->
            logger.fInfo { "Find movie shelf $shelf items, size: ${data.list.size}" }
            withContext(Dispatchers.Main) { shelfItems.getValue(shelf).addAll(data.list) }
        }.onFailure {
            if (it is CancellationException) throw it
            logger.fWarn { "Update movie shelf $shelf failed: ${it.stackTraceToString()}" }
        }
        withContext(Dispatchers.Main) { shelfLoading[shelf] = false }
    }
}

/**
 * 电影首页的片单，数据来自索引接口，点「查看全部」时带着同样的条件进索引
 */
enum class MovieShelf(
    val indexOrder: IndexOrder,
    val seasonStatus: SeasonStatus = SeasonStatus.All
) {
    Free(indexOrder = IndexOrder.PlayCount, seasonStatus = SeasonStatus.Free),
    RecentlyUpdated(indexOrder = IndexOrder.UpdateTime)
}
