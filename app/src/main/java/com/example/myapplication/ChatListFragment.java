package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ChatListFragment extends Fragment {
    private NodeAdapter adapter;
    private TextView emptyView;
    private List<String> connectedNodes = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_chat_list);
        emptyView = view.findViewById(R.id.empty_view);

        adapter = new NodeAdapter(connectedNodes, nodeId -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.openIndividualChat(nodeId);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            updateConnectedNodes(activity.getConnectedNodeNames());
        }

        return view;
    }

    public void updateConnectedNodes(List<String> nodes) {
        connectedNodes = nodes;
        if (adapter != null) {
            adapter.updateData(nodes);
            emptyView.setVisibility(nodes.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }
}