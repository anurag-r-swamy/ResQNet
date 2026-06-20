package com.example.myapplication;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NodeAdapter extends RecyclerView.Adapter<NodeAdapter.ViewHolder> {
    private List<String> nodes;
    private OnNodeClickListener listener;

    public interface OnNodeClickListener {
        void onNodeClick(String nodeId);
    }

    public NodeAdapter(List<String> nodes, OnNodeClickListener listener) {
        this.nodes = nodes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_node, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String nodeName = nodes.get(position);
        holder.nodeName.setText(nodeName);
        
        MainActivity main = MainActivity.getInstance();
        if (main != null) {
            boolean isDirect = main.isNodeDirect(nodeName);
            if (isDirect) {
                holder.nodeStatus.setText("Directly Connected");
                holder.nodeStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
            } else {
                holder.nodeStatus.setText("Mesh (via peers)");
                holder.nodeStatus.setTextColor(Color.parseColor("#FF9800")); // Orange
            }
        }
        
        holder.itemView.setOnClickListener(v -> listener.onNodeClick(nodeName));
    }

    @Override
    public int getItemCount() {
        return nodes.size();
    }

    public void updateData(List<String> newNodes) {
        this.nodes = newNodes;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nodeName, nodeStatus;
        ViewHolder(View itemView) {
            super(itemView);
            nodeName = itemView.findViewById(R.id.node_name);
            nodeStatus = itemView.findViewById(R.id.node_status_text);
        }
    }
}
