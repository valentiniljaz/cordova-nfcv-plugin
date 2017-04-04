# Ionic2 NfcV Service

Service provides helpers methods for accessing NfcV tags on Android

## Install

```npm install https://github.com/valentiniljaz/cordova-nfc-plugin```

Add provider for NfcvService within your module:

```
import {NfcvService} from 'cordova-nfc-plugin/ionic2';

@NgModule({
  providers: [
    NfcvService
  ]
})
export class MyModule {}

```

## Example

app.component.ts
```
import {Component, OnInit} from '@angular/core';
import { Platform } from 'ionic-angular';
import { StatusBar } from '@ionic-native/status-bar';
import { SplashScreen } from '@ionic-native/splash-screen';
import { TabsPage } from '../pages/tabs/tabs';
import { NfcvService } from 'cordova-nfc-plugin/ionic2';

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

      nfcvService.addNdefListener();
    });
  }

  ngOnInit() {
    this.nfcvService.onTag().subscribe((tag) => {
      //Avoid reading null tag on first call
      if (tag) {
        console.log('Found tag:', tag);
        this.tag = tag;
      }
    });
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

1) In constructor I setup NdefListener and within `ngOnInit` I subscribe to Ndef messages.
2) Once a Nfc intent is received (phone will beep) its Ndef message is sent over to `onTag`.
3) In `onTag` I set the property `tag` to the current read tag which is then displayed in view.
3) View has a button which calls `read` method when tapped.
4) `NfcvService.read` method waits for another Nfc intent (phone will beep again). Once an intent is received it reads data from specified addresses and returns read data. You will notice that when this intent is received `onTag` will also receive new event (but the same tag).

The idea for NdefListener is to just handle Ndef messages and nothing else. Once a tag is received within `onTag` you display specific options for the received tag. Then the user chooses an option which will be executed against that tag. Ndef listener is separated from read/write operations. With NdefListener you receive an event each time a tag's Ndef message is read, and that's it. If you want to read from that device, Android has to dispatch new intent.

## NfcvService.addNdefListener

In order to get notified whenever a new tag is discovered you need to setup two things:

1) Within `platform.ready()` setup ndefListener: 
```
nfcvService.addNdefListener();
```
This method will add new event listener for Ndef messages. Whenever a new tag is discovered it will push new event down the observable stream. You can subscribe to this stream and you'll be notified whenever a tag is discovered.

2) Subscribe to obsevable:
```
this.nfcvService.onTag().subscribe((tag) => {
    if (tag !== null) {
        console.log('Tag', tag);
    }
});
```
Since this is BehaviorSubject the first tag will always be `null`. You can subscribe within `ngOnInit` method or basically any other as well.

`addNdefListener` method starts listening for Ndef messages. Whenever it receives a Ndef message it dispatches an event. You can listen to the event by subscribing with `onTag` method. Cordova plugin sends Ndef message as byte array so you need to parse the array in order to retrieve Ndef message. Method `parseNdef` is used to parse Ndef messages. At the moment `parseNdef` only understands messages advertised by Nfcv hardware refered to in attached datasheet.

Complete example:
```
import {Component, OnInit} from '@angular/core';
import {Platform} from 'ionic-angular';
import {StatusBar, Splashscreen, Device} from 'ionic-native';

import {NfcvService} from 'cordova-nfc-plugin/ionic2';

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

            nfcvService.addNdefListener();
        });
    }

    ngOnInit() {
        this.nfcvService.onTag().subscribe((tag) => {
            console.log('Tag', tag);
        });
    }
}

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

## NfcvService.init

`init` method is not required in most cases. Everytime you invoke any other method (addNdefListener, read, write etc.) from Cordova plugin, NfcV adapater is initialized. But sometimes you would like to initilize the adpater to see if the NfcV adapter can be properly setup, in this case you can invoke `init`.

```
import { Component } from '@angular/core';
import { Platform } from 'ionic-angular';
import { StatusBar } from '@ionic-native/status-bar';
import { SplashScreen } from '@ionic-native/splash-screen';

import {NfcvService} from 'cordova-nfc-plugin/ionic2';

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
