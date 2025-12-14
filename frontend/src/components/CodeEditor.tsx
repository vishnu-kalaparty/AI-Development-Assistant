import Editor from '@monaco-editor/react'

interface CodeEditorProps {
  value: string
  onChange: (value: string | undefined) => void
  language?: string
  height?: string | number
  readOnly?: boolean
}

export default function CodeEditor({
  value,
  onChange,
  language = 'python',
  height = '400px',
  readOnly = false,
}: CodeEditorProps) {
  return (
    <Editor
      height={height}
      language={language}
      value={value}
      onChange={onChange}
      theme="vs-dark"
      options={{
        minimap: { enabled: false },
        fontSize: 14,
        lineNumbers: 'on',
        readOnly,
        scrollBeyondLastLine: false,
        automaticLayout: true,
      }}
    />
  )
}
