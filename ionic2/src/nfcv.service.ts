import {Injectable} from '@angular/core';
import {BehaviorSubject, Subject} from 'rxjs/Rx';

declare var NfcV: any;
declare var require: {
    <T>(path: string): T;
    (paths: string[], callback: (...modules: any[]) => void): void;
    ensure: (paths: string[], callback: (require: <T>(path: string) => T) => void) => void;
};

@Injectable()
export class NfcvService {

    private commands = {
        "getSystemInfo": [0x00, 0x2B]
    };
    private ndefRecordWithDeviceName = 0;

    private ndefSubject: BehaviorSubject<any>;
    private ndefError: BehaviorSubject<any>;
    private tagSubject: Subject<any>;
    private tagError: Subject<any>;
    private tagListener = null;

    constructor() {
        this.ndefSubject = new BehaviorSubject(null);
        this.ndefError = new BehaviorSubject(null);
        this.tagSubject = new Subject();
        this.tagError = new Subject();
    }

    // NfcV methods

    public init() {
        return new Promise((resolve, reject) => {
            NfcV.init((success) => {
                resolve(success);
            }, (error) => {
                reject(error);
            });
        });
    }

    public isAvailable() {
        return new Promise((resolve, reject) => {
            NfcV.checkNfcVAvailability((success) => {
                resolve(success);
            }, (error) => {
                reject(error);
            });
        });
    }

    public getSystemInfo(startListen?) {
        console.log('** SYSTEM INFO START **');
        return new Promise((mainResolve, mainReject) => {
            this.startListening(startListen)
                .then(() => {
                    NfcV.transceive(new Uint8Array(this.commands.getSystemInfo),
                        (systemInfoData) => {
                            let systemInfoBytes = new Uint8Array(systemInfoData);
                            console.log('** SYSTEM INFO END **', systemInfoBytes);
                            if (systemInfoBytes[0] !== 0) {
                                if (systemInfoBytes.length >= 1) {
                                    mainReject('E_SYSTEM_INFO_FAILED | ERROR CODE: ' + systemInfoBytes[1]);
                                } else {
                                    mainReject('E_SYSTEM_INFO_FAILED | RESPONSE CODE: ' + systemInfoBytes[0]);
                                }
                            } else {
                                mainResolve(this.Uint8ArraySplice(systemInfoBytes, 0, 1));
                            }
                        },
                        (error) => {
                            mainReject(error);
                        });
                })
                .catch((error) => {
                    mainReject(error);
                });
        });
    }

    public waitForNdef() {
        document.addEventListener('NdefTag', (event) => {
            console.log('NdefTag Event', event);
            let deviceTitle = this.parseNdef(JSON.parse((<any>event).ndef));
            if (deviceTitle == 'UNDEFINED_NDEF' || deviceTitle == 'NDEF_PARSE_ERROR') {
                this.ndefError.next(deviceTitle);
            } else {
                this.ndefSubject.next(deviceTitle);
            }
        }, true);

        NfcV.addNdefListener();
    }

    public onNdef(onSuccess: Function, onError: Function) {
        this.ndefSubject.subscribe((data) => {
            onSuccess(data);
        });
        this.ndefError.subscribe((error) => {
            onError(error);
        });
    }

    public waitForTag(device?) {
        if (this.tagListener === null) {
            this.tagListener = this.startListening(true, device)
                .then((data) => {
                    this.tagListener = null;
                    this.tagSubject.next(data);
                })
                .catch((error) => {
                    this.tagListener = null;
                    this.tagError.next(error);
                });
        }
    }

    public onTag(onSuccess: Function, onError: Function) {
        this.tagSubject.subscribe((data) => {
            onSuccess(data);
        });
        this.tagError.subscribe((error) => {
            onError(error);
        });
    }

    public startListening(startListen?, device?) {
        if (startListen === undefined) {
            startListen = true;
        }

        if (startListen) {
            return new Promise((resolve, reject) => {
                NfcV.startListening(
                    (data) => {
                        if (device !== undefined) {
                            let ndef = this.parseNdef(new Uint8Array(data));
                            if (device.regexp.test(ndef)) {
                                resolve(new Uint8Array(data));
                            } else {
                                reject("E_WRONG_DEVICE_TYPE");
                            }
                        } else {
                            resolve(new Uint8Array(data));
                        }
                    },
                    (error) => {
                        reject(error);
                    }
                );
            });
        } else {
            return new Promise((resolve) => { resolve(null); });
        }
    }

    public stopListening() {
        NfcV.stopListening();
    }
    
    public transceive(data) {
        return new Promise(((resolve, reject) => {
            NfcV.transceive(data, (res) => resolve(new Uint8Array(res)), (err) => reject(err));
        }))
    }

    public read(blocks: any[], startListen?, device?): Promise<any> {
        console.log('** READ START **', blocks);
        let readData = [];
        return new Promise((mainResolve, mainReject) => {
            this.startListening(startListen, device)
                .then(() => {
                    // Create promise that immediately resolves
                    let readPromise = new Promise<void>((readResolve) => {
                        readResolve();
                    });

                    for(let block of blocks) {
                        // Chain read blocks
                        readPromise = readPromise
                            .then(() => {
                                return this.readBlock(block.block, false)
                                    .then((data) => {
                                        readData.push({
                                            "block": block.block,
                                            "data": data
                                        });
                                    });
                            });
                    }

                    // Finally resolve main promise
                    readPromise
                        .then(() => {
                            console.log('** READ END **', readData);
                            mainResolve(readData);
                        })
                        .catch((error) => {
                            mainReject(error);
                        });
                })
                .catch((error) => {
                    mainReject(error);
                });
        });
    }

    public readBlock(block, startListen?): Promise<any> {
        console.log('** READ BLK START **', block);
        return new Promise((mainResolve, mainReject) => {
            this.startListening(startListen)
                .then(() => {
                    NfcV.readBlock(block,
                        (data) => {
                            let dataBytes = new Uint8Array(data);
                            console.log('** READ BLK END **', dataBytes);
                            if (dataBytes[0] !== 0) {
                                mainReject('E_READ_FAILED - BLOCK: ' + block + ' | CODE: ' + dataBytes[1]);
                            } else {
                                mainResolve(this.Uint8ArraySplice(dataBytes, 0, 1));
                            }
                        },
                        (error) => {
                            mainReject(error);
                        }
                    );
                })
                .catch((error) => {
                    mainReject(error);
                });
        });
    }

    public write(blocks: any[], startListen?, device?): Promise<any> {
        console.log('** WRITE START **', blocks);
        let writtenData = [];
        return new Promise((mainResolve, mainReject) => {
            this.startListening(startListen, device)
                .then(() => {
                    // Create promise that immediately resolves
                    let writePromise = new Promise<void>((writeResolve) => {
                        writeResolve();
                    });

                    for(let block of blocks) {
                        // Chain write blocks
                        writePromise = writePromise
                            .then(() => {
                                return this.writeBlock(block.block, block.data, false)
                                    .then((response) => {
                                        writtenData.push({
                                            "block": block.block,
                                            "data": block.data,
                                            "response": response
                                        });
                                    });
                            });
                    }

                    // Finally resolve main promise
                    writePromise
                        .then(() => {
                            console.log('** WRITE END **', writtenData);
                            mainResolve(writtenData);
                        })
                        .catch((error) => {
                            mainReject(error);
                        });
                })
                .catch((error) => {
                    mainReject(error);
                });
        });
    }

    public writeBlock(block, data, startListen?): Promise<any> {
        console.log('** WRITE BLK START **', block, data);
        return new Promise((mainResolve, mainReject) => {
            this.startListening(startListen)
                .then(() => {
                    NfcV.writeBlock(block, data,
                        (response) => {
                            let responseBytes = new Uint8Array(response);
                            console.log('** WRITE BLK END **', responseBytes);
                            if (responseBytes[0] !== 0) {
                                mainReject('E_WRITE_FAILED - BLOCK: ' + block + ' | CODE: ' + responseBytes[1]);
                            } else {
                                mainResolve(responseBytes);
                            }
                        },
                        (error) => {
                            mainReject(error);
                        }
                    );
                })
                .catch((error) => {
                    mainReject(error);
                });
        });
    }

    public readRange(startBlock, endBlock, startListen?, device?) {
        let blocks = [];
        for(let i = startBlock; i <= endBlock; i++) {
            blocks.push({
                block: new Uint8Array([i])
            });
        }
        return this.read(blocks, startListen, device)
            .then((readData) => {
                let allData = [];
                readData.forEach((item) => {
                    for(let i = 0; i < item.data.length; i++) {
                        allData.push(item.data[i]);
                    }
                });
                return new Uint8Array(allData);
            });
    }

    public readUntil(startBlock: Number, checkConditionUntil: Function, maxBlocks?: Number, startListen?) {
        return new Promise((readResolve, readReject) => {
            let blocks = [];

            let readNextAction = (blockIndx) => {
                if (maxBlocks !== undefined && blockIndx > maxBlocks) {
                    // Done reading
                    readResolve(blocks);
                    return;
                }

                let blockAddr = new Uint8Array([ startBlock + blockIndx ]);
                this.readBlock(blockAddr, blockIndx == 0 ? startListen : false)
                    .then((blockData) => {
                        // If checkConditionUntil returns true then reading is not completed
                        if (checkConditionUntil(blockData, blockAddr, blockIndx)) {
                            blocks.push({
                                block: blockAddr,
                                data: blockData
                            });
                            readNextAction(++blockIndx);
                        } else {
                            // If it returns false, reading is done and we can return all blocks
                            blocks.push({
                                block: blockAddr,
                                data: blockData
                            });
                            readResolve(blocks);
                        }
                    })
                    .catch((error) => {
                        readReject(error);
                    });
            };
            readNextAction(0);
        });
    }

    // Helper methods

    public parseNdef(ndef) {
        if (ndef.length < 1) {
            return 'UNDEFINED_NDEF';
        }

        let ndefParser = <any>require('@taptrack/ndef');
        let message;
        try {
            message = ndefParser.Message.fromBytes(ndef);
        } catch(e) {
            return 'NDEF_PARSE_ERROR';
        }

        let records = message.getRecords();
        if (records.length > this.ndefRecordWithDeviceName) {
            let recordContents = ndefParser.Utils.resolveTextRecord(records[this.ndefRecordWithDeviceName]);
            return recordContents.content;
        } else {
            return 'NDEF_PARSE_ERROR';
        }
    }

    public Uint8ArraySplice(arr, starting, deleteCount, elements?) {
        if (arguments.length === 1) {
            return arr;
        }
        starting = Math.max(starting, 0);
        deleteCount = Math.max(deleteCount, 0);
        elements = elements || [];

        const newSize = arr.length - deleteCount + elements.length;
        const splicedArray = new arr.constructor(newSize);

        splicedArray.set(arr.subarray(0, starting));
        splicedArray.set(elements, starting);
        splicedArray.set(arr.subarray(starting + deleteCount), starting + elements.length);
        return splicedArray;
    }

    public byteArrayToInt(byteArray) {
        let value = 0;
        for (let i = byteArray.length - 1; i >= 0; i--) {
            value = (value * 256) + byteArray[i];
        }
        return value;
    };

    public bytesToString(array) {
        var result = "";
        for (var i = 0; i < array.length; i++) {
            result += String.fromCharCode(array[i]);
        }
        return result;
    }

}
