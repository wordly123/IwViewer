package com.rerere.iwara4a.ui.screen.search

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.room.util.StringUtil
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import com.rerere.iwara4a.R
import com.rerere.iwara4a.ui.component.*
import com.rerere.iwara4a.ui.local.LocalNavController
import com.rerere.iwara4a.util.stringResource
import java.util.stream.Collectors

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun SearchScreen(searchViewModel: SearchViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarScrollState())
    Scaffold(
        topBar = {
            Md3TopBar(
                navigationIcon = {
                    BackIcon()
                },
                title = {
                    Text(stringResource(R.string.search))
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            val pageList = rememberPageListPage()
            Column {
                SearchBar(searchViewModel,
                    queryParam = pageList.queryParam,
                    onChangeSort = {
                        pageList.queryParam = MediaQueryParam(
                            sortType = it,
                            filters = pageList.queryParam.filters
                        )
                    },
                    onChangeFiler = {
                        pageList.queryParam = MediaQueryParam(
                            pageList.queryParam.sortType,
                            it
                        )
                    },
                    onSearch = {
                        searchViewModel.provider.load(pageList.page, pageList.queryParam)
                    }
                )
                PageList(
                    state = pageList,
                    provider = searchViewModel.provider,
                    supportQueryParam = true
                ) { list ->
                    LazyVerticalGrid(columns = GridCells.Fixed(2)) {
                        items(list) {
                            MediaPreviewCard(navController, it)
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N)
@Composable
private fun SearchBar(
    searchViewModel: SearchViewModel,
    queryParam: MediaQueryParam,
    onChangeFiler: (MutableSet<String>) -> Unit,
    onChangeSort: (SortType) -> Unit,
    onSearch: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var enable by rememberSaveable { mutableStateOf(false) }
    var select by rememberSaveable { mutableStateOf(1) }
    var year by rememberSaveable { mutableStateOf("2022") }
    var month by rememberSaveable { mutableStateOf("All") }
    Card(modifier = Modifier.padding(4.dp)) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = searchViewModel.query,
                    onValueChange = { searchViewModel.query = it.replace("\n", "") },
                    maxLines = 1,
                    placeholder = {
                        Text(text = stringResource(id = R.string.screen_search_bar_placeholder))
                    },
                    colors = TextFieldDefaults.textFieldColors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        if (searchViewModel.query.isNotEmpty()) {
                            IconButton(onClick = { searchViewModel.query = "" }) {
                                Icon(Icons.Outlined.Close, null)
                            }
                        }

                    },
                    leadingIcon = {
                        if (enable) {
                            IconButton(
                                onClick = {
                                    enable = !enable
                                }) {
                                Icon(Icons.Outlined.ExpandLess, null)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    enable = !enable
                                }) {
                                Icon(Icons.Outlined.ExpandMore, null)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchViewModel.query.isBlank()) {
                                Toast.makeText(
                                    context,
                                    context.stringResource(id = R.string.screen_search_bar_empty),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val filters = queryParam.filters.toMutableSet()
                                if (queryParam.filters.stream().filter { it.startsWith("created") }.collect(
                                        Collectors.toSet()
                                    ).size == 0){
                                    filters.add("created:$year")
                                    onChangeFiler(filters)
                                }
                                focusManager.clearFocus()
                                onSearch()
                            }
                        }
                    )
                )
            }
            IconButton(onClick = {
                if (searchViewModel.query.isBlank()) {
                    Toast.makeText(
                        context,
                        context.stringResource(id = R.string.screen_search_bar_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val filters = queryParam.filters.toMutableSet()
                    if (queryParam.filters.stream().filter { it.startsWith("created") }.collect(
                            Collectors.toSet()
                        ).size == 0){
                        filters.add("created:$year")
                        onChangeFiler(filters)
                    }
                    focusManager.clearFocus()
                    onSearch()
                }
            }) {
                Icon(Icons.Outlined.Search, null)
            }
        }
    }

    AnimatedVisibility(
        visible = enable,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
            MEDIA_FILTERS.subList(1, 2).fastForEach { filter ->
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp, 0.dp),
                    mainAxisSpacing = 8.dp,
                    crossAxisSpacing = (-10).dp,
                    mainAxisAlignment = FlowMainAxisAlignment.Start,
                    crossAxisAlignment = FlowCrossAxisAlignment.Start
                ) {
                    filter.value.fastForEach { value ->
                        val code = "${filter.type}:$value"
                        FilterChip(
                            selected = queryParam.filters.contains(code),
                            onClick = {
                                val filters = queryParam.filters.toMutableSet()
                                if (!filters.contains(code)) {
                                    filters.add(code)
                                } else {
                                    filters.remove(code)
                                }
                                onChangeFiler(filters)
                                onSearch()
                            },
                            label = {
                                Text(text = (filter.type to value).filterName())
                            }
                        )
                    }
                }
            }
        }
        var expand1 by remember { mutableStateOf(false) }
        var expand2 by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp, 120.dp, 0.dp, 0.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = year,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clickable {
                    expand1 = true
                }
            )
            Text(
                text = " - ",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = month,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clickable {
                    expand2 = true
                }
            )
            DropdownMenu(
                expanded = expand1,
                onDismissRequest = {
                    expand1 = false
                },
                offset = DpOffset(120.dp, 0.dp),
            ) {
                MEDIA_FILTERS_TIME.subList(0, 1).fastForEach { filter ->
                    filter.value.fastForEach { value ->
                        DropdownMenuItem(
                            text = {
                                Text(text = value, modifier = Modifier.padding(25.dp, 0.dp))
                            },
                            onClick = {
                                val filters = queryParam.filters.toMutableSet()
                                year = value
                                expand1 = false
                                filters.removeAll(
                                    filters.stream().filter { it.startsWith(filter.type) }.collect(
                                        Collectors.toSet()
                                    )
                                )
                                filters.add("${filter.type}:$value")
                                onChangeFiler(filters)
                                onSearch()
                            },
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = expand2,
                onDismissRequest = {
                    expand2 = false
                },
                offset = DpOffset(280.dp, 0.dp)
            ) {
                MEDIA_FILTERS_TIME.subList(1, 2).fastForEach { filter ->
                    filter.value.fastForEach { value ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = value,
                                    modifier = Modifier.padding(35.dp, 0.dp, 0.dp, 0.dp)
                                )
                            },
                            onClick = {
                                month = value
                                expand2 = false
                                val filters = queryParam.filters.toMutableSet()
                                if (value == "All") {
                                    filters.removeAll(
                                        filters.stream().filter { it.startsWith(filter.type) }
                                            .collect(
                                                Collectors.toSet()
                                            )
                                    )
                                    filters.add("${filter.type}:" + year)
                                } else {
                                    filters.removeAll(
                                        filters.stream().filter { it.startsWith(filter.type) }
                                            .collect(
                                                Collectors.toSet()
                                            )
                                    )
                                    filters.add("${filter.type}:" + year + "-" + value)
                                }
                                onChangeFiler(filters)
                                onSearch()
                            }
                        )
                    }
                }
            }
        }
        if (queryParam.filters.size != 0
            ||
            queryParam.filters.stream().filter { it.startsWith("created") }.collect(
                Collectors.toSet()
            ).size != 0
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp, 160.dp, 0.dp, 0.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "最新日期",
                    modifier = Modifier.clickable {
                        select = 1
                        onChangeSort(SortType.DATE)
                        onSearch()
                    },
                    fontWeight = if (select == 1) FontWeight.Bold else null,
                    textDecoration = if (select == 1) TextDecoration.Underline else null
                )
                Text(
                    text = "最多播放量",
                    modifier = Modifier.clickable {
                        select = 2
                        onChangeSort(SortType.VIEWS)
                        onSearch()
                    },
                    fontWeight = if (select == 2) FontWeight.Bold else null,
                    textDecoration = if (select == 2) TextDecoration.Underline else null
                )
                Text(
                    text = "最多收藏",
                    modifier = Modifier.clickable {
                        select = 3
                        onChangeSort(SortType.LIKES)
                        onSearch()
                    },
                    fontWeight = if (select == 3) FontWeight.Bold else null,
                    textDecoration = if (select == 3) TextDecoration.Underline else null
                )
            }
        }
    }
}



