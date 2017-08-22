package com.tenforwardconsulting.bgloc;

public class AdaptedLocation {
  private double latitude;
  private double longitude;
  private float accuracy;
  private double altitude;
  private float bearing;
  private String provider;
  private long time;
  private float speed;
  private boolean fromMockProvider;

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }

  public float getAccuracy() {
    return accuracy;
  }

  public void setAccuracy(float accuracy) {
    this.accuracy = accuracy;
  }

  public double getAltitude() {
    return altitude;
  }

  public void setAltitude(double altitude) {
    this.altitude = altitude;
  }

  public float getBearing() {
    return bearing;
  }

  public void setBearing(float bearing) {
    this.bearing = bearing;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

  public float getSpeed() {
    return speed;
  }

  public void setSpeed(float speed) {
    this.speed = speed;
  }

  public boolean isFromMockProvider() {
    return fromMockProvider;
  }

  public void setFromMockProvider(boolean fromMockProvider) {
    this.fromMockProvider = fromMockProvider;
  }
}