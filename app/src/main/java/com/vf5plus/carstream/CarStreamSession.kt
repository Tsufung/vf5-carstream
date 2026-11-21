package com.vf5plus.carstream

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class CarStreamSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return CarStreamScreen(carContext)
    }
}
