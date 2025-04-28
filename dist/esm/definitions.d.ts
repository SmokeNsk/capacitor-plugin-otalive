import type { Plugin, PluginListenerHandle } from '@capacitor/core';
export interface OTAPluginEvents {
    newVersionAvailable: {
        version: string;
        description: string;
    };
    updateFailed: {
        error: string;
    };
    updateSuccess: object | undefined;
    updateRolledBack: object | undefined;
}
export interface CheckpointData {
    name: string;
    executionTime: number;
}
export interface OtaLiveUpdaterPlugin extends Plugin {
    applyUpdate(): Promise<void>;
    rollBackUpdate(): Promise<void>;
    checkpoint(data: CheckpointData): Promise<void>;
    addListener(eventName: 'newVersionAvailable', listenerFunc: (data: {
        version: string;
        description: string;
    }) => void): Promise<PluginListenerHandle>;
    addListener(eventName: 'updateFailed', listenerFunc: (data: {
        error: string;
    }) => void): Promise<PluginListenerHandle>;
    addListener(eventName: 'updateSuccess', listenerFunc: (data: object) => void): Promise<PluginListenerHandle>;
    addListener(eventName: 'updateRolledBack', listenerFunc: (data: object) => void): Promise<PluginListenerHandle>;
}
declare module '@capacitor/core' {
    interface PluginRegistry {
        OtaLiveUpdater: OtaLiveUpdaterPlugin;
    }
}
