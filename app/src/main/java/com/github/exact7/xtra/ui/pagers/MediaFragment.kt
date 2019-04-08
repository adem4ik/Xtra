package com.github.exact7.xtra.ui.pagers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import com.github.exact7.xtra.R
import com.github.exact7.xtra.ui.common.Scrollable
import com.github.exact7.xtra.ui.main.MainActivity
import kotlinx.android.synthetic.main.common_recycler_view_layout.*
import kotlinx.android.synthetic.main.fragment_media.*


abstract class MediaFragment : Fragment(), Scrollable {

    private var previousItem = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previousItem = savedInstanceState?.getInt("previousItem") ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != previousItem) {
                    childFragmentManager.beginTransaction().replace(R.id.fragmentContainer, onSpinnerItemSelected(position)).commit()
                    previousItem = position
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        search.setOnClickListener { (requireActivity() as MainActivity).openSearch() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("previousItem", previousItem)
        super.onSaveInstanceState(outState)
    }

    abstract fun onSpinnerItemSelected(position: Int): Fragment

    override fun scrollToTop() {
        recyclerView.scrollToPosition(0)
    }
}