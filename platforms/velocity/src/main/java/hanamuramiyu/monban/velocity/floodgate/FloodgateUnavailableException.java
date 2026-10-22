package hanamuramiyu.monban.velocity.floodgate;

public final class FloodgateUnavailableException extends RuntimeException {
    public FloodgateUnavailableException(String message) {
        super(message);
    }

    public FloodgateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
