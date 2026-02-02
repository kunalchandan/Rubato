package one.chandan.rubato.helper.recyclerview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import one.chandan.rubato.R;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.ui.adapter.SongHorizontalAdapter;
import one.chandan.rubato.util.Preferences;

public final class QueueSwipeHelper {
    private QueueSwipeHelper() {
    }

    public enum SwipeAction {
        PLAY_NEXT,
        ADD_TO_QUEUE,
        TOGGLE_FAVORITE,
        NONE
    }

    public interface QueueSwipeAction {
        boolean canPerform(Child song, SwipeAction action);

        void onSwipeAction(Child song, SwipeAction action);

        void onSwipeRejected(Child song, SwipeAction action);
    }

    public interface SongProvider {
        Child getSong(int position);
    }

    public static ItemTouchHelper attach(RecyclerView recyclerView, SongHorizontalAdapter adapter, QueueSwipeAction action) {
        return attach(recyclerView, adapter, adapter::getItem, action);
    }

    public static ItemTouchHelper attach(RecyclerView recyclerView, RecyclerView.Adapter<?> adapter, SongProvider provider, QueueSwipeAction action) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            private final Paint backgroundPaint = new Paint();
            private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int swipeFlags = 0;
                SwipeAction left = resolveAction(viewHolder.itemView.getContext(), ItemTouchHelper.LEFT);
                SwipeAction right = resolveAction(viewHolder.itemView.getContext(), ItemTouchHelper.RIGHT);
                if (left != SwipeAction.NONE) {
                    swipeFlags |= ItemTouchHelper.LEFT;
                }
                if (right != SwipeAction.NONE) {
                    swipeFlags |= ItemTouchHelper.RIGHT;
                }
                return makeMovementFlags(0, swipeFlags);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }

                Child song = provider != null ? provider.getSong(position) : null;
                if (song == null) {
                    adapter.notifyItemChanged(position);
                    return;
                }

                SwipeAction swipeAction = resolveAction(viewHolder.itemView.getContext(), direction);
                if (swipeAction == SwipeAction.NONE) {
                    adapter.notifyItemChanged(position);
                    return;
                }

                if (action != null && action.canPerform(song, swipeAction)) {
                    action.onSwipeAction(song, swipeAction);
                    viewHolder.itemView.setHapticFeedbackEnabled(true);
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                } else if (action != null) {
                    action.onSwipeRejected(song, swipeAction);
                }

                adapter.notifyItemChanged(position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
                    super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    return;
                }

                drawSwipe(canvas, viewHolder, dX, actionState, backgroundPaint, labelPaint);

                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper helper = new ItemTouchHelper(callback);
        helper.attachToRecyclerView(recyclerView);
        return helper;
    }

    public static void drawSwipe(Canvas canvas, RecyclerView.ViewHolder viewHolder, float dX, int actionState, Paint backgroundPaint, Paint labelPaint) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
            return;
        }

        View itemView = viewHolder.itemView;
        Context context = itemView.getContext();
        int height = itemView.getHeight();
        int itemTop = itemView.getTop();
        int itemBottom = itemView.getBottom();
        float absDx = Math.abs(dX);
        float threshold = itemView.getWidth() * 0.25f;
        boolean crossed = absDx > threshold;

        if (crossed && Boolean.FALSE.equals(itemView.getTag(R.id.tag_swipe_haptic_triggered))) {
            itemView.setHapticFeedbackEnabled(true);
            itemView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            itemView.setTag(R.id.tag_swipe_haptic_triggered, Boolean.TRUE);
        } else if (!crossed) {
            itemView.setTag(R.id.tag_swipe_haptic_triggered, Boolean.FALSE);
        }

        if (dX > 0) {
            SwipeAction action = resolveAction(context, ItemTouchHelper.RIGHT);
            if (action == SwipeAction.NONE) return;
            int bgColor = resolveActionColor(context, action);
            int iconRes = resolveActionIcon(action, true);
            String label = resolveActionLabel(context, action, true);
            int iconTint = context.getColor(android.R.color.white);
            backgroundPaint.setColor(bgColor);
            canvas.drawRect(itemView.getLeft(), itemTop, itemView.getLeft() + dX, itemBottom, backgroundPaint);
            drawSwipeContent(canvas, context, iconRes, iconTint, label, true, itemView.getLeft(), itemTop, height, labelPaint);
        } else if (dX < 0) {
            SwipeAction action = resolveAction(context, ItemTouchHelper.LEFT);
            if (action == SwipeAction.NONE) return;
            int bgColor = resolveActionColor(context, action);
            int iconRes = resolveActionIcon(action, false);
            String label = resolveActionLabel(context, action, false);
            int iconTint = context.getColor(android.R.color.white);
            backgroundPaint.setColor(bgColor);
            canvas.drawRect(itemView.getRight() + dX, itemTop, itemView.getRight(), itemBottom, backgroundPaint);
            drawSwipeContent(canvas, context, iconRes, iconTint, label, false, itemView.getRight(), itemTop, height, labelPaint);
        }
    }

    private static void drawSwipeContent(Canvas canvas, Context context, int iconRes, int tint, String label, boolean alignLeft, int edgeX, int itemTop, int height, Paint textPaint) {
        Drawable icon = AppCompatResources.getDrawable(context, iconRes);
        if (icon == null) {
            return;
        }

        int iconSize = dpToPx(context, 24);
        int iconMargin = dpToPx(context, 20);
        int iconTop = itemTop + (height - iconSize) / 2;
        int iconLeft = alignLeft ? edgeX + iconMargin : edgeX - iconMargin - iconSize;

        icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
        icon.setTint(tint);
        icon.draw(canvas);

        if (label == null || label.isEmpty()) {
            return;
        }

        textPaint.setColor(tint);
        textPaint.setTextSize(spToPx(context, 14));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        float textWidth = textPaint.measureText(label);
        float textX = alignLeft ? iconLeft + iconSize + dpToPx(context, 12) : iconLeft - dpToPx(context, 12) - textWidth;
        float textY = itemTop + (height / 2f) + (textPaint.getTextSize() / 2.6f);
        canvas.drawText(label, textX, textY, textPaint);
    }

    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static int spToPx(Context context, int sp) {
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        return Math.round(sp * scaledDensity);
    }

    public static SwipeAction resolveAction(Context context, int direction) {
        String pref = direction == ItemTouchHelper.LEFT
                ? Preferences.getQueueSwipeLeftAction()
                : Preferences.getQueueSwipeRightAction();
        if (Preferences.QUEUE_SWIPE_ACTION_PLAY_NEXT.equals(pref)) {
            return SwipeAction.PLAY_NEXT;
        }
        if (Preferences.QUEUE_SWIPE_ACTION_ADD_QUEUE.equals(pref)) {
            return SwipeAction.ADD_TO_QUEUE;
        }
        if (Preferences.QUEUE_SWIPE_ACTION_LIKE.equals(pref)) {
            return SwipeAction.TOGGLE_FAVORITE;
        }
        return SwipeAction.NONE;
    }

    private static int resolveActionColor(Context context, SwipeAction action) {
        if (action == SwipeAction.TOGGLE_FAVORITE) {
            return context.getColor(R.color.queue_swipe_gold);
        }
        return context.getColor(R.color.queue_swipe_green);
    }

    private static int resolveActionIcon(SwipeAction action, boolean isRight) {
        switch (action) {
            case PLAY_NEXT:
                return R.drawable.ic_skip_next;
            case ADD_TO_QUEUE:
                return R.drawable.ic_queue;
            case TOGGLE_FAVORITE:
                return R.drawable.ic_favorite;
            default:
                return isRight ? R.drawable.ic_queue : R.drawable.ic_skip_next;
        }
    }

    private static String resolveActionLabel(Context context, SwipeAction action, boolean isRight) {
        switch (action) {
            case PLAY_NEXT:
                return context.getString(R.string.queue_swipe_label_play_next);
            case ADD_TO_QUEUE:
                return context.getString(R.string.queue_swipe_label_add_queue);
            case TOGGLE_FAVORITE:
                return context.getString(R.string.queue_swipe_label_like);
            default:
                return "";
        }
    }
}
