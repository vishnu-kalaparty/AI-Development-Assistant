import { useCallback, useEffect, useState } from 'react'
import { SandboxTreeNode } from '../components/SandboxFileTree'
import { deleteSandboxFile, listSandboxFiles, readSandboxFile, writeSandboxFile } from '../api/sandboxApi'

export function useSandboxFiles(autoLoad = true) {
  const [files, setFiles] = useState<string[]>([])
  const [tree, setTree] = useState<SandboxTreeNode[]>([])
  const [loading, setLoading] = useState(false)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const result = await listSandboxFiles()
      setFiles(result.files)
      setTree(result.tree)
    } catch (error) {
      console.error('Error loading sandbox files:', error)
      setFiles([])
      setTree([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (autoLoad) {
      void refresh()
    }
  }, [autoLoad, refresh])

  const loadFile = useCallback(async (filePath: string) => {
    const response = await readSandboxFile(filePath)
    return response.content
  }, [])

  const saveFile = useCallback(
    async (filePath: string, content: string) => {
      await writeSandboxFile(filePath, content)
      await refresh()
    },
    [refresh]
  )

  const deleteFile = useCallback(
    async (filePath: string) => {
      await deleteSandboxFile(filePath)
      await refresh()
    },
    [refresh]
  )

  return { files, tree, loading, refresh, loadFile, saveFile, deleteFile }
}
