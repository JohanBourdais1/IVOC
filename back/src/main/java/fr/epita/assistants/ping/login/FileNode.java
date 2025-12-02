package fr.epita.assistants.ping.login;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileNode {
    private String name;
    private boolean directory;
    private List<FileNode> children;
}
