import projectIcon from '../assets/project.png';
import React from 'react';
import './NewFolder.css';
import { getTree, fullUrl } from './Utils';
import { origFetch } from '../App';

async function handleNewFolder() {
    const name = (document.getElementById("name") as HTMLInputElement).value;
    const parent = localStorage.getItem("selectedFolder") || "";
    const relativePath = parent ? `${parent}/${name}` : name;

    let res = await origFetch(fullUrl('/api/projects/' + localStorage.getItem("projectId") + '/folders'), {
          method: 'POST',
          headers: { 
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json' },
          body: JSON.stringify({ relativePath }),
        });
    if (res.status === 401) {
        res = await origFetch(fullUrl('/api/user/refresh'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: localStorage.getItem('refreshToken') }),
        });
        const r = await res.json();
        localStorage.setItem('token', r.token || '');
        localStorage.setItem('refreshToken', r.refreshToken || '');
        res = await origFetch(fullUrl('/api/projects/' + localStorage.getItem("projectId") + '/folders'), {
          method: 'POST',
          headers: { 
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json' },
          body: JSON.stringify({ relativePath }),
        });
    }
    if (!res.ok) {
        const err = await res.json();
        console.error(err);
        alert("Relative path must be a valid path, like 'src/components'");
        throw new Error(err.message || 'File creation failed');
    }
    res = await origFetch(fullUrl('/api/projects/'+localStorage.getItem("projectId")+'/files/tree'), {
          method: 'GET',
          headers: { 
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json' }
        });

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
    document.getElementById("project_name")!.innerHTML = '▼ '  + localStorage.getItem("projectName");
    document.getElementById("close")?.click();
}

const NewFile: React.FC = () => {
    return (
        <div className="folder-panel">
            <h2>Create a folder</h2>
            <form onSubmit={(e) => {e.preventDefault();handleNewFolder(); return false;}}>
                <div className="input-group">
                    <img src={projectIcon} alt="File Icon" className="input-icon" />
                    <input id="name" placeholder="relative path" required />
                </div>
                <input type="submit" value={"Create folder"}></input>
            </form>
        </div>
    );
};

export default NewFile;