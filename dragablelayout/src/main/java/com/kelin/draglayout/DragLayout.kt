package com.kelin.draglayout

import android.annotation.SuppressLint
import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.v4.widget.ViewDragHelper
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
         * 表示当前没有任何拖拽方向。当拖拽状态为SCROLL_STATE_IDLE时获取拖拽方向时就会得到这个值。
         */
        const val DRAG_ORIENTATION_IDLE = 0xf0
        /**
         * 表示当前正在从下至上拖拽。
         */
        const val DRAG_ORIENTATION_UP = 0xf1
        /**
         * 表示当前正在从上至下拖拽。
         */
        const val DRAG_ORIENTATION_DOWN = 0xf2
        /**
         * 表示当前正在从右至左拖拽。
         */
        const val DRAG_ORIENTATION_LEFT = 0xf3
        /**
         * 表示当前正在从左至右拖拽。
         */
        const val DRAG_ORIENTATION_RIGHT = 0xf4
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
                    bottom - (dragHandleSize * resources.displayMetrics.density + 0.5f).toInt()
                }
                LayoutParams.MODE_PERCENT -> {
                    bottom - dragView.height / 100 * dragHandleSize
                }
                LayoutParams.MODE_PERCENT_PRENT -> {
                    bottom - height / 100 * dragHandleSize
                }
                else -> throw IllegalArgumentException("the dragHandleMode is unknown!")
            }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return dragHelper.shouldInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        dragHelper.processTouchEvent(event)
        return true
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
        private var curDragOrientation = DRAG_ORIENTATION_IDLE

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return child.id == dragViewId && dragViewId != NO_ID
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            Log.i("============", "$dragViewBegin|$dragViewEnd|$top")
            return if (dy > 0) {
                curDragOrientation = DRAG_ORIENTATION_DOWN
                if (child.top >= dragViewBegin) {
                    dragViewBegin
                } else {
                    top
                }
            } else {
                curDragOrientation = DRAG_ORIENTATION_UP
                if (child.top <= dragViewEnd) {
                    dragViewEnd
                } else {
                    top
                }
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            dragHelper.viewDragState
            val center = Math.abs(dragViewEnd - dragViewBegin).ushr(1)
            val threshold = if (curDragOrientation == DRAG_ORIENTATION_IDLE) DRAG_VEL_THRESHOLD else DRAG_VEL_THRESHOLD * Math.abs(releasedChild.top - getDifferenceValue()) / center
            val finalTop = when {
                Math.abs(yvel) > threshold && Math.abs(yvel) > 1000 -> getFinalTop()
                releasedChild.top < center + dragViewEnd -> dragViewEnd
                else -> dragViewBegin
            }
            dragHelper.settleCapturedViewAt(0, finalTop)
            invalidate()
        }

        private fun getDifferenceValue() = when (curDragOrientation) {
            DRAG_ORIENTATION_UP -> dragViewBegin
            DRAG_ORIENTATION_DOWN -> dragViewEnd
            else -> TODO("this orientation is not handle!")
        }

        private fun getFinalTop() = when (curDragOrientation) {
            DRAG_ORIENTATION_UP -> dragViewEnd
            DRAG_ORIENTATION_DOWN -> dragViewBegin
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