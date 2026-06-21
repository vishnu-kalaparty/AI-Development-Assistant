import axios from 'axios'
import { SandboxTreeNode } from '../components/SandboxFileTree'

export async function listSandboxFiles(
  prefix?: string
): Promise<{ files: string[]; tree: SandboxTreeNode[]; prefix?: string }> {
  const result = await axios.get('/api/sandbox/files', {
    params: prefix?.trim() ? { prefix: prefix.trim() } : undefined,
  })
  return {
    files: result.data.files || [],
    tree: result.data.tree || [],
    prefix: result.data.prefix,
  }
}

export async function readSandboxFile(filePath: string): Promise<{ content: string; message?: string }> {
  const response = await axios.post('/api/sandbox/read', { filePath })
  return response.data
}

export async function writeSandboxFile(filePath: string, content: string): Promise<void> {
  await axios.post('/api/sandbox/write', { filePath, content })
}

export async function deleteSandboxFile(filePath: string): Promise<void> {
  await axios.delete('/api/sandbox/delete', { data: { filePath } })
}
