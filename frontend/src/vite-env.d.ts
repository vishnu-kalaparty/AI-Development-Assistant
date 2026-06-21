/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string
  readonly VITE_SDLC_USE_API?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
