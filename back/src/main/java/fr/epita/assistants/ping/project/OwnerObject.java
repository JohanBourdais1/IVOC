package fr.epita.assistants.ping.project;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OwnerObject {
    public UUID id;
    public String displayName;
    public String avatar;
}
