package com.plcoding.audiorecorder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.plcoding.audiorecorder.forms.ChecklistForm;

import java.util.List;

public class ChecklistAdapter extends RecyclerView.Adapter<ChecklistAdapter.ChecklistViewHolder> {
    private List<ChecklistForm> checklists;
    private OnChecklistSelectedListener listener;

    public interface OnChecklistSelectedListener {
        void onChecklistSelected(ChecklistForm form);
    }

    public ChecklistAdapter(List<ChecklistForm> checklists, OnChecklistSelectedListener listener) {
        this.checklists = checklists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChecklistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_checklist, parent, false);
        return new ChecklistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChecklistViewHolder holder, int position) {
        ChecklistForm form = checklists.get(position);

        holder.titleTextView.setText(form.getTitle());
        holder.descriptionTextView.setText(form.getDescription());
        holder.versionTextView.setText("v" + form.getVersion());
        holder.questionCountTextView.setText(form.getQuestion_count() + " questions");

        if (form.is_mandatory()) {
            holder.mandatoryBadge.setVisibility(View.VISIBLE);
        } else {
            holder.mandatoryBadge.setVisibility(View.GONE);
        }

        holder.startButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChecklistSelected(form);
            }
        });
    }

    @Override
    public int getItemCount() {
        return checklists.size();
    }

    static class ChecklistViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView descriptionTextView;
        TextView versionTextView;
        TextView questionCountTextView;
        TextView mandatoryBadge;
        Button startButton;

        ChecklistViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.checklist_title);
            descriptionTextView = itemView.findViewById(R.id.checklist_description);
            versionTextView = itemView.findViewById(R.id.checklist_version);
            questionCountTextView = itemView.findViewById(R.id.checklist_question_count);
            mandatoryBadge = itemView.findViewById(R.id.mandatory_badge);
            startButton = itemView.findViewById(R.id.start_checklist_button);
        }
    }
}