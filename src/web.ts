import { WebPlugin } from '@capacitor/core';

import type { OtaLiveUpdaterPlugin } from './definitions';

export class OtaLiveUpdaterWeb extends WebPlugin implements OtaLiveUpdaterPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
