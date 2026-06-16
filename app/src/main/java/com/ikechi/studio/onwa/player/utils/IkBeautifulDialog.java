package com.ikechi.studio.onwa.player.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import com.ikechi.studio.onwa.player.R;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.adapter.RecyclingAdapter;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.animator.DefaultItemAnimator;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.decoration.SpacingDecoration;
import com.ikechi.studio.onwa.widgets.recyclerview.v1.widget.RecyclingView;

public class IkBeautifulDialog {

    private static final String TAG = "IkBeautifulDialog";

    private final Context context;
    private Dialog dialog;
    private String message;
    private String positiveButtonText;
    private String negativeButtonText;
    private boolean cancelable = true;

    private int backgroundColor = Color.TRANSPARENT;
    private Drawable backgroundDrawable;
    private int[] gradientColors;
    private GradientDrawable.Orientation gradientOrientation = GradientDrawable.Orientation.TOP_BOTTOM;

    private int mEffectiveBgColor = Color.DKGRAY;

    private OnPositiveClickListener positiveListener;
    private OnNegativeClickListener negativeListener;

    private String inputHint;
    private String inputInitialText;
    private OnInputConfirmedListener inputListener;

    private String[] listItems;
    private OnItemClickListener itemClickListener;

    private View customView;

    public interface OnPositiveClickListener { void onClick(); }
    public interface OnNegativeClickListener { void onClick(); }
    public interface OnInputConfirmedListener { void onInputConfirmed(String inputText); }
    public interface OnItemClickListener { void onItemClick(int position, String item); }

    public IkBeautifulDialog(Context context) {
        this.context = context;
    }

    public IkBeautifulDialog setMessage(String message) { this.message = message; return this; }
    public IkBeautifulDialog setPositiveButton(String text, OnPositiveClickListener listener) {
        this.positiveButtonText = text; this.positiveListener = listener; return this;
    }
    public IkBeautifulDialog setNegativeButton(String text, OnNegativeClickListener listener) {
        this.negativeButtonText = text; this.negativeListener = listener; return this;
    }
    public IkBeautifulDialog setCancelable(boolean cancelable) { this.cancelable = cancelable; return this; }

    public IkBeautifulDialog setBackgroundColor(int color) {
        this.backgroundColor = color; this.backgroundDrawable = null; this.gradientColors = null; return this;
    }
    public IkBeautifulDialog setBackgroundDrawable(Drawable drawable) {
        this.backgroundDrawable = drawable; this.backgroundColor = Color.TRANSPARENT; this.gradientColors = null; return this;
    }
    public IkBeautifulDialog setBackgroundGradient(int[] colors, GradientDrawable.Orientation orientation) {
        this.gradientColors = colors; this.gradientOrientation = orientation;
        this.backgroundColor = Color.TRANSPARENT; this.backgroundDrawable = null; return this;
    }

    public IkBeautifulDialog setInput(String hint, String initialText, OnInputConfirmedListener listener) {
        this.inputHint = hint; this.inputInitialText = initialText; this.inputListener = listener; return this;
    }
    public IkBeautifulDialog setItems(String[] items, OnItemClickListener listener) {
        this.listItems = items; this.itemClickListener = listener; return this;
    }
    public IkBeautifulDialog setCustomView(View view) { this.customView = view; return this; }

    public void show() {
        if (message == null) throw new IllegalStateException("Message must be set");
        boolean hasPos = positiveButtonText != null;
        boolean hasNeg = negativeButtonText != null;
        if (!hasPos && !hasNeg) throw new IllegalStateException("At least one button required");
        if (hasPos && hasNeg) createTwoButtonDialog(); else createSingleButtonDialog(hasPos);
    }
    public void showInput() {
        if (message == null) throw new IllegalStateException("Message required");
        if (positiveButtonText == null) throw new IllegalStateException("Positive button required");
        createInputDialog();
    }
    public void showList() {
        if (listItems == null || listItems.length == 0) throw new IllegalStateException("Items array required");
        createListDialog();
    }
    public void showCustom() {
        if (customView == null) throw new IllegalStateException("Custom view required");
        createCustomDialog();
    }

    private void setupWindow(Dialog dlg) {
        Window window = dlg.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                             WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setDimAmount(0.5f);
        }
    }

    private void applyBackground(ViewGroup root) {
        Drawable bg = root.getBackground();

        if (backgroundColor != Color.TRANSPARENT) {
            if (bg != null) {
                bg.setTint(backgroundColor);
                mEffectiveBgColor = backgroundColor;
            } else {
                root.setBackgroundColor(backgroundColor);
                mEffectiveBgColor = backgroundColor;
            }
            applyAdaptiveTextColors(root, mEffectiveBgColor);
            return;
        }

        if (backgroundDrawable != null) {
            root.setBackground(backgroundDrawable);
            mEffectiveBgColor = extractColorFromDrawable(backgroundDrawable);
            applyAdaptiveTextColors(root, mEffectiveBgColor);
            return;
        }

        if (gradientColors != null) {
            GradientDrawable gd = new GradientDrawable(gradientOrientation, gradientColors);
            root.setBackground(gd);
            mEffectiveBgColor = gradientColors[0];
            applyAdaptiveTextColors(root, mEffectiveBgColor);
            return;
        }

        mEffectiveBgColor = extractColorFromDrawable(bg);
        applyAdaptiveTextColors(root, mEffectiveBgColor);
    }

    private int extractColorFromDrawable(Drawable drawable) {
		if (drawable == null) return Color.DKGRAY;

		if (drawable instanceof ColorDrawable) {
			return ((ColorDrawable) drawable).getColor();
		}

		if (drawable instanceof GradientDrawable) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				int[] colors = ((GradientDrawable) drawable).getColors();
				if (colors != null && colors.length > 0) return colors[0];
			}
		}

		return Color.DKGRAY;
	}

    private void applyAdaptiveTextColors(ViewGroup root, int bgColor) {
        boolean isDark = isColorDark(bgColor);
        int textColor        = isDark ? Color.WHITE   : Color.BLACK;
        int secondaryColor   = isDark ? 0xFFCCCCCC   : 0xFF333333;
        int hintColor        = isDark ? 0xFF888888   : 0xFF999999;
        adaptChildViews(root, textColor, secondaryColor, hintColor);
    }

    private void adaptChildViews(ViewGroup parent, int textColor, int secColor, int hintColor) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof EditText) {
                ((EditText) child).setTextColor(textColor);
                ((EditText) child).setHintTextColor(hintColor);
            } else if (child instanceof TextView && !(child instanceof Button)) {
                TextView tv = (TextView) child;
                tv.setTextColor(child.getId() == R.id.tv_dialog_message ? textColor : secColor);
            } else if (child instanceof ViewGroup) {
                adaptChildViews((ViewGroup) child, textColor, secColor, hintColor);
            }
        }
    }

    private boolean isColorDark(int color) {
        double lum = (0.299 * Color.red(color)
			+ 0.587 * Color.green(color)
			+ 0.114 * Color.blue(color)) / 255.0;
        return lum < 0.5;
    }

    private void createTwoButtonDialog() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_beautiful);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        setupWindow(dialog);
        View rootView = dialog.findViewById(android.R.id.content);
        if (rootView instanceof ViewGroup) applyBackground((ViewGroup) rootView);

        TextView tvMsg = dialog.findViewById(R.id.tv_dialog_message);
        Button btnPos  = dialog.findViewById(R.id.btn_dialog_positive);
        Button btnNeg  = dialog.findViewById(R.id.btn_dialog_negative);

        tvMsg.setText(message);
        btnPos.setText(positiveButtonText);
        btnPos.setOnClickListener(v -> {
            if (positiveListener != null) positiveListener.onClick();
            dismiss();
        });
        btnNeg.setText(negativeButtonText);
        btnNeg.setOnClickListener(v -> {
            if (negativeListener != null) negativeListener.onClick();
            dismiss();
        });
        dialog.show();
    }

    private void createSingleButtonDialog(final boolean isPositive) {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_beautiful_single);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        setupWindow(dialog);
        View rootView = dialog.findViewById(android.R.id.content);
        if (rootView instanceof ViewGroup) applyBackground((ViewGroup) rootView);

        TextView tvMsg = dialog.findViewById(R.id.tv_dialog_message);
        Button btn     = dialog.findViewById(R.id.btn_dialog_single);

        tvMsg.setText(message);
        btn.setText(isPositive ? positiveButtonText : negativeButtonText);
        btn.setOnClickListener(v -> {
            if (isPositive  && positiveListener != null) positiveListener.onClick();
            if (!isPositive && negativeListener != null) negativeListener.onClick();
            dismiss();
        });
        dialog.show();
    }

    private void createInputDialog() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_beautiful_input);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        setupWindow(dialog);
        View rootView = dialog.findViewById(android.R.id.content);
        if (rootView instanceof ViewGroup) applyBackground((ViewGroup) rootView);

        TextView tvMsg   = dialog.findViewById(R.id.tv_dialog_message);
        EditText et      = dialog.findViewById(R.id.et_dialog_input);
        Button btn       = dialog.findViewById(R.id.btn_dialog_positive);

        tvMsg.setText(message);
        if (inputHint != null)        et.setHint(inputHint);
        if (inputInitialText != null) et.setText(inputInitialText);
        btn.setText(positiveButtonText);
        btn.setOnClickListener(v -> {
            String text = et.getText().toString().trim();
            if (inputListener != null) inputListener.onInputConfirmed(text);
            dismiss();
        });
        dialog.show();
    }

    private void createListDialog() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_beautiful_list);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        setupWindow(dialog);
        View rootView = dialog.findViewById(android.R.id.content);
        if (rootView instanceof ViewGroup) applyBackground((ViewGroup) rootView);

        TextView tvMsg         = dialog.findViewById(R.id.tv_dialog_message);
        RecyclingView recycler = dialog.findViewById(R.id.recycler_dialog_items);
        Button btnCancel       = dialog.findViewById(R.id.btn_dialog_cancel);

        if (message != null && !message.isEmpty()) {
            tvMsg.setText(message);
            tvMsg.setVisibility(View.VISIBLE);
        } else {
            tvMsg.setVisibility(View.GONE);
        }

        recycler.setOrientation(RecyclingView.VERTICAL);
        recycler.setItemAnimator(new DefaultItemAnimator());
        recycler.addItemDecoration(new SpacingDecoration(dpToPx(1), true, 0x1AE0E0E0));
        recycler.setScrollBarEnabled(true);

        int screenHeight    = context.getResources().getDisplayMetrics().heightPixels;
        int maxListHeight   = (int) (screenHeight * 0.55f);
        int totalItemHeight = listItems.length * dpToPx(52);
        int listHeight      = Math.min(totalItemHeight, maxListHeight);

        ViewGroup.LayoutParams params = recycler.getLayoutParams();
        if (params == null) params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight);
        else params.height = listHeight;
        recycler.setLayoutParams(params);

        int itemTextColor = isColorDark(mEffectiveBgColor) ? Color.WHITE : 0xFF333333;
        ListDialogAdapter adapter = new ListDialogAdapter(listItems, itemTextColor, position -> {
            if (itemClickListener != null) itemClickListener.onItemClick(position, listItems[position]);
            dismiss();
        });
        recycler.setAdapter(adapter);
        btnCancel.setOnClickListener(v -> dismiss());
        dialog.show();
    }

    private void createCustomDialog() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(customView);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        setupWindow(dialog);
        if (customView instanceof ViewGroup) applyBackground((ViewGroup) customView);
        dialog.show();
    }

    public void dismiss() { if (dialog != null && dialog.isShowing()) dialog.dismiss(); }
    public boolean isShowing() { return dialog != null && dialog.isShowing(); }
    private int dpToPx(int dp) { return Math.round(dp * context.getResources().getDisplayMetrics().density); }

    // -------------------------------------------------------------------------
    // Internal adapter
    // -------------------------------------------------------------------------

    private static class ListDialogAdapter extends RecyclingAdapter {

        interface OnItemClickListener { void onItemClick(int position); }

        private final String[]            mItems;
        private final int                 mTextColor;
        private final OnItemClickListener mListener;

        ListDialogAdapter(String[] items, int textColor, OnItemClickListener listener) {
            mItems = items; mTextColor = textColor; mListener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(32, 24, 32, 24);
            tv.setTextColor(mTextColor);
            tv.setTextSize(14f);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
								   ViewGroup.LayoutParams.MATCH_PARENT,
								   ViewGroup.LayoutParams.WRAP_CONTENT));
            return new SimpleHolder(tv);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int pos) {
            SimpleHolder sh = (SimpleHolder) holder;
            sh.tvText.setText(mItems[pos]);
            sh.tvText.setOnClickListener(v -> {
                if (mListener != null) mListener.onItemClick(pos);
            });
        }

        @Override public int getItemCount() { return mItems.length; }

        private static class SimpleHolder extends RecyclingAdapter.ViewHolder {
            final TextView tvText;
            SimpleHolder(TextView v) { super(v); tvText = v; }
        }
    }
}