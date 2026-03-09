package com.racso.calendaralarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.racso.calendaralarm.ui.theme.CalendarAlarmTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val WEB_CLIENT_ID = "749537515351-brf4vmkr0s2mpau3qou4q6ln3ho3l9eb.apps.googleusercontent.com"
private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"

enum class ViewMode { LIST, THREE_DAYS }

class MainActivity : ComponentActivity() {

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val calendarGranted = permissions[Manifest.permission.READ_CALENDAR] == true
            if (!calendarGranted) {
                Toast.makeText(this, "Permiso de calendario necesario", Toast.LENGTH_SHORT).show()
            }
            showUI()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        } else {
            showUI()
        }
    }

    private fun showUI() {
        setContent {
            CalendarAlarmTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var account by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val existing = GoogleSignIn.getLastSignedInAccount(context)
        if (existing != null && GoogleSignIn.hasPermissions(existing, Scope(CALENDAR_SCOPE))) {
            account = existing
        }
    }

    if (account == null) {
        SignInScreen(onSignInSuccess = { account = it })
    } else {
        MainContent(
            account = account!!,
            onSignOut = {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken(WEB_CLIENT_ID)
                    .requestScopes(Scope(CALENDAR_SCOPE))
                    .build()
                GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
                    account = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    account: GoogleSignInAccount,
    onSignOut: () -> Unit,
    viewModel: CalendarViewModel = viewModel()
) {
    var showSettings by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf<ViewMode>(ViewMode.LIST) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!showSettings) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_gong),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text = if (showSettings) "Configuración" else "Gong",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { viewModel.loadEvents(); viewModel.loadThreeDayEvents() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Actualizar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            viewMode = if (viewMode == ViewMode.LIST) ViewMode.THREE_DAYS else ViewMode.LIST
                        }) {
                            Icon(
                                if (viewMode == ViewMode.LIST) Icons.Default.CalendarViewWeek else Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = "Cambiar vista",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        bottomBar = {
            if (!showSettings) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavigationItem(
                            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                            label = "Events",
                            isSelected = viewMode == ViewMode.LIST,
                            onClick = { viewMode = ViewMode.LIST }
                        )
                        NavigationItem(
                            icon = Icons.Default.CalendarMonth,
                            label = "Calendar",
                            isSelected = viewMode == ViewMode.THREE_DAYS,
                            onClick = { viewMode = ViewMode.THREE_DAYS }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (showSettings) {
                SettingsScreen(viewModel, onSignOut)
            } else {
                if (viewMode == ViewMode.LIST) {
                    MainScreen(account, viewModel)
                } else {
                    ThreeDayView(viewModel)
                }
            }
        }
    }
}

@Composable
fun NavigationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun MainScreen(
    account: GoogleSignInAccount,
    viewModel: CalendarViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeAlarms by viewModel.activeAlarms.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadEvents()
    }

    MainScreenContent(
        account = account,
        uiState = uiState,
        activeAlarms = activeAlarms,
        onSetAlarm = { event, mins -> viewModel.setAlarmForEvent(event, mins) },
        onCancelAlarm = { event -> viewModel.cancelAlarmForEvent(event) },
        onPermissionResult = { viewModel.loadEvents() }
    )
}

@Composable
fun MainScreenContent(
    account: GoogleSignInAccount,
    uiState: CalendarUiState,
    activeAlarms: Map<String, Int>,
    onSetAlarm: (CalendarEvent, Int) -> Unit,
    onCancelAlarm: (CalendarEvent) -> Unit,
    onPermissionResult: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { onPermissionResult() }

    var selectedEvent by remember { mutableStateOf<CalendarEvent?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = account.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (val state = uiState) {
            is CalendarUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is CalendarUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No hay eventos") }
            is CalendarUiState.NeedsPermission -> LaunchedEffect(state) { permissionLauncher.launch(state.intent) }
            is CalendarUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Error: ${state.message}") }
            is CalendarUiState.Success -> {
                val groupedEvents = remember(state.events) {
                    state.events.groupBy { event ->
                        Instant.ofEpochMilli(event.startMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                }

                val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE d 'de' MMMM") }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    groupedEvents.forEach { (date, events) ->
                        item {
                            val headerText = when {
                                date == LocalDate.now() -> "Today"
                                date == LocalDate.now().plusDays(1) -> "Tomorrow"
                                else -> date.format(dateFormatter).uppercase()
                            }
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(events) { event ->
                            val alarmMins = activeAlarms[event.id]
                            CompactEventCard(
                                event = event,
                                hasAlarm = alarmMins != null,
                                alarmMins = alarmMins,
                                onClick = { selectedEvent = event }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedEvent?.let { event ->
        EventDetailDialog(
            event = event,
            alarmMins = activeAlarms[event.id],
            onDismiss = { selectedEvent = null },
            onSetAlarm = { mins -> onSetAlarm(event, mins) },
            onCancelAlarm = { onCancelAlarm(event) }
        )
    }
}

@Composable
fun CompactEventCard(
    event: CalendarEvent,
    hasAlarm: Boolean,
    alarmMins: Int? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(90.dp) // Large enough for all devices
                    .padding(end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val timeParts = event.startTime.split(" ").last().split(":")
                Text(
                    text = "${timeParts[0]}:${timeParts[1]}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (timeParts[0].toInt() < 12) "AM" else "PM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
            
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = event.calendarName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (hasAlarm && alarmMins != null) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = if (alarmMins == 0) "Inicio" else "${alarmMins}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            if (hasAlarm) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ThreeDayView(viewModel: CalendarViewModel) {
    val uiState by viewModel.threeDayUiState.collectAsState()
    val activeAlarms by viewModel.activeAlarms.collectAsState()
    val startDate by viewModel.currentStartDate.collectAsState()
    
    val days = (0..2).map { startDate.plusDays(it.toLong()) }
    val hours = (8..20).toList()
    var selectedEvent by remember { mutableStateOf<CalendarEvent?>(null) }

    LaunchedEffect(startDate) {
        viewModel.loadThreeDayEvents()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.previousDays() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Anterior")
                }
                Text(
                    text = "${startDate.month.name.take(3)} ${startDate.year}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                IconButton(onClick = { viewModel.nextDays() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Siguiente")
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp)) {
            days.forEach { day ->
                val isToday = day == LocalDate.now()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        day.dayOfWeek.name.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (uiState is CalendarUiState.Success || uiState is CalendarUiState.Empty) {
            val events = if (uiState is CalendarUiState.Success) (uiState as CalendarUiState.Success).events else emptyList()
            
            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column {
                    hours.forEach { hour ->
                        Row(modifier = Modifier.height(64.dp).fillMaxWidth()) {
                            Text(
                                text = String.format("%02d:00", hour),
                                modifier = Modifier.width(48.dp).padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            repeat(3) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxSize().padding(start = 48.dp)) {
                    days.forEach { day ->
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val dayEvents = events.filter {
                                Instant.ofEpochMilli(it.startMillis).atZone(ZoneId.systemDefault()).toLocalDate() == day
                            }
                            dayEvents.forEach { event ->
                                val startTime = Instant.ofEpochMilli(event.startMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                                if (startTime.hour in hours) {
                                    val topOffset = ((startTime.hour - hours.first()) * 64 + (startTime.minute * 64 / 60)).toFloat()
                                    
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 2.dp)
                                            .offset(y = topOffset.dp)
                                            .height(60.dp)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(0.dp))
                                            .clickable { selectedEvent = event }
                                            .padding(4.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = event.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (uiState is CalendarUiState.Loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        }
    }

    selectedEvent?.let { event ->
        EventDetailDialog(
            event = event,
            alarmMins = activeAlarms[event.id],
            onDismiss = { selectedEvent = null },
            onSetAlarm = { mins -> viewModel.setAlarmForEvent(event, mins) },
            onCancelAlarm = { viewModel.cancelAlarmForEvent(event) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailDialog(
    event: CalendarEvent,
    alarmMins: Int?,
    onDismiss: () -> Unit,
    onSetAlarm: (Int) -> Unit,
    onCancelAlarm: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "CONFIRMED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_gong),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d") }
                    val date = Instant.ofEpochMilli(event.startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text(
                        text = "${date.format(dateFormatter)} • ${event.startTime.split(" ").last()} - ${event.endTime.split(" ").last()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(text = "Calendario: ${event.calendarName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                
                Spacer(Modifier.height(32.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(24.dp))
                
                Text("SET ALARM REMINDER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(Modifier.height(12.dp))
                
                var tempMins by remember { mutableIntStateOf(alarmMins ?: 15) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedCard(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (tempMins == 0) "At start" else "$tempMins minutes before", style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Default.ExpandMore, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(0, 5, 10, 15, 30, 60).forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text(if (mins == 0) "At start" else "$mins minutes before") },
                                    onClick = {
                                        tempMins = mins
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { 
                            onSetAlarm(tempMins) 
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Set", fontWeight = FontWeight.Bold)
                    }
                }
                
                if (alarmMins != null) {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { onCancelAlarm(); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Desactivar Alarma", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: CalendarViewModel, onSignOut: () -> Unit) {
    val calendars by viewModel.calendars.collectAsState()
    val selectedIds by viewModel.selectedCalendarIds.collectAsState()
    val defaultMins by viewModel.defaultAlarmMinutes.collectAsState()
    val maxListEvents by viewModel.maxListEvents.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Configuración General", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = "Alarma por defecto (minutos antes):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = defaultMins.toFloat(),
                onValueChange = { viewModel.setDefaultAlarmMinutes(it.toInt()) },
                valueRange = 0f..60f,
                steps = 11,
                modifier = Modifier.weight(1f)
            )
            Text(text = "$defaultMins min", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Cantidad de eventos en el listado:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = maxListEvents.toFloat(),
                onValueChange = { viewModel.setMaxListEvents(it.toInt()) },
                valueRange = 5f..50f,
                steps = 8,
                modifier = Modifier.weight(1f)
            )
            Text(text = "$maxListEvents", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)) {
            Text("Cerrar Sesión", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = "Selecciona tus calendarios:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (calendars.isEmpty()) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(calendars) { calendar ->
                    val isSelected = selectedIds.contains(calendar.id)
                    ListItem(
                        headlineContent = { Text(calendar.summary, fontWeight = FontWeight.Medium) },
                        supportingContent = { if (calendar.primary) Text("Calendario Principal") },
                        trailingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleCalendarSelection(calendar.id) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.toggleCalendarSelection(calendar.id) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
fun SignInScreen(onSignInSuccess: (GoogleSignInAccount) -> Unit) {
    val context = LocalContext.current
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(WEB_CLIENT_ID)
            .requestScopes(Scope(CALENDAR_SCOPE))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)
            onSignInSuccess(account)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al iniciar sesión", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = R.drawable.ic_gong),
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.height(16.dp))
            Text("Gong", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("Iniciar sesión con Google", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EventCardPreview() {
    CalendarAlarmTheme {
        CompactEventCard(
            event = CalendarEvent("1", "Team Sync", "25/10 10:00", "11:00", 0L, "Conference Room A"),
            hasAlarm = true,
            alarmMins = 15,
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val sampleEvents = listOf(
        CalendarEvent("1", "Team Sync", "25/10 10:00", "25/10 11:30", Instant.now().toEpochMilli(), "Conference Room A"),
        CalendarEvent("2", "Product Review", "25/10 13:30", "25/10 15:00", Instant.now().plus(4, ChronoUnit.HOURS).toEpochMilli(), "Zoom Call"),
        CalendarEvent("3", "Client Meeting", "26/10 09:00", "26/10 10:00", Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli(), "Blue Bottle Coffee")
    )
    
    CalendarAlarmTheme {
        MainScreenContent(
            account = GoogleSignIn.getLastSignedInAccount(LocalContext.current) ?: GoogleSignInAccount.createDefault(),
            uiState = CalendarUiState.Success(sampleEvents),
            activeAlarms = mapOf("1" to 15),
            onSetAlarm = { _, _ -> },
            onCancelAlarm = {},
            onPermissionResult = {}
        )
    }
}