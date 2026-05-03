import type { TokenEntity } from '@/entities/token'

interface WordTokenProps {
  token: TokenEntity
}

export function WordToken({ token }: Readonly<WordTokenProps>) {
  if (token.type === 'whitespace') {
    return <span>{token.text}</span>
  }
  if (token.type === 'punctuation') {
    return <span data-type="punctuation">{token.text}</span>
  }
  return (
    <span
      data-type="word"
      data-word-key={token.wordKey ?? undefined}
      data-token-id={token.id}
    >
      {token.text}
    </span>
  )
}
