package com.plcoding.audiorecorder.utils;

import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.util.Log;

public class KeyboardUtils {
    private static final String TAG = "KeyboardUtils";

    // Show keyboard for an EditText
    public static void showKeyboard(EditText editText) {
        if (editText == null) return;

        editText.requestFocus();
        try {
            InputMethodManager imm = (InputMethodManager)
                    editText.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing keyboard", e);
        }
    }

    // Hide keyboard
    public static void hideKeyboard(View view) {
        if (view == null) return;

        try {
            InputMethodManager imm = (InputMethodManager)
                    view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding keyboard", e);
        }
    }

    // Clear focus from all EditTexts in a view hierarchy
    public static void clearFocus(View rootView) {
        if (rootView == null) return;

        rootView.clearFocus();

        if (rootView instanceof EditText) {
            hideKeyboard(rootView);
        }
    }
}