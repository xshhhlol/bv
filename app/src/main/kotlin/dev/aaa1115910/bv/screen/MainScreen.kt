package dev.aaa1115910.bv.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.rememberDrawerState
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.settings.SettingsActivity
import dev.aaa1115910.bv.activities.user.FollowActivity
import dev.aaa1115910.bv.activities.user.LoginActivity
import dev.aaa1115910.bv.activities.user.UserSwitchActivity
import dev.aaa1115910.bv.component.UserPanel
import dev.aaa1115910.bv.screen.main.HomeContent
import dev.aaa1115910.bv.screen.main.LeftNaviContent
import dev.aaa1115910.bv.screen.main.LeftNaviItem
import dev.aaa1115910.bv.screen.main.PersonalContent
import dev.aaa1115910.bv.screen.main.PgcContent
import dev.aaa1115910.bv.screen.main.UgcContent
import dev.aaa1115910.bv.screen.search.SearchInputScreen
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.ui.theme.sectionContentTransform
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.UserViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.androidx.compose.koinViewModel

private val logger = KotlinLogging.logger("MainScreen")

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    userViewModel: UserViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var showUserPanel by remember { mutableStateOf(false) }
    var lastPressBack: Long by remember { mutableLongStateOf(0L) }
    var selectedDrawerItem by remember { mutableStateOf(Prefs.homeLeftNaviItem) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 板块切走时页面会被销毁，用它把每个板块的状态（选中的 tab、列表滚动位置）存下来
    val sectionStateHolder = rememberSaveableStateHolder()

    val personalFocusRequester = remember { FocusRequester() }
    val mainFocusRequester = remember { FocusRequester() }
    val ugcFocusRequester = remember { FocusRequester() }
    val pgcFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    val handleBack = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPressBack < 1000 * 3) {
            logger.fInfo { "Exiting bug video" }
            (context as Activity).finish()
        } else {
            lastPressBack = currentTime
            R.string.home_press_back_again_to_exit.toast(context)
        }
    }

    val onFocusToContent: () -> Unit = {
        when (selectedDrawerItem) {
            LeftNaviItem.Home -> mainFocusRequester.requestFocus()
            LeftNaviItem.UGC -> ugcFocusRequester.requestFocus()
            LeftNaviItem.PGC -> pgcFocusRequester.requestFocus()
            LeftNaviItem.Search -> searchFocusRequester.requestFocus()
            LeftNaviItem.Personal -> personalFocusRequester.requestFocus()
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            onFocusToContent()
        }.onFailure {
            logger.fException(it) { "request default focus requester failed" }
        }
    }

    BackHandler {
        handleBack()
    }

    NavigationDrawer(
        modifier = modifier,
        drawerContent = {
            LeftNaviContent(
                isLogin = userViewModel.isLogin,
                avatar = userViewModel.face,
                //avatar = "https://i2.hdslb.com/bfs/face/ef0457addb24141e15dfac6fbf45293ccf1e32ab.jpg",
                //username = "碧诗",
                selectedItem = selectedDrawerItem,
                onLeftNaviItemChanged = { selectedDrawerItem = it },
                onOpenSettings = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                onFocusToContent = onFocusToContent,
                onShowUserPanel = {
                    showUserPanel = true
                },
                onLogin = {
                    context.startActivity(Intent(context, LoginActivity::class.java))
                }
            )
        },
        drawerState = drawerState
    ) {
        Box(
            modifier = Modifier
        ) {
            AnimatedContent(
                targetState = selectedDrawerItem,
                label = "main animated content",
                transitionSpec = {
                    sectionContentTransform(forward = targetState.ordinal >= initialState.ordinal)
                }
            ) { screen ->
                sectionStateHolder.SaveableStateProvider(screen.name) {
                    when (screen) {
                        LeftNaviItem.Search -> SearchInputScreen(defaultFocusRequester = searchFocusRequester)
                        LeftNaviItem.Personal -> PersonalContent(navFocusRequester = personalFocusRequester)
                        LeftNaviItem.Home -> HomeContent(navFocusRequester = mainFocusRequester)
                        LeftNaviItem.UGC -> UgcContent(navFocusRequester = ugcFocusRequester)
                        LeftNaviItem.PGC -> PgcContent(navFocusRequester = pgcFocusRequester)
                        else -> {}
                    }
                }
            }

            AnimatedVisibility(
                visible = showUserPanel,
                // 面板从中心轻微放大着淡入，比单纯 fade 更有「弹出来」的感觉
                enter = fadeIn(tween(BVMotion.DurationMedium, easing = BVMotion.EmphasizedEasing)) +
                        scaleIn(
                            animationSpec = tween(
                                BVMotion.DurationMedium,
                                easing = BVMotion.EmphasizedDecelerateEasing
                            ),
                            initialScale = 0.94f
                        ),
                exit = fadeOut(tween(BVMotion.DurationFast)) + scaleOut(targetScale = 0.97f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 径向遮罩：中间稍亮，边缘压暗，把注意力收到面板上
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.86f)
                                )
                            )
                        )
                ) {
                    UserPanel(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(12.dp),
                        username = userViewModel.username,
                        face = userViewModel.face,
                        level = userViewModel.responseData?.level ?: 0,
                        currentExp = userViewModel.responseData?.levelExp?.currentExp ?: 0,
                        nextLevelExp = with(userViewModel.responseData?.levelExp?.nextExp) {
                            if (this == null) {
                                1
                            } else if (this <= 0) {
                                userViewModel.responseData?.levelExp?.currentExp ?: 1
                            } else {
                                (userViewModel.responseData?.levelExp?.currentExp ?: 1)
                                +(userViewModel.responseData?.levelExp?.nextExp ?: 0)
                            }
                        },
                        onHide = { showUserPanel = false },
                        onGoUserSwitch = {
                            context.startActivity(Intent(context, UserSwitchActivity::class.java))
                        },
                        onGoFollowingUp = {
                            context.startActivity(Intent(context, FollowActivity::class.java))
                        },
                    )
                }
            }
        }
    }
}
