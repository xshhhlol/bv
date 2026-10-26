package dev.aaa1115910.bv.viewmodel.user

import dev.aaa1115910.biliapi.entity.season.FollowingSeasonType
import dev.aaa1115910.biliapi.repositories.AuthRepository
import dev.aaa1115910.biliapi.repositories.SeasonRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class FollowingSeasonViewModelTest {
    private fun createViewModel() = FollowingSeasonViewModel(SeasonRepository(AuthRepository()))

    @Test
    fun `without initial type it stays bangumi`() {
        val viewModel = createViewModel()

        viewModel.applyInitialType(null)

        assertEquals(FollowingSeasonType.Bangumi, viewModel.followingSeasonType)
    }

    @Test
    fun `init again after recreation keeps user selected type`() {
        val viewModel = createViewModel()
        viewModel.applyInitialType(FollowingSeasonType.Cinema)
        assertEquals(FollowingSeasonType.Cinema, viewModel.followingSeasonType)

        // 用户在筛选里切回番剧，随后 Activity 重建，页面拿同一个 Intent 再初始化一次
        viewModel.followingSeasonType = FollowingSeasonType.Bangumi
        viewModel.applyInitialType(FollowingSeasonType.Cinema)

        assertEquals(FollowingSeasonType.Bangumi, viewModel.followingSeasonType)
    }
}
