package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class DiscoveryFragment extends Fragment {
    private static final String TAG = "DiscoveryFragment";
    private TextView statusText, nodeIdText;
    private NodeAdapter adapter;
    private List<String> discoveredNodes = new ArrayList<>();

    @Override
    public void onResume() {
        super.onResume();
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            updateDiscoveredNodes(activity.getAllMeshNodeNames());
            updateStatus(activity.getStatus());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_discovery, container, false);
        statusText = view.findViewById(R.id.status_text);
        nodeIdText = view.findViewById(R.id.node_id_text);
        RecyclerView recyclerView = view.findViewById(R.id.discovery_recycler);
        
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            String fullId = activity.getDisplayName();
            nodeIdText.setText(getString(R.string.node_id_label, fullId));
            statusText.setText(activity.getStatus());
            discoveredNodes = activity.getAllMeshNodeNames();
        }

        adapter = new NodeAdapter(discoveredNodes, nodeName -> {
            MainActivity main = (MainActivity) getActivity();
            if (main != null) {
                // If it's a direct neighbor not yet connected, try connecting
                if (!main.isNodeDirect(nodeName)) {
                    main.connectToNodeByName(nodeName);
                }
                // Always open chat, mesh will handle the routing
                main.openIndividualChat(nodeName);
            }
        });
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.btn_connect).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshNearby();
            }
        });

        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshNearby();
            }
        });

        return view;
    }

    public void updateStatus(String status) {
        if (statusText != null) statusText.setText(status);
    }

    public void updateDiscoveredNodes(List<String> nodes) {
        this.discoveredNodes = nodes;
        if (adapter != null) adapter.updateData(nodes);
    }
}
