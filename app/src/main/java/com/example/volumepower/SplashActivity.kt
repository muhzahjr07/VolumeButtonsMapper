package risk.tech.volumebuttons

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
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
        findViewById<android.view.View>(R.id.splash_root).background = drawable

        val logoFileName = prefs.getString("app_splash_logo", "lush_green.png") ?: "lush_green.png"
        try {
            val inputStream = assets.open("logo/$logoFileName")
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            findViewById<android.widget.ImageView>(R.id.logo).setImageBitmap(bitmap)
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val iconResId = when (logoFileName) {
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

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2000) // 2 seconds delay
    }
}
