package team.devapp.capacitor.otalive;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.work.*;
import com.getcapacitor.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import okhttp3.*;

@CapacitorPlugin(name = "OtaLiveUpdater")
public class OtaLiveUpdaterPlugin extends Plugin {
    private static final String TAG = "OtaLiveUpdater";
    private static final String WORK_NAME = "ota_version_check";
    private static final String PREFS_NAME = "ota_prefs";
    private static final String KEY_CURRENT_VERSION = "current_version";
    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String currentVersion = "1.0.0";
    private String pendingVersionPath = null;
    private final Gson gson = new Gson();
    private final Set<String> calledCheckpoints = new HashSet<>();
    private SharedPreferences prefs;

    static class OTAUpdate {
        String version;
        String date;
        String releaseDate;
        String bundleUrl;
        String description;
        String sha256;
    }

    static class UpdateConfig {
        Map<String, Integer> requiredCheckpoints;
        long timeoutCheckMillis;
    }

    @Override
    public void load() {
        Log.d(TAG, "OTA Plugin loaded");
        prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentVersion = prefs.getString(KEY_CURRENT_VERSION, "1.0.0");
        Log.d(TAG, "Loaded currentVersion: " + currentVersion);
        scheduleVersionCheck();
    }

    private void scheduleVersionCheck() {
        Log.d(TAG, "Scheduling WorkManager task");
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        VersionCheckWorker.bridge=this.getBridge();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(VersionCheckWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build();

        WorkManager.getInstance(getContext())
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, workRequest);
        Log.d(TAG, "WorkManager task enqueued with ID: " + workRequest.getId());

        // Проверяем статус задачи
        WorkManager.getInstance(getContext())
                .getWorkInfoByIdLiveData(workRequest.getId())
                .observeForever(workInfo -> {
                    if (workInfo != null) {
                        Log.d(TAG, "WorkInfo state: " + workInfo.getState());
                    }
                });
    }

    public static class VersionCheckWorker extends Worker {
        private static final String TAG = "OTAWorker";
        private final OkHttpClient client = new OkHttpClient();
        private final Gson gson = new Gson();
        private final File storageDir;
        private String currentVersion;
        private final SharedPreferences prefs;
        private static Bridge bridge;
        public VersionCheckWorker(Context context, WorkerParameters params) {
            super(context, params);
            this.storageDir = context.getFilesDir();
            this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            this.currentVersion = prefs.getString(KEY_CURRENT_VERSION, "1.0.0");
            Log.d(TAG, "Worker initialized with currentVersion: " + currentVersion);
        }

        @Override
        public Result doWork() {
            Log.d(TAG, "Worker started");
            try {
                List<OTAUpdate> updates = fetchLastVersion();
                Log.d(TAG, "Fetched updates: " + updates.size());
                if (updates.isEmpty()) return Result.success();
                OTAUpdate latestUpdate = updates.get(0);
                if (isNewerVersion(latestUpdate.version, currentVersion)) {
                    Log.d(TAG, "New version found: " + latestUpdate.version);
                    downloadAndStoreBundle(latestUpdate);
                }
                if (isReleaseDateReached(latestUpdate.releaseDate)) {
                    Log.d(TAG, "Release date reached, extracting bundle");
                    extractBundle();
                    notifyWebView(latestUpdate);
                }
                return Result.success();
            } catch (Exception e) {
                Log.e(TAG, "Worker error: " + e.getMessage());
                return Result.retry();
            }
        }

        private List<OTAUpdate> fetchLastVersion() throws IOException {
            Log.d(TAG, "Fetching last version from server");
            Request request = new Request.Builder()
                    .url("http://192.168.1.231:5111/otalive/lastversion")
                    .build();
            Response response = client.newCall(request).execute();
            if (response.body() == null) {
                Log.e(TAG, "Empty response from server");
                throw new IOException("Empty response");
            }
            String json = response.body().string();
            Type listType = new TypeToken<List<OTAUpdate>>(){}.getType();
            return gson.fromJson(json, listType);
        }

        private boolean isNewerVersion(String newVersion, String currentVersion) {
            boolean isNewer = newVersion.compareTo(currentVersion) > 0;
            Log.d(TAG, "Comparing versions: new=" + newVersion + ", current=" + currentVersion + ", isNewer=" + isNewer);
            return isNewer;
        }

        private boolean isReleaseDateReached(String releaseDate) {
            try {
                boolean reached = System.currentTimeMillis() >= Long.parseLong(releaseDate);
                Log.d(TAG, "Release date check: " + releaseDate + ", reached=" + reached);
                return reached;
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid release date format: " + releaseDate);
                return false;
            }
        }

        private void downloadAndStoreBundle(OTAUpdate update) throws IOException {
            Log.d(TAG, "Downloading bundle: " + update.bundleUrl);
            Request request = new Request.Builder()
                    .url("http://192.168.1.231:5111" + update.bundleUrl)
                    .build();
            Response response = client.newCall(request).execute();
            if (response.body() == null) {
                Log.e(TAG, "Empty bundle response");
                throw new IOException("Empty bundle");
            }
            byte[] bytes = response.body().bytes();
            File zipFile = new File(storageDir, "newversion.zip");
            try (FileOutputStream fos = new FileOutputStream(zipFile)) {
                fos.write(bytes);
            }
            String computedSha = sha256(bytes);
            if (!update.sha256.equals(computedSha)) {
                Log.e(TAG, "SHA256 mismatch: expected=" + update.sha256 + ", computed=" + computedSha);
                throw new IOException("SHA256 mismatch");
            }
            Log.d(TAG, "Bundle downloaded and stored");
        }

        private void extractBundle() throws IOException {
            Log.d(TAG, "Extracting bundle");
            File zipFile = new File(storageDir, "newversion.zip");
            File extractDir = new File(storageDir, "new_version");
            try (ZipFile zip = new ZipFile(zipFile)) {
                for (java.util.Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    File file = new File(extractDir, entry.getName());
                    if (entry.isDirectory()) {
                        file.mkdirs();
                    } else {
                        file.getParentFile().mkdirs();
                        try (InputStream is = zip.getInputStream(entry); FileOutputStream fos = new FileOutputStream(file)) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "Bundle extracted");
        }

        private void notifyWebView(OTAUpdate update) {
            Log.d(TAG, "Notifying WebView about new version: " + update.version);
            JSObject data = new JSObject();
            data.put("version", update.version);
            data.put("description", update.description);

            if (bridge != null) {
                bridge.triggerJSEvent("newVersionAvailable", "window", data.toString());
            }
        }

        private String sha256(byte[] data) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(data);
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                return hexString.toString();
            } catch (Exception e) {
                Log.e(TAG, "SHA256 error: " + e.getMessage());
                return "";
            }
        }
    }

    @PluginMethod
    public void applyUpdate(PluginCall call) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Applying update");
                pendingVersionPath = new File(getContext().getFilesDir(), "new_version").getAbsolutePath();
                getBridge().getWebView().loadUrl("file://" + pendingVersionPath + "/index.html");
                validateCheckpoints();
                prefs.edit().putString(KEY_CURRENT_VERSION, currentVersion).apply();
                Log.d(TAG, "Update applied, saved currentVersion: " + currentVersion);
                call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Apply update error: " + e.getMessage());
                rollback();
                JSObject errorData = new JSObject();
                errorData.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
                notifyListeners("updateFailed", errorData);
                sendErrorLog(e.getMessage() != null ? e.getMessage() : "Unknown error");
                call.reject(e.getMessage());
            }
        });
    }

    private void validateCheckpoints() throws Exception {
        Log.d(TAG, "Validating checkpoints");
        UpdateConfig config = loadUpdateConfig();
        calledCheckpoints.clear();
        CountDownLatch latch = new CountDownLatch(1);

        getBridge().getWebView().evaluateJavascript(
                "window.registerCheckpoint = function(name, executionTime) {" +
                        "    window.dispatchEvent(new CustomEvent('checkpoint', { detail: { name: name, executionTime: executionTime } }));" +
                        "};",
                null
        );

        getBridge().getWebView().evaluateJavascript(
                "document.addEventListener('checkpoint', function(e) {" +
                        "    Capacitor.Plugins.OTA.checkpoint(e.detail.name, e.detail.executionTime);" +
                        "});",
                null
        );

        executor.execute(() -> {
            try {
                Log.d(TAG, "Waiting for checkpoints, timeout: " + config.timeoutCheckMillis + "ms");
                Thread.sleep(config.timeoutCheckMillis);
                if (calledCheckpoints.size() < config.requiredCheckpoints.size()) {
                    throw new Exception("Not all checkpoints were called");
                }
            } catch (Exception e) {
                Log.e(TAG, "Checkpoint validation error: " + e.getMessage());
                rollback();
                JSObject errorData = new JSObject();
                errorData.put("error", e.getMessage());
                notifyListeners("updateFailed", errorData);
                sendErrorLog(e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        latch.await(config.timeoutCheckMillis + 1000, TimeUnit.MILLISECONDS);

        if (calledCheckpoints.size() != config.requiredCheckpoints.size()) {
            Log.e(TAG, "Checkpoint validation failed: called " + calledCheckpoints.size() + ", required " + config.requiredCheckpoints.size());
            throw new Exception("Checkpoint validation failed: not all checkpoints called");
        }

        for (Map.Entry<String, Integer> entry : config.requiredCheckpoints.entrySet()) {
            if (!calledCheckpoints.contains(entry.getKey())) {
                Log.e(TAG, "Checkpoint not called: " + entry.getKey());
                throw new Exception("Checkpoint " + entry.getKey() + " was not called");
            }
        }

        currentVersion = new File(pendingVersionPath).getName();
        pendingVersionPath = null;
        Log.d(TAG, "Checkpoints validated, new currentVersion: " + currentVersion);
        notifyListeners("updateSuccess", new JSObject());
    }

    @PluginMethod
    public void checkpoint(PluginCall call) {
        String name = call.getString("name");
        Integer executionTime = call.getInt("executionTime");
        if (name == null || executionTime == null) {
            Log.e(TAG, "Invalid checkpoint data");
            call.reject("Invalid checkpoint data");
            return;
        }

        try {
            UpdateConfig config = loadUpdateConfig();
            if (config.requiredCheckpoints.containsKey(name)) {
                if (executionTime == config.requiredCheckpoints.get(name)) {
                    calledCheckpoints.add(name);
                    Log.d(TAG, "Checkpoint called: " + name + ", executionTime: " + executionTime);
                    call.resolve();
                } else {
                    throw new Exception("Checkpoint " + name + " execution time mismatch");
                }
            } else {
                throw new Exception("Unknown checkpoint: " + name);
            }
        } catch (Exception e) {
            Log.e(TAG, "Checkpoint error: " + e.getMessage());
            rollback();
            JSObject errorData = new JSObject();
            errorData.put("error", e.getMessage());
            notifyListeners("updateFailed", errorData);
            sendErrorLog(e.getMessage());
            call.reject(e.getMessage());
        }
    }

    private UpdateConfig loadUpdateConfig() throws IOException {
        Log.d(TAG, "Loading update config");
        File configFile = new File(getContext().getFilesDir(), "new_version/update.config.json");
        try (java.io.FileReader reader = new java.io.FileReader(configFile)) {
            return gson.fromJson(reader, UpdateConfig.class);
        }
    }

    private void rollback() {
        Log.d(TAG, "Rolling back to previous version");
        String currentPath = getContext().getFilesDir().getAbsolutePath() + "/current_version/index.html";
        getBridge().getWebView().loadUrl("file://" + currentPath);
        pendingVersionPath = null;
    }

    @PluginMethod
    public void rollBackUpdate(PluginCall call) {
        Log.d(TAG, "Rollback update requested");
        rollback();
        notifyListeners("updateRolledBack", new JSObject());
        call.resolve();
    }

    private void sendErrorLog(String error) {
        Log.d(TAG, "Sending error log: " + error);
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                "{\"error\":\"" + error + "\",\"timestamp\":\"" + System.currentTimeMillis() + "\"}"
        );
        Request request = new Request.Builder()
                .url("http://192.168.1.231:5111/otalive/log")
                .post(body)
                .build();
        executor.execute(() -> {
            try {
                client.newCall(request).execute();
            } catch (IOException e) {
                Log.e(TAG, "Error log send failed: " + e.getMessage());
            }
        });
    }
}