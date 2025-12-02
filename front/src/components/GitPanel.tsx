import * as React from 'react';
import Button from '@mui/material/Button';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { SiGit } from 'react-icons/si';
import './GitPanel.css';
import { fullUrl } from './Utils';
import projectIcon from '../assets/project.png';
import { origFetch } from '../App';


export default function GitPanel() {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);
  const [gitAddPanel, setGitAddPanel] = React.useState(false);
  const [gitCommitPanel, setGitCommitPanel] = React.useState(false);
  const [gitRemotePanel, setGitRemotePanel] = React.useState(false);
  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };
  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleGitAction = async (action: string, params: Array<string>) => {
    console.log("Executing git action:", action, "with params:", params);
    let res = await origFetch(fullUrl('/api/projects/' + localStorage.getItem('projectId') + '/exec'), {
              method: 'POST',
              headers: {  
                'accept': '*/*',
                'Authorization': `Bearer ${localStorage.getItem('token')}`,
                'Content-Type': 'application/json' 
            },
            body: JSON.stringify({ 
              'feature': "git",
              'command': action,
              'params': params
             }),
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
            res = await origFetch(fullUrl('/api/projects/' + localStorage.getItem('projectId') + '/exec'), {
              method: 'POST',
              headers: {  
                'accept': '*/*',
                'Authorization': `Bearer ${localStorage.getItem('token')}`,
                'Content-Type': 'application/json' 
            },
            body: JSON.stringify({ 
              'feature': "git",
              'command': action,
              'params': params
             }),
            });
        }
        if (!res.ok) {
            const err = await res.json();
            console.error(err);
            console.error("Error retrieving project:", err);
            alert("The git command failed. Please check the parameters and try again.");
            throw new Error(err.message || 'File creation failed');
        }
    handleClose();
    document.getElementById("close")?.click();
  };

  const GitAdd: React.FC = () => {
    return (
        <div className="gitAdd-panel">
            <h2>Files You want to add</h2>
            <form onSubmit={(e) => {
              e.preventDefault();
              const inputs = document.querySelectorAll('.input-group input');
              const paths = Array.from(inputs).map(input => (input as HTMLInputElement).value == null ? '' : (input as HTMLInputElement).value.trim()).filter(path => path !== '');
              if (paths.length === 0) {
                alert("Please enter at least one file path.");
                return false;
              }
              handleGitAction("add", paths);
              return false;
              }}>
                <div className="input-group">
                    <img src={projectIcon} alt="File Icon" className="input-icon" />
                    <input id="0" placeholder="File path" required />
                    <button onClick={(e) =>{
                      e.preventDefault();
                      const i = document.createElement('input');
                      i.placeholder = 'File path';
                      i.required = false;
                      i.id = (document.querySelectorAll('.input-group input').length).toString();
                      const icon = document.createElement('img');
                      icon.src = projectIcon;
                      icon.alt = 'File Icon';
                      icon.className = 'input-icon';
                      document.querySelector('.input-group')?.appendChild(icon);
                      document.querySelector('.input-group')?.appendChild(i);
                      }}>+</button>
                </div>
                <input type='submit' value={"Add files"} ></input>
            </form>
        </div>
    );
};

const GitCommit: React.FC = () => {
    return (
        <div className="gitAdd-panel">
            <h2>Commit message</h2>
            <form onSubmit={(e) => {
              e.preventDefault();
              handleGitAction("commit", [(document.getElementById("0") as HTMLInputElement).value]);
              return false;
              }}>
                <div className="input-group">
                    <img src={projectIcon} alt="File Icon" className="input-icon" />
                    <input id="0" placeholder="Commit message" required />
                </div>
                <input type='submit' value={"Commit"} ></input>
            </form>
        </div>
    );
};

const GitRemote: React.FC = () => {
    return (
        <div className="gitAdd-panel">
            <h2>Add remote</h2>
            <form onSubmit={(e) => {
              e.preventDefault();
              handleGitAction("remote", [(document.getElementById("0") as HTMLInputElement).value, (document.getElementById("1") as HTMLInputElement).value]);
              return false;
              }}>
                <div className="input-group">
                    <img src={projectIcon} alt="File Icon" className="input-icon" />
                    <input id="0" placeholder="Remote, default origin" />
                    <img src={projectIcon} alt="File Icon" className="input-icon" />
                    <input id="1" placeholder="URL" required />
                </div>
                <input type='submit' value={"Add remote"} ></input>
            </form>
        </div>
    );
};

  return (
    <div className='git-panel'>
      <Button
        id="file-button"
        aria-controls={open ? 'basic-menu' : undefined}
        aria-haspopup="true"
        aria-expanded={open ? 'true' : undefined}
        onClick={handleClick}
      >
          <SiGit
              aria-label="Git"
              color={open ? 'grey' : 'white'}
              size={20}
          />
      </Button>
      <Menu
        id="basic-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        slotProps={{
          list: {
            'aria-labelledby': 'basic-button',
          },
        }}
      >
        <MenuItem onClick={()=> {handleGitAction("init", [])}}>Git Init</MenuItem>
        <MenuItem onClick={()=>{setGitAddPanel(true)}}>Git Add</MenuItem>
        <MenuItem onClick={()=>{setGitCommitPanel(true)}}>Git Commit</MenuItem>
        <MenuItem onClick={()=> {handleGitAction("push", [])}}>Git Push</MenuItem>
        <MenuItem onClick={()=>{setGitRemotePanel(true)}}>Git Remote</MenuItem>
      </Menu>

      {gitAddPanel && (
      <div className='project-create'>
        <GitAdd />
        <a href="#" id="close" className="close" onClick={()=>{setGitAddPanel(false);
          handleClose();
        }}></a>
      </div>
    )}

    {gitCommitPanel && (
      <div className='project-create'>
        <GitCommit />
        <a href="#" id="close" className="close" onClick={()=>{setGitCommitPanel(false);
          handleClose();
        }}></a>
      </div>
    )}

    {gitRemotePanel && (
      <div className='project-create'>
        <GitRemote />
        <a href="#" id="close" className="close" onClick={()=>{setGitRemotePanel(false);
          handleClose();
        }}></a>
      </div>
    )}
    </div>
  );
}
