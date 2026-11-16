package com.alps.minimal

import android.content.Context
import android.view.MotionEvent
import android.view.animation.TranslateAnimation
import android.widget.ViewFlipper
import kotlin.math.abs

/**
 * Two-page horizontal pager for the landscape cockpit.
 * It intercepts only deliberate horizontal gestures, leaving taps and vertical
 * scrolling to the child views.
 */
class SwipeViewFlipper(context: Context) : ViewFlipper(context) {
    private var downX = 0f
    private var downY = 0f
    private var swipeHandled = false

    init {
        val enterFromRight = TranslateAnimation(1f, 0f, 0f, 0f).apply { duration = 280L }
        val leaveToLeft = TranslateAnimation(0f, -0.16f, 0f, 0f).apply { duration = 240L }
        val enterFromLeft = TranslateAnimation(-0.16f, 0f, 0f, 0f).apply { duration = 280L }
        val leaveToRight = TranslateAnimation(0f, 1f, 0f, 0f).apply { duration = 240L }
        inAnimation = enterFromRight
        outAnimation = leaveToLeft
        setAnimateFirstView(false)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swipeHandled = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > dp(38) && abs(dx) > abs(dy) * 1.25f) {
                    swipeHandled = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                if (swipeHandled && abs(dx) > dp(58)) {
                    if (dx < 0f && displayedChild < childCount - 1) {
                        inAnimation = TranslateAnimation(1f, 0f, 0f, 0f).apply { duration = 280L }
                        outAnimation = TranslateAnimation(0f, -0.16f, 0f, 0f).apply { duration = 240L }
                        showNext()
                    } else if (dx > 0f && displayedChild > 0) {
                        inAnimation = TranslateAnimation(-0.16f, 0f, 0f, 0f).apply { duration = 280L }
                        outAnimation = TranslateAnimation(0f, 1f, 0f, 0f).apply { duration = 240L }
                        showPrevious()
                    }
                }
                swipeHandled = false
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                swipeHandled = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density
}
