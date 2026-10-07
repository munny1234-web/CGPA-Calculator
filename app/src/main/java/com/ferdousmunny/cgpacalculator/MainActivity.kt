package com.ferdousmunny.cgpacalculator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.ferdousmunny.cgpacalculator.data.CourseRepository
import com.ferdousmunny.cgpacalculator.data.ThemeMode
import com.ferdousmunny.cgpacalculator.data.UserPreferences
import com.ferdousmunny.cgpacalculator.model.Course
import com.ferdousmunny.cgpacalculator.model.GradeOption
import com.ferdousmunny.cgpacalculator.model.GradeScale
import com.ferdousmunny.cgpacalculator.util.CourseImporter
import com.ferdousmunny.cgpacalculator.util.BackupManager
import com.ferdousmunny.cgpacalculator.util.PdfExporter
import com.ferdousmunny.cgpacalculator.model.calculateCGPA
import com.ferdousmunny.cgpacalculator.ui.theme.CGPATheme
import com.ferdousmunny.cgpacalculator.util.AppLanguage
import com.ferdousmunny.cgpacalculator.util.BiometricAuthHelper
import com.ferdousmunny.cgpacalculator.util.ImportResult
import com.ferdousmunny.cgpacalculator.util.LocalAppStrings
import com.ferdousmunny.cgpacalculator.util.RatingPrompt
import com.ferdousmunny.cgpacalculator.util.ScenarioManager
import com.ferdousmunny.cgpacalculator.util.stringsFor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val uid = user.uid
        val userPrefs = UserPreferences(applicationContext, uid)
        val repository = CourseRepository(applicationContext, uid)

        setContent {
            var language by remember { mutableStateOf(userPrefs.getLanguage()) }
            var themeMode by remember { mutableStateOf(userPrefs.getThemeMode()) }
            var university by remember { mutableStateOf(userPrefs.getUniversity()) }
            var totalCreditsRequired by remember { mutableStateOf(userPrefs.getTotalCreditsRequired()) }
            var biometricLockEnabled by remember { mutableStateOf(userPrefs.isBiometricLockEnabled()) }
            var isUnlocked by remember { mutableStateOf(!userPrefs.isBiometricLockEnabled()) }
            var lockError by remember { mutableStateOf<String?>(null) }
            var gradingScale by remember { mutableStateOf(userPrefs.getGradingScale()) }
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkMode = when (themeMode) {
                ThemeMode.SYSTEM -> systemInDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            fun tryUnlock() {
                lockError = null
                BiometricAuthHelper.authenticate(
                    activity = this@MainActivity,
                    onSuccess = { isUnlocked = true },
                    onError = { msg -> lockError = msg }
                )
            }

            CGPATheme(darkTheme = darkMode) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(language)) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (!isUnlocked) {
                            LaunchedEffect(Unit) { tryUnlock() }
                            LockScreen(errorMessage = lockError, onRetry = { tryUnlock() })
                        } else {
                        AppRoot(
                            userName = user.displayName ?: user.email ?: "Student",
                            userPrefs = userPrefs,
                            repository = repository,
                            themeMode = themeMode,
                            onThemeModeChange = { themeMode = it; userPrefs.setThemeMode(it) },
                            biometricLockEnabled = biometricLockEnabled,
                            onBiometricLockChange = { biometricLockEnabled = it; userPrefs.setBiometricLockEnabled(it) },
                            gradingScale = gradingScale,
                            onGradingScaleChange = { gradingScale = it; userPrefs.setGradingScale(it) },
                            language = language,
                            onLanguageChange = { language = it; userPrefs.setLanguage(it) },
                            university = university,
                            onUniversityChange = { university = it; userPrefs.setUniversity(it) },
                            totalCreditsRequired = totalCreditsRequired,
                            onTotalCreditsRequiredChange = { totalCreditsRequired = it; userPrefs.setTotalCreditsRequired(it) },
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        )
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen { HOME, SETTINGS, ABOUT }

/** Groups courses by semester, preserving the order semesters first appear in. */
private fun groupBySemester(courses: List<Course>, noSemesterLabel: String): List<Pair<String, List<Course>>> {
    val map = linkedMapOf<String, MutableList<Course>>()
    courses.forEach { c ->
        val key = c.semester.ifBlank { noSemesterLabel }
        map.getOrPut(key) { mutableListOf() }.add(c)
    }
    return map.map { it.key to it.value }
}

/** Reasonable year range for the semester picker: 2021 through a few years into the future. */
private fun yearOptions(): List<String> {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    return (2021..(currentYear + 6)).map { it.toString() }
}

@Composable
fun LockScreen(errorMessage: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("App Locked", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Unlock with your fingerprint, face, or device PIN to continue",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try Again") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    userName: String,
    userPrefs: UserPreferences,
    repository: CourseRepository,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    biometricLockEnabled: Boolean,
    onBiometricLockChange: (Boolean) -> Unit,
    gradingScale: String,
    onGradingScaleChange: (String) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    university: String,
    onUniversityChange: (String) -> Unit,
    totalCreditsRequired: Double,
    onTotalCreditsRequiredChange: (Double) -> Unit,
    onLogout: () -> Unit
) {
    val s = LocalAppStrings.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var department by remember { mutableStateOf(userPrefs.getDepartment()) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(userName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        if (department.isNotBlank()) {
                            Text(department, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                        if (university.isNotBlank()) {
                            Text(university, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NavigationDrawerItem(
                        label = { Text(s.menuHome) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        selected = currentScreen == Screen.HOME,
                        onClick = { currentScreen = Screen.HOME; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text(s.menuSettings) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        selected = currentScreen == Screen.SETTINGS,
                        onClick = { currentScreen = Screen.SETTINGS; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text(s.menuAbout) },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        selected = currentScreen == Screen.ABOUT,
                        onClick = { currentScreen = Screen.ABOUT; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Divider()
                    NavigationDrawerItem(
                        label = { Text(s.logout) },
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; showLogoutConfirm = true },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                Screen.HOME -> s.appName
                                Screen.SETTINGS -> s.settingsTitle
                                Screen.ABOUT -> s.aboutTitle
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(repository = repository, totalCreditsRequired = totalCreditsRequired, gradingScale = gradingScale)
                    Screen.SETTINGS -> SettingsScreen(
                        department = department,
                        onDepartmentChange = { department = it; userPrefs.setDepartment(it) },
                        university = university,
                        onUniversityChange = onUniversityChange,
                        totalCreditsRequired = totalCreditsRequired,
                        onTotalCreditsRequiredChange = onTotalCreditsRequiredChange,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        biometricLockEnabled = biometricLockEnabled,
                        onBiometricLockChange = onBiometricLockChange,
                        gradingScale = gradingScale,
                        onGradingScaleChange = onGradingScaleChange,
                        language = language,
                        onLanguageChange = onLanguageChange,
                        onClearData = { repository.clearAll() },
                        onAccountDeleted = onLogout
                    )
                    Screen.ABOUT -> AboutScreen()
                }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(s.logoutConfirmTitle) },
            text = { Text(s.logoutConfirmMessage) },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onLogout() }) { Text(s.yes) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text(s.no) }
            }
        )
    }
}

// ---------------------- Home: My Courses / Retake / Future Courses ----------------------

@Composable
fun HomeScreen(repository: CourseRepository, totalCreditsRequired: Double, gradingScale: String) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val courses = remember { mutableStateListOf<Course>().apply { addAll(repository.loadCourses()) } }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(s.tabMyCourses, s.tabRetake, s.tabAddCourses, s.tabTargetCgpa, s.tabInsights)
    var showRatingDialog by remember { mutableStateOf(false) }
    val gradeOptions = remember(gradingScale) { GradeScale.forScaleName(gradingScale) }

    LaunchedEffect(Unit) {
        if (RatingPrompt.recordAppOpenAndShouldPrompt(context)) {
            showRatingDialog = true
        }
    }

    fun persist() { repository.saveCourses(courses) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }
        Crossfade(targetState = selectedTab, label = "home_tab_content") { tab ->
        when (tab) {
            0 -> CoursesScreen(
                courses = courses,
                gradeOptions = gradeOptions,
                onAdd = { c -> courses.add(c); persist() },
                onEdit = { updated ->
                    val idx = courses.indexOfFirst { it.id == updated.id }
                    if (idx >= 0) {
                        courses[idx] = updated
                        persist()
                    }
                },
                onDelete = { c -> courses.removeAll { it.id == c.id }; persist() },
                onImport = { imported -> courses.addAll(imported); persist() }
            )
            1 -> RetakeScreen(
                courses = courses,
                gradeOptions = gradeOptions,
                onApplyBatch = { selections ->
                    selections.forEach { (id, grade) ->
                        val idx = courses.indexOfFirst { it.id == id }
                        if (idx >= 0) {
                            courses[idx] = courses[idx].copy(gradeLabel = grade.label, gradePoint = grade.point)
                        }
                    }
                    persist()
                }
            )
            2 -> WhatIfScreen(
                courses = courses,
                gradeOptions = gradeOptions,
                onSaveAll = { newCourses -> courses.addAll(newCourses); persist() }
            )
            3 -> TargetAndTrendScreen(courses = courses)
            4 -> InsightsScreen(courses = courses, totalCreditsRequired = totalCreditsRequired)
        }
        }
    }

    if (showRatingDialog) {
        AlertDialog(
            onDismissRequest = { showRatingDialog = false; RatingPrompt.remindLater(context) },
            title = { Text(s.rateUsTitle) },
            text = { Text(s.rateUsMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showRatingDialog = false
                    RatingPrompt.markAsked(context)
                    RatingPrompt.openPlayStore(context)
                }) { Text(s.rateUsNow) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showRatingDialog = false; RatingPrompt.remindLater(context) }) { Text(s.rateUsLater) }
                    TextButton(onClick = { showRatingDialog = false; RatingPrompt.markAsked(context) }) { Text(s.rateUsNever) }
                }
            }
        )
    }
}

/** A friendlier, icon-led empty state used wherever a list has no content yet. */
@Composable
private fun EmptyState(icon: ImageVector, message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Stat card showing current CGPA with a subtle gradient background. */
@Composable
private fun CgpaStatCard(cgpa: Double, totalCredit: Double) {
    val s = LocalAppStrings.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        )
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(s.currentCgpa, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))
            Text(
                text = String.format("%.2f", cgpa),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text("${s.totalCredit}: ${String.format("%.1f", totalCredit)}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
        }
    }
}

/** Maps a grade label to a semantic color -- green for high grades, red for failing ones. */
private fun gradeColor(gradeLabel: String): Color {
    return when {
        gradeLabel.startsWith("A") -> Color(0xFF2E7D32)
        gradeLabel.startsWith("B") -> Color(0xFF1565C0)
        gradeLabel.startsWith("C") -> Color(0xFFE65100)
        else -> Color(0xFFC62828)
    }
}

/** Small colored pill showing a grade, e.g. "A+". */
@Composable
private fun GradeBadge(gradeLabel: String) {
    val color = gradeColor(gradeLabel)
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(gradeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** One course row inside a semester box. Name/details always keep the delete button visible. */
@Composable
private fun CourseRow(course: Course, deleteLabel: String, gradeOptions: List<GradeOption>, onDelete: () -> Unit, onEdit: (Course) -> Unit) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(course.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${course.credit} cr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                GradeBadge(course.gradeLabel)
                if (course.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showNotes = true }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = s.courseNotesLabel,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = deleteLabel, tint = MaterialTheme.colorScheme.error)
        }
    }

    if (showNotes) {
        AlertDialog(
            onDismissRequest = { showNotes = false },
            title = { Text(s.viewNotesTitle) },
            text = { Text(course.notes) },
            confirmButton = {
                TextButton(onClick = { showNotes = false }) { Text(s.ok) }
            }
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(s.deleteConfirmTitle) },
            text = { Text(s.deleteConfirmMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onDelete()
                    Toast.makeText(context, s.courseDeletedMessage, Toast.LENGTH_SHORT).show()
                }) { Text(s.yes) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(s.no) }
            }
        )
    }

    if (showEditDialog) {
        AddCourseDialog(
            title = s.editCourseTitle,
            existingCourse = course,
            gradeOptions = gradeOptions,
            onDismiss = { showEditDialog = false },
            onConfirm = { updated ->
                showEditDialog = false
                onEdit(updated)
                Toast.makeText(context, s.courseUpdatedMessage, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun StickySemesterHeader(semesterName: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                semesterName,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                "$count",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Divider()
}

/** One course wrapped in its own card, used under a sticky semester header. */
@Composable
private fun CourseCard(course: Course, deleteLabel: String, gradeOptions: List<GradeOption>, onDelete: () -> Unit, onEdit: (Course) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
            CourseRow(course = course, deleteLabel = deleteLabel, gradeOptions = gradeOptions, onDelete = onDelete, onEdit = onEdit)
        }
    }
}

private enum class SortOption { DEFAULT, NAME, GRADE, CREDIT }

private fun applySorting(courses: List<Course>, option: SortOption): List<Course> {
    return when (option) {
        SortOption.DEFAULT -> courses
        SortOption.NAME -> courses.sortedBy { it.name.lowercase() }
        SortOption.GRADE -> courses.sortedByDescending { it.gradePoint }
        SortOption.CREDIT -> courses.sortedByDescending { it.credit }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CoursesScreen(
    courses: List<Course>,
    gradeOptions: List<GradeOption>,
    onAdd: (Course) -> Unit,
    onEdit: (Course) -> Unit,
    onDelete: (Course) -> Unit,
    onImport: (List<Course>) -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var showImportInfo by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<ImportResult?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBackupMenu by remember { mutableStateOf(false) }
    val cgpa = calculateCGPA(courses)

    fun handleImportResult(result: ImportResult) {
        isImporting = false
        if (result.courses.isNotEmpty()) {
            pendingImport = result
        } else {
            Toast.makeText(context, s.importNothingFound, Toast.LENGTH_LONG).show()
        }
    }

    fun handleImportError(e: Exception) {
        isImporting = false
        Toast.makeText(context, e.localizedMessage ?: "Import failed", Toast.LENGTH_LONG).show()
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                try {
                    handleImportResult(CourseImporter.parseImage(context, uri))
                } catch (e: Exception) {
                    handleImportError(e)
                }
            }
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(BackupManager.exportToJson(courses).toByteArray())
                }
                Toast.makeText(context, s.backupExportSuccess, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.localizedMessage ?: "Export failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: ""
                val restored = BackupManager.importFromJson(jsonText)
                if (restored.isNotEmpty()) {
                    pendingImport = ImportResult(restored, 0)
                } else {
                    Toast.makeText(context, s.backupImportNothingFound, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, e.localizedMessage ?: "Import failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            val mimeType = context.contentResolver.getType(uri) ?: ""
            when {
                mimeType == "application/pdf" -> {
                    scope.launch {
                        try {
                            handleImportResult(CourseImporter.parsePdf(context, uri))
                        } catch (e: Exception) {
                            handleImportError(e)
                        }
                    }
                }
                mimeType.startsWith("image/") -> {
                    scope.launch {
                        try {
                            handleImportResult(CourseImporter.parseImage(context, uri))
                        } catch (e: Exception) {
                            handleImportError(e)
                        }
                    }
                }
                else -> {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            handleImportResult(CourseImporter.parse(stream))
                        } ?: run { isImporting = false }
                    } catch (e: Exception) {
                        handleImportError(e)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val filteredCourses = applySorting(
            if (searchQuery.isBlank()) {
                courses
            } else {
                courses.filter { it.name.contains(searchQuery, ignoreCase = true) }
            },
            sortOption
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                CgpaStatCard(cgpa = cgpa, totalCredit = courses.sumOf { it.credit })
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { filePicker.launch("*/*") },
                        enabled = !isImporting,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.importProcessing, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.importFromFile, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = !isImporting,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Import Photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { showImportInfo = true }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.HelpOutline,
                                contentDescription = "Format help",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (courses.isNotEmpty()) {
                                    val file = PdfExporter.exportTranscript(context, courses)
                                    PdfExporter.shareFile(context, file)
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = s.exportPdf,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box {
                            IconButton(onClick = { showBackupMenu = true }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(expanded = showBackupMenu, onDismissRequest = { showBackupMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(s.backupExport) },
                                    onClick = {
                                        showBackupMenu = false
                                        backupExportLauncher.launch("cgpa_backup_${System.currentTimeMillis()}.json")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(s.backupImport) },
                                    onClick = {
                                        showBackupMenu = false
                                        backupImportLauncher.launch("application/json")
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(s.searchHint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { showSortMenu = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = s.sortLabel,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when (sortOption) {
                                SortOption.DEFAULT -> s.sortDefault
                                SortOption.NAME -> s.sortByName
                                SortOption.GRADE -> s.sortByGrade
                                SortOption.CREDIT -> s.sortByCredit
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text(s.sortDefault) }, onClick = { sortOption = SortOption.DEFAULT; showSortMenu = false })
                        DropdownMenuItem(text = { Text(s.sortByName) }, onClick = { sortOption = SortOption.NAME; showSortMenu = false })
                        DropdownMenuItem(text = { Text(s.sortByGrade) }, onClick = { sortOption = SortOption.GRADE; showSortMenu = false })
                        DropdownMenuItem(text = { Text(s.sortByCredit) }, onClick = { sortOption = SortOption.CREDIT; showSortMenu = false })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (courses.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        EmptyState(icon = Icons.Default.MenuBook, message = s.noCoursesMessage)
                    }
                }
            } else if (filteredCourses.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        EmptyState(icon = Icons.Default.SearchOff, message = s.noSearchResults)
                    }
                }
            } else {
                val groups = groupBySemester(filteredCourses, s.noSemesterGroup)
                groups.forEach { (semesterName, list) ->
                    stickyHeader(key = "header_$semesterName") {
                        StickySemesterHeader(semesterName, list.size)
                    }
                    items(list, key = { it.id }) { course ->
                        CourseCard(course = course, deleteLabel = s.delete, gradeOptions = gradeOptions, onDelete = { onDelete(course) }, onEdit = onEdit)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = s.addCourse)
        }
    }

    if (showDialog) {
        AddCourseDialog(
            title = s.addCourse,
            gradeOptions = gradeOptions,
            onDismiss = { showDialog = false },
            onConfirm = { course -> onAdd(course); showDialog = false }
        )
    }

    if (showImportInfo) {
        AlertDialog(
            onDismissRequest = { showImportInfo = false },
            title = { Text(s.importCourses) },
            text = { Text(s.importFormatHint) },
            confirmButton = {
                TextButton(onClick = { showImportInfo = false }) { Text(s.ok) }
            }
        )
    }

    pendingImport?.let { result ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(s.importReviewTitle) },
            text = {
                Column {
                    Text(
                        "${result.courses.size} ${s.importSuccess}" +
                                if (result.skippedLines > 0) " (${result.skippedLines} skipped)" else "",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(result.courses, key = { it.id }) { c ->
                            Text("• ${c.name} — ${c.credit} cr, ${c.gradeLabel}", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onImport(result.courses)
                    pendingImport = null
                }) { Text(s.importAddAll) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(s.cancel) }
            }
        )
    }
}

/** Shared semester-picker: type a name (Spring/Summer/Fall/etc.) and pick a year (2021+). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemesterPicker(
    semesterName: String,
    onSemesterNameChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    label: String
) {
    var yearExpanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = semesterName,
                onValueChange = onSemesterNameChange,
                placeholder = { Text("Spring / Summer / Fall") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(0.7f)) {
                SimpleDropdownField(
                    label = "Year",
                    selectedText = year,
                    options = yearOptions(),
                    expanded = yearExpanded,
                    onExpandedChange = { yearExpanded = it },
                    onOptionSelected = onYearChange
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseDialog(
    title: String,
    existingCourse: Course? = null,
    gradeOptions: List<GradeOption>,
    onDismiss: () -> Unit,
    onConfirm: (Course) -> Unit
) {
    val s = LocalAppStrings.current
    var name by remember { mutableStateOf(existingCourse?.name ?: "") }
    var credit by remember { mutableStateOf(existingCourse?.credit?.toString() ?: "") }
    var gradeExpanded by remember { mutableStateOf(false) }
    var selectedGrade by remember {
        mutableStateOf(
            existingCourse?.let { ec -> gradeOptions.firstOrNull { it.label == ec.gradeLabel } }
                ?: gradeOptions.first()
        )
    }
    var semesterName by remember {
        mutableStateOf(existingCourse?.semester?.substringBeforeLast(" ", "") ?: "")
    }
    var year by remember {
        mutableStateOf(
            existingCourse?.semester?.substringAfterLast(" ", "")?.takeIf { it.toIntOrNull() != null }
                ?: Calendar.getInstance().get(Calendar.YEAR).toString()
        )
    }
    var notes by remember { mutableStateOf(existingCourse?.notes ?: "") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.courseNameHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = credit,
                    onValueChange = { credit = it },
                    label = { Text(s.creditHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                SimpleDropdownField(
                    label = s.gradeLabel,
                    selectedText = "${selectedGrade.label} (${selectedGrade.point})",
                    options = gradeOptions.map { "${it.label} (${it.point})" },
                    expanded = gradeExpanded,
                    onExpandedChange = { gradeExpanded = it },
                    onOptionSelected = { text ->
                        selectedGrade = gradeOptions.first { "${it.label} (${it.point})" == text }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SemesterPicker(
                    semesterName = semesterName,
                    onSemesterNameChange = { semesterName = it },
                    year = year,
                    onYearChange = { year = it },
                    label = s.semesterLabel
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(s.courseNotesHint) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val creditValue = credit.toDoubleOrNull()
                if (name.isBlank() || creditValue == null || creditValue <= 0.0) {
                    error = s.fixErrors
                } else {
                    onConfirm(
                        Course(
                            id = existingCourse?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            credit = creditValue,
                            gradeLabel = selectedGrade.label,
                            gradePoint = selectedGrade.point,
                            semester = if (semesterName.isBlank()) "" else "${semesterName.trim()} $year",
                            notes = notes.trim()
                        )
                    )
                }
            }) { Text(s.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}

// ---------------------- Retake Calculator (multi-select) ----------------------

@Composable
fun RetakeScreen(courses: List<Course>, gradeOptions: List<GradeOption>, onApplyBatch: (Map<String, GradeOption>) -> Unit) {
    val s = LocalAppStrings.current
    if (courses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Default.Refresh, message = s.noCoursesMessage)
        }
        return
    }

    val selections = remember { mutableStateMapOf<String, GradeOption>() }
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    var showApplyConfirm by remember { mutableStateOf(false) }

    val newCourses = courses.map { c -> selections[c.id]?.let { c.copy(gradeLabel = it.label, gradePoint = it.point) } ?: c }
    val currentCGPA = calculateCGPA(courses)
    val newCGPA = calculateCGPA(newCourses)
    val diff = newCGPA - currentCGPA
    val diffColor = if (diff > 0.0001) Color(0xFF2E7D32) else if (diff < -0.0001) Color(0xFFC62828) else Color.Unspecified

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(s.retakeInstruction, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            groupBySemester(courses, s.noSemesterGroup).forEach { (semesterName, list) ->
                item(key = "rheader_$semesterName") {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(semesterName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            Divider(modifier = Modifier.padding(vertical = 6.dp))
                            list.forEachIndexed { index, c ->
                                val isChecked = selections.containsKey(c.id)
                                val currentSelection = selections[c.id]
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    selections[c.id] = gradeOptions.firstOrNull { it.label == c.gradeLabel }
                                                        ?: gradeOptions.first()
                                                } else {
                                                    selections.remove(c.id)
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("${c.credit} cr  |  current: ${c.gradeLabel}", fontSize = 12.sp)
                                        }
                                    }
                                    if (isChecked && currentSelection != null) {
                                        Box(modifier = Modifier.padding(start = 44.dp, bottom = 8.dp)) {
                                            SimpleDropdownField(
                                                label = s.retakeNewGrade,
                                                selectedText = "${currentSelection.label} (${currentSelection.point})",
                                                options = gradeOptions.map { "${it.label} (${it.point})" },
                                                expanded = expandedMap[c.id] ?: false,
                                                onExpandedChange = { expandedMap[c.id] = it },
                                                onOptionSelected = { text ->
                                                    selections[c.id] = gradeOptions.first { "${it.label} (${it.point})" == text }
                                                }
                                            )
                                        }
                                    }
                                    if (index != list.lastIndex) {
                                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(s.currentCgpa)
                    Text(String.format("%.2f", currentCGPA), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(s.cgpaAfterRetake)
                    Text(String.format("%.2f", newCGPA), fontWeight = FontWeight.Bold, color = diffColor)
                }
                if (selections.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (diff > 0.0001) "${s.cgpaWillIncrease} ${String.format("%.2f", diff)}"
                        else if (diff < -0.0001) "${s.cgpaWillDecrease} ${String.format("%.2f", -diff)}"
                        else s.cgpaNoChange,
                        color = diffColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                if (selections.isEmpty()) {
                    Toast.makeText(context, s.retakeNoneSelected, Toast.LENGTH_SHORT).show()
                } else {
                    showApplyConfirm = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(s.retakeApplySelected)
        }
        Text(s.retakeNote, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }

    if (showApplyConfirm) {
        AlertDialog(
            onDismissRequest = { showApplyConfirm = false },
            title = { Text(s.applyRetakeConfirmTitle) },
            text = { Text(s.applyRetakeConfirmMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showApplyConfirm = false
                    onApplyBatch(selections.toMap())
                    selections.clear()
                    Toast.makeText(context, s.retakesAppliedMessage, Toast.LENGTH_SHORT).show()
                }) { Text(s.yes) }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirm = false }) { Text(s.no) }
            }
        )
    }
}

// ---------------------- Future Courses (What-if planner) ----------------------

@Composable
fun WhatIfScreen(courses: List<Course>, gradeOptions: List<GradeOption>, onSaveAll: (List<Course>) -> Unit) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val tempCourses = remember { mutableStateListOf<Course>() }
    var name by remember { mutableStateOf("") }
    var credit by remember { mutableStateOf("") }
    var gradeExpanded by remember { mutableStateOf(false) }
    var selectedGrade by remember { mutableStateOf(gradeOptions.first()) }
    var semesterName by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var error by remember { mutableStateOf("") }
    var pendingNewCourse by remember { mutableStateOf<Course?>(null) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var savedScenarios by remember { mutableStateOf(ScenarioManager.loadScenarios(context)) }
    var newScenarioNameInput by remember { mutableStateOf("") }
    var showScenarioSaveDialog by remember { mutableStateOf(false) }

    val currentCGPA = calculateCGPA(courses)
    val projectedCGPA = calculateCGPA(courses + tempCourses)
    val diff = projectedCGPA - currentCGPA
    val diffColor = if (diff > 0.0001) Color(0xFF2E7D32) else if (diff < -0.0001) Color(0xFFC62828) else Color.Unspecified

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(s.addCoursesInstruction, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(s.courseNameHint) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = credit, onValueChange = { credit = it },
            label = { Text(s.creditHint) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        SimpleDropdownField(
            label = s.gradeLabel,
            selectedText = "${selectedGrade.label} (${selectedGrade.point})",
            options = gradeOptions.map { "${it.label} (${it.point})" },
            expanded = gradeExpanded,
            onExpandedChange = { gradeExpanded = it },
            onOptionSelected = { text ->
                selectedGrade = gradeOptions.first { "${it.label} (${it.point})" == text }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SemesterPicker(
            semesterName = semesterName,
            onSemesterNameChange = { semesterName = it },
            year = year,
            onYearChange = { year = it },
            label = s.semesterLabel
        )
        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val creditValue = credit.toDoubleOrNull()
            if (name.isBlank() || creditValue == null || creditValue <= 0.0) {
                error = s.fixErrors
            } else {
                error = ""
                pendingNewCourse = Course(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    credit = creditValue,
                    gradeLabel = selectedGrade.label,
                    gradePoint = selectedGrade.point,
                    semester = if (semesterName.isBlank()) "" else "${semesterName.trim()} $year"
                )
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(s.addToList)
        }

        pendingNewCourse?.let { newCourse ->
            AlertDialog(
                onDismissRequest = { pendingNewCourse = null },
                title = { Text(s.addToListConfirmTitle) },
                text = {
                    Text(
                        "${s.addToListConfirmMessage}\n\n${newCourse.name} — ${newCourse.credit} cr, ${newCourse.gradeLabel}" +
                                if (newCourse.semester.isNotBlank()) " (${newCourse.semester})" else ""
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        tempCourses.add(0, newCourse)
                        pendingNewCourse = null
                        name = ""
                        credit = ""
                        Toast.makeText(context, s.courseAddedToListMessage, Toast.LENGTH_SHORT).show()
                    }) { Text(s.yes) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingNewCourse = null }) { Text(s.no) }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tempCourses.isNotEmpty()) {
            Text(s.possibleCourses, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            tempCourses.forEach { c ->
                PlannedCourseCard(
                    course = c,
                    onDelete = {
                        tempCourses.removeAll { it.id == c.id }
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(s.currentCgpa)
                    Text(String.format("%.2f", currentCGPA), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(s.projectedCgpa)
                    Text(String.format("%.2f", projectedCGPA), fontWeight = FontWeight.Bold, color = diffColor)
                }
                if (tempCourses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (diff > 0.0001) "${s.cgpaWillIncrease} ${String.format("%.2f", diff)}"
                        else if (diff < -0.0001) "${s.cgpaWillDecrease} ${String.format("%.2f", -diff)}"
                        else s.cgpaNoChange,
                        color = diffColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showScenarioSaveDialog = true },
            enabled = tempCourses.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(s.saveScenarioButton)
        }

        if (savedScenarios.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(s.savedScenariosLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            savedScenarios.forEach { scenario ->
                val scenarioCgpa = calculateCGPA(courses + scenario.courses)
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(scenario.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "${scenario.courses.size} courses  |  ${s.projectedCgpa}: ${String.format("%.2f", scenarioCgpa)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            tempCourses.clear()
                            tempCourses.addAll(scenario.courses)
                            Toast.makeText(context, s.scenarioLoadedMessage, Toast.LENGTH_SHORT).show()
                        }) { Text(s.loadScenarioButton) }
                        IconButton(onClick = {
                            ScenarioManager.deleteScenario(context, scenario)
                            savedScenarios = ScenarioManager.loadScenarios(context)
                            Toast.makeText(context, s.scenarioDeletedMessage, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = s.delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { showSaveConfirm = true },
            enabled = tempCourses.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(s.saveAllAsCompleted)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showScenarioSaveDialog) {
        AlertDialog(
            onDismissRequest = { showScenarioSaveDialog = false; newScenarioNameInput = "" },
            title = { Text(s.saveScenarioButton) },
            text = {
                OutlinedTextField(
                    value = newScenarioNameInput,
                    onValueChange = { newScenarioNameInput = it },
                    label = { Text(s.scenarioNameHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newScenarioNameInput.isNotBlank()) {
                        ScenarioManager.saveScenario(context, newScenarioNameInput.trim(), tempCourses.toList())
                        savedScenarios = ScenarioManager.loadScenarios(context)
                        newScenarioNameInput = ""
                        showScenarioSaveDialog = false
                        Toast.makeText(context, s.scenarioSavedMessage, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(s.save) }
            },
            dismissButton = {
                TextButton(onClick = { showScenarioSaveDialog = false; newScenarioNameInput = "" }) { Text(s.cancel) }
            }
        )
    }

    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text(s.saveCompletedConfirmTitle) },
            text = { Text(s.saveCompletedConfirmMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    onSaveAll(tempCourses.toList())
                    tempCourses.clear()
                    Toast.makeText(context, s.coursesSavedMessage, Toast.LENGTH_SHORT).show()
                }) { Text(s.yes) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) { Text(s.no) }
            }
        )
    }
}

/** One planned-course card with its own delete confirmation. */
@Composable
private fun PlannedCourseCard(course: Course, onDelete: () -> Unit) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(course.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${course.credit} cr, ${course.gradeLabel}", fontSize = 12.sp)
                if (course.semester.isNotBlank()) {
                    Text(course.semester, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = s.delete, tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(s.deleteConfirmTitle) },
            text = { Text(s.deleteConfirmMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onDelete()
                    Toast.makeText(context, s.removedFromListMessage, Toast.LENGTH_SHORT).show()
                }) { Text(s.yes) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(s.no) }
            }
        )
    }
}

// ---------------------- Target CGPA Calculator ----------------------

@Composable
fun TargetCgpaScreen(courses: List<Course>) {
    val s = LocalAppStrings.current
    var targetText by remember { mutableStateOf("") }
    var remainingCreditsText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var resultColor by remember { mutableStateOf(Color.Unspecified) }

    val currentCredit = courses.sumOf { it.credit }
    val currentPoints = courses.sumOf { it.credit * it.gradePoint }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(s.targetCgpaInstruction, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = targetText,
            onValueChange = { targetText = it },
            label = { Text(s.targetCgpaLabel) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = remainingCreditsText,
            onValueChange = { remainingCreditsText = it },
            label = { Text(s.remainingCreditsLabel) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val target = targetText.toDoubleOrNull()
                val remaining = remainingCreditsText.toDoubleOrNull()
                if (target == null || remaining == null || remaining <= 0.0) {
                    result = s.fixErrors
                    resultColor = Color(0xFFC62828)
                } else {
                    val requiredGpa = (target * (currentCredit + remaining) - currentPoints) / remaining
                    when {
                        requiredGpa <= 0.0 -> {
                            result = s.targetAlreadyMetMessage
                            resultColor = Color(0xFF2E7D32)
                        }
                        requiredGpa > 4.0 -> {
                            result = s.targetNotAchievableMessage
                            resultColor = Color(0xFFC62828)
                        }
                        else -> {
                            result = "${s.requiredGpaLabel}: ${String.format("%.2f", requiredGpa)}\n${s.targetAchievableMessage}"
                            resultColor = Color.Unspecified
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(s.calculateButton)
        }

        result?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(
                    message,
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    color = if (resultColor == Color.Unspecified) MaterialTheme.colorScheme.primary else resultColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ---------------------- Semester CGPA Trend ----------------------

private val semesterOrderMap = mapOf(
    "SPRING" to 1, "SUMMER" to 2, "FALL" to 3, "AUTUMN" to 3, "WINTER" to 4
)

private fun semesterSortKey(semester: String): Pair<Int, Int> {
    val parts = semester.trim().split(" ")
    val year = parts.lastOrNull()?.toIntOrNull() ?: 0
    val namePart = parts.dropLast(1).joinToString(" ").uppercase()
    val order = semesterOrderMap.entries.firstOrNull { namePart.contains(it.key) }?.value ?: 5
    return year to order
}

private fun computeSemesterTrend(courses: List<Course>): List<Pair<String, Double>> {
    val withSemester = courses.filter { it.semester.isNotBlank() }
    val semesters = withSemester.map { it.semester }.distinct()
        .sortedWith(compareBy({ semesterSortKey(it).first }, { semesterSortKey(it).second }))
    val result = mutableListOf<Pair<String, Double>>()
    val cumulative = mutableListOf<Course>()
    semesters.forEach { sem ->
        cumulative.addAll(withSemester.filter { it.semester == sem })
        result.add(sem to calculateCGPA(cumulative))
    }
    return result
}

@Composable
fun CgpaTrendScreen(courses: List<Course>) {
    val s = LocalAppStrings.current
    val trend = remember(courses) { computeSemesterTrend(courses) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(s.trendInstruction, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        if (trend.size < 2) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(s.trendNotEnoughData, modifier = Modifier.padding(16.dp))
            }
        } else {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(s.trendChartTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    CgpaLineChart(trend = trend)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(16.dp)) {
                    trend.forEachIndexed { index, (semesterName, cgpaValue) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(semesterName, fontSize = 13.sp)
                            Text(String.format("%.2f", cgpaValue), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        if (index != trend.lastIndex) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CgpaLineChart(trend: List<Pair<String, Double>>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            Column(
                modifier = Modifier.width(28.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("4.0", "3.0", "2.0", "1.0", "0.0").forEach { label ->
                    Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val chartWidth = size.width
                val chartHeight = size.height
                val maxGpa = 4.0

                for (i in 0..4) {
                    val y = chartHeight - (i / maxGpa * chartHeight).toFloat()
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (trend.size >= 2) {
                    val stepX = chartWidth / (trend.size - 1)
                    val points = trend.mapIndexed { index, pair ->
                        val x = stepX * index
                        val y = chartHeight - (pair.second / maxGpa * chartHeight).toFloat()
                        Offset(x, y)
                    }

                    val fillPath = Path().apply {
                        moveTo(points.first().x, chartHeight)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, chartHeight)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.35f), primaryColor.copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = chartHeight
                        )
                    )

                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = primaryColor,
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    points.forEach { point ->
                        drawCircle(color = primaryColor, radius = 6.dp.toPx(), center = point)
                        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = point)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp)) {
            trend.forEach { (semesterName, _) ->
                Text(
                    text = semesterName,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun TargetAndTrendScreen(courses: List<Course>) {
    val s = LocalAppStrings.current
    var targetText by remember { mutableStateOf("") }
    var remainingCreditsText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var resultColor by remember { mutableStateOf(Color.Unspecified) }

    val currentCredit = courses.sumOf { it.credit }
    val currentPoints = courses.sumOf { it.credit * it.gradePoint }
    val trend = remember(courses) { computeSemesterTrend(courses) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(s.tabTargetCgpa, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(s.targetCgpaInstruction, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = targetText,
            onValueChange = { targetText = it },
            label = { Text(s.targetCgpaLabel) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = remainingCreditsText,
            onValueChange = { remainingCreditsText = it },
            label = { Text(s.remainingCreditsLabel) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val target = targetText.toDoubleOrNull()
                val remaining = remainingCreditsText.toDoubleOrNull()
                if (target == null || remaining == null || remaining <= 0.0) {
                    result = s.fixErrors
                    resultColor = Color(0xFFC62828)
                } else {
                    val requiredGpa = (target * (currentCredit + remaining) - currentPoints) / remaining
                    when {
                        requiredGpa <= 0.0 -> {
                            result = s.targetAlreadyMetMessage
                            resultColor = Color(0xFF2E7D32)
                        }
                        requiredGpa > 4.0 -> {
                            result = s.targetNotAchievableMessage
                            resultColor = Color(0xFFC62828)
                        }
                        else -> {
                            result = "${s.requiredGpaLabel}: ${String.format("%.2f", requiredGpa)}\n${s.targetAchievableMessage}"
                            resultColor = Color.Unspecified
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(s.calculateButton)
        }

        result?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(
                    message,
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    color = if (resultColor == Color.Unspecified) MaterialTheme.colorScheme.primary else resultColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Divider()
        Spacer(modifier = Modifier.height(20.dp))

        Text(s.tabTrend, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(s.trendInstruction, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        if (trend.size < 2) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(s.trendNotEnoughData, modifier = Modifier.padding(16.dp))
            }
        } else {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(s.trendChartTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    CgpaLineChart(trend = trend)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(16.dp)) {
                    trend.forEachIndexed { index, (semesterName, cgpaValue) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(semesterName, fontSize = 13.sp)
                            Text(String.format("%.2f", cgpaValue), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        if (index != trend.lastIndex) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------- Insights: Grade Distribution, Best/Worst Semester, Credit Progress ----------------------

private fun computePerSemesterGPA(courses: List<Course>): List<Pair<String, Double>> {
    val withSemester = courses.filter { it.semester.isNotBlank() }
    val semesters = withSemester.map { it.semester }.distinct()
        .sortedWith(compareBy({ semesterSortKey(it).first }, { semesterSortKey(it).second }))
    return semesters.map { sem ->
        sem to calculateCGPA(withSemester.filter { it.semester == sem })
    }
}

private fun computeGradeDistribution(courses: List<Course>): List<Pair<String, Int>> {
    return GradeScale.grades.map { grade ->
        grade.label to courses.count { it.gradeLabel == grade.label }
    }.filter { it.second > 0 }
}

@Composable
fun InsightsScreen(courses: List<Course>, totalCreditsRequired: Double) {
    val s = LocalAppStrings.current

    if (courses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Default.Insights, message = s.insightsNoData)
        }
        return
    }

    val completedCredits = courses.sumOf { it.credit }
    val progressFraction = if (totalCreditsRequired > 0) {
        (completedCredits / totalCreditsRequired).coerceIn(0.0, 1.0)
    } else 0.0
    val perSemester = remember(courses) { computePerSemesterGPA(courses) }
    val gradeDistribution = remember(courses) { computeGradeDistribution(courses) }
    val maxGradeCount = gradeDistribution.maxOfOrNull { it.second } ?: 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(s.creditProgressTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progressFraction.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${s.creditsCompletedLabel}: ${String.format("%.1f", completedCredits)}", fontSize = 12.sp)
                    Text("${s.creditsRequiredLabel}: ${String.format("%.0f", totalCreditsRequired)}", fontSize = 12.sp)
                }
                val remaining = (totalCreditsRequired - completedCredits).coerceAtLeast(0.0)
                Spacer(modifier = Modifier.height(4.dp))
                if (remaining <= 0.0) {
                    Text(s.creditsGoalReachedMessage, fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                } else {
                    Text("${s.creditsRemainingLabel}: ${String.format("%.1f", remaining)}", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (perSemester.size >= 2) {
            val best = perSemester.maxByOrNull { it.second }
            val worst = perSemester.minByOrNull { it.second }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (best != null) {
                    ElevatedCard(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(s.bestSemesterLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(best.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(String.format("%.2f", best.second), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (worst != null) {
                    ElevatedCard(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(s.worstSemesterLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(worst.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(String.format("%.2f", worst.second), color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(s.gradeDistributionTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))
                gradeDistribution.forEach { (label, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(label, fontSize = 12.sp, modifier = Modifier.width(32.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(count.toFloat() / maxGradeCount.toFloat())
                                    .height(16.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$count", fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
