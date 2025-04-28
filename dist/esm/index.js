import { registerPlugin } from '@capacitor/core';
const OtaLiveUpdater = registerPlugin('OtaLiveUpdater', {
    web: () => import('./web').then((m) => new m.OtaLiveUpdaterWeb()),
});
export * from './definitions';
export { OtaLiveUpdater };
//# sourceMappingURL=index.js.map