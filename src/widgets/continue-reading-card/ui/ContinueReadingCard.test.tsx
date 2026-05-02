import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import type { BookEntity } from '@/entities'
import { ContinueReadingCard } from './ContinueReadingCard'

const book: BookEntity = {
  id: 'b1',
  title: 'Moby Dick',
  author: 'Herman Melville',
  fileName: 'moby-dick.epub',
  language: 'en',
  coverPath: null,
  createdAt: 1000,
}

describe('ContinueReadingCard', () => {
  it('renders title, chapter title, and progress percentage', () => {
    render(
      <ContinueReadingCard
        book={book}
        chapterTitle="Chapter 3: The Spouter-Inn"
        progressPct={42}
        onResume={vi.fn()}
      />,
    )

    expect(screen.getByText('Moby Dick')).toBeInTheDocument()
    expect(screen.getByText('Chapter 3: The Spouter-Inn')).toBeInTheDocument()
    expect(screen.getByText('42%')).toBeInTheDocument()
  })

  it('renders without crashing when chapterTitle is null', () => {
    render(
      <ContinueReadingCard
        book={book}
        chapterTitle={null}
        progressPct={10}
        onResume={vi.fn()}
      />,
    )

    expect(screen.getByText('Moby Dick')).toBeInTheDocument()
    expect(screen.queryByText('Chapter 3: The Spouter-Inn')).not.toBeInTheDocument()
  })

  it('calls onResume with book id when Resume button is clicked', () => {
    const onResume = vi.fn()
    render(
      <ContinueReadingCard
        book={book}
        chapterTitle="Intro"
        progressPct={5}
        onResume={onResume}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: /resume/i }))
    expect(onResume).toHaveBeenCalledWith('b1')
  })
})
