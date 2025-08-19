package brs.events;

public class NetVolumeChangedEvent {
    private final long uploadedVolume;
    private final long downloadedVolume;

    /*
     * NetVolumeChangedEvent is an event that carries information about the change
     * in network volume.
     * It contains the total uploaded and downloaded volume since the last event.
     * This event can be used to notify subscribers about changes in network usage.
     * * @param uploadedVolume The total volume of data uploaded since the last
     * event.
     * * @param downloadedVolume The total volume of data downloaded since the last
     * event.
     */
    public NetVolumeChangedEvent(long uploadedVolume, long downloadedVolume) {
        this.uploadedVolume = uploadedVolume;
        this.downloadedVolume = downloadedVolume;
    }

    public long getUploadedVolume() {
        return uploadedVolume;
    }

    public long getDownloadedVolume() {
        return downloadedVolume;
    }
}