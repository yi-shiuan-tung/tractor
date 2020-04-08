package io.github.ytung.tractor;

public class InvalidPlayException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidPlayException(String message) {
        super(message);
    }
}
