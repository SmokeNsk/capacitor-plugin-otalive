import type { Plugin, PluginListenerHandle } from '@capacitor/core';

// Интерфейс данных для событий
export interface OTAPluginEvents {
  newVersionAvailable: { version: string; description: string };
  updateFailed: { error: string };
  updateSuccess: object|undefined;
  updateRolledBack: object|undefined;
}

// Интерфейс данных для вызова чекпоинта
export interface CheckpointData {
  name: string;
  executionTime: number;
}

// Интерфейс плагина
export interface OtaLiveUpdaterPlugin extends Plugin {
  // Метод для применения обновления
  applyUpdate(): Promise<void>;

  // Метод для отката обновления
  rollBackUpdate(): Promise<void>;

  // Метод для вызова чекпоинта
  checkpoint(data: CheckpointData): Promise<void>;

  // Подписка на события
  addListener(
    eventName: 'newVersionAvailable',
    listenerFunc: (data: { version: string; description: string }) => void
  ): Promise<PluginListenerHandle> ;

  addListener(
    eventName: 'updateFailed',
    listenerFunc: (data: { error: string }) => void
  ): Promise<PluginListenerHandle> ;

  addListener(
    eventName: 'updateSuccess',
    listenerFunc: (data: object) => void
  ): Promise<PluginListenerHandle> ;

  addListener(
    eventName: 'updateRolledBack',
    listenerFunc: (data: object) => void
  ): Promise<PluginListenerHandle> ;
}

// Объявление плагина
declare module '@capacitor/core' {
  interface PluginRegistry {
    OtaLiveUpdater: OtaLiveUpdaterPlugin;
  }
}