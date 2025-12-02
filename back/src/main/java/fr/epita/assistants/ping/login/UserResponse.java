package fr.epita.assistants.ping.login;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    public UUID id;
    public String login;
    public String displayName;
    public Boolean isAdmin;
    public String avatar;
}
