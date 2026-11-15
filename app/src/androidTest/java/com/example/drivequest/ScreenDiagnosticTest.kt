package com.example.drivequest

import android.widget.EditText
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 진단용 화면만 대상으로 복귀와 재생성 시 임시 입력을 검증한다. */
@RunWith(AndroidJUnit4::class)
class ScreenDiagnosticTest {
    @Test fun comparesWindowOrientations() {
        ActivityScenario.launch(DiagnosticActivity::class.java).use { scenario ->
            var portraitWidth = 0
            var landscapeWidth = 0
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            Thread.sleep(2500)
            scenario.onActivity {
                portraitWidth = it.window.decorView.width
                Log.i("DriveQuestScreen", "portrait window=${it.window.decorView.width}x${it.window.decorView.height}")
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(2500)
            scenario.onActivity {
                landscapeWidth = it.window.decorView.width
                Log.i("DriveQuestScreen", "landscape window=${it.window.decorView.width}x${it.window.decorView.height}")
            }
            check(portraitWidth > 0 && landscapeWidth > 0 && portraitWidth != landscapeWidth) {
                "Could not verify different portrait and landscape window widths"
            }
        }
    }

    @Test fun keepsInputAcrossBackgroundAndRecreation() {
        ActivityScenario.launch(DiagnosticActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<EditText>(R.id.diagnostic_input).setText("가상 입력") }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertEquals("가상 입력", it.findViewById<EditText>(R.id.diagnostic_input).text.toString()) }
            scenario.recreate()
            scenario.onActivity { assertEquals("가상 입력", it.findViewById<EditText>(R.id.diagnostic_input).text.toString()) }
        }
    }
}
