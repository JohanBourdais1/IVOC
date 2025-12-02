import { editorRef } from './EditorPanel';
import { origFetch } from '../App';

type FileNode = {
  name: string;
  directory: boolean;
  children?: FileNode[];
};
type Entry = string | FileNode;

export const API_BASE_URL =  'http://localhost:8080';

export function fullUrl(path: string) {
  if (path.startsWith('/api')) {
    return `${API_BASE_URL}${path}`;
  }
  return path;
}

async function handleFileClick(path: string) {
  const fileSelected = document.getElementById("file-selected");
  fileSelected!.innerHTML = path.replace(/\s*\/\s*/g, ' / ');
  fileSelected!.dataset.path = path;
  console.log("handleFileClick BEFORE fetch, token:", localStorage.getItem('token'));
  let res = await origFetch(fullUrl('/api/projects/' + localStorage.getItem("projectId") + '/files?path=' + path), {
          method: 'GET',
          headers: { 
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'accept': '*/*' },
        });

    if (res.status === 401) {
        res = await origFetch(fullUrl('/api/user/refresh'), {
          method: 'POST',
          headers: { 
            'accept': '*/*',
            'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: localStorage.getItem('refreshToken') }),
        });
        const r = await res.json();
        localStorage.setItem('token', r.token || '');
        localStorage.setItem('refreshToken', r.refreshToken || '');
        res = await origFetch(fullUrl('/api/projects/' + localStorage.getItem("projectId") + '/files?path=' + path), {
          method: 'GET',
          headers: { 
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'accept': '*/*' },
        });
    }
    if (!res.ok) {
        const err = await res.json();
        console.error(err);
        throw new Error(err.message || 'File retrieval failed');
    }
    const fileContent = await res.text();
    if (editorRef.current) {
    editorRef.current.setValue(fileContent);
  } else {
    console.warn("Éditeur Monaco non monté");
  }
  const extension = path.split('.').pop() || 'plaintext';
  let language = 'plaintext';
  switch (extension) {
    case 'js':
      language = 'javascript';
      break;
    case 'ts':
      language = 'typescript';
      break;
    case 'py':
      language = 'python';
      break;
    case 'java':
      language = 'java';
      break;
    case 'c':
      language = 'c';
      break;
    case 'cc':
      language = 'cpp';
      break;
    case 'cpp':
      language = 'cpp';
      break;
    case 'cs':
      language = 'csharp';
      break;
    case 'sh':
      language = 'bash';
      break;
    default:
      language = 'plaintext';
  }

  if (editorRef.current) {
    const model = editorRef.current.getModel();
    if (model) {
      (window as unknown as { monaco: typeof import('monaco-editor') }).monaco.editor.setModelLanguage(
        model,
        language
      );
    }
  }
}


export function getTree(
  data: Entry[],
  tab: number = 1,
  parentPath: string = '',
  parentIdxs : number[] = [],
): HTMLUListElement {
  const ul = document.createElement('ul');

  data.sort((a, b) => {
    const nameA = typeof a === 'string' ? a : a.name;
    const nameB = typeof b === 'string' ? b : b.name;
    return nameA.localeCompare(nameB, undefined, { numeric: true });
  });

  data.forEach((item, idx) => {
    const localIdx = idx + 1;
    const idxs = [...parentIdxs, localIdx];
    const dataNumber = idxs.join(' ');
    const indent = tab * 10;
    const folderPath = parentPath ? `${parentPath}/${typeof item === 'string' ? item : item.name}` : (typeof item === 'string' ? item : item.name);


    if (typeof item === 'string' || (typeof item === 'object' && !item.directory)) {
      // FICHIER
      const name = typeof item === 'string' ? item : item.name;
      const li = document.createElement('li');
      li.classList.add('selectable-file');

      li.textContent = `${localIdx} ${name}`;
      li.dataset.path = folderPath;
      li.dataset.number = dataNumber;
      li.id = folderPath;
      li.style.marginLeft = `${indent}px`;
      li.addEventListener('click', () => {
        document.querySelectorAll('.selectable-file.selected')
            .forEach(el => el.classList.remove('selected'));
        document.querySelectorAll('.selectable-folder.selected')
            .forEach(el => el.classList.remove('selected'));
        li.classList.add('selected');
        handleFileClick(folderPath);
      });

      ul.appendChild(li);

    } else if (typeof item === 'object' && item.directory && item.name != '.git') {
     // const folderPath = currentPath ? `${currentPath}/${folderName}` : folderName;

      const folder = document.createElement('h4');
      folder.textContent = `▼ ${localIdx} ${item.name}`;
      folder.dataset.path = folderPath;
      folder.dataset.number = dataNumber;
      folder.classList.add('selectable-folder');
      folder.style.marginLeft = `${indent}px`;

      folder.addEventListener('click', (e) => {
        e.stopPropagation();
        const isOpen = folder.textContent?.startsWith('▼ ');
        folder.textContent = (isOpen ? '► ' : '▼ ') + localIdx + ' ' + item.name;
        childrenUl.style.display = isOpen ? 'none' : 'block';

        // sélection visuelle
        document.querySelectorAll('.selectable-folder.selected')
            .forEach(el => el.classList.remove('selected'));
        folder.classList.add('selected');
        localStorage.setItem('selectedFolder', folderPath);
      });

      const childrenUl = document.createElement('ul');
      childrenUl.style.margin = '0';
      if (item.children && item.children.length) {
        const subtree = getTree(item.children, tab + 1, folderPath, parentIdxs);
        while (subtree.firstChild) {
          childrenUl.appendChild(subtree.firstChild);
        }
      }

      ul.appendChild(folder);
      ul.appendChild(childrenUl);
    }
  });

  return ul;
}

export async function saveFile(path: string) {

    const content = editorRef.current?.getValue() || '';
  let res = await origFetch(fullUrl('/api/projects/' + localStorage.getItem("projectId") + '/files/save?path=' + path), {
    method: 'PUT',
    headers: {
      'accept': '*/*',
      'Authorization': `Bearer ${localStorage.getItem('token')}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ 'content': content }),
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
        res = await fetch(fullUrl('/api/projects/' + localStorage.getItem("projectId") + '/files/save?path=' + path), {
            method: 'PUT',
            headers: {
            'accept': '*/*',
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json'
            },
            body: JSON.stringify({'content': content}),
        });
    }
    if (!res.ok) {
        const err = await res.json();
        console.error(err);
        throw new Error(err.message || 'File save failed');
    }
}