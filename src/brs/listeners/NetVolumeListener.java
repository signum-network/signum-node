package brs.listeners;

public interface NetVolumeListener {
    void onNetVolumeChanged(long uploadedVolume, long downloadedVolume);
}