import projectIcon from '../assets/project.png';
import React from 'react';
import './OpenProject.css';
import { getTree, fullUrl } from './Utils';
import { origFetch } from '../App';

async function handleOpenProject() {
    const name = (document.getElementById("name") as HTMLInputElement).value;
    let res = await origFetch(fullUrl('/api/projects/name/' + name), {
          method: 'GET',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json' 
        },
        });
    if (res.status === 401) {
        console.log("Unauthorized, refreshing token");
        res = await origFetch(fullUrl('/api/user/refresh'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: localStorage.getItem('refreshToken') }),
        });
        const r = await res.json();
        localStorage.setItem('token', r.token || '');
        localStorage.setItem('refreshToken', r.refreshToken || '');
        res = await origFetch(fullUrl('/api/projects/name/' + name), {
          method: 'GET',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json' },
        });
    }
    if (!res.ok) {
        const err = await res.json();
        console.error(err);
        console.error("Error retrieving project:", err);
        alert("The project name must be the exact name of the project you want to open.");
        throw new Error(err.message || 'File creation failed');
    }
    localStorage.setItem('projectName', name);
    let o = await res.json();
    localStorage.setItem('projectId', o.id);
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
    o = await res.json();
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
        <div className="open-panel">
            <h2>Open a project</h2>
            <form onSubmit={(e) => {e.preventDefault();handleOpenProject(); return false;}}>
                <div className="input-group">
                    <img src={projectIcon} alt="File Icon" className="input-icon" />
                    <input id="name" placeholder="Project name" required />
                </div>
                <input type='submit' value={"Open Project"} ></input>
            </form>
        </div>
    );
};

export default NewFile;