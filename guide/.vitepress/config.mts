// The Lekto user guide: a VitePress site published on GitHub Pages (ADR-0024).
//
// The content root is this directory (`guide/`), never the repository's internal
// `docs/` tree, so an ADR, `CONTEXT.md` or a research report can never be
// published by accident. VitePress builds from its working directory, and CI
// runs the build from `guide/`, so only the pages below become a part of the site.
import { defineConfig } from "vitepress";

const repository = "https://github.com/Domingax/lekto";

const sections = [
  { text: "Getting started", link: "/getting-started" },
  { text: "Import", link: "/import" },
  { text: "Library", link: "/library" },
  { text: "Reading", link: "/reading" },
  { text: "Word lookup", link: "/word-lookup" },
  { text: "Vocabulary", link: "/vocabulary" },
  { text: "Pronunciation", link: "/pronunciation" },
  { text: "LLM provider and privacy", link: "/llm-provider" },
  { text: "Vault and sync", link: "/vault-and-sync" },
  { text: "Settings and FAQ", link: "/settings-and-faq" },
];

export default defineConfig({
  title: "Lekto",
  description:
    "The Lekto user guide: read and learn a language with the books you already own.",
  // The GitHub Pages project site lives under /lekto/, not at the domain root.
  // Pages keep their `.html` suffix: GitHub Pages serves a static artifact, so a
  // link that ends in `.html` resolves on every host without a rewrite rule.
  base: "/lekto/",
  // Internal guide notes (`AGENTS.md`, `README.md`) are not pages and must never
  // be published; the build still reads them, it just does not render them.
  srcExclude: ["**/AGENTS.md", "**/README.md"],
  head: [["link", { rel: "icon", href: "/lekto/favicon.svg" }]],
  themeConfig: {
    siteTitle: "Lekto",
    // Built-in local search: the index is built into the site, so no query ever
    // leaves the reader's browser and no hosted service is involved (ADR-0002).
    search: { provider: "local" },
    // Every page is a file in this repository, so a reader can fix it directly.
    editLink: {
      pattern: `${repository}/edit/main/guide/:path`,
      text: "Edit this page on GitHub",
    },
    socialLinks: [{ icon: "github", link: repository }],
    nav: [
      { text: "Guide", link: "/getting-started" },
      { text: "Download", link: "/#download" },
      { text: "GitHub", link: repository },
    ],
    sidebar: sections,
    footer: {
      message: "Released under the AGPL-3.0 licence.",
      copyright: "Lekto",
    },
  },
  // The tool is i18n-ready: the locale map and the `fr/` tree exist so the French
  // translation can land page by page, but English is the only written guide.
  locales: {
    root: { label: "English", lang: "en" },
    fr: {
      label: "Français",
      lang: "fr",
      link: "/fr/",
      themeConfig: {
        sidebar: [{ text: "Guide Lekto", link: "/fr/" }],
      },
    },
  },
});
