package io.github.hanshs474.kavel;

/**
 * Every failure the service can report, with a {@link Reason} to branch on:
 * wait, reword, or sign in are three different fixes and the message alone
 * does not tell them apart.
 */
public class KavelException extends Exception {
    private static final long serialVersionUID = 1L;

    /** What went wrong, in terms of what the caller should do next. */
    public enum Reason {
        /**
         * The free allowance is spent, for this client id or for this machine
         * today. An API key from https://www.kavel.ai/settings/apikeys lifts it.
         */
        QUOTA,
        /** The content filter refused the prompt or the model failed on it. Reword it; retrying the same text does not help. */
        REJECTED,
        /** The request needs a signed-in account: a model off the free shelf, or video. */
        SIGN_IN,
        /** The API key is invalid or was deleted. */
        AUTH,
        /** The deadline passed before the image was ready. */
        TIMEOUT,
        /** Anything else: a bad argument, a network error, an unexpected response. */
        OTHER
    }

    private final Reason reason;

    public KavelException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public KavelException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** @return what the caller should do about it */
    public Reason reason() {
        return reason;
    }
}
