package com.example.practiceai

import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import android.content.Context
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.darkColorScheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.lazy.rememberLazyListState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    PracticeAIApp()
                }
            }
        }
    }
}

data class ChatMessage(
    val author: String,
    val text: String
)

@Composable
fun PracticeAIApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("eva_prefs", Context.MODE_PRIVATE)
    var serverUrl = "http://176.125.193.183:8000"
    var username by remember {
        mutableStateOf(
            prefs.getString("username", "") ?: ""
        )
    }

    var password by remember {
        mutableStateOf(
            prefs.getString("password", "") ?: ""
        )
    }

    var rememberMe by remember {
        mutableStateOf(
            prefs.getBoolean("rememberMe", false)
        )
    }
    var isLoggedIn by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    if (!isLoggedIn) {
        LoginRegisterScreen(

            username = username,
            password = password,
            status = status,

            rememberMe = rememberMe,
            onRememberMeChange = { rememberMe = it },
            onUsernameChange = { username = it },
            onPasswordChange = { password = it },

            onLoginClick = {
                scope.launch {
                    status = "Выполняется вход..."
                    val ok = login(serverUrl, username, password)

                    if (ok) {
                        if (rememberMe) {
                            prefs.edit()
                                .putString("username", username)
                                .putString("password", password)
                                .putBoolean("rememberMe", true)
                                .apply()
                        } else {
                            prefs.edit().clear().apply()
                        }

                        status = ""
                        isLoggedIn = true
                    } else {
                        status = "Ошибка входа. Проверь логин, пароль или сервер."
                    }
                }
            },
            onRegisterClick = {
                scope.launch {
                    status = "Регистрация..."
                    val result = register(serverUrl, username, password)
                    status = result
                }
            }
        )

    } else {
        ChatScreen(
            serverUrl = serverUrl,
            username = username,
            onLogout = {
                isLoggedIn = false
                status = "Вы вышли из аккаунта"
            }
        )
    }
}

@Composable
fun LoginRegisterScreen(

    username: String,
    password: String,
    status: String,
    rememberMe: Boolean,

    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(
                id = R.drawable.eva_background
            ),
            contentDescription = null,

            modifier = Modifier
                .fillMaxSize()
                .blur(14.dp),

            contentScale =
                ContentScale.Crop
        )
        Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = 0.55f
                        )
                    )
                )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
            )
        {
            Text(
                text = "ЕВА",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Единый Виртуальный Ассистент студента",
                style = MaterialTheme.typography.bodyMedium
            )



            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Логин") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = onRememberMeChange
                )

                Text("Сохранить логин и пароль")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Войти")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRegisterClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Зарегистрироваться")
            }

            if (status.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(status)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    serverUrl: String,
    username: String,
    onLogout: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var isServerOnline by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showKnowledge by remember { mutableStateOf(false) }
    var knowledgeText by remember { mutableStateOf("") }

    var messages by remember {
        mutableStateOf<List<ChatMessage>>(emptyList())
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    LaunchedEffect(Unit) {
        messages = loadHistory(serverUrl, username)

        if (messages.isEmpty()) {
            messages = listOf(
                ChatMessage("Система", "Вы вошли как $username")
            )
        }

        while (true) {
            isServerOnline = checkServerOnline(serverUrl)
            delay(5000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "ЕВА",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )

                NavigationDrawerItem(
                    label = { Text("База знаний") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            knowledgeText = loadKnowledge(serverUrl)
                            showKnowledge = true
                        }
                    }
                )

                NavigationDrawerItem(
                    label = { Text("О проекте") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            showAbout = true
                        }
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Очистить историю") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()

                            val ok = clearHistory(serverUrl, username)

                            messages = listOf(
                                ChatMessage(
                                    "Система",
                                    if (ok) "История очищена" else "Ошибка очистки"
                                )
                            )
                        }
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Выйти") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            onLogout()
                        }
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Меню"
                        )
                    }

                    Image(
                        painter = painterResource(
                            id = R.drawable.eva_avatar
                        ),
                        contentDescription = "ЕВА",

                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),

                        contentScale =
                            ContentScale.Crop
                    )

                    Spacer(
                        modifier =
                            Modifier.width(10.dp)
                    )
                    Column {

                        Text(
                            text = "ЕВА",
                            style =
                                MaterialTheme
                                    .typography
                                    .headlineSmall
                        )

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color =
                                            if (isServerOnline)
                                                Color(
                                                    0xFF4CAF50
                                                )
                                            else
                                                Color.Red,

                                        shape =
                                            CircleShape
                                    )
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(6.dp)
                            )

                            Text(
                                text =
                                    if (isServerOnline)
                                        "Онлайн"
                                    else
                                        "Оффлайн",

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.width(12.dp)
                    )
                    Row {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    knowledgeText = loadKnowledge(serverUrl)
                                    showKnowledge = true
                                }
                            }
                        ) {
                            Text("База")
                        }

                        TextButton(
                            onClick = { showAbout = true }
                        ) {
                            Text("О приложении")
                        }
                    }
                }
            }
            Text(
                text = "Виртуальный помощник студента",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ева думает над ответом...")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Введите сообщение") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isBlank()) return@Button

                    messages = messages + ChatMessage("Вы", text)
                    input = ""
                    isLoading = true

                    scope.launch {
                        val answer = sendChatMessage(
                            serverUrl = serverUrl,
                            username = username,
                            message = text
                        )

                        messages = messages + ChatMessage("Бот", answer)
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Отправить")
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = {
                Text("О приложении ЕВА")
            },
            text = {
                Text(
                    "ЕВА — Единый Виртуальный Ассистент студента.\n\n" +
                            "Функции:\n" +
                            "• регистрация и авторизация\n" +
                            "• чат с ИИ\n" +
                            "• память контекста\n" +
                            "• история сообщений\n" +
                            "• серверная обработка запросов\n\n" +
                            "Технологии:\n" +
                            "Android, Kotlin, Jetpack Compose, FastAPI, SQLite, Ollama/Llama."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showAbout = false }
                ) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showKnowledge) {
        AlertDialog(
            onDismissRequest = { showKnowledge = false },
            title = {
                Text("База знаний ЕВЫ")
            },
            text = {
                Text(knowledgeText)
            },
            confirmButton = {
                TextButton(
                    onClick = { showKnowledge = false }
                ) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {

    val isUser =
        msg.author == "Вы"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp
            ),

        horizontalArrangement =
            if (isUser)
                Arrangement.End
            else
                Arrangement.Start
    ) {

        Card(
            modifier =
                Modifier.widthIn(
                    max = 300.dp
                ),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (isUser)
                            MaterialTheme
                                .colorScheme
                                .primaryContainer
                        else
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                ),

            shape =
                MaterialTheme
                    .shapes
                    .extraLarge
        ) {

            Column(
                modifier =
                    Modifier.padding(14.dp)
            ) {

                Text(
                    text = msg.author,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text = msg.text,
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )
            }
        }
    }
}
suspend fun register(
    serverUrl: String,
    username: String,
    password: String
): String {
    return withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("username", username)
            json.put("password", password)

            val response = postJson(
                urlString = "$serverUrl/register",
                json = json.toString()
            )

            if (response.contains("User created")) {
                "Пользователь создан. Теперь нажми «Войти»."
            } else if (response.contains("User already exists")) {
                "Такой пользователь уже существует."
            } else {
                response
            }
        } catch (e: Exception) {
            "Ошибка регистрации: ${e.message}"
        }
    }
}

suspend fun login(
    serverUrl: String,
    username: String,
    password: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("username", username)
            json.put("password", password)

            val response = postJson(
                urlString = "$serverUrl/login",
                json = json.toString()
            )

            response.contains("Login success")
        } catch (e: Exception) {
            false
        }
    }
}

suspend fun sendChatMessage(
    serverUrl: String,
    username: String,
    message: String
): String {
    return withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("username", username)
            json.put("message", message)

            val response = postJson(
                urlString = "$serverUrl/chat",
                json = json.toString()
            )

            val obj = JSONObject(response)
            obj.optString("answer", "Нет ответа от сервера")
        } catch (e: Exception) {
            "Ошибка подключения: ${e.message}"
        }
    }
}
suspend fun loadHistory(
    serverUrl: String,
    username: String
): List<ChatMessage> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/history/$username")
            val connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("X-API-Key", "eva-secret-2026")
            val response = connection
                .inputStream
                .bufferedReader()
                .use { it.readText() }

            val jsonArray =
                org.json.JSONArray(response)

            val list = mutableListOf<ChatMessage>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                list.add(
                    ChatMessage(
                        author = obj.getString("author"),
                        text = obj.getString("text")
                    )
                )
            }

            list

        } catch (e: Exception) {
            listOf(
                ChatMessage(
                    "Система",
                    "Не удалось загрузить историю"
                )
            )
        }
    }
}
suspend fun loadKnowledge(
    serverUrl: String
): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/knowledge")
            val connection =
                url.openConnection() as HttpURLConnection
            connection.setRequestProperty("X-API-Key", "eva-secret-2026")
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-API-Key", "eva-secret-2026")
            val response = connection
                .inputStream
                .bufferedReader()
                .use { it.readText() }

            val obj = JSONObject(response)

            val university =
                obj.optString(
                    "university_name",
                    "Не указано"
                )

            val assistant =
                obj.optString(
                    "assistant_name",
                    "ЕВА"
                )

            val practice =
                obj.getJSONObject("practice")

            val title =
                practice.optString(
                    "title",
                    "Не указано"
                )

            val goal =
                practice.optString(
                    "goal",
                    "Не указано"
                )

            val technologies =
                practice.getJSONArray(
                    "technologies"
                )

            val techList =
                mutableListOf<String>()

            for (i in 0 until technologies.length()) {
                techList.add(
                    technologies.getString(i)
                )
            }

            """
$assistant

ВУЗ:
$university

Практика:
$title

Цель:
$goal

Технологии:
${techList.joinToString("\n") { "• $it" }}
            """.trimIndent()

        } catch (e: Exception) {
            "Ошибка загрузки: ${e.message}"
        }
    }
}
suspend fun clearHistory(
    serverUrl: String,
    username: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("$serverUrl/history/$username")
            val connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "DELETE"

            connection.setRequestProperty(
                "X-API-Key",
                "eva-secret-2026"
            )

            connection.responseCode in 200..299

        } catch (e: Exception) {
            false
        }
    }
}
suspend fun checkServerOnline(
    serverUrl: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(serverUrl)
            val connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            connection.responseCode in 200..299

        } catch (e: Exception) {
            false
        }
    }
}
fun postJson(
    urlString: String,
    json: String
): String {
    val url = URL(urlString)
    val connection = url.openConnection() as HttpURLConnection

    connection.requestMethod = "POST"
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty("X-API-Key", "eva-secret-2026")
    connection.doOutput = true
    connection.connectTimeout = 10000
    connection.readTimeout = 120000

    OutputStreamWriter(connection.outputStream).use { writer ->
        writer.write(json)
        writer.flush()
    }

    val stream = if (connection.responseCode in 200..299) {
        connection.inputStream
    } else {
        connection.errorStream
    }

    return stream.bufferedReader().use { it.readText() }
}