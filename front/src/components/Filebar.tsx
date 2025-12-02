import React from "react";
import "./Filebar.css";
import { getTree, fullUrl } from './Utils';
import { origFetch } from '../App';

async function updateFileTree() {
    let res = await origFetch(fullUrl('/api/projects/'+localStorage.getItem("projectId")+'/files/tree'), {
          method: 'GET',
          headers: { 
            'Authorization': 'Bearer ' + localStorage.getItem("token"),
            'Content-Type': 'application/json' 
          }
        });
      if (res.status == 401) {
        console.log("Unauthorized, refreshing token");
        res = await origFetch(fullUrl('/api/user/refresh'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: localStorage.getItem('refreshToken') }),
        });
        const r = await res.json();
        localStorage.setItem('token', r.token || '');
        localStorage.setItem('refreshToken', r.refreshToken || '');
        res = await origFetch(fullUrl('/api/projects/'+localStorage.getItem("projectId")+'/files/tree'), {
          method: 'GET',
          headers: { 
            'Authorization': 'Bearer ' + localStorage.getItem("token"),
            'Content-Type': 'application/json' 
          }
        });
    }

    if (!res.ok) {
        const err = await res.json();
        throw new Error(err.message || 'File tree retrieval failed');
    }
    const o = await res.json();
    document.getElementById("file-tree")?.remove();
    const root = document.getElementById('filebar');
    const t = getTree(o.children);
    t.id = "file-tree";
    root?.appendChild(t);

    // récupérer le nom du dossier
    t.addEventListener("click", (e) => {
        const target = e.target as HTMLElement;
        if (target.tagName === "H4" && target.dataset.path) {
            localStorage.setItem("selectedFolder", target.dataset.path);
        }
    });

    document.getElementById("project_name")!.innerHTML = '▼ ' + localStorage.getItem("projectName")!;
    return true;
}

const Filebar: React.FC = () => {
  React.useEffect(() => {
    const projectId = localStorage.getItem("projectId");
    if (projectId) {
      updateFileTree().catch(err => console.error(err));
    }
  }, []);
    
  
  return (
    <div id="filebar" className="filebar">
      <h3 onClick={() =>{
        const l = document.getElementById("project_name");
        if (l!= undefined) {
          if (l.innerHTML !== undefined && l.innerHTML.startsWith('▼ '))
          {
            const fileTree = document.getElementById("file-tree");
            if (fileTree) {
              fileTree.style.display = 'none';
            }
            l.innerHTML = '► ' + l.innerHTML.substring(1);
          }
          else
          {
            const fileTree = document.getElementById("file-tree");
            if (fileTree) {
              fileTree.style.display = 'block';
            }
            l.innerHTML = '▼ ' + l.innerHTML.substring(1);
          }
        }
      }} id="project_name"></h3>
    </div>
  );
};

export default Filebar;