import * as React from 'react';
import Button from '@mui/material/Button';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { SiFiles } from 'react-icons/si';
import './ProjectPanel.css';
import NewFile from './NewFile';
import NewFolder from './NewFolder';
import { saveFile } from './Utils';

export default function ProjectPanel() {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const [isFilePanelOpen, setIsFilePanelOpen] = React.useState(false);
  const [isFolderPanelOpen, setIsFolderPanelOpen] = React.useState(false);

  const open = Boolean(anchorEl);
  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };
  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleNewFile = () => {
    setIsFilePanelOpen(true);
    setAnchorEl(null);
  };

  const handleCloseFilePanel = () => {
    setIsFilePanelOpen(false);
  };

  const handleNewFolder = () => {
    setIsFolderPanelOpen(true);
    setAnchorEl(null);
  };

  const handleCloseFolderPanel = () => {
    setIsFolderPanelOpen(false);
  };

  return (
    <div className='user-panel-container'>
    <div className='project-panel'>
      <Button
        id="file-button"
        aria-controls={open ? 'basic-menu' : undefined}
        aria-haspopup="true"
        aria-expanded={open ? 'true' : undefined}
        onClick={handleClick}
      >
          <SiFiles
              aria-label="Files"
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
        <MenuItem onClick={handleNewFile}>Nouveau Fichier</MenuItem>
        <MenuItem onClick={handleNewFolder}>Nouveau Dossier</MenuItem>
        <MenuItem onClick={() => {saveFile(document.getElementById("file-selected")?.dataset.path || ""); handleClose()}}>Enregistrer</MenuItem>
      </Menu>
    </div>
    {isFilePanelOpen && (
      <div className='project-create'>
        <NewFile />
        <a href="#" id="close" className="close" onClick={handleCloseFilePanel}></a>
      </div>
    )}

    {isFolderPanelOpen && (
      <div className='project-create'>
        <NewFolder />
        <a href="#" id="close" className="close" onClick={handleCloseFolderPanel}></a>
      </div>
    )}
    
    </div>
  );
}
