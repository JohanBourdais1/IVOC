package fr.epita.assistants.ping.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "users")
public class UserModel {
    @Id @GeneratedValue  private UUID id;
    @Column(unique = true) private String login;
    private String password;
    @Column(name = "display_name") private String displayName;
    @Column(name = "avatar") private String avatar;
    @Column(name = "is_admin") private Boolean isAdmin;
    @Column(name = "refresh_token")
    private String refreshToken;
    @ManyToMany(mappedBy = "members")
    private List<ProjectModel> memberProjects = new ArrayList<>();

    @PreRemove
    private void preRemove()
    {
        for (ProjectModel p : memberProjects)
        {
            p.getMembers().remove(this);
        }
    }
}
