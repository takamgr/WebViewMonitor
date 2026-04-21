package com.example.webviewmonitor

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*

object VacancyFilterDialog {

    fun show(
        context: Context,
        dates: List<String>,
        onConfirm: (Map<String, List<Int>>) -> Unit
    ) {
        val density = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(), (16 * density).toInt(),
                (24 * density).toInt(), (8 * density).toInt()
            )
        }

        val dateCheckBoxes = mutableListOf<CheckBox>()
        val roundCheckBoxesList = mutableListOf<List<CheckBox>>()

        val dateContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // ヘッダー行
        dateContainer.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply {
                text = "日付"
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
            })
            for (i in 1..4) {
                addView(TextView(context).apply {
                    text = "R$i"
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
        })

        dates.forEach { date ->
            val roundCbs = (1..4).map {
                CheckBox(context).apply {
                    isChecked = false
                    isEnabled = false
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).also { it.gravity = Gravity.CENTER }
                }
            }

            val dateCb = CheckBox(context).apply {
                text = date
                isChecked = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
            }

            dateCb.setOnCheckedChangeListener { _, checked ->
                roundCbs.forEach { it.isEnabled = checked; it.isChecked = checked }
            }

            dateCheckBoxes.add(dateCb)
            roundCheckBoxesList.add(roundCbs)

            dateContainer.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(dateCb)
                roundCbs.forEach { addView(it) }
            })
        }

        root.addView(ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (320 * density).toInt()
            )
            addView(dateContainer)
        })

        // 全選択・全解除
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply {
                text = "全選択"
                tag = "btn_all_select"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply {
                text = "全解除"
                tag = "btn_all_clear"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        val dialog = AlertDialog.Builder(context)
            .setTitle("監視条件を選択")
            .setView(root)
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("決定") { _, _ ->
                val result = mutableMapOf<String, List<Int>>()
                dates.forEachIndexed { i, date ->
                    if (dateCheckBoxes[i].isChecked) {
                        val rounds = roundCheckBoxesList[i]
                            .mapIndexedNotNull { idx, cb -> if (cb.isChecked) idx + 1 else null }
                        if (rounds.isNotEmpty()) result[date] = rounds
                    }
                }
                onConfirm(result)
            }
            .show()

        val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val enabledColor = btnPositive.currentTextColor
        btnPositive.isEnabled = false
        btnPositive.setTextColor(android.graphics.Color.GRAY)

        val updateButton = {
            val anyChecked = dateCheckBoxes.any { it.isChecked }
            btnPositive.isEnabled = anyChecked
            btnPositive.setTextColor(if (anyChecked) enabledColor else android.graphics.Color.GRAY)
        }

        dateCheckBoxes.forEach { cb ->
            cb.setOnCheckedChangeListener { _, checked ->
                val idx = dateCheckBoxes.indexOf(cb)
                roundCheckBoxesList[idx].forEach { it.isEnabled = checked; it.isChecked = checked }
                updateButton()
            }
        }

        // 全選択・全解除ボタンもボタン状態を更新する
        root.findViewWithTag<android.widget.Button>("btn_all_select")?.setOnClickListener {
            dateCheckBoxes.forEachIndexed { i, dateCb ->
                dateCb.isChecked = true
                roundCheckBoxesList[i].forEach { it.isEnabled = true; it.isChecked = true }
            }
            updateButton()
        }
        root.findViewWithTag<android.widget.Button>("btn_all_clear")?.setOnClickListener {
            dateCheckBoxes.forEachIndexed { i, dateCb ->
                dateCb.isChecked = false
                roundCheckBoxesList[i].forEach { it.isEnabled = false; it.isChecked = false }
            }
            updateButton()
        }
    }
}
