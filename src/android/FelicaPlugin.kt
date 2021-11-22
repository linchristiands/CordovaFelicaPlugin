package eizaburo.felica.plugin;

import android.widget.Toast
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import org.apache.cordova.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*


class FelicaPlugin : CordovaPlugin() {
    lateinit var context: CallbackContext

    @Throws(JSONException::class)
    override fun execute(action: String, data: JSONArray, callbackContext: CallbackContext): Boolean {
        context = callbackContext
        var result = true
        try {
           if (action.equals("nativeToast")) {
                nativeToast(); 
            }
            else if(action.equals("Hello")) {
                val input = data.getString(0)
                val output = "Kotlin says \"$input\""
                callbackContext.success(output)
            }
            else if(action.equals("startnfc")){
                readNFC();
            }
            else if(action.equals("stopnfc")){
                stopNFC();
            }
            else {
                handleError("Invalid action")
                result = false
            }
        } catch (e: Exception) {
            handleException(e)
            result = false
        }

        return result
    }

    fun bytesToHexString(bytes: ByteArray): String{
        val sb = StringBuilder()
        val fm = Formatter(sb)
        for(b in bytes){
            fm.format("%02x",b)
        }
        return sb.toString()
    }

    @Throws(IOException::class)
    private fun readWithoutEncryption(idm: ByteArray, blocksize: Int): ByteArray? {
        val bout = ByteArrayOutputStream(100) //とりあえず

        //readWithoutEncryptionコマンド組み立て<
        bout.write(0) //コマンド長（後で入れる）
        bout.write(0x06) //0x06はRead Without Encryptionを表す
        bout.write(idm) //8byte:idm
        bout.write(1) //サービス数
        bout.write(0x4f) //サービスコードリスト WAONカード番号は684F
        bout.write(0x68) //サービスコードリスト
        bout.write(blocksize) //ブロック数
        for (i in 0 until blocksize) {
            bout.write(0x80) //ブロックリスト
            bout.write(i)
        }
        val msg: ByteArray = bout.toByteArray()
        msg[0] = msg.size.toByte()
        return msg
    }

    public fun nativeToast(){
        Toast.makeText(webView.getContext(), "Hello World Cordova Plugin in Kotlin", Toast.LENGTH_SHORT).show();
    }
    private fun stopNFC(){
        var nfcAdapter: NfcAdapter = NfcAdapter.getDefaultAdapter(this.cordova.getActivity());
        nfcAdapter.disableReaderMode(this.cordova.getActivity());
        context.success("OK");
    }
    public fun readNFC(){
        var nfcAdapter: NfcAdapter = NfcAdapter.getDefaultAdapter(this.cordova.getActivity());
        val NfcData=JSONObject()
        nfcAdapter.enableReaderMode(this.cordova.getActivity(), object : NfcAdapter.ReaderCallback {
                override fun onTagDiscovered(tag: Tag?) {
                    val nfc = NfcF.get(tag);
                    nfc.connect();
                    val byteArray=ByteArray(6).apply{
                        var i = 0
                        this[i++] = 0x06              // [0] 最初はリクエストパケットのサイズが入る。6byte固定。
                        this[i++] = 0x00       // [1] コマンドコードが入る。
                        this[i++] = 0xFE.toByte()     // [2] システムコードの先頭byteが入る。
                        this[i++] = 0x00    // [3] システムコードの末尾byteが入る。
                        this[i++] = 0x00       // [4] リクエストコードが入る。
                        this[i++] = 0x00
                    }
                    val polling_response=nfc.transceive(byteArray);
                    val idm = Arrays.copyOfRange(polling_response,2,10);
                    val pmm = Arrays.copyOfRange(polling_response,11,19);
                    val idmString = bytesToHexString(idm);
                    val pmmString = bytesToHexString(pmm);
                    val waonno_request = readWithoutEncryption(idm!!,2);
                    val waonno_response = nfc.transceive(waonno_request);
                    val waonno=Arrays.copyOfRange(waonno_response,13,21);
                    val waonnoString=bytesToHexString(waonno);
                    NfcData.put("idm",idmString);
                    NfcData.put("pmm",pmmString);
                    NfcData.put("waonno",waonnoString);
                    Log.d("nfcDataIdm",NfcData.getString("idm"));
                    Log.d("nfcDatapmm",NfcData.getString("pmm"));
                    Log.d("nfcDatawaonno",NfcData.getString("waonno"));
                    context.success(NfcData);
                    nfc.close();
                }
            }, NfcAdapter.FLAG_READER_NFC_F, null);
    }
    private fun handleError(errorMsg: String) {
        try {
            Log.e(TAG, errorMsg)
            context.error(errorMsg)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }

    }

    private fun handleException(exception: Exception) {
        handleError(exception.toString())
    }

    companion object {

        protected val TAG = "HelloKotlin"
    }
}