package fr.epita.assistants.ping.repository;

import fr.epita.assistants.ping.data.model.UserModel;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepository<UserModel> {

    public Optional<UserModel> findByLogin(String login) {
        return find("login", login).firstResultOptional();
    }

    public boolean existsByLogin(String login) {
        return find("login", login).count() > 0;
    }

    public Optional<UserModel> findById(UUID id) {
        return find("id", id).firstResultOptional();
    }

    public boolean isAdmin(SecurityContext securityContext) {
        return securityContext.isUserInRole("admin");
    }
}

