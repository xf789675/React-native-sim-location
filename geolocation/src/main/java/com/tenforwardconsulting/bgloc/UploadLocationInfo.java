package com.tenforwardconsulting.bgloc;

import android.location.Location;

/**
 * Created by melody on 17/08/2017.
 */

public class UploadLocationInfo {
  private String carrierName;
  private String countryIso;
  private int dataRoaming;  // 1 is enabled ; 0 is disabled
  private String displayName;
  private String iccId;
  private int mcc;
  private int mnc;
  private String number;
  private int simSlotIndex;
  private int subscriptionId;
  private boolean networkRoaming;
  private String deviceId;
  private Location location;

  public String getCarrierName() {
    return carrierName;
  }

  public void setCarrierName(String carrierName) {
    this.carrierName = carrierName;
  }

  public String getCountryIso() {
    return countryIso;
  }

  public void setCountryIso(String countryIso) {
    this.countryIso = countryIso;
  }

  public int getDataRoaming() {
    return dataRoaming;
  }

  public void setDataRoaming(int dataRoaming) {
    this.dataRoaming = dataRoaming;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getIccId() {
    return iccId;
  }

  public void setIccId(String iccId) {
    this.iccId = iccId;
  }

  public int getMcc() {
    return mcc;
  }

  public void setMcc(int mcc) {
    this.mcc = mcc;
  }

  public int getMnc() {
    return mnc;
  }

  public void setMnc(int mnc) {
    this.mnc = mnc;
  }

  public String getNumber() {
    return number;
  }

  public void setNumber(String number) {
    this.number = number;
  }

  public int getSimSlotIndex() {
    return simSlotIndex;
  }

  public void setSimSlotIndex(int simSlotIndex) {
    this.simSlotIndex = simSlotIndex;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(int subscriptionId) {
    this.subscriptionId = subscriptionId;
  }

  public boolean isNetworkRoaming() {
    return networkRoaming;
  }

  public void setNetworkRoaming(boolean networkRoaming) {
    this.networkRoaming = networkRoaming;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(String deviceId) {
    this.deviceId = deviceId;
  }

  public Location getLocation() {
    return location;
  }

  public void setLocation(Location location) {
    this.location = location;
  }
}
