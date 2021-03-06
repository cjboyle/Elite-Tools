package com.connorboyle.elitetools.adapters;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.connorboyle.elitetools.fragments.Notebook;
import com.connorboyle.elitetools.R;
import com.connorboyle.elitetools.models.NoteDBHelper;
import com.connorboyle.elitetools.models.NoteEntry;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Connor Boyle on 16-Sep-17.
 */

public class NoteEntryAdapter extends CursorRecyclerViewAdapter<NoteEntryAdapter.NoteEntryViewHolder> {

    private static final int REMOVAL_TIME = 3000;

    private Handler handler = new Handler(); // handler for running delayed runnables
    private HashMap<NoteEntry, Runnable> pendingRunnables = new HashMap<>(); // map of items to pending runnables, so we can cancel a removal if need be

    private Context context;
    private ArrayList<NoteEntry> notesToRemove;
    private final Notebook.OnItemTouchListener onItemTouchListener;

    public NoteEntryAdapter(Context context, Cursor cursor, Notebook.OnItemTouchListener onItemTouchListener) {
        super(context, cursor); // for CursorRecyclerViewAdapter
        this.context = context;
        this.onItemTouchListener = onItemTouchListener;
        this.notesToRemove = new ArrayList<>();
    }

    @Override
    public NoteEntryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new NoteEntryViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.note_entry_view, parent, false));
    }

    @Override
    public void onBindViewHolder(final NoteEntryViewHolder viewHolder, Cursor cursor) {
        changeCursor(cursor);
        getCursor().moveToPosition(viewHolder.getAdapterPosition());
        final NoteEntry note = new NoteEntry(
                getCursor().getLong(0), getCursor().getString(1), getCursor().getString(2), 0);
        viewHolder.tvNoteTitle.setText(note.title);

        if (notesToRemove.contains(note)) {
            // we need to show the "undo" state of the row
            viewHolder.itemView.setBackgroundColor(Color.RED);
            viewHolder.tvNoteTitle.setVisibility(View.GONE);
            viewHolder.flNoteEntry.setOnClickListener(null);
            viewHolder.btnUndo.setVisibility(View.VISIBLE);
            viewHolder.btnUndo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // user wants to undo the removal, let's cancel the pending task
                    Runnable pendingRemovalRunnable = pendingRunnables.get(note);
                    pendingRunnables.remove(note);
                    if (pendingRemovalRunnable != null)
                        handler.removeCallbacks(pendingRemovalRunnable);
                    notesToRemove.remove(note);
                    // this will rebind the row in "normal" state
                    notifyItemChanged(viewHolder.getAdapterPosition());
                }
            });
        } else {
            // we need to show the "normal" state
            viewHolder.itemView.setBackgroundColor(Color.TRANSPARENT);
            viewHolder.tvNoteTitle.setVisibility(View.VISIBLE);
            viewHolder.tvNoteTitle.setText(note.title);
            viewHolder.btnUndo.setVisibility(View.GONE);
            viewHolder.btnUndo.setOnClickListener(null);
            viewHolder.flNoteEntry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getCursor().moveToPosition(viewHolder.getAdapterPosition());
                    onItemTouchListener.onNoteEntryClick(getCursor());
                    notifyItemChanged(viewHolder.getAdapterPosition());
                }
            });
        }
    }

    void remove(int pos) {
        getCursor().moveToPosition(pos);
        NoteEntry noteToDelete = new NoteEntry(
                getCursor().getLong(0), getCursor().getString(1), getCursor().getString(2), 0);
        NoteDBHelper db = new NoteDBHelper(context);
        db.open();
        if (notesToRemove.contains(noteToDelete)) {
            if (db.deleteNote(noteToDelete)) {
                notesToRemove.remove(noteToDelete);
                changeCursor(db.getNotes());
                getCursor().moveToFirst();
                notifyDataSetChanged();
            }
        } else {
            Toast.makeText(context, "Failed to delete note", Toast.LENGTH_SHORT).show();
        }
//        db.close();
    }

    public void pendingRemoval(final int pos) {
        getCursor().moveToPosition(pos);
        final NoteEntry note = new NoteEntry(
                getCursor().getLong(0), getCursor().getString(1), getCursor().getString(2), 0);
        if (!notesToRemove.contains(note)) {
            notesToRemove.add(note);
            // this will redraw row in "undo" state
            notifyItemChanged(pos);
            // let's create, store and post a runnable to remove the item
            Runnable pendingRemovalRunnable = new Runnable() {
                @Override
                public void run() {
                    remove(pos);
                }
            };
            handler.postDelayed(pendingRemovalRunnable, REMOVAL_TIME);
            pendingRunnables.put(note, pendingRemovalRunnable);
        }
    }

    public boolean isPendingRemoval(int position) {
        getCursor().moveToPosition(position);
        final NoteEntry note = new NoteEntry(
                getCursor().getLong(0), getCursor().getString(1), getCursor().getString(2), 0);
        return notesToRemove.contains(note);
    }

    static class NoteEntryViewHolder extends RecyclerView.ViewHolder {
        TextView tvNoteTitle;
        FrameLayout flNoteEntry;
        Button btnUndo;

        NoteEntryViewHolder(View view) {
            super(view);
            tvNoteTitle = (TextView) itemView.findViewById(R.id.tvNoteTitle);
            flNoteEntry = (FrameLayout) itemView.findViewById(R.id.flNoteEntry);
            btnUndo = (Button) itemView.findViewById(R.id.btnUndo);
        }
    }
}
