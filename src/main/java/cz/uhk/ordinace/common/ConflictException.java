package cz.uhk.ordinace.common;

import java.util.OptionalLong;

public class ConflictException extends RuntimeException {

    private final OptionalLong existingId;

    public ConflictException(String message) {
        super(message);
        this.existingId = OptionalLong.empty();
    }

    public ConflictException(String message, long existingId) {
        super(message);
        this.existingId = OptionalLong.of(existingId);
    }

    public OptionalLong getExistingId() {
        return existingId;
    }
}
