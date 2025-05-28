package team.devapp.capacitor.otalive;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.work.*;
import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import okhttp3.*;

@CapacitorPlugin(name = "OtaLiveUpdater")
public class OtaLiveUpdaterPlugin extends Plugin {
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
            // Инициализация SharedPreferences
            prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            // Загрузка текущей версии из SharedPreferences
            currentVersion = prefs.getString(KEY_CURRENT_VERSION, "1.0.0");
            Log.d("OTA", "Run OTALIVE currentVersion: " + currentVersion);
            scheduleVersionCheck();
        }

        private void scheduleVersionCheck() {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            VersionCheckWorker.bridge=this.getBridge();

            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(VersionCheckWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build();

            WorkManager.getInstance(getContext())
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest);
        }

        public static class VersionCheckWorker extends Worker {
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
                // Загрузка текущей версии из SharedPreferences
                this.currentVersion = prefs.getString(KEY_CURRENT_VERSION, "1.0.0");
            }

            @Override
            public Result doWork() {
                try {
                    List<OTAUpdate> updates = fetchLastVersion();
                    if (updates.isEmpty()) return Result.success();
                    OTAUpdate latestUpdate = updates.get(0);
                    if (isNewerVersion(latestUpdate.version, currentVersion)) {
                        downloadAndStoreBundle(latestUpdate);
                    }
                    if (isReleaseDateReached(latestUpdate.releaseDate)) {
                        extractBundle();
                        notifyWebView(latestUpdate);
                    }
                    return Result.success();
                } catch (Exception e) {
                    // Логируем ошибку
                    return Result.retry();
                }
            }

            private List<OTAUpdate> fetchLastVersion() throws IOException {
                Request request = new Request.Builder()
                        .url("https://192.168.1.231:5111/otalive/lastversion")
                        .build();
                Response response = client.newCall(request).execute();
                Log.d("OTA", "Loaded currentVersion: " + currentVersion);
                if (response.body() == null) throw new IOException("Empty response");
                String json = response.body().string();
                Type listType = new TypeToken<List<OTAUpdate>>(){}.getType();
                return gson.fromJson(json, listType);
            }

            private boolean isNewerVersion(String newVersion, String currentVersion) {
                return newVersion.compareTo(currentVersion) > 0;
            }

            private boolean isReleaseDateReached(String releaseDate) {
                try {
                    return System.currentTimeMillis() >= Long.parseLong(releaseDate);
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            private void downloadAndStoreBundle(OTAUpdate update) throws IOException {
                Request request = new Request.Builder()
                        .url("https://192.168.1.231:5111" + update.bundleUrl)
                        .build();
                Response response = client.newCall(request).execute();
                if (response.body() == null) throw new IOException("Empty bundle");
                byte[] bytes = response.body().bytes();
                File zipFile = new File(storageDir, "newversion.zip");
                try (FileOutputStream fos = new FileOutputStream(zipFile)) {
                    fos.write(bytes);
                }
                if (!update.sha256.equals(sha256(bytes))) {
                    throw new IOException("SHA256 mismatch");
                }
            }

            private void extractBundle() throws IOException {
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
            }

            private void notifyWebView(OTAUpdate update) {
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
                    return "";
                }
            }
        }

        @PluginMethod
        public void applyUpdate(PluginCall call) {
            executor.execute(() -> {
                try {
                    pendingVersionPath = new File(getContext().getFilesDir(), "new_version").getAbsolutePath();
                    getBridge().getWebView().loadUrl("file://" + pendingVersionPath + "/index.html");
                    validateCheckpoints();
                    // Сохраняем новую версию в SharedPreferences при успешном обновлении
                    prefs.edit().putString(KEY_CURRENT_VERSION, currentVersion).apply();
                    call.resolve();
                } catch (Exception e) {
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
            UpdateConfig config = loadUpdateConfig();
            calledCheckpoints.clear();
            CountDownLatch latch = new CountDownLatch(1);

            // Регистрируем JS функцию для получения чекпоинтов
            getBridge().getWebView().evaluateJavascript(
                    "window.registerCheckpoint = function(name, executionTime) {" +
                            "    window.dispatchEvent(new CustomEvent('checkpoint', { detail: { name: name, executionTime: executionTime } }));" +
                            "};",
                    null
            );

            // Подписываемся на события чекпоинтов
            getBridge().getWebView().evaluateJavascript(
                    "document.addEventListener('checkpoint', function(e) {" +
                            "    Capacitor.Plugins.OTA.checkpoint(e.detail.name, e.detail.executionTime);" +
                            "});",
                    null
            );

            // Ждем завершения таймера или всех чекпоинтов
            executor.execute(() -> {
                try {
                    Thread.sleep(config.timeoutCheckMillis);
                    if (calledCheckpoints.size() < config.requiredCheckpoints.size()) {
                        throw new Exception("Not all checkpoints were called");
                    }
                } catch (Exception e) {
                    rollback();
                    JSObject errorData = new JSObject();
                    errorData.put("error", e.getMessage());
                    notifyListeners("updateFailed", errorData);
                    sendErrorLog(e.getMessage());
                } finally {
                    latch.countDown();
                }
            });

            // Ожидаем завершения проверки
            latch.await(config.timeoutCheckMillis + 1000, TimeUnit.MILLISECONDS);

            // Проверяем, все ли чекпоинты вызваны
            if (calledCheckpoints.size() != config.requiredCheckpoints.size()) {
                throw new Exception("Checkpoint validation failed: not all checkpoints called");
            }

            for (Map.Entry<String, Integer> entry : config.requiredCheckpoints.entrySet()) {
                if (!calledCheckpoints.contains(entry.getKey())) {
                    throw new Exception("Checkpoint " + entry.getKey() + " was not called");
                }
            }

            // Успешное обновление
            currentVersion = new File(pendingVersionPath).getName();
            pendingVersionPath = null;
            notifyListeners("updateSuccess", new JSObject());
        }

        @PluginMethod
        public void checkpoint(PluginCall call) {
            String name = call.getString("name");
            Integer executionTime = call.getInt("executionTime");
            if (name == null || executionTime == null) {
                call.reject("Invalid checkpoint data");
                return;
            }

            try {
                UpdateConfig config = loadUpdateConfig();
                if (config.requiredCheckpoints.containsKey(name)) {
                    if (executionTime == config.requiredCheckpoints.get(name)) {
                        calledCheckpoints.add(name);
                        call.resolve();
                    } else {
                        throw new Exception("Checkpoint " + name + " execution time mismatch");
                    }
                } else {
                    throw new Exception("Unknown checkpoint: " + name);
                }
            } catch (Exception e) {
                rollback();
                JSObject errorData = new JSObject();
                errorData.put("error", e.getMessage());
                notifyListeners("updateFailed", errorData);
                sendErrorLog(e.getMessage());
                call.reject(e.getMessage());
            }
        }

        private UpdateConfig loadUpdateConfig() throws IOException {
            File configFile = new File(getContext().getFilesDir(), "new_version/update.config.json");
            try (java.io.FileReader reader = new java.io.FileReader(configFile)) {
                return gson.fromJson(reader, UpdateConfig.class);
            }
        }

        private void rollback() {
            String currentPath = getContext().getFilesDir().getAbsolutePath() + "/current_version/index.html";
            getBridge().getWebView().loadUrl("file://" + currentPath);
            pendingVersionPath = null;
        }

        @PluginMethod
        public void rollBackUpdate(PluginCall call) {
            rollback();
            notifyListeners("updateRolledBack", new JSObject());
            call.resolve();
        }

        private void sendErrorLog(String error) {
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    "{\"error\":\"" + error + "\",\"timestamp\":\"" + System.currentTimeMillis() + "\"}"
            );
            Request request = new Request.Builder()
                    .url("https://mydomain.com/otalive/log")
                    .post(body)
                    .build();
            executor.execute(() -> {
                try {
                    client.newCall(request).execute();
                } catch (IOException e) {
                    // Логируем
                }
            });
        }
    }