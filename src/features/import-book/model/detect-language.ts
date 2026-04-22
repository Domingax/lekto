const ISO3_TO_ISO1: Record<string, string> = {
  eng: 'en',
  fra: 'fr',
  spa: 'es',
  deu: 'de',
  ita: 'it',
  por: 'pt',
  nld: 'nl',
  rus: 'ru',
  zho: 'zh',
  jpn: 'ja',
  kor: 'ko',
  ara: 'ar',
  pol: 'pl',
  swe: 'sv',
  tur: 'tr',
}

/** Detect ISO 639-1 code from text. Returns null if detection fails. */
export async function detectLanguage(text: string): Promise<string | null> {
  const { franc } = await import('franc')
  const code3 = franc(text.slice(0, 3000))
  return ISO3_TO_ISO1[code3] ?? null
}
