package kz.della.quickorder

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kz.della.quickorder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback ?: return@registerForActivityResult
        val uris: Array<Uri>? = if (result.resultCode == RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else {
            null
        }
        callback.onReceiveValue(uris)
        fileChooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        webView = binding.webView
        configureWebView(webView)
        setupRefresh()
        setupFab()
        registerBackHandler()

        val initialUrl = intent.getStringExtra(EXTRA_TARGET_URL) ?: BASE_URL
        if (savedInstanceState == null) {
            webView.loadUrl(initialUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TARGET_URL)?.let { webView.loadUrl(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(wv: WebView) {
        val s: WebSettings = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(false)
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.userAgentString = s.userAgentString + " DellaQuickOrderAndroid/1.0"

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                val host = url.host ?: return false
                return if (host.endsWith("della.kz") || host.endsWith("della.ua") ||
                    host.endsWith("della.eu") || host.endsWith("della.pl") ||
                    host.endsWith("della.md")
                ) {
                    false
                } else {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    }.onFailure {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.cannot_open_link),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefresh.isRefreshing = false
                binding.progress.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progress.visibility = View.GONE
                    Snackbar.make(
                        binding.root,
                        getString(R.string.network_error),
                        Snackbar.LENGTH_LONG
                    ).setAction(R.string.retry) { view.reload() }.show()
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progress.progress = newProgress
                binding.progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                supportActionBar?.subtitle = title
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                return try {
                    fileChooserLauncher.launch(fileChooserParams.createIntent())
                    true
                } catch (_: Exception) {
                    fileChooserCallback = null
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.cannot_open_picker),
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }
        }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener { webView.reload() }
    }

    private fun setupFab() {
        binding.fabQuickOrder.setOnClickListener {
            webView.loadUrl(BASE_URL + PATH_CREATE_FREIGHT)
        }
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_home -> { webView.loadUrl(BASE_URL + PATH_HOME); true }
        R.id.action_my_freights -> { webView.loadUrl(BASE_URL + PATH_MY_FREIGHTS); true }
        R.id.action_search -> { webView.loadUrl(BASE_URL + PATH_SEARCH_FREIGHTS); true }
        R.id.action_quick_order -> { webView.loadUrl(BASE_URL + PATH_CREATE_FREIGHT); true }
        R.id.action_reload -> { webView.reload(); true }
        R.id.action_open_browser -> {
            val current = webView.url ?: BASE_URL
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current)))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_TARGET_URL = "kz.della.quickorder.TARGET_URL"
        const val BASE_URL = "https://mob.della.kz"
        const val PATH_HOME = "/app/home"
        const val PATH_MY_FREIGHTS = "/app/my/freights"
        const val PATH_SEARCH_FREIGHTS = "/app/search/freights"
        const val PATH_CREATE_FREIGHT = "/app/my/freights/create"
    }
}
