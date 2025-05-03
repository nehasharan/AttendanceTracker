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


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            MaterialTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val userState = remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }

    if (userState.value == null) {
        LoginScreen { user ->
            userState.value = user
        }
    } else {
        AttendanceScreen(userId = userState.value!!.uid)
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (FirebaseUser) -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Welcome ${it.user?.displayName}", Toast.LENGTH_SHORT).show()
                        onLoginSuccess(it.user!!)
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: ApiException) {
                Toast.makeText(context, "Google sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Office Tracker!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
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
fun AttendanceScreen(userId: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    val selectedDate = remember { mutableStateOf(LocalDate.now()) }
    val allAttendanceDates = remember { mutableStateListOf<LocalDate>() }

    val selectedMonth = remember { mutableStateOf(selectedDate.value.monthValue) }
    val selectedYear = remember { mutableStateOf(selectedDate.value.year) }

    val isLoading = remember { mutableStateOf(true) }

    val datePickerDialog = remember {
        DatePickerDialog(context).apply {
            setOnDateSetListener { _, year, month, day ->
                val pickedDate = LocalDate.of(year, month + 1, day)
                selectedDate.value = pickedDate
                selectedMonth.value = pickedDate.monthValue
                selectedYear.value = pickedDate.year
            }
        }
    }

    // Load all attendance once
    LaunchedEffect(userId) {
        db.collection("attendance")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                allAttendanceDates.clear()
                for (doc in result) {
                    val dateStr = doc.getString("date")
                    if (dateStr != null) {
                        try {
                            val date = LocalDate.parse(dateStr)
                            allAttendanceDates.add(date)
                        } catch (_: Exception) {}
                    }
                }
                isLoading.value = false
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to load attendance", Toast.LENGTH_SHORT).show()
                isLoading.value = false
            }
    }

    val alreadyMarked = allAttendanceDates.contains(selectedDate.value)
    val filteredDates = allAttendanceDates.filter {
        it.monthValue == selectedMonth.value && it.year == selectedYear.value
    }.sortedDescending()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text("Selected: ${selectedDate.value}", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { datePickerDialog.show() }) {
            Text("Pick a Date")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val doc = hashMapOf(
                    "userId" to userId,
                    "date" to selectedDate.value.toString(),
                    "attended" to true
                )
                db.collection("attendance").add(doc)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Marked ${selectedDate.value}", Toast.LENGTH_SHORT).show()
                        allAttendanceDates.add(selectedDate.value)
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            },
            enabled = !alreadyMarked
        ) {
            Text(if (alreadyMarked) "Already Marked" else "Mark as Attended")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                selectedMonth.value = (selectedMonth.value - 1).takeIf { it >= 1 } ?: 12
                if (selectedMonth.value == 12) selectedYear.value -= 1
            }) {
                Text("<")
            }

            Spacer(Modifier.width(16.dp))

            Text("${Month.of(selectedMonth.value)} ${selectedYear.value}", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.width(16.dp))

            Button(onClick = {
                selectedMonth.value = (selectedMonth.value + 1).takeIf { it <= 12 } ?: 1
                if (selectedMonth.value == 1) selectedYear.value += 1
            }) {
                Text(">")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Days you went to office this month:")

        if (isLoading.value) {
            CircularProgressIndicator()
        } else if (filteredDates.isEmpty()) {
            Text("No days marked in ${Month.of(selectedMonth.value)} ${selectedYear.value}")
        } else {
            LazyColumn {
                items(filteredDates) { date ->
                    Text("- $date", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
