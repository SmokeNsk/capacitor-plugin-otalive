import { WebPlugin } from '@capacitor/core';
export class OtaLiveUpdaterWeb extends WebPlugin {
    applyUpdate() {
        throw new Error('Method not implemented.');
    }
    rollBackUpdate() {
        throw new Error('Method not implemented.');
    }
    checkpoint(data) {
        throw new Error('Method not implemented.' + data.name);
    }
}
//# sourceMappingURL=web.js.map