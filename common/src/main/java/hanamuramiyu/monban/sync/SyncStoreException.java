package hanamuramiyu.monban.sync;

/**
 * Unified unchecked error type for sync metadata persistence (watermark, epoch).
 */
public class SyncStoreException extends RuntimeException {
    public SyncStoreException(String message) {
        super(message);
    }

    public SyncStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
