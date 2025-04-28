import Foundation

// Простая структура для хранения информации о версии
struct VersionState: Codable {
    let versionId: String
    let path: String? // nil для "bundle"
}

// Класс для управления персистентным состоянием плагина
class StateManager {
    private let userDefaults = UserDefaults.standard
    private let fileManager = FileManager.default

    // Ключи для UserDefaults
    private let currentVersionKey = "capacitor.updatemanager.currentVersion"
    private let pendingVersionKey = "capacitor.updatemanager.pendingVersion"
    private let pendingCheckpointsKey = "capacitor.updatemanager.pendingCheckpoints"
    private let pendingTimeoutKey = "capacitor.updatemanager.pendingTimeout"
    private let rollbackVersionKey = "capacitor.updatemanager.rollbackVersion" // Версия для отката

    // --- Текущая Активная Версия ---
    var currentVersion: VersionState {
        get {
            if let data = userDefaults.data(forKey: currentVersionKey),
               let decoded = try? JSONDecoder().decode(VersionState.self, from: data) {
                return decoded
            }
            return VersionState(versionId: "bundle", path: nil) // По умолчанию бандл
        }
        set {
            if let encoded = try? JSONEncoder().encode(newValue) {
                userDefaults.set(encoded, forKey: currentVersionKey)
            } else {
                 userDefaults.removeObject(forKey: currentVersionKey) // Удаляем при ошибке
            }
        }
    }

    // --- Ожидающая Верификации Версия ---
     var pendingVersion: VersionState? {
        get {
            if let data = userDefaults.data(forKey: pendingVersionKey),
               let decoded = try? JSONDecoder().decode(VersionState.self, from: data) {
                return decoded
            }
            return nil
        }
        set {
            if let version = newValue, let encoded = try? JSONEncoder().encode(version) {
                userDefaults.set(encoded, forKey: pendingVersionKey)
            } else {
                 userDefaults.removeObject(forKey: pendingVersionKey)
            }
        }
    }

     var pendingRequiredCheckpoints: [String]? {
        get { userDefaults.stringArray(forKey: pendingCheckpointsKey) }
        set { userDefaults.set(newValue, forKey: pendingCheckpointsKey) }
    }

     var pendingTimeoutMillis: Int? {
        get { userDefaults.object(forKey: pendingTimeoutKey) as? Int }
        set { userDefaults.set(newValue, forKey: pendingTimeoutKey) }
    }

     // --- Версия для Отката ---
     var rollbackVersion: VersionState {
        get {
             if let data = userDefaults.data(forKey: rollbackVersionKey),
                let decoded = try? JSONDecoder().decode(VersionState.self, from: data) {
                 return decoded
             }
             return VersionState(versionId: "bundle", path: nil) // По умолчанию откатываемся к бандлу
        }
        set {
             if let encoded = try? JSONEncoder().encode(newValue) {
                 userDefaults.set(encoded, forKey: rollbackVersionKey)
             } else {
                 userDefaults.removeObject(forKey: rollbackVersionKey) // Если ошибка, откатываемся к бандлу
             }
        }
    }

    // --- Утилиты ---

    // Получить полный URL к папке версии в директории Library/LocalData/
    func getFullUrlForVersion(version: VersionState) -> URL? {
        guard let path = version.path, !path.isEmpty else { return nil } // Нет пути для бандла
        guard let libraryDir = fileManager.urls(for: .libraryDirectory, in: .userDomainMask).first else {
             return nil
        }
        // Capacitor обычно использует Library/LocalData/ для DATA директории
        return libraryDir.appendingPathComponent("LocalData", isDirectory: true).appendingPathComponent(path, isDirectory: true)
    }

     // Проверить, существует ли index.html в папке версии
     func checkIndexHtmlExists(for versionUrl: URL?) -> Bool {
         guard let url = versionUrl else { return false }
         let indexHtmlUrl = url.appendingPathComponent("index.html")
         return fileManager.fileExists(atPath: indexHtmlUrl.path)
     }

     // Очистка состояния ожидания
     func clearPendingState() {
         userDefaults.removeObject(forKey: pendingVersionKey)
         userDefaults.removeObject(forKey: pendingCheckpointsKey)
         userDefaults.removeObject(forKey: pendingTimeoutKey)
     }

     // Синхронизация UserDefaults
     func synchronize() {
         userDefaults.synchronize()
     }
}