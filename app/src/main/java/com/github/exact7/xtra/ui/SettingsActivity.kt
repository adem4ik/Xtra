package com.github.exact7.xtra.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.github.exact7.xtra.R
import com.github.exact7.xtra.util.C
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(C.THEME, true)) R.style.DarkTheme else R.style.LightTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val activity = requireActivity()
            findPreference<SwitchPreferenceCompat>("theme").setOnPreferenceChangeListener { _, newValue ->
                activity.apply {
                    setTheme(if (newValue == true) R.style.DarkTheme else R.style.LightTheme)
                    recreate()
                }
                true
            }
            findPreference<SeekBarPreference>("chatWidth").setOnPreferenceChangeListener { _, newValue ->
                val displayMetrics = DisplayMetrics()
                activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
                val deviceLandscapeWidth = with(displayMetrics) {
                    if (heightPixels > widthPixels) heightPixels else widthPixels
                }
                val chatWidth = (deviceLandscapeWidth * (newValue as Int / 100f)).toInt()
                PreferenceManager.getDefaultSharedPreferences(context).edit { putInt(C.LANDSCAPE_CHAT_WIDTH, chatWidth) }
                activity.setResult(Activity.RESULT_OK, Intent().putExtra(C.LANDSCAPE_CHAT_WIDTH, chatWidth))
                true
            }
            findPreference<ListPreference>(C.PORTRAIT_COLUMN_COUNT).setOnPreferenceChangeListener { _, _ ->
                activity.setResult(Activity.RESULT_OK, Intent().putExtra("shouldRecreate", activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT))
                true
            }
            findPreference<ListPreference>(C.LANDSCAPE_COLUMN_COUNT).setOnPreferenceChangeListener { _, _ ->
                activity.setResult(Activity.RESULT_OK, Intent().putExtra("shouldRecreate", activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE))
                true
            }
        }
    }
}