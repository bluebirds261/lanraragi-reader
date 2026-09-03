package com.lanraragi.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lanraragi.reader.data.HistoryEntry
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.AppTopBar
import com.lanraragi.reader.ui.EmptyBox
import com.lanraragi.reader.ui.LoadingImage
import com.lanraragi.reader.ui.Routes
import com.lanraragi.reader.ui.edgeSwipeBack
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

/** 打开档案历史：按天分组，每块右下角显示 24 小时制打开时间。 */
@Composable
fun HistoryScreen(container: AppContainer, navController: NavController) {
    val entries by container.historyRepository.entries.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = {
            AppTopBar(
                title = "历史",
                onBack = { navController.popBackStack() },
                actions = {
                    OutlinedButton(onClick = { container.historyRepository.clear() }) {
                        Text("清空")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyBox("暂无历史记录。\n打开档案后会自动记录。", Modifier.padding(padding))
        } else {
            val grouped = remember(entries) {
                entries.groupBy { dateFormat.format(Date(it.timestamp)) }
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (date, dayEntries) ->
                    item(key = "header-$date") {
                        Text(
                            date,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(dayEntries, key = { it.timestamp }) { entry ->
                        HistoryRow(entry) {
                            navController.navigate(Routes.detail(entry.arcid))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(96.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                LoadingImage(
                    model = ApiClient.thumbnailUrl(entry.arcid),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { entry.arcid },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        timeFormat.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun weekdayName(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}

private fun computeMaxConsecutive(dates: List<String>): Int {
    val sorted = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
    if (sorted.isEmpty()) return 0
    var max = 1
    var cur = 1
    for (i in 1 until sorted.size) {
        if (sorted[i] == sorted[i - 1].plusDays(1)) {
            cur++
            max = maxOf(max, cur)
        } else {
            cur = 1
        }
    }
    return max
}

/** 每日打卡：顶部标题「您冲了吗」，三部分卡片 + 真实日历。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinScreen(container: AppContainer, navController: NavController) {
    val dates by container.checkinRepository.dates.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val todayChecked = today.toString() in dates
    var showReport by remember { mutableStateOf(false) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    val monthCount = remember(dates, currentMonth) {
        dates.count { it.startsWith(currentMonth.toString()) }
    }
    val totalCount = dates.size
    val maxConsecutive = remember(dates) { computeMaxConsecutive(dates) }

    Scaffold(
        modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
        topBar = {
            AppTopBar(
                title = "您冲了吗",
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { showReport = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "历史打卡报表")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // 第一部分：今日打卡卡片
            Card(shape = RoundedCornerShape(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(0.75f)) {
                        Text(
                            "${today.monthValue}/${today.dayOfMonth} 星期${weekdayName(today.dayOfWeek.value)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (todayChecked) "今日已打卡" else "今日尚未打卡",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { scope.launch { container.checkinRepository.checkin() } },
                        enabled = !todayChecked,
                    ) {
                        Text(if (todayChecked) "已打卡" else "打卡")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 第二部分：统计卡片
            Card(shape = RoundedCornerShape(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    CheckinStat("本月打卡", monthCount)
                    CheckinStat("发射次数", totalCount)
                    CheckinStat("最高连续", maxConsecutive)
                }
            }

            Spacer(Modifier.height(20.dp))

            // 第三部分：日历
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("打卡日历", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上月")
                }
                Text(currentMonth.toString(), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下月")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 星期标题：一 到 日
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                    Text(
                        w,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 日期格子（真实日历：每月天数 + 第一天星期）
            val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value // 1=周一 .. 7=周日
            val daysInMonth = currentMonth.lengthOfMonth()
            val cells = buildList {
                repeat(firstDayOfWeek - 1) { add(null) }
                (1..daysInMonth).forEach { add(it) }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val key = "$currentMonth-${day.toString().padStart(2, '0')}"
                                val checked = key in dates
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .border(
                                            if (checked) 0.dp else 1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(8.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        day.toString(),
                                        color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) {
                        Box(Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
    }

    if (showReport) {
        ModalBottomSheet(onDismissRequest = { showReport = false }) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("历史打卡报表", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (dates.isEmpty()) {
                    Text("暂无打卡记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    dates.sortedDescending().forEach { d ->
                        Text(d, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckinStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
