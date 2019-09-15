package com.github.exact7.xtra.ui.common

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crashlytics.android.Crashlytics
import com.github.exact7.xtra.repository.LoadingState
import com.github.exact7.xtra.util.gone
import kotlinx.android.synthetic.main.common_recycler_view_layout.*

abstract class PagedListFragment<T, VM : PagedListViewModel<T>> : BaseNetworkFragment() {

    protected lateinit var viewModel: VM
    protected lateinit var adapter: BasePagedListAdapter<T>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = createViewModel()
        adapter = createAdapter().apply {
            registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    try {
                        if (isResumed) {
                            recyclerView.post {
                                recyclerView?.let {
                                    if (positionStart < (it.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()) {
                                        it.scrollToPosition(0)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Crashlytics.logException(e)
                    }
                }
            })
        }
        recyclerView.adapter = adapter
    }

    override fun initialize() {
        viewModel.list.observe(viewLifecycleOwner, Observer {
            adapter.submitList(it)
            nothing_here.isVisible = it.isEmpty()
        })
        viewModel.loadingState.observe(viewLifecycleOwner, Observer {
            val isLoading = it == LoadingState.LOADING
            val isListEmpty = adapter.currentList.isNullOrEmpty()
            if (isLoading) {
                nothing_here.gone()
            }
            progressBar.isVisible = isLoading && isListEmpty
            if (swipeRefresh.isEnabled) {
                swipeRefresh.isRefreshing = isLoading && !isListEmpty
            }
        })
        viewModel.pagingState.observe(viewLifecycleOwner, Observer(adapter::setPagingState))
        if (swipeRefresh.isEnabled) {
            swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        }
    }

    override fun onNetworkRestored() {
        viewModel.retry()
    }

    protected abstract fun createAdapter(): BasePagedListAdapter<T>
    protected abstract fun createViewModel(): VM
}