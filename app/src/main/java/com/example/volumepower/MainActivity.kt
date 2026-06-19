package risk.tech.volumebuttons

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 1001
    private lateinit var masterSwitch: android.widget.Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 48, 48, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
            
            val titleText = TextView(this@MainActivity).apply {
                text = getString(R.string.app_name)
                textSize = 22f
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            masterSwitch = android.widget.Switch(this@MainActivity).apply {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                val isServiceOn = isAccessibilityServiceEnabled(this@MainActivity, VolumePowerAccessibilityService::class.java)
                
                isChecked = prefs.getBoolean("master_toggle", false) && isServiceOn
                
                setOnCheckedChangeListener { buttonView, isChecked ->
                    if (isChecked) {
                        if (!isAccessibilityServiceEnabled(this@MainActivity, VolumePowerAccessibilityService::class.java)) {
                            buttonView.isChecked = false
                            showAccessibilityDisclosureDialog()
                        } else {
                            prefs.edit().putBoolean("master_toggle", true).apply()
                        }
                    } else {
                        prefs.edit().putBoolean("master_toggle", false).apply()
                    }
                }
            }
            
            addView(titleText)
            addView(masterSwitch)
        }

        val fragmentContainer = android.widget.FrameLayout(this).apply {
            id = R.id.fragment_container
        }

        layout.addView(titleBar)
        layout.addView(fragmentContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        
        setContentView(layout)
        updateBackground()

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(fragmentContainer.id, DashboardFragment())
                .commit()
        }

        checkFirstLaunch()
    }

    fun updateBackground() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = prefs.getString("app_bg_theme", "default_gradient") ?: "default_gradient"
        
        val drawable = when (theme) {
            "default_gradient" -> {
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(android.graphics.Color.parseColor("#003a32"), android.graphics.Color.parseColor("#8A9A5B"), android.graphics.Color.parseColor("#b99e00"))
                )
            }
            "midnight_blue" -> {
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(android.graphics.Color.parseColor("#0f2027"), android.graphics.Color.parseColor("#203a43"), android.graphics.Color.parseColor("#2c5364"))
                )
            }
            "purple_nebula" -> {
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(android.graphics.Color.parseColor("#1f1c2c"), android.graphics.Color.parseColor("#582456"), android.graphics.Color.parseColor("#928dab"))
                )
            }
            "crimson_sunset" -> {
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(android.graphics.Color.parseColor("#2b1055"), android.graphics.Color.parseColor("#75225b"), android.graphics.Color.parseColor("#b03a2e"))
                )
            }
            "emerald_dream" -> {
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(android.graphics.Color.parseColor("#000428"), android.graphics.Color.parseColor("#004e92"), android.graphics.Color.parseColor("#006633"))
                )
            }
            "solid_black" -> android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#000000"))
            "solid_gray" -> android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#121212"))
            "solid_navy" -> android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#0a192f"))
            else -> {
                android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(android.graphics.Color.parseColor("#003a32"), android.graphics.Color.parseColor("#8A9A5B"), android.graphics.Color.parseColor("#b99e00"))
                )
            }
        }
        window.setBackgroundDrawable(drawable)
        
        val currentLogo = prefs.getString("app_splash_logo", "lush_green.png") ?: "lush_green.png"
        val iconResId = when (currentLogo) {
            "lush_green.png" -> R.drawable.ic_logo_lush_green
            "midnight_blue.png" -> R.drawable.ic_logo_midnight_blue
            "purple_nebula.png" -> R.drawable.ic_logo_purple_nebula
            "crimson_sunset.png" -> R.drawable.ic_logo_crimson_sunset
            "emerald_dream.png" -> R.drawable.ic_logo_emerald_dream
            "pitch_black.png" -> R.drawable.ic_logo_pitch_black
            "dark_gray.png" -> R.drawable.ic_logo_dark_gray
            "deep_navy.png" -> R.drawable.ic_logo_deep_navy
            else -> R.drawable.ic_logo_lush_green
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val builder = android.app.ActivityManager.TaskDescription.Builder()
                builder.setIcon(iconResId)
                builder.setLabel(getString(R.string.app_name))
                setTaskDescription(builder.build())
            } else {
                @Suppress("DEPRECATION")
                val bm = android.graphics.BitmapFactory.decodeResource(resources, iconResId)
                @Suppress("DEPRECATION")
                val td = android.app.ActivityManager.TaskDescription(getString(R.string.app_name), bm)
                setTaskDescription(td)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAccessibilityDisclosureDialog() {
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Accessibility Service Required")
            .setMessage("Volume Buttons Mapper requires the Accessibility Service permission to detect volume button presses and perform your configured custom actions (such as locking the screen, toggling flashlight, or launching apps).\n\nThis service is used strictly for intercepting volume button events and does not collect, store, or share any personal or sensitive user data.\n\nPlease enable the Volume Buttons Mapper service in the upcoming Accessibility settings screen.")
            .setPositiveButton("Agree & Continue") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Accessibility Settings not available", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
            
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.WHITE)
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.WHITE)
        }
    }

    private fun checkFirstLaunch() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("is_first_launch", true)) {
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Welcome to Volume Buttons Mapper!")
                .setMessage("Volume Buttons Mapper requires the Accessibility Service permission to detect volume button presses and perform your configured custom actions (such as locking the screen, toggling flashlight, or launching apps).\n\nThis service is used strictly for intercepting volume button events and does not collect, store, or share any personal or sensitive user data.\n\nClick 'Agree & Continue' to grant required permissions and enable the service.")
                .setPositiveButton("Agree & Continue") { _, _ ->
                    prefs.edit().putBoolean("is_first_launch", false).apply()
                    val neededPerms = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        neededPerms.add(Manifest.permission.CAMERA)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            neededPerms.add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    }
                    if (neededPerms.isNotEmpty()) {
                        ActivityCompat.requestPermissions(this, neededPerms.toTypedArray(), PERMISSION_REQUEST_CODE)
                    } else {
                        showAccessibilityDisclosureDialog()
                    }
                }
                .setCancelable(false)
                .show()
                
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.WHITE)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateBackground()
        if (::masterSwitch.isInitialized) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val isServiceOn = isAccessibilityServiceEnabled(this, VolumePowerAccessibilityService::class.java)
            if (!isServiceOn && masterSwitch.isChecked) {
                masterSwitch.isChecked = false
                prefs.edit().putBoolean("master_toggle", false).apply()
            } else if (isServiceOn && prefs.getBoolean("master_toggle", false) != masterSwitch.isChecked) {
                masterSwitch.isChecked = prefs.getBoolean("master_toggle", false)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (!isAccessibilityServiceEnabled(this, VolumePowerAccessibilityService::class.java)) {
                showAccessibilityDisclosureDialog()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expectedComponentName = android.content.ComponentName(context, accessibilityService)
        val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }
}

class DashboardFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        view.findViewById<MaterialCardView>(R.id.card_vol_up).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, VolUpFragment())
                .addToBackStack(null)
                .commit()
        }
        
        view.findViewById<MaterialCardView>(R.id.card_vol_down).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, VolDownFragment())
                .addToBackStack(null)
                .commit()
        }
        
        view.findViewById<MaterialCardView>(R.id.card_haptics).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HapticsFragment())
                .addToBackStack(null)
                .commit()
        }
        
        view.findViewById<MaterialCardView>(R.id.card_themes).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ThemesFragment())
                .addToBackStack(null)
                .commit()
        }
        
        view.findViewById<MaterialCardView>(R.id.card_advanced).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AdvancedFragment())
                .addToBackStack(null)
                .commit()
        }
        
        return view
    }
}

abstract class BaseVolFragment : PreferenceFragmentCompat() {
    private val prefKeys = mutableListOf<String>()

    protected fun setupActionPreference(key: String) {
        prefKeys.add(key)
        val pref = findPreference<androidx.preference.Preference>(key)
        
        pref?.setOnPreferenceClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ActionSelectionFragment.newInstance(key, pref.title.toString()))
                .addToBackStack(null)
                .commit()
            true
        }
    }
    
    override fun onResume() {
        super.onResume()
        val sharedPrefs = preferenceManager.sharedPreferences
        for (key in prefKeys) {
            val pref = findPreference<androidx.preference.Preference>(key)
            val value = sharedPrefs?.getString(key, "normal") ?: "normal"
            pref?.summary = getActionName(value)
        }
    }
    
    private fun getActionName(value: String): String {
        if (value.startsWith("app:")) {
            val pkg = value.substring(4)
            try {
                val pm = requireContext().packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                return "Launch " + pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                return "Launch Custom App"
            }
        }
        val values = resources.getStringArray(R.array.action_values)
        val entries = resources.getStringArray(R.array.action_entries)
        val index = values.indexOf(value)
        return if (index >= 0) entries[index] else "Normal System Behavior"
    }
}

class VolUpFragment : BaseVolFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_vol_up, rootKey)
        setupActionPreference("up_single")
        setupActionPreference("up_long")
        setupActionPreference("up_double")
        setupActionPreference("up_off")
    }
}

class VolDownFragment : BaseVolFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_vol_down, rootKey)
        setupActionPreference("down_single")
        setupActionPreference("down_long")
        setupActionPreference("down_double")
        setupActionPreference("down_off")
    }
}

class HapticsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_haptics, rootKey)
        
        findPreference<androidx.preference.Preference>("vibrate_intensity")?.setOnPreferenceChangeListener { _, newValue ->
            try {
                val intensity = newValue as Int
                val amplitude = ((intensity * 255) / 100).coerceIn(1, 255)
                val duration = intensity.toLong()
                @Suppress("DEPRECATION")
                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                    vm.defaultVibrator
                } else {
                    requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, amplitude))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            true
        }
    }
}

data class ThemeItem(val name: String, val value: String, val assetFileName: String, val previewDrawable: android.graphics.drawable.Drawable)

class ThemesFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_themes, container, false)
        val recyclerThemes = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_themes)
        val recyclerLogos = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_logos)
        
        recyclerThemes.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
        recyclerLogos.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
        
        val items = listOf(
            ThemeItem("Lush Green & Gold", "default_gradient", "lush_green.png", android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(android.graphics.Color.parseColor("#003a32"), android.graphics.Color.parseColor("#8A9A5B"), android.graphics.Color.parseColor("#b99e00"))
            )),
            ThemeItem("Midnight Blue", "midnight_blue", "midnight_blue.png", android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(android.graphics.Color.parseColor("#0f2027"), android.graphics.Color.parseColor("#203a43"), android.graphics.Color.parseColor("#2c5364"))
            )),
            ThemeItem("Purple Nebula", "purple_nebula", "purple_nebula.png", android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(android.graphics.Color.parseColor("#1f1c2c"), android.graphics.Color.parseColor("#582456"), android.graphics.Color.parseColor("#928dab"))
            )),
            ThemeItem("Crimson Sunset", "crimson_sunset", "crimson_sunset.png", android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(android.graphics.Color.parseColor("#2b1055"), android.graphics.Color.parseColor("#75225b"), android.graphics.Color.parseColor("#b03a2e"))
            )),
            ThemeItem("Emerald Dream", "emerald_dream", "emerald_dream.png", android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(android.graphics.Color.parseColor("#000428"), android.graphics.Color.parseColor("#004e92"), android.graphics.Color.parseColor("#006633"))
            )),
            ThemeItem("Solid Pitch Black", "solid_black", "pitch_black.png", android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#000000"))),
            ThemeItem("Solid Dark Gray", "solid_gray", "dark_gray.png", android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#121212"))),
            ThemeItem("Solid Deep Navy", "solid_navy", "deep_navy.png", android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#0a192f")))
        )
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val currentTheme = prefs.getString("app_bg_theme", "default_gradient") ?: "default_gradient"
        val currentLogo = prefs.getString("app_splash_logo", "lush_green.png") ?: "lush_green.png"
        var pendingLogo = currentLogo
        
        val btnSaveLogo = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_logo)
        
        fun updateButtonState() {
            if (pendingLogo != prefs.getString("app_splash_logo", "lush_green.png")) {
                btnSaveLogo?.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E0FFFFFF"))
                btnSaveLogo?.setTextColor(android.graphics.Color.parseColor("#000000"))
                btnSaveLogo?.text = "Apply Icon Change (Unsaved)"
            } else {
                btnSaveLogo?.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#20FFFFFF"))
                btnSaveLogo?.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"))
                btnSaveLogo?.text = "Apply Icon Change"
            }
        }
        updateButtonState()
        
        recyclerThemes.adapter = ThemeAdapter(items, currentTheme) { selectedValue ->
            prefs.edit().putString("app_bg_theme", selectedValue).apply()
            (requireActivity() as MainActivity).updateBackground()
        }
        
        val logoAdapter = LogoAdapter(items, currentLogo) { selectedLogo ->
            pendingLogo = selectedLogo
            updateButtonState()
        }
        recyclerLogos.adapter = logoAdapter
        
        fun performApplyAndRestart() {
            prefs.edit().putString("app_splash_logo", pendingLogo).apply()
            val aliasName = when (pendingLogo) {
                "lush_green.png" -> "SplashActivityLushGreen"
                "midnight_blue.png" -> "SplashActivityMidnightBlue"
                "purple_nebula.png" -> "SplashActivityPurpleNebula"
                "crimson_sunset.png" -> "SplashActivityCrimsonSunset"
                "emerald_dream.png" -> "SplashActivityEmeraldDream"
                "pitch_black.png" -> "SplashActivityPitchBlack"
                "dark_gray.png" -> "SplashActivityDarkGray"
                "deep_navy.png" -> "SplashActivityDeepNavy"
                else -> "SplashActivityLushGreen"
            }
            switchSystemAppIcon(requireContext(), aliasName)
            
            val intent = Intent(requireContext(), SplashActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            requireContext().startActivity(intent)
            requireActivity().finish()
        }
        
        btnSaveLogo?.setOnClickListener {
            if (pendingLogo == prefs.getString("app_splash_logo", "lush_green.png")) {
                android.widget.Toast.makeText(requireContext(), "Icon is already active", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm App Icon Change")
                .setMessage("Are you sure you want to change the system App Icon? The application will restart to apply the changes.")
                .setPositiveButton("Restart Now") { _, _ ->
                    performApplyAndRestart()
                }
                .setNegativeButton("Cancel", null)
                .show()

            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.WHITE)
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.WHITE)
            }
        }
        
        val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val activeLogo = prefs.getString("app_splash_logo", "lush_green.png") ?: "lush_green.png"
                if (pendingLogo != activeLogo) {
                    val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Unsaved Icon Change")
                        .setMessage("You have selected a new App Icon but haven't applied it. Would you like to apply the change now or discard it?")
                        .setPositiveButton("Apply Change") { _, _ ->
                            performApplyAndRestart()
                        }
                        .setNegativeButton("Discard") { _, _ ->
                            pendingLogo = activeLogo
                            isEnabled = false
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                        .show()

                    val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.WHITE)
                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.WHITE)
                    }
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        
        return view
    }
}

class ThemeAdapter(
    private val items: List<ThemeItem>,
    private var currentTheme: String,
    private val onThemeSelected: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    class ThemeViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val cardContainer: MaterialCardView = view.findViewById(R.id.card_theme_container)
        val imagePreview: android.widget.ImageView = view.findViewById(R.id.image_theme_preview)
        val textName: TextView = view.findViewById(R.id.text_theme_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_card, parent, false)
        return ThemeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val item = items[position]
        holder.textName.text = item.name
        
        holder.imagePreview.setImageDrawable(null)
        holder.imagePreview.background = item.previewDrawable
        
        if (item.value == currentTheme) {
            holder.cardContainer.strokeColor = android.graphics.Color.parseColor("#00E676")
            holder.cardContainer.strokeWidth = 6
        } else {
            holder.cardContainer.strokeColor = android.graphics.Color.parseColor("#80FFFFFF")
            holder.cardContainer.strokeWidth = 2
        }
        
        holder.cardContainer.setOnClickListener {
            currentTheme = item.value
            onThemeSelected(item.value)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = items.size
}

class LogoAdapter(
    private val items: List<ThemeItem>,
    private var currentLogo: String,
    private val onLogoSelected: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<LogoAdapter.LogoViewHolder>() {

    class LogoViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val cardContainer: MaterialCardView = view.findViewById(R.id.card_theme_container)
        val imagePreview: android.widget.ImageView = view.findViewById(R.id.image_theme_preview)
        val textName: TextView = view.findViewById(R.id.text_theme_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_card, parent, false)
        return LogoViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogoViewHolder, position: Int) {
        val item = items[position]
        holder.textName.text = item.name
        
        holder.imagePreview.background = null
        try {
            val inputStream = holder.itemView.context.assets.open("logo/" + item.assetFileName)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            holder.imagePreview.setImageBitmap(bitmap)
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (item.assetFileName == currentLogo) {
            holder.cardContainer.strokeColor = android.graphics.Color.parseColor("#00E676")
            holder.cardContainer.strokeWidth = 6
        } else {
            holder.cardContainer.strokeColor = android.graphics.Color.parseColor("#80FFFFFF")
            holder.cardContainer.strokeWidth = 2
        }
        
        holder.cardContainer.setOnClickListener {
            currentLogo = item.assetFileName
            onLogoSelected(item.assetFileName)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = items.size
}

fun switchSystemAppIcon(context: Context, activeAliasClassName: String) {
    val packageManager = context.packageManager
    val packageName = context.packageName

    val allAliases = listOf(
        "$packageName.SplashActivityLushGreen",
        "$packageName.SplashActivityMidnightBlue",
        "$packageName.SplashActivityPurpleNebula",
        "$packageName.SplashActivityCrimsonSunset",
        "$packageName.SplashActivityEmeraldDream",
        "$packageName.SplashActivityPitchBlack",
        "$packageName.SplashActivityDarkGray",
        "$packageName.SplashActivityDeepNavy"
    )

    allAliases.forEach { alias ->
        val componentName = android.content.ComponentName(packageName, alias)
        val targetState = if (alias == "$packageName.$activeAliasClassName") {
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        
        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                targetState,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class AdvancedFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_advanced, rootKey)
        
        findPreference<androidx.preference.Preference>("open_accessibility")?.setOnPreferenceClickListener {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Settings not available", android.widget.Toast.LENGTH_SHORT).show()
            }
            true
        }

        findPreference<androidx.preference.Preference>("reset_defaults")?.setOnPreferenceClickListener {
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm Reset to Default")
                .setMessage("Are you sure you want to reset all settings to their default values? This will restore the default button mappings, haptic preferences, background theme, and system app icon. The application will restart to apply these changes.")
                .setPositiveButton("Reset Now") { _, _ ->
                    preferenceManager.sharedPreferences?.edit()
                        ?.clear()
                        ?.putBoolean("master_toggle", false)
                        ?.putBoolean("is_first_launch", true)
                        ?.apply()
                    switchSystemAppIcon(requireContext(), "SplashActivityLushGreen")
                    val intent = android.content.Intent(requireContext(), SplashActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    requireContext().startActivity(intent)
                    activity?.finish()
                }
                .setNegativeButton("Cancel", null)
                .show()

            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.WHITE)
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.WHITE)
            }
            true
        }

        findPreference<androidx.preference.Preference>("open_camera_perm")?.setOnPreferenceClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:" + requireContext().packageName)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Settings not available", android.widget.Toast.LENGTH_SHORT).show()
            }
            true
        }
        
        findPreference<androidx.preference.Preference>("open_dnd_perm")?.setOnPreferenceClickListener {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Settings not available", android.widget.Toast.LENGTH_SHORT).show()
            }
            true
        }
        
        findPreference<androidx.preference.Preference>("open_write_settings_perm")?.setOnPreferenceClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = android.net.Uri.parse("package:" + requireContext().packageName)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Settings not available", android.widget.Toast.LENGTH_SHORT).show()
            }
            true
        }

        findPreference<androidx.preference.Preference>("open_bluetooth_perm")?.setOnPreferenceClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:" + requireContext().packageName)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Settings not available", android.widget.Toast.LENGTH_SHORT).show()
            }
            true
        }
    }
}
