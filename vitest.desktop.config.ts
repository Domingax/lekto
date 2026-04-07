import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['e2e/desktop/**/*.spec.ts'],
    environment: 'node',
    testTimeout: 60_000,
    hookTimeout: 60_000,
    pool: 'forks',
    poolOptions: {
      forks: { singleFork: true },
    },
  },
})
