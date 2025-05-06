package com.plcoding.audiorecorder.utils;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

public class DraggableButtonHelper {
    private float dX, dY;
    private float initialTouchX, initialTouchY;
    private long touchStartTime;
    private boolean isDragging = false;
    private static final int CLICK_THRESHOLD = 200; // milliseconds
    private static final int MOVE_THRESHOLD = 10; // pixels

    public interface OnButtonClickListener {
        void onClick();
    }

    public void makeDraggable(View button, OnButtonClickListener clickListener) {
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Store initial positions
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    dX = view.getX() - event.getRawX();
                    dY = view.getY() - event.getRawY();
                    touchStartTime = System.currentTimeMillis();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = Math.abs(event.getRawX() - initialTouchX);
                    float deltaY = Math.abs(event.getRawY() - initialTouchY);

                    // Check if movement exceeds threshold
                    if (deltaX > MOVE_THRESHOLD || deltaY > MOVE_THRESHOLD) {
                        isDragging = true;

                        // Perform drag
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;

                        // Keep button within parent bounds
                        ViewGroup parent = (ViewGroup) view.getParent();
                        int parentWidth = parent.getWidth();
                        int parentHeight = parent.getHeight();
                        int viewWidth = view.getWidth();
                        int viewHeight = view.getHeight();

                        newX = Math.max(0, Math.min(newX, parentWidth - viewWidth));
                        newY = Math.max(0, Math.min(newY, parentHeight - viewHeight));

                        view.setX(newX);
                        view.setY(newY);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    long touchDuration = System.currentTimeMillis() - touchStartTime;

                    if (!isDragging && touchDuration < CLICK_THRESHOLD) {
                        // This is a click
                        view.performClick();
                        if (clickListener != null) {
                            clickListener.onClick();
                        }
                    } else if (isDragging) {
                        // Snap to edge if dragged
                        snapToEdgeIfNeeded(view);
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    return true;
            }
            return false;
        });
    }

    private void snapToEdgeIfNeeded(View view) {
        ViewGroup parent = (ViewGroup) view.getParent();
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        float currentX = view.getX();
        float currentY = view.getY();
        float viewWidth = view.getWidth();
        float viewHeight = view.getHeight();

        float centerX = currentX + viewWidth / 2;
        float targetX = currentX;

        // Snap to left or right edge
        if (centerX < parentWidth / 2) {
            targetX = 0; // Snap to left
        } else {
            targetX = parentWidth - viewWidth; // Snap to right
        }

        animateToPosition(view, targetX, currentY);
    }

    private void animateToPosition(View view, float targetX, float targetY) {
        float startX = view.getX();
        float startY = view.getY();

        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(300);
        animator.setInterpolator(new OvershootInterpolator(1.0f));

        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            float newX = startX + (targetX - startX) * progress;
            float newY = startY + (targetY - startY) * progress;

            view.setX(newX);
            view.setY(newY);
        });

        animator.start();
    }
}