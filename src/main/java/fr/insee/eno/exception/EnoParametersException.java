package fr.insee.eno.exception;
public class EnoParametersException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EnoParametersException(String message) {
        super(message);
    }
}