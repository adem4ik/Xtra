package com.github.exact7.xtra.model.game

import com.google.gson.annotations.SerializedName

class GamesResponse(
    @SerializedName("_total")
    val total: Int,
    @SerializedName("top")
    val games: List<Game>)
