package fr.epita.assistants.ping.project;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    public UUID id;
    public String name;
    public OwnerObject owner;
    public List<OwnerObject> members;
}
