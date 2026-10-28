package com.elsewhere.location.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.location.core.Geo
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.shizuku.ShizukuStatus
import com.elsewhere.location.shizuku.ShizukuManager
import com.elsewhere.location.ui.ElsewhereViewModel
import com.elsewhere.location.ui.PermissionUiState
import com.elsewhere.location.ui.components.BrandLockup
import com.elsewhere.location.ui.components.BrandMark
import com.elsewhere.location.ui.components.ContourField
import com.elsewhere.location.ui.components.EIcon
import com.elsewhere.location.ui.components.Hairline
import com.elsewhere.location.ui.components.PrimaryAction
import com.elsewhere.location.ui.components.SignalDot
import com.elsewhere.location.ui.components.Spacing
import com.elsewhere.location.ui.components.StatusTone
import com.elsewhere.location.ui.components.TinyActionChip
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
import kotlin.math.cos
import kotlin.math.sin

private const val STEPS = 3

@Composable
fun OnboardingScreen(viewModel: ElsewhereViewModel) {
    val shizuku by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = step > 0) { step -= 1 }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding(),
    ) {
        OnboardingHeader(
            step = step,
            onSkip = viewModel::completeOnboarding,
        )
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(330)) { it / 4 * direction } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 5 * direction } + fadeOut(tween(160)))
            },
            label = "onboardingPage",
        ) { page ->
            when (page) {
                0 -> OnboardingIntroPage()
                1 -> OnboardingShizukuPage(
                    shizuku = shizuku,
                    permissions = permissions,
                    onOpenShizuku = {
                        viewModel.openUrl(ShizukuManager.SHIZUKU_RELEASES_URL)
                    },
                    onOpenShizukuApp = viewModel::openShizukuApp,
                    onRefresh = viewModel::refreshSystemStatus,
                    onAuthorize = viewModel::autoConfigureWithShizuku,
                    onDeveloperOptions = viewModel::openDeveloperOptions,
                    onSkip = { step = 2 },
                )
                else -> OnboardingPermissionPage(
                    shizuku = shizuku,
                    permissions = permissions,
                    onRequestRuntime = viewModel::requestRuntimePermissions,
                    onRequestOverlay = viewModel::requestOverlayPermission,
                    onMockSettings = {
                        if (shizuku.permissionGranted) {
                            viewModel.grantMockLocationPermission()
                        } else {
                            viewModel.openDeveloperOptions()
                        }
                    },
                )
            }
        }
        OnboardingFooter(
            step = step,
            shizuku = shizuku,
            permissions = permissions,
            onBack = { if (step > 0) step -= 1 },
            onNext = { if (step < STEPS - 1) step += 1 else viewModel.completeOnboarding() },
            onPrimaryAction = {
                when (step) {
                    0 -> step = 1
                    1 -> {
                        when {
                            permissions.mockLocation -> step = 2
                            !shizuku.binderAlive && shizuku.installed -> viewModel.openShizukuApp()
                            !shizuku.binderAlive -> viewModel.openUrl(ShizukuManager.SHIZUKU_RELEASES_URL)
                            else -> viewModel.autoConfigureWithShizuku()
                        }
                    }
                    else -> viewModel.completeOnboarding()
                }
            },
            primaryLabel = onboardingPrimaryLabel(step, shizuku, permissions),
        )
    }
}

private fun onboardingPrimaryLabel(
    step: Int,
    shizuku: ShizukuStatus,
    permissions: PermissionUiState,
): String = when (step) {
    0 -> "开始校准"
    1 -> when {
        permissions.mockLocation -> "下一步 · 权限"
        !shizuku.binderAlive && shizuku.installed -> "打开 Shizuku 启动"
        !shizuku.binderAlive -> "获取 Shizuku"
        else -> "自动配置注入通道"
    }
    else -> "进入他乡"
}

@Composable
private fun OnboardingHeader(step: Int, onSkip: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLockup(compact = true)
        Spacer(Modifier.weight(1f))
        Text(
            "%02d / %02d".format(step + 1, STEPS),
            style = MonoLabel.copy(fontSize = 9.sp),
            color = Muted,
        )
        Spacer(Modifier.width(13.dp))
        Text(
            "跳过",
            style = MonoTiny,
            color = Signal,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(horizontal = 2.dp, vertical = 5.dp),
        )
    }
    Hairline(Modifier.padding(horizontal = 20.dp), color = Line.copy(alpha = 0.7f))
}

@Composable
private fun OnboardingFooter(
    step: Int,
    shizuku: ShizukuStatus,
    permissions: PermissionUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPrimaryAction: () -> Unit,
    primaryLabel: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(STEPS) { index ->
                Box(
                    Modifier
                        .weight(if (index == step) 2f else 1f)
                        .height(2.dp)
                        .background(
                            when {
                                index < step -> Ink
                                index == step -> Signal
                                else -> Line
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                Box(
                    Modifier
                        .size(42.dp)
                        .border(1.dp, LineStrong, RoundedCornerShape(2.dp))
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(EIcon.Back, contentDescription = "上一步", tint = Ink, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(9.dp))
            }
            PrimaryAction(
                text = primaryLabel,
                onClick = onPrimaryAction,
                modifier = Modifier.weight(1f),
                icon = when (step) {
                    0 -> EIcon.Cursor
                    1 -> if (permissions.mockLocation) EIcon.ChevronRight else EIcon.Shield
                    else -> EIcon.Map
                },
            )
            if (step == 1 && !permissions.mockLocation) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(50.dp)
                        .border(1.dp, LineStrong, RoundedCornerShape(2.dp))
                        .clickable(role = Role.Button, onClick = onNext),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(EIcon.ChevronRight, contentDescription = "先跳过", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = when (step) {
                0 -> "所有坐标、路线与备份只保存在本机 · 不采集遥测"
                1 -> "推荐 Shizuku。也可在开发者选项中选择“他乡”作为模拟位置应用。"
                else -> "请只在你有权修改位置的应用与测试场景中使用。"
            },
            style = MonoTiny.copy(fontSize = 8.sp),
            color = Faint,
            maxLines = 2,
        )
    }
}

@Composable
private fun OnboardingIntroPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "ROOT-FREE POSITION INSTRUMENT",
            style = MonoLabel.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
            color = Signal,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "把坐标，\n握在手里。",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 52.sp,
                lineHeight = 54.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = Ink,
        )
        Spacer(Modifier.height(11.dp))
        Text(
            "用一张地图，替身去另一个地方。",
            style = MaterialTheme.typography.bodyLarge,
            color = InkSoft,
        )
        Text(
            "选点、行走、规划路线；\n或者只是让世界以为，你正站在别处。",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))
        ReticleHero()
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            IntroMetric("CHANNEL", "SHIZUKU", Modifier.weight(1f))
            IntroMetric("DATUM", "WGS-84", Modifier.weight(1f))
            IntroMetric("ACCESS", "ROOT-FREE", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CAPABILITIES / 一台位置仪", style = MonoLabel.copy(fontSize = 8.sp), color = Muted)
            Spacer(Modifier.weight(1f))
            Text("无 ROOT · 不绕过风控", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            IntroCapability(
                "01",
                "地图选点",
                "拖图、搜索、真实 GPS 对齐",
                Modifier.weight(1f),
            )
            IntroCapability(
                "02",
                "节奏移动",
                "走路 / 跑步 / 骑行",
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            IntroCapability(
                "03",
                "路线巡航",
                "航点、环线与原地漂移",
                Modifier.weight(1f),
            )
            IntroCapability(
                "04",
                "悬浮摇杆",
                "跨应用控制方向",
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun IntroCapability(
    index: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Hairline(color = Line.copy(alpha = 0.7f))
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(index, style = MonoTiny.copy(fontSize = 8.sp), color = Signal)
            Spacer(Modifier.width(7.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
        }
        Spacer(Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
    }
}

@Composable
private fun ReticleHero() {
    val infinite = rememberInfiniteTransition(label = "onboardingReticle")
    val ringPulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
        label = "ring",
    )
    val dotPulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "dot",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(232.dp)
            .clip(CutCornerShape(topEnd = 26.dp, bottomStart = 26.dp))
            .background(PaperHigh)
            .border(1.dp, Line, CutCornerShape(topEnd = 26.dp, bottomStart = 26.dp)),
    ) {
        ContourField(
            modifier = Modifier.fillMaxSize(),
            color = Line.copy(alpha = 0.43f),
            accent = Signal.copy(alpha = 0.25f),
            focus = Offset(0.53f, 0.48f),
        )
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.53f, size.height * 0.48f)
            val radius = size.minDimension * 0.30f
            drawCircle(
                color = Ink.copy(alpha = 0.68f),
                radius = radius * ringPulse,
                center = center,
                style = Stroke(1.2.dp.toPx()),
            )
            drawCircle(
                color = Ink.copy(alpha = 0.20f),
                radius = radius * 0.65f,
                center = center,
                style = Stroke(0.8.dp.toPx()),
            )
            drawCircle(
                color = Signal.copy(alpha = 0.24f),
                radius = radius * 0.42f,
                center = center,
                style = Stroke(0.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
            )
            val line = 0.9.dp.toPx()
            drawLine(
                color = Ink.copy(alpha = 0.7f),
                start = Offset(center.x, 20.dp.toPx()),
                end = Offset(center.x, center.y - radius - 10.dp.toPx()),
                strokeWidth = line,
            )
            drawLine(
                color = Ink.copy(alpha = 0.7f),
                start = Offset(center.x, center.y + radius + 10.dp.toPx()),
                end = Offset(center.x, size.height - 20.dp.toPx()),
                strokeWidth = line,
            )
            drawLine(
                color = Ink.copy(alpha = 0.7f),
                start = Offset(20.dp.toPx(), center.y),
                end = Offset(center.x - radius - 10.dp.toPx(), center.y),
                strokeWidth = line,
            )
            drawLine(
                color = Ink.copy(alpha = 0.7f),
                start = Offset(center.x + radius + 10.dp.toPx(), center.y),
                end = Offset(size.width - 20.dp.toPx(), center.y),
                strokeWidth = line,
            )
            repeat(32) { index ->
                val angle = Math.toRadians(index * 11.25 - 90.0)
                val major = index % 8 == 0
                val inner = radius * 0.84f
                val outer = radius + if (major) 8.dp.toPx() else 4.dp.toPx()
                drawLine(
                    color = if (major) Ink.copy(alpha = 0.62f) else LineStrong.copy(alpha = 0.65f),
                    start = Offset(
                        center.x + cos(angle).toFloat() * inner,
                        center.y + sin(angle).toFloat() * inner,
                    ),
                    end = Offset(
                        center.x + cos(angle).toFloat() * outer,
                        center.y + sin(angle).toFloat() * outer,
                    ),
                    strokeWidth = if (major) 1.1.dp.toPx() else 0.7.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }
            drawCircle(
                color = Signal.copy(alpha = 0.20f),
                radius = 17.dp.toPx() * dotPulse,
                center = center,
            )
            drawCircle(Ink, radius = 7.5.dp.toPx(), center = center)
            drawCircle(Signal, radius = 4.6.dp.toPx(), center = center)
            drawLine(
                color = PaperHigh,
                start = Offset(center.x - 2.3.dp.toPx(), center.y),
                end = Offset(center.x + 2.3.dp.toPx(), center.y),
                strokeWidth = 0.8.dp.toPx(),
            )
            drawLine(
                color = PaperHigh,
                start = Offset(center.x, center.y - 2.3.dp.toPx()),
                end = Offset(center.x, center.y + 2.3.dp.toPx()),
                strokeWidth = 0.8.dp.toPx(),
            )
        }
        Text(
            "FIX · 01",
            style = MonoTiny.copy(fontSize = 8.sp),
            color = Muted,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp),
        )
        Text(
            "31°13′49.4″N\n121°28′25.3″E",
            style = MonoLabel.copy(fontSize = 9.sp, lineHeight = 15.sp),
            color = InkSoft,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 12.dp),
            textAlign = TextAlign.End,
        )
        Text(
            "WGS-84 / LIVE",
            style = MonoTiny.copy(fontSize = 8.sp, letterSpacing = 0.7.sp),
            color = Signal,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 14.dp, top = 12.dp),
        )
    }
}

@Composable
private fun IntroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MonoTiny.copy(fontSize = 8.sp), color = Muted)
        Text(value, style = MonoLabel.copy(fontSize = 9.sp), color = Ink)
    }
}

@Composable
private fun OnboardingShizukuPage(
    shizuku: ShizukuStatus,
    permissions: PermissionUiState,
    onOpenShizuku: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRefresh: () -> Unit,
    onAuthorize: () -> Unit,
    onDeveloperOptions: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("02 / 系统通道", style = MonoLabel.copy(fontSize = 9.sp), color = Signal)
        Spacer(Modifier.height(9.dp))
        Text(
            "先借一条\n系统通道。",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 42.sp),
            color = Ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Shizuku 以 shell 身份运行，通过无线调试启动。它没有 Root，也不修改系统分区。",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp))
                .background(PaperHigh)
                .border(1.dp, Line, CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalDot(if (shizuku.ready) Live else Signal, 6.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (shizuku.ready) "通道已就绪" else "通道待连接",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (shizuku.ready) "READY" else "ACTION REQUIRED",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = if (shizuku.ready) LiveDeep() else Signal,
                )
            }
            Spacer(Modifier.height(12.dp))
            SetupStatusRow(
                index = "01",
                title = "Shizuku 服务",
                detail = when {
                    shizuku.binderAlive -> "Binder 正常 · API v${shizuku.version ?: "?"}"
                    shizuku.installed -> "已安装，等待启动服务"
                    else -> "尚未安装"
                },
                ready = shizuku.binderAlive,
            )
            SetupStatusRow(
                index = "02",
                title = "他乡授权",
                detail = if (shizuku.permissionGranted) "已允许执行受控 shell 命令" else "尚未授权",
                ready = shizuku.permissionGranted,
            )
            SetupStatusRow(
                index = "03",
                title = "模拟位置 AppOp",
                detail = if (permissions.mockLocation) "系统已允许测试定位提供者" else "未允许，可使用开发者选项",
                ready = permissions.mockLocation,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TinyActionChip(
                    if (shizuku.installed) "打开 Shizuku" else "获取 Shizuku",
                    if (shizuku.installed) onOpenShizukuApp else onOpenShizuku,
                )
                TinyActionChip("授权 / 配置", onAuthorize, selected = true)
                TinyActionChip("开发者选项", onDeveloperOptions)
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (shizuku.binderAlive) "Shizuku 已连接" else "启动服务后回到他乡会自动刷新。",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Muted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "手动刷新",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Signal,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onRefresh),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(PaperLow.copy(alpha = 0.45f))
                .padding(13.dp),
        ) {
            Text("为什么不是 Root？", style = MaterialTheme.typography.titleSmall, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "Root 会修改系统并触发更大范围的风险。他乡只需要 shell 级授权：自动完成模拟位置 AppOp、悬浮窗与外部缓存快照。",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        if (!(shizuku.binderAlive && shizuku.permissionGranted && permissions.mockLocation)) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onSkip)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("先进入下一步，稍后配置", style = MonoTiny, color = Muted)
                Spacer(Modifier.width(5.dp))
                Icon(EIcon.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun LiveDeep() = com.elsewhere.location.ui.theme.LiveDeep

@Composable
private fun SetupStatusRow(index: String, title: String, detail: String, ready: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(index, style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Box(
            Modifier
                .size(20.dp)
                .background(if (ready) Ink else PaperLow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (ready) {
                Icon(EIcon.Check, contentDescription = "已就绪", tint = Live, modifier = Modifier.size(12.dp))
            } else {
                SignalDot(Signal, 4.dp)
            }
        }
    }
}

@Composable
private fun OnboardingPermissionPage(
    shizuku: ShizukuStatus,
    permissions: PermissionUiState,
    onRequestRuntime: () -> Unit,
    onRequestOverlay: () -> Unit,
    onMockSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("03 / 校准", style = MonoLabel.copy(fontSize = 9.sp), color = Signal)
        Spacer(Modifier.height(9.dp))
        Text(
            "让仪器\n看清自己。",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 42.sp),
            color = Ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "这些权限都只服务于定位、前台运行和悬浮摇杆。不会上传位置。",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft,
        )
        Spacer(Modifier.height(15.dp))
        PermissionOnboardingRow(
            index = "A",
            title = "精确位置",
            description = "读取真实 GPS / 维持前台定位服务",
            granted = permissions.fineLocation,
            action = "授予",
            onClick = onRequestRuntime,
        )
        PermissionOnboardingRow(
            index = "B",
            title = "通知",
            description = "显示常驻服务通知，便于随时停止",
            granted = permissions.notifications,
            action = "授予",
            onClick = onRequestRuntime,
        )
        PermissionOnboardingRow(
            index = "C",
            title = "悬浮摇杆",
            description = "在其他应用上方拖动方向与调整速度",
            granted = permissions.overlay,
            action = "授权",
            onClick = onRequestOverlay,
        )
        PermissionOnboardingRow(
            index = "D",
            title = "模拟位置通道",
            description = if (shizuku.binderAlive) "可通过 Shizuku 自动完成" else "开发者选项中手动选择他乡",
            granted = permissions.mockLocation,
            action = "配置",
            onClick = onMockSettings,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp))
                .background(Ink)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalDot(Live, 5.dp)
                Spacer(Modifier.width(7.dp))
                Text("能力说明", style = MonoLabel.copy(fontSize = 9.sp), color = Live)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "定位注入、漂移、运动与路线在本机真实生效；缓存清理由系统页面手动完成。",
                style = MaterialTheme.typography.bodySmall,
                color = Paper.copy(alpha = 0.84f),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "运动传感器与 Wi-Fi / 蓝牙 / 基站为应用内推演与环境参数；Shizuku 无法把传感器流注入第三方 App。界面会明确标注。",
                style = MaterialTheme.typography.bodySmall,
                color = Paper.copy(alpha = 0.54f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "坐标从你按下开始，直到你停止。",
                style = MonoTiny.copy(fontSize = 8.sp),
                color = Muted,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PermissionOnboardingRow(
    index: String,
    title: String,
    description: String,
    granted: Boolean,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(25.dp)
                .border(1.dp, if (granted) Ink else LineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                Icon(EIcon.Check, contentDescription = "已授予", tint = Ink, modifier = Modifier.size(14.dp))
            } else {
                Text(index, style = MonoTiny.copy(fontSize = 8.sp), color = Muted)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Text(
            if (granted) "已就绪" else action,
            style = MonoTiny.copy(fontSize = 8.sp),
            color = if (granted) Green() else Signal,
            modifier = Modifier
                .background(if (granted) PaperLow else SignalWash, RoundedCornerShape(1.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
    Hairline(color = Line.copy(alpha = 0.55f))
}

private fun Green() = com.elsewhere.location.ui.theme.Green
