package com.elsewhere.location.ui.environment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsewhere.location.core.BluetoothProfile
import com.elsewhere.location.core.CellProfile
import com.elsewhere.location.core.DriftConfig
import com.elsewhere.location.core.DriftTempo
import com.elsewhere.location.core.EnvironmentProfile
import com.elsewhere.location.core.WifiProfile
import com.elsewhere.location.data.FridaInjectionMode
import com.elsewhere.location.ui.ElsewhereViewModel
import com.elsewhere.location.ui.FridaUiState
import com.elsewhere.location.ui.components.DriftPlot
import com.elsewhere.location.ui.components.EIcon
import com.elsewhere.location.ui.components.Hairline
import com.elsewhere.location.ui.components.NoticeBlock
import com.elsewhere.location.ui.components.ScreenBackdrop
import com.elsewhere.location.ui.components.ScreenHeader
import com.elsewhere.location.ui.components.SectionRule
import com.elsewhere.location.ui.components.SignalDot
import com.elsewhere.location.ui.components.StatusTone
import com.elsewhere.location.ui.components.SurveySegmentedControl
import com.elsewhere.location.ui.components.SurveySlider
import com.elsewhere.location.ui.components.SurveySwitch
import com.elsewhere.location.ui.components.SurveyTextField
import com.elsewhere.location.ui.components.TinyActionChip
import com.elsewhere.location.ui.theme.Amber
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
import kotlinx.coroutines.delay

@Composable
fun EnvironmentScreen(
    viewModel: ElsewhereViewModel,
    onOpenSettings: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val frida by viewModel.fridaUi.collectAsStateWithLifecycle()
    val environment = settings.environment
    val drift = settings.drift
    val context = LocalContext.current
    var fridaTargetDraft by remember(frida.targetPackage) {
        mutableStateOf(frida.targetPackage)
    }
    val copyText: (String, String) -> Unit = { label, text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
    }
    val driftPhaseTransition = rememberInfiniteTransition(label = "drift")
    val driftPhase by driftPhaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "driftPhase",
    )
    val activeSources = listOf(
        environment.wifi.enabled,
        environment.bluetooth.enabled,
        environment.cell.enabled,
    ).count { it }

    Box(Modifier.fillMaxSize().background(Paper).statusBarsPadding()) {
        ScreenBackdrop(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.TopCenter),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp),
        ) {
            ScreenHeader(
                eyebrow = "ENVIRONMENT / 05",
                title = "环境参数",
                subtitle = "空气、漂移与背景噪声。只改变他乡输出的那一层世界。",
                onSettings = onOpenSettings,
                statusText = if (session.running) "LIVE SESSION" else "NEXT SESSION",
                statusTone = if (session.running) StatusTone.Ready else StatusTone.Idle,
            )
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PRESETS / 场景预设", style = MonoLabel, color = Muted)
                    Spacer(Modifier.weight(1f))
                    Text("$activeSources / 3 源启用", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    PresetChip("空白", "CLEAR", environment.preset == "clear") {
                        viewModel.applyEnvironmentPreset("clear")
                    }
                    PresetChip("咖啡馆", "CAFE", environment.preset == "cafe") {
                        viewModel.applyEnvironmentPreset("cafe")
                    }
                    PresetChip("地铁", "METRO", environment.preset == "metro") {
                        viewModel.applyEnvironmentPreset("metro")
                    }
                    PresetChip("办公室", "OFFICE", environment.preset == "office") {
                        viewModel.applyEnvironmentPreset("office")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionRule(
                index = "01",
                title = "定位漂移",
                english = "position drift",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    Text(
                        if (drift.enabled) "%.1f m".format(drift.amplitudeMeters) else "OFF",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = if (drift.enabled) Signal else Faint,
                    )
                },
            )

            Column(
                Modifier.padding(horizontal = 16.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(topEnd = 20.dp, bottomStart = 20.dp))
                        .background(PaperHigh)
                        .border(1.dp, Line, CutCornerShape(topEnd = 20.dp, bottomStart = 20.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DriftPlot(
                        amplitudeMeters = if (drift.enabled) drift.amplitudeMeters else 0.01f,
                        phase = driftPhase,
                        modifier = Modifier.width(128.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("让 GPS 不完全可信", style = MaterialTheme.typography.titleMedium, color = Ink)
                            Spacer(Modifier.weight(1f))
                            SurveySwitch(
                                checked = drift.enabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setDrift(drift.copy(enabled = enabled))
                                },
                                contentDescription = "启用定位漂移",
                            )
                        }
                        Text(
                            "在注入坐标周围加入平滑偏移，模拟城市中的定位游移。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                        Spacer(Modifier.height(9.dp))
                        Text(
                            if (drift.enabled) "%.1f m · %s".format(drift.amplitudeMeters, drift.tempo.title) else "未启用",
                            style = MonoLabel.copy(fontSize = 8.sp, letterSpacing = 0.3.sp),
                            color = if (drift.enabled) Signal else Faint,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                AnimatedVisibilityLike(drift.enabled) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(PaperLow.copy(alpha = 0.46f))
                            .padding(13.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("振幅", style = MaterialTheme.typography.titleSmall, color = Ink)
                            Spacer(Modifier.weight(1f))
                            Text("%.1f m".format(drift.amplitudeMeters), style = MonoTiny, color = Ink)
                        }
                        SurveySlider(
                            value = drift.amplitudeMeters,
                            onValueChange = {
                                viewModel.setDrift(drift.copy(amplitudeMeters = it))
                            },
                            valueRange = 0.2f..15f,
                            steps = 29,
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text("0.2 m", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
                            Spacer(Modifier.weight(1f))
                            Text("15 m", style = MonoTiny.copy(fontSize = 8.sp), color = Faint)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("节奏", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        SurveySegmentedControl(
                            options = DriftTempo.entries.map { it to it.title },
                            selected = drift.tempo,
                            onSelect = { viewModel.setDrift(drift.copy(tempo = it)) },
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            drift.tempo.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalDot(Signal, 5.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "漂移直接作用于注入坐标；参数在下一次开始模拟时生效。",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Muted,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "02",
                title = "无线环境",
                english = "radio environment",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    Text("APP-SIDE EXTRAS", style = MonoTiny.copy(fontSize = 8.sp), color = Signal)
                },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                NoticeBlock(
                    text = "这些字段会随 mock Location 的 extras 一起输出，但不会改变系统 Wi-Fi / 蓝牙 / 基站的扫描结果。第三方 App 仍可能读取真实无线电信息，界面不把它伪装成系统级注入。",
                    title = "环境参数 / 非系统扫描修改",
                    tone = StatusTone.Warning,
                )
                Spacer(Modifier.height(10.dp))
                EnvironmentSourceBlock(
                    icon = EIcon.Wifi,
                    title = "Wi-Fi",
                    code = "WIFI ENVIRONMENT",
                    enabled = environment.wifi.enabled,
                    summary = if (environment.wifi.enabled) {
                        "${environment.wifi.ssid} · ${environment.wifi.rssi} dBm"
                    } else "未写入 Wi-Fi 信息",
                    onToggle = { enabled ->
                        viewModel.setEnvironment(
                            environment.copy(wifi = environment.wifi.copy(enabled = enabled)),
                        )
                    },
                ) {
                    Column {
                        EnvTextField(
                            label = "SSID",
                            external = environment.wifi.ssid,
                            keyboardType = KeyboardType.Text,
                            onCommit = { value ->
                                viewModel.setEnvironment(
                                    environment.copy(wifi = environment.wifi.copy(ssid = value)),
                                )
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        EnvTextField(
                            label = "BSSID",
                            external = environment.wifi.bssid,
                            keyboardType = KeyboardType.Text,
                            onCommit = { value ->
                                viewModel.setEnvironment(
                                    environment.copy(wifi = environment.wifi.copy(bssid = value.uppercase())),
                                )
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EnvTextField(
                                label = "RSSI (DBM)",
                                external = environment.wifi.rssi.toString(),
                                keyboardType = KeyboardType.Number,
                                onCommit = { value ->
                                    value.toIntOrNull()?.let { rssi ->
                                        viewModel.setEnvironment(
                                            environment.copy(
                                                wifi = environment.wifi.copy(
                                                    rssi = rssi.coerceIn(-120, 0),
                                                ),
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            EnvTextField(
                                label = "CHANNEL",
                                external = environment.wifi.channel.toString(),
                                keyboardType = KeyboardType.Number,
                                onCommit = { value ->
                                    value.toIntOrNull()?.let { channel ->
                                        viewModel.setEnvironment(
                                            environment.copy(
                                                wifi = environment.wifi.copy(
                                                    channel = channel.coerceIn(1, 196),
                                                ),
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                EnvironmentSourceBlock(
                    icon = EIcon.Bluetooth,
                    title = "蓝牙",
                    code = "BLUETOOTH ENVIRONMENT",
                    enabled = environment.bluetooth.enabled,
                    summary = if (environment.bluetooth.enabled) {
                        "${environment.bluetooth.name} · ${environment.bluetooth.rssi} dBm"
                    } else "未写入蓝牙信息",
                    onToggle = { enabled ->
                        viewModel.setEnvironment(
                            environment.copy(bluetooth = environment.bluetooth.copy(enabled = enabled)),
                        )
                    },
                ) {
                    EnvTextField(
                        label = "DEVICE NAME",
                        external = environment.bluetooth.name,
                        onCommit = { value ->
                            viewModel.setEnvironment(
                                environment.copy(bluetooth = environment.bluetooth.copy(name = value)),
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    EnvTextField(
                        label = "MAC ADDRESS",
                        external = environment.bluetooth.address,
                        onCommit = { value ->
                            viewModel.setEnvironment(
                                environment.copy(bluetooth = environment.bluetooth.copy(address = value.uppercase())),
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    EnvTextField(
                        label = "RSSI (DBM)",
                        external = environment.bluetooth.rssi.toString(),
                        keyboardType = KeyboardType.Number,
                        onCommit = { value ->
                            value.toIntOrNull()?.let { rssi ->
                                viewModel.setEnvironment(
                                    environment.copy(
                                        bluetooth = environment.bluetooth.copy(
                                            rssi = rssi.coerceIn(-120, 0),
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                EnvironmentSourceBlock(
                    icon = EIcon.Cell,
                    title = "基站",
                    code = "CELL ENVIRONMENT",
                    enabled = environment.cell.enabled,
                    summary = if (environment.cell.enabled) {
                        "${environment.cell.carrier} · ${environment.cell.networkType} · ${environment.cell.signalDbm} dBm"
                    } else "未写入基站信息",
                    onToggle = { enabled ->
                        viewModel.setEnvironment(
                            environment.copy(cell = environment.cell.copy(enabled = enabled)),
                        )
                    },
                ) {
                    EnvTextField(
                        label = "CARRIER",
                        external = environment.cell.carrier,
                        onCommit = { value ->
                            viewModel.setEnvironment(
                                environment.copy(cell = environment.cell.copy(carrier = value)),
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EnvTextField(
                            label = "MCC",
                            external = environment.cell.mcc,
                            keyboardType = KeyboardType.Number,
                            onCommit = { value ->
                                viewModel.setEnvironment(
                                    environment.copy(cell = environment.cell.copy(mcc = value.filter(Char::isDigit).take(3))),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        EnvTextField(
                            label = "MNC",
                            external = environment.cell.mnc,
                            keyboardType = KeyboardType.Number,
                            onCommit = { value ->
                                viewModel.setEnvironment(
                                    environment.copy(cell = environment.cell.copy(mnc = value.filter(Char::isDigit).take(3))),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EnvTextField(
                            label = "AREA / LAC",
                            external = environment.cell.areaCode.toString(),
                            keyboardType = KeyboardType.Number,
                            onCommit = { value ->
                                value.toIntOrNull()?.let {
                                    viewModel.setEnvironment(
                                        environment.copy(cell = environment.cell.copy(areaCode = it)),
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        EnvTextField(
                            label = "CELL ID",
                            external = environment.cell.cellId.toString(),
                            keyboardType = KeyboardType.Number,
                            onCommit = { value ->
                                value.toIntOrNull()?.let {
                                    viewModel.setEnvironment(
                                        environment.copy(cell = environment.cell.copy(cellId = it)),
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EnvTextField(
                            label = "NETWORK",
                            external = environment.cell.networkType,
                            onCommit = { value ->
                                viewModel.setEnvironment(
                                    environment.copy(cell = environment.cell.copy(networkType = value.uppercase())),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        EnvTextField(
                            label = "SIGNAL (DBM)",
                            external = environment.cell.signalDbm.toString(),
                            keyboardType = KeyboardType.Number,
                            onCommit = { value ->
                                value.toIntOrNull()?.let { signal ->
                                    viewModel.setEnvironment(
                                        environment.copy(
                                            cell = environment.cell.copy(
                                                signalDbm = signal.coerceIn(-140, -40),
                                            ),
                                        ),
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionRule(
                index = "03",
                title = "注入方式",
                english = "payload",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(PaperHigh)
                        .border(1.dp, Line, RoundedCornerShape(2.dp))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("写入 Location extras", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(
                            "随测试位置一起提供 Wi-Fi / 蓝牙 / 基站字段。读取与否取决于目标应用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    SurveySwitch(
                        checked = environment.writeLocationExtras,
                        onCheckedChange = { enabled ->
                            viewModel.setEnvironment(environment.copy(writeLocationExtras = enabled))
                        },
                        contentDescription = "写入 Location extras",
                    )
                }
                Spacer(Modifier.height(10.dp))
                PayloadPreview(environment)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalDot(Amber, 5.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "定位漂移是真实模拟；无线环境字段仅作为附加数据。",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Muted,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "04",
                title = "Frida 动态 Hook",
                english = "frida sensor bridge",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = {
                    Text(
                        "EXTERNAL INJECTION",
                        style = MonoTiny.copy(fontSize = 8.sp),
                        color = Signal,
                    )
                },
            )
            FridaSensorBridgeSection(
                frida = frida,
                targetDraft = fridaTargetDraft,
                onTargetDraftChange = { fridaTargetDraft = it },
                sensorSummary = listOfNotNull(
                    "ACCEL".takeIf { settings.sensors.accelerometer },
                    "GYRO".takeIf { settings.sensors.gyroscope },
                    "GRAVITY".takeIf { settings.sensors.gravity },
                    "LINEAR".takeIf { settings.sensors.linearAcceleration },
                    "STEPS".takeIf { settings.sensors.stepCounter },
                    "DETECT".takeIf { settings.sensors.stepDetector },
                ).joinToString(" · ").ifBlank { "NONE" },
                modeLabel = "${settings.motionMode.title} · %.1f m/s".format(
                    settings.speeds.forMode(settings.motionMode),
                ),
                onCheckEnvironment = { viewModel.refreshFridaEnvironment(fridaTargetDraft) },
                onModeSelect = viewModel::setFridaInjectionMode,
                onGenerate = {
                    viewModel.setFridaTarget(fridaTargetDraft)
                    viewModel.generateFridaHook(fridaTargetDraft)
                },
                onCopy = copyText,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnimatedVisibilityLike(visible: Boolean, content: @Composable () -> Unit) {
    // Kept intentionally simple so the settings form never jumps while typing.
    if (visible) content()
}

@Composable
private fun PresetChip(
    title: String,
    code: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(CutCornerShape(topEnd = 10.dp))
            .background(if (selected) Ink else PaperHigh)
            .border(1.dp, if (selected) Ink else Line, CutCornerShape(topEnd = 10.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) PaperHigh else Ink,
        )
        Text(
            code,
            style = MonoTiny.copy(fontSize = 7.sp, letterSpacing = 0.7.sp),
            color = if (selected) Live else Faint,
        )
    }
}

@Composable
private fun EnvironmentSourceBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    code: String,
    enabled: Boolean,
    summary: String,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, if (enabled) LineStrong else Line, RoundedCornerShape(2.dp))
            .background(if (enabled) PaperHigh else PaperHigh.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Ink else Faint,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Ink else Muted)
                    Spacer(Modifier.width(6.dp))
                    Text(code, style = MonoTiny.copy(fontSize = 7.sp, letterSpacing = 0.5.sp), color = Faint)
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) Muted else Faint,
                    maxLines = 1,
                )
            }
            SurveySwitch(
                checked = enabled,
                onCheckedChange = onToggle,
                contentDescription = title,
            )
        }
        if (enabled) {
            Hairline(color = Line.copy(alpha = 0.7f))
            Column(Modifier.padding(13.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun FridaSensorBridgeSection(
    frida: FridaUiState,
    targetDraft: String,
    onTargetDraftChange: (String) -> Unit,
    sensorSummary: String,
    modeLabel: String,
    onCheckEnvironment: () -> Unit,
    onModeSelect: (FridaInjectionMode) -> Unit,
    onGenerate: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    val environment = frida.environment
    val checked = environment?.checked == true
    val target = frida.targetInfo?.takeIf { it.packageName == targetDraft.trim() }
    val jdwpAvailable = target?.debuggable == true ||
        (checked && environment?.debuggable == true)
    Column(Modifier.padding(horizontal = 16.dp)) {
        NoticeBlock(
            text = when (frida.injectionMode) {
                FridaInjectionMode.JDWP ->
                    "JDWP 免 Root：仅限 debuggable App。frida-jdwp-loader 通过 ADB + JDWP 注入 frida-gadget，无需 Root、不重打包；生产包通常不可调试。"
                FridaInjectionMode.ROOT_SERVER ->
                    "Root 路径：需要设备上的 frida-server 运行，能覆盖任意目标 App；Shizuku 不提供 ptrace 权限。"
                FridaInjectionMode.GADGET ->
                    "Gadget 路径：需要把 Frida Gadget 打包进目标 APK 并重新签名；无 Root，但目标会被改写。"
            },
            title = "外部注入 / 三条不同信任级的路径",
            tone = StatusTone.Warning,
        )
        Spacer(Modifier.height(10.dp))
        Text("INJECTION PATH", style = MonoLabel.copy(fontSize = 8.sp), color = Muted)
        Spacer(Modifier.height(5.dp))
        SurveySegmentedControl(
            options = FridaInjectionMode.entries.map { it to it.shortLabel },
            selected = frida.injectionMode,
            onSelect = onModeSelect,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            frida.injectionMode.detail,
            style = MonoTiny.copy(fontSize = 8.sp),
            color = Muted,
        )
        Spacer(Modifier.height(10.dp))
        SurveyTextField(
            value = targetDraft,
            onValueChange = onTargetDraftChange,
            label = "TARGET PACKAGE",
            placeholder = "com.example.target",
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
            onImeAction = onGenerate,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TinyActionChip(
                text = if (frida.checkingEnvironment) "检测中…" else "检测环境",
                onClick = onCheckEnvironment,
                icon = EIcon.Refresh,
                enabled = !frida.checkingEnvironment,
            )
            Spacer(Modifier.width(7.dp))
            TinyActionChip(
                text = if (frida.generating) "生成中…" else "生成 Hook 脚本",
                onClick = onGenerate,
                selected = true,
                icon = EIcon.Sensors,
                enabled = !frida.generating && targetDraft.isNotBlank(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(PaperHigh)
                .border(1.dp, Line, RoundedCornerShape(2.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            FridaStatusRow(
                label = "TARGET",
                value = when {
                    target == null -> "NOT CHECKED"
                    !target.installed -> "NOT INSTALLED"
                    target.debuggable -> "DEBUGGABLE"
                    else -> "NOT DEBUGGABLE"
                },
                color = when {
                    target == null -> Faint
                    target.debuggable -> Green
                    else -> Amber
                },
            )
            FridaStatusRow(
                label = "JDWP",
                value = when {
                    target == null && !checked -> "—"
                    jdwpAvailable -> "AVAILABLE"
                    else -> "NO"
                },
                color = when {
                    target == null && !checked -> Faint
                    jdwpAvailable -> Green
                    else -> Amber
                },
            )
            FridaStatusRow(
                label = "SHIZUKU",
                value = when {
                    environment == null -> "NOT CHECKED"
                    environment.shizukuReady -> "READY"
                    else -> "LIMITED"
                },
                color = when {
                    !checked -> Faint
                    environment?.shizukuReady == true -> Green
                    else -> Amber
                },
            )
            FridaStatusRow(
                label = "SU BINARY",
                value = when {
                    !checked -> "—"
                    environment?.suBinary == true -> "FOUND"
                    else -> "NOT FOUND"
                },
                color = when {
                    !checked -> Faint
                    environment?.suBinary == true -> Amber
                    else -> Faint
                },
            )
            FridaStatusRow(
                label = "FRIDA SERVER",
                value = when {
                    !checked -> "—"
                    environment?.fridaServer == true -> "RUNNING"
                    else -> "NOT FOUND"
                },
                color = when {
                    !checked -> Faint
                    environment?.fridaServer == true -> Green
                    else -> Amber
                },
            )
            FridaStatusRow(label = "HOOK MODE", value = modeLabel, color = Ink)
        }
        if (frida.injectionMode == FridaInjectionMode.JDWP &&
            target != null &&
            target.installed &&
            !target.debuggable &&
            !jdwpAvailable
        ) {
            Spacer(Modifier.height(7.dp))
            Text(
                "该目标未标记 debuggable；JDWP 不会生效。请改用 Root/Gadget、调试版目标，或在 ro.debuggable=1 的模拟器上测试。",
                style = MonoTiny.copy(fontSize = 8.sp),
                color = Amber,
            )
        }
        if (frida.error != null) {
            Spacer(Modifier.height(7.dp))
            Text(frida.error, style = MonoTiny.copy(fontSize = 8.sp), color = Amber)
        }
        if (frida.notice != null && frida.artifact == null) {
            Spacer(Modifier.height(7.dp))
            Text(frida.notice, style = MonoTiny.copy(fontSize = 8.sp), color = Muted)
        }
        val artifact = frida.artifact
        if (artifact != null) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp))
                    .background(Ink)
                    .padding(13.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FRIDA SCRIPT / READY", style = MonoLabel.copy(fontSize = 8.sp), color = Live)
                    Spacer(Modifier.weight(1f))
                    Text(
                        artifact.injectionMode.shortLabel,
                        style = MonoTiny.copy(fontSize = 7.sp),
                        color = Paper.copy(alpha = 0.38f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "TARGET  ${artifact.targetPackage}",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Paper.copy(alpha = 0.75f),
                )
                Text(
                    "DEBUG  ${if (artifact.targetInfo.debuggable) "YES" else "NO"}",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = if (artifact.targetInfo.debuggable) {
                        Live.copy(alpha = 0.85f)
                    } else {
                        Amber
                    },
                )
                Text(
                    "CHANNELS  $sensorSummary",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Paper.copy(alpha = 0.55f),
                )
                Text(
                    "LOCAL  ${artifact.localPath}",
                    style = MonoTiny.copy(fontSize = 7.sp),
                    color = Paper.copy(alpha = 0.4f),
                )
                artifact.deployedPath?.let { path ->
                    Text(
                        "DEVICE  $path",
                        style = MonoTiny.copy(fontSize = 7.sp),
                        color = Live.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text("HOST COMMAND", style = MonoTiny.copy(fontSize = 7.sp), color = Live)
                Text(
                    artifact.hostCommand,
                    style = MonoTiny.copy(fontSize = 8.sp).copy(fontFamily = JetBrainsMono),
                    color = Paper.copy(alpha = 0.82f),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TinyActionChip(
                        text = "复制电脑命令",
                        onClick = { onCopy("Frida host command", artifact.hostCommand) },
                        selected = true,
                        dark = true,
                        icon = EIcon.External,
                    )
                    if (artifact.deviceCommand.isNotBlank()) {
                        Spacer(Modifier.width(7.dp))
                        TinyActionChip(
                            text = "复制设备命令",
                            onClick = { onCopy("Frida device command", artifact.deviceCommand) },
                            dark = true,
                        )
                    }
                }
                if (artifact.note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(artifact.note, style = MonoTiny.copy(fontSize = 7.sp), color = Amber)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            "JDWP 只对 debuggable 目标有效；Root/Gadget 覆盖其他目标。LSPatch / VirtualXposed 需要重打包或虚拟空间，不是本节的无重打包 JDWP 路径。",
            style = MonoTiny.copy(fontSize = 8.sp),
            color = Faint,
        )
    }
}

@Composable
private fun FridaStatusRow(label: String, value: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MonoLabel.copy(fontSize = 8.sp), color = Muted)
        Spacer(Modifier.weight(1f))
        Text(value, style = MonoTiny.copy(fontSize = 8.sp), color = color)
    }
}

@Composable
private fun EnvTextField(
    label: String,
    external: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var local by remember { mutableStateOf(external) }
    if (external != local) {
        // Presets or a reset from another surface update the displayed value.
        // This comparison also avoids a feedback loop while typing.
        if (local.isEmpty() || external.length > local.length + 1) local = external
    }
    SurveyTextField(
        value = local,
        onValueChange = {
            local = it
            onCommit(it)
        },
        label = label,
        modifier = modifier,
        keyboardType = keyboardType,
        singleLine = true,
    )
}

@Composable
private fun PayloadPreview(environment: EnvironmentProfile) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp))
            .background(Ink)
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("INJECTION EXTRAS", style = MonoLabel.copy(fontSize = 8.sp), color = Live)
            Spacer(Modifier.weight(1f))
            Text("WGS-84", style = MonoTiny.copy(fontSize = 8.sp), color = Paper.copy(alpha = 0.35f))
        }
        Spacer(Modifier.height(8.dp))
        PayloadLine(
            "wifi",
            if (environment.wifi.enabled) "${environment.wifi.ssid} / ${environment.wifi.bssid}" else "disabled",
        )
        PayloadLine(
            "bt",
            if (environment.bluetooth.enabled) "${environment.bluetooth.name} / ${environment.bluetooth.rssi} dBm" else "disabled",
        )
        PayloadLine(
            "cell",
            if (environment.cell.enabled) {
                "${environment.cell.mcc}-${environment.cell.mnc} / ${environment.cell.areaCode} / ${environment.cell.cellId}"
            } else "disabled",
        )
        PayloadLine("extras", if (environment.writeLocationExtras) "attached" else "detached")
    }
}

@Composable
private fun PayloadLine(key: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            key,
            style = MonoTiny.copy(fontSize = 8.sp, fontFamily = JetBrainsMono),
            color = Paper.copy(alpha = 0.42f),
            modifier = Modifier.width(46.dp),
        )
        Text(
            value,
            style = MonoTiny.copy(fontSize = 8.sp, fontFamily = JetBrainsMono, letterSpacing = 0.sp),
            color = Paper.copy(alpha = 0.78f),
            maxLines = 1,
        )
    }
}
