package com.example.drivequest

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

private val Background = Color(0xFF0B1420)
private val Surface = Color(0xFF152333)
private val Muted = Color(0xFFACBAC8)
private val Accent = Color(0xFFB9EC70)
private val Blue = Color(0xFF7DCEC7)

@Serializable private data object Home : NavKey
@Serializable private data object Quest : NavKey
@Serializable private data object Driving : NavKey
@Serializable private data object Result : NavKey
@Serializable private data object Details : NavKey
@Serializable private data object Rewards : NavKey
@Serializable private data object Connected : NavKey

private class DemoStore(context: Context) {
    private val prefs = context.getSharedPreferences("drivequest_demo", Context.MODE_PRIVATE)
    var points by mutableIntStateOf(prefs.getInt("points", 0)); private set
    var sessions by mutableIntStateOf(prefs.getInt("sessions", 0)); private set
    var xp by mutableIntStateOf(prefs.getInt("xp", 0)); private set
    var hat by mutableStateOf(prefs.getBoolean("hat", false)); private set
    var lastDistanceKm by mutableStateOf(if (prefs.contains("last_sdk_vehicle_trip_km")) prefs.getFloat("last_sdk_vehicle_trip_km", 0f).toDouble() else null); private set
    var lastDurationSeconds by mutableIntStateOf(prefs.getInt("last_duration_seconds", 0)); private set
    fun complete(id: String, distanceKm: Double?, durationSeconds: Int) {
        if (id.isBlank() || prefs.getString("last_completed", null) == id) return
        points += 120; sessions++; xp += 40
        lastDistanceKm = distanceKm; lastDurationSeconds = durationSeconds
        prefs.edit().putString("last_completed", id).putInt("points", points)
            .putInt("sessions", sessions).putInt("xp", xp)
            .also { if (distanceKm != null) it.putFloat("last_sdk_vehicle_trip_km", distanceKm.toFloat()) else it.remove("last_sdk_vehicle_trip_km") }
            .putInt("last_duration_seconds", durationSeconds).apply()
    }
    fun unlockHat() {
        if (hat || points < 100) return
        points -= 100; hat = true
        prefs.edit().putInt("points", points).putBoolean("hat", true).apply()
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var trip: TripViewModel
    private var permissionNotice by mutableStateOf("")
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        trip.permissionsChanged()
        permissionNotice = if (missingPermissions().isEmpty()) "" else "허용하지 않은 항목은 수신하지 않습니다. 권한 설정에서 변경할 수 있어요."
    }
    private fun missingPermissions(): List<String> = buildList {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) add(PlatformVehicleSource.SPEED_PERMISSION)
        // Coarse location remains usable if the user chose approximate location.
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    private fun requestDataPermissions() {
        if (trip.displayOnly) return
        val missing = missingPermissions()
        if (missing.isEmpty()) { trip.permissionsChanged(); return }
        val history = getSharedPreferences("permission_requests", MODE_PRIVATE)
        if (missing.any { history.getBoolean(it, false) && !shouldShowRequestPermissionRationale(it) }) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            return
        }
        fun launch() {
            if (trip.displayOnly) return
            history.edit().also { editor -> missing.forEach { editor.putBoolean(it, true) } }.apply()
            permissionLauncher.launch(missing.toTypedArray())
        }
        if (missing.any { shouldShowRequestPermissionRationale(it) })
            android.app.AlertDialog.Builder(this).setTitle("주행 정보 권한")
                .setMessage("차량 속도와 현재 위치를 퀘스트 진행 확인에 사용합니다. 위치는 앱이 보이는 동안만 수신하고 좌표를 저장하거나 외부로 보내지 않습니다.")
                .setPositiveButton("계속") { _, _ -> launch() }.setNegativeButton("나중에", null).show()
        else launch()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trip = ViewModelProvider(this)[TripViewModel::class.java]
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Background,
                surface = Surface, onPrimary = Background, onSurface = Color.White)) {
                val store = remember { DemoStore(applicationContext) }
                LaunchedEffect(trip.displayOnly) {
                    val history = getSharedPreferences("permission_requests", MODE_PRIVATE)
                    if (!trip.displayOnly && missingPermissions().any { !history.getBoolean(it, false) }) requestDataPermissions()
                }
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    DriveQuestApp(store, trip, permissionNotice, ::requestDataPermissions)
                }
            }
        }
    }
    override fun onStart() { super.onStart(); trip.startForeground() }
    override fun onStop() { trip.stopForeground(); super.onStop() }
}

@Composable private fun DriveQuestApp(store: DemoStore, trip: TripViewModel, permissionNotice: String, permissions: () -> Unit) {
    val backStack = rememberNavBackStack(Home)
    fun home() { backStack.clear(); backStack.add(Home) }
    BackHandler(enabled = trip.displayOnly) { /* No navigation into interactive content while restricted. */ }
    if (trip.displayOnly) {
        DrivingDashboard(trip)
        return
    }
    NavDisplay(backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
            entry<Home> {
                Page("DRIVEQUEST", "오늘의 드라이브") {
                    Text("운전을 즐거움으로,\n친환경을 혜택으로.", fontSize = 27.sp,
                        lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(trip.connection, color = Blue)
                    if (permissionNotice.isNotBlank()) Text(permissionNotice, color = Muted)
                    SecondaryButton("주행 정보 권한 설정", permissions)
                    Spacer(Modifier.height(20.dp))
                    Panel {
                        SmallLabel("나의 성장")
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("LEVEL", "${1 + store.xp / 200}", Accent)
                            Metric("포인트", "${store.points} P", Color.White)
                            Metric("완료 기록", "${store.sessions}회", Color.White)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Panel {
                        SmallLabel(if (trip.active) "진행 중인 퀘스트" else "오늘의 추천 퀘스트")
                        Spacer(Modifier.height(8.dp))
                        Text("여유로운 에코 드라이브", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(if (trip.active) trip.destination else selectedDestination(trip), fontSize = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(if (trip.active) "차량 주행거리 · ${trip.distanceKm?.let { receivedKm(it) } ?: "정보 대기 중"}"
                            else "목적지까지 도로 상황을 살피며 함께해요.", color = Muted)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(if (trip.active) "진행 상황 보기" else "퀘스트 확인하기") {
                            backStack.add(if (trip.active) Driving else Quest)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    EfficiencyComparison(trip)
                    Spacer(Modifier.height(16.dp))
                    Panel {
                        SmallLabel("최근 기록")
                        Spacer(Modifier.height(8.dp))
                        if (store.sessions == 0) Text("첫 번째 여정을 기다리고 있어요.")
                        else if (store.lastDurationSeconds == 0) Text("이전 여정 ${store.sessions}회 · 거리 정보 없음")
                        else Text("차량 주행 세션 ${receivedKm(store.lastDistanceKm, "거리 미수신")} · ${journeyTime(store.lastDurationSeconds)}")
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton("성장과 보상") { backStack.add(Rewards) }
                        SecondaryButton("연결 정보") { backStack.add(Connected) }
                    }
                }
            }
            entry<Quest> {
                Page("QUEST", "출발 전 퀘스트", onBack = { backStack.removeAt(backStack.lastIndex) }) {
                    Text(selectedDestination(trip), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("내비게이션에 목적지를 설정하면 해당 목적지로 퀘스트를 시작합니다.", color = Muted, fontSize = 13.sp)
                    QuestMissions(trip)
                    Text("급가속 관찰 기준: 2.5m/s² 이상 1초 지속. 목표는 주행을 돕는 참고 기준입니다.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("차량 정보가 연결되면 주행 상태를 확인할 수 있어요.", color = Muted, fontSize = 13.sp)
                    Text("도로 상황과 교통법규가 모든 미션보다 우선해요.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(enabled = trip.link.routeGuiding, onClick = {
                        trip.beginTrip(selectedDestination(trip))
                        if (trip.active) backStack.add(Driving)
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (trip.link.routeGuiding) "퀘스트 시작" else "내비 목적지 설정 대기") }
                }
            }
            entry<Driving> {
                Page("DRIVE", "퀘스트 진행", onBack = { home() }) {
                    JourneyProgress(trip)
                    Spacer(Modifier.height(16.dp))
                    EfficiencyComparison(trip)
                    Spacer(Modifier.height(16.dp))
                    Text("출발하면 버튼 없는 주행 요약 화면으로 전환됩니다.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("주행 종료") {
                        if (trip.finishTrip()) {
                            store.complete(trip.tripId, trip.distanceKm, trip.elapsedSeconds.toInt())
                            backStack.add(Result)
                        }
                    }
                }
            }
            entry<Result> {
                Page("REPORT", "이번 여정", onBack = { home() }) {
                    Text(trip.destination, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Panel {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("차량 주행 세션 거리", receivedKm(store.lastDistanceKm, "미수신"), Accent)
                            Metric("관찰 시간", journeyTime(store.lastDurationSeconds), Color.White)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Vehicle SDK 원본 · 퀘스트 시작 이후 거리와는 다를 수 있습니다.", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    EfficiencyComparison(trip)
                    Spacer(Modifier.height(16.dp))
                    QuestMissions(trip)
                    Panel {
                        SmallLabel("이번 여정의 보상")
                        Spacer(Modifier.height(8.dp))
                        Text("+120 P   +40 XP", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("구간별 리포트") { backStack.add(Details) }
                    SecondaryButton("성장과 보상") { backStack.add(Rewards) }
                    SecondaryButton("홈으로") { home() }
                }
            }
            entry<Details> {
                Page("INSIGHTS", "구간별 리포트", onBack = { backStack.removeAt(backStack.lastIndex) }) {
                    Text("주행 정보가 쌓이면 구간별 리포트를 확인할 수 있어요.", color = Muted)
                    Spacer(Modifier.height(16.dp))
                    Mission("01", "출발 구간", "주행 정보 대기 중")
                    Mission("02", "이동 구간", "에너지 정보 대기 중")
                    Mission("03", "도착 구간", "도착 정보 대기 중")
                    Spacer(Modifier.height(16.dp))
                    Text("급감속이나 방향지시등 상태만으로 운전을 평가하지 않습니다.", color = Muted)
                    SecondaryButton("홈으로") { home() }
                }
            }
            entry<Rewards> {
                Page("MY GROWTH", "성장과 보상", onBack = { backStack.removeAt(backStack.lastIndex) }) {
                    Panel {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("LEVEL", "${1 + store.xp / 200}", Accent)
                            Metric("경험치", "${store.xp} XP", Color.White)
                            Metric("포인트", "${store.points} P", Color.White)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Mission("✦", "첫 걸음 배지", if (store.sessions > 0) "해금 완료" else "첫 여정 완료 후 해금")
                    Spacer(Modifier.height(16.dp))
                    Panel {
                        MascotPortrait(store.hat)
                        Text("초록 모자", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("앱 내 꾸미기 아이템", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        if (store.hat) Text("적용 완료", color = Accent)
                        else Button(onClick = { store.unlockHat() }, enabled = store.points >= 100,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("100 P로 해금") }
                    }
                    Spacer(Modifier.height(16.dp))
                    SecondaryButton("홈으로") { home() }
                }
            }
            entry<Connected> {
                Page("CONNECTION", "연결 정보", onBack = { backStack.removeAt(backStack.lastIndex) }) {
                    VehicleDataPanel(trip)
                    SecondaryButton("주행 정보 권한 설정", permissions)
                    Spacer(Modifier.height(16.dp))
                    Panel {
                        SmallLabel("내비게이션 · 자동 연결")
                        Spacer(Modifier.height(8.dp))
                        Text(trip.link.destination)
                        Text(trip.link.route, color = Blue)
                        Spacer(Modifier.height(12.dp))
                        Text("누적계 원본  ${trip.link.odometer}")
                        Text("브레이크  ${trip.link.brake}")
                        Text("방향지시등  ${trip.link.signal}")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("공식 SDK/API 수신값만 표시합니다. 내비 위치와 Android 위치는 다른 출처이며, 경로 재생에 따른 내비 이동과 실차 수신·주행 중 표시 승인은 별도 검증이 필요합니다.",
                        color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                }
            }
        })
}

private fun selectedDestination(trip: TripViewModel): String =
    if (trip.link.routeGuiding && trip.link.destination.isNotBlank() && trip.link.destination != "확인 중")
        trip.link.destination else "내비게이션 목적지 설정 대기"

@Composable internal fun JourneyProgress(trip: TripViewModel) {
    Panel {
        SmallLabel(if (trip.active) "목적지 퀘스트 진행 중" else "목적지 퀘스트 대기")
        Spacer(Modifier.height(8.dp))
        Text(trip.destination, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("차량 속도", trip.speedText, Accent)
            Metric("차량 주행거리", trip.distanceText, Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Text("남은 거리 · ${trip.remainingText}", color = Blue)
        Spacer(Modifier.height(12.dp))
        Text("현재 위치 · ${trip.locationText}", color = Blue)
        QuestMissions(trip)
        Text("관찰 시간 ${journeyTime(trip.elapsedSeconds.toInt())}", color = Muted, fontSize = 13.sp)
    }
}

@Composable internal fun EfficiencyComparison(trip: TripViewModel) {
    Panel {
        SmallLabel("지난 동일 구간 대비 에너지 효율")
        Spacer(Modifier.height(10.dp))
        Text("비교 준비 중", color = Blue, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(trip.ecoText, color = Muted)
        Text("비교 기록이 쌓이면 보여드릴게요.", color = Muted)
        Text("지난 기록 없음", color = Muted)
    }
}

@Composable private fun DrivingDashboard(trip: TripViewModel) {
    val maxLength = if (trip.ux.activeRestrictions and 4 != 0) trip.ux.maxTextLength else 64
    val maxItems = if (trip.ux.activeRestrictions and 32 != 0) trip.ux.maxItems else 3
    fun label(value: String) = value.take(maxLength.coerceAtLeast(1))
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("DRIVEQUEST", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(label(if (!trip.ux.known) "차량 상태 확인 중" else "주행 중 · 정보만 표시합니다"),
                color = Muted, fontSize = 14.sp)
            Panel {
                Text(label(if (trip.active) trip.destination else "진행 중인 퀘스트 없음"),
                    fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(label(trip.speedText), color = Accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(label("남은 거리 · ${trip.remainingText}"), color = Muted)
                Text(label("차량 주행거리 · ${trip.distanceKm?.let { receivedKm(it) } ?: "정보 대기 중"}"), color = Muted)
            }
            if (maxItems >= 2) Panel {
                SmallLabel(label("오늘의 퀘스트"))
                Text(label("급가속 3회 미만 · ${trip.rapidText}"), color = Blue, fontSize = 18.sp)
                Text(label("구간 연비 12km/L 이상 · 정보 대기"), color = Muted)
                Text(label("속도위반 0회 · 정보 대기"), color = Muted)
            }
            if (maxItems >= 3) Text(label(trip.connection), color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable private fun Page(eyebrow: String, title: String, onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState())
        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Text("‹", fontSize = 30.sp, color = Color.White)
                    }
                    Text(eyebrow, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}
@Composable internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(22.dp)).padding(20.dp), content = content)
}
@Composable internal fun SmallLabel(text: String) { Text(text, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
@Composable internal fun Metric(label: String, value: String, color: Color) {
    Column { Text(label, color = Muted, fontSize = 12.sp); Spacer(Modifier.height(5.dp)); Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}
@Composable internal fun Mission(number: String, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Surface, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(number, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp))
        Column { Text(title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text(subtitle, color = Muted, fontSize = 12.sp) }
    }
}
@Composable internal fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(14.dp)) { Text(label, fontWeight = FontWeight.Bold) }
}
@Composable internal fun SecondaryButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp)) { Text(label, color = Color.White) }
}
