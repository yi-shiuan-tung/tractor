package io.github.ytung.tractor;

public class InvalidKittyException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidKittyException(String message) {
        super(message);
    }
}
