import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import type { BookEntity } from '@/entities'
import { BookListItem } from './BookListItem'

const book: BookEntity = {
  id: 'b1',
  title: 'Don Quixote',
  author: 'Miguel de Cervantes',
  fileName: 'don-quixote.epub',
  language: 'es',
  coverPath: null,
  createdAt: 1000,
}

describe('BookListItem', () => {
  it('renders title, author, language badge, and progress percentage', () => {
    render(
      <BookListItem
        book={book}
        progressPct={23}
        languageName="Spanish"
        onOpen={vi.fn()}
        onRequestDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('Don Quixote')).toBeInTheDocument()
    expect(screen.getByText('Miguel de Cervantes')).toBeInTheDocument()
    expect(screen.getByText('Spanish')).toBeInTheDocument()
    expect(screen.getByText('23%')).toBeInTheDocument()
  })

  it('does not render author line when author is null', () => {
    const bookNoAuthor: BookEntity = { ...book, author: null }
    render(
      <BookListItem
        book={bookNoAuthor}
        progressPct={0}
        languageName="Spanish"
        onOpen={vi.fn()}
        onRequestDelete={vi.fn()}
      />,
    )

    expect(screen.queryByText('Miguel de Cervantes')).not.toBeInTheDocument()
    expect(screen.getByText('Don Quixote')).toBeInTheDocument()
  })

  it('calls onOpen with book id when row is clicked', () => {
    const onOpen = vi.fn()
    render(
      <BookListItem
        book={book}
        progressPct={5}
        languageName="Spanish"
        onOpen={onOpen}
        onRequestDelete={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByText('Don Quixote'))
    expect(onOpen).toHaveBeenCalledWith('b1')
  })

  it('calls onRequestDelete with book and does NOT call onOpen when trash button is clicked', () => {
    const onOpen = vi.fn()
    const onRequestDelete = vi.fn()
    render(
      <BookListItem
        book={book}
        progressPct={5}
        languageName="Spanish"
        onOpen={onOpen}
        onRequestDelete={onRequestDelete}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: /delete don quixote/i }))
    expect(onRequestDelete).toHaveBeenCalledWith(book)
    expect(onOpen).not.toHaveBeenCalled()
  })

  it('trash button has accessible aria-label', () => {
    render(
      <BookListItem
        book={book}
        progressPct={0}
        languageName="Spanish"
        onOpen={vi.fn()}
        onRequestDelete={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: /delete don quixote/i })).toBeInTheDocument()
  })
})
