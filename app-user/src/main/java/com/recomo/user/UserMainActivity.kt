package com.recomo.user

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.recomo.common.controller.ControllerManager
import com.recomo.user.control.UserTouchControlViewModel
import com.recomo.user.controller.UserControllerRouter
import com.recomo.user.data.media.UserMediaManager
import com.recomo.user.ui.theme.RecomoUserTheme
import com.recomo.user.ui.screens.UserMainScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UserMainActivity : AppCompatActivity() {
    @Inject lateinit var mediaManager: UserMediaManager

    private val touchControlViewModel: UserTouchControlViewModel by viewModels()
    private val controllerManager = ControllerManager()
    private lateinit var controllerRouter: UserControllerRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controllerRouter = UserControllerRouter(touchControlViewModel)
        lifecycleScope.launch { mediaManager.migrateOldDirectories() }
        lifecycleScope.launch { controllerRouter.start(this, controllerManager) }
        setContent {
            RecomoUserTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    UserMainScreen()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controllerManager.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (controllerManager.onMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }
}
