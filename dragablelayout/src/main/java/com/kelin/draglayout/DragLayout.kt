package com.kelin.draglayout

import android.annotation.SuppressLint
import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.v4.widget.ViewDragHelper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View


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
     * 用来记录可拖拽View的初始顶部位置。
     */
    private var dragViewTop = 0xFFFF_FFF

    companion object {
        /**
         * 拖拽速率的阈值。
         */
        private const val DRAG_VEL_THRESHOLD = 8000
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
                dragViewTop = dragView.top
            }
        }
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

    private inner class ViewDragHelperCallImpl : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return child.id == dragViewId && dragViewId != NO_ID
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return if (dy > 0) {
                if (child.bottom >= bottom) {
                    dragViewTop
                } else {
                    top
                }
            } else {
                if (child.top <= 0) {
                    0
                } else {
                    top
                }
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val center = dragViewTop.ushr(1)
            val threshold = DRAG_VEL_THRESHOLD * (center - releasedChild.top) / center
            val finalTop = when {
                yvel < -threshold -> 0
                yvel > threshold -> dragViewTop
                releasedChild.top < center -> 0
                else -> dragViewTop
            }
            dragHelper.settleCapturedViewAt(0, finalTop)
            invalidate()
        }
    }
}