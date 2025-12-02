import React, { useState, useEffect, useRef, useCallback } from "react";
import Header from "./components/Header";
import Filebar from "./components/Filebar";
import EditorPanel, { editorRef } from "./components/EditorPanel";
import ConsolePanel from "./components/ConsolePanel";
import "./App.css";
import Stack from '@mui/material/Stack';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { fullUrl, saveFile } from "./components/Utils";
import { useWebSpeech } from "./hooks/useWebSpeech";

export const origFetch = window.fetch;

function isIvokCommand(t: string) {
  t = t.toLowerCase().replace(/[^a-z]/g, "");
  return ["ivok", "evok", "evoke", "evoked", "ebook","Evoque"].some(prefix =>
    t.startsWith(prefix)
  );
}
function getCommandAfterIvok(t: string) {
  return t.replace(/^ *(ivok|evok|evoke|evoked|ebook) +/i, "");
}
function similarity(a: string, b: string) {
  a = a.replace(/\s+/g, "").toLowerCase();
  b = b.replace(/\s+/g, "").toLowerCase();
  const matrix = Array(a.length + 1).fill(0).map((_, i) => [i]);
  for (let j = 1; j <= b.length; j++) matrix[0][j] = j;
  for (let i = 1; i <= a.length; i++)
    for (let j = 1; j <= b.length; j++)
      matrix[i][j] = a[i - 1] === b[j - 1]
        ? matrix[i - 1][j - 1]
        : Math.min(
            matrix[i - 1][j] + 1,
            matrix[i][j - 1] + 1,
            matrix[i - 1][j - 1] + 1
          );
  return 1 - matrix[a.length][b.length] / Math.max(a.length, b.length, 1);
}

const App: React.FC = () => {
  const [token, setToken] = useState<string | null>(localStorage.getItem('token'));
  const [filebarVisible, setFilebarVisible] = useState(true);
  const [consoleVisible, setConsoleVisible] = useState(true);
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [isLoginMode, setIsLoginMode] = useState(true);
  const [error, setError] = useState("");
  const [voiceField, setVoiceField] = useState<"none" | "login" | "password">("none");
  const [editMode, setEditMode] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  // Macros vocales pour l'édition
  const editMacros = useCallback((text: string) => {
    if (!editorRef.current) return;
    const editor = editorRef.current;
    let position = editor.getPosition();
    let macroUsed = false;
    text = text.trim().toLowerCase();

    const insert = (str: string) => {
      editor.executeEdits("", [{
        range: {
          startLineNumber: position.lineNumber,
          startColumn: position.column,
          endLineNumber: position.lineNumber,
          endColumn: position.column
        },
        text: str,
        forceMoveMarkers: true
      }]);
      editor.focus();
      macroUsed = true;
    };

    function textNumberToInt(word: string): number | null {
      const numbers: { [key: string]: number } = {
        "zero": 0, "one": 1, "two": 2, "three": 3, "four": 4,
        "five": 5, "six": 6, "seven": 7, "eight": 8, "nine": 9,
        "ten": 10, "eleven": 11, "twelve": 12, "thirteen": 13, "fourteen": 14,
        "fifteen": 15, "sixteen": 16, "seventeen": 17, "eighteen": 18, "nineteen": 19,
        "twenty": 20
      };
      return numbers[word.toLowerCase()] ?? null;
    }
    
    // ... dans ta fonction editMacros :
    const deleteRegex = /delete\s+([0-9]+|[a-z]+)/i;
    const match = text.toLowerCase().match(deleteRegex);
    
    if (match) {
      let count: number | null = null;
      if (!isNaN(Number(match[1]))) {
        count = parseInt(match[1], 10);
      } else {
        count = textNumberToInt(match[1]);
      }
      if (count && count > 0) {
        for (let i = 0; i < count; i++) {
          editor.trigger('keyboard', 'deleteLeft', {});
        }
        macroUsed = true;
        return macroUsed;
      }
    }
    // Macros
    switch (text.toLowerCase()) {
      case "delete word":
        editor.trigger('keyboard', 'deleteWordLeft', {});
        macroUsed = true;
        break;
      case "delete line":
        editor.trigger('keyboard', 'deleteAllLeft', {});
        editor.trigger('keyboard', 'deleteAllRight', {});
        macroUsed = true;
        break;
      default:
        macroUsed = false;
    }

    return macroUsed;
  }, []);

  // Dictée mode édit
  const handleDictation = useCallback((text: string) => {
    if (!editorRef.current) return;
    // Si macro reconnue, exécute macro et return
    if (editMacros(text)) return;
    text = text.trim().toLowerCase();

    text = text.replace(/p1/g, "("); // Nettoyage des caractères spéciaux
    text = text.replace(/p2/g, ")"); // Nettoyage des caractères spéciaux
    text = text.replace(/b1/g, "{"); // Nettoyage des caractères spéciaux
    text = text.replace(/b2/g, "}"); // Nettoyage des caractères spéciaux
    text = text.replace(/c1/g, "["); // Nettoyage des caractères spéciaux
    text = text.replace(/c2/g, "]"); // Nettoyage des caractères spéciaux
    text = text.replace(/dot/g, "."); // Nettoyage des caractères spéciaux
    text = text.replace(/semicolon/g, ";"); // Nettoyage des caractères spéciaux
    text = text.replace(/colon/g, ":"); // Nettoyage des caractères spéciaux
    text = text.replace(/column/g, ":"); // Nettoyage des caractères spéciaux

    text = text.replace(/quote/g, "\""); // Nettoyage des caractères spéciaux
    text = text.replace(/single quote/g, "'"); // Nettoyage des caractères spéciaux
    text = text.replace(/indent/g, "\t");
    text = text.replace(/line/g, "\n"); // Nettoyage des caractères spéciaux
    text = text.replace(/definition/g, "def"); // Nettoyage des caractères spéciaux
    text = text.replace(/score/g, "_"); // Nettoyage des caractères spéciaux
    // Nettoyage des caractères spéciaux
    // Sinon, insère texte + espace
    if (text.length === 0) return;
    if (!(text.endsWith("\n") || text.endsWith("\t"))) {
      text += " "; // Ajoute un espace à la fin si pas de saut de ligne
    }

    const editor = editorRef.current;
    const position = editor.getPosition();
    editor.executeEdits("", [{
      range: {
        startLineNumber: position.lineNumber,
        startColumn: position.column,
        endLineNumber: position.lineNumber,
        endColumn: position.column
      }, 
      text: text,
      forceMoveMarkers: true
    }]);
    editor.focus();
  }, [editMacros]);

  // RECHARGE FILEBAR après création de fichier sans reload toute la page
  function refreshFilebar() {
    if ((window as any).updateFileTree) {
      (window as any).updateFileTree();
    } else {
      window.location.reload();
    }
  }

  const handleVoice = useCallback((text: string) => {
    let raw = text.trim();
    let t = raw.toLowerCase();

    // PAGE LOGIN : PAS besoin de "evok" devant
    if (!token) {
      if (t === "login") {
        setVoiceField("login");
        return;
      }
      if (t === "password") {
        setVoiceField("password");
        return;
      }
      if (t === "create account" || t === "create mode") {
        setIsLoginMode(false);
        return;
      }
      if (t === "back to login" || t === "login mode") {
        setIsLoginMode(true);
        return;
      }
      if (voiceField === "login") {
        let newText = raw.replace(/[^a-zA-Z0-9.]/g, '');
        newText = newText.replace(/dot/g, '.');
        newText = newText.toLowerCase();
        setLogin(newText);
        setVoiceField("none");
        return;
      }
      if (voiceField === "password") {
        setPassword(raw);
        setVoiceField("none");
        return;
      }
      if (t === "submit" || t === "log in") {
        formRef.current?.dispatchEvent(new Event("submit", { cancelable: true, bubbles: true }));
        return;
      }
      return;
    }

    // MODE DICTEE EN EDITOR (peut quitter avec "quit")
    if (editMode && t === "quit") {
      setEditMode(false);
      return;
    }
    if (editMode) {
      handleDictation(raw);
      return;
    }

    // DANS L'APP : il faut "evok" devant les commandes
    if (isIvokCommand(t)) {
      t = getCommandAfterIvok(raw.toLowerCase());

      // Créer un projet : "evok create project [NOM]"
      if (t.startsWith("create project ")) {
        const name = t.replace("create project ", "").trim();
        fetch(fullUrl('/api/projects'), {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ name })
        })
        .then(async res => {
          if (!res.ok) throw new Error("Erreur création projet");
          const proj = await res.json();
          localStorage.setItem('projectId', proj.id);
          localStorage.setItem('projectName', proj.name);
          window.location.reload();
        });
        return;
      }

      // Créer un fichier : "evok create file [NOM]"
      if (t.startsWith("create file ")) {
        let relativePath = t.replace("create file ", "").trim();
        relativePath = relativePath.replace(/ dot/g, ".").replace(/ /, "_");
        fetch(fullUrl(`/api/projects/${localStorage.getItem("projectId")}/files`), {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ relativePath })
        }).then(() => {
          refreshFilebar();
        });
        return;
      }

      // Edit mode ON
      if (t === "edit") {
        setEditMode(true);
        return;
      }
      // Quit edit mode
      if (t === "quit") {
        setEditMode(false);
        return;
      }
      // Run code
      if (t === "run" || t === "ran" || t === "ren") {
        // Ici tu peux mettre ton appel pour lancer le code (ex: clique sur un bouton d'execution ou fetch backend...)
        // Par exemple, tu peux déclencher un bouton s'il existe :
        const btn = document.getElementById('run-button');
        if (btn) btn.click();
        // Ou fais un fetch ou un custom event si besoin...
        return;
      }
      // Open file
      if (t.startsWith("open ")) {
        let fileName = t.replace("open ", "").trim();
        fileName = fileName.replace(/\s+/g, "").replace(/pay$/i, "py").replace(/pai$/i, "py");

        const files = Array.from(document.querySelectorAll('[data-path], .file-tree li, .file-list-item')) as HTMLElement[];
        let best: HTMLElement | null = null;
        let bestScore = 0.5;
        files.forEach(f => {
          const txt = (f.textContent || "").replace(/\s+/g, "").toLowerCase();
          const score = similarity(fileName, txt);
          if (score > bestScore) {
            best = f;
            bestScore = score;
          }
        });
        if (best) best.click();
        return;
      }
      if (t === "save") {
        const selected = document.getElementById("file-selected");
        if (selected && selected.dataset && selected.dataset.path) {
          saveFile(selected.dataset.path);
        }
        return;
      }
      if (t === "log out") {
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        window.location.reload();
        return;
      }
      return;
    }
    // Toutes les autres paroles sont ignorées pour la partie app
  }, [token, voiceField, login, password, editMode, handleDictation]);

  useWebSpeech(handleVoice);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      if (isLoginMode) {
        const res = await fetch(fullUrl('/api/user/login'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ login, password }),
        });
        if (!res.ok) {
          const err = await res.json();
          alert(err.message || 'Login failed');
          throw new Error(err.message || 'Login failed');
        }
        const { token, refreshToken } = await res.json();
        setToken(token);
        localStorage.setItem('token', token);
        localStorage.setItem('refreshToken', refreshToken);
      } else {
        const res = await fetch(fullUrl('/api/user'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ login, password, isAdmin: false }),
        });
        if (!res.ok) {
          const err = await res.json();
          alert("The login must have a dot (.) in it, like 'john.doe'.");
          throw new Error(err.message || 'Registration failed');
        }
        const loginRes = await fetch(fullUrl('/api/user/login'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ login, password }),
        });
        if (!loginRes.ok) {
          const err = await loginRes.json();
          throw new Error(err.message || 'Auto-login failed');
        }
        const { token, refreshToken } = await loginRes.json();
        setToken(token);
        localStorage.setItem('token', token);
        localStorage.setItem('refreshToken', refreshToken);
      }
    } catch (err) {
      setError((err as Error).message);
    }
  };

  useEffect(() => {
    if (token) localStorage.setItem('token', token);
    else {
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
    }
  }, [token]);

  useEffect(() => {
    if (!token) return;
    const origFetch = window.fetch;
    window.fetch = async (input, init) => {
      let url: string;
      let newInit: RequestInit = { ...(init || {}) };
      if (typeof input === 'string') url = fullUrl(input);
      else if (input instanceof URL) url = fullUrl(input.toString());
      else {
        url = fullUrl(input.url);
        newInit = { method: input.method, headers: input.headers, body: input.body, ...newInit };
      }
      const headers = new Headers(newInit.headers || {});
      headers.set('Authorization', `Bearer ${localStorage.getItem('token')}`);
      newInit.headers = headers;
      let res = await origFetch(url, newInit);
      if (res.status === 401 && localStorage.getItem('refreshToken')) {
        const refreshRes = await origFetch(fullUrl('/api/user/refresh'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: localStorage.getItem('refreshToken') }),
        });
        if (refreshRes.ok) {
          const { token: newToken, refreshToken: newRefresh } = await refreshRes.json();
          setToken(newToken);
          localStorage.setItem('token', newToken);
          localStorage.setItem('refreshToken', newRefresh);
          headers.set('Authorization', `Bearer ${newToken}`);
          newInit.headers = headers;
          res = await origFetch(url, newInit);
        } else {
          setToken(null);
        }
      }
      return res;
    };
    return () => { window.fetch = origFetch; };
  }, [token]);

  if (!token) {
    return (
      <div className="auth-container">
        <Typography variant="h5" align="center">
          {isLoginMode ? 'Login' : 'Register'}
        </Typography>
        <form
          ref={formRef}
          onSubmit={handleSubmit}
          style={{ maxWidth: 320, margin: '0 auto' }}
        >
          <Stack spacing={2}>
            <TextField
              label="Login"
              value={login}
              onChange={e => setLogin(e.target.value)}
              fullWidth
              required
              sx={{
                backgroundColor: voiceField === 'login' ? 'rgba(25, 118, 210, 0.1)' : 'transparent',
                transition: 'background-color 0.3s',
              }}
            />
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              fullWidth
              required
              sx={{
                backgroundColor: voiceField === 'password' ? 'rgba(25, 118, 210, 0.1)' : 'transparent',
                transition: 'background-color 0.3s',
              }}
            />
            {error && <Typography color="error">{error}</Typography>}
            <Button type="submit" variant="contained" fullWidth>
              {isLoginMode ? 'Login' : 'Register'}
            </Button>
            <Button
              onClick={() => { setIsLoginMode(!isLoginMode); setError(''); }}
              fullWidth
            >
              {isLoginMode ? 'Create account' : 'Back to login'}
            </Button>
          </Stack>
        </form>
      </div>
    );
  }

  return (
    <div className="app">
      <Header
        onToggleFilebar={() => setFilebarVisible(!filebarVisible)}
        onToggleTerminal={() => setConsoleVisible(!consoleVisible)}
        editMode={editMode}
      />
      <div className="main">
        {filebarVisible && <Filebar />}
        <div className="content">
          <Stack spacing={0} sx={{ height: '100%' }}>
            <div
              id="editor"
              className="editor-wrapper"
              onBlur={() =>
                saveFile(
                  document.getElementById("file-selected")?.dataset.path || ""
                )
              }
            >
              <EditorPanel value="" />
            </div>
            {consoleVisible && (
              <div className="console-wrapper">
                <ConsolePanel />
              </div>
            )}
          </Stack>
        </div>
      </div>
    </div>
  );
};

export default App;
