import type { AsyncResult } from '../../lib/types'

export interface FilesystemAdapter {
  readFile(path: string): AsyncResult<string>
  writeFile(path: string, data: string): AsyncResult<void>
  deleteFile(path: string): AsyncResult<void>
  mkdir(path: string): AsyncResult<void>
  readdir(path: string): AsyncResult<string[]>
  exists(path: string): AsyncResult<boolean>
  copyFile(src: string, dest: string): AsyncResult<void>
}
