package com.termux.shared.markdown;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.shared.R;

import java.util.List;

/**
 * A minimal {@link RecyclerView.Adapter} that renders a list of
 * {@link MarkdownUtils.MarkdownBlock}s produced by
 * {@link MarkdownUtils#parseMarkdownBlocks(android.content.Context, String)}.
 * <p/>
 * Replaces {@code io.noties.markwon.recycler.MarkwonAdapter}, whose upstream Markwon repository
 * was archived in 2022. Only supports the two row types {@code ReportActivity} needs: a default
 * text row for everything except fenced code blocks, and a horizontally-scrollable monospace row
 * for fenced code blocks - mirroring the original
 * {@code MarkwonAdapter.builderTextViewIsRoot(default).include(FencedCodeBlock.class, codeBlockEntry)}
 * setup exactly.
 */
public class MarkdownBlockAdapter extends RecyclerView.Adapter<MarkdownBlockAdapter.BlockViewHolder> {

    private static final int VIEW_TYPE_TEXT = 0;
    private static final int VIEW_TYPE_CODE_BLOCK = 1;

    private final List<MarkdownUtils.MarkdownBlock> blocks;

    public MarkdownBlockAdapter(@NonNull List<MarkdownUtils.MarkdownBlock> blocks) {
        this.blocks = blocks;
    }

    @Override
    public int getItemViewType(int position) {
        return blocks.get(position).type == MarkdownUtils.MarkdownBlock.Type.CODE_BLOCK
            ? VIEW_TYPE_CODE_BLOCK : VIEW_TYPE_TEXT;
    }

    @NonNull
    @Override
    public BlockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_CODE_BLOCK) {
            View view = inflater.inflate(R.layout.markdown_adapter_node_code_block, parent, false);
            return new BlockViewHolder(view, view.findViewById(R.id.code_text_view));
        } else {
            View view = inflater.inflate(R.layout.markdown_adapter_node_default, parent, false);
            return new BlockViewHolder(view, view.findViewById(R.id.default_text_view));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull BlockViewHolder holder, int position) {
        holder.textView.setText(blocks.get(position).content);
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    static final class BlockViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        BlockViewHolder(@NonNull View itemView, @NonNull TextView textView) {
            super(itemView);
            this.textView = textView;
        }
    }

}
