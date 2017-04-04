package nfc.plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.nio.ByteBuffer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.tech.*;
import android.nfc.Tag;
import android.widget.Toast;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import android.os.Parcelable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import java.text.MessageFormat;

public class NfcVHandler {

    public static final String NFC_OK = "NFC_OK";
    public static final String NFC_INIT_OK = "NFC_INIT_OK";
    public static final String NFC_CHECK_OK = "NFC_CHECK_OK";
    private static final String NFC_STOP = "NFC_STOP";
    private static final String E_NO_NFC = "E_NO_NFC";
    private static final String E_NFC_DISABLED = "E_NFC_DISABLED";
    private static final String E_NULL_TAG = "E_NULL_TAG";
    private static final String E_ADDR_TOO_LONG = "E_ADDR_TOO_LONG";
    private static final String E_NFC_INTENT_UNKNOWN = "E_NFC_INTENT_UNKNOWN";

    // If tag does not support NDEF, then prevent reading NDEF
    private static final boolean READ_NDEF = true;
    // Address of the first block that contains NDEF message encoded with TLV (T = type; L = length; V = value)
    private static final int NDEF_BLOCK_ADDR = 1;

    // Tags may have different values
    private static final byte CMD_READ = (byte)0x20;
    private static final byte CMD_WRITE = (byte)0x21;
    private static final byte CMD_FLAGS = (byte)0x02;

    private NfcAdapter nfcAdapter;
    private Activity activity;
    private CordovaWebView webView;
    private CallbackContext callbackContext;

    private PendingIntent foregroundPendingIntent;
    private IntentFilter[] foregroundFilters;
    private Intent newIntent;

    private static String javaScriptEventTemplate =
        "var e = document.createEvent(''Event'');\n" +
        "e.initEvent(''NdefTag'');\n" +
        "e.ndef = ''{0}'';\n" +
        "document.dispatchEvent(e);";

    public NfcVHandler() {
        this.getNfcAdapter();
    }

    public NfcVHandler(Activity activity, CordovaWebView webView) {
        this.activity = activity;
        this.webView = webView;

        this.getNfcAdapter();
        this.setupForegroundDispatch(this.activity);
    }

    private void getNfcAdapter() {
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this.getActivity());
    }

    public void setCallbackContext(CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
    }

    public void setup(Activity activity, CallbackContext callbackContext) {
        this.activity = activity;
        this.callbackContext = callbackContext;
        this.setupForegroundDispatch(this.activity);
    }

    public String checkNfcAdapter() {
        if (this.nfcAdapter == null) {
            return E_NO_NFC;
        } else if (!this.nfcAdapter.isEnabled()) {
            return E_NFC_DISABLED;
        } else {
            return NFC_OK;
        }
    }

    public void newIntent(Intent intent) {
        this.newIntent = intent;

        System.out.println("**NFC-PLUGIN - Action: " + intent.getAction());

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            byte[] ndef = NfcVHandler.getNdefPayloadFromNdef(this.newIntent);
            this.webView.sendJavascript(NfcVHandler.getJsEventTemplate(ndef));
            
            this.callbackContext.success(ndef);
        } 
        else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            try {
                byte[] ndef = NfcVHandler.getNdefPayloadFromNfcv(this.newIntent);
                this.webView.sendJavascript(NfcVHandler.getJsEventTemplate(ndef));
                
                this.callbackContext.success(ndef);
            } catch(Exception e) {
                this.callbackContext.error(e.getMessage());
            }
        } 
        else {
            this.callbackContext.error(E_NFC_INTENT_UNKNOWN);
        }
    }

    public static String startIntent(Intent intent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            byte[] ndef = NfcVHandler.getNdefPayloadFromNdef(intent);
            return NfcVHandler.getJsEventTemplate(ndef);
        }
        else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            try {
                byte[] ndef = NfcVHandler.getNdefPayloadFromNfcv(intent);
                return NfcVHandler.getJsEventTemplate(ndef);
            } catch(Exception e) {}
        }
        return "";
    }

    public void stopForegroundDispatch() {
        this.nfcAdapter.disableForegroundDispatch(this.getActivity());
    }

    public void startForegroundDispatch() {
        String[][] foregroundTechList = new String[][]{
            new String [] { NfcV.class.getName() }
        };
        this.nfcAdapter.enableForegroundDispatch(this.getActivity(), this.foregroundPendingIntent, this.foregroundFilters, foregroundTechList);
    }

    /* Cordova methods */

    public void init() {
        this.callbackContext.success(NFC_INIT_OK);
    }

    public void checkNfcVAvailibility() {
        this.callbackContext.success(NFC_CHECK_OK);
    }

    public void startListening() {
        this.startForegroundDispatch();
    }

    public void stopListening() {
        this.stopForegroundDispatch();
        this.callbackContext.success(NFC_STOP);
    }

    public void readBlock(JSONObject blockAddr) throws Exception {
        byte[] readBlockAddr = this.argToBytes(blockAddr);

        byte[] result = NfcVHandler.readNfcVBlock(this.newIntent, readBlockAddr);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
        this.callbackContext.sendPluginResult(pluginResult);
    }

    public void writeBlock(JSONObject blockAddr, JSONObject blockData) throws Exception {
        byte[] writeBlockAddr = this.argToBytes(blockAddr);
        byte[] writeBlockData = this.argToBytes(blockData);

        byte[] result = NfcVHandler.writeNfcVBlock(this.newIntent, writeBlockAddr, writeBlockData);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
        this.callbackContext.sendPluginResult(pluginResult);
    }

    public void transceive(JSONObject jsonRequest) throws Exception {
        byte[] request = this.argToBytes(jsonRequest);
        byte[] result = NfcVHandler.transceiveNfcV(this.newIntent, request);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
        this.callbackContext.sendPluginResult(pluginResult);
    }

    /* Private methods */

    private static byte[] transceiveNfcV(Intent intent, byte[] request) throws Exception {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) {
            throw new Exception(E_NULL_TAG);
        }

        byte[] response = new byte[1];
        NfcV nfcv = NfcV.get(tag);
    
        if (nfcv != null) {
            try {
                nfcv.connect();
                response = nfcv.transceive(request);
            } catch (IOException e) {
                if (e.getMessage().equals("Tag was lost.")) {
                    // Continue, because of Tag bug
                } else {
                    throw e;
                }
            } finally {
                try {
                    nfcv.close();
                } catch (IOException e) {}
            }
        }

        return response;
    }

    private static byte[] readNfcVBlock(Intent intent, byte[] readBlock) throws Exception {
        // Prepare request
        byte[] request = new byte[2 + readBlock.length];
        request[0] = NfcVHandler.getRequestFlags(readBlock);
        request[1] = CMD_READ;
        for (int i = 0; i < readBlock.length; i++) {
            request[2 + i] = readBlock[i];
        }

        return NfcVHandler.transceiveNfcV(intent, request);
    }

    private static byte[] writeNfcVBlock(Intent intent, byte[] writeBlock, byte[] writeData) throws Exception {
        // Prepare request
        byte[] request = new byte[2 + writeBlock.length + writeData.length];
        request[0] = NfcVHandler.getRequestFlags(writeBlock);
        request[1] = CMD_WRITE;
        for (int i = 0; i < writeBlock.length; i++) {
            request[2 + i] = writeBlock[i];
        }
        for (int i = 0; i < writeData.length; i++) {
            request[2 + writeBlock.length + i] = writeData[i];
        }

        return NfcVHandler.transceiveNfcV(intent, request);
    }

    private void setupForegroundDispatch(Activity activity) {
        Intent foregroundIntent = new Intent(activity.getApplicationContext(), activity.getClass());
        foregroundIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        this.foregroundPendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, foregroundIntent, 0);

        IntentFilter filterNdef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
           filterNdef.addDataType("text/plain");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("E_NDEF_FILTER", e);
        }

        IntentFilter filterTech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        try {
           filterTech.addDataType("text/plain");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("E_TECH_FILTER", e);
        }

        this.foregroundFilters = new IntentFilter[]{ filterNdef, filterTech };
    }

    private Activity getActivity() { 
        return this.activity; 
    }

    private Intent getIntent() {
        return this.activity.getIntent();
    }

    private static byte[] getNdefPayloadFromNdef(Intent intent) {
        Parcelable[] messages = intent.getParcelableArrayExtra((NfcAdapter.EXTRA_NDEF_MESSAGES));
        NdefMessage ndefMsg = (NdefMessage)messages[0];
        NdefRecord ndefRecord = ndefMsg.getRecords()[0];
        return ndefRecord.getPayload();
    }

    private static byte[] getNdefPayloadFromNfcv(Intent intent) throws Exception {
        if (READ_NDEF) {
            // Ndef is stored in TLV format (T = type; L = length; V = value)

            byte[] firstBlock = NfcVHandler.readNfcVBlock(intent, new byte[]{ (byte)NDEF_BLOCK_ADDR });
            // Response code [0] must be 0x00
            // For Ndef: T [1] must be 0x03

            if ((byte)firstBlock[0] == 0x00 && (byte)firstBlock[1] == 0x03) {
                int payloadLength = (int)firstBlock[2];
                int payloadIndx = 0;
                byte[] payload = new byte[payloadLength];

                // First byte defines response flags, so we must skip it
                int blockLen = (firstBlock.length - 1);
                int firstBytes = ((payloadLength < blockLen) ? payloadLength : blockLen);
                // Start from second byte assuming we skipped first byte
                for (int i = 2; i < firstBytes; i++) {
                    payload[payloadIndx] = firstBlock[i + 1];
                    payloadIndx++;
                    payloadLength--;
                }

                int ndefBlockIndx = 0;
                byte[] block;
                int bytesLen;
                while(payloadLength > 0) {
                    ndefBlockIndx++;
                    block = NfcVHandler.readNfcVBlock(intent, new byte[]{ (byte)(NDEF_BLOCK_ADDR + ndefBlockIndx) });
                    if ((byte)block[0] == 0x00) {
                        // First byte defines response flags, so we must skip it
                        blockLen = (block.length - 1);
                        bytesLen = ((payloadLength < blockLen) ? payloadLength : blockLen);
                        for (int i = 0; i < bytesLen; i++) {
                            payload[payloadIndx] = block[i + 1];
                            payloadIndx++;
                            payloadLength--;
                        }
                    } else {
                        // Error reading
                        return new byte[1];
                    }
                }

                return payload;
            } else {
                // It's not NDEF
                return new byte[1];
            }
        } else {
            return new byte[1];
        }
    }

    private static String getJsEventTemplate(byte[] ndef) {
        JSONArray ndefJson = NfcVHandler.byteArrayToJSON(ndef);
        String ndefString = ndefJson.toString();
        return MessageFormat.format(NfcVHandler.javaScriptEventTemplate, ndefString);
    }

    private static byte getRequestFlags(byte[] blockAddr) throws Exception {
        if (blockAddr.length > 1) {
            throw new Exception(E_ADDR_TOO_LONG);
        }
        return CMD_FLAGS;
    }

    private static JSONArray byteArrayToJSON(byte[] bytes) {
        JSONArray json = new JSONArray();
        for (byte aByte : bytes) {
            json.put((byte)aByte);
        }
        return json;
    }
    
    private String bytesToHex(byte[] bytes){
        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;

        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v >>> 4];
            hexChars[j*2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }

    private byte[] argToBytes(JSONObject bytes) {
        byte[] readBlock = new byte[1];

        Iterator<String> keys;
        String key;

        try {
            readBlock = new byte[ bytes.length() ];

            keys = bytes.keys();
            while ( keys.hasNext() ) {
                key = (String)keys.next();
                readBlock[ Integer.parseInt(key) ] = (byte)bytes.getInt(key);
            }

        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }

        return readBlock;
    }
}