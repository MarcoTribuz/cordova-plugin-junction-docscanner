package com.marcotribuzio.junction.docscanner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Base64;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class JunctionDocScanner extends CordovaPlugin {

    private static final int REQ_DOC_SCAN = 49210;

    private CallbackContext pendingCallback;
    private int jpegQuality = 70;
    private int maxPages = 3;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("isAvailable".equals(action)) {
            // ML Kit doc scanner requires GMS. Probe via classloader.
            boolean ok;
            try {
                Class.forName("com.google.mlkit.vision.documentscanner.GmsDocumentScanning");
                ok = true;
            } catch (ClassNotFoundException e) {
                ok = false;
            }
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, ok));
            return true;
        }
        if ("scan".equals(action)) {
            this.maxPages    = args.optInt(0, 3);
            this.jpegQuality = args.optInt(1, 70);
            this.pendingCallback = callbackContext;
            cordova.setActivityResultCallback(this);
            launchScanner();
            return true;
        }
        return false;
    }

    private void launchScanner() {
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit(maxPages)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();

        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        Activity activity = cordova.getActivity();
        Task<android.content.IntentSender> task = scanner.getStartScanIntent(activity);
        task.addOnSuccessListener(intentSender -> {
            try {
                activity.startIntentSenderForResult(intentSender, REQ_DOC_SCAN, null, 0, 0, 0);
            } catch (Exception e) {
                fail("startIntentSender: " + e.getMessage());
            }
        }).addOnFailureListener(e -> fail("scanner init: " + e.getMessage()));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_DOC_SCAN) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            fail("cancelled");
            return;
        }
        GmsDocumentScanningResult result = GmsDocumentScanningResult.fromActivityResultIntent(data);
        if (result == null) {
            fail("no result");
            return;
        }
        List<GmsDocumentScanningResult.Page> pages = result.getPages();
        if (pages == null || pages.isEmpty()) {
            fail("no pages");
            return;
        }
        JSONArray out = new JSONArray();
        for (GmsDocumentScanningResult.Page page : pages) {
            try {
                String b64 = encodeUriToJpegBase64(page.getImageUri());
                if (b64 != null) out.put(b64);
            } catch (Exception e) {
                // skip bad page, continue
            }
        }
        if (out.length() == 0) {
            fail("no encoded pages");
            return;
        }
        ok(out);
    }

    private String encodeUriToJpegBase64(Uri uri) throws Exception {
        Activity activity = cordova.getActivity();
        InputStream is = activity.getContentResolver().openInputStream(uri);
        if (is == null) return null;
        Bitmap src = BitmapFactory.decodeStream(is);
        is.close();
        if (src == null) return null;
        Bitmap resized = resize(src, 1600);
        if (resized != src) src.recycle();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, jpegQuality, bos);
        resized.recycle();
        byte[] bytes = bos.toByteArray();
        bos.close();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private Bitmap resize(Bitmap src, int maxLongSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longSide = Math.max(w, h);
        if (longSide <= maxLongSide) return src;
        float scale = (float) maxLongSide / longSide;
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        return Bitmap.createBitmap(src, 0, 0, w, h, m, true);
    }

    private void fail(String msg) {
        if (pendingCallback == null) return;
        pendingCallback.error(msg);
        pendingCallback = null;
    }

    private void ok(JSONArray result) {
        if (pendingCallback == null) return;
        pendingCallback.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
        pendingCallback = null;
    }
}
