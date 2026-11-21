package com.sanjaimenan.practice_tracker

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class Session(val date: Long, val minutes: Int)

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("practice", "Practice reminders", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        setContent { MaterialTheme { Tracker(this) } }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val n = NotificationCompat.Builder(context, "practice")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Practice Tracker").setContentText("Time for your keyboard practice.")
            .setAutoCancel(true).build()
        context.getSystemService(NotificationManager::class.java).notify(1, n)
    }
}

@Composable
fun Tracker(context: Context) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var logged by remember { mutableStateOf(auth.currentUser != null) }
    var msg by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("Practice") }
    var seconds by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(80) }
    var metro by remember { mutableStateOf(false) }
    var grade by remember { mutableIntStateOf(1) }
    var pieces by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Not started") }
    var sessions by remember { mutableStateOf(listOf<Session>()) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }

    LaunchedEffect(running) { while (running) { delay(1000); seconds++ } }
    LaunchedEffect(metro, bpm) { while (metro) { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70); delay(60000L / bpm.coerceIn(40, 240)) } }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    LaunchedEffect(logged) {
        val uid = auth.currentUser?.uid
        if (logged && uid != null) {
            db.collection("users").document(uid).collection("sessions").orderBy("date")
                .addSnapshotListener { snap, _ ->
                    sessions = snap?.documents?.map { Session(it.getLong("date") ?: 0, (it.getLong("minutes") ?: 0).toInt()) }?.reversed() ?: emptyList()
                }
            db.collection("users").document(uid).collection("progress").document("trinity")
                .addSnapshotListener { s, _ ->
                    if (s != null && s.exists()) {
                        grade = (s.getLong("grade") ?: 1).toInt()
                        pieces = s.getString("pieces") ?: ""
                        status = s.getString("status") ?: "Not started"
                    }
                }
        }
    }

    if (!logged) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Practice Tracker", style = MaterialTheme.typography.headlineLarge)
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { auth.signInWithEmailAndPassword(email.trim(), password).addOnSuccessListener { logged = true }.addOnFailureListener { msg = it.localizedMessage ?: "Sign in failed" } }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
            OutlinedButton(onClick = { auth.createUserWithEmailAndPassword(email.trim(), password).addOnSuccessListener { logged = true }.addOnFailureListener { msg = it.localizedMessage ?: "Account creation failed" } }, modifier = Modifier.fillMaxWidth()) { Text("Create account") }
            Text(msg)
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Practice Tracker", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = { auth.signOut(); logged = false }) { Text("Sign out") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Practice", "Metronome", "Trinity", "Reports", "Reminder").forEach {
                    FilterChip(selected = page == it, onClick = { page = it }, label = { Text(it) })
                }
            }
            when (page) {
                "Practice" -> {
                    Text("%02d:%02d:%02d".format(seconds/3600, seconds/60%60, seconds%60), style = MaterialTheme.typography.displayMedium)
                    Button(onClick = { running = !running }) { Text(if (running) "Pause" else "Start") }
                    OutlinedButton(onClick = {
                        running = false
                        val uid = auth.currentUser?.uid
                        if (uid != null) db.collection("users").document(uid).collection("sessions")
                            .add(mapOf("date" to System.currentTimeMillis(), "minutes" to (seconds/60).coerceAtLeast(1)))
                            .addOnSuccessListener { msg = "Session saved" }.addOnFailureListener { msg = it.localizedMessage ?: "Save failed" }
                        seconds = 0
                    }) { Text("Finish & save") }
                    Text(msg)
                    LazyColumn { items(sessions) { s ->
                        ListItem(headlineContent = { Text("${s.minutes} minutes") },
                            supportingContent = { Text(SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(s.date))) })
                    } }
                }
                "Metronome" -> {
                    Text("$bpm BPM", style = MaterialTheme.typography.displayLarge)
                    Slider(value = bpm.toFloat(), onValueChange = { bpm = it.toInt() }, valueRange = 40f..240f)
                    Button(onClick = { metro = !metro }) { Text(if (metro) "Stop" else "Start metronome") }
                }
                "Trinity" -> {
                    Text("Trinity Grade $grade")
                    Slider(value = grade.toFloat(), onValueChange = { grade = it.toInt() }, valueRange = 1f..8f, steps = 6)
                    OutlinedTextField(pieces, { pieces = it }, label = { Text("Pieces / syllabus notes") }, modifier = Modifier.fillMaxWidth())
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) { Text(status) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("Not started", "Preparing", "Ready", "Booked", "Passed").forEach {
                                DropdownMenuItem(text = { Text(it) }, onClick = { status = it; expanded = false })
                            }
                        }
                    }
                    Button(onClick = {
                        val uid = auth.currentUser?.uid ?: return@Button
                        db.collection("users").document(uid).collection("progress").document("trinity")
                            .set(mapOf("grade" to grade, "pieces" to pieces, "status" to status))
                            .addOnSuccessListener { msg = "Progress saved" }
                    }) { Text("Save progress") }
                    Text(msg)
                }
                "Reports" -> {
                    val now = System.currentTimeMillis()
                    val week = sessions.filter { it.date >= now - 7L*24*60*60*1000 }.sumOf { it.minutes }
                    val month = sessions.filter { it.date >= now - 30L*24*60*60*1000 }.sumOf { it.minutes }
                    Text("Last 7 days: $week minutes")
                    Text("Last 30 days: $month minutes")
                    Text("Sessions: ${sessions.size}")
                    Button(onClick = {
                        val csv = "Date,Minutes\n" + sessions.joinToString("\n") { "${it.date},${it.minutes}" }
                        val f = java.io.File(context.cacheDir, "practice-sessions.csv"); f.writeText(csv)
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        context.startActivity(Intent.createChooser(send, "Export CSV"))
                    }) { Text("Export CSV") }
                }
                else -> {
                    var hour by remember { mutableIntStateOf(18) }
                    var minute by remember { mutableIntStateOf(0) }
                    Text("Daily reminder: %02d:%02d".format(hour, minute))
                    Row { OutlinedButton(onClick = { hour = (hour+1)%24 }) { Text("Hour +") }; OutlinedButton(onClick = { minute = (minute+15)%60 }) { Text("Minute +15") } }
                    Button(onClick = {
                        val i = Intent(context, ReminderReceiver::class.java)
                        val p = PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,hour); set(Calendar.MINUTE,minute); set(Calendar.SECOND,0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DATE,1) }
                        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setRepeating(AlarmManager.RTC_WAKEUP,c.timeInMillis,AlarmManager.INTERVAL_DAY,p)
                        msg = "Daily reminder scheduled"
                    }) { Text("Enable reminder") }
                    OutlinedButton(onClick = {
                        val p = PendingIntent.getBroadcast(context,1,Intent(context,ReminderReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(p); msg = "Reminder cancelled"
                    }) { Text("Disable reminder") }
                    Text(msg)
                }
            }
        }
    }
}
