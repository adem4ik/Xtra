package com.github.andreyasadchy.xtra.model.helix.stream

import android.os.Parcelable
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Stream(
        val id: String = "",
        val user_id: String,
        val user_login: String,
        val user_name: String,
        val game_id: String = "",
        val game_name: String = "",
        val type: String = "",
        val title: String = "",
        val viewer_count: Int = 0,
        val started_at: String = "",
        val language: String = "",
        val thumbnail_url: String = "",
        var profileImageURL: String = "") : Parcelable {

        val thumbnail: String
                get() = TwitchApiHelper.getTemplateUrl(thumbnail_url, "video")
        val channelLogo: String
                get() = TwitchApiHelper.getTemplateUrl(profileImageURL, "profileimage")
}