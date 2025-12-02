package fr.epita.assistants.ping.data.model;

import java.util.*;

import jakarta.persistence.*;
import lombok.Generated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cascade;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "projects")
public class ProjectModel {
    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    private String path;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserModel owner;

    @ManyToMany
    @JoinTable(
        name = "project_members",
        joinColumns = @JoinColumn(name = "project" +
                "" +
                "_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<UserModel> members = new ArrayList<>();

    public void addMember(UserModel user) {
        members.add(user);
    }

    public void removeMember(UserModel user) {
        members.remove(user);
    }

    public boolean isMember(UUID userId) {
        return !members.stream().filter(userModel -> userModel.getId().equals(userId)).toList().isEmpty();
    }
}
