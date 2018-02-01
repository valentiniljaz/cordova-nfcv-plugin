# cordova-nfcv-plugin

Cordova plugin for reading and writing to NFC tags using NfcV protocol (with support for Ionic2+)

Installation:
----------------------------------------------------

Requirements: Make sure you have a working environment for Ionic2+ project or for Cordova project before installing 
the plugin.

Ionic2+:

`ionic plugin add https://github.com/valentiniljaz/cordova-nfcv-plugin`

Cordova (without Ionic2+)

`cordova plugin add https://github.com/valentiniljaz/cordova-nfcv-plugin`

Configuration:
----------------------------------------------------
Plugin configuration is done by changing values in NfcVHandler class (`src/android/NfcVHandler.java`).

Some NFC tags might not support NDEF:

* READ_NDEF: Prevent reading NDEF if tag does not support it.
* NDEF_BLOCK_ADDR: Address of the first block that contains NDEF message encoded with TLV (T = type; L = length; V = value). See attached datasheet 02 (chapters: 3.2 and 4.1) for more info.

The following values should be the same for any tag. If not, you should change to the appropriate values:

* CMD_READ: Code for read command (default: 0x20).
* CMD_WRITE: Code for write command (default: 0x21).
* CMD_FLAGS: Each request specifies some flags (default: 0x02 - High data rate). See attached datasheet 01 (chapter: 19.1) for more info.


Usage:
----------------------------------------------------

#### `NfcV.init: function (success, error));`

Initializes plugin. In most cases not even required, since every request also initializes the adapter.

* success - Function returns "NFC_INIT_OK"
* error - Check error flags below


#### `NfcV.checkNfcVAvailability: function (success, error));`

Check if Nfc hardware is available.

* success - Function returns "NFC_CHECK_OK"
* error - Check error flags below

#### `NfcV.addNdefListener: function (success, error));`

Get notified when ever new device is discovered. Ndef message is sent in event data.

* success - If listener added it returns "NDEF_LISTENER_ADDED"
* error - Check error flags below

You need to add `document.addEventListener` to be notified when a new device is discovered.

```
document.addEventListener('NdefTag', (event) => {
    console.log('Ndef', JSON.parse(event.ndef));
}, true);

NfcV.addNdefListener();
```

#### `NfcV.startListening: function (success, error));`

Starts listening for new "ACTION_TECH_DISCOVERED" intent.

* success - When intent recieved it returns "NFC_INTENT_ACTIVE"
* error - Check error flags below


#### `NfcV.stopListening: function (success, error));`

It disables foreground dispatch. Intents are no longer received.

* success - It returns "NFC_STOP"
* error - Check error flags below

#### `NfcV.transceive: function (request, success, error));`

It is used to dispatch any kind of request against a NFC tag. Request object has to include a full request: flags, block_addr and any data.

* success - It returns response from the request. If it is a read request it returns the read data. If it is a write request it returns write response.
* error - Check error flags below


#### `NfcV.readBlock: function (blockAddr, success, error));`

Reads one block from `blockAddr`.

* success - It returns bytes read from block at `blockAddr` along with response flags
* error - Check error flags below


#### `NfcV.writeBlock: function (blockAddr, blockData, success, error));`

Writes `blockData` into one block at `blockAddr`.

* success - It returns bytes from write response (error flag and any error code)
* error - Check error flags below


#### ERRORs

* `E_NO_NFC` - NFC is not supported
* `E_NFC_DISABLED` - NFC is not enabled
* `E_NULL_TAG` - Tag returned NULL
* `E_ADDR_TOO_LONG` - Block addr is too long (more than 2 bytes)

#### Datasheets

* Datasheet 01: Specs for M24LR04E-R (01__Specs__M24LR04E-R)
	- chapters: 19, 20, 26
* Datasheet 02: Using M24LR04E-R (02__Using__M24LR04E-R)
	- chapters: 3, 4

#### AndroidManifest.xml

Add the following intent filters inside `activity`:

```
<intent-filter>
    <action android:name="android.nfc.action.NDEF_DISCOVERED" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
<intent-filter>
    <action android:name="android.nfc.action.TECH_DISCOVERED" />
</intent-filter>
<meta-data android:name="android.nfc.action.TECH_DISCOVERED" android:resource="@xml/nfc_tech_filter" />
```
### nfc_tech_filter

Create new file within `platforms/android/res/xml/nfc_tech_filter.xml`:

```
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <tech-list>
        <tech>android.nfc.tech.NfcV</tech>
    </tech-list>
</resources>
```

Ionic2+ service: NfcvService
----------------------------------------------------

[More about Ionic2+ support ...](./ionic2/readme.md)
