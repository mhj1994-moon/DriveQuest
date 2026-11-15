package com.example.drivequest

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun VehicleDataPanel(trip: TripViewModel) {
    Panel {
        SmallLabel("공식 SDK · API 수신")
        Spacer(Modifier.height(10.dp))
        Text("차량 속도 · ${trip.speedText}")
        Text("Android 위치 · ${trip.locationText}")
        Text("차량 현재 주행거리 · ${trip.distanceText}")
        Text("차량 현재 연비 · ${trip.fuelText}")
        Text("내비 남은 거리 원본 · ${trip.remainingText}")
        Text("내비 위치 응답 · " + (trip.link.navigationPosition?.value?.let { "%.5f, %.5f".format(it.first, it.second) } ?: "수신 대기"))
        Text("내비 제한속도 원본 · " + (trip.link.speedLimitRaw?.value?.toString() ?: "수신 대기") + " · 단위·유효 구간 미검증")
        Text("차량 주행 세션과 퀘스트의 시작 시점은 다를 수 있습니다. 거리 차이는 계산하지 않습니다.")
    }
}

@Composable internal fun QuestMissions(trip: TripViewModel) {
    Mission("01", "급가속 3회 미만", trip.rapidText)
    Mission("02", "구간 연비 12km/L 이상", trip.ecoText)
    Mission("03", "속도위반 0회", trip.speedingText)
}
