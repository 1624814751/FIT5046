package com.example.movesmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.movesmart.navigation.AppNavHost

//
val AppBackground = Color(0xFFF7F7F5)
val AppPrimary = Color(0xFF5B5FEF)
val AppAccent = Color(0xFFD9FF66)
val AppSurface = Color(0xFFFFFFFF)
val TextInk = Color(0xFF171A1C)
val TextMuted = Color(0xFF70757A)
val SafeGreen = Color(0xFF2E7D5B)
val CardShape = RoundedCornerShape(20.dp)
val ButtonShape = RoundedCornerShape(50)

data class WorkoutRecord(val id: Int, val title: String, val duration: String, val date: String)
val fakeHistoryData = listOf(
    WorkoutRecord(1, "Outdoor Walk", "45 min", "Today, 2:00 PM"),
    WorkoutRecord(2, "Indoor Cycling", "30 min", "Yesterday, 6:30 PM"),
    WorkoutRecord(3, "Yoga", "20 min", "Sep 06, 8:00 AM")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
fun LiveActivityScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Text("Outdoor Walk", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextInk)
        Text("Medium Intensity", fontSize = 14.sp, color = TextMuted)

        Spacer(modifier = Modifier.height(80.dp))
        Text("24:15", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = AppPrimary)
        Text("Duration", fontSize = 14.sp, color = TextMuted)

        Spacer(modifier = Modifier.height(60.dp))
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = "Safety", tint = SafeGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Heart rate normal", fontWeight = FontWeight.Bold, color = TextInk)
                    Text("112 bpm - Keep going!", fontSize = 12.sp, color = TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { },
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = TextInk),
            shape = ButtonShape,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 60.dp) // 留出临时按钮空间
        ) {
            Text("Finish Workout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun HistoryScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground).padding(top = 60.dp, start = 20.dp, end = 20.dp)
    ) {
        Text("Your Progress", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextInk)
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(fakeHistoryData) { record ->
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(record.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextInk)
                            Text("${record.duration} • ${record.date}", fontSize = 12.sp, color = TextMuted)
                        }
                        Row {
                            IconButton(onClick = { }) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = AppPrimary) }
                            IconButton(onClick = { }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted) }
                        }
                    }
                }
            }
        }
    }
}