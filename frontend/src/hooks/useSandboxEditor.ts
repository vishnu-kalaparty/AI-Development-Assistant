import { useCallback, useState } from 'react'
import { useSandboxFiles } from './useSandboxFiles'

export function useSandboxEditor(autoLoad = true) {
  const { files, tree, loading, refresh, loadFile, saveFile, deleteFile } = useSandboxFiles(autoLoad)
  const [editorPath, setEditorPath] = useState('')
  const [editorContent, setEditorContent] = useState('')

  const loadEditorFile = useCallback(
    async (path?: string) => {
      const target = (path ?? editorPath).trim()
      if (!target) {
        return false
      }
      const content = await loadFile(target)
      setEditorContent(content)
      setEditorPath(target)
      return true
    },
    [editorPath, loadFile]
  )

  const saveEditorFile = useCallback(async () => {
    const path = editorPath.trim()
    if (!path) {
      throw new Error('Enter a file path to save')
    }
    await saveFile(path, editorContent)
  }, [editorPath, editorContent, saveFile])

  const clearEditor = useCallback(() => {
    setEditorPath('')
    setEditorContent('')
  }, [])

  return {
    files,
    tree,
    loading,
    refresh,
    deleteFile,
    editorPath,
    setEditorPath,
    editorContent,
    setEditorContent,
    loadEditorFile,
    saveEditorFile,
    clearEditor,
  }
}
