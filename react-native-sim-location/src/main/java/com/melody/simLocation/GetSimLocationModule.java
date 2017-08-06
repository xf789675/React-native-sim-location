package com.melody.simLocation;

import android.content.Context;
import android.telephony.CellIdentityGsm;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.TelephonyManager;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Created by melody on 06/08/2017.
 */

public class GetSimLocationModule extends ReactContextBaseJavaModule {

    ReactApplicationContext reactContext;

    public GetSimLocationModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return null;
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        TelephonyManager telManager = (TelephonyManager) this.reactContext.getSystemService(Context.TELEPHONY_SERVICE);
        int phoneType = telManager.getPhoneType();
        List<CellInfo> cellInfoList = telManager.getAllCellInfo();
        for(CellInfo cellInfo : cellInfoList) {
            if(phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                CellInfoGsm gsmCellInfo = (CellInfoGsm) cellInfo;
                CellIdentityGsm identityGsm = gsmCellInfo.getCellIdentity();
                identityGsm.getCid();
                identityGsm.getLac();
            }
        }
        switch(phoneType) {
            case 0:  // none radio
                break;
            case 1: // GSM


        }
    }

}
