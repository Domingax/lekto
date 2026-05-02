export interface BookEntity {
  id: string
  title: string
  author: string | null
  fileName: string
  language: string
  coverPath: string | null
  createdAt: number
}
