import React from "react";
import "./Header.css";
import ProjectPanel from "./ProjectPanel";
import GitPanel from "./GitPanel";
import UserPanel from "./UserPanel";
import { BsFillPlayFill, BsFillTerminalFill } from "react-icons/bs";
import { Button } from '@mui/material';
import { PistonClient } from "piston-api-client";
import { editorRef } from "./EditorPanel";

interface HeaderProps {
  onToggleFilebar: () => void;
  onToggleTerminal: () => void;
  editMode: boolean; // <== ajout ici
}

async function runCode() {
  const pistonClient = new PistonClient({ server: 'https://emkc.org' });
  const runtimes = await pistonClient.runtimes();
  if (runtimes.success) {
    console.log(runtimes.data);
  }
  const language = editorRef.current?.getModel()?.getLanguageId() || 'plaintext';
  const content = editorRef.current?.getValue() || '';
  if (language !== 'plaintext') {
    const result = await pistonClient.execute({
      language: language,
      version: '*',
      files: [
        { content: content }
      ],
      args: [],
    });
    if (result.success) {
      document.getElementById("console")!.innerHTML +=
        "Running " + document.getElementById('file-selected')?.innerHTML +
        '</br>' + '$ ' + result.data.run.output + "<br/>";
    }
  }
}

const Header: React.FC<HeaderProps> = ({ onToggleFilebar, onToggleTerminal, editMode }) => {
  return (
    <div className="header">
      <div className="header-left">
        <button onClick={onToggleFilebar} className="toggle-file">☰</button>
        <div className="header-panels">
          <ProjectPanel />
          <GitPanel />
          <UserPanel />
        </div>
      </div>
      <div className="header-title">IVok™ Editor</div>

      <div className="header-actions flex items-center gap-2">
        <Button onClick={runCode} id="run-button">
          <BsFillPlayFill aria-label="Run" color="white" size={24} />
        </Button>
        <Button onClick={onToggleTerminal}>
          <BsFillTerminalFill aria-label="Console" color="white" size={18} />
        </Button>
      </div>

      <div className="file-selected">
        <span className="file-name" id="file-selected"></span>
      </div>

      {/* === INDICATEUR MODE EDITION === */}
      {editMode && (
        <div className="edit-mode-indicator">
          🎙️ MODE ÉDITION VOCALE ACTIVÉ
        </div>
      )}
    </div>
  );
};

export default Header;