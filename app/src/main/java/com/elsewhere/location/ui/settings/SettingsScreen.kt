package com.elsewhere.location.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.location.BuildConfig
import com.elsewhere.location.core.CapabilityItem
import com.elsewhere.location.core.CapabilityState
import com.elsewhere.location.core.MapLayer
import com.elsewhere.location.shizuku.ShizukuManager
import com.elsewhere.location.ui.ElsewhereViewModel
import com.elsewhere.location.ui.components.EIcon
import com.elsewhere.location.ui.components.Hairline
import com.elsewhere.location.ui.components.NoticeBlock
import com.elsewhere.location.ui.components.PrimaryAction
import com.elsewhere.location.ui.components.ScreenBackdrop
import com.elsewhere.location.ui.components.ScreenHeader
import com.elsewhere.location.ui.components.SecondaryAction
import com.elsewhere.location.ui.components.SectionRule
import com.elsewhere.location.ui.components.SettingRow
import com.elsewhere.location.ui.components.SignalDot
import com.elsewhere.location.ui.components.StatusTone
import com.elsewhere.location.ui.components.SurveySegmentedControl
import com.elsewhere.location.ui.components.SurveySwitch
import com.elsewhere.location.ui.components.SurveyTextField
import com.elsewhere.location.ui.components.TinyActionChip
import com.elsewhere.location.ui.theme.Amber
import com.elsewhere.location.ui.theme.Danger
import com.elsewhere.location.ui.theme.Faint
import com.elsewhere.location.ui.theme.Green
import com.elsewhere.location.ui.theme.Ink
import com.elsewhere.location.ui.theme.InkSoft
import com.elsewhere.location.ui.theme.JetBrainsMono
import com.elsewhere.location.ui.theme.Line
import com.elsewhere.location.ui.theme.LineStrong
import com.elsewhere.location.ui.theme.Live
import com.elsewhere.location.ui.theme.MonoLabel
import com.elsewhere.location.ui.theme.MonoTiny
import com.elsewhere.location.ui.theme.Muted
import com.elsewhere.location.ui.theme.Paper
import com.elsewhere.location.ui.theme.PaperDeep
import com.elsewhere.location.ui.theme.PaperHigh
import com.elsewhere.location.ui.theme.PaperLow
import com.elsewhere.location.ui.theme.Signal
import com.elsewhere.location.ui.theme.SignalWash
import com.elsewhere.location.ui.theme.SystemSans

@Composable
fun SettingsScreen(
    viewModel: ElsewhereViewModel,
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val update by viewModel.updateUi.collectAsStateWithLifecycle()
    var amapKey by remember(settings.amapWebKey) { mutableStateOf(settings.amapWebKey) }
    var repoDraft by remember(settings.updateRepo) { mutableStateOf(settings.updateRepo) }
    var expandedDiagnostic by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshDiagnostics() }
    LaunchedEffect(
        shizuku.permissionGranted,
        shizuku.binderAlive,
        permissions.mockLocation,
        permissions.fineLocation,
        permissions.overlay,
    ) {
        if (diagnostics.report != null) viewModel.refreshDiagnostics()
    }

    Box(Modifier.fillMaxSize().background(Paper).statusBarsPadding()) {
        ScreenBackdrop(
            Modifier
                .fillMaxWidth()
                .height(270.dp)
                .align(Alignment.TopCenter),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
        ) {
            ScreenHeader(
                eyebrow = "SYSTEM / SETTINGS",
                title = "系统与诊断",
                subtitle = "先看通道是否通畅，再决定世界往哪里移动。",
                onBack = onBack,
                statusText = if (shizuku.ready) "READY" else "LIMITED",
                statusTone = if (shizuku.ready) StatusTone.Ready else StatusTone.Warning,
            )

            Spacer(Modifier.height(14.dp))
            SectionRule(
                index = "00",
                title = "使用指导",
                english = "field manual",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    TinyActionChip("打开", onOpenGuide, icon = EIcon.Info)
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp))
                        .background(Ink)
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FIELD MANUAL", style = MonoLabel.copy(fontSize = 9.sp), color = Live)
                        Spacer(Modifier.weight(1f))
                        Text("内置", style = MonoTiny.copy(fontSize = 7.sp), color = Paper.copy(alpha = 0.35f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "从 Shizuku 通道、真实 GPS、悬浮摇杆，到路线录制、Frida JDWP 一键包和 LSPatch / Xposed 模块。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.copy(alpha = 0.62f),
                    )
                    Spacer(Modifier.height(9.dp))
                    TinyActionChip(
                        "阅读完整指导",
                        onOpenGuide,
                        selected = true,
                        dark = true,
                        icon = EIcon.Info,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "01",
                title = "注入通道",
                english = "shizuku channel",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    TinyActionChip("刷新", viewModel::refreshSystemStatus, icon = EIcon.Refresh)
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(topEnd = 22.dp, bottomStart = 22.dp))
                        .background(Ink)
                        .padding(15.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalDot(if (shizuku.ready) Live else Signal, 6.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                shizuku.ready -> "Shizuku 通道已就绪"
                                shizuku.binderAlive -> "Shizuku 服务已连接，等待他乡授权"
                                shizuku.installed -> "Shizuku 已安装，服务未运行"
                                else -> "尚未安装 Shizuku"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = PaperHigh,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (shizuku.ready) "ROOT-FREE" else "ACTION REQUIRED",
                            style = MonoTiny.copy(fontSize = 8.sp),
                            color = if (shizuku.ready) Live else Paper.copy(alpha = 0.45f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "通过无线调试运行的 shell 通道，完成模拟位置 AppOp、悬浮窗与外部缓存快照。不会修改系统分区。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.copy(alpha = 0.58f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChannelMetric("SERVICE", if (shizuku.binderAlive) "ONLINE" else "OFFLINE")
                        ChannelMetric("UID", shizuku.uid?.toString() ?: "—")
                        ChannelMetric("MOCK APPOP", if (permissions.mockLocation) "ALLOWED" else "DENIED")
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (!shizuku.ready) {
                            TinyActionChip(
                                "自动配置权限",
                                viewModel::autoConfigureWithShizuku,
                                selected = true,
                                icon = EIcon.Shield,
                                dark = true,
                            )
                        } else if (!permissions.mockLocation) {
                            TinyActionChip(
                                "配置模拟位置",
                                viewModel::grantMockLocationPermission,
                                icon = EIcon.Cursor,
                                dark = true,
                            )
                        }
                        TinyActionChip(
                            "打开 Shizuku 项目",
                            { viewModel.openUrl(ShizukuManager.SHIZUKU_RELEASES_URL) },
                            icon = EIcon.External,
                            dark = true,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "也可以用开发者选项 → 选择模拟位置信息应用，手动选择“他乡”。",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Paper.copy(alpha = 0.34f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryAction(
                        "开发者选项",
                        viewModel::openDeveloperOptions,
                        modifier = Modifier.weight(1f),
                        icon = EIcon.Sliders,
                    )
                    SecondaryAction(
                        "查看诊断",
                        viewModel::refreshDiagnostics,
                        modifier = Modifier.weight(1f),
                        icon = EIcon.Refresh,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "02",
                title = "系统诊断",
                english = "capability checks",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    if (diagnostics.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.2.dp,
                            color = Signal,
                        )
                    } else {
                        TinyActionChip("重新检测", viewModel::refreshDiagnostics, icon = EIcon.Refresh)
                    }
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (diagnostics.error != null) {
                    NoticeBlock(
                        text = diagnostics.error ?: "诊断失败。",
                        title = "DIAGNOSTICS ERROR",
                        tone = StatusTone.Danger,
                    )
                }
                val report = diagnostics.report
                if (report == null) {
                    if (diagnostics.loading) {
                        Row(
                            Modifier.padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.3.dp,
                                color = Signal,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text("正在检查 Shizuku、AppOp 与系统 Hook 信号…", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                } else {
                    Text(
                        "${report.deviceSummary} · UID ${report.shizukuUid ?: "—"}",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Faint,
                    )
                    Spacer(Modifier.height(7.dp))
                    report.items.forEach { item ->
                        DiagnosticRow(
                            item = item,
                            expanded = expandedDiagnostic == item.id,
                            onToggle = {
                                expandedDiagnostic = if (expandedDiagnostic == item.id) null else item.id
                            },
                            onAction = {
                                handleCapabilityAction(item, viewModel)
                                viewModel.refreshDiagnostics()
                            },
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    HookConsole(report.hookSignals)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "「系统 Hook 状态」仅用于探测 Root / LSPosed 痕迹。他乡不依赖 Hook，也不能绕过目标应用的 Mock 检测或反作弊。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "03",
                title = "地图与搜索",
                english = "map & geocoding",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("地图底图", style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(7.dp))
                SurveySegmentedControl(
                    options = MapLayer.entries.map { it to it.title },
                    selected = settings.mapLayer,
                    onSelect = viewModel::setMapLayer,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "高德底图显示 GCJ-02，内部与注入始终使用 WGS-84，自动纠偏。OSM 底图依赖 OpenStreetMap 网络。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                SurveyTextField(
                    value = amapKey,
                    onValueChange = { amapKey = it },
                    label = "高德 Web 服务 Key（可选）",
                    placeholder = "留空时使用 Photon / OpenStreetMap",
                    keyboardType = KeyboardType.Text,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "用于更完整的中文地址检索与逆地理编码；Key 只保存在本机。",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Faint,
                        modifier = Modifier.weight(1f),
                    )
                    TinyActionChip("保存 Key", {
                        viewModel.setAmapWebKey(amapKey.trim())
                        viewModel.notify(if (amapKey.isBlank()) "已恢复默认地址服务。" else "已保存高德 Web Key。")
                    })
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "04",
                title = "交互与引导",
                english = "experience",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingRow(
                    title = "触觉反馈",
                    subtitle = "开始、停止与关键操作时提供触觉确认",
                    trailing = {
                        SurveySwitch(
                            checked = settings.hapticsEnabled,
                            onCheckedChange = viewModel::setHaptics,
                            contentDescription = "触觉反馈",
                        )
                    },
                )
                Hairline(color = Line.copy(alpha = 0.55f))
                SettingRow(
                    title = "悬浮摇杆偏好",
                    subtitle = if (permissions.overlay) "已获得悬浮窗权限" else "尚未获得“显示在其他应用上层”权限",
                    trailing = {
                        SurveySwitch(
                            checked = settings.overlayEnabled,
                            onCheckedChange = {
                                if (it) viewModel.requestOverlayPermission() else viewModel.setOverlayPreference(false)
                            },
                            contentDescription = "悬浮摇杆偏好",
                        )
                    },
                )
                Hairline(color = Line.copy(alpha = 0.55f))
                SettingRow(
                    title = "重新观看首次引导",
                    subtitle = "重新检查 Shizuku、权限与能力边界",
                    onClick = viewModel::reopenOnboarding,
                    trailing = {
                        Icon(EIcon.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                    },
                )
                Hairline(color = Line.copy(alpha = 0.55f))
                SettingRow(
                    title = "位置服务设置",
                    subtitle = "打开系统定位总开关",
                    onClick = viewModel::openLocationSettingsPage,
                    trailing = {
                        Icon(EIcon.External, contentDescription = null, tint = Muted, modifier = Modifier.size(15.dp))
                    },
                )
                Hairline(color = Line.copy(alpha = 0.55f))
                SettingRow(
                    title = "电池优化设置",
                    subtitle = "长时间路线巡航建议加入白名单",
                    onClick = viewModel::requestBatteryOptimizationSettings,
                    trailing = {
                        Icon(EIcon.External, contentDescription = null, tint = Muted, modifier = Modifier.size(15.dp))
                    },
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "05",
                title = "GitHub 更新",
                english = "releases",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("当前版本", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Spacer(Modifier.weight(1f))
                    Text(BuildConfig.VERSION_NAME, style = MonoTiny, color = Ink)
                }
                Spacer(Modifier.height(9.dp))
                SurveyTextField(
                    value = repoDraft,
                    onValueChange = { repoDraft = it },
                    label = "更新仓库 / OWNER/REPO",
                    placeholder = BuildConfig.UPDATE_REPO.ifBlank { "owner/repo" },
                    keyboardType = KeyboardType.Text,
                )
                Spacer(Modifier.height(9.dp))
                PrimaryAction(
                    text = if (update.checking) "正在检查…" else "检查 GitHub Release",
                    onClick = {
                        viewModel.setUpdateRepo(repoDraft)
                        viewModel.checkForUpdates(repoDraft)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = EIcon.Refresh,
                    loading = update.checking,
                )
                if (update.error != null) {
                    Spacer(Modifier.height(8.dp))
                    NoticeBlock(
                        text = update.error ?: "",
                        title = "UPDATE CHECK",
                        tone = StatusTone.Warning,
                    )
                }
                val info = update.info
                if (info != null) {
                    Spacer(Modifier.height(9.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp))
                            .background(if (info.isNewer) SignalWash else PaperLow.copy(alpha = 0.6f))
                            .border(1.dp, if (info.isNewer) Signal.copy(alpha = 0.35f) else Line, CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp))
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (info.isNewer) "发现新版本" else "已是最新",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (info.isNewer) Signal else Ink,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(info.versionName, style = MonoLabel, color = if (info.isNewer) Signal else Muted)
                        }
                        if (info.releaseNotes.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                info.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (info.isNewer) {
                            TinyActionChip(
                                if (info.downloadUrl != null) "打开 APK 下载" else "打开发布页",
                                {
                                    viewModel.openUrl(info.downloadUrl ?: info.releaseUrl)
                                },
                                selected = true,
                                icon = EIcon.External,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "仅通过 GitHub API 检查公开 Release，不自动下载或安装。发布仓库可在 gradle.properties 中配置。",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Faint,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "06",
                title = "关于他乡",
                english = "about",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.elsewhere.location.ui.components.BrandMark(size = 34.dp)
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text("他乡 · 虚拟定位仪", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text("ELSEWHERE · ROOT-FREE POSITION INSTRUMENT", style = MonoTiny.copy(fontSize = 7.sp), color = Muted)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("v${BuildConfig.VERSION_NAME.removeSuffix("-dev")}", style = MonoLabel, color = Faint)
                }
                Spacer(Modifier.height(12.dp))
                NoticeBlock(
                    text = "真实生效：系统 mock Location、运动/路线、漂移、悬浮摇杆。缓存清理由系统页面手动完成，外部缓存可存档快照。应用内模拟：运动传感器、Wi-Fi / 蓝牙 / 基站 extras。无 Root 无法向第三方注入传感器流或改变系统无线扫描结果。",
                    title = "能力边界",
                    tone = StatusTone.Idle,
                )
                Spacer(Modifier.height(9.dp))
                NoticeBlock(
                    text = "不采集遥测，不上传坐标与路线。网络仅用于地图瓦片、地址搜索与 GitHub 更新检查。所有位置、路线与备份只保存在本机。",
                    title = "隐私",
                    tone = StatusTone.Idle,
                )
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                Text(
                    "FONTS · Space Grotesk (OFL) / JetBrains Mono (OFL)\nMAP · osmdroid (Apache 2.0) / 高德栅格瓦片 / OpenStreetMap\nGEO · Photon / AMap Web Service",
                    style = MonoTiny.copy(fontSize = 8.sp, lineHeight = 15.sp),
                    color = Faint,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "仅用于你有权修改位置的应用和测试场景。请遵守目标应用的服务条款与当地法律。",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Faint,
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun ChannelMetric(label: String, value: String) {
    Column {
        Text(label, style = MonoTiny.copy(fontSize = 7.sp, letterSpacing = 0.5.sp), color = Paper.copy(alpha = 0.30f))
        Text(value, style = MonoTiny.copy(fontSize = 8.sp), color = Paper.copy(alpha = 0.74f))
    }
}

@Composable
private fun DiagnosticRow(
    item: CapabilityItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: () -> Unit,
) {
    val tone = when (item.state) {
        CapabilityState.READY -> Green
        CapabilityState.LIMITED -> Amber
        CapabilityState.BLOCKED -> Danger
        CapabilityState.UNKNOWN -> Faint
    }
    val stateLabel = when (item.state) {
        CapabilityState.READY -> "READY"
        CapabilityState.LIMITED -> "LIMITED"
        CapabilityState.BLOCKED -> "BLOCKED"
        CapabilityState.UNKNOWN -> "UNKNOWN"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(tone, RoundedCornerShape(50)))
            Spacer(Modifier.width(9.dp))
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Spacer(Modifier.weight(1f))
            Text(stateLabel, style = MonoTiny.copy(fontSize = 7.sp), color = tone)
            Spacer(Modifier.width(8.dp))
            Icon(
                if (expanded) EIcon.ChevronDown else EIcon.ChevronRight,
                contentDescription = null,
                tint = Faint,
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            if (item.actionLabel != null) {
                Spacer(Modifier.width(8.dp))
                TinyActionChip(item.actionLabel, onAction)
            }
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                modifier = Modifier.padding(top = 6.dp, start = 15.dp),
            )
        }
    }
    Hairline(color = Line.copy(alpha = 0.5f))
}

@Composable
private fun HookConsole(signals: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp))
            .background(Ink)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignalDot(Live, 5.dp)
            Spacer(Modifier.width(6.dp))
            Text("HOOK / SIGNAL LOG", style = MonoLabel.copy(fontSize = 8.sp), color = Live)
            Spacer(Modifier.weight(1f))
            Text("READ ONLY", style = MonoTiny.copy(fontSize = 7.sp), color = Paper.copy(alpha = 0.30f))
        }
        Spacer(Modifier.height(7.dp))
        signals.forEach { signal ->
            Text(
                "> $signal",
                style = MonoTiny.copy(fontSize = 8.sp, letterSpacing = 0.sp, fontFamily = JetBrainsMono),
                color = Paper.copy(alpha = 0.60f),
            )
        }
    }
}

private fun handleCapabilityAction(item: CapabilityItem, viewModel: ElsewhereViewModel) {
    when (item.id) {
        "shizuku_service" -> viewModel.openUrl(ShizukuManager.SHIZUKU_RELEASES_URL)
        "shizuku_permission" -> viewModel.autoConfigureWithShizuku()
        "mock_appop" -> viewModel.grantMockLocationPermission()
        "fine_location" -> viewModel.requestRuntimePermissions()
        "overlay" -> viewModel.requestOverlayPermission()
        "notifications" -> viewModel.requestNotificationSettings()
        "battery" -> viewModel.requestBatteryOptimizationSettings()
        "system_location" -> viewModel.openLocationSettingsPage()
        else -> Unit
    }
}
