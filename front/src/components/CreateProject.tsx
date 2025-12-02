import projectIcon from '../assets/project.png';
import React from 'react';
import './CreateProject.css';
import { fullUrl } from './Utils';
import { origFetch } from '../App';

async function handleCreateProject() {
    console.log("Creating project");
    const name = (document.getElementById("name") as HTMLInputElement).value;
    let res = await origFetch(fullUrl('/api/projects'), {
          method: 'POST',
          headers: { 
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json' },
          body: JSON.stringify({ name }),
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
        res = await origFetch(fullUrl('/api/projects'), {
          method: 'POST',
          headers: { 
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json' },
          body: JSON.stringify({ name }),
        });
    }
    if (!res.ok) {
        const err = await res.json();
        throw new Error(err.message || 'Project creation failed');
    }
    const o = await res.json();
    localStorage.setItem('projectId', o.id);
    localStorage.setItem('projectName', o.name);
    if (document.getElementById("project_name")!.innerHTML) {
      document.getElementById("project_name")!.innerHTML =  document.getElementById("project_name")!.innerHTML.substring(0,2) + o.name;
    }
    else
      document.getElementById("project_name")!.innerHTML = '▼ ' + o.name;
    document.getElementById("file-tree")?.remove();
    document.getElementById("close")?.click();
}

const CreateProject: React.FC = () => {
    return (
        <div className="create-panel">
            <h2>Create a project</h2>
            <form onSubmit={(e) => {e.preventDefault(); handleCreateProject(); return false;}}>
                <div className="input-group">
                    <img src={projectIcon} alt="Project Icon" className="input-icon" />
                    <input id="name" placeholder="name" required />
                </div>
                <input type='submit' value={"Create project"} ></input>
            </form>
        </div>
    );
};

export default CreateProject;