package fr.epita.assistants.ping.errors;

import fr.epita.assistants.ping.utils.HttpError;
import fr.epita.assistants.ping.utils.IHttpError;
import jakarta.ws.rs.core.Response.Status;
import lombok.Getter;

import static jakarta.ws.rs.core.Response.Status.*;


@Getter
public enum ErrorsCode implements IHttpError {
    EXAMPLE_ERROR(BAD_REQUEST, "Example error: %s"),

    LOGIN_NULL(BAD_REQUEST, "The login or the password is null"),
    LOGIN_INVALID(UNAUTHORIZED, "The login/password combination is invalid"),
    LOGIN_PASSWORD_INVALID(BAD_REQUEST, "The login or password is invalid"),
    LOGIN_ALREADY_TAKEN(CONFLICT, "The login is already taken"),
    INVALID_LOGIN_FORMAT(BAD_REQUEST, "Invalid login format"),

    USER_NOT_FOUND(NOT_FOUND, "User not found"),
    OWNS_PROJECTS(FORBIDDEN, "The user is not allowed to access this endpoint, or the user owns projects"),

    CREATE_PROJECT_INVALID(BAD_REQUEST, "The project name is invalid (null or empty for example)"),
    PROJECT_NOT_FOUND(NOT_FOUND, "Project not found"),

    PATH_INVALID(BAD_REQUEST, "The path is not validdddddd"),
    PATH_NOT_FOUND(NOT_FOUND, "The specified path was not found"),
    ACCESS_DENIED(FORBIDDEN, "Access denied to this resource"),

    EMPTY_NAME(BAD_REQUEST, "Name is empty or null"),
    INTERNAL_ERROR(INTERNAL_SERVER_ERROR, "Internal error"),
    INVALID_REQUEST(BAD_REQUEST, "Invalid request"),
    USER_ALREADY_IN(CONFLICT, "User already in project"),
    PROJECTNOTFOUND(BAD_REQUEST, "Project not found");


    private final HttpError error;

    ErrorsCode(Status status, String message) {
        error = new HttpError(status, message);
    }

    @Override
    public RuntimeException get(Object... args) {
        return error.get(args);
    }

    @Override
    public void throwException(Object... args) {
        throw error.get(args);
    }
}