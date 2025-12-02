package fr.epita.assistants.ping.project;

import fr.epita.assistants.ping.data.model.ProjectModel;
import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.errors.ErrorsCode;
import fr.epita.assistants.ping.repository.ProjectRepository;
import fr.epita.assistants.ping.repository.UserRepository;
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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.*;

@Path("/api/projects")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    UserRepository userRepository;

    @Inject
    ProjectRepository projectRepository;

    @ConfigProperty(name = "PROJECT_DEFAULT_PATH")
    String basePath;

    @Inject
    Logger logger;

    @Context
    SecurityContext securityContext;


    @POST
    @Authenticated
    @Transactional
    public Response createProject(CreateProjectRequest request) {
        if ( request == null || request.name == null || request.name.isEmpty()) {
            logger.error("The name is null or empty");
            ErrorsCode.EMPTY_NAME.throwException();
        }
        String userId = securityContext.getUserPrincipal().getName();
        Optional<UserModel> userOpt = userRepository.findById(UUID.fromString(userId));
        if (userOpt.isEmpty()) {
            logger.error(String.format("id : %s, User not found", userId));
            ErrorsCode.USER_NOT_FOUND.throwException();
        }
        ProjectModel project = new ProjectModel();
        project.setName(request.name);
        UserModel owner = userOpt.get();
        project.setOwner(owner);

        project.setPath(basePath);
        project.addMember(owner);

        projectRepository.persist(project);

        if (basePath.endsWith("/"))
            basePath = basePath.substring(0, basePath.length() - 1);
        project.setPath(basePath + "/" + project.getId());

        try {
            Files.createDirectories(Paths.get(project.getPath()));
        } catch (IOException e) {
            logger.error(String.format("id : %s, Internal error", userId));
            ErrorsCode.INTERNAL_ERROR.throwException();
        }

        ProjectResponse p = new ProjectResponse();
        p.setId(project.getId());
        p.setName(request.name);
        p.setOwner(new OwnerObject(
                project.getOwner().getId(),
                project.getOwner().getDisplayName(),
                project.getOwner().getAvatar()
        ));
        p.setMembers(project.getMembers().stream()
                .map(m -> new OwnerObject(
                        m.getId(),
                        m.getDisplayName(),
                        m.getAvatar()
                ))
                .toList());
        logger.info(String.format("%s with id %s has been created by %s.", request.name, project.getId(), userId));
        return Response.ok(p).build();
    }

    @GET
    @Authenticated
    public Response getMyProjects(@QueryParam("onlyOwned") Boolean onlyOwned) {
        UUID userId = UUID.fromString(securityContext.getUserPrincipal().getName());
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            logger.error(String.format("id : %s, Id of the project is wrong.", userId));
            ErrorsCode.USER_NOT_FOUND.throwException();
        }

        List<ProjectModel> owned = projectRepository.findByOwnerId(userId);
        List<ProjectModel> member = projectRepository.findAllByMember(userId);

        List<ProjectModel> projects;
        if (onlyOwned == null)
            onlyOwned = false;
        if (onlyOwned) {
            projects = owned;
        } else {
            Set<ProjectModel> all = new HashSet<>(owned);
            all.addAll(member);
            projects = new ArrayList<>(all);
        }

        List<ProjectResponse> response = projects.stream()
                .map(proj -> {
                    OwnerObject owner = new OwnerObject(
                            proj.getOwner().getId(),
                            proj.getOwner().getDisplayName(),
                            proj.getOwner().getAvatar()
                    );
                    List<OwnerObject> members = proj.getMembers().stream()
                            .map(m -> new OwnerObject(
                                    m.getId(),
                                    m.getDisplayName(),
                                    m.getAvatar()
                            ))
                            .toList();
                    return new ProjectResponse(proj.getId(), proj.getName(), owner, members);
                })
                .toList();
        logger.info(String.format("Got all the projects created by id: %s, %s.", userId, onlyOwned));
        return Response.ok(response).build();
    }

    @GET
    @RolesAllowed("admin")
    @Path("/all")
    public Response getAllProjects() {
        List<ProjectModel> projects = projectRepository.findAll().list();

        List<ProjectResponse> response = projects.stream()
                .map(proj -> {
                    OwnerObject owner = new OwnerObject(
                            proj.getOwner().getId(),
                            proj.getOwner().getDisplayName(),
                            proj.getOwner().getAvatar()
                    );
                    List<OwnerObject> members = proj.getMembers().stream()
                            .map(m -> new OwnerObject(
                                    m.getId(),
                                    m.getDisplayName(),
                                    m.getAvatar()
                            ))
                            .toList();
                    return new ProjectResponse(proj.getId(), proj.getName(), owner, members);
                })
                .toList();

        String userId = securityContext.getUserPrincipal().getName();
        logger.info(String.format("Got all the projects, requested by id: %s.", userId));
        return Response.ok(response).build();
    }

    @GET
    @Authenticated
    @Path("/{id}")
    public Response getProject(@PathParam("id") UUID id) {
        Optional<ProjectModel> projectOpt = projectRepository.findById(id);
        if (projectOpt.isEmpty()) {
            logger.error("Id of the project is wrong.");
            ErrorsCode.PROJECT_NOT_FOUND.throwException();
        }

        ProjectModel project = projectOpt.get();
        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        if (!projectRepository.userHasAccess(id, UUID.fromString(userId), isAdmin)) {
            logger.error(String.format("id : %s, Is not an admin or do not have access to it.", userId));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        ProjectResponse p = new ProjectResponse();
        p.setId(project.getId());
        p.setName(project.getName());
        p.setOwner(new OwnerObject(
                project.getOwner().getId(),
                project.getOwner().getDisplayName(),
                project.getOwner().getAvatar()
        ));
        p.setMembers(project.getMembers().stream()
                .map(m -> new OwnerObject(
                        m.getId(),
                        m.getDisplayName(),
                        m.getAvatar()
                ))
                .toList());
        logger.info(String.format("Got the project with the id %s, requested by id: %s.", project.getId(), userId));

        return Response.ok(p).build();
    }

    @DELETE
    @Authenticated
    @Transactional
    @Path("/{id}")
    public Response deleteProject(@PathParam("id") UUID id) {
        Optional<ProjectModel> projectOpt = projectRepository.findById(id);
        if (projectOpt.isEmpty()) {
            logger.error("Id of the project is wrong.");
            ErrorsCode.PROJECT_NOT_FOUND.throwException();
        }

        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        Optional<UserModel> userOpt = userRepository.findById(UUID.fromString(userId));
        if (userOpt.isEmpty()) {
            logger.error(String.format("id : %s, User not found", userId));
            ErrorsCode.USER_NOT_FOUND.throwException();
        }

        if (!isAdmin && !projectOpt.get().getOwner().getId().toString().equals(userId)) {
            logger.error(String.format("id : %s, Is not an admin or do not have access to it.", userId));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        ProjectModel project = projectOpt.get();

        try {
            java.nio.file.Path projectPath = Paths.get(project.getPath());
            if (Files.exists(projectPath)) {
                Files.walk(projectPath)
                        .sorted(Comparator.reverseOrder())
                        .map(java.nio.file.Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            logger.error(String.format("id : %s, Internal error", userId));
            ErrorsCode.INTERNAL_ERROR.throwException();
        }

        projectRepository.delete(project);
        logger.info(String.format("Deleted the project with the id %s, requested by id: %s.", project.getId(), userId));
        return Response.noContent().build();
    }

    @PUT
    @Authenticated
    @Path("/{id}")
    public Response updateProject(@PathParam("id") UUID id, UpdateProjectRequest request) {
        if (request.name == null && request.newOwnerId == null) {
            logger.error("The name is null or empty");
            ErrorsCode.EMPTY_NAME.throwException();
        }
        Optional<ProjectModel> projectOpt = projectRepository.findById(id);
        if (projectOpt.isEmpty()) {
            logger.error("Id of the project is wrong.");
            ErrorsCode.PROJECT_NOT_FOUND.throwException();
        }

        ProjectModel project = projectOpt.get();
        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        if (!isAdmin && !project.getOwner().getId().toString().equals(userId)) {
            logger.error(String.format("id : %s, Is not an admin or do not have access to it.", userId));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }
        if (request.newOwnerId != null) {
            if (project.getMembers().stream().filter(i -> i.getId().equals(request.newOwnerId)).toList().isEmpty()) {
                logger.error(String.format("id : %s, Id of the project is wrong.", userId));
                ErrorsCode.PROJECT_NOT_FOUND.throwException();
            }
            Optional<UserModel> userOpt = userRepository.findById(request.newOwnerId);
            if (userOpt.isEmpty()) {
                logger.error(String.format("id : %s, User not found", userId));
                ErrorsCode.USER_NOT_FOUND.throwException();
            }
            project.setOwner(userOpt.get());
        }

        if (request.name != null)
            project.setName(request.name);

        ProjectResponse p = new ProjectResponse();
        p.setId(project.getId());
        p.setName(project.getName());
        p.setOwner(new OwnerObject(
                project.getOwner().getId(),
                project.getOwner().getDisplayName(),
                project.getOwner().getAvatar()
        ));
        p.setMembers(project.getMembers().stream()
                .map(m -> new OwnerObject(
                        m.getId(),
                        m.getDisplayName(),
                        m.getAvatar()
                ))
                .toList());
        logger.info(String.format("Updated the project with the id %s, requested by id: %s, %s, %s.", project.getId(), userId, request.name, request.newOwnerId));

        return Response.ok(p).build();
    }

    @POST
    @Authenticated
    @Transactional
    @Path("/{id}/add-user")
    public Response addUser(@PathParam("id") UUID id, AddUserRequest request) {
        if (request.userId == null || request.userId.toString().isEmpty()) {
            logger.error("The id is null or empty");
            ErrorsCode.EMPTY_NAME.throwException();
        }
        Optional<ProjectModel> projectOpt = projectRepository.findById(id);
        if (projectOpt.isEmpty()) {
            logger.error("Id of the project is wrong.");
            ErrorsCode.PROJECT_NOT_FOUND.throwException();
        }
        Optional<UserModel> userOpt = userRepository.findById(request.userId);
        if (userOpt.isEmpty()) {
            logger.error("User not found");
            ErrorsCode.USER_NOT_FOUND.throwException();
        }

        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        if (!projectRepository.userHasAccess(id, UUID.fromString(userId), isAdmin)) {
            logger.error(String.format("id : %s, Is not an admin or do not have access to it.", userId));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        if (!projectOpt.get().getMembers().stream().filter(i -> i.getId().equals(request.userId)).toList().isEmpty()) {
            logger.error(String.format("id : %s, Already in the project.", userId));
            ErrorsCode.USER_ALREADY_IN.throwException();
        }

        projectOpt.get().addMember(userOpt.get());
        projectRepository.persist(projectOpt.get());
        logger.info(String.format("Added the user with id : %s,  to the project with the id %s, requested by id: %s, %s.", userOpt.get().getId(), projectOpt.get().getId(), userId, request.userId));

        return Response.noContent().build();
    }

    @POST
    @Authenticated
    @Transactional
    @Path("/{id}/remove-user")
    public Response removeUser(@PathParam("id") UUID id, AddUserRequest request) {
        if (request.userId == null || request.userId.toString().isEmpty()) {
            logger.error("The id is null or empty");
            ErrorsCode.EMPTY_NAME.throwException();
        }
        Optional<ProjectModel> projectOpt = projectRepository.findById(id);
        if (projectOpt.isEmpty()) {
            logger.error("Id of the project is wrong.");
            ErrorsCode.PROJECT_NOT_FOUND.throwException();
        }
        Optional<UserModel> userOpt = userRepository.findById(request.userId);
        if (userOpt.isEmpty()) {
            logger.error("User not found");
            ErrorsCode.USER_NOT_FOUND.throwException();
        }

        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        if (projectOpt.get().getOwner().getId().equals(request.userId) || (!isAdmin && !projectOpt.get().getOwner().getId().toString().equals(userId))) {
            logger.error(String.format("id : %s, Is not an admin or do not have access to it.", userId));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        if (projectOpt.get().getMembers().stream().filter(i -> i.getId().equals(request.userId)).toList().isEmpty()) {
            logger.error(String.format("id : %s, User not found", userId));
            ErrorsCode.USER_NOT_FOUND.throwException();
        }

        projectOpt.get().removeMember(userOpt.get());
        projectRepository.persist(projectOpt.get());
        logger.info(String.format("Removed the user with id : %s,  to the project with the id %s, requested by id: %s, %s.", userOpt.get().getId(), projectOpt.get().getId(), userId, request.userId));

        return Response.noContent().build();
    }

    @POST
    @Authenticated
    @Path("/{id}/exec")
    public Response execGitCommand(@PathParam("id") UUID projectId, ExecuteFeatureRequest req) {
        if (req == null || req.feature == null || req.command == null) {
            logger.error("Invalid request");
            ErrorsCode.PATH_INVALID.throwException();
        }
        Optional<ProjectModel> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            logger.error("Id of the project is wrong.");
            ErrorsCode.PROJECT_NOT_FOUND.throwException();
        }

        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        if (!projectRepository.userHasAccess(projectId, UUID.fromString(userId), isAdmin)) {
            logger.error(String.format("id : %s, Is not an admin or do not have access to it.", userId));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }
        java.nio.file.Path projectRoot;
        try {
            projectRoot = Paths.get(basePath, projectId.toString()).normalize();
            String feat = req.feature.trim().toLowerCase();
            String cmd = req.command.trim().toLowerCase();

            if (!"git".equals(feat)) {
                logger.error(String.format("id : %s, Invalid request", userId));
                ErrorsCode.PATH_INVALID.throwException();
            }

            File repoDir = projectRoot.toFile();

            if ("init".equals(cmd)) {
                Git.init()
                        .setDirectory(repoDir)
                        .call();
            } else {
                if (!Files.isDirectory(projectRoot.resolve(".git"))) {
                    logger.error(String.format("id : %s, Invalid request", userId));
                    ErrorsCode.PATH_INVALID.throwException();
                }

                try (Git git = Git.open(repoDir)) {
                    switch (cmd) {
                        case "add":
                            List<String> addPaths = req.params;
                            if (addPaths == null || addPaths.isEmpty()) {
                                logger.error(String.format("id : %s, Invalid request", userId));
                                ErrorsCode.PATH_INVALID.throwException();
                            }
                            for (String p : addPaths) {
                                java.nio.file.Path target = projectRoot.resolve(p).normalize();
                                if (!target.startsWith(projectRoot) || !Files.exists(target)) {
                                    logger.error(String.format("id : %s, Invalid request", userId));
                                    ErrorsCode.PATH_INVALID.throwException();
                                }
                                git.add().addFilepattern(p).call();
                            }
                            break;

                        case "commit":
                            System.out.println("ddddddd");
                            List<String> commitParams = req.params;
                            if (commitParams == null || commitParams.isEmpty() ||
                                    commitParams.getFirst().trim().isEmpty()) {
                                logger.error(String.format("id : %s, Invalid request", userId));
                                ErrorsCode.PATH_INVALID.throwException();
                            }
                            String message = commitParams.getFirst();
                            git.commit()
                                    .setMessage(message)
                                    .call();
                            break;

                        case "push":
                            String remote = "origin";
                            String branch = "master";
                            List<String> pushParams = req.params;
                            if (pushParams != null && !pushParams.isEmpty()) {
                                remote = pushParams.get(0);
                                if (pushParams.size() > 1) {
                                    branch = pushParams.get(1);
                                }
                            }
                            git.push()
                                    .setRemote(remote)
                                    .setRefSpecs(new RefSpec("HEAD:refs/heads/" + branch))
                                    .call();
                            break;
                        case "remote":
                            String name = "origin";
                            String url = "";
                            if (req.params != null && !req.params.isEmpty()) {
                                if(!req.params.getFirst().trim().isEmpty())
                                    name = req.params.get(0);
                                if (req.params.size() > 1) {
                                    url = req.params.get(1);
                                }
                            }
                            else {
                                logger.error(String.format("id : %s, Invalid request", userId));
                                ErrorsCode.USER_NOT_FOUND.throwException();
                            }
                            System.out.println(String.format("id : %s, %s -> %s", userId, name, url));
                            git.remoteAdd().setName(name).setUri(new URIish(url)).call();
                            break;
                        default:
                            logger.error(String.format("id : %s, Invalid request", userId));
                            ErrorsCode.EMPTY_NAME.throwException();
                    }
                }
            }
            logger.info(String.format("Executed a command, requested by id: %s, %s, %s, %s.", userId, projectId, req.command, req.feature));

            return Response.noContent().build();

        } catch (GitAPIException | IOException | InvalidPathException | URISyntaxException e) {
            logger.error(String.format("id : %s, Internal error", userId));
            throw ErrorsCode.INTERNAL_ERROR.get();
        }

    }

    @DELETE
    @Authenticated
    @Transactional
    @Path("/{name}")
    public Response deleteProject(@PathParam("name") String name) {
        Optional<ProjectModel> projectOpt = projectRepository.findByName(name);
        if (projectOpt.isEmpty()) {
            logger.error("Id of the project is wrong.");
            ErrorsCode.PROJECT_NOT_FOUND.throwException();
        }

        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        Optional<UserModel> userOpt = userRepository.findById(UUID.fromString(userId));
        if (userOpt.isEmpty()) {
            logger.error(String.format("id : %s, User not found", userId));
            ErrorsCode.USER_NOT_FOUND.throwException();
        }

        if (!isAdmin && !projectOpt.get().getOwner().getId().toString().equals(userId)) {
            logger.error(String.format("id : %s, Is not an admin or do not have access to it.", userId));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        ProjectModel project = projectOpt.get();

        try {
            java.nio.file.Path projectPath = Paths.get(project.getPath());
            if (Files.exists(projectPath)) {
                Files.walk(projectPath)
                        .sorted(Comparator.reverseOrder())
                        .map(java.nio.file.Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            logger.error(String.format("id : %s, Internal error", userId));
            ErrorsCode.INTERNAL_ERROR.throwException();
        }

        projectRepository.delete(project);
        logger.info(String.format("Deleted the project with the id %s, requested by id: %s.", project.getId(), userId));
        return Response.noContent().build();
    }

    @GET
    @Authenticated
    @Path("/name/{name}")
    public Response getProject(@PathParam("name") String name) {
        System.out.println(name);
        Optional<ProjectModel> projectOpt = projectRepository.findByName(name);
        if (projectOpt.isEmpty()) {
            logger.error("Id of the project is wrong.");
            System.out.println(String.format("id : %s, Project not found", name));
            ErrorsCode.PROJECT_NOT_FOUND.throwException();
        }

        ProjectModel project = projectOpt.get();
        String userId = securityContext.getUserPrincipal().getName();
        boolean isAdmin = securityContext.isUserInRole("admin");

        if (!projectRepository.userHasAccess(project.getId(), UUID.fromString(userId), isAdmin)) {
            logger.error(String.format("id : %s, Is not an admin or do not have access to it.", userId));
            ErrorsCode.OWNS_PROJECTS.throwException();
        }

        ProjectResponse p = new ProjectResponse();
        p.setId(project.getId());
        p.setName(project.getName());
        p.setOwner(new OwnerObject(
                project.getOwner().getId(),
                project.getOwner().getDisplayName(),
                project.getOwner().getAvatar()
        ));
        p.setMembers(project.getMembers().stream()
                .map(m -> new OwnerObject(
                        m.getId(),
                        m.getDisplayName(),
                        m.getAvatar()
                ))
                .toList());
        logger.info(String.format("Got the project with the id %s, requested by id: %s.", project.getId(), userId));

        return Response.ok(p).build();
    }
}
