package risk.tech.volumebuttons

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

class ActionSelectionFragment : Fragment() {
    private var prefKey: String = ""
    private var prefTitle: String = ""

    companion object {
        fun newInstance(prefKey: String, prefTitle: String): ActionSelectionFragment {
            val fragment = ActionSelectionFragment()
            val args = Bundle()
            args.putString("PREF_KEY", prefKey)
            args.putString("PREF_TITLE", prefTitle)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefKey = arguments?.getString("PREF_KEY") ?: ""
        prefTitle = arguments?.getString("PREF_TITLE") ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_action_selection, container, false)
        view.findViewById<TextView>(R.id.text_title).text = prefTitle

        val containerActions = view.findViewById<LinearLayout>(R.id.container_actions)
        
        val values = resources.getStringArray(R.array.action_values)
        val entries = resources.getStringArray(R.array.action_entries)

        val genActions = listOf("none", "normal", "back", "home", "recents", "lock", "screenshot", "voice_command", "power", "web_search")
        val sysCtrl = listOf("wifi", "bluetooth", "dnd", "flashlight", "auto_rotate", "auto_brightness", "brightness_plus", "brightness_minus", "brightness_0", "brightness_50", "brightness_100", "notifications", "quick_settings")
        val mediaCtrl = listOf("volume_control", "volume_up", "volume_down", "media_next", "media_previous", "media_play_pause", "doomscroll_up", "doomscroll_down")
        val appCtrl = listOf("launch_this_app", "pause_app_10s", "close_all_apps", "camera", "dialer", "browser", "settings")

        addCategory(inflater, containerActions, "Actions", genActions, entries, values)
        addCategory(inflater, containerActions, "System", sysCtrl, entries, values)
        addCategory(inflater, containerActions, "Media", mediaCtrl, entries, values)
        addCategory(inflater, containerActions, "Applications", appCtrl, entries, values)

        val customAppCard = inflater.inflate(R.layout.item_action_card, containerActions, false) as MaterialCardView
        customAppCard.findViewById<TextView>(R.id.text_action_name).text = "Select Custom App..."
        customAppCard.setOnClickListener {
            showCustomAppPicker()
        }
        containerActions.addView(customAppCard)

        return view
    }

    private fun showCustomAppPicker() {
        val pm = requireContext().packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        
        val appsList = pm.queryIntentActivities(intent, 0)
        appsList.sortBy { it.loadLabel(pm).toString() }
        
        val adapter = object : android.widget.ArrayAdapter<android.content.pm.ResolveInfo>(requireContext(), android.R.layout.select_dialog_item, android.R.id.text1, appsList) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)
                val appInfo = getItem(position)
                textView.text = appInfo?.loadLabel(pm)
                
                val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    textView.setTextColor(android.graphics.Color.WHITE)
                } else {
                    textView.setTextColor(android.graphics.Color.BLACK)
                }

                val icon = appInfo?.loadIcon(pm)
                if (icon != null) {
                    val size = (32 * context.resources.displayMetrics.density).toInt()
                    icon.setBounds(0, 0, size, size)
                    textView.setCompoundDrawables(icon, null, null, null)
                    textView.compoundDrawablePadding = (16 * context.resources.displayMetrics.density).toInt()
                }
                return view
            }
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select App")
            .setAdapter(adapter) { _, which ->
                val pkg = appsList[which].activityInfo.packageName
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putString(prefKey, "app:$pkg").apply()
                parentFragmentManager.popBackStack()
            }
            .show()
    }

    private fun addCategory(inflater: LayoutInflater, container: LinearLayout, title: String, keys: List<String>, allEntries: Array<String>, allValues: Array<String>) {
        val titleView = TextView(requireContext()).apply {
            text = title
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.colorAccent))
            setPadding(24, 24, 24, 8)
            isAllCaps = true
        }
        container.addView(titleView)

        for (key in keys) {
            val index = allValues.indexOf(key)
            if (index >= 0) {
                val card = inflater.inflate(R.layout.item_action_card, container, false) as MaterialCardView
                card.findViewById<TextView>(R.id.text_action_name).text = allEntries[index]
                
                card.setOnClickListener {
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit().putString(prefKey, key).apply()
                    parentFragmentManager.popBackStack()
                }
                container.addView(card)
            }
        }
    }
}
