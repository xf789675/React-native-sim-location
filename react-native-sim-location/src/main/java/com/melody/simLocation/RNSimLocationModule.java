package com.melody.simLocation;

import android.content.Context;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Created by melody on 06/08/2017.
 */

public class RNSimLocationModule extends ReactContextBaseJavaModule {

  ReactApplicationContext reactContext;

  public RNSimLocationModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNSimLocation";
  }

  @ReactMethod
  public void getSimLocation(Callback success, Callback error) {
    final Map<String, Object> constants = new HashMap<>();
    List<GsmLocation> gsmLocations = new ArrayList<>();
    List<CdmaLocation> cdmaLocations = new ArrayList<>();
    List<GsmLocation> lteLocations = new ArrayList<>();
    List<GsmLocation> wcdmaLocations = new ArrayList<>();
    try {
      TelephonyManager telManager = (TelephonyManager) this.reactContext.getSystemService(Context.TELEPHONY_SERVICE);
      int phoneType = telManager.getPhoneType();
      List<CellInfo> cellInfoList = telManager.getAllCellInfo();
      for (CellInfo cellInfo : cellInfoList) {
        if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
          if (cellInfo instanceof CellInfoGsm) {
            CellInfoGsm gsmCellInfo = (CellInfoGsm) cellInfo;
            CellIdentityGsm identityGsm = gsmCellInfo.getCellIdentity();
            int cid = identityGsm.getCid();
            int lac = identityGsm.getLac();
            if (cid == Integer.MAX_VALUE || lac == Integer.MAX_VALUE)
              continue;
            GsmLocation location = new GsmLocation();
            location.setCid(cid);
            location.setLac(lac);
            gsmLocations.add(location);
          } else if (cellInfo instanceof CellInfoWcdma) {
            CellInfoWcdma wcdmaCellInfo = (CellInfoWcdma) cellInfo;
            CellIdentityWcdma identityWcdma = wcdmaCellInfo.getCellIdentity();
            int cid = identityWcdma.getCid();
            int lac = identityWcdma.getLac();
            if (cid == Integer.MAX_VALUE || lac == Integer.MAX_VALUE)
              continue;
            GsmLocation location = new GsmLocation();
            location.setCid(cid);
            location.setLac(lac);
            wcdmaLocations.add(location);
          } else if (cellInfo instanceof CellInfoLte) {
            CellInfoLte lteCellInfo = (CellInfoLte) cellInfo;
            CellIdentityLte identityLte = lteCellInfo.getCellIdentity();
            int ci = identityLte.getCi();
            int tac = identityLte.getTac();
            if (ci == Integer.MAX_VALUE || tac == Integer.MAX_VALUE)
              continue;
            GsmLocation location = new GsmLocation();
            location.setCid(ci);
            location.setLac(tac);
            lteLocations.add(location);
          }

        } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
          if (cellInfo instanceof CellInfoCdma) {
            CellInfoCdma cdmaCellInfo = (CellInfoCdma) cellInfo;
            CellIdentityCdma identityCdma = cdmaCellInfo.getCellIdentity();
            int lat = identityCdma.getLatitude();
            int lng = identityCdma.getLatitude();
            if (lat == Integer.MAX_VALUE || lng == Integer.MAX_VALUE)
              continue;
            CdmaLocation location = new CdmaLocation();
            location.setLat(lat);
            location.setLng(lng);
            cdmaLocations.add(location);
          }
        }
      }


      if(gsmLocations.size() > 0)
        constants.put("gsm", gsmLocations);
      if(wcdmaLocations.size() > 0)
        constants.put("wcdma", wcdmaLocations);
      if(lteLocations.size() > 0)
        constants.put("lte", lteLocations);
      if(cdmaLocations.size() > 0)
        constants.put("cdma", cdmaLocations);

      ObjectMapper objectMapper = new ObjectMapper();
      String json = objectMapper.writeValueAsString(constants);
      success.invoke(json);
    } catch (Exception e) {
      error.invoke(e.getMessage());
    }
  }

  @Nullable
  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    TelephonyManager telManager = (TelephonyManager) this.reactContext.getSystemService(Context.TELEPHONY_SERVICE);
    int phoneType = telManager.getPhoneType();
    List<CellInfo> cellInfoList = telManager.getAllCellInfo();
    constants.put("length", cellInfoList.size());
    constants.put("max_integer", Integer.MAX_VALUE);
//    Toast.makeText(reactContext, "All Cell Info length: " + cellInfoList.size(), Toast.LENGTH_LONG);
    int gsmCount = 0;
    int cdmaCount = 0;
    int lteCount = 0;
    for (CellInfo cellInfo : cellInfoList) {
      if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
        if(cellInfo instanceof CellInfoGsm) {
          CellInfoGsm gsmCellInfo = (CellInfoGsm) cellInfo;
          CellIdentityGsm identityGsm = gsmCellInfo.getCellIdentity();
          int cid = identityGsm.getCid();
          int lac = identityGsm.getLac();
          constants.put("cid" + gsmCount, cid);
          constants.put("lac" + gsmCount, lac);
          gsmCount++;
        } else if(cellInfo instanceof CellInfoLte) {
          CellInfoLte lteCellInfo = (CellInfoLte) cellInfo;
          CellIdentityLte identityLte = lteCellInfo.getCellIdentity();
          int ci = identityLte.getCi();
          int tac = identityLte.getTac();
          constants.put("ci" + lteCount, ci);
          constants.put("tac" + lteCount, tac);
          lteCount++;
        }

      } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
        if(cellInfo instanceof CellInfoCdma) {
          CellInfoCdma cdmaCellInfo = (CellInfoCdma) cellInfo;
          CellIdentityCdma identityCdma = cdmaCellInfo.getCellIdentity();
          int lat = identityCdma.getLatitude();
          int lng = identityCdma.getLatitude();
          constants.put("lat" + cdmaCount, lat);
          constants.put("lng" + cdmaCount, lng);
          cdmaCount++;
        }
      }
    }

    return constants;

  }

}
