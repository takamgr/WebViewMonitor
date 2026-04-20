package com.example.webviewmonitor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.widget.*

object VacancyFilterDialog {

    fun show(
        context: Context,
        dates: List<String>,
        onStart: (selectedDates: List<String>, selectedRounds: List<Int>) -> Unit
    ) {
        val density = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(), (16 * density).toInt(),
                (24 * density).toInt(), (8 * density).toInt()
            )
        }

        // 日付セクション
        root.addView(TextView(context).apply {
            text = "日付"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        })

        val dateCheckBoxes = mutableListOf<CheckBox>()
        val dateContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        dates.forEach { date ->
            val cb = CheckBox(context).apply { text = date; isChecked = true }
            dateCheckBoxes.add(cb)
            dateContainer.addView(cb)
        }
        root.addView(ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (280 * density).toInt()
            )
            addView(dateContainer)
        })

        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply {
                text = "全選択"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { dateCheckBoxes.forEach { it.isChecked = true } }
            })
            addView(Button(context).apply {
                text = "全解除"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { dateCheckBoxes.forEach { it.isChecked = false } }
            })
        })

        // ラウンドセクション
        root.addView(TextView(context).apply {
            text = "ラウンド"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
        })

        val roundCheckBoxes = mutableListOf<CheckBox>()
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            for (i in 1..4) {
                val cb = CheckBox(context).apply {
                    text = "R$i"
                    isChecked = true
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                roundCheckBoxes.add(cb)
                addView(cb)
            }
        })

        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply {
                text = "全選択"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { roundCheckBoxes.forEach { it.isChecked = true } }
            })
            addView(Button(context).apply {
                text = "全解除"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { roundCheckBoxes.forEach { it.isChecked = false } }
            })
        })

        AlertDialog.Builder(context)
            .setTitle("監視条件を選択")
            .setView(root)
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("監視開始") { _, _ ->
                val selDates  = dates.filterIndexed { i, _ -> dateCheckBoxes[i].isChecked }
                val selRounds = (1..4).filter { roundCheckBoxes[it - 1].isChecked }
                onStart(selDates, selRounds)
            }
            .show()
    }
}
