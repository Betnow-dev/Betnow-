package com.webintoapk

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private val api = WebIntoApkApi()
    private val content = LinearLayout(this)
    private val statusLabel = TextView(this)
    private val emailInput by lazy { field("Admin email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) }
    private val passwordInput by lazy { field("Password", InputType.TYPE_TEXT_VARIATION_PASSWORD) }
    private var accessToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(19, 34, 54)
        window.decorView.systemUiVisibility = 0

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(246, 248, 251))
            isFillViewport = true
            addView(content)
        }
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20), dp(28), dp(20), dp(36))
        setContentView(scrollView)
        showLogin()
    }

    private fun showLogin() {
        content.removeAllViews()
        content.addView(title("WebIntoAPK"))
        content.addView(subtitle("Turn a website you control into an installable Android app."))
        val card = card()
        card.addView(sectionTitle("Admin sign in"))
        card.addView(emailInput)
        card.addView(passwordInput)
        card.addView(actionButton("Sign in") { signIn() })
        content.addView(card, sectionParams())
        content.addView(statusLabel)
    }

    private fun signIn() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isBlank() || password.isBlank()) {
            showStatus("Enter your admin email and password.")
            return
        }

        showStatus("Signing in…")
        api.signIn(email, password) { token, error ->
            runOnUiThread {
                if (token == null) {
                    showStatus(error ?: "Sign in failed.")
                } else {
                    accessToken = token
                    passwordInput.text?.clear()
                    showDashboard()
                }
            }
        }
    }

    private fun showDashboard() {
        content.removeAllViews()
        content.addView(title("Website app builder"))
        content.addView(subtitle("Create an app, then request its APK from the secure cloud builder."))

        val accountRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(sectionTitle("Your apps"), LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(actionButton("Sign out") {
                accessToken = null
                showLogin()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        }
        content.addView(accountRow)
        content.addView(statusLabel)
        val projectsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val nameInput = field("App name")
        val packageInput = field("Package ID, e.g. com.example.myapp")
        val urlInput = field("Website URL (HTTPS)", InputType.TYPE_TEXT_VARIATION_URI).apply {
            setText("https://")
        }
        val admobAppInput = field("AdMob App ID (Google test ID by default)").apply {
            setText("ca-app-pub-3940256099942544~3347511713")
        }
        val admobBannerInput = field("AdMob banner ID (Google test ID by default)").apply {
            setText("ca-app-pub-3940256099942544/9214589741")
        }
        val form = card()
        form.addView(sectionTitle("Create an app"))
        form.addView(nameInput)
        form.addView(packageInput)
        form.addView(urlInput)
        form.addView(admobAppInput)
        form.addView(admobBannerInput)
        form.addView(actionButton("Create app") {
            val name = nameInput.text.toString().trim()
            val packageName = packageInput.text.toString().trim()
            val siteUrl = urlInput.text.toString().trim()
            val admobAppId = admobAppInput.text.toString().trim()
            val admobBannerId = admobBannerInput.text.toString().trim()
            val error = validateProject(name, packageName, siteUrl, admobAppId, admobBannerId)
            if (error != null) {
                showStatus(error)
                return@actionButton
            }
            val token = accessToken ?: return@actionButton showStatus("Sign in again to continue.")
            showStatus("Creating app…")
            api.createProject(
                token,
                name,
                packageName,
                siteUrl,
                admobAppId,
                admobBannerId,
            ) { project, createError ->
                runOnUiThread {
                    if (project == null) {
                        showStatus(createError ?: "Could not create app.")
                    } else {
                        nameInput.text?.clear()
                        packageInput.text?.clear()
                        urlInput.setText("https://")
                        showStatus("App created.")
                        refreshProjects(projectsContainer)
                    }
                }
            }
        })
        content.addView(form, sectionParams())
        content.addView(actionButton("Refresh apps") { refreshProjects(projectsContainer) })
        content.addView(projectsContainer)
        refreshProjects(projectsContainer)
    }

    private fun validateProject(
        name: String,
        packageName: String,
        siteUrl: String,
        admobAppId: String,
        admobBannerId: String,
    ): String? {
        if (!Regex("^[A-Za-z0-9][A-Za-z0-9_. -]{1,59}$").matches(name)) {
            return "App name must be 2–60 characters and use letters, numbers, spaces, dots, or hyphens."
        }
        if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(packageName)) {
            return "Use a package ID like com.example.myapp."
        }
        val uri = Uri.parse(siteUrl)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return "Enter a valid HTTPS website you own or control."
        }
        if (!Regex("^ca-app-pub-[0-9]+~[0-9]+$").matches(admobAppId)) {
            return "Enter a valid AdMob App ID."
        }
        if (!Regex("^ca-app-pub-[0-9]+/[0-9]+$").matches(admobBannerId)) {
            return "Enter a valid AdMob banner ID."
        }
        return null
    }

    private fun refreshProjects(container: LinearLayout) {
        val token = accessToken ?: return
        container.removeAllViews()
        container.addView(subtitle("Loading apps…"))
        api.listProjects(token) { projects, error ->
            runOnUiThread {
                if (projects == null) {
                    showStatus(error ?: "Could not load apps.")
                    return@runOnUiThread
                }
                container.removeAllViews()
                if (projects.isEmpty()) {
                    container.addView(subtitle("No apps yet. Create your first app above."))
                } else {
                    projects.forEach { project ->
                        container.addView(projectCard(project), sectionParams())
                    }
                }
            }
        }
    }

    private fun projectCard(project: WebAppProject): LinearLayout {
        val card = card()
        card.addView(sectionTitle(project.name))
        card.addView(subtitle(project.packageName))
        card.addView(subtitle(project.siteUrl))

        val buildsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(actionButton("Build APK") {
            val token = accessToken ?: return@actionButton showStatus("Sign in again to continue.")
            showStatus("Requesting a build for ${project.name}…")
            api.requestBuild(token, project.id) { buildId, error ->
                runOnUiThread {
                    showStatus(
                        if (buildId == null) error ?: "Could not start build."
                        else "Build queued: ${buildId.take(8)}",
                    )
                    refreshBuilds(project.id, buildsContainer)
                }
            }
        })
        card.addView(actionButton("Refresh build status") {
            refreshBuilds(project.id, buildsContainer)
        })
        card.addView(buildsContainer)
        refreshBuilds(project.id, buildsContainer)
        return card
    }

    private fun refreshBuilds(projectId: String, container: LinearLayout) {
        val token = accessToken ?: return
        container.removeAllViews()
        container.addView(subtitle("Loading builds…"))
        api.listBuilds(token, projectId) { builds, error ->
            runOnUiThread {
                container.removeAllViews()
                if (builds == null) {
                    container.addView(subtitle(error ?: "Could not load builds."))
                } else if (builds.isEmpty()) {
                    container.addView(subtitle("No builds yet."))
                } else {
                    builds.forEach { build ->
                        container.addView(subtitle("${build.status.uppercase()} · ${build.id.take(8)}"))
                        build.error?.let { container.addView(subtitle(it)) }
                        build.apkPath?.let { addArtifactButton(container, token, it) }
                    }
                }
            }
        }
    }

    private fun addArtifactButton(container: LinearLayout, token: String, path: String) {
        container.addView(actionButton("Download APK") {
            showStatus("Preparing secure download…")
            api.getArtifactUrl(token, path) { url, error ->
                runOnUiThread {
                    if (url == null) {
                        showStatus(error ?: "Could not prepare download.")
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        showStatus("Opening APK download.")
                    }
                }
            }
        })
    }

    private fun showStatus(message: String) {
        statusLabel.text = message
        statusLabel.visibility = View.VISIBLE
    }

    private fun field(hintText: String, inputType: Int = InputType.TYPE_CLASS_TEXT) =
        EditText(this).apply {
            hint = hintText
            this.inputType = InputType.TYPE_CLASS_TEXT or inputType
            setSingleLine(true)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = sectionParams()
        }

    private fun title(value: String) = TextView(this).apply {
        text = value
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(19, 34, 54))
        setPadding(0, 0, 0, dp(6))
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(19, 34, 54))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun subtitle(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(89, 101, 112))
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(224, 229, 234))
        }
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun sectionParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        bottomMargin = dp(12)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
