import Editor, {type BeforeMount, type OnMount} from "@monaco-editor/react";
import "./EditorPanel.css";
import * as monaco from "monaco-editor";


export interface EditorPanelProps {
    value: string;
    language?: string;
    theme?: string;
    onChange?: (value: string | undefined) => void;
    id?: string;
}

export const editorRef = {
  current: null as monaco.editor.IStandaloneCodeEditor | null,
};

const EditorPanel: React.FC<EditorPanelProps> = ({ value, language, onChange }) => {

    const handleBeforeMount: BeforeMount = (monaco) => {
        monaco.editor.defineTheme("noBackground", {
            base: "vs",
            inherit: true,
            rules: [],
            colors: {
                "editor.background": "#00000000",
                "editorCursor.foreground": "#000000",
                "editor.lineHighlightBackground": "#00000000",
                "editorLineNumber.foreground": "#888888",
            },
        });
};

const handleMount: OnMount = (editor) => {
    editorRef.current = editor;
  };

    return (
        <div className="editor-panel">
            <Editor
                height="100%"
                beforeMount={handleBeforeMount}
                theme="vs-dark"
                language={language}
                onMount={handleMount}
                defaultLanguage="plaintext"
                defaultValue=""
                value={value}
                onChange={(v) => onChange && onChange(v)}
                options={{
                    minimap: { enabled: false },
                    wordWrap: "on",
                    automaticLayout: true,
                    renderLineHighlight: "none",
                    scrollbar: {
                        vertical: "auto",
                        horizontal: "hidden",
                        verticalScrollbarSize: 7,
                        useShadows: false
                    },
                    overviewRulerBorder: false,
                    overviewRulerLanes: 0,
                }}

            />
        </div>
    );
};

export default EditorPanel;