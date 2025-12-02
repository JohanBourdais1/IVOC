import projectIcon from '../assets/project.png';
import React from 'react';
import './DeleteProject.css';
import { fullUrl } from './Utils';
import { origFetch } from '../App';

async function handleDeleteProject() {
    const name = (document.getElementById("name") as HTMLInputElement).value;
    let res = await origFetch(fullUrl('/api/projects/' + name), {
          method: 'DELETE',
          headers: { 
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json' },
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
        res = await origFetch(fullUrl('/api/projects/' + name), {
          method: 'DELETE',
          headers: { 
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json' },
        });
    }
    if (!res.ok) {
        const err = await res.json();
        alert("The project name must be the exact name of the project you want to delete.");
        throw new Error(err.message || 'Project deletion failed');
    }
    localStorage.removeItem('projectId');
    localStorage.removeItem('projectName');
    document.getElementById("project_name")!.innerHTML = '';
    document.getElementById("file-tree")?.remove();
    document.getElementById("file-selected")!.innerHTML = '';
    document.getElementById("close")?.click();
}

const DeleteProject: React.FC = () => {
    return (
        <div className="delete-panel">
            <h2>Delete a project</h2>
            <form onSubmit={(e) => {e.preventDefault(); handleDeleteProject(); return false;}}>
                <div className="input-group">
                    <img src={projectIcon} alt="Project Icon" className="input-icon" />
                    <input id="name" placeholder="Project name" required />
                </div>
                <input type='submit' value={"Delete project"} ></input>
            </form>
        </div>
    );
};

export default DeleteProject;