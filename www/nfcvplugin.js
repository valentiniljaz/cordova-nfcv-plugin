var NfcV = {
    init: function(success, error) {
        cordova.exec(success, error, "NfcVPlugin", "init", []);
    },
    checkNfcVAvailability: function(success, error) {
        cordova.exec(success, error, "NfcVPlugin", "checkNfcVAvailability", []);
    },
    addNdefListener: function(success, error) {
        cordova.exec(success, error, "NfcVPlugin", "addNdefListener", []);
    },
    startListening: function(success, error) {
        cordova.exec(success, error, "NfcVPlugin", "startListening", []);
    },
    stopListening: function(success, error) {
        cordova.exec(success, error, "NfcVPlugin", "stopListening", []);
    },
    transceive: function(request, success, error) {
        cordova.exec(success, error, "NfcVPlugin", "transceive", [request]);
    },
    readBlock: function(blockAddr, success, error) {
        cordova.exec(success, error, "NfcVPlugin", "readBlock", [blockAddr]);
    },
    writeBlock: function(blockAddr, blockData, success, error) {
        cordova.exec(success, error, "NfcVPlugin", "writeBlock", [blockAddr, blockData]);
    }
};
module.exports = NfcV;