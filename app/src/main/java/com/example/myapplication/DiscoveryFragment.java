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

public class DiscoveryFragment extends Fragment {
    private TextView statusText;
    private TextView nodeIdText;
    private NodeAdapter adapter;
    private List<String> discoveredNodes = new ArrayList<>();

    @Override
    public void onResume() {
        super.onResume();
        refreshUI();
    }

    private void refreshUI() {
        MainActivity activity = MainActivity.getInstance();
        if (activity != null) {
            updateDiscoveredNodes(activity.getAllMeshNodeNames());
            updateStatus(activity.getStatus());
            if (nodeIdText != null) {
                nodeIdText.setText(getString(R.string.node_id_label, activity.getDisplayName()));
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_discovery, container, false);
        statusText = view.findViewById(R.id.status_text);
        nodeIdText = view.findViewById(R.id.node_id_text);
        RecyclerView recyclerView = view.findViewById(R.id.discovery_recycler);
        
        adapter = new NodeAdapter(discoveredNodes, nodeName -> {
            MainActivity main = MainActivity.getInstance();
            if (main != null) {
                // Multi-hop Mesh logic:
                // We only try to establish a direct Bluetooth connection if the node is within physical range
                // and not already connected. Otherwise, we just open the chat and the mesh routing 
                // in MainActivity will handle relaying the messages through other peers automatically.
                if (!main.isNodeDirect(nodeName)) {
                    // Try to connect only if it's a direct neighbor we've seen in scans
                    // main.connectToNodeByName will internally check if the node is in range.
                    main.connectToNodeByName(nodeName);
                }
                
                // Open individual chat activity
                main.openIndividualChat(nodeName);
            }
        });
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        View btnRefresh = view.findViewById(R.id.btn_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                MainActivity main = MainActivity.getInstance();
                if (main != null) main.refreshNearby();
            });
        }

        View btnConnect = view.findViewById(R.id.btn_connect);
        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> {
                MainActivity main = MainActivity.getInstance();
                if (main != null) main.refreshNearby();
            });
        }

        refreshUI();
        return view;
    }

    public void updateStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }

    public void updateDiscoveredNodes(List<String> nodes) {
        this.discoveredNodes = nodes;
        if (adapter != null) {
            adapter.updateData(nodes);
        }
    }
}
