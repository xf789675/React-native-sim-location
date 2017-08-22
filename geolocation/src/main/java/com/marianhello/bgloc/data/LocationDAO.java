package com.marianhello.bgloc.data;

import com.tenforwardconsulting.bgloc.UploadLocationInfo;

import java.util.Collection;
import java.util.List;

public interface LocationDAO {
    public Collection<BackgroundLocation> getAllLocations();
    public Collection<BackgroundLocation> getValidLocations();
    public Long locationsForSyncCount(Long millisSinceLastBatch);
    public Long persistLocation(BackgroundLocation location);
    public Long persistLocation(UploadLocationInfo location);
    public Long persistLocationWithLimit(BackgroundLocation location, Integer maxRows);
    public void deleteLocation(Long locationId);
    public void deleteAllLocations();
}
