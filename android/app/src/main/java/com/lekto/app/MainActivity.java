package com.lekto.app;

import android.os.Build;
import android.os.Bundle;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(VaultFsPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Subclass BridgeWebViewClient so Capacitor's URL interception (local assets,
            // shouldOverrideUrlLoading, etc.) is fully preserved. We only add handling for
            // renderer crashes: instead of letting Chromium's AwBrowserTerminator kill the
            // whole process, recreate the Activity so a fresh renderer is spawned.
            getBridge().getWebView().setWebViewClient(new BridgeWebViewClient(getBridge()) {
                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    recreate();
                    return true;
                }
            });
        }
    }
}
