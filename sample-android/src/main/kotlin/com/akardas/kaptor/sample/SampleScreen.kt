package com.akardas.kaptor.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.akardas.kaptor.android.KaptorAndroid
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScreen(client: HttpClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("Tap a request, then open the inspector.\n") }

    fun run(label: String, block: suspend () -> HttpResponse) {
        scope.launch {
            log += "→ $label…\n"
            log += try {
                val response = block()
                "✓ $label → ${response.status}\n"
            } catch (e: Exception) {
                "✗ $label → ${e.message}\n"
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Kaptor Sample") }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { run("GET users") { client.get("$BASE_URL/users") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET /users (JSON list)") }

            Button(
                onClick = { run("GET user") { client.get("$BASE_URL/users/7") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET /users/7 (JSON object)") }

            Button(
                onClick = {
                    run("POST login") {
                        client.post("$BASE_URL/login") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"user":"trinity","password":"the-one"}""")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("POST /login (JSON body)") }

            Button(
                onClick = { run("GET search") { client.get("$BASE_URL/search?q=ktor") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET /search?q=ktor") }

            Button(
                onClick = { run("GET report.gz") { client.get("$BASE_URL/report.gz") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET /report.gz (gzip decoding)") }

            Button(
                onClick = { run("GET slow") { client.get("$BASE_URL/slow") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET /slow (3s, in-progress demo)") }

            Button(
                onClick = { run("GET orders/999") { client.get("$BASE_URL/orders/999") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET /orders/999 (404)") }

            Button(
                onClick = { run("POST checkout") { client.post("$BASE_URL/checkout") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("POST /checkout (500)") }

            Button(
                onClick = { run("GET inventory") { client.get("$BASE_URL/inventory") } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET /inventory (503)") }

            OutlinedButton(
                onClick = { KaptorAndroid.launch(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Kaptor") }

            Text(
                text = log,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
