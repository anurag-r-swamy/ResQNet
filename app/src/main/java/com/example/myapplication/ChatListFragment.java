package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class ChatListFragment extends Fragment {
    private NodeAdapter adapter;
    private View emptyView;
    private List<String> connectedNodes = new ArrayList<>();

    private final BroadcastReceiver meshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.example.myapplication.MESH_DATA_CHANGED".equals(action) ||
                "com.example.myapplication.MESSAGE_RECEIVED".equals(action)) {
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    updateConnectedNodes(activity.getConnectedNodeNames());
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.example.myapplication.MESH_DATA_CHANGED");
            filter.addAction("com.example.myapplication.MESSAGE_RECEIVED");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getActivity().registerReceiver(meshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getActivity().registerReceiver(meshReceiver, filter);
            }
        }
        
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            updateConnectedNodes(activity.getConnectedNodeNames());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() != null) {
            getActivity().unregisterReceiver(meshReceiver);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_chat_list);
        emptyView = view.findViewById(R.id.empty_view);

        adapter = new NodeAdapter(connectedNodes, nodeId -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openIndividualChat(nodeId);
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
        this.connectedNodes = nodes;
        if (adapter != null) {
            adapter.updateData(nodes);
            if (emptyView != null) {
                emptyView.setVisibility(nodes.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }
    }
}