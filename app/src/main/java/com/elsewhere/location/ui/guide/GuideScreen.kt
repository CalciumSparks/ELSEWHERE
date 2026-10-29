package com.elsewhere.location.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.location.BuildConfig
import com.elsewhere.location.ui.components.EIcon
import com.elsewhere.location.ui.components.NoticeBlock
import com.elsewhere.location.ui.components.ScreenBackdrop
import com.elsewhere.location.ui.components.ScreenHeader
import com.elsewhere.location.ui.components.SectionRule
import com.elsewhere.location.ui.components.StatusTone
import com.elsewhere.location.ui.components.TinyActionChip
import com.elsewhere.location.ui.theme.Amber
import com.elsewhere.location.ui.theme.Faint
import com.elsewhere.location.ui.theme.Ink
import com.elsewhere.location.ui.theme.InkSoft
import com.elsewhere.location.ui.theme.JetBrainsMono
import com.elsewhere.location.ui.theme.Line
import com.elsewhere.location.ui.theme.Live
import com.elsewhere.location.ui.theme.MonoLabel
import com.elsewhere.location.ui.theme.MonoTiny
import com.elsewhere.location.ui.theme.Muted
import com.elsewhere.location.ui.theme.Paper
import com.elsewhere.location.ui.theme.PaperHigh
import com.elsewhere.location.ui.theme.Signal

/**
 * The permanent in-app field manual. New features get a numbered section here
 * instead of relying only on one-off hints inside their cards.
 */
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onOpenEnvironment: () -> Unit,
    onOpenMotion: () -> Unit,
) {
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
                eyebrow = "FIELD MANUAL / 使用指导",
                title = "使用指导",
                subtitle = "先把通道跑通，再决定世界往哪里移动。",
                onBack = onBack,
                statusText = "v${BuildConfig.VERSION_NAME.removeSuffix("-dev")}",
                statusTone = StatusTone.Ready,
            )

            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(topEnd = 22.dp, bottomStart = 22.dp))
                        .background(Ink)
                        .padding(15.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FIELD MANUAL / 使用指导", style = MonoLabel.copy(fontSize = 9.sp), color = Live)
                        Spacer(Modifier.weight(1f))
                        Text("NO ACCOUNT · NO TELEMETRY", style = MonoTiny.copy(fontSize = 7.sp), color = Paper.copy(alpha = 0.35f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "他乡的核心是“把选中的坐标交给系统定位栈”。路线录制与传感器 Hook 是两条独立附加链路；不要把它们当成同一个开关。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PaperHigh,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        TinyActionChip("打开环境页", onOpenEnvironment, selected = true, dark = true, icon = EIcon.Environment)
                        TinyActionChip("打开运动页", onOpenMotion, dark = true, icon = EIcon.Motion)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(index = "01", title = "快速开始", english = "first fix", modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                GuideStep(
                    "01",
                    "准备通道",
                    "推荐完成 Shizuku 授权；没有 Shizuku 时，用开发者选项 →「选择模拟位置信息应用」选中「他乡」，并授予位置、通知和悬浮窗权限。",
                )
                GuideStep(
                    "02",
                    "选点",
                    "拖地图让目标点落在准星下，也可以搜索地址或输入经纬度。地图上的准星就是最终注入点。",
                )
                GuideStep(
                    "03",
                    "启动",
                    "地图页点「开始模拟」。系统定位、前台服务和悬浮摇杆开始工作；停止时测试提供者会被移除。",
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(index = "02", title = "地图与真实 GPS", english = "map / real probe", modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "拖动地图、缩放和搜索都只改变待注入点，不会在后台偷偷开始模拟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "右侧「真实」会读取系统 GPS，先显示坐标、地址、精度和缓存年龄；确认后再「应用此点」。只有缓存时会标注「缓存定位」，不会冒充实时 GPS。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(index = "03", title = "悬浮摇杆与路线", english = "joystick / route", modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "开始模拟后，悬浮摇杆默认出现。按住拖动方向，松手滑行；面板内可切换走路 / 跑步 / 骑行。红色方块停止会话，× 只收起面板。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "地图右侧「路线」进入工作台：添加航点、设置模式与环线，保存后可巡航。巡航时悬浮面板切换为环形进度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "04",
                title = "路线录制",
                english = "route recorder",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = { TinyActionChip("去录制", onOpenMotion, icon = EIcon.Record) },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                GuideStep(
                    "01",
                    "先开始模拟",
                    "录制采样的是当前被注入的坐标，不是独立 GPS。没有模拟会话时「开始录制」不可用。",
                )
                GuideStep(
                    "02",
                    "开始移动",
                    "点「开始录制」，然后用悬浮摇杆或已保存路线移动；录制卡片会显示时长、距离和点数。",
                )
                GuideStep(
                    "03",
                    "结束并保存",
                    "点「结束并保存」生成一个普通 RoutePlan，可直接巡航、编辑或删除。采样约 1 秒一次，忽略小于 1.5 m 的点；少于 5 m 不保存。",
                )
                Text(
                    "模拟停止时，足够长的录制会尝试自动保存；进程被系统杀死时，尚未结束的录制会丢失。",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Amber,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "05",
                title = "Frida / JDWP 免Root",
                english = "external sensor bridge",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = { TinyActionChip("去环境页", onOpenEnvironment, icon = EIcon.Sensors) },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                NoticeBlock(
                    text = "JDWP 只对 debuggable 目标有效；无需 Root、无需重打包，但仍需要电脑/Termux 上的 ADB 完成最后一跳。",
                    title = "外部注入 / 不是任意 App",
                    tone = StatusTone.Warning,
                )
                Spacer(Modifier.height(8.dp))
                GuideStep(
                    "01",
                    "检测目标",
                    "环境页 →「04 Frida 动态 Hook」→ 路径选 JDWP，填目标包名，点「检测环境」。状态行应显示 TARGET DEBUGGABLE / JDWP AVAILABLE。",
                )
                GuideStep(
                    "02",
                    "生成脚本",
                    "点「生成 Hook 脚本」。结果面板会给出目标、通道和本地脚本路径；脚本包含当前模式、速度和传感器开关。",
                )
                GuideStep(
                    "03",
                    "导出并运行",
                    "点「导出 / 分享一键注入包」，发到电脑解压；Windows 运行 run-jdwp-windows.ps1，macOS/Linux 运行 run-jdwp-unix.sh。包内固定 Frida Gadget 16.7.19。",
                )
                GuideCode(
                    listOf(
                        "adb pull '<script>' ./elsewhere-sensor.js",
                        "python frida-jdwp-loader.py frida -n <pkg> \\",
                        "  -i script -l ./elsewhere-sensor.js -g 16.7.19",
                        "adb logcat -s ELSEWHERE-SENSOR:*",
                    ),
                )
                Text(
                    "Frida 17 移除了 Gadget 的 Java bridge；本项目固定 16.7.19。Root / Gadget 路径在同一区块切换；LSPatch / Xposed 是另一条 patch 路线。",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Faint,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(
                index = "06",
                title = "LSPatch / Xposed",
                english = "rootless module",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailing = { TinyActionChip("去模块", onOpenEnvironment, icon = EIcon.Info) },
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                NoticeBlock(
                    text = "模块不能注入未修改的 App。LSPatch 需要把模块嵌入 patched APK；VirtualXposed 需要目标与模块进入虚拟空间。两者都不需要 Root，但会改变签名/运行容器。",
                    title = "patch 或虚拟空间",
                    tone = StatusTone.Warning,
                )
                Spacer(Modifier.height(8.dp))
                GuideStep(
                    "01",
                    "导出并安装模块",
                    "环境页 →「05 LSPatch / Xposed」→「导出模块 APK」，安装导出的 APK。模块 API 为 Xposed 82。",
                )
                GuideStep(
                    "02",
                    "Patch 目标",
                    "LSPatch 选择目标 APK，Integrated 模式勾选「他乡 · 传感器 Xposed 模块」后 patch 并安装；或把目标和模块都放进 VirtualXposed。",
                )
                GuideStep(
                    "03",
                    "同步配置",
                    "点「写入当前配置」通过 Shizuku 写全局配置，或复制 ADB 命令。模块默认 WALK/1.35 m/s/全部通道，每 2 秒重读。",
                )
                Text(
                    "LSPatch 会改变目标签名；VirtualXposed 使用虚拟容器。两者都可能被完整性校验或 Xposed 检测发现。",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Amber,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(index = "07", title = "缓存、更新与诊断", english = "maintenance", modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "「应用」页可以扫描应用、查看可访问缓存，并跳转系统缓存页面。Shizuku 不具备删除其他应用内部缓存的能力；外部快照写回后也不保证目标能读取。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "「系统与诊断」里检查 Shizuku、AppOp、悬浮窗与 Hook 信号；「检查更新」只读取公开 GitHub Release，不会自动下载安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionRule(index = "08", title = "边界", english = "honest limits", modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "注入位置会被 Android 标记为 mock；不绕过反作弊、Mock 检测或 Play Integrity。传感器 Hook 只影响被注入/被 patch 的目标进程；不能凭空创造硬件事件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "所有外部服务（高德/OSM、Photon、GitHub、Frida、LSPatch）也有自己的条款和可用性限制。",
                    style = MonoTiny.copy(fontSize = 8.sp),
                    color = Faint,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideStep(index: String, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            index,
            style = MonoTiny.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
            color = Signal,
            modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun GuideCode(lines: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(Ink)
            .border(1.dp, Line, RoundedCornerShape(2.dp))
            .padding(12.dp),
    ) {
        Text("COMMAND / 操作提示", style = MonoTiny.copy(fontSize = 7.sp), color = Live)
        Spacer(Modifier.height(5.dp))
        lines.forEach { line ->
            Text(
                line,
                style = MonoTiny.copy(fontSize = 8.sp, fontFamily = JetBrainsMono),
                color = PaperHigh.copy(alpha = 0.8f),
            )
        }
    }
}
