package fr.epita.assistants.ping.login;

import fr.epita.assistants.ping.data.model.ProjectModel;
import fr.epita.assistants.ping.errors.ErrorsCode;
import fr.epita.assistants.ping.repository.ProjectRepository;
import fr.epita.assistants.ping.repository.UserRepository;
import fr.epita.assistants.ping.utils.Logger;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@jakarta.ws.rs.Path("/api/projects/{projectId}/folders")
@Produces(MediaType.APPLICATION_JSON)
public class FolderResource {

    @ConfigProperty(name = "PROJECT_DEFAULT_PATH")
    String baseDir;

    @Inject
    ProjectRepository projectRepo;

    @Inject
    UserRepository userRepo;

    @Inject
    Logger logger;

    @Context
    SecurityContext securityContext;

    private UUID getCurrentUserId() {
        return UUID.fromString(securityContext.getUserPrincipal().getName());
    }

    private Path safeResolveAndAuthorize(UUID projectId, String relPath) {
        ProjectModel project = projectRepo.findById(projectId)
                .orElseThrow(() -> {
                    logger.error(String.format("Path is not a Folder. ID: %s", securityContext.getUserPrincipal().getName()));
                    return ErrorsCode.PATH_NOT_FOUND.get();
                });
        UUID userId = getCurrentUserId();
        if (!project.isMember(userId) && !userRepo.isAdmin(securityContext)) {
            logger.error(String.format("Not have access right: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.ACCESS_DENIED.get();
        }
        Path root = Paths.get(baseDir, projectId.toString()).normalize();
        final Path target;
        try {
            target = root.resolve(relPath == null ? "" : relPath).normalize();
        } catch (InvalidPathException e) {
            logger.error(String.format("Invalid Path, ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_INVALID.get();
        }
        if (!target.startsWith(root)) {
            logger.error(String.format("Invalid Path, ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_INVALID.get();
        }
        return target;
    }

    private void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }


    @GET
    @Authenticated
    public Response listFolders(
            @PathParam("projectId") UUID projectId,
            @QueryParam("path") @DefaultValue("") String relativePath
    ) {
        Path target = safeResolveAndAuthorize(projectId, relativePath);
        if (!Files.isDirectory(target)) {
            logger.error(String.format("Path is not a Folder. ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_INVALID.get();
        }
        try (Stream<Path> children = Files.list(target)) {
            List<String> folders = children
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
            logger.info(String.format("Folder Listed at %s, ID : %s, %s", relativePath, securityContext.getUserPrincipal().getName(), projectId));
            return Response.ok(folders).build();
        } catch (NoSuchFileException e) {
            logger.error(String.format("Folder Not Found ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_NOT_FOUND.get();
        } catch (IOException e) {
            logger.error(String.format("Internal Error ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.INTERNAL_ERROR.get();
        }
    }


    @POST
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createFolder(
            @PathParam("projectId") UUID projectId,
            PathRequest req
    ) {
        Path target = safeResolveAndAuthorize(projectId, req.getRelativePath());
        if (Files.exists(target)) {
            logger.error(String.format("Cant Create Folder. ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_INVALID.get();
        }
        try {
            Files.createDirectories(target);
            URI location = UriBuilder.fromPath("/api/projects/{projectId}/folders")
                    .queryParam("path", req.getRelativePath())
                    .build(projectId);
            logger.info(String.format("Folder Created at %s, ID : %s, %s", req.relativePath, securityContext.getUserPrincipal().getName(), projectId.toString()));
            return Response.created(location).build();
        } catch (NoSuchFileException e) {
            logger.error(String.format("Folder Not Found ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_NOT_FOUND.get();
        } catch (SecurityException e) {
            logger.error(String.format("Not have access right: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.ACCESS_DENIED.get();
        } catch (IOException e) {
            logger.error(String.format("Internal Error ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.INTERNAL_ERROR.get();
        }
    }

    @DELETE
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteFolder(
            @PathParam("projectId") UUID projectId,
            PathRequest req
    ) {
        Path target = safeResolveAndAuthorize(projectId, req.getRelativePath());
        if (!Files.isDirectory(target)) {
            logger.error(String.format("Path is not a Folder. ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_NOT_FOUND.get();
        }
        try {
            Path root = Paths.get(baseDir, projectId.toString()).normalize();
            if (target.equals(root)) {
                try (Stream<Path> children = Files.list(root)) {
                    children.forEach(child -> {
                        try {
                            deleteRecursively(child);
                        } catch (IOException ignored) {
                        }
                    });
                }
            } else {
                if (!Files.isDirectory(target)) {
                    logger.error(String.format("Path is not a Folder. ID: %s", securityContext.getUserPrincipal().getName()));
                    throw ErrorsCode.PATH_INVALID.get();
                }
                deleteRecursively(target);
            }
            logger.info(String.format("Folder Delete at %s, ID : %s, %s", req.relativePath, securityContext.getUserPrincipal().getName(), projectId));
            return Response.noContent().build();
        } catch (NoSuchFileException e) {
            logger.error(String.format("Folder Not Found ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_NOT_FOUND.get();
        } catch (SecurityException e) {
            logger.error(String.format("Not have access right: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.ACCESS_DENIED.get();
        } catch (IOException e) {
            logger.error(String.format("Internal Error ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.INTERNAL_ERROR.get();
        }
    }


    @PUT
    @jakarta.ws.rs.Path("/move")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public Response moveFolder(
            @PathParam("projectId") UUID projectId,
            MoveRequest req
    ) {
        Path src = safeResolveAndAuthorize(projectId, req.getSrc());
        Path dst = safeResolveAndAuthorize(projectId, req.getDst());
        if (!Files.isDirectory(src) || Files.exists(dst)) {
            logger.error(String.format("Path is not a Folder OR cant Created it. ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_INVALID.get();
        }
        try {
            Files.createDirectories(dst.getParent());
            Files.move(src, dst);
            logger.info(String.format("Folder Moved at %s, ID : %s, %s, %s", req.getDst(), securityContext.getUserPrincipal().getName(), req.src, projectId));
            return Response.noContent().build();
        } catch (NoSuchFileException e) {
            logger.error(String.format("Folder Not Found ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_NOT_FOUND.get();
        } catch (SecurityException e) {
            logger.error(String.format("Not have access right: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.ACCESS_DENIED.get();
        } catch (IOException e) {
            logger.error(String.format("Internal Error ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.INTERNAL_ERROR.get();
        }
    }
}
