package com.rerere.iwara4a.ui.screen.index

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.rerere.iwara4a.R
import kotlinx.coroutines.launch

@Composable
fun IndexDrawer(
    navController: NavController,
    indexViewModel: IndexViewModel,
    drawerState: DrawerState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    fun isLoading() = indexViewModel.loadingSelf

    var dialog by remember {
        mutableStateOf(false)
    }
    if(dialog){
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = {
                Text(stringResource(id = R.string.screen_index_drawer_logout_title))
            },
            text = {
                Text(stringResource(id = R.string.screen_index_drawer_logout_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = false
                    navController.navigate("login") {
                        popUpTo("index") {
                            inclusive = true
                        }
                    }
                }) {
                    Text(stringResource(R.string.yes_button))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Profile
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 18.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Profile Pic
                Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray)
                    ) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model = indexViewModel.self.profilePic,
                            contentDescription = null
                        )
                    }
                }

                // Profile Info
                Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)) {
                    // UserName
                    Text(
                        text = if (isLoading()) stringResource(id = R.string.loading) else indexViewModel.self.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Navigation List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 下载
            NavigationDrawerItem(
                selected = false,
                onClick = {
                    coroutineScope.launch {
                        drawerState.close()
                        navController.navigate("download")
                    }
                },
                icon = {
                    Icon(Icons.Outlined.Download, null)
                },
                label = {
                    Text(text = stringResource(R.string.screen_index_drawer_item_downloads))
                },
                badge = {}
            )

            // 喜欢
            NavigationDrawerItem(
                selected = false,
                icon = {
                    Icon(Icons.Outlined.Favorite, null)
                },
                label = {
                    Text(text = stringResource(R.string.screen_index_drawer_item_likes))
                },
                onClick = {
                    coroutineScope.launch {
                        drawerState.close()
                        navController.navigate("like")
                    }
                },
                badge = {}
            )

            // 关注
            NavigationDrawerItem(
                selected = false,
                icon = {
                    Icon(Icons.Outlined.SupervisedUserCircle, null)
                },
                label = {
                    Text(stringResource(R.string.screen_follow_title))
                },
                badge = {},
                onClick = {
                    coroutineScope.launch {
                        drawerState.close()
                        navController.navigate("following")
                    }
                }
            )

            // oreno3d搜索
            NavigationDrawerItem(
                selected = false,
                icon = {
                    Icon(Icons.Outlined.Search, null)
                },
                label = {
                    Text(text = "Oreno3d搜索")
                },
                badge = {},
                onClick = {
                    coroutineScope.launch {
                        drawerState.close()
                        navController.navigate("oreno3d")
                    }
                }
            )

            // 设置
            NavigationDrawerItem(
                onClick =  {
                    coroutineScope.launch {
                        drawerState.close()
                        navController.navigate("setting")
                    }
                },
                icon = {
                    Icon(Icons.Outlined.Settings, null)
                },
                label = {
                    Text(text = stringResource(R.string.screen_index_drawer_item_setting))
                },
                badge = {},
                selected = false
            )
        }
    }
}