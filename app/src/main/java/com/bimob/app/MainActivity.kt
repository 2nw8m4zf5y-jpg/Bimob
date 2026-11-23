package com.bimob.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 48, 32, 32)
        root.setBackgroundColor(Color.rgb(245, 247, 250))

        val title = TextView(this)
        title.text = "BIMOB"
        title.textSize = 32f
        title.setTextColor(Color.rgb(35, 45, 60))
        title.gravity = Gravity.CENTER
        title.setPadding(0, 30, 0, 30)
        root.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "Building design workspace"
        subtitle.textSize = 18f
        subtitle.setTextColor(Color.GRAY)
        subtitle.gravity = Gravity.CENTER
        root.addView(subtitle)

        setContentView(root)
    }
}
