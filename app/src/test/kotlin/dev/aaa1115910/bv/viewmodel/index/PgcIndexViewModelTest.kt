package dev.aaa1115910.bv.viewmodel.index

import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.entity.pgc.index.Area
import dev.aaa1115910.biliapi.entity.pgc.index.IndexOrder
import dev.aaa1115910.biliapi.entity.pgc.index.SeasonStatus
import dev.aaa1115910.biliapi.entity.pgc.index.Style
import dev.aaa1115910.biliapi.repositories.PgcRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class PgcIndexViewModelTest {
    @Test
    fun `preset is applied on first init`() {
        val viewModel = PgcIndexViewModel(PgcRepository())

        viewModel.initIfNeeded(
            pgcType = PgcType.Movie,
            seasonStatus = SeasonStatus.Free,
            style = Style.ScienceFiction
        )

        assertEquals(PgcType.Movie, viewModel.pgcType)
        assertEquals(IndexOrder.PlayCount, viewModel.indexOrder)
        assertEquals(SeasonStatus.Free, viewModel.seasonStatus)
        assertEquals(Style.ScienceFiction, viewModel.style)
    }

    @Test
    fun `init again after recreation keeps user changed filters`() {
        val viewModel = PgcIndexViewModel(PgcRepository())
        viewModel.initIfNeeded(pgcType = PgcType.Movie, style = Style.ScienceFiction)

        // 用户在筛选里改了条件，随后 Activity 重建，页面拿同一个 Intent 再初始化一次
        viewModel.indexOrder = IndexOrder.UpdateTime
        viewModel.style = Style.Comedy
        viewModel.initIfNeeded(pgcType = PgcType.Movie, style = Style.ScienceFiction)

        assertEquals(IndexOrder.UpdateTime, viewModel.indexOrder)
        assertEquals(Style.Comedy, viewModel.style)
    }

    @Test
    fun `preset not available for the type is ignored`() {
        val viewModel = PgcIndexViewModel(PgcRepository())

        // 综艺没有地区筛选
        viewModel.initIfNeeded(pgcType = PgcType.Variety, area = Area.America)

        assertEquals(Area.All, viewModel.area)
    }
}
