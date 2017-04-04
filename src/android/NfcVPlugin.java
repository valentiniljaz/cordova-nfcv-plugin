package nfc.plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.apache.cordova.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import nfc.plugin.NfcVHandler;
import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;

public class NfcVPlugin extends CordovaPlugin {

    private static final String FUNC_INIT = "init";
    private static final String FUNC_CHECK_NFCV_AVAILABILITY = "checkNfcVAvailability";
    private static final String FUNC_ADD_NDEF_LISTENER = "addNdefListener";
    private static final String FUNC_START_LISTENING = "startListening";
    private static final String FUNC_STOP_LISTENING = "stopListening";
    private static final String FUNC_TRANSCEIVE = "transceive";
    private static final String FUNC_READ_BLOCK = "readBlock";
    private static final String FUNC_WRITE_BLOCK = "writeBlock";
    
    private boolean pluginInit = false;
    private NfcVHandler handler;
    private String jsEventPending = "";

    private void initPlugin() {
        if (!this.pluginInit) {
            this.handler = new NfcVHandler(this.cordova.getActivity(), this.webView);
            this.pluginInit = true;
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.initPlugin();
        boolean result = true;
        String checkNfc = this.handler.checkNfcAdapter();

        if (checkNfc.equalsIgnoreCase(NfcVHandler.NFC_OK)) {
            this.handler.setCallbackContext(callbackContext);

            try {
                if (action.equalsIgnoreCase(FUNC_INIT)) {
                    this.handler.init();
                } 
                else if (action.equalsIgnoreCase(FUNC_CHECK_NFCV_AVAILABILITY)) {
                    this.handler.checkNfcVAvailibility();
                } 
                else if (action.equalsIgnoreCase(FUNC_ADD_NDEF_LISTENER)) {
                    if (this.jsEventPending.length() > 0) {
                        this.webView.sendJavascript(this.jsEventPending);
                    }
                    callbackContext.success("NDEF_LISTENER_ADDED");
                } 
                else if (action.equalsIgnoreCase(FUNC_START_LISTENING)) {
                    this.handler.startListening();
                } 
                else if (action.equalsIgnoreCase(FUNC_STOP_LISTENING)) {
                    this.handler.stopListening();
                } 
                else if (action.equalsIgnoreCase(FUNC_TRANSCEIVE)) {
                    this.handler.transceive(args.getJSONObject(0));
                } 
                else if (action.equalsIgnoreCase(FUNC_READ_BLOCK)) {
                    this.handler.readBlock(args.getJSONObject(0));
                } 
                else if (action.equalsIgnoreCase(FUNC_WRITE_BLOCK)) {
                    this.handler.writeBlock(args.getJSONObject(0), args.getJSONObject(1));
                } 
                else {
                    callbackContext.error("INVALID_ACTION: " + action);
                    result = false;
                }
            } catch(Exception e) {
                callbackContext.error("NFC_ERROR: " + e.getMessage());
                result = false;
            }

        } else {
            callbackContext.error(checkNfc);
            result = false;
        }

        return result;
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = this.cordova.getActivity().getIntent();
        this.jsEventPending = NfcVHandler.startIntent(intent);
    }
    
    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (this.pluginInit) {
            this.handler.newIntent(intent);
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        if (this.pluginInit) {
            this.handler.stopForegroundDispatch();
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        if (this.pluginInit) {
            this.handler.startForegroundDispatch();
        }
    }
}