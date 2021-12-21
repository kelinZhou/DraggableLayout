package com.kelin.draggablelayoutdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = TestAdapter(getDataList("我是条目"))
        rvList2.layoutManager = LinearLayoutManager(this)
        rvList2.adapter = TestAdapter(getDataList("我是拖拽列表的条目"))
        rvList2.requestDisallowInterceptTouchEvent(true)
//        tvRoot.setOnClickListener { dlRoot.scrollToOpen() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_operation, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return if (item?.itemId == R.id.menu_operation) {
            dlRoot.smoothOpen()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun getDataList(title: String): List<String> {
        val list = ArrayList<String>()
        for (i: Int in 1..100) {
            list.add("$title $i")
        }
        return list
    }

    inner class TestAdapter(private val dataList: List<String>) : RecyclerView.Adapter<TestViewHolder>() {

        override fun getItemViewType(position: Int) = R.layout.item_test

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TestViewHolder(parent, viewType)

        override fun getItemCount() = dataList.size

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
            holder.onBindViewHolder(dataList[position])
        }
    }

    inner class TestViewHolder(parent: ViewGroup, @LayoutRes layoutRes: Int) : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)) {

        fun onBindViewHolder(text: String) {
            itemView.findViewById<TextView>(R.id.tvText).text = text
        }
    }
}
