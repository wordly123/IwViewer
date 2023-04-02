package com.rerere.iwara4a.ui.screen.index

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.rerere.iwara4a.BuildConfig
import com.rerere.iwara4a.R
import com.rerere.iwara4a.sharedPreferencesOf
import com.rerere.iwara4a.ui.component.Md3BottomNavigation
import com.rerere.iwara4a.ui.component.Md3TopBar
import com.rerere.iwara4a.ui.component.md.Banner
import com.rerere.iwara4a.ui.local.LocalNavController
import com.rerere.iwara4a.ui.local.LocalSelfData
import com.rerere.iwara4a.ui.screen.index.page.ExplorePage
import com.rerere.iwara4a.ui.screen.index.page.RankPage
import com.rerere.iwara4a.ui.screen.index.page.SubPage
import com.rerere.iwara4a.ui.states.rememberPrimaryClipboardState
import com.rerere.iwara4a.util.DataState
import com.rerere.iwara4a.util.findActivity
import com.rerere.iwara4a.util.getVersionName
import com.rerere.iwara4a.util.openUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.time.Duration.Companion.days

@Composable
fun IndexScreen(navController: NavController, indexViewModel: IndexViewModel = hiltViewModel()) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    //val screenType = rememberWindowSizeClass()
    val screenType = calculateWindowSizeClass(LocalContext.current.findActivity())
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            IndexDrawer(navController, indexViewModel, drawerState)
        }
    ) {
        Scaffold(
            topBar = {
                TopBar(drawerState, indexViewModel)
            }
        ) { innerPadding ->
            val content = remember {
                movableContentOf {
                    Column {
                        val update by indexViewModel.updateChecker.collectAsState()
                        var dismissUpdate by rememberSaveable {
                            mutableStateOf(true)
                        }
                        val context = LocalContext.current
                        val currentVersion = LocalContext.current.getVersionName()
                        AnimatedVisibility(
                            visible = update is DataState.Success
                                    && update.readSafely()?.name != null
                                    && update.readSafely()?.name != currentVersion
                                    && !dismissUpdate
                        ) {
                            Banner(
                                modifier = Modifier.padding(16.dp),
                                icon = {
                                    Icon(Icons.Outlined.Update, null)
                                },
                                title = {
                                    Text(text = "${stringResource(id = R.string.screen_index_update_title)}: ${update.readSafely()?.name}")
                                },
                                text = {
                                    Text(text = update.readSafely()?.body ?: "", maxLines = 6)
                                },
                                buttons = {
                                    TextButton(onClick = {
                                        dismissUpdate = true
                                    }) {
                                        Text(text = stringResource(id = R.string.screen_index_button_update_neglect))
                                    }
                                    TextButton(onClick = {
                                        context.openUrl("https://github.com/re-ovo/iwara4a/releases/latest")
                                    }) {
                                        Text(stringResource(id = R.string.screen_index_button_update_github))
                                    }
                                }
                            )
                        }
                        ClipboardBanner()
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            count = 4,
                            userScrollEnabled = false
                        ) { page ->
                            when (page) {
                                0 -> {
                                    SubPage(indexViewModel)
                                }
                                1 -> {
                                    RankPage(indexViewModel)
                                }
                                2 -> {
                                    ExplorePage(indexViewModel)
                                }
                            }
                        }
                    }
                }
            }

            if (screenType.widthSizeClass != WindowWidthSizeClass.Compact) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    SideRail(pagerState.currentPage) {
                        coroutineScope.launch { pagerState.scrollToPage(it) }
                    }
                    content()
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxWidth()
                ){
                    Box(modifier = Modifier.weight(1f)) {
                        content()
                    }
                    BottomBar(
                        currentPage = pagerState.currentPage,
                        scrollToPage = {
                            coroutineScope.launch {
                                pagerState.scrollToPage(it)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ClipboardBanner() {
    val navController = LocalNavController.current
    var clipBoard by rememberPrimaryClipboardState()
    var showDialog by remember {
        mutableStateOf(false)
    }
    var link by remember {
        mutableStateOf("")
    }
    LaunchedEffect(clipBoard) {
        clipBoard?.getItemAt(0)?.let {
            if (it.text?.matches(Regex("https://ecchi.iwara.tv/videos/.+")) == true) {
                link = it.text.toString().substringAfter("/videos/")
                showDialog = true
            } else {
                showDialog = false
            }
        } ?: kotlin.run {
            showDialog = false
        }
    }
    AnimatedVisibility(showDialog) {
        Banner(
            modifier = Modifier.padding(16.dp),
            title = {
                Text("检测到剪贴板存在视频链接")
            },
            text = {
                Text("https://ecchi.iwara.tv/videos/$link")
            },
            icon = {
                Icon(Icons.Outlined.Link, null)
            },
            buttons = {
                TextButton(
                    onClick = {
                        clipBoard = null
                    }
                ) {
                    Text("移除")
                }
                TextButton(
                    onClick = {
                        navController.navigate("video/$link")
                    }
                ) {
                    Text("打开")
                }
            }
        )
    }
}

@Composable
private fun TopBar(
    drawerState: DrawerState,
    indexViewModel: IndexViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val self = LocalSelfData.current
    LaunchedEffect(Unit) {
        delay(100)
        val setting = context.sharedPreferencesOf("donation")
        if (
            System.currentTimeMillis() - setting.getLong(
                "lastPopup",
                0L
            ) >= 1.days.inWholeMilliseconds
            && !self.profilePic.contains("default-avatar.jpg")
            && Locale.getDefault().language == Locale.SIMPLIFIED_CHINESE.language
        ) {
            setting.edit {
                putLong("lastPopup", System.currentTimeMillis())
            }
        }
    }
    Md3TopBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = { coroutineScope.launch {drawerState.open()} }) {
                Icon(Icons.Outlined.Menu, null)
            }
        },
        actions = {
            IconButton(onClick = { navController.navigate("search") }) {
                Icon(Icons.Outlined.Search, null)
            }

            if (BuildConfig.DEBUG) {
                IconButton(
                    onClick = {
                        navController.navigate("test")
                    }
                ) {
                    Icon(Icons.Outlined.Work, null)
                }
            }
        }
    )
}

@Composable
private fun SideRail(currentPage: Int, scrollToPage: (Int) -> Unit) {
    NavigationRail {
        NavigationRailItem(
            selected = currentPage == 0,
            onClick = {
                scrollToPage(0)
            },
            icon = {
                Icon(imageVector = Icons.Outlined.Subscriptions, contentDescription = null)
            },
            label = {
                Text(text = stringResource(R.string.screen_index_bottom_sub))
            },
            alwaysShowLabel = false
        )
        NavigationRailItem(
            selected = currentPage == 1,
            onClick = {
                scrollToPage(1)
            },
            icon = {
                Icon(imageVector = Icons.Outlined.Recommend, contentDescription = null)
            },
            label = {
                Text(text = stringResource(R.string.screen_index_bottom_recommend))
            },
            alwaysShowLabel = false
        )
        NavigationRailItem(
            selected = currentPage == 2,
            onClick = {
                scrollToPage(2)
            },
            icon = {
                Icon(imageVector = Icons.Outlined.Sort, contentDescription = null)
            },
            label = {
                Text(text = stringResource(R.string.screen_index_bottom_sort))
            },
            alwaysShowLabel = false
        )

        NavigationRailItem(
            selected = currentPage == 3,
            onClick = {
                scrollToPage(3)
            },
            icon = {
                Icon(imageVector = Icons.Outlined.Explore, contentDescription = null)
            },
            label = {
                Text(text = stringResource(R.string.screen_index_bottom_explore))
            },
            alwaysShowLabel = false
        )
    }
}

@Composable
private fun BottomBar(currentPage: Int, scrollToPage: (Int) -> Unit) {
    Md3BottomNavigation {
        NavigationBarItem(
            selected = currentPage == 0,
            onClick = {
                scrollToPage(0)
            },
            icon = {
                Icon(imageVector = Icons.Outlined.Subscriptions, contentDescription = null)
            },
            label = {
                Text(text = stringResource(R.string.screen_index_bottom_sub))
            },
            alwaysShowLabel = false
        )
        NavigationBarItem(
            selected = currentPage == 1,
            onClick = {
                scrollToPage(1)
            },
            icon = {
                Icon(imageVector = Icons.Outlined.Sort, contentDescription = null)
            },
            label = {
                Text(text = stringResource(R.string.screen_index_bottom_sort))
            },
            alwaysShowLabel = false
        )

        NavigationBarItem(
            selected = currentPage == 2,
            onClick = {
                scrollToPage(2)
            },
            icon = {
                Icon(imageVector = Icons.Outlined.Explore, contentDescription = null)
            },
            label = {
                Text(text = stringResource(R.string.screen_index_bottom_explore))
            },
            alwaysShowLabel = false
        )
    }
}