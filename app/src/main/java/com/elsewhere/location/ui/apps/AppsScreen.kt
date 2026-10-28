package com.elsewhere.location.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.location.core.CacheBackupRecord
import com.elsewhere.location.core.Geo
import com.elsewhere.location.data.LocationSdkTag
import com.elsewhere.location.data.TargetAppInfo
import com.elsewhere.location.data.TargetAppRepository
import com.elsewhere.location.ui.AppFilter
import com.elsewhere.location.ui.AppsUiState
import com.elsewhere.location.ui.ElsewhereViewModel
import com.elsewhere.location.ui.components.EIcon
import com.elsewhere.location.ui.components.EIconButton
import com.elsewhere.location.ui.components.Hairline
import com.elsewhere.location.ui.components.NoticeBlock
import com.elsewhere.location.ui.components.PrimaryAction
import com.elsewhere.location.ui.components.ScreenBackdrop
import com.elsewhere.location.ui.components.ScreenHeader
import com.elsewhere.location.ui.components.SecondaryAction
import com.elsewhere.location.ui.components.SectionRule
import com.elsewhere.location.ui.components.SignalDot
import com.elsewhere.location.ui.components.StatusTone
import com.elsewhere.location.ui.components.SurveySwitch
import com.elsewhere.location.ui.components.TinyActionChip
import com.elsewhere.location.ui.components.TonalTag
import com.elsewhere.location.ui.components.TopSheetShape
import com.elsewhere.location.ui.theme.Danger
import com.elsewhere.location.ui.theme.Faint
import com.elsewhere.location.ui.theme.Ink
import com.elsewhere.location.ui.theme.InkSoft
import com.elsewhere.location.ui.theme.Line
import com.elsewhere.location.ui.theme.LineStrong
import com.elsewhere.location.ui.theme.Live
import com.elsewhere.location.ui.theme.MonoLabel
import com.elsewhere.location.ui.theme.MonoTiny
import com.elsewhere.location.ui.theme.Muted
import com.elsewhere.location.ui.theme.Paper
import com.elsewhere.location.ui.theme.PaperHigh
import com.elsewhere.location.ui.theme.PaperLow
import com.elsewhere.location.ui.theme.Signal
import com.elsewhere.location.ui.theme.SignalWash
import com.elsewhere.location.ui.theme.SystemSans
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewModel: ElsewhereViewModel,
    onOpenSettings: () -> Unit,
) {
    val appsState by viewModel.appsUi.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var backupFirst by remember { mutableStateOf(true) }
    var restoreTarget by remember { mutableStateOf<CacheBackupRecord?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibleApps = appsState.visibleApps(settings.backups)
    val latestBackup = appsState.selectedPackage?.let { viewModel.latestBackup(it, settings.backups) }
    val supportedCount = appsState.apps.count { it.supportsLocationCacheMaintenance }

    LaunchedEffect(Unit) { viewModel.ensureAppsLoaded() }

    Box(Modifier.fillMaxSize().background(Paper)) {
        ScreenBackdrop(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.TopCenter),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                eyebrow = "APPS / 04",
                title = "应用与缓存",
                subtitle = "选择目标应用，手动清理或归档定位缓存。",
                onSettings = onOpenSettings,
                statusText = if (shizuku.ready) "SHIZUKU READY" else "SHIZUKU REQUIRED",
                statusTone = if (shizuku.ready) StatusTone.Ready else StatusTone.Warning,
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .background(PaperHigh)
                            .border(1.dp, LineStrong, RoundedCornerShape(2.dp))
                            .padding(horizontal = 11.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(EIcon.Search, contentDescription = null, tint = Signal, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        SearchAppsField(
                            value = appsState.query,
                            onValueChange = viewModel::setAppsQuery,
                            modifier = Modifier.weight(1f),
                        )
                        if (appsState.query.isNotEmpty()) {
                            Box(
                                Modifier
                                    .clickable { viewModel.setAppsQuery("") }
                                    .padding(2.dp),
                            ) {
                                Icon(EIcon.Close, contentDescription = "清除搜索", tint = Faint, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    EIconButton(
                        icon = EIcon.Refresh,
                        contentDescription = "重新扫描",
                        onClick = viewModel::refreshApps,
                        size = 43.dp,
                        border = LineStrong,
                        enabled = !appsState.loading,
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AppFilter.entries.forEach { filter ->
                        TinyActionChip(
                            text = filter.label,
                            selected = appsState.filter == filter,
                            onClick = { viewModel.setAppsFilter(filter) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "INSTALLED · ${appsState.apps.size}  ·  AMAP / BAIDU · $supportedCount",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Muted,
                    )
                    Spacer(Modifier.weight(1f))
                    if (appsState.scanning) {
                        Text(
                            "扫描 ${appsState.scannedCount}/${appsState.scanTotal}",
                            style = MonoTiny.copy(fontSize = 8.sp),
                            color = Signal,
                        )
                    } else {
                        Text("SDK 扫描完成", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
                    }
                }
                if (appsState.scanning) {
                    Spacer(Modifier.height(6.dp))
                    ScanProgress(appsState)
                }
                if (!shizuku.ready) {
                    Spacer(Modifier.height(9.dp))
                    NoticeBlock(
                        text = "Android 不允许 shell 身份静默清理其他应用缓存。清理会通过系统应用信息页手动完成；Shizuku 仅用于外部缓存备份/恢复与读取缓存大小。",
                        title = "Shizuku 尚未就绪",
                        tone = StatusTone.Warning,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                appsState.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Signal, strokeWidth = 1.5.dp)
                    }
                }
                appsState.error != null -> {
                    Column(Modifier.padding(16.dp)) {
                        NoticeBlock(
                            text = appsState.error ?: "读取应用失败。",
                            title = "SCAN ERROR",
                            tone = StatusTone.Danger,
                        )
                        Spacer(Modifier.height(8.dp))
                        SecondaryAction("重试", viewModel::refreshApps, icon = EIcon.Refresh)
                    }
                }
                visibleApps.isEmpty() -> {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (appsState.apps.isEmpty()) "没有读取到可启动应用。" else "没有符合条件的应用。",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "可切换筛选或清空搜索。应用列表只包含带桌面入口的应用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(visibleApps, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                hasBackup = settings.backups.any { it.packageName == app.packageName },
                                onClick = { viewModel.selectApp(app.packageName) },
                            )
                        }
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                NoticeBlock(
                                    text = "缓存清理由系统应用信息页手动完成，保留账号、登录状态和应用设置。Shizuku 的 shell 身份无法静默清理其他应用缓存；他乡只能存档可访问的外部缓存快照，无法读取私有内部缓存。",
                                    title = "清理与恢复范围",
                                    tone = StatusTone.Idle,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val selected = appsState.selectedApp
    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectApp(null) },
            sheetState = sheetState,
            containerColor = PaperHigh,
            shape = TopSheetShape,
            dragHandle = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 3.dp)
                            .background(LineStrong, RoundedCornerShape(1.dp)),
                    )
                }
            },
        ) {
            AppDetailSheet(
                app = selected,
                state = appsState,
                shizukuReady = shizuku.ready,
                latestBackup = latestBackup,
                onDismiss = { viewModel.selectApp(null) },
                onReadCache = viewModel::readSelectedCacheSize,
                onClear = { showClearDialog = true },
                onRestore = { restoreTarget = latestBackup },
                onDismissMessage = viewModel::dismissOperationMessage,
                onOpenSettings = {
                    viewModel.selectApp(null)
                    onOpenSettings()
                },
            )
        }
    }

    if (showClearDialog && selected != null) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = PaperHigh,
            titleContentColor = Ink,
            textContentColor = InkSoft,
            title = { Text("手动清理「${selected.label}」缓存", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "Android 不允许无 Root 的 Shizuku 静默清空其他应用缓存。下一步会打开系统应用信息页，请手动进入“存储和缓存”→“清除缓存”。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "清理前可选择保存目标应用可访问的外部缓存快照。系统私有内部缓存无法被 Shizuku 读取，因此不能完整回滚；快照写回也不保证目标应用能读取。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("先备份外部缓存", style = MaterialTheme.typography.titleSmall, color = Ink)
                            Text(
                                if (shizuku.ready) "通过 Shizuku 保存到本应用的外部文件目录" else "需要 Shizuku，当前不会备份",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                        SurveySwitch(
                            checked = backupFirst,
                            onCheckedChange = { backupFirst = it },
                            enabled = shizuku.ready,
                            contentDescription = "清理前备份外部缓存",
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !appsState.clearing,
                    onClick = {
                        showClearDialog = false
                        viewModel.prepareManualCacheCleanup(backupFirst)
                    },
                ) {
                    Text(if (appsState.clearing) "准备中…" else "打开系统清理页", color = Signal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = Muted)
                }
            },
        )
    }

    if (restoreTarget != null) {
        val record = restoreTarget!!
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            containerColor = PaperHigh,
            titleContentColor = Ink,
            textContentColor = InkSoft,
            title = { Text("写回外部缓存快照？", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "将把「${record.appLabel}」的外部缓存快照写回系统目录。由于写入者仍是 Shizuku 的 shell 身份，目标应用不一定能读取这些文件；内部私有缓存永远无法备份。恢复前会先停止目标应用。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreTarget = null
                    viewModel.restoreCacheBackup(record)
                }) {
                    Text("写回快照", color = Signal)
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) {
                    Text("取消", color = Muted)
                }
            },
        )
    }
}

@Composable
private fun SearchAppsField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Box(modifier) {
        if (value.isEmpty()) {
            Text(
                "搜索应用名或包名",
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
            cursorBrush = SolidColor(Signal),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )
    }
}

@Composable
private fun ScanProgress(state: AppsUiState) {
    val progress = if (state.scanTotal <= 0) 0f else state.scannedCount.toFloat() / state.scanTotal
    Box(Modifier.fillMaxWidth().height(2.dp).background(Line)) {
        Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Signal))
    }
}

@Composable
private fun AppRow(
    app: TargetAppInfo,
    hasBackup: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, 38.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (hasBackup) {
                        Spacer(Modifier.width(5.dp))
                        TonalTag("备份", fill = SignalWash, color = Signal)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    app.packageName,
                    style = MonoTiny.copy(fontSize = 8.sp, letterSpacing = 0.sp),
                    color = Faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    app.sdkScan?.tags?.forEach { tag ->
                        TonalTag(
                            tag.label,
                            fill = when (tag) {
                                LocationSdkTag.AMAP -> SignalWash
                                LocationSdkTag.BAIDU -> PaperLow
                            },
                            color = when (tag) {
                                LocationSdkTag.AMAP -> Signal
                                LocationSdkTag.BAIDU -> InkSoft
                            },
                        )
                    }
                    if (app.sdkScan == null) {
                        Text("检测中…", style = MonoTiny.copy(fontSize = 7.sp), color = Faint)
                    } else if (app.sdkScan?.tags?.isEmpty() == true) {
                        Text("未发现高德 / 百度 SDK", style = MonoTiny.copy(fontSize = 7.sp), color = Faint)
                    }
                    if (app.sdkScan?.reliable == false) {
                        Text("部分 APK 无法读取", style = MonoTiny.copy(fontSize = 7.sp), color = Danger)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (app.cacheBytes != null) {
                    Text(
                        TargetAppRepository.formatCacheSize(app.cacheBytes),
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = InkSoft,
                    )
                }
                Icon(EIcon.ChevronRight, contentDescription = null, tint = Faint, modifier = Modifier.size(13.dp))
            }
        }
        Hairline(Modifier.padding(horizontal = 16.dp), color = Line.copy(alpha = 0.48f))
    }
}

@Composable
private fun AppIcon(app: TargetAppInfo, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .background(PaperLow)
            .border(1.dp, Line, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Text(
                app.label.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun AppDetailSheet(
    app: TargetAppInfo,
    state: AppsUiState,
    shizukuReady: Boolean,
    latestBackup: CacheBackupRecord?,
    onDismiss: () -> Unit,
    onReadCache: () -> Unit,
    onClear: () -> Unit,
    onRestore: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 52.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.headlineSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${app.packageName} · ${app.versionName.ifBlank { "未知版本" }}",
                    style = MonoTiny.copy(fontSize = 8.sp, letterSpacing = 0.sp),
                    color = Muted,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (app.sdkScan?.tags?.isEmpty() == true) {
                TonalTag("未检测到地图 SDK", fill = PaperLow, color = Muted)
            } else {
                app.sdkScan?.tags?.forEach { tag ->
                    TonalTag(
                        tag.label,
                        fill = if (tag == LocationSdkTag.AMAP) SignalWash else PaperLow,
                        color = if (tag == LocationSdkTag.AMAP) Signal else InkSoft,
                    )
                }
            }
            TonalTag(if (app.isSystem) "系统应用" else "用户应用", fill = PaperLow, color = Muted)
        }
        Spacer(Modifier.height(13.dp))
        Hairline()
        Spacer(Modifier.height(13.dp))
        Text("CACHE / 缓存占用", style = MonoLabel, color = Muted)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                when {
                    state.cacheReading -> "读取中…"
                    (state.cacheBytes ?: app.cacheBytes) == null -> "未读取"
                    else -> TargetAppRepository.formatCacheSize(state.cacheBytes ?: app.cacheBytes)
                },
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 32.sp),
                color = Ink,
            )
            Spacer(Modifier.weight(1f))
            if (!state.cacheReading) {
                TinyActionChip("读取大小", onReadCache, icon = EIcon.Refresh)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.3.dp, color = Signal)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            state.cacheError ?: "缓存大小需要用量访问；清除缓存本身不需要。",
            style = MonoTiny.copy(fontSize = 8.sp),
            color = if (state.cacheError != null) Danger else Faint,
        )
        Spacer(Modifier.height(14.dp))
        if (!shizukuReady) {
            NoticeBlock(
                text = "手动清理不依赖 Shizuku。外部缓存备份与恢复、读取缓存大小需要 Shizuku 或用量访问。",
                title = "系统权限说明",
                tone = StatusTone.Warning,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryAction("前往诊断与设置", onOpenSettings, icon = EIcon.Sliders)
            Spacer(Modifier.height(10.dp))
        }
        PrimaryAction(
            text = if (state.clearing) "准备备份…" else "手动清理定位缓存",
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            icon = EIcon.External,
            enabled = !state.clearing && !state.restoring,
            loading = state.clearing,
            danger = false,
        )
        Spacer(Modifier.height(9.dp))
        if (latestBackup != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(PaperLow.copy(alpha = 0.5f))
                    .border(1.dp, Line, RoundedCornerShape(2.dp))
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("最近快照 · 外部缓存", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text(
                        "${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(latestBackup.createdAtEpochMs))} · ${TargetAppRepository.formatCacheSize(latestBackup.sizeBytes)}",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Muted,
                    )
                }
                Spacer(Modifier.width(8.dp))
                SecondaryAction(
                    if (state.restoring) "写回中…" else "写回",
                    onRestore,
                    enabled = !state.restoring && !state.clearing,
                )
            }
        } else {
            Text(
                "还没有该应用的外部缓存备份。清理时可选择先备份。",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (state.operationMessage != null) {
            Spacer(Modifier.height(10.dp))
            NoticeBlock(
                text = state.operationMessage!!,
                title = "CACHE WORKFLOW",
                tone = StatusTone.Idle,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismissMessage) {
                Text("关闭提示", color = Muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        NoticeBlock(
            text = "系统会负责清理该应用全部缓存（包含高德 / 百度等 SDK 的定位缓存），保留账号、设置与内部数据库。Shizuku 可以存档可访问的外部缓存，但无法备份系统私有内部缓存；写回后的归属仍受平台限制。",
            title = "操作范围",
            tone = StatusTone.Idle,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "SDK 检测通过扫描 APK 的 DEX 字符串完成，仅作为定位缓存清理的参考，不代表官方适配。",
            style = MonoTiny.copy(fontSize = 8.sp, color = Faint),
        )
    }
}
