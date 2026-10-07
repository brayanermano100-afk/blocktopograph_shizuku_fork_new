package com.mithrilmania.blocktopograph.chunkbase;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mithrilmania.blocktopograph.R;

/**
 * Shows Chunkbase's Seed Map with the current world's seed pre-filled via the
 * page's deep-link URL fragment (#seed=...&platform=bedrock), so structures,
 * biomes, etc. show up without the person having to type the seed in by hand.
 */
public class ChunkbaseFragment extends Fragment {

    private static final String ARG_SEED = "seed";

    public static ChunkbaseFragment newInstance(long seed) {
        ChunkbaseFragment fragment = new ChunkbaseFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_SEED, seed);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chunkbase, container, false);

        WebView webView = root.findViewById(R.id.chunkbase_webview);
        ProgressBar progressBar = root.findViewById(R.id.chunkbase_progress);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        long seed = getArguments() != null ? getArguments().getLong(ARG_SEED, 0) : 0;
        String url = "https://www.chunkbase.com/apps/seed-map#seed=" + seed + "&platform=bedrock";
        webView.loadUrl(url);

        return root;
    }
}
