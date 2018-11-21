package com.kelin.draglayout

import android.annotation.SuppressLint
import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.v4.widget.ViewDragHelper
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup


/**
 * **描述:** 子View可以被拖拽的ViewGroup。
 *
 * **创建人:** kelin
 *
 * **创建时间:** 2018/10/27  下午4:01
 *
 * **版本:** v 1.0.0
 */
class DragLayout(context: Context, attrs: AttributeSet?, defStyle: Int) : ConstraintLayout(context, attrs, defStyle) {

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val dragHelper = ViewDragHelper.create(this, 1.0F, ViewDragHelperCallImpl())

    private var dragViewId = NO_ID
    /**
     * 用来记录可拖拽View被收起后的位置。
     */
    private var dragViewBegin = 0
    /**
     * 用来记录可拖拽View被展开后的位置。
     */
    private var dragViewEnd = 0
    private var isDraggable = false
    private var lastX = 0F
    private var lastY = 0F
    private var curDragStatus = DRAG_STATE_BEGIN

    private val associatedRecyclerViewListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            updateRecyclerViewScrollState(recyclerView!!)
        }

        override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            updateRecyclerViewScrollState(recyclerView!!)
        }
    }

    companion object {
        /**
         * 拖拽速率的阈值。
         */
        private const val DRAG_VEL_THRESHOLD = 8000
        /**
         * Indicates that the pager is in an idle, settled state. The current page
         * is fully in view and no animation is in progress.
         */
        const val SCROLL_STATE_IDLE = 0

        /**
         * Indicates that the pager is currently being dragged by the user.
         */
        const val SCROLL_STATE_DRAGGING = 1

        /**
         * Indicates that the pager is in the process of settling to a final position.
         */
        const val SCROLL_STATE_SETTLING = 2
        /**
         * 表示当前状态为可拖拽的子View处于开始位置。
         */
        const val DRAG_STATE_BEGIN = 0xf0
        /**
         * 表示当前状态为可拖拽的子View处于结束位置。
         */
        const val DRAG_STATE_END = 0xf1
        /**
         * 表示当前正在从下至上拖拽。
         */
        const val DRAG_STATE_UP = 0xf2
        /**
         * 表示当前正在从上至下拖拽。
         */
        const val DRAG_STATE_DOWN = 0xf3
    }

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DragLayout)
            if (typedArray != null) {
                dragViewId = typedArray.getResourceId(R.styleable.DragLayout_dragView, dragViewId)
                typedArray.recycle()
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (dragViewId != View.NO_ID) {
            val dragView = findViewById<View>(dragViewId)
            if (dragView is RecyclerView) {
                setAssociatedRecyclerView(dragView)
            }else if (dragView is ViewGroup) {
                for (i: Int in 0 until dragView.childCount) {
                    val childAt = dragView.getChildAt(i)
                    if (childAt is RecyclerView) {
                        setAssociatedRecyclerView(childAt)
                    }
                }
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (dragViewId != NO_ID) {
            val dragView = findViewById<View>(dragViewId)
            if (dragView != null) {
                val lp = dragView.layoutParams
                if (lp != null && lp is DragLayout.LayoutParams) {
                    val dragHandleBeginMode = lp.getDragHandleBeginMode()
                    if (dragHandleBeginMode != DragLayout.LayoutParams.DRAG_HANDLE_BEGIN_OR_END_NOT_SET) {
                        dragViewBegin = parserValueFromMode(dragHandleBeginMode, bottom, dragView, lp.getDragHandleBeginSize())
                        dragView.layout(dragView.left, dragViewBegin, dragView.right, dragView.bottom + dragViewBegin)

                        val dragHandleEndMode = lp.getDragHandleEndMode()
                        dragViewEnd = if (dragHandleEndMode != DragLayout.LayoutParams.DRAG_HANDLE_BEGIN_OR_END_NOT_SET) {
                            parserValueFromMode(dragHandleEndMode, bottom, dragView, lp.getDragHandleEndSize())
                        } else {
                            bottom - dragView.height
                        }
                    }
                } else {
                    dragViewBegin = dragView.top
                }
            }
        }
    }

    private fun parserValueFromMode(mode: Int, bottom: Int, dragView: View, dragHandleSize: Int) =
            when (mode) {
                LayoutParams.MODE_DP -> {
                    bottom - (dragHandleSize * resources.displayMetrics.density + 0.5).toInt()
                }
                LayoutParams.MODE_PERCENT -> {
                    bottom - (dragView.height / 100.0 * dragHandleSize + 0.5).toInt()
                }
                LayoutParams.MODE_PERCENT_PRENT -> {
                    bottom - (height / 100.0 * dragHandleSize + 0.5).toInt()
                }
                else -> throw IllegalArgumentException("the dragHandleMode is unknown!")
            }


    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        var draggable = true
        val topView = dragHelper.findTopChildUnder(x.toInt(), ev.y.toInt())
        if (topView?.id == dragViewId) {
            val x = ev.x
            val y = ev.y
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = x
                    lastY = y
                }
                MotionEvent.ACTION_MOVE -> {
                    val spaceX = Math.abs(lastX - x)
                    val spaceY = Math.abs(lastY - y)
                    if (spaceY < spaceX || y > lastY || !isDraggable || curDragStatus == DRAG_STATE_BEGIN) {
                        draggable = true
                    }
                }
            }
        }

        return draggable or dragHelper.shouldInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        dragHelper.processTouchEvent(event)
        return true
    }

    private fun setAssociatedRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(associatedRecyclerViewListener)
        updateRecyclerViewScrollState(recyclerView)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper.continueSettling(true)) {
            invalidate()
        }
    }

    override fun generateDefaultLayoutParams(): ConstraintLayout.LayoutParams {
        return DragLayout.LayoutParams(-2, -2)
    }

    override fun generateLayoutParams(attrs: AttributeSet): ConstraintLayout.LayoutParams {
        return DragLayout.LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return DragLayout.LayoutParams(p)
    }

    private inner class ViewDragHelperCallImpl : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return child.id == dragViewId && dragViewId != NO_ID
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            Log.i("============", "$dragViewBegin|$dragViewEnd|$top|$dy")
            return if (dy > 0) {
                if (child.top + dy >= dragViewBegin) {
                    curDragStatus = DRAG_STATE_BEGIN
                    dragViewBegin
                } else {
                    curDragStatus = DRAG_STATE_DOWN
                    top
                }
            } else {
                if (child.top + dy <= dragViewEnd) {
                    curDragStatus = DRAG_STATE_END
                    dragViewEnd
                } else {
                    curDragStatus = DRAG_STATE_UP
                    top
                }
            }
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            return child.left
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            if (releasedChild.top != dragViewBegin && releasedChild.top != dragViewEnd) {
                val center = Math.abs(dragViewEnd - dragViewBegin).ushr(1)
                val threshold = if (curDragStatus == DRAG_STATE_BEGIN) DRAG_VEL_THRESHOLD else DRAG_VEL_THRESHOLD * Math.abs(releasedChild.top - getDifferenceValue()) / center
                val finalTop = when {
                    Math.abs(yvel) > threshold && Math.abs(yvel) > 1000 -> getFinalTop()
                    releasedChild.top < center + dragViewEnd -> dragViewEnd
                    else -> dragViewBegin
                }
                dragHelper.settleCapturedViewAt(releasedChild.left, finalTop)
                invalidate()
            }
        }

        private fun getDifferenceValue() = when (curDragStatus) {
            DRAG_STATE_UP -> dragViewBegin
            DRAG_STATE_DOWN -> dragViewEnd
            else -> TODO("this orientation is not handle!")
        }

        private fun getFinalTop() = when (curDragStatus) {
            DRAG_STATE_UP -> dragViewEnd
            DRAG_STATE_DOWN -> dragViewBegin
            else -> TODO("this orientation is not handle!")
        }
    }

    class LayoutParams : ConstraintLayout.LayoutParams {

        /**
         * 拖拽把手的开始位置。
         */
        private var dragHandleBegin: Int = DRAG_HANDLE_BEGIN_OR_END_NOT_SET
        /**
         * 拖拽把手的结束位置。
         */
        private var dragHandleEnd: Int = DRAG_HANDLE_BEGIN_OR_END_NOT_SET

        companion object {
            /**
             * dragHandleSize的value值所支持的单位。
             */
            private val UNIT = arrayOf("dp", "%", "%p")
            /**
             * 表示没有设置拖拽把手的布局方式及大小。
             */
            const val DRAG_HANDLE_BEGIN_OR_END_NOT_SET = 0x0000_0000
            /**
             * 表示当前拖拽把手的布局方式为dp。
             */
            const val MODE_DP = 0x1000_0000
            /**
             * 表示当前拖拽把手的布局方式为自身百分比。
             */
            const val MODE_PERCENT = 0x2000_0000
            /**
             * 表示当前拖拽把手的布局方式为父布局的百分比。
             */
            const val MODE_PERCENT_PRENT = 0x3000_0000
            /**
             * 把手参数的数值限定。
             */
            private const val VALUE_QUALIFIED = 0x0FFF_FFFF
            /**
             * 把手参数的模式限定。
             */
            private const val MODE_QUALIFIED = 0x7000_0000
        }

        constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.DragLayout_Layout)
            val beginValue = ta.getString(R.styleable.DragLayout_Layout_dragHandleBegin)
            val endValue = ta.getString(R.styleable.DragLayout_Layout_dragHandleEnd)
            ta.recycle()
            if (beginValue != null) {
                dragHandleBegin = parserDragHandleSize(beginValue, "dragHandleBegin")
                if (endValue != null) {
                    dragHandleEnd = parserDragHandleSize(endValue, "dragHandleEnd")
                }
            }
        }

        constructor(width: Int, height: Int) : super(width, height)

        constructor(layoutParams: ViewGroup.LayoutParams) : super(layoutParams)

        fun getDragHandleBeginMode() = if (dragHandleBegin == DRAG_HANDLE_BEGIN_OR_END_NOT_SET) DRAG_HANDLE_BEGIN_OR_END_NOT_SET else dragHandleBegin and MODE_QUALIFIED

        fun getDragHandleBeginSize() = if (dragHandleBegin == DRAG_HANDLE_BEGIN_OR_END_NOT_SET) DRAG_HANDLE_BEGIN_OR_END_NOT_SET else dragHandleBegin and VALUE_QUALIFIED

        fun getDragHandleEndMode() = if (dragHandleEnd == DRAG_HANDLE_BEGIN_OR_END_NOT_SET) DRAG_HANDLE_BEGIN_OR_END_NOT_SET else dragHandleEnd and MODE_QUALIFIED

        fun getDragHandleEndSize() = if (dragHandleEnd == DRAG_HANDLE_BEGIN_OR_END_NOT_SET) DRAG_HANDLE_BEGIN_OR_END_NOT_SET else dragHandleEnd and VALUE_QUALIFIED

        private fun parserDragHandleSize(value: String, argName: String): Int {
            val trim = value.trim()
            return if (trim.isNotEmpty()) {
                val mode: Int
                val number = when {
                    trim.endsWith(UNIT[0], true) -> {
                        mode = MODE_DP
                        trim.replace(UNIT[0], "", true)
                    }
                    trim.endsWith(UNIT[1], true) -> {
                        mode = MODE_PERCENT
                        trim.replace(UNIT[1], "", true)
                    }
                    trim.endsWith(UNIT[2], true) -> {
                        mode = MODE_PERCENT_PRENT
                        trim.replace(UNIT[2], "", true)
                    }
                    else -> throw RuntimeException("the $argName value:$value is unknown!")
                }.toInt()

                Math.abs(number) and VALUE_QUALIFIED or mode
            } else {
                DRAG_HANDLE_BEGIN_OR_END_NOT_SET
            }
        }
    }

    private fun updateRecyclerViewScrollState(recyclerView: RecyclerView) {
        if (recyclerView.childCount == 0) {
            isDraggable = true
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
                    isDraggable = true
                    return
                }
            }
            isDraggable = false
        }
    }

    interface OnDragStateChangedListener {

        /**
         * 当拖拽的状态被改变时调用。
         *
         * @param state 当前被改变后的拖拽状态.
         * @param isFolded 当前被拖拽的子View是否是被隐藏的，也就是说当前可以被拖拽的子View是否回到了最初的状态。
         *
         * @see DragLayout.SCROLL_STATE_IDLE
         *
         * @see DragLayout.SCROLL_STATE_DRAGGING
         *
         * @see DragLayout.SCROLL_STATE_SETTLING
         */
        fun onDragStateChanged(state: Int, isFolded: Boolean)
    }

    interface OnDraggingListener {

        fun onPageScrolled(offset: Float, offsetTotal: Int, orientation: Int)
    }
}