package com.example.drivequest

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** 테스트 전용 화면. 제품 화면이 아니며 가상 텍스트만 사용한다. */
class DiagnosticActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        content.addView(TextView(this).apply { text = "가상 화면 진단 — 실제 서비스 화면 아님" })
        content.addView(EditText(this).apply {
            id = R.id.diagnostic_input
            hint = "가상 입력"
            setSingleLine(true)
        })
        setContentView(content)
    }
}
