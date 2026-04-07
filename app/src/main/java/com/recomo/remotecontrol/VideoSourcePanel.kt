package com.recomo.remotecontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recomo.remotecontrol.camviewer.data.model.VideoSource
import com.recomo.remotecontrol.camviewer.ui.screens.video.VideoViewModel

/**
 * Panel for selecting video input source.
 */
@Composable
fun VideoSourcePanel() {
    val videoViewModel: VideoViewModel = hiltViewModel()
    val currentSource by videoViewModel.videoSource.collectAsState()
    val hdmiAvailable by videoViewModel.hdmiDeviceAvailable.collectAsState()
    
    PanelBox(title = "VIDEO SOURCE", titleSize = 12.sp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // WebSocket option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentSource == VideoSource.WEBSOCKET,
                    onClick = { videoViewModel.setVideoSource(VideoSource.WEBSOCKET) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color(0xFF4CAF50),
                        unselectedColor = Color(0xFFAAAAAA)
                    )
                )
                Column {
                    Text(
                        "WebSocket (Phone Camera)",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        "H.265 stream via Orin relay",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                }
            }

            // Orin WebSocket option (MJPEG/JPEG frames forwarded from Orin)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentSource == VideoSource.WEBSOCKET_ORIN,
                    onClick = { videoViewModel.setVideoSource(VideoSource.WEBSOCKET_ORIN) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color(0xFF4CAF50),
                        unselectedColor = Color(0xFFAAAAAA)
                    )
                )
                Column {
                    Text(
                        "WebSocket Orin (UVC MJPEG)",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        "Orin UVC bridge stream via WebSocket",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                }
            }
            
            // HDMI-USB option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentSource == VideoSource.HDMI_USB,
                    onClick = { 
                        android.util.Log.i("VideoSourcePanel", "HDMI button clicked, hdmiAvailable=$hdmiAvailable")
                        if (hdmiAvailable) {
                            videoViewModel.setVideoSource(VideoSource.HDMI_USB) 
                        }
                    },
                    enabled = hdmiAvailable,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color(0xFF4CAF50),
                        unselectedColor = Color(0xFFAAAAAA),
                        disabledSelectedColor = Color(0xFF666666),
                        disabledUnselectedColor = Color(0xFF444444)
                    )
                )
                Column {
                    Text(
                        if (hdmiAvailable) "HDMI-to-USB Capture" else "HDMI-to-USB (Not Connected)",
                        color = if (hdmiAvailable) Color.White else Color(0xFF666666),
                        fontSize = 12.sp
                    )
                    Text(
                        "Direct UVC device input",
                        color = if (hdmiAvailable) Color(0xFF888888) else Color(0xFF444444),
                        fontSize = 10.sp
                    )
                }
            }
            
            // Refresh button
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                SecondaryButton(
                    text = "REFRESH",
                    modifier = Modifier.height(28.dp),
                    onClick = { videoViewModel.checkHdmiDevice() }
                )
            }
        }
    }
}
