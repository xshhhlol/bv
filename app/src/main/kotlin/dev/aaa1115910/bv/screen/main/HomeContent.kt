package dev.aaa1115910.bv.screen.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.screen.main.home.DynamicsScreen
import dev.aaa1115910.bv.screen.main.home.PopularScreen
import dev.aaa1115910.bv.screen.main.home.RecommendScreen
import dev.aaa1115910.bv.ui.theme.tabContentTransform
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.UserViewModel
import dev.aaa1115910.bv.viewmodel.home.DynamicViewModel
import dev.aaa1115910.bv.viewmodel.home.PopularViewModel
import dev.aaa1115910.bv.viewmodel.home.RecommendViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private val logger = KotlinLogging.logger("HomeContent")

@Composable
fun HomeContent(
    navFocusRequester: FocusRequester,
    recommendViewModel: RecommendViewModel = koinViewModel(),
    popularViewModel: PopularViewModel = koinViewModel(),
    dynamicViewModel: DynamicViewModel = koinViewModel(),
    userViewModel: UserViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    // 切走的页面会被销毁，滚动位置默认也跟着没了；用它把每个 tab 的状态存起来，
    // 切回来还在原来的位置，不用重新往下翻
    val tabStateHolder = rememberSaveableStateHolder()

    val firstTab = remember { Prefs.firstHomeTopNavItem }
    // 和 TopNav 里的选中项一起存，从别的板块切回主页时还停在原来那一栏
    var selectedTab by rememberSaveable(
        stateSaver = Saver(
            save = { it.name },
            restore = { HomeTopNavItem.valueOf(it) }
        )
    ) { mutableStateOf(firstTab) }
    var focusOnContent by remember { mutableStateOf(false) }

    val getReorderedItems: (HomeTopNavItem) -> List<HomeTopNavItem> = { item ->
        val allItems = HomeTopNavItem.entries
        val startIndex = allItems.indexOf(item)
        if (startIndex == -1) emptyList()
        else allItems.drop(startIndex) + allItems.take(startIndex)
    }
    val reorderedItems = remember {
        getReorderedItems(firstTab)
    }

    // 首次进来才拉数据。
    // 这个 effect 在每次从「分区」「影视」切回主页时都会重跑一次，而 loadMore() 本身不判空，
    // 来回切几趟就会给每个列表各追加好几页——请求白发一轮，列表还越滚越长，越用越卡。
    LaunchedEffect(Unit) {
        if (recommendViewModel.recommendVideoList.isEmpty()) {
            scope.launch(Dispatchers.IO) { recommendViewModel.loadMore() }
        }
        if (popularViewModel.popularVideoList.isEmpty()) {
            scope.launch(Dispatchers.IO) { popularViewModel.loadMore() }
        }
        dynamicViewModel.ensureCurrentTabLoaded()
        // 内部有 shouldUpdateInfo 节流，重复调用不会真的发请求
        userViewModel.updateUserInfo()
    }

    //监听登录变化
    LaunchedEffect(userViewModel.isLogin) {
        if (userViewModel.isLogin) {
            //login
            userViewModel.updateUserInfo()
        } else {
            //logout
            userViewModel.clearUserInfo()
        }
    }

    Scaffold(
        topBar = {
            TopNav(
                modifier = Modifier
                    .focusRequester(navFocusRequester),
                items = reorderedItems,
                isLargePadding = !focusOnContent,
                onSelectedChanged = { nav ->
                    selectedTab = nav as HomeTopNavItem
                    when (nav) {
                        HomeTopNavItem.Recommend -> {}
                        HomeTopNavItem.Popular -> {}
                        HomeTopNavItem.Dynamics -> dynamicViewModel.ensureCurrentTabLoaded()
                    }
                },
                onClick = { nav ->
                    when (nav) {
                        HomeTopNavItem.Recommend -> {
                            logger.fInfo { "clear recommend data" }
                            recommendViewModel.clearData()
                            logger.fInfo { "reload recommend data" }
                            scope.launch(Dispatchers.IO) { recommendViewModel.loadMore() }
                        }

                        HomeTopNavItem.Popular -> {
                            logger.fInfo { "clear popular data" }
                            popularViewModel.clearData()
                            logger.fInfo { "reload popular data" }
                            scope.launch(Dispatchers.IO) { popularViewModel.loadMore() }
                        }

                        HomeTopNavItem.Dynamics -> dynamicViewModel.refresh()
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .onFocusChanged { focusOnContent = it.hasFocus }
                .onPreviewKeyEvent {
                    if (it.key == Key.Menu) {
                        if (it.type == KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        when (selectedTab) {
                            HomeTopNavItem.Recommend -> {
                                recommendViewModel.clearData()
                                scope.launch(Dispatchers.IO) { recommendViewModel.loadMore() }
                            }

                            HomeTopNavItem.Popular -> {
                                popularViewModel.clearData()
                                scope.launch(Dispatchers.IO) { popularViewModel.loadMore() }
                            }

                            HomeTopNavItem.Dynamics -> dynamicViewModel.refresh()
                        }
                        navFocusRequester.requestFocus()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                },
        ) {
            AnimatedContent(
                targetState = selectedTab,
                label = "home animated content",
                transitionSpec = {
                    tabContentTransform(
                        forward = reorderedItems.indexOf(targetState) >= reorderedItems.indexOf(initialState)
                    )
                }
            ) { screen ->
                tabStateHolder.SaveableStateProvider(screen.name) {
                    when (screen) {
                        HomeTopNavItem.Recommend -> RecommendScreen()
                        HomeTopNavItem.Popular -> PopularScreen()
                        HomeTopNavItem.Dynamics -> DynamicsScreen()
                    }
                }
            }
        }
    }
}
