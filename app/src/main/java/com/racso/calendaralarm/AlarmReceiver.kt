package com.racso.calendaralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventTitle = intent.getStringExtra("EVENT_TITLE") ?: "Evento de Calendario"
        Log.d("AlarmReceiver", "Alarma recibida para: $eventTitle")

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("EVENT_TITLE", eventTitle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(alarmIntent)
    }
}