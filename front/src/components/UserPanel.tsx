import * as React from 'react';
import Button from '@mui/material/Button';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { BsPersonFill } from "react-icons/bs";
import './UserPanel.css';
import CreateProject from './CreateProject';
import DeleteProject from './DeleteProject';
import OpenProject from './OpenProject';

export default function UserPanel() {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const [isProjectPanelOpen, setIsProjectPanelOpen] = React.useState(false);
  const [isDeletePanelOpen, setIsDeletePanelOpen] = React.useState(false);
  const [isOpenPanelOpen, setIsOpenPanelOpen] = React.useState(false);

  const open = Boolean(anchorEl);
  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };
  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleLogout = () => {
    setAnchorEl(null);
    localStorage.removeItem('token');
    window.location.reload();
  };

  const handleCreateProject = () => {
    setIsProjectPanelOpen(true);
    setAnchorEl(null);

  };

  const handleCloseProjectPanel = () => {
    setIsProjectPanelOpen(false);
    setAnchorEl(null);

  };

  const handleDeleteProject = () => {
    setIsDeletePanelOpen(true);
    setAnchorEl(null);
  };

  const handleCloseDeletePanel = () => {
    setIsDeletePanelOpen(false);
    setAnchorEl(null);
  };

  const handleOpenProject = () => {
    setIsOpenPanelOpen(true);
    setAnchorEl(null);
  };

  const handleCloseOpenPanel = () => {
    setIsOpenPanelOpen(false);
    setAnchorEl(null);
  };

  return (
    <div className='user-panel-container'>
    <div className='user-panel'>
      <Button
        id="file-button"
        aria-controls={open ? 'basic-menu' : undefined}
        aria-haspopup="true"
        aria-expanded={open ? 'true' : undefined}
        onClick={handleClick}
      >
          <BsPersonFill
              aria-label="User"
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
        <MenuItem onClick={handleLogout}>Se deconnecter</MenuItem>
        <MenuItem onClick={handleCreateProject}>Créer un projet</MenuItem>
        <MenuItem onClick={handleDeleteProject}>Supprimer un projet</MenuItem>
        <MenuItem onClick={handleOpenProject}>Ouvrir un projet</MenuItem>
      </Menu>
    </div>
    {isProjectPanelOpen && (
      <div className='project-create'>
        <CreateProject />
        <a href="#" id="close" className="close" onClick={handleCloseProjectPanel}></a>
      </div>
    )}

    {isDeletePanelOpen && (
      <div className='project-create'>
        <DeleteProject />
        <a href="#" id="close" className="close" onClick={handleCloseDeletePanel}></a>
      </div>
    )}

    {isOpenPanelOpen && (
      <div className='project-create'>
        <OpenProject />
        <a href="#" id="close" className="close" onClick={handleCloseOpenPanel}></a>
      </div>
    )}
    </div>
  );
}
