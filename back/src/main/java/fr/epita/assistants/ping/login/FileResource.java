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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

@jakarta.ws.rs.Path("/api/projects/{projectId}/files")
@Produces(MediaType.APPLICATION_JSON)
public class FileResource {

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

    private FileNode buildTree(Path path) {
        String name = path.getFileName().toString();
        if (Files.isDirectory(path)) {
            List<FileNode> children = new ArrayList<>();
            try (Stream<Path> stream = Files.list(path)) {
                stream.forEach(p -> children.add(buildTree(p)));
            } catch (IOException e) {
                logger.error("Erreur lors de la lecture du dossier {}");
                throw ErrorsCode.INTERNAL_ERROR.get();
            }
            return new FileNode(name, true, children);
        } else {
            return new FileNode(name, false, null);
        }
    }

    @GET
    @jakarta.ws.rs.Path("/tree")
    @Authenticated
    public Response listFilesAndFolders(@PathParam("projectId") UUID projectId) {
        Path root = safeResolveAndAuthorize(projectId, "");
        FileNode tree = buildTree(root);
        logger.info("List full tree for project {} by user {}");
        return Response.ok(tree).build();
    }


    private UUID getCurrentUserId() {
        return UUID.fromString(securityContext.getUserPrincipal().getName());
    }

    private Path safeResolveAndAuthorize(UUID projectId, String relPath) {
        ProjectModel project = projectRepo.findById(projectId)
                .orElseThrow(() -> ErrorsCode.PROJECTNOTFOUND.get());
        UUID userId = getCurrentUserId();
        if (!project.isMember(userId) && !userRepo.isAdmin(securityContext)) {
            throw ErrorsCode.ACCESS_DENIED.get();
        }
        Path root = Paths.get(baseDir, projectId.toString()).normalize();
        Path target;
        try {
            target = root.resolve(relPath == null ? "" : relPath).normalize();
        } catch (InvalidPathException e) {
            throw ErrorsCode.PATH_INVALID.get();
        }
        if (!target.startsWith(root)) {
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
    public Response getFile(
            @PathParam("projectId") UUID projectId,
            @QueryParam("path") @DefaultValue("") String relativePath
    ) {
        Path target = safeResolveAndAuthorize(projectId, relativePath);
        try {
            if (Files.isDirectory(target)) {
                logger.error(String.format("Path is not a file. ID: %s", securityContext.getUserPrincipal().getName()));
                throw ErrorsCode.PATH_NOT_FOUND.get();
            }
            byte[] content = Files.readAllBytes(target);
            String filename = target.getFileName().toString();
            logger.info(String.format("File Get at %s, ID : %s, %s", relativePath, securityContext.getUserPrincipal().getName(), projectId.toString()));
            return Response.ok(content)
                    .type(MediaType.APPLICATION_OCTET_STREAM)
                    //        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
        } catch (NoSuchFileException e) {
            logger.error(String.format("File Not Found ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_NOT_FOUND.get();
        } catch (IOException e) {
            logger.error(String.format("Internal Error ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.INTERNAL_ERROR.get();
        }
    }

    @DELETE
    @Authenticated
    public Response deleteFile(
            @PathParam("projectId") UUID projectId,
            PathRequest req
    ) {
        Path target = safeResolveAndAuthorize(projectId, req.getRelativePath());
        if (Files.isDirectory(target)) {
            logger.error(String.format("Path is not a file. ID: %s", securityContext.getUserPrincipal().getName()));
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
                deleteRecursively(target);
            }
            logger.info(String.format("File Delete at %s, ID : %s, %s", req.getRelativePath(), securityContext.getUserPrincipal().getName(), projectId.toString()));
            return Response.noContent().build();
        } catch (NoSuchFileException e) {
            logger.error(String.format("File Not Found ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_NOT_FOUND.get();
        } catch (AccessDeniedException e) {
            logger.error(String.format("Not have access right: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.ACCESS_DENIED.get();
        } catch (IOException e) {
            logger.error(String.format("Internal Error ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.INTERNAL_ERROR.get();
        }
    }

    @POST
    @Authenticated
    public Response createFile(
            @PathParam("projectId") UUID projectId,
            PathRequest req
    ) {
        Path target = safeResolveAndAuthorize(projectId, req.getRelativePath());
        if (Files.exists(target)) {
            logger.error(String.format("Cant Create File. ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PROJECTNOTFOUND.get();
        }
        try {
            //Files.createDirectories(target.getParent());
            Files.createFile(target);
            URI location = UriBuilder.fromPath("/api/projects/{projectId}/files")
                    .queryParam("path", req.getRelativePath())
                    .build(projectId);
            logger.info(String.format("File Created at %s. ID: %s, %s", req.getRelativePath(), securityContext.getUserPrincipal().getName(), projectId.toString()));
            return Response.created(location).build();
        } catch (NoSuchFileException e) {
            logger.error(String.format("File Not Found ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_NOT_FOUND.get();
        } catch (AccessDeniedException e) {
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
    public Response moveFile(
            @PathParam("projectId") UUID projectId,
            MoveRequest req
    ) {
        Path src = safeResolveAndAuthorize(projectId, req.getSrc());
        Path dst = safeResolveAndAuthorize(projectId, req.getDst());
        if (Files.exists(dst)) {
            logger.error(String.format("Cant Move File. ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_INVALID.get();
        }
        try {
            Files.createDirectories(dst.getParent());
            Files.move(src, dst);
            logger.info(String.format("File Moved at %s. ID: %s, %s, %s", req.getDst(), securityContext.getUserPrincipal().getName(), projectId.toString(), req.src));
            return Response.noContent().build();
        } catch (NoSuchFileException e) {
            logger.error(String.format("File Not Found ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.PATH_NOT_FOUND.get();
        } catch (AccessDeniedException e) {
            logger.error(String.format("Not have access right: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.ACCESS_DENIED.get();
        } catch (IOException e) {
            logger.error(String.format("Internal Error ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.INTERNAL_ERROR.get();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/upload")
    @Authenticated
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response uploadFile(
            @PathParam("projectId") UUID projectId,
            @QueryParam("path") String relativePath,
            InputStream body
    ) {
        Path target = safeResolveAndAuthorize(projectId, relativePath);
        try {
            //Files.createDirectories(target.getParent());
            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
            URI location = UriBuilder.fromPath("/api/projects/{projectId}/files")
                    .queryParam("path", relativePath)
                    .build(projectId);
            logger.info(String.format("File Uploaded at %s ID: %s, %s", relativePath, securityContext.getUserPrincipal().getName(), projectId.toString()));
            return Response.created(location).build();
        } catch (AccessDeniedException e) {
            logger.error(String.format("Not have access right: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.ACCESS_DENIED.get();
        } catch (IOException e) {
            logger.error(String.format("Internal Error ID: %s", securityContext.getUserPrincipal().getName()));
            throw ErrorsCode.INTERNAL_ERROR.get();
        }
    }

    @PUT
    @jakarta.ws.rs.Path("/save")
    @Authenticated
    public Response saveFileContent(
            @PathParam("projectId") UUID projectId,
            @QueryParam("path") String relativePath,
            SaveFileRequest request
    ) {
        System.out.println(request.content);
        if (request == null || request.content == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing file content.")
                    .build();
        }

        Path target = safeResolveAndAuthorize(projectId, relativePath);

        try {
            Files.createDirectories(target.getParent()); // au cas où
            Files.writeString(target, request.content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info(String.format("File Saved at %s by user %s", relativePath, securityContext.getUserPrincipal().getName()));
            return Response.noContent().build();
        } catch (AccessDeniedException e) {
            logger.error("Access denied while saving file: " + e.getMessage());
            throw ErrorsCode.ACCESS_DENIED.get();
        } catch (IOException e) {
            logger.error("Failed to save file: " + e.getMessage());
            throw ErrorsCode.INTERNAL_ERROR.get();
        }
    }

}