package com.rerere.iwara4a.ui.screen.search

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rerere.iwara4a.ui.component.BackIcon
import com.rerere.iwara4a.ui.component.ComposeWebview
import com.rerere.iwara4a.ui.component.Md3TopBar

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun Oreno3dScreen() {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarScrollState())
    Scaffold(
        topBar = {
            Md3TopBar(
                navigationIcon = {
                    BackIcon()
                },
                title = {
                    Text("Oreno3d搜索")
                },
                scrollBehavior = scrollBehavior
            )
        }
    ){
        ComposeWebview(
            link = "https://oreno3d.com/",
            session = null,
            onTitleChange = {},
            modifier = Modifier.padding(0.dp,100.dp)
        )
    }
}

