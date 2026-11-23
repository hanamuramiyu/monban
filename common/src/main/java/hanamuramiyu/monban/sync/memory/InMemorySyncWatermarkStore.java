package hanamuramiyu.monban.sync.memory;

import hanamuramiyu.monban.sync.SyncStoreException;
import hanamuramiyu.monban.sync.SyncWatermark;
import hanamuramiyu.monban.sync.SyncWatermarkStore;

import java.util.Optional;

public final class InMemorySyncWatermarkStore implements SyncWatermarkStore {
    private SyncWatermark stored;
    private int saveCount;
    private boolean failNextSave;

    @Override
    public synchronized Optional<SyncWatermark> load() {
        return Optional.ofNullable(stored);
    }

    @Override
    public synchronized void save(SyncWatermark watermark) {
        if (failNextSave) {
            failNextSave = false;
            throw new SyncStoreException("Injected watermark save failure.");
        }
        stored = watermark;
        saveCount++;
    }

    public synchronized void failNextSave() {
        failNextSave = true;
    }

    public synchronized int saveCount() {
        return saveCount;
    }
}
