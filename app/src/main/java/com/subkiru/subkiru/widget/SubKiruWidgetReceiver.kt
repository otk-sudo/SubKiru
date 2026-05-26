package com.subkiru.subkiru.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// ホーム画面ウィジェットのブロードキャストレシーバー
class SubKiruWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SubKiruWidget()
}
