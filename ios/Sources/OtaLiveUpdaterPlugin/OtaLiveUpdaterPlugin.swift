import Foundation
import Capacitor
import ZIPFoundation
import BackgroundTasks
/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(OtaLiveUpdaterPlugin)
public class OtaLiveUpdaterPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "OtaLiveUpdaterPlugin"
    public let jsName = "OtaLiveUpdater"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "checkpoint",returnType: CAPPluginReturnPromise)
    ]
    private var currentVersion = "1.0.0"
    private var pendingVersionPath: String?
    private let storageDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    private let taskIdentifier = "team.devapp.capacitor.otalive.versionCheck"
    private var calledCheckpoints: Set<String> = []

    struct OTAUpdate: Codable {
        let version: String
        let date: String
        let releaseDate: String
        let bundleUrl: String
        let description: String
        let sha256: String
    }

    struct UpdateConfig: Codable {
        let requiredCheckpoints: [String: Int]
        let timeoutCheckMillis: Int
    }

    public override func load() {
        registerBackgroundTask()
        scheduleVersionCheck()
    }

    private func registerBackgroundTask() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            self.handleVersionCheck(task: task as! BGAppRefreshTask)
        }
    }

    private func scheduleVersionCheck() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60) // 15 минут

        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Failed to schedule task: \(error)")
        }
    }

    private func handleVersionCheck(task: BGAppRefreshTask) {
        scheduleVersionCheck() // Планируем следующую задачу

        task.expirationHandler = {
            task.setTaskCompleted(success: false)
        }

        Task {
            do {
                let updates = try await fetchLastVersion()
                guard let latestUpdate = updates.first else {
                    task.setTaskCompleted(success: true)
                    return
                }
                if isNewerVersion(latestUpdate.version, currentVersion) {
                    try await downloadAndStoreBundle(latestUpdate)
                }
                if isReleaseDateReached(latestUpdate.releaseDate) {
                    try await extractBundle()
                    notifyWebView(latestUpdate)
                }
                task.setTaskCompleted(success: true)
            } catch {
                task.setTaskCompleted(success: false)
            }
        }
    }

    private func fetchLastVersion() async throws -> [OTAUpdate] {
        let url = URL(string: "https://mydomain.com/otalive/lastversion")!
        let (data, _) = try await URLSession.shared.data(from: url)
        return try JSONDecoder().decode([OTAUpdate].self, from: data)
    }

    private func isNewerVersion(_ newVersion: String, _ currentVersion: String) -> Bool {
        return newVersion > currentVersion
    }

    private func isReleaseDateReached(_ releaseDate: String) -> Bool {
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: releaseDate) else { return false }
        return Date() >= date
    }

    private func downloadAndStoreBundle(_ update: OTAUpdate) async throws {
        let url = URL(string: "https://mydomain.com\(update.bundleUrl)")!
        let (data, _) = try await URLSession.shared.data(from: url)
        let zipFile = storageDir.appendingPathComponent("newversion.zip")
        try data.write(to: zipFile)
        if update.sha256 != data.sha256() { throw NSError(domain: "", code: -1, userInfo: [NSLocalizedDescriptionKey: "SHA256 mismatch"]) }
    }

    private func extractBundle() async throws {
        let zipFile = storageDir.appendingPathComponent("newversion.zip")
        let extractDir = storageDir.appendingPathComponent("new_version")
        try FileManager.default.createDirectory(at: extractDir, withIntermediateDirectories: true)
        try FileManager.default.unzipItem(at: zipFile, to: extractDir)
    }

    private func notifyWebView(_ update: OTAUpdate) {
        notifyListeners("newVersionAvailable", data: [
            "version": update.version,
            "description": update.description
        ])
    }

    @objc func applyUpdate(_ call: CAPPluginCall) {
        Task {
            do {
                pendingVersionPath = storageDir.appendingPathComponent("new_version").path
                webView?.load(URLRequest(url: URL(fileURLWithPath: "\(pendingVersionPath!)/index.html")))
                try await validateCheckpoints()
                call.resolve()
            } catch {
                rollback()
                notifyListeners("updateFailed", data: ["error": error.localizedDescription])
                sendErrorLog(error.localizedDescription)
                call.reject(error.localizedDescription)
            }
        }
    }

    private func validateCheckpoints() async throws {
        let config = try loadUpdateConfig()
        calledCheckpoints.removeAll()

        // Регистрируем JS функцию
        webView?.evaluateJavaScript(
            "window.registerCheckpoint = function(name, executionTime) {" +
            "    window.dispatchEvent(new CustomEvent('checkpoint', { detail: { name: name, executionTime: executionTime } }));" +
            "};" +
            "document.addEventListener('checkpoint', function(e) {" +
            "    Capacitor.Plugins.OTA.checkpoint(e.detail);" +
            "});"
        )

        // Ждем таймаут
        try await Task.sleep(nanoseconds: UInt64(config.timeoutCheckMillis) * 1_000_000)

        // Проверяем, все ли чекпоинты вызваны
        if calledCheckpoints.count != config.requiredCheckpoints.count {
            throw NSError(domain: "", code: -1, userInfo: [NSLocalizedDescriptionKey: "Not all checkpoints were called"])
        }

        for (checkpoint, expectedTime) in config.requiredCheckpoints {
            if !calledCheckpoints.contains(checkpoint) {
                throw NSError(domain: "", code: -1, userInfo: [NSLocalizedDescriptionKey: "Checkpoint \(checkpoint) was not called"])
            }
        }

        // Успех
        currentVersion = URL(fileURLWithPath: pendingVersionPath!).lastPathComponent
        pendingVersionPath = nil
        notifyListeners("updateSuccess", data: [:])
    }

    @objc func checkpoint(_ call: CAPPluginCall) {
        guard let data = call.getObject("detail"),
              let name = data["name"] as? String,
              let executionTime = data["executionTime"] as? Int else {
            call.reject("Invalid checkpoint data")
            return
        }

        do {
            let config = try loadUpdateConfig()
            if let expectedTime = config.requiredCheckpoints[name] {
                if executionTime == expectedTime {
                    calledCheckpoints.insert(name)
                    call.resolve()
                } else {
                    throw NSError(domain: "", code: -1, userInfo: [NSLocalizedDescriptionKey: "Checkpoint \(name) execution time mismatch"])
                }
            } else {
                throw NSError(domain: "", code: -1, userInfo: [NSLocalizedDescriptionKey: "Unknown checkpoint: \(name)"])
            }
        } catch {
            rollback()
            notifyListeners("updateFailed", data: ["error": error.localizedDescription])
            sendErrorLog(error.localizedDescription)
            call.reject(error.localizedDescription)
        }
    }

    private func loadUpdateConfig() throws -> UpdateConfig {
        let configFile = storageDir.appendingPathComponent("new_version/update.config.json")
        let data = try Data(contentsOf: configFile)
        return try JSONDecoder().decode(UpdateConfig.self, from: data)
    }

    private func rollback() {
        webView?.load(URLRequest(url: URL(fileURLWithPath: "\(storageDir.path)/current_version/index.html")))
        pendingVersionPath = nil
    }

    @objc func rollBackUpdate(_ call: CAPPluginCall) {
        rollback()
        notifyListeners("updateRolledBack", data: [:])
        call.resolve()
    }

    private func sendErrorLog(_ error: String) {
        Task {
            var request = URLRequest(url: URL(string: "https://mydomain.com/otalive/log")!)
            request.httpMethod = "POST"
            request.httpBody = try? JSONEncoder().encode(["error": error, "timestamp": ISO8601DateFormatter().string(from: Date())])
            try? await URLSession.shared.data(for: request)
        }
    }
}

extension Data {
    func sha256() -> String {
        // Реализовать вычисление SHA256
        return ""
    }
}