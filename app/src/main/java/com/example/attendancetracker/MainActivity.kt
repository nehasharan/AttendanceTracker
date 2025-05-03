package com.example.attendancetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.attendancetracker.ui.theme.AttendanceTrackerTheme
import com.google.firebase.FirebaseApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.auth.FirebaseUser
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*
import java.time.Month
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf("splash") }
                var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    currentScreen = if (user == null) "login" else "app"
                }

                when (currentScreen) {
                    "splash" -> SplashScreen()
                    "login" -> LoginScreen { loggedInUser ->
                        user = loggedInUser
                        currentScreen = "app"
                    }
                    "app" -> App(userId = user!!.uid)
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Welcome to Office Tracker", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (FirebaseUser) -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { onLoginSuccess(it.user!!) }
                .addOnFailureListener {
                    Toast.makeText(context, "Login failed", Toast.LENGTH_SHORT).show()
                }
        } catch (e: ApiException) {
            Toast.makeText(context, "Google sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Office Tracker", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(context, gso)
            launcher.launch(client.signInIntent)
        }) {
            Text("Sign in with Google")
        }
    }
}

@Composable
fun App(userId: String) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = "Calendar") },
                    label = { Text("Calendar") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Star, contentDescription = "Starred item") },
                    label = { Text("Metrics") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> CalendarScreen(userId)
                1 -> MetricsScreen(userId)
            }
        }
    }
}

@Composable
fun CalendarScreen(userId: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    val today = LocalDate.now()
    val currentMonth = remember { mutableStateOf(YearMonth.from(today)) }
    val selectedDate = remember { mutableStateOf(today) }
    val markedDates = remember { mutableStateListOf<LocalDate>() }

    LaunchedEffect(userId) {
        db.collection("attendance")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                markedDates.clear()
                for (doc in result) {
                    val dateStr = doc.getString("date") ?: continue
                    try {
                        markedDates.add(LocalDate.parse(dateStr))
                    } catch (_: Exception) {}
                }
            }
    }

    fun toggleAttendance(date: LocalDate) {
        if (markedDates.contains(date)) {
            db.collection("attendance")
                .whereEqualTo("userId", userId)
                .whereEqualTo("date", date.toString())
                .get()
                .addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        doc.reference.delete()
                    }
                    markedDates.remove(date)
                    Toast.makeText(context, "Unmarked $date", Toast.LENGTH_SHORT).show()
                }
        } else {
            val doc = hashMapOf(
                "userId" to userId,
                "date" to date.toString(),
                "attended" to true
            )
            db.collection("attendance").add(doc)
                .addOnSuccessListener {
                    markedDates.add(date)
                    Toast.makeText(context, "Marked $date", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val daysInMonth = remember(currentMonth.value) {
        val yearMonth = currentMonth.value
        (1..yearMonth.lengthOfMonth()).map { day ->
            yearMonth.atDay(day)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                modifier = Modifier
                    .clickable {
                        currentMonth.value = currentMonth.value.minusMonths(1)
                    }
                    .padding(8.dp),
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = currentMonth.value.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                        " ${currentMonth.value.year}",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = ">",
                modifier = Modifier
                    .clickable {
                        currentMonth.value = currentMonth.value.plusMonths(1)
                    }
                    .padding(8.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(columns = GridCells.Fixed(7), content = {
            items(daysInMonth) { date ->
                val isMarked = markedDates.contains(date)
                val isToday = date == today

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(4.dp)
                        .background(
                            when {
                                isMarked -> Color(0xFFAAF683)
                                isToday -> Color.LightGray
                                else -> Color.Transparent
                            },
                            shape = MaterialTheme.shapes.small
                        )
                        .clickable { toggleAttendance(date) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontWeight = if (isMarked) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        })
    }
}

@Composable
fun MetricsScreen(userId: String) {
    val today = LocalDate.now()
    val attendanceDates = remember { mutableStateListOf<LocalDate>() }

    LaunchedEffect(userId) {
        FirebaseFirestore.getInstance()
            .collection("attendance")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snap ->
                attendanceDates.clear()
                snap.documents.forEach { doc ->
                    doc.getString("date")?.let { attendanceDates.add(LocalDate.parse(it)) }
                }
            }
    }

    fun countDaysInLastWeeks(weeks: Int): Int {
        val from = today.minusWeeks(weeks.toLong())
        return attendanceDates.count { it >= from && it <= today }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Attendance Summary", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        val metrics = listOf(
            "This Week" to countDaysInLastWeeks(1),
            "Last 4 Weeks" to countDaysInLastWeeks(4),
            "Last 13 Weeks" to countDaysInLastWeeks(13),
            "Last 26 Weeks" to countDaysInLastWeeks(26),
            "Last 52 Weeks" to countDaysInLastWeeks(52),
        )

        metrics.forEach { (label, count) ->
            Text("$label: $count days")
            Spacer(Modifier.height(8.dp))
        }
    }
}

