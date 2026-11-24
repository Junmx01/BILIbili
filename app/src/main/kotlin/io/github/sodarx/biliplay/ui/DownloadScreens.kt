@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.sodarx.biliplay.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sodarx.biliplay.core.*
import io.github.sodarx.biliplay.data.DownloadEntry

@Composable
internal fun DownloadsScreen(s: AppState, m: BiliPlayViewModel) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) } // 0 = download order, 1 = title, 2 = uploader
    fun open(entry: DownloadEntry, share: Boolean) {
        menu = null
        runCatching {
            io.github.sodarx.biliplay.download.DownloadInterop.open(
                context,
                entry,
                s.profileId,
                share,
            )
        }
            .onFailure { m.message("无法打开文件，请检查文件是否存在或其他应用是否可用") }
    }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(!s.downloadTasks, { m.downloadTasks(false) }, label = { Text("已下载") })
            FilterChip(s.downloadTasks, { m.downloadTasks(true) }, label = { Text("下载任务") })
        }
        val listed =
            s.downloads
                .filter {
                    if (s.downloadTasks) it.status != "complete" else it.status == "complete"
                }
                .distinctBy {
                    if (it.status == "complete") it.track.bvid + ":" + it.track.page else it.id
                }
        if (!s.downloadTasks && listed.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("搜索已下载的歌曲") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, "清除搜索")
                            }
                        }
                    },
                )
                FilterChip(
                    selected = sort != 0,
                    onClick = { sort = (sort + 1) % 3 },
                    label = { Text(listOf("默认排序", "按标题", "按 UP 主")[sort]) },
                )
            }
        }
        val rows =
            if (s.downloadTasks) listed
            else
                listed
                    .filter {
                        query.isBlank() ||
                            it.track.title.contains(query.trim(), ignoreCase = true) ||
                            it.track.uploader.contains(query.trim(), ignoreCase = true)
                    }
                    .let { list ->
                        when (sort) {
                            1 -> list.sortedBy { it.track.title.lowercase() }
                            2 -> list.sortedBy { it.track.uploader.lowercase() }
                            else -> list
                        }
                    }
        if (!s.downloadTasks && rows.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        val all = rows.map { it.track }
                        m.playFrom(all.first(), all)
                    }
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("播放全部")
                }
                OutlinedButton(
                    onClick = {
                        val all = rows.map { it.track }.shuffled()
                        m.playFrom(all.first(), all)
                    }
                ) {
                    Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("随机播放")
                }
            }
        }
        LazyColumn {
            items(rows, key = { it.id }) { entry ->
                val selecting = s.selectedDownloads.isNotEmpty()
                Column {
                    ListItem(
                        onClick = {
                            if (selecting) m.selectDownload(entry.id)
                            else if (entry.status == "complete")
                                m.playFrom(entry.track, rows.map { it.track })
                            else m.retryDownload(entry)
                        },
                        onLongClick = { m.selectDownload(entry.id) },
                        onLongClickLabel = "选择下载",
                        content = {
                            Text(
                                songTitle(entry.track.title),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    if (entry.status == "complete") entry.track.uploader
                                    else downloadStatus(entry)
                                )
                                if (entry.error.isNotBlank())
                                    Text(entry.error, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        leadingContent = {
                            if (selecting)
                                Checkbox(
                                    entry.id in s.selectedDownloads,
                                    { m.selectDownload(entry.id) },
                                )
                            else Artwork(entry.track, 48)
                        },
                        trailingContent = {
                            if (entry.status in listOf("queued", "downloading", "encoding"))
                                IconButton(onClick = { m.cancelDownload(entry) }) {
                                    Icon(Icons.Default.Close, "取消下载")
                                }
                            else if (entry.status in listOf("failed", "cancelled"))
                                IconButton(onClick = { m.retryDownload(entry) }) {
                                    Icon(Icons.Default.Refresh, "重试下载")
                                }
                            else
                                Box {
                                    IconButton(onClick = { menu = entry.id }) {
                                        Icon(Icons.Default.MoreVert, "下载操作")
                                    }
                                    DropdownMenu(
                                        expanded = menu == entry.id,
                                        onDismissRequest = { menu = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("用其他应用打开") },
                                            onClick = { open(entry, false) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("分享文件") },
                                            onClick = { open(entry, true) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除") },
                                            onClick = {
                                                menu = null
                                                m.selectDownload(entry.id)
                                                m.dialog("deleteDownloads")
                                            },
                                        )
                                    }
                                }
                        },
                    )
                    val progress by
                        animateFloatAsState(
                            entry.progress / 100f,
                            MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "download progress",
                        )
                    androidx.compose.animation.AnimatedVisibility(
                        entry.status in listOf("downloading", "encoding"),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            if (rows.isEmpty()) item { EmptyContent(if (s.downloadTasks) "暂无下载任务" else if (query.isNotBlank()) "没有匹配的歌曲" else "暂无下载") }
        }
    }
}

private fun downloadStatus(e: DownloadEntry) =
    when (e.status) {
        "queued" -> "等待下载"
        "downloading" -> "下载中 ${e.progress}%"
        "encoding" -> "转码中 ${e.progress}%"
        "cancelled" -> "已取消"
        "failed" -> "下载失败"
        else -> "已下载"
    }
