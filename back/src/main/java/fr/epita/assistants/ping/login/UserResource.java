package fr.epita.assistants.ping.login;

import fr.epita.assistants.ping.data.model.ProjectModel;
import fr.epita.assistants.ping.errors.ErrorsCode;
import fr.epita.assistants.ping.repository.ProjectRepository;
import fr.epita.assistants.ping.repository.UserRepository;

import java.util.*;

import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.jwt.JwtUtils;
import fr.epita.assistants.ping.utils.Logger;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/api/user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    UserRepository userRepository;

    @Inject
    ProjectRepository projectRepository;

    @Context
    SecurityContext securityContext;

    @Inject
    Logger logger;

    @POST
    @Path("/login")
    @Transactional
    public Response login(LoginRequest request) {
        if (request.login == null || request.password == null) {
            logger.error("The login or password is null");
            ErrorsCode.LOGIN_NULL.throwException();
        }

        var userOpt = userRepository.findByLogin(request.login);
        if (userOpt.isEmpty()) {
            logger.error("Invalid credentials");
            ErrorsCode.LOGIN_INVALID.throwException();
        }

        var user = userOpt.get();
        if (!user.getPassword().equals(request.password)) {
            logger.error("Invalid credentials");
            ErrorsCode.LOGIN_INVALID.throwException();
        }

        String token = JwtUtils.generateToken(user.getId(), user.getIsAdmin());
        String refreshToken = UUID.randomUUID().toString();
        user.setRefreshToken(refreshToken);
        logger.info(String.format("User with id %s just logged in %s %s.", user.getId(), request.login, request.password));
        return Response.ok(new LoginResponse(token, refreshToken)).build();
    }

    @POST
    @Transactional
    public Response createUser(NewUserRequest req) {
        if (req.login == null || req.password == null) {
            ErrorsCode.LOGIN_PASSWORD_INVALID.throwException();
        }

        String regex = "^[^._]+[._][^._]+$";
        if (!req.login.matches(regex)) {
            ErrorsCode.LOGIN_PASSWORD_INVALID.throwException();
        }

        if (userRepository.existsByLogin(req.login)) {
            ErrorsCode.LOGIN_ALREADY_TAKEN.throwException();
        }

        UserModel user = new UserModel();
        user.setLogin(req.login);
        user.setPassword(req.password);

        String[] parts = req.login.split("[._]");
        String name = String.join(" ", Arrays.stream(parts)
                .map(part ->part.substring(0, 1).toUpperCase() + part.substring(1))
                .toArray(String[]::new));
        user.setDisplayName(name);

        user.setIsAdmin(req.isAdmin != null && req.isAdmin);
        user.setAvatar(null);

        userRepository.persist(user);
        return Response.status(200).entity(new UserResponse(
                user.getId(),
                user.getLogin(),
                user.getDisplayName(),
                user.getIsAdmin(),
                user.getAvatar()
        )).build();
    }

    @GET
    @Authenticated
    @Path("/all")
    public Response getAllUsers() {
        List<UserModel> users = userRepository.findAll().list();
        List<UserResponse> res = users.stream()
                        .map(u -> {
                            return new UserResponse(u.getId(), u.getLogin(), u.getDisplayName(), u.getIsAdmin(), u.getAvatar());
                        }).toList();

        logger.info(String.format("%s: Get all users", securityContext.getUserPrincipal().getName()));
        return Response.ok(res).build();
    }

    @GET
    @Path("/{id}")
    @Authenticated
    public Response getUser(@PathParam("id") UUID id) {
        Optional<UserModel> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            logger.error(String.format("%s: User not found", securityContext.getUserPrincipal().getName()));
            ErrorsCode.USER_NOT_FOUND.throwException();
        }
        UserModel user = userOpt.get();
        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        if (!isAdmin && !userId.equals(id.toString())) {
            logger.error(String.format("%s: Data not available", securityContext.getUserPrincipal().getName()));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        logger.info(String.format("%s: Get user %s", securityContext.getUserPrincipal().getName(), user.getId()));

        return Response.ok(new UserResponse(
                user.getId(),
                user.getLogin(),
                user.getDisplayName(),
                user.getIsAdmin(),
                user.getAvatar()
        )).build();
    }

    @DELETE
    @Authenticated
    @Path("/{id}")
    @Transactional
    public Response deleteUser(@PathParam("id") UUID id) {
        Optional<UserModel> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            logger.error(String.format("%s: User not found", securityContext.getUserPrincipal().getName()));
            ErrorsCode.USER_NOT_FOUND.throwException();
        }

        List<ProjectModel> proj = projectRepository.findByOwnerId(id);
        if (!proj.isEmpty()) {
            logger.error(String.format("%s: Not owner of the project", securityContext.getUserPrincipal().getName()));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        logger.info(String.format("%s: Deleted user %s %s %s", securityContext.getUserPrincipal().getName(), userOpt.get().getDisplayName(), userOpt.get().getId(), userOpt.get().getLogin()));
        userRepository.delete(userOpt.get());
        return Response.noContent().build();
    }

    @POST
    @Path("/refresh")
    @Transactional
    public Response refreshToken(RefreshTokenRequest request) {
        if (request.refreshToken == null || request.refreshToken.isBlank()) {
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        Optional<UserModel> userOpt = userRepository.find("refreshToken", request.refreshToken).firstResultOptional();
        if (userOpt.isEmpty()) {
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        UserModel user = userOpt.get();
        String newToken = JwtUtils.generateToken(user.getId(), user.getIsAdmin());

        String newRefreshToken = UUID.randomUUID().toString();
        user.setRefreshToken(newRefreshToken);
        userRepository.persist(user);

        return Response.ok(new LoginResponse(newToken, newRefreshToken)).build();
    }

    @PUT
    @Path("/{id}")
    @Authenticated
    @Transactional
    public Response updateUser(@PathParam("id") UUID id, UpdateUserRequest req) {
        Optional<UserModel> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            logger.error(String.format("%s: User not found", securityContext.getUserPrincipal().getName()));
            ErrorsCode.USER_NOT_FOUND.throwException();
        }

        UserModel user = userOpt.get();

        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        if (!isAdmin && !userId.equals(id.toString()))
        {
            logger.error(String.format("%s: Not available", securityContext.getUserPrincipal().getName()));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        if (req.password != null && !req.password.isBlank()) {
            user.setPassword(req.password);
        }

        if (req.displayName != null && !req.displayName.isBlank()) {
            user.setDisplayName(req.displayName);
        }

        user.setAvatar(req.avatar);

        userRepository.persist(user);
        logger.info(String.format("%s: Updated user %s, %s, %s, %s, %s, %s", securityContext.getUserPrincipal().getName(), user.getDisplayName(), user.getLogin(), user.getId(), req.avatar, req.displayName, req.password));
        return Response.ok(new UserResponse(
                user.getId(),
                user.getLogin(),
                user.getDisplayName(),
                user.getIsAdmin(),
                user.getAvatar()
        )).build();
    }
}

