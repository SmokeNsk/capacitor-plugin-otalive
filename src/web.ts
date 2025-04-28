import { WebPlugin } from '@capacitor/core';

import type { CheckpointData, OtaLiveUpdaterPlugin } from './definitions';

 
export class OtaLiveUpdaterWeb extends WebPlugin implements OtaLiveUpdaterPlugin {
  applyUpdate(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  rollBackUpdate(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  checkpoint(data: CheckpointData): Promise<void> {
    throw new Error('Method not implemented.'+data.name);
  }
 
 
}
