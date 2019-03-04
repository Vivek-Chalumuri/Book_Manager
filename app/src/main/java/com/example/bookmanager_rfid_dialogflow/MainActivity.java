package com.example.bookmanager_rfid_dialogflow;

/*
 *  @author Vivek Chalumuri
 */

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.*;
import android.widget.*;
import android.nfc.*;
import android.app.PendingIntent;
import android.content.*;
import android.os.*;
import java.util.*;
import android.text.TextUtils;
import ai.api.*;
import ai.api.android.AIConfiguration;
import ai.api.android.AIService;
import ai.api.android.AIDataService;
import ai.api.model.*;
import com.google.gson.JsonElement;
import java.util.Map;
import android.speech.tts.TextToSpeech;



public class MainActivity extends AppCompatActivity implements AIListener {

    Context c;
    NfcAdapter nA;
    PendingIntent pI;
    IntentFilter[] tF;
    TextView bI;
    TextView bO;
    TextView bL;
    AIService aS;
    AIDataService aD;
    Button wBL;
    TextView wTR;
    TextToSpeech T;
    private int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        c = this;
        wBL = findViewById(R.id.wBL);
        wTR = findViewById(R.id.wTR);
        bI = findViewById(R.id.bI);
        bO = findViewById(R.id.bO);
        bL = findViewById(R.id.bL);

        final AIConfiguration aC = new AIConfiguration(
                "a5c2d25443224b8a8a5b4f92d9b221d0",
                AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System
        );
        aS = AIService.getService(this, aC);
        aS.setListener(this);
        aD = new AIDataService(this, aC);

        wBL.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            MY_PERMISSIONS_REQUEST_READ_CONTACTS);

                } else {

                    aS.startListening();
                }
            }
        });

        T = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int s) {
                if(s != TextToSpeech.ERROR) {
                    T.setLanguage(Locale.US);
                    T.setSpeechRate(0.7f);
                }
            }
        });

        nA = NfcAdapter.getDefaultAdapter(this);
        if (nA == null) {
            Toast.makeText(this, "Device does not support NFC!", Toast.LENGTH_LONG).show();
            finish();
        }

        readIntent(getIntent());
        pI = PendingIntent.getActivity(
                this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0
        );
        IntentFilter tD = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tD.addCategory(Intent.CATEGORY_DEFAULT);
        tF = new IntentFilter[] { tD };
    }

    public void onResult(final AIResponse rP) {
        Result r = rP.getResult();
        String pS = "";
        if (r.getParameters() != null && !r.getParameters().isEmpty()) {
            for (final Map.Entry<String, JsonElement> entry : r.getParameters().entrySet()) {
                pS += "(" + entry.getKey() + ", " + entry.getValue() + ") ";
            }
        }
        wTR.setText("BACKEND RESPONSE RECEIVED SUCCESSFULLY!\n\n" + "Query: " + r.getResolvedQuery()
                + "\nAction: " + r.getAction() + "\nParameters: " + pS
                + "\nFulfillment: " + r.getFulfillment().getSpeech()
        );
        T.speak(r.getFulfillment().getSpeech(), TextToSpeech.QUEUE_FLUSH, null, null);
        String[] fs = r.getFulfillment().getSpeech().split(" ");
        bO.setText("<Not returned by backend>");
        bL.setText("<Not returned by backend>");
        for (int i=0; i<fs.length; i++) {
            if (fs[i].toLowerCase().equals("owner")) { bO.setText(fs[i+1]); }
            if (fs[i].toLowerCase().equals("room")) { bL.setText(fs[i+1]); }
        }
    }

    @Override
    protected void onNewIntent(Intent iT) {
        setIntent(iT);
        readIntent(iT);
    }

    @Override
    public void onPause(){
        super.onPause();
        nA.disableForegroundDispatch(this);
    }

    @Override
    public void onResume(){
        super.onResume();
        nA.enableForegroundDispatch(this, pI, tF, null);
    }

    @Override
    public void onListeningStarted() {}

    @Override
    public void onListeningCanceled() {}

    @Override
    public void onListeningFinished() {}

    @Override
    public void onAudioLevel(final float l) {}

    @Override
    public void onError(final AIError e) { wTR.setText(e.toString()); }

    private void readIntent(Intent iT) {
        String action = iT.getAction();
        if (action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {

            byte[] b = ((Tag)iT.getParcelableExtra(NfcAdapter.EXTRA_TAG)).getId();
            char[] hA = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
            char[] hC = new char[b.length * 2];
            int t;
            for (int i=0; i<b.length; i++) {
                t = b[i] & 0xFF;
                hC[i*2] = hA[t >>> 4];
                hC[i*2 + 1] = hA[t & 0x0F];
            }
            bI.setText(new String(hC));

            final String qS = "The RFID of this book is " + new String(hC) + ".";
            final String eS = null;
            final String cS = null;

            if (TextUtils.isEmpty(qS) && TextUtils.isEmpty(eS)) {
                onError(new AIError("query or event is invalid!"));
                return;
            }

            new AsyncTask<String, Void, AIResponse>() {
                private AIError aE;

                @Override
                protected AIResponse doInBackground(final String... params) {
                    final AIRequest rQ = new AIRequest();
                    String q = params[0];
                    String e = params[1];

                    if (!TextUtils.isEmpty(q))
                        rQ.setQuery(q);
                    if (!TextUtils.isEmpty(e))
                        rQ.setEvent(new AIEvent(e));
                    final String cS = params[2];
                    RequestExtras rE = null;
                    if (!TextUtils.isEmpty(cS)) {
                        final List<AIContext> c = Collections.singletonList(new AIContext(cS));
                        rE = new RequestExtras(c, null);
                    }
                    try {
                        return aD.request(rQ, rE);
                    } catch (final AIServiceException e1) {
                        aE = new AIError(e1);
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(final AIResponse rP) {
                    if (rP != null) {
                        onResult(rP);
                    } else {
                        onError(aE);
                    }
                }
            }.execute(qS, eS, cS);

            Toast.makeText(c, "RFID read successfully!", Toast.LENGTH_LONG).show();
        }
    }

}
