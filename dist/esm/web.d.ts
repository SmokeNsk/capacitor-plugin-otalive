import { WebPlugin } from '@capacitor/core';
import type { CheckpointData, OtaLiveUpdaterPlugin } from './definitions';
export declare class OtaLiveUpdaterWeb extends WebPlugin implements OtaLiveUpdaterPlugin {
    applyUpdate(): Promise<void>;
    rollBackUpdate(): Promise<void>;
    checkpoint(data: CheckpointData): Promise<void>;
}
