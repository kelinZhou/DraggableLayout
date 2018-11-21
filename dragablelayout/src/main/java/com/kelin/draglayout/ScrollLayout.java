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

package com.kelin.draglayout;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.os.Build;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Scroller;


/**
 * Layout that can scroll down to a max offset and can tell the scroll progress by
 * OnScrollProgressListener.
 */
public class ScrollLayout extends FrameLayout {

    /**
     * 表示当前拖拽把手的布局方式为dp。
     */
    private static final int MODE_DP = 0x1000_0000;
    /**
     * 表示当前拖拽把手的布局方式为自身百分比。
     */
    private static final int MODE_PERCENT = 0x2000_0000;
    /**
     * 表示没有设置拖拽把手的布局方式及大小。
     */
    private static final int DRAG_HANDLE_BEGIN_OR_END_NOT_SET = 0x0000_0000;
    /**
     * dragHandleSize的value值所支持的单位。
     */
    private static final String[] UNIT = new String[]{"dp", "%"};

    /**
     * 把手参数的数值限定。
     */
    private static final int VALUE_QUALIFIED = 0x0FFF_FFFF;
    /**
     * 把手参数的模式限定。
     */
    private static final int MODE_QUALIFIED = 0x7000_0000;

    private static final int MAX_SCROLL_DURATION = 400;
    private static final int MIN_SCROLL_DURATION = 100;
    private static final int FLING_VELOCITY_SLOP = 80;
    private static final float DRAG_SPEED_MULTIPLIER = 1.2f;
    private static final int DRAG_SPEED_SLOP = 30;
    private static final int MOTION_DISTANCE_SLOP = 10;
    private static final float SCROLL_TO_CLOSE_OFFSET_FACTOR = 0.5f;
    private static final float SCROLL_TO_EXIT_OFFSET_FACTOR = 0.8f;
    private static final int MODE_NONE = 0x0;
    private static final int MODE_OPEN = 0x1;
    private static final int MODE_CLOSE = 0x2;
    private static final int MODE_EXIT = 0x3;
    private final GestureDetector.OnGestureListener gestureListener =
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (velocityY > FLING_VELOCITY_SLOP) {
                        if (lastFlingStatus.equals(Status.OPENED) && -getScrollY() > offsetInfo.maxOffset) {
                            scrollToExit();
                        } else {
                            scrollToOpen();
                        }
                        return true;
                    } else if (velocityY < FLING_VELOCITY_SLOP && getScrollY() <= -offsetInfo.maxOffset) {
                        scrollToOpen();
                        return true;
                    } else if (velocityY < FLING_VELOCITY_SLOP && getScrollY() > -offsetInfo.maxOffset) {
                        scrollToClose();
                        return true;
                    }
                    return false;
                }
            };

    private final AbsListView.OnScrollListener associatedListViewListener =
            new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    updateListViewScrollState(view);
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                     int totalItemCount) {
                    updateListViewScrollState(view);
                }
            };
    private final RecyclerView.OnScrollListener associatedRecyclerViewListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    updateRecyclerViewScrollState(recyclerView);
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    updateRecyclerViewScrollState(recyclerView);
                }
            };

    final OffsetInfo offsetInfo = new OffsetInfo();
    private float lastY;
    private float lastDownX;
    private float lastDownY;
    private Status lastFlingStatus = Status.CLOSED;
    private Scroller scroller;
    private GestureDetector gestureDetector;
    private boolean isSupportExit = false;
    private boolean isAllowHorizontalScroll = true;
    private boolean isDraggable = true;
    private boolean isAllowPointerIntercepted = true;
    private boolean isCurrentPointerIntercepted = false;
    private InnerStatus currentInnerStatus = InnerStatus.OPENED;
    private int initialMode = MODE_EXIT;
    private OnScrollChangedListener onScrollChangedListener;
    private ContentScrollView mScrollView;
    /**
     * 用来记录内部会有事件冲突的View。
     */
    private View contentView;

    public ScrollLayout(Context context) {
        this(context, null);
    }

    public ScrollLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressLint("ObsoleteSdkInt")
    public ScrollLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            scroller = new Scroller(getContext(), null, true);
        } else {
            scroller = new Scroller(getContext());
        }
        gestureDetector = new GestureDetector(getContext(), gestureListener);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ScrollLayout);
            if (a != null) {
                offsetInfo.minOffsetText = a.getString(R.styleable.ScrollLayout_minOffset);
                offsetInfo.maxOffsetText = a.getString(R.styleable.ScrollLayout_maxOffset);
                offsetInfo.exitOffsetText = a.getString(R.styleable.ScrollLayout_exitOffset);
                isAllowHorizontalScroll = a.getBoolean(R.styleable.ScrollLayout_allowHorizontalScroll, true);
                isSupportExit = a.getBoolean(R.styleable.ScrollLayout_isSupportExit, true);
                initialMode = a.getInteger(R.styleable.ScrollLayout_mode, MODE_EXIT);
                a.recycle();
            }
        }
    }

    private ContentScrollView.OnScrollChangedListener mOnScrollChangedListener = new ContentScrollView.OnScrollChangedListener() {
        @Override
        public void onScrollChanged(int l, int t, int oldL, int oldT) {
            if (null == mScrollView) return;
            if (null != onScrollChangedListener)
                onScrollChangedListener.onChildScroll(oldT);
            if (mScrollView.getScrollY() == 0) {
                setDraggable(true);
            } else {
                setDraggable(false);
            }
        }
    };

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        offsetInfo.measure(getResources(), h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (initialMode != MODE_NONE) {
            switch (initialMode) {
                case MODE_OPEN:
                    setToOpen();
                    break;
                case MODE_CLOSE:
                    setToClosed();
                    break;
                case MODE_EXIT:
                    setToExit();
                    break;
            }
            initialMode = MODE_NONE;
        }
        findContentView(this);
    }

    private void findContentView(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof AbsListView) {
                setAssociatedListView((AbsListView) child);
            } else if (child instanceof RecyclerView) {
                setAssociatedRecyclerView((RecyclerView) child);
            } else if (child instanceof ContentScrollView) {
                setAssociatedScrollView((ContentScrollView) child);
            } else if (child instanceof ViewGroup) {
                findContentView((ViewGroup) child);
            }
        }
    }

    /**
     * Set the scrolled position of your view. This will cause a call to
     * {@link #onScrollChanged(int, int, int, int)} and the view will be
     * invalidated.
     *
     * @param x the x position to scroll to
     * @param y the y position to scroll to
     */
    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(x, y);
        if (offsetInfo.maxOffset == offsetInfo.minOffset) {
            return;
        }
        //only from min to max or from max to min,send progress out. not exit
        if (-y <= offsetInfo.maxOffset) {
            float progress = (float) (-y - offsetInfo.minOffset) / (offsetInfo.maxOffset - offsetInfo.minOffset);
            onScrollProgressChanged(progress);
        } else {
            float progress = (float) (-y - offsetInfo.maxOffset) / (offsetInfo.maxOffset - offsetInfo.exitOffset);
            onScrollProgressChanged(progress);
        }
        if (y == -offsetInfo.minOffset) {
            // closed
            if (currentInnerStatus != InnerStatus.CLOSED) {
                currentInnerStatus = InnerStatus.CLOSED;
                onScrollFinished(Status.CLOSED);
            }
        } else if (y == -offsetInfo.maxOffset) {
            // opened
            if (currentInnerStatus != InnerStatus.OPENED) {
                currentInnerStatus = InnerStatus.OPENED;
                onScrollFinished(Status.OPENED);
            }
        } else if (isSupportExit && y == -offsetInfo.exitOffset) {
            // exited
            if (currentInnerStatus != InnerStatus.EXIT) {
                currentInnerStatus = InnerStatus.EXIT;
                onScrollFinished(Status.EXIT);
            }
        }
    }

    private void onScrollFinished(Status status) {
        if (onScrollChangedListener != null) {
            onScrollChangedListener.onScrollFinished(status);
        }
    }

    private void onScrollProgressChanged(float progress) {
        if (onScrollChangedListener != null) {
            onScrollChangedListener.onScrollProgressChanged(progress);
        }
    }

    @Override
    public void computeScroll() {
        if (!scroller.isFinished() && scroller.computeScrollOffset()) {
            int currY = scroller.getCurrY();
            scrollTo(0, currY);
            if (currY == -offsetInfo.minOffset || currY == -offsetInfo.maxOffset || (isSupportExit && currY == -offsetInfo.exitOffset)) {
                scroller.abortAnimation();
            } else {
                invalidate();
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getY() < Math.abs(getScrollY()) || ev.getY() > getBottom() || (!isDraggable && currentInnerStatus == InnerStatus.CLOSED)) {
            return false;
        } else {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    float x = ev.getX();
                    lastDownX = x;
                    float y = ev.getY();
                    lastDownY = lastY = y;
                    isAllowPointerIntercepted = true;
                    isCurrentPointerIntercepted = false;
                    if (currentInnerStatus == InnerStatus.EXIT || currentInnerStatus == InnerStatus.OPENED || !isInContentView(x, y)) {
                        currentInnerStatus = InnerStatus.MOVING;
                        isCurrentPointerIntercepted = true;
                        return true;
                    } else if (!scroller.isFinished()) {
                        scroller.forceFinished(true);
                        currentInnerStatus = InnerStatus.MOVING;
                        isCurrentPointerIntercepted = true;
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isAllowPointerIntercepted = true;
                    isCurrentPointerIntercepted = false;
                    if (currentInnerStatus == InnerStatus.MOVING) {
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!isAllowPointerIntercepted) {
                        return false;
                    }
                    if (isCurrentPointerIntercepted) {
                        return true;
                    }
                    int deltaY = (int) (ev.getY() - lastDownY);
                    int deltaX = (int) (ev.getX() - lastDownX);
                    if (Math.abs(deltaY) < MOTION_DISTANCE_SLOP) {
                        return false;
                    }
                    if (Math.abs(deltaY) < Math.abs(deltaX)) {
                        // horizontal event
                        if (isAllowHorizontalScroll) {
                            isAllowPointerIntercepted = false;
                            isCurrentPointerIntercepted = false;
                            return false;
                        }
                    }
                    if (currentInnerStatus == InnerStatus.CLOSED) {
                        // when closed, only handle downwards motion event
                        if (deltaY < 0) {
                            // upwards
                            return false;
                        }
                    } else if (currentInnerStatus == InnerStatus.OPENED && !isSupportExit) {
                        // when opened, only handle upwards motion event
                        if (deltaY > 0) {
                            // downwards
                            return false;
                        }
                    }
                    isCurrentPointerIntercepted = true;
                    return true;
                default:
                    return false;
            }
            return false;
        }
    }

    /**
     * 根据一个坐标，用来判断当前坐标是否在内部的有事件冲突的View上。
     *
     * @param x x坐标。
     * @param y y坐标。
     */
    private boolean isInContentView(float x, float y) {
        return contentView != null && x > contentView.getLeft() && x < contentView.getRight() && y > contentView.getTop() && y < contentView.getBottom();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if ((currentInnerStatus != InnerStatus.MOVING && (event.getY() < Math.abs(getScrollY()) || event.getY() > getBottom())) || !isCurrentPointerIntercepted) {
            return false;
        } else {
            gestureDetector.onTouchEvent(event);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int deltaY = (int) ((event.getY() - lastY) * DRAG_SPEED_MULTIPLIER);
                    deltaY = (int) (Math.signum(deltaY)) * Math.min(Math.abs(deltaY), DRAG_SPEED_SLOP);
                    if (disposeEdgeValue(deltaY)) {
                        return true;
                    }
                    currentInnerStatus = InnerStatus.MOVING;
                    int toScrollY = getScrollY() - deltaY;
                    if (toScrollY >= -offsetInfo.minOffset) {
                        scrollTo(0, -offsetInfo.minOffset);
                    } else if (toScrollY <= -offsetInfo.maxOffset && !isSupportExit) {
                        scrollTo(0, -offsetInfo.maxOffset);
                    } else {
                        scrollTo(0, toScrollY);
                    }
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (currentInnerStatus == InnerStatus.MOVING) {
                        completeMove();
                        return true;
                    }
                    break;
                default:
                    return false;
            }
            return false;
        }
    }

    private boolean disposeEdgeValue(int deltaY) {
        return deltaY <= 0 && getScrollY() >= -offsetInfo.minOffset || deltaY >= 0 && getScrollY() <= (isSupportExit ? -offsetInfo.exitOffset : -offsetInfo.maxOffset);
    }

    private void completeMove() {
        float closeValue = -((offsetInfo.maxOffset - offsetInfo.minOffset) * SCROLL_TO_CLOSE_OFFSET_FACTOR);
        if (getScrollY() > closeValue) {
            scrollToClose();
        } else {
            if (isSupportExit) {
                float exitValue = -((offsetInfo.exitOffset - offsetInfo.maxOffset) * SCROLL_TO_EXIT_OFFSET_FACTOR + offsetInfo.maxOffset);
                if (getScrollY() <= closeValue && getScrollY() > exitValue) {
                    scrollToOpen();
                } else {
                    scrollToExit();
                }
            } else {
                scrollToOpen();
            }
        }
    }

    /**
     * 滚动布局打开关闭,关闭否则滚动.
     */
    public void showOrHide() {
        if (currentInnerStatus == InnerStatus.OPENED) {
            scrollToClose();
        } else if (currentInnerStatus == InnerStatus.CLOSED) {
            scrollToOpen();
        }
    }

    /**
     * 滚动布局开放,offsetInfo.maxOffset之后向下滚动.
     */
    public void scrollToOpen() {
        if (currentInnerStatus == InnerStatus.OPENED) {
            return;
        }
        if (offsetInfo.maxOffset == offsetInfo.minOffset) {
            return;
        }
        int dy = -getScrollY() - offsetInfo.maxOffset;
        if (dy == 0) {
            return;
        }
        lastFlingStatus = Status.OPENED;
        currentInnerStatus = InnerStatus.SCROLLING;
        int duration = MIN_SCROLL_DURATION
                + Math.abs((MAX_SCROLL_DURATION - MIN_SCROLL_DURATION) * dy / (offsetInfo.maxOffset - offsetInfo.minOffset));
        scroller.startScroll(0, getScrollY(), 0, dy, duration);
        invalidate();
    }

    /**
     * 滚动的布局来关闭,滚动到offsetInfo.minOffset.
     */
    public void scrollToClose() {
        if (currentInnerStatus == InnerStatus.CLOSED) {
            return;
        }
        if (offsetInfo.maxOffset == offsetInfo.minOffset) {
            return;
        }
        int dy = -getScrollY() - offsetInfo.minOffset;
        if (dy == 0) {
            return;
        }
        lastFlingStatus = Status.CLOSED;
        currentInnerStatus = InnerStatus.SCROLLING;
        int duration = MIN_SCROLL_DURATION
                + Math.abs((MAX_SCROLL_DURATION - MIN_SCROLL_DURATION) * dy / (offsetInfo.maxOffset - offsetInfo.minOffset));
        scroller.startScroll(0, getScrollY(), 0, dy, duration);
        invalidate();
    }

    /**
     * 滚动布局退出
     */
    public void scrollToExit() {
        if (!isSupportExit) return;
        if (currentInnerStatus == InnerStatus.EXIT) {
            return;
        }
        if (offsetInfo.exitOffset == offsetInfo.maxOffset) {
            return;
        }
        int dy = -getScrollY() - offsetInfo.exitOffset;
        if (dy == 0) {
            return;
        }
        lastFlingStatus = Status.EXIT;
        currentInnerStatus = InnerStatus.SCROLLING;
        int duration = MIN_SCROLL_DURATION
                + Math.abs((MAX_SCROLL_DURATION - MIN_SCROLL_DURATION) * dy / (offsetInfo.exitOffset - offsetInfo.maxOffset));
        scroller.startScroll(0, getScrollY(), 0, dy, duration);
        invalidate();
    }

    /**
     * 初始化布局开放,没有动画。
     */
    public void setToOpen() {
        scrollTo(0, -offsetInfo.maxOffset);
        currentInnerStatus = InnerStatus.OPENED;
        lastFlingStatus = Status.OPENED;
    }

    /**
     * 初始化布局关闭,没有动画。
     */
    public void setToClosed() {
        scrollTo(0, -offsetInfo.minOffset);
        currentInnerStatus = InnerStatus.CLOSED;
        lastFlingStatus = Status.CLOSED;
    }

    /**
     * 初始化布局,退出,没有动画。
     */
    public void setToExit() {
        if (!isSupportExit) return;
        scrollTo(0, -offsetInfo.exitOffset);
        currentInnerStatus = InnerStatus.EXIT;
    }

    public void setIsSupportExit(boolean isSupportExit) {
        this.isSupportExit = isSupportExit;
    }

    public boolean isSupportExit() {
        return isSupportExit;
    }

    public boolean isAllowHorizontalScroll() {
        return isAllowHorizontalScroll;
    }

    public void setAllowHorizontalScroll(boolean isAllowed) {
        isAllowHorizontalScroll = isAllowed;
    }

    public boolean isDraggable() {
        return isDraggable;
    }

    public void setDraggable(boolean draggable) {
        this.isDraggable = draggable;
    }

    public void setOnScrollChangedListener(OnScrollChangedListener listener) {
        this.onScrollChangedListener = listener;
    }

    public Status getCurrentStatus() {
        switch (currentInnerStatus) {
            case CLOSED:
                return Status.CLOSED;
            case OPENED:
                return Status.OPENED;
            case EXIT:
                return Status.EXIT;
            default:
                return Status.OPENED;
        }
    }

    /**
     * Set associated list view, then this layout will only be able to drag down when the list
     * view is scrolled to top.
     *
     * @param listView
     */
    public void setAssociatedListView(AbsListView listView) {
        contentView = listView;
        listView.setOnScrollListener(associatedListViewListener);
        updateListViewScrollState(listView);
    }

    /**
     * Set associated list view, then this layout will only be able to drag down when the list
     * view is scrolled to top.
     *
     * @param recyclerView
     */
    public void setAssociatedRecyclerView(RecyclerView recyclerView) {
        contentView = recyclerView;
        recyclerView.addOnScrollListener(associatedRecyclerViewListener);
        updateRecyclerViewScrollState(recyclerView);
    }

    private void updateListViewScrollState(AbsListView listView) {
        if (listView.getChildCount() == 0) {
            setDraggable(true);
        } else {
            if (listView.getFirstVisiblePosition() == 0) {
                View firstChild = listView.getChildAt(0);
                if (firstChild.getTop() == listView.getPaddingTop()) {
                    setDraggable(true);
                    return;
                }
            }
            setDraggable(false);
        }
    }

    private void updateRecyclerViewScrollState(RecyclerView recyclerView) {
        if (recyclerView.getChildCount() == 0) {
            setDraggable(true);
        } else {
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            int[] i = new int[1];
            if (layoutManager instanceof LinearLayoutManager || layoutManager instanceof GridLayoutManager) {
                i[0] = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                i = null;
                i = ((StaggeredGridLayoutManager) layoutManager).findFirstVisibleItemPositions(i);
            }
            if (i[0] == 0) {
                View firstChild = recyclerView.getChildAt(0);
                if (firstChild.getTop() == recyclerView.getPaddingTop()) {
                    setDraggable(true);
                    return;
                }
            }
            setDraggable(false);
        }
    }

    public void setAssociatedScrollView(ContentScrollView scrollView) {
        this.contentView = this.mScrollView  = scrollView;
        this.mScrollView.setScrollbarFadingEnabled(false);
        this.mScrollView.setOnScrollChangeListener(mOnScrollChangedListener);
    }

    private enum InnerStatus {
        EXIT, OPENED, CLOSED, MOVING, SCROLLING
    }

    /**
     * 获取屏幕内容高度
     *
     * @return
     */
    public int getScreenHeight() {
        DisplayMetrics dm = new DisplayMetrics();
        ((Activity) getContext()).getWindowManager().getDefaultDisplay().getMetrics(dm);
        int result = 0;
        int resourceId = getContext().getResources()
                .getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getContext().getResources().getDimensionPixelSize(resourceId);
        }
        int screenHeight = dm.heightPixels - result;
        return screenHeight;
    }


    /**
     * 表明Scrolllayout的状态,只可以打开或关闭。
     */
    public enum Status {
        EXIT, OPENED, CLOSED
    }

    /**
     * 注册这个Scrolllayout可以监控其滚动
     */
    public interface OnScrollChangedListener {
        /**
         * 每次滚动改变值
         *
         * @param currentProgress 0 to 1, 1 to -1, 0 means close, 1 means open, -1 means exit.
         */
        void onScrollProgressChanged(float currentProgress);

        /**
         * 滚动状态改变时调用的方法
         *
         * @param currentStatus the current status after change
         */
        void onScrollFinished(Status currentStatus);

        /***
         * 滚动子视图
         *
         * @param top the child view scroll data
         */
        void onChildScroll(int top);
    }

    class OffsetInfo {
        private String minOffsetText;
        private String maxOffsetText;
        private String exitOffsetText;
        int minOffset;
        int maxOffset;
        int exitOffset;

        private void measure(Resources res, int height) {
            minOffset = parserValueFromMode(res, parserOffset(minOffsetText, "minOffset"), height);
            maxOffset = height - parserValueFromMode(res, parserOffset(maxOffsetText, "maxOffset"), height);
            exitOffset = height - parserValueFromMode(res, parserOffset(exitOffsetText, "exitOffset"), height);
        }

        private int parserOffset(String value, String argName) {
            int result;
            String trim = value.trim();
            if (!TextUtils.isEmpty(trim)) {
                int mode;
                String number;
                if (trim.endsWith(UNIT[0])) {
                    mode = MODE_DP;
                    number = trim.replace(UNIT[0], "");
                } else if (trim.endsWith(UNIT[1])) {
                    mode = MODE_PERCENT;
                    number = trim.replace(UNIT[1], "");
                } else {
                    throw new RuntimeException(String.format("the %s value:%s is unknown!", argName, value));
                }
                result = Math.abs(Integer.parseInt(number)) & VALUE_QUALIFIED | mode;
            } else {
                result = DRAG_HANDLE_BEGIN_OR_END_NOT_SET;
            }
            return result;
        }

        private int parserValueFromMode(Resources res, int info, int height) {
            int mode = info & MODE_QUALIFIED;
            int size = info & VALUE_QUALIFIED;
            int result;
            if (mode == MODE_DP) {
                result = (int) (size * res.getDisplayMetrics().density + 0.5);
            } else if (mode == MODE_PERCENT) {
                result = (int) (height / 100.0 * size + 0.5);
            } else {
                throw new IllegalArgumentException("the dragHandleMode is unknown!");
            }
            return result;
        }
    }

}
