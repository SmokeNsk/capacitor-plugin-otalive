'use strict';

var core = require('@capacitor/core');

const OtaLiveUpdater = core.registerPlugin('OtaLiveUpdater', {
    web: () => Promise.resolve().then(function () { return web; }).then((m) => new m.OtaLiveUpdaterWeb()),
});

class OtaLiveUpdaterWeb extends core.WebPlugin {
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

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    OtaLiveUpdaterWeb: OtaLiveUpdaterWeb
});

exports.OtaLiveUpdater = OtaLiveUpdater;
//# sourceMappingURL=plugin.cjs.js.map
