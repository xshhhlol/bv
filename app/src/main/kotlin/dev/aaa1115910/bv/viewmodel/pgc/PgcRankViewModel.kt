package dev.aaa1115910.bv.viewmodel.pgc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.pgc.PgcRankDay
import dev.aaa1115910.biliapi.entity.pgc.PgcRankItem
import dev.aaa1115910.biliapi.entity.pgc.PgcType
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
class PgcRankViewModel(
    private val pgcRepository: PgcRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    var pgcType by mutableStateOf(PgcType.Movie)
        private set
    var rankDay by mutableStateOf(PgcRankDay.ThreeDays)
        private set

    val rankItems = mutableStateListOf<PgcRankItem>()
    var note by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    private var loadJob: Job? = null

    /**
     * 加载排行榜，切换统计周期时会丢弃上一次还没完成的请求
     */
    fun load(pgcType: PgcType = this.pgcType, rankDay: PgcRankDay = this.rankDay) {
        this.pgcType = pgcType
        this.rankDay = rankDay
        loadJob?.cancel()
        rankItems.clear()
        note = ""
        loadFailed = false
        loading = true
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                pgcRepository.getRank(pgcType = pgcType, day = rankDay)
            }.onSuccess { data ->
                logger.fInfo { "Load $pgcType rank[$rankDay] success, size: ${data.items.size}" }
                withContext(Dispatchers.Main) {
                    rankItems.addAll(data.items)
                    note = data.note
                    loading = false
                }
            }.onFailure {
                if (it is CancellationException) throw it
                logger.fWarn { "Load $pgcType rank[$rankDay] failed: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    loadFailed = true
                    loading = false
                }
            }
        }
    }
}
