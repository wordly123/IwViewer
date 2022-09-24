package com.rerere.iwara4a.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.google.accompanist.flowlayout.FlowRow
import com.rerere.iwara4a.R

data class MediaQueryParam(
    var sortType: SortType,
    var filters: MutableSet<String>
) {
    companion object {
        @JvmStatic
        val Default = MediaQueryParam(
            sortType = SortType.DATE,
            filters = hashSetOf()
        )
    }
}

enum class SortType(val value: String) {
    DATE("date"),
    VIEWS("views"),
    LIKES("likes")
}

data class MediaFilter(
    val type: String,
    val value: List<String>
) {
    constructor(type: String, vararg values: String) : this(type, values.toList())
}

val MEDIA_FILTERS = listOf(
    MediaFilter("created", "2022", "2022-09", "2022-08", "2021", "2020", "2019", "2018"),
    MediaFilter("field_categories","1","6","7","8","33","34","16104","16105","16190","31263","31264","31265")
)

@Composable
fun Pair<String, String>.filterName() = when (this.first) {
    "type" -> when (this.second) {
        "video" -> "视频"
        "image" -> "图片"
        else -> this.second
    }
    "created" -> this.second
    "field_categories" -> when (this.second) {
        "1" -> "舰娘"
        "6" -> "Vocaloid"
        "7" -> "东方"
        "8" -> "MMD杂志"
        "33" -> "偶像大师"
        "34" -> "原创角色"
        "16104" -> "甜心选择"
        "16105" -> "Source FilmMaker"
        "16190" -> "虚拟主播"
        "31263" -> "碧蓝航线"
        "31264" -> "原神"
        "31265" -> "Hololive"
        else -> "(${this.second})"
    }
    else -> "${this.first}: ${this.second}"
}

@Composable
fun SortType.displayName() = when (this) {
    SortType.DATE -> "最新日期"
    SortType.VIEWS -> "最多播放量"
    SortType.LIKES -> "最多收藏"
}

@Composable
fun QueryParamSelector(
    queryParam: MediaQueryParam,
    onChangeSort: (SortType) -> Unit,
    onChangeFiler: (MutableSet<String>) -> Unit,
    onClose: () -> Unit
) {
    var showDialog by rememberSaveable {
        mutableStateOf(false)
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            text = {
                Column {
                    var expand by remember {
                        mutableStateOf(false)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(modifier = Modifier.weight(0.4f), text = "排序")
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable {
                                    expand = !expand
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = queryParam.sortType.displayName(),
                                modifier = Modifier.weight(1.2f)
                            )
                            Icon(Icons.Outlined.ArrowDropDown, null)
                            DropdownMenu(
                                expanded = expand,
                                onDismissRequest = {
                                    expand = false
                                },
                                offset = DpOffset(
                                    x = 120.dp,
                                    y = 0.dp
                                )
                            ) {
                                SortType.values().forEach {
                                    DropdownMenuItem(
                                        onClick = {
                                            onChangeSort(it)
                                            expand = false
                                        },
                                        text = {
                                            Text(text = it.displayName())
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    Column {
                        MEDIA_FILTERS.fastForEach { filter ->
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                mainAxisSpacing = 4.dp
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
                                        },
                                        label = {
                                            Text(text = (filter.type to value).filterName())
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onClose()
                    }
                ) {
                    Text(stringResource(R.string.confirm_button))
                }
            }
        )
    }

    IconButton(onClick = { showDialog = true }) {
        Icon(Icons.Outlined.FilterAlt, null)
    }
}