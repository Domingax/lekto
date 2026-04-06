import { isTauri } from '../is-tauri'
import { createAndroidFilePickerAdapter } from './file-picker.android'
import { createDesktopFilePickerAdapter } from './file-picker.desktop'
import type { FilePickerAdapter, PickedFile } from './file-picker.interface'

export type { FilePickerAdapter, PickedFile }

export const filePickerAdapter: FilePickerAdapter = isTauri()
  ? createDesktopFilePickerAdapter()
  : createAndroidFilePickerAdapter()
