import SandboxExplorer from './SandboxExplorer'
import { useSandboxEditor } from '../hooks/useSandboxEditor'

interface SandboxWorkbenchProps {
  layout?: 'compact' | 'expanded'
}

export default function SandboxWorkbench({ layout = 'compact' }: SandboxWorkbenchProps) {
  const {
    files,
    tree,
    refresh,
    deleteFile,
    editorPath,
    setEditorPath,
    editorContent,
    setEditorContent,
    loadEditorFile,
    saveEditorFile,
  } = useSandboxEditor()

  const handleLoad = async (path?: string) => {
    try {
      const loaded = await loadEditorFile(path)
      if (!loaded && !path) {
        alert('Enter a file path to load')
      }
    } catch {
      alert('Failed to load file')
    }
  }

  const handleDelete = async (path: string) => {
    if (!confirm(`Delete ${path}?`)) return
    try {
      await deleteFile(path)
      if (editorPath === path) {
        setEditorPath('')
        setEditorContent('')
      }
    } catch {
      alert('Error deleting file')
    }
  }

  const handleSave = async () => {
    try {
      await saveEditorFile()
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to save file')
    }
  }

  return (
    <SandboxExplorer
      sandboxFiles={files}
      sandboxTree={tree}
      draftFileStatuses={{}}
      activeSandboxPath=""
      editorPath={editorPath}
      editorContent={editorContent}
      editorBaseline={null}
      editorFileStatus={null}
      showDiff={false}
      readOnly={false}
      awaitingHuman={false}
      layout={layout}
      onEditorPathChange={setEditorPath}
      onEditorContentChange={setEditorContent}
      onLoadFile={(path) => void handleLoad(path)}
      onDeleteFile={(path) => void handleDelete(path)}
      onRefresh={() => void refresh()}
      onSave={() => void handleSave()}
    />
  )
}
