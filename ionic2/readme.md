# Ionic2+ NfcV Service

Service provides helper methods for accessing NfcV tags on Android.

Support for Ionic2+ is optional. You can use the plugin without Ionic2+. If you'll be using Angular 2+ 
(but not Ionic2+) within you project you can still use NfcV Service. There is nothing specific about the plugin that would 
limit its use only to Ionic2+. It's only tested it with Ionic2, but it can be used with any other framework.

## Install

```npm install https://github.com/valentiniljaz/cordova-nfcv-plugin```

Add provider for NfcvService within your module:

```
import {NfcvService} from 'cordova-nfcv-plugin/ionic2';

@NgModule({
  providers: [
    NfcvService
  ]
})
export class MyModule {}

```

If you won't be using Ionic2+ framework but your project will use Angular 2+, you can still
use Nfcv service. Make sure you install NPM dependency and then you just import the service 
and use it.

## Requirements

Make sure you have the latest version of NodeJS and NPM.

Make sure you installed Typescript globally:

`npm install -g typescript`

## Example

app.component.ts
```
import {Component, OnInit} from '@angular/core';
import { Platform } from 'ionic-angular';
import { StatusBar } from '@ionic-native/status-bar';
import { SplashScreen } from '@ionic-native/splash-screen';
import { TabsPage } from '../pages/tabs/tabs';
import { NfcvService } from 'cordova-nfcv-plugin/ionic2';

@Component({
  templateUrl: 'app.html'
})
export class MyApp implements OnInit {
  rootPage:any = TabsPage;
  public tag;

  constructor(platform: Platform, statusBar: StatusBar, splashScreen: SplashScreen, public nfcvService: NfcvService) {
    platform.ready().then(() => {
      // Okay, so the platform is ready and our plugins are available.
      // Here you can do any higher level native things you might need.
      statusBar.styleDefault();
      splashScreen.hide();

      nfcvService.waitForNdef();
    });
  }

  ngOnInit() {
    this.nfcvService.onNdef(
      (tag) => {
        //Avoid reading null tag on first call
        if (tag) {
          console.log('Found tag:', tag);
          this.tag = tag;
        }
      },
      (error) => {
        //Avoid reading null tag on first call
        if (error) {
          console.log('Error tag:', error);
        }
      }
    );
  }

  read() {
    var device = {"regexp": new RegExp('^AIR\-SAVER v[0-9\.]+$', 'i')};
    this.nfcvService.
    read([
      { block: new Uint8Array([0x18]) },
      { block: new Uint8Array([0x19]) }
    ], true, device)
        .then((data) => {
          console.log(data);
        });
  }
}
```
app.html
```
<ion-nav [root]="rootPage"></ion-nav>
<div *ngIf="tag">
    Tag: {{tag}}
    <button ion-button (tap)="read()">READ</button>
</div>
```

1) In constructor I setup Ndef listener and within `ngOnInit` I subscribe to Ndef messages.
2) Once a Nfc intent is received (phone will beep) its Ndef message is sent over to `onNdef`.
3) In `onNdef` I set the property `tag` to the current read tag which is then displayed in view.
3) View has a button which calls `read` method when tapped.
4) `NfcvService.read` method waits for another Nfc intent (phone will beep again). Once an intent is received it reads data from specified addresses and returns read data. You will notice that when this intent is received `onNdef` will also receive new event (but the same tag).

The idea for Ndef listener is to just handle Ndef messages and nothing else. Once a tag is received within `onNdef` you display specific options for the received tag. Then the user chooses an option which will be executed against that tag. Ndef listener is separated from read/write operations. With Ndef listener you receive an event each time a tag's Ndef message is read, and that's it. If you want to read from that device, Android has to dispatch new intent.

## NfcvService.waitForNdef

In order to get notified whenever a new tag is discovered you need to setup two things:

1) Within `platform.ready()` setup Ndef listener: 
```
nfcvService.waitForNdef();
```
This method will add new event listener for Ndef messages. Whenever a new tag is discovered it will push new event down the observable stream. You can subscribe to this stream and you'll be notified whenever a tag is discovered.

2) Subscribe to stream:
```
this.nfcvService.onNdef(
  (tag) => {
    //Avoid reading null tag on first call
    if (tag) {
      console.log('Found tag:', tag);
      this.tag = tag;
    }
  },
  (error) => {
    //Avoid reading null tag on first call
    if (error) {
      console.log('Error tag:', error);
    }
  }
);
```
Since this is BehaviorSubject the first tag will always be `null`. You can subscribe within `ngOnInit` method or basically any other as well.

`waitForNdef` method starts listening for Ndef messages. Whenever it receives a Ndef message it dispatches an event. You can listen to the event by subscribing with `onNdef` method. Cordova plugin sends Ndef message as byte array so you need to parse the array in order to retrieve Ndef message. Method `parseNdef` is used to parse Ndef messages.

Complete example:
```
import {Component, OnInit} from '@angular/core';
import {Platform} from 'ionic-angular';
import {StatusBar, Splashscreen, Device} from 'ionic-native';

import {NfcvService} from 'cordova-nfcv-plugin/ionic2';

@Component({
    templateUrl: 'my-app.html'
})
export class MyApp implements OnInit {
    constructor(    platform: Platform,
                    public nfcvService: NfcvService)
    {
        platform.ready().then(() => {
            StatusBar.styleDefault();
            Splashscreen.hide();

            nfcvService.waitForNdef();
        });
    }

    ngOnInit() {
        this.nfcvService.onNdef((tag) => {
            console.log('Tag', tag);
        });
    }
}

```

## startListen parameter

If you check out `NfcvService.ts` you will notice, that many methods accept parameter `startListen`. 
The parameter tells the methods whether to wait and listen for NfcV intent or to use already 
dispatched intent. In order to execute many requests against a tag in just one go, you need to 
use one NfcV intent. To use only one intent you must manipulate `startListen` parameter, which 
must be `true` only the first time and `false` for all subsequent requests.

```
this.nfcvService.waitForTag(); // Start listening for new NfcV intent
this.nfcvService.onTag(
	(tag) => {
		console.log('Found tag', tag);
		this.nfcvService.getSystemInfo(false) // Use the same NfcV intent
			.then((systemInfo) => {
				console.log('SystemInfo:', systemInfo); 
				// Check the tag's UID and AFI (optionally)
				// If the tag is the one, then:
				this.nfcvService.readRange(0x03, 0x40, false) // Use the same NfcV intent
					.then((data) => {
						console.log('Read data:', data);
						this.nfcvService.waitForTag(); // Start listening for new NfcV intent
					})
					.catch((error) => {
						console.log('Error reading:', error);
						this.nfcvService.waitForTag(); // Start listening for new NfcV intent
					});
			})
			.catch((error) => {
				console.log('Error reading:', error);
				this.nfcvService.waitForTag(); // Start listening for new NfcV intent
			});
	},
	(error) => {
		console.log('Error on tag', error);
		this.nfcvService.waitForTag(); // Start listening for new NfcV intent
	});
```

## NfcvService.read

`read` method accepts array of blocks from which to read data. It returns new array with block addresses and block data.

```
this.nfcvService.read([
    { block: new Uint8Array([0x01]) },
    { block: new Uint8Array([0x02]) }
], true, device)
    .then((data) => {
        console.log(data);
    });
```

`device` object represents the device with which you wish to communicate, it is an optional argument. At the moment `device` must only provide one key 
`regexp`, which is used to match the device Ndef message read by Nfc adapter.

```
var device = {"regexp": new RegExp('<regula-exp>', 'i')};
```

Replace `<regula-exp>` with your regular expression. For example: `^AIR\-SAVER v[0-9\.]+$`.

## NfcvService.write

Method writes data to specified block addresses.

```
this.nfcvService.write([
    { block: new Uint8Array([0x01]]), data: new Uint8Array([0x01, 0x02, 0x03, 0x04]) },
    { block: new Uint8Array([0x02]]), data: new Uint8Array([0x01, 0x02, 0x03, 0x04]) }
], true, device);

```

## NfcvService.readRange

Read data within specified range. Method will return one array with all read data from block in specified range.

```
nfcvService.readRange(0x03, 0x40, true).then((data) => {
  console.log('NfcV Data for blocks: 0x03 - 0x40', data);
});
```

## NfcvService.readUntil

Read data until some condition is met. Method accepts:

* `startBlock` address,
* `checkConditionUntil` callback,
* `maxBlocks` count,
* `startListen`.

Method will read data starting on address `startBlock` until `checkConditionUntil` returns `false` or `maxBlocks` is reached. Callback `checkConditionUntil` is called on each read block with the following arguments:

* `blockData` - read data from block `blockAddr`,
* `blockAddr` - current block address, 
* `blockIndx` - current block index.

```
this.nfcvService.readUntil(
    0x03,
    (data) => {
        if (data[0] == 0 && data[1] == 0) {
            return false;
        } else {
            return true;
        }
    },
    32,
    true
);
```

## NfcvService.waitForTag

Method is used to start listening for NfcV intents.

## NfcvService.onTag

When a NfcV intent is triggered success callback defined within this method will be called. Then you can do some extra work with the tag. After you finish with the tag, you need to dispatch new `waitForTag` in order to receive new notifications (when new tags are discovered).

```
this.nfcvService.waitForTag();
this.nfcvService.onTag(
    (tag) => {
        console.log('Found tag', tag);
        this.nfcvService.readRange(0x03, 0x40, false)
            .then((data) => {
                console.log('Read data:', data);
                this.nfcvService.waitForTag();
            })
            .catch((error) => {
                console.log('Error reading:', error);
                this.nfcvService.waitForTag();
            });
    },
    (error) => {
        console.log('Error on tag', error);
        this.nfcvService.waitForTag();
    });
```

## NfcvService.init

`init` method is not required in most cases. Everytime you invoke any other method (read, write etc.) from Cordova plugin, NfcV adapater is initialized. But sometimes you would like to initilize the adpater to see if the NfcV adapter can be properly setup, in this case you can invoke `init`.

```
import { Component } from '@angular/core';
import { Platform } from 'ionic-angular';
import { StatusBar } from '@ionic-native/status-bar';
import { SplashScreen } from '@ionic-native/splash-screen';

import {NfcvService} from 'cordova-nfcv-plugin/ionic2';

@Component({
  templateUrl: 'my-app.html'
})
export class MyApp {
  constructor(platform: Platform, statusBar: StatusBar, splashScreen: SplashScreen, nfcvService: NfcvService) {
    platform.ready().then(() => {
      statusBar.styleDefault();
      splashScreen.hide();

      nfcvService.init().then((success) => {
        console.log('NfcV OK', success);
      });
    });
  }
}
```

## NfcvService.isAvailable

Simillary to `init` you can use `isAvailable` method to check if NfcV adapter within a smartphone is actually available.

```
nfcvService.isAvailable().then((success) => {
  console.log('NfcV Available', success);
});
```

## NfcvService.getSystemInfo

Read system info data. See attached datasheed 01 chapter 26.12. Method will return byte array with system info.

```
nfcvService.getSystemInfo(true).then((systemInfo) => {
  console.log('NfcV System Info', systemInfo);
});
```

## NfcvService.parseNdef

Helper method for parsing NDEF message from byte array. By default it returns text of first record.


## NfcvService.Uint8ArraySplice

Helper method for splicing Uint8Array.

```
Uint8ArraySplice(arr, starting, deleteCount, elements?)
```

## NfcvService.byteArrayToInt

Convert byte array to integer.


## NfcvService.bytesToString

Convert byte array to string.
