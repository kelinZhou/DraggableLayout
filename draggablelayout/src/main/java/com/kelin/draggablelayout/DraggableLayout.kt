/*
 *
 *  * sufly0001@gmail.com Modify the code to enhance the ease of use.
 *  *
 *  * Copyright (C) 2015 Ted xiong-wei@hotmail.com
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package com.kelin.draggablelayout

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.support.annotation.IntDef
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Scroller


/**
 * **描述:** 可拖拽布局。
 *
 * **创建人:** kelin
 *
 * **创建时间:** 2018/10/27  下午4:01
 *
 * **版本:** v 1.0.0
 */
class DraggableLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : FrameLayout(context, attrs, defStyle) {

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(STATUS_OPENED, STATUS_CLOSED, STATUS_MIDDLE)
    annotation class Status

    private val spaceInfo: SpaceInfo
    private var initialStatus = STATUS_CLOSED
    private var contentViewId = View.NO_ID
    private var hasMiddleStatus = false
    private var currentInnerStatus = InnerStatus.MIDDLE
    private var contentView: View? = null
    private var onScrollChangedListener: OnScrollChangedListener? = null

    private var lastY = 0F
    private var lastDownX = 0F
    private var lastDownY = 0F

    private var isDraggable = true

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.DraggableLayout)
            if (a != null) {
                spaceInfo = SpaceInfo(
                        a.getString(R.styleable.DraggableLayout_minRemainingSpace) ?: "",
                        a.getString(R.styleable.DraggableLayout_middleRemainingSpace) ?: "",
                        a.getString(R.styleable.DraggableLayout_handleSize) ?: ""
                )
                initialStatus = a.getInteger(R.styleable.DraggableLayout_initStatus, initialStatus)
                contentViewId = a.getResourceId(R.styleable.DraggableLayout_contentViewId, contentViewId)
                hasMiddleStatus = a.getBoolean(R.styleable.DraggableLayout_hasMiddleStatus, hasMiddleStatus)
                a.recycle()
            } else {
                spaceInfo = SpaceInfo("", "", "")
            }
        } else {
            spaceInfo = SpaceInfo("", "", "")
        }
    }

    @Suppress("ObsoleteSdkInt")
    private val scroller by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            Scroller(getContext(), null, true)
        } else {
            Scroller(getContext())
        }
    }

    private val associatedListViewListener by lazy {
        object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                updateListViewScrollState(view)
            }

            override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                updateListViewScrollState(view)
            }
        }
    }

    private val associatedRecyclerViewListener by lazy {
        object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                updateRecyclerViewScrollState(recyclerView)
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateRecyclerViewScrollState(recyclerView)
            }
        }
    }

    @get:Status val currentStatus: Int
        get() = when (currentInnerStatus) {
            InnerStatus.OPENED -> STATUS_OPENED
            InnerStatus.CLOSED -> STATUS_CLOSED
            else -> STATUS_MIDDLE
        }


    private val gestureDetector by lazy {
        GestureDetector(getContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(me1: MotionEvent, me2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (velocityY > SLOP_FLING_VELOCITY) {
                    if (-scrollY > spaceInfo.middleSpace) {
                        smoothClose()
                    } else {
                        if (hasMiddleStatus) {
                            smoothMiddle()
                        } else {
                            smoothClose()
                        }
                    }
                    return true
                } else if (!hasMiddleStatus || velocityY < SLOP_FLING_VELOCITY && scrollY > -spaceInfo.middleSpace) {
                    smoothOpen()
                    return true
                } else if (velocityY < SLOP_FLING_VELOCITY && scrollY <= -spaceInfo.maxSpace) {
                    smoothMiddle()
                    return true
                }
                return false
            }
        })
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val cv = findViewById<View>(contentViewId)
        if (cv != null) {
            contentView = cv
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        spaceInfo.measure(resources, h)
        getChildAt(0).layoutParams.height = h - spaceInfo.minSpace
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (initialStatus != STATUS_NONE) {
            when (initialStatus) {
                STATUS_OPENED -> open()
                STATUS_MIDDLE -> middle()
                STATUS_CLOSED -> close()
            }
            initialStatus = STATUS_NONE
        }
        findContentView(this)
    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, y)
        if (spaceInfo.maxSpace == spaceInfo.minSpace) {
            return
        }
        if (-y <= spaceInfo.maxSpace) {
            val progress = (-y - spaceInfo.minSpace).toFloat() / (spaceInfo.maxSpace - spaceInfo.minSpace)
            onScrollProgressChanged(progress)
        } else {
            val progress = (-y - spaceInfo.maxSpace).toFloat() / (spaceInfo.maxSpace - spaceInfo.maxSpace)
            onScrollProgressChanged(progress)
        }
        if (y == -spaceInfo.minSpace) {
            if (currentInnerStatus != InnerStatus.OPENED) {
                currentInnerStatus = InnerStatus.OPENED
                onScrollFinished(STATUS_OPENED)
            }
        } else if (y == -spaceInfo.maxSpace) {
            if (currentInnerStatus != InnerStatus.MIDDLE) {
                currentInnerStatus = InnerStatus.MIDDLE
                onScrollFinished(STATUS_MIDDLE)
            }
        } else if (y == -spaceInfo.maxSpace) {
            if (currentInnerStatus != InnerStatus.CLOSED) {
                currentInnerStatus = InnerStatus.CLOSED
                onScrollFinished(STATUS_CLOSED)
            }
        }
    }

    override fun computeScroll() {
        if (!scroller.isFinished && scroller.computeScrollOffset()) {
            val currY = scroller.currY
            scrollTo(0, currY)
            if (currY == -spaceInfo.minSpace || currY == -spaceInfo.maxSpace || currY == -spaceInfo.maxSpace) {
                scroller.abortAnimation()
            } else {
                invalidate()
            }
        }
    }


    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        if (y < Math.abs(scrollY) || y > bottom || !isDraggable && currentInnerStatus == InnerStatus.OPENED && !isInHandlerView(x, y)) {
            return false
        } else {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastDownX = x
                    lastY = y
                    lastDownY = lastY
                    if (currentInnerStatus == InnerStatus.CLOSED || currentInnerStatus == InnerStatus.MIDDLE || !isInContentView(x, y)) {
                        currentInnerStatus = InnerStatus.MOVING
                        return true
                    } else if (!scroller.isFinished) {
                        scroller.forceFinished(true)
                        currentInnerStatus = InnerStatus.MOVING
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentInnerStatus == InnerStatus.MOVING) {
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = (y - lastDownY).toInt()
                    if (Math.abs(deltaY) < SLOP_MOTION_DISTANCE) {
                        return false
                    }
                    if (currentInnerStatus == InnerStatus.OPENED) {
                        if (deltaY < 0) {
                            return false
                        }
                    } else if (currentInnerStatus == InnerStatus.MIDDLE) {
                        if (deltaY > 0) {
                            return false
                        }
                    }
                    return true
                }
                else -> return false
            }
            return false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (currentInnerStatus != InnerStatus.MOVING && (event.y < Math.abs(scrollY) || event.y > bottom)) {
            return false
        } else {
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val space = lastY - event.y
                    val targetY = scrollY + space
                    return if (space != 0F && !disposeEdgeValue(space)) {
                        currentInnerStatus = InnerStatus.MOVING
                        if (targetY > -spaceInfo.minSpace) {
                            scrollTo(0, -spaceInfo.minSpace)
                        } else if (targetY < -spaceInfo.maxSpace) {
                            scrollTo(0, -spaceInfo.maxSpace)
                        } else if (spaceInfo.maxSpace > 0 && targetY < -spaceInfo.maxSpace) {
                            scrollTo(0, -spaceInfo.maxSpace)
                        } else {
                            scrollBy(0, (lastY - event.y).toInt())
                        }
                        lastY = event.y
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (currentInnerStatus == InnerStatus.MOVING) {
                    completeMove()
                    return true
                }
                else -> return false
            }
            return false
        }
    }

    private fun isInHandlerView(x: Float, y: Float): Boolean {
        val inner = getChildAt(0)
        val cv = contentView
        return cv != null && x > inner.left && x < inner.right && y > spaceInfo.minSpace && y < cv.top + spaceInfo.minSpace
    }

    /**
     * 根据一个坐标，用来判断当前坐标是否在内部的有事件冲突的View上。
     *
     * @param x x坐标。
     * @param y y坐标。
     */
    private fun isInContentView(x: Float, y: Float): Boolean {
        val cv = contentView
        return cv != null && x > cv.left && x < cv.right && y > cv.top + spaceInfo.minSpace && y < cv.bottom
    }

    private fun disposeEdgeValue(space: Float): Boolean {
        return space > 0 && scrollY >= -spaceInfo.minSpace || space < 0 && scrollY <= -spaceInfo.maxSpace
    }

    private fun completeMove() {
        val middleValue = -((spaceInfo.maxSpace - spaceInfo.minSpace) * THRESHOLD_SCROLL_TO_MIDDLE)
        if (scrollY > middleValue) {
            smoothOpen()
        } else {
            val closeValue = -((spaceInfo.maxSpace - spaceInfo.maxSpace) * THRESHOLD_SCROLL_TO_CLOSE + spaceInfo.maxSpace)
            if (scrollY <= middleValue && scrollY > closeValue) {
                if (hasMiddleStatus) {
                    smoothMiddle()
                } else {
                    smoothClose()
                }
            } else {
                smoothClose()
            }
        }
    }

    private fun onScrollFinished(@Status status: Int) {
        onScrollChangedListener?.onScrollFinished(status)
    }

    private fun onScrollProgressChanged(progress: Float) {
        onScrollChangedListener?.onScrollProgressChanged(progress)
    }

    private fun findContentView(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            when (val child = parent.getChildAt(i)) {
                is AbsListView -> setAssociatedListView(child)
                is RecyclerView -> setAssociatedRecyclerView(child)
                is DraggableScrollView -> setAssociatedScrollView(child)
                is ScrollView -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        setAssociatedView(child)
                    } else {
                        throw RuntimeException("Not support ScrollView, when your sdk version lower Android M.")
                    }
                }
                is ViewGroup -> findContentView(child)
            }
        }
    }

    private fun setAssociatedListView(listView: AbsListView) {
        if (contentView == null) {
            contentView = listView
        }
        listView.setOnScrollListener(associatedListViewListener)
        updateListViewScrollState(listView)
    }

    private fun setAssociatedRecyclerView(recyclerView: RecyclerView) {
        if (contentView == null) {
            contentView = recyclerView
        }
        recyclerView.addOnScrollListener(associatedRecyclerViewListener)
        updateRecyclerViewScrollState(recyclerView)
    }

    private fun setAssociatedScrollView(scrollView: DraggableScrollView) {
        if (contentView == null) {
            this.contentView = scrollView
        }
        scrollView.isScrollbarFadingEnabled = false
        scrollView.setOnScrollChangeListener { sv, _, oldT ->
            onScrollChangedListener?.onChildScroll(oldT)
            if (sv.scrollY == 0) {
                setDraggable(true)
            } else {
                setDraggable(false)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun setAssociatedView(view: View) {
        if (contentView == null) {
            this.contentView = view
        }
        view.isScrollbarFadingEnabled = false
        view.setOnScrollChangeListener { sv, _, _, _, oldT ->
            onScrollChangedListener?.onChildScroll(oldT)
            if (sv.scrollY == 0) {
                setDraggable(true)
            } else {
                setDraggable(false)
            }
        }
    }

    private fun updateListViewScrollState(listView: AbsListView) {
        if (listView.childCount == 0) {
            setDraggable(true)
        } else {
            if (listView.firstVisiblePosition == 0) {
                val firstChild = listView.getChildAt(0)
                if (firstChild.top == listView.paddingTop) {
                    setDraggable(true)
                    return
                }
            }
            setDraggable(false)
        }
    }

    private fun updateRecyclerViewScrollState(recyclerView: RecyclerView) {
        if (recyclerView.childCount == 0) {
            setDraggable(true)
        } else {
            val layoutManager = recyclerView.layoutManager
            var i = IntArray(1)
            if (layoutManager is LinearLayoutManager || layoutManager is GridLayoutManager) {
                i[0] = (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            } else if (layoutManager is StaggeredGridLayoutManager) {
                i = layoutManager.findFirstVisibleItemPositions(i)
            }
            if (i[0] == 0) {
                val firstChild = recyclerView.getChildAt(0)
                if (firstChild.top == recyclerView.paddingTop) {
                    setDraggable(true)
                    return
                }
            }
            setDraggable(false)
        }
    }

    fun setDraggable(draggable: Boolean) {
        this.isDraggable = draggable
    }

    /**
     * 滚动布局打开关闭,关闭否则滚动.
     */
    fun showOrHide() {
        if (currentInnerStatus == InnerStatus.MIDDLE) {
            smoothOpen()
        } else if (currentInnerStatus == InnerStatus.OPENED) {
            if (hasMiddleStatus) {
                smoothMiddle()
            } else {
                smoothClose()
            }
        }
    }

    /**
     * 滚动布局开放,spaceInfo.maxOffset之后向下滚动.
     */
    fun smoothMiddle() {
        if (currentInnerStatus == InnerStatus.MIDDLE) {
            return
        }
        if (spaceInfo.maxSpace == spaceInfo.middleSpace) {
            return
        }
        val dy = -scrollY - spaceInfo.middleSpace
        if (dy == 0) {
            return
        }
        currentInnerStatus = InnerStatus.SCROLLING
        initialStatus = STATUS_MIDDLE
        val duration = MIN_SCROLL_DURATION + Math.abs((MAX_SCROLL_DURATION - MIN_SCROLL_DURATION) * dy / (spaceInfo.maxSpace - spaceInfo.minSpace))
        scroller.startScroll(0, scrollY, 0, dy, duration)
        invalidate()
    }

    /**
     * 滚动的布局来关闭,滚动到offsetInfo.minSpace.
     */
    fun smoothOpen() {
        if (currentInnerStatus == InnerStatus.OPENED) {
            return
        }
        if (spaceInfo.maxSpace == spaceInfo.minSpace) {
            return
        }
        val dy = -scrollY - spaceInfo.minSpace
        if (dy == 0) {
            return
        }
        currentInnerStatus = InnerStatus.SCROLLING
        initialStatus = STATUS_OPENED
        val duration = MIN_SCROLL_DURATION + Math.abs((MAX_SCROLL_DURATION - MIN_SCROLL_DURATION) * dy / (spaceInfo.maxSpace - spaceInfo.minSpace))
        scroller.startScroll(0, scrollY, 0, dy, duration)
        invalidate()
    }

    /**
     * 滚动布局退出
     */
    fun smoothClose() {
        val dy = -scrollY - spaceInfo.maxSpace
        if (currentInnerStatus != InnerStatus.CLOSED && spaceInfo.middleSpace != spaceInfo.maxSpace && dy != 0) {
            currentInnerStatus = InnerStatus.SCROLLING
            initialStatus = STATUS_CLOSED
            val duration = MIN_SCROLL_DURATION + Math.abs((MAX_SCROLL_DURATION - MIN_SCROLL_DURATION) * dy / (spaceInfo.maxSpace - spaceInfo.minSpace))
            scroller.startScroll(0, scrollY, 0, dy, duration)
            invalidate()
        }
    }

    /**
     * 初始化布局开放,没有动画。
     */
    fun middle() {
        scrollTo(0, -spaceInfo.middleSpace)
        currentInnerStatus = InnerStatus.MIDDLE
        initialStatus = STATUS_MIDDLE
    }

    /**
     * 初始化布局关闭,没有动画。
     */
    fun open() {
        scrollTo(0, -spaceInfo.minSpace)
        currentInnerStatus = InnerStatus.OPENED
        initialStatus = STATUS_OPENED
    }

    /**
     * 初始化布局,退出,没有动画。
     */
    fun close() {
        scrollTo(0, -spaceInfo.maxSpace)
        currentInnerStatus = InnerStatus.CLOSED
        initialStatus = STATUS_CLOSED
    }

    companion object {
        /**
         * 表示当前拖拽把手的布局方式为dp。
         */
        private const val MODE_DP = 0x1000_0000
        /**
         * 表示当前拖拽把手的布局方式为自身百分比。
         */
        private const val MODE_PERCENT = 0x2000_0000
        /**
         * 表示没有设置拖拽把手的布局方式及大小。
         */
        private const val DRAG_HANDLE_BEGIN_OR_END_NOT_SET = 0x0000_0000
        /**
         * dragHandleSize的value值所支持的单位。
         */
        private val UNIT = arrayOf("dp", "%")

        /**
         * 把手参数的数值限定。
         */
        private const val VALUE_QUALIFIED = 0x0FFF_FFFF;
        /**
         * 把手参数的模式限定。
         */
        private const val MODE_QUALIFIED = 0x7000_0000;
        /**
         * 最大滚动时长。
         */
        private const val MAX_SCROLL_DURATION = 400
        /**
         * 最小滚动时长。
         */
        private const val MIN_SCROLL_DURATION = 100
        private const val SLOP_FLING_VELOCITY = 80
        private const val SLOP_MOTION_DISTANCE = 10
        private const val THRESHOLD_SCROLL_TO_MIDDLE = 0.5f
        private const val THRESHOLD_SCROLL_TO_CLOSE = 0.8f

        /**
         * 表示当前的状态为没有任何状态。可能是因为View刚刚被创建的原因，所以没有任何状态。
         */
        private const val STATUS_NONE = 0x0000
        /**
         * 表示当前状态为中间状态，也就是抽屉刚刚打开一半的时候。
         */
        const val STATUS_MIDDLE = 0x0001
        /**
         * 表示当前状态为打开状态，也就是抽屉完全被打开了。
         */
        const val STATUS_OPENED = 0x0002
        /**
         * 表示当前状态为关闭状态，也就是说抽屉完全被关闭了。除了把手外，其他的内容是不可见的。
         */
        const val STATUS_CLOSED = 0x0003
    }

    private enum class InnerStatus {
        CLOSED, MIDDLE, OPENED, MOVING, SCROLLING
    }

    /**
     * DraggableLayout的滚动监听。
     */
    interface OnScrollChangedListener {
        /**
         * This is called when the scrolling.
         *
         * @param currentProgress from -1 to 1, 0 means middle, 1 means open, -1 means close.
         */
        fun onScrollProgressChanged(currentProgress: Float)

        /**
         * This is called when the scrolling state changes.
         *
         * @param currentStatus the current status after change
         */
        fun onScrollFinished(@Status currentStatus: Int)

        /***
         * This is called when it's child scrolling.
         *
         * @param top the child view scroll data.
         */
        fun onChildScroll(top: Int)
    }

    private inner class SpaceInfo(private val minRemainingSpace: String, private val middleRemainingSpace: String, private val maxRemainingSpace: String) {
        internal var minSpace: Int = 0
        internal var middleSpace: Int = 0
        internal var maxSpace: Int = 0

        internal fun measure(res: Resources, height: Int) {
            minSpace = parserValueFromMode(res, parserOffset(minRemainingSpace, "minRemainingSpace"), height)
            middleSpace = height - parserValueFromMode(res, parserOffset(middleRemainingSpace, "middleRemainingSpace"), height)
            maxSpace = height - parserValueFromMode(res, parserOffset(maxRemainingSpace, "maxRemainingSpace"), height)
        }

        private fun parserOffset(value: String, argName: String): Int {
            val result: Int
            val trim = value.trim { it <= ' ' }
            if (trim.isNotEmpty()) {
                val mode: Int
                val number: String
                when {
                    trim.endsWith(UNIT[0]) -> {
                        mode = MODE_DP
                        number = trim.replace(UNIT[0], "")
                    }
                    trim.endsWith(UNIT[1]) -> {
                        mode = MODE_PERCENT
                        number = trim.replace(UNIT[1], "")
                    }
                    else -> throw RuntimeException(String.format("the %s value:%s is unknown!", argName, value))
                }
                result = Math.abs(Integer.parseInt(number)) and VALUE_QUALIFIED or mode
            } else {
                result = DRAG_HANDLE_BEGIN_OR_END_NOT_SET
            }
            return result
        }

        private fun parserValueFromMode(res: Resources, info: Int, height: Int): Int {
            val mode = info and MODE_QUALIFIED
            val size = info and VALUE_QUALIFIED
            val result: Int
            result = when (mode) {
                MODE_DP -> (size * res.displayMetrics.density + 0.5).toInt()
                MODE_PERCENT -> (height / 100.0 * size + 0.5).toInt()
                else -> throw IllegalArgumentException("the dragHandleMode is unknown!")
            }
            return result
        }
    }
}