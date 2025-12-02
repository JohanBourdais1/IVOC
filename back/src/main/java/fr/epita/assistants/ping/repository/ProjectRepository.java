package fr.epita.assistants.ping.repository;

import fr.epita.assistants.ping.data.model.ProjectModel;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProjectRepository implements PanacheRepository<ProjectModel> {

    public Optional<ProjectModel> findById(UUID id) {
        return find("id", id).firstResultOptional();
    }

    public List<ProjectModel> findByOwnerId(UUID ownerId) {
        return list("owner.id", ownerId);
    }

    public List<ProjectModel> findAllByMember(UUID userId) {
        return list("SELECT p FROM ProjectModel p JOIN p.members m WHERE m.id = ?1", userId);
    }

    public Optional<ProjectModel> findByName(String name) {
        return find("name", name).firstResultOptional();
    }

    public boolean userHasAccess(UUID projectId, UUID userId, boolean isAdmin) {
        if (isAdmin) return true;

        Optional<ProjectModel> project = findById(projectId);
        return project.isPresent() && (
            project.get().getOwner().getId().equals(userId) ||
            project.get().getMembers().stream().anyMatch(u -> u.getId().equals(userId))
        );
    }


}

