import { registerPlugin } from '@capacitor/core';

import type { OtaLiveUpdaterPlugin } from './definitions';

const OtaLiveUpdater = registerPlugin<OtaLiveUpdaterPlugin>('OtaLiveUpdater', {
  web: () => import('./web').then((m) => new m.OtaLiveUpdaterWeb()),
});

export * from './definitions';
export { OtaLiveUpdater };
