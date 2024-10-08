package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.github.andreyasadchy.xtra.UserFollowedUsersQuery
import com.github.andreyasadchy.xtra.UsersLastBroadcastQuery
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.FollowOrderEnum
import com.github.andreyasadchy.xtra.model.ui.FollowSortEnum
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File

class FollowedChannelsDataSource(
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val offlineRepository: OfflineRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val okHttpClient: OkHttpClient,
    private val coroutineScope: CoroutineScope,
    private val filesDir: String,
    private val userId: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlApi: GraphQLRepository,
    private val apolloClient: ApolloClient,
    private val checkIntegrity: Boolean,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    private val sort: FollowSortEnum,
    private val order: FollowOrderEnum) : PagingSource<Int, User>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, User> {
        return try {
            val response = try {
                if (!offset.isNullOrBlank()) {
                    loadRange()
                } else {
                    val list = mutableListOf<User>()
                    for (i in localFollowsChannel.loadFollows().let { if (order == FollowOrderEnum.ASC) it.asReversed() else it }) {
                        list.add(User(channelId = i.userId, channelLogin = i.userLogin, channelName = i.userName, profileImageUrl = i.channelLogo, followLocal = true))
                    }
                    val remote = try {
                        when (apiPref.elementAt(0)?.second) {
                            C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad() } else throw Exception()
                            C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad() } else throw Exception()
                            C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL; gqlLoad() } else throw Exception()
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                        try {
                            when (apiPref.elementAt(1)?.second) {
                                C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad() } else throw Exception()
                                C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad() } else throw Exception()
                                C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL; gqlLoad() } else throw Exception()
                                else -> throw Exception()
                            }
                        } catch (e: Exception) {
                            if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                            try {
                                when (apiPref.elementAt(2)?.second) {
                                    C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad() } else throw Exception()
                                    C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad() } else throw Exception()
                                    C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL; gqlLoad() } else throw Exception()
                                    else -> throw Exception()
                                }
                            } catch (e: Exception) {
                                if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                                listOf()
                            }
                        }
                    }
                    if (remote.isNotEmpty()) {
                        for (i in remote) {
                            val item = list.find { it.channelId == i.channelId }
                            if (item == null) {
                                i.followAccount = true
                                list.add(i)
                            } else {
                                item.followAccount = true
                                item.followedAt = i.followedAt
                                item.lastBroadcast = i.lastBroadcast
                            }
                        }
                    }
                    val allIds = mutableListOf<String>()
                    for (i in list) {
                        if (i.profileImageUrl == null || i.profileImageUrl?.contains("image_manager_disk_cache") == true || i.lastBroadcast == null) {
                            i.channelId?.let { allIds.add(it) }
                        }
                    }
                    if (allIds.isNotEmpty()) {
                        for (ids in allIds.chunked(100)) {
                            val get1 = apolloClient.newBuilder().apply {
                                gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
                            }.build().query(UsersLastBroadcastQuery(Optional.Present(ids))).execute()
                            get1.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                            val get = get1.data?.users
                            if (get != null) {
                                for (user in get) {
                                    val item = list.find { it.channelId == user?.id }
                                    if (item != null) {
                                        if (item.followLocal) {
                                            if (item.profileImageUrl == null || item.profileImageUrl?.contains("image_manager_disk_cache") == true) {
                                                updateLocalUser(item.channelId, user?.profileImageURL)
                                            }
                                        } else {
                                            if (item.profileImageUrl == null) {
                                                item.profileImageUrl = user?.profileImageURL
                                            }
                                        }
                                        item.lastBroadcast = user?.lastBroadcast?.startedAt?.toString()
                                    }
                                }
                            }
                        }
                    }
                    if (order == FollowOrderEnum.ASC) {
                        when (sort) {
                            FollowSortEnum.FOLLOWED_AT -> list.sortedWith(compareBy(nullsLast()) { it.followedAt })
                            FollowSortEnum.LAST_BROADCAST -> list.sortedWith(compareBy(nullsLast()) { it.lastBroadcast })
                            else -> list.sortedWith(compareBy(nullsLast()) { it.channelLogin })
                        }
                    } else {
                        when (sort) {
                            FollowSortEnum.FOLLOWED_AT -> list.sortedWith(compareByDescending(nullsFirst()) { it.followedAt })
                            FollowSortEnum.LAST_BROADCAST -> list.sortedWith(compareByDescending(nullsFirst()) { it.lastBroadcast })
                            else -> list.sortedWith(compareByDescending(nullsFirst()) { it.channelLogin })
                        }
                    }
                }
            } catch (e: Exception) {
                if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                listOf()
            }
            LoadResult.Page(
                data = response,
                prevKey = null,
                nextKey = if (!offset.isNullOrBlank() && (api == C.HELIX || nextPage)) {
                    nextPage = false
                    (params.key ?: 1) + 1
                } else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun helixLoad(): List<User> {
        val get = helixApi.getUserFollows(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            userId = userId,
            limit = 100,
            offset = offset
        )
        offset = get.cursor
        return get.data
    }

    private suspend fun gqlQueryLoad(): List<User> {
        val get2 = apolloClient.newBuilder().apply {
            gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
        }.build().query(UserFollowedUsersQuery(
            first = Optional.Present(100),
            after = Optional.Present(offset)
        )).execute()
        get2.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        val get1 = get2.data!!.user!!.follows!!
        val get = get1.edges!!
        val list = mutableListOf<User>()
        for (i in get) {
            list.add(User(
                channelId = i?.node?.id,
                channelLogin = i?.node?.login,
                channelName = i?.node?.displayName,
                followedAt = i?.followedAt?.toString(),
                lastBroadcast = i?.node?.lastBroadcast?.startedAt?.toString(),
                profileImageUrl = i?.node?.profileImageURL,
            ))
        }
        offset = get.lastOrNull()?.cursor?.toString()
        nextPage = get1.pageInfo?.hasNextPage ?: true
        return list
    }

    private suspend fun gqlLoad(): List<User> {
        val get = gqlApi.loadFollowedChannels(gqlHeaders, 100, offset)
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    private suspend fun loadRange(): List<User> {
        val list = if (!offset.isNullOrBlank()) {
            when (api) {
                C.HELIX -> helixLoad()
                C.GQL_QUERY -> if (nextPage) gqlQueryLoad() else listOf()
                C.GQL -> if (nextPage) gqlLoad() else listOf()
                else -> listOf()
            }
        } else listOf()
        val allIds = mutableListOf<String>()
        for (i in list) {
            if (i.profileImageUrl == null || i.lastBroadcast == null) {
                i.channelId?.let { allIds.add(it) }
            }
        }
        if (allIds.isNotEmpty()) {
            for (ids in allIds.chunked(100)) {
                val get1 = apolloClient.newBuilder().apply {
                    gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
                }.build().query(UsersLastBroadcastQuery(Optional.Present(ids))).execute()
                get1.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                val get = get1.data?.users
                if (get != null) {
                    for (user in get) {
                        val item = list.find { it.channelId == user?.id }
                        if (item != null) {
                            if (item.followLocal) {
                                if (item.profileImageUrl == null || item.profileImageUrl?.contains("image_manager_disk_cache") == true) {
                                    updateLocalUser(item.channelId, user?.profileImageURL)
                                }
                            } else {
                                if (item.profileImageUrl == null) {
                                    item.profileImageUrl = user?.profileImageURL
                                }
                            }
                            item.lastBroadcast = user?.lastBroadcast?.startedAt?.toString()
                        }
                    }
                }
            }
        }
        return list
    }

    private fun updateLocalUser(userId: String?, profileImageURL: String?) {
        if (!userId.isNullOrBlank()) {
            coroutineScope.launch {
                profileImageURL.takeIf { !it.isNullOrBlank() }?.let { TwitchApiHelper.getTemplateUrl(it, "profileimage") }?.let {
                    File(filesDir, "profile_pics").mkdir()
                    val path = filesDir + File.separator + "profile_pics" + File.separator + userId
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                if (response.isSuccessful) {
                                    File(path).sink().buffer().use { sink ->
                                        sink.writeAll(response.body.source())
                                    }
                                }
                            }
                        } catch (e: Exception) {

                        }
                    }
                    path
                }?.let { downloadedLogo ->
                    localFollowsChannel.getFollowByUserId(userId)?.let {
                        localFollowsChannel.updateFollow(it.apply {
                            channelLogo = downloadedLogo
                        })
                    }
                    offlineRepository.getVideosByUserId(userId).forEach {
                        offlineRepository.updateVideo(it.apply {
                            channelLogo = downloadedLogo
                        })
                    }
                    bookmarksRepository.getBookmarksByUserId(userId).forEach {
                        bookmarksRepository.updateBookmark(it.apply {
                            userLogo = downloadedLogo
                        })
                    }
                }
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, User>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
