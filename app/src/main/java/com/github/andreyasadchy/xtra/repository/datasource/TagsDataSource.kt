package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository

class TagsDataSource(
    private val gqlHeaders: Map<String, String>,
    private val getGameTags: Boolean,
    private val query: String,
    private val api: GraphQLRepository,
) : PagingSource<Int, Tag>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Tag> {
        return try {
            val response = query.takeIf { it.isNotBlank() }?.let {
                if (getGameTags) {
                    api.loadGameTags(gqlHeaders, query, 100).data?.searchCategoryTags?.map {
                        Tag(
                            id = it.id,
                            name = it.localizedName,
                            scope = it.scope
                        )
                    }
                } else {
                    api.loadFreeformTags(gqlHeaders, query, 100).data?.searchFreeformTags?.edges?.map { edge ->
                        Tag(
                            name = edge.node.tagName
                        )
                    }
                }
            } ?: emptyList()
            LoadResult.Page(
                data = response,
                prevKey = null,
                nextKey = null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Tag>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
