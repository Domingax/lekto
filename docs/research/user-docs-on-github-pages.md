# Publishing end-user documentation on GitHub Pages: a generator and pipeline decision

**Date:** 2026-10-07
**Scope:** Which tool chain should publish Lekto's **end-user documentation** (how to
use the app) as a **GitHub Pages** site, given: Kotlin Multiplatform app (Android
first, desktop second), **AGPL-3.0** source under ADR-0011 (every dependency,
test-scope included, must be AGPL-compatible), build and release in **GitHub
Actions**, and **no release workflow yet** (only `ci.yml`, `dictionary-pack.yml`,
`nightly.yml`). English/French is the target language pair.
**Method:** Primary sources only — each generator's own documentation, repository
and licence; GitHub's own Pages and Actions docs; the `actions/*` Pages actions;
and the FSF licence list as the authority for AGPL compatibility. Versions and
page dates were read on **2026-10-07**. Every claim carries a URL. Unverified
points are gathered in [§Unverified and flagged](#unverified-and-flagged).

> **Headline:** GitHub's own docs now *recommend a GitHub Actions workflow* over
> branch publishing for any non-Jekyll build, and the three Actions
> (`actions/configure-pages`, `actions/upload-pages-artifact`,
> `actions/deploy-pages`) are the supported mechanism. The generator choice is
> therefore really a choice about **runtime, versioning and i18n** — and that
> field moved sharply in late 2025: **MkDocs 1.x has not shipped since 30 Aug
> 2024 and Material for MkDocs is in maintenance mode**, with its author having
> built a successor, **Zensical**, and publicly calling MkDocs "a supply chain
> risk". That knocks the historically obvious answer (MkDocs + Material) off its
> default pedestal. **VitePress** (MIT, first-class i18n, built-in local search)
> is the lightest fit for a mostly-flat end-user guide; **Docusaurus** (MIT,
> Node) is the strongest if you want first-class versioning and i18n together;
> **mdBook + `mdbook-i18n-helpers`** is the smallest dependency surface and its
> licence (MPL-2.0) is AGPL-compatible, but it lacks first-class versioning.
> None of the candidates has a non-free or AGPL-incompatible licence.

---

## Version snapshot (read 2026-10-07)

| Component | Version / status | Licence | Source |
|---|---|---|---|
| MkDocs | **1.6.1** — latest release **2024-08-30**; no later release | BSD-2-Clause | [MkDocs release notes](https://www.mkdocs.org/about/release-notes/), [MkDocs licence](https://www.mkdocs.org/about/license/) |
| Material for MkDocs | **9.7.0 — "the final version"**; **maintenance mode** | MIT | [Insiders now free](https://squidfunk.github.io/mkdocs-material/blog/2025/11/11/insiders-now-free-for-everyone/), [licence](https://squidfunk.github.io/mkdocs-material/license/) |
| Zensical (MkDocs successor) | released; reads `mkdocs.yml`; feature parity pending | MIT | [Zensical announcement](https://squidfunk.github.io/mkdocs-material/blog/2025/11/05/zensical/) |
| Docusaurus | **3.10.2** (3.10 "is out!") | MIT | [Docusaurus](https://docusaurus.io/), [`LICENSE`](https://raw.githubusercontent.com/facebook/docusaurus/main/LICENSE) |
| VitePress | **2.0.0-alpha.20** (pre-release) / **1.6.4** stable | MIT | [VitePress](https://vitepress.dev/) |
| mdBook | **0.5.4** | **MPL-2.0** | [mdBook guide](https://rust-lang.github.io/mdBook/), [repo](https://github.com/rust-lang/mdBook) |
| Just the Docs (Jekyll theme) | current `main` | MIT | [Just the Docs](https://just-the-docs.com/) |
| Docsify | repo `develop` (v4 line) | MIT | [Docsify repo](https://github.com/docsifyjs/docsify) |
| Jekyll (standalone) | **4.4.1** | MIT | [Jekyll](https://jekyllrb.com/) |
| Jekyll on GitHub Pages (pinned) | **3.10.0**, Ruby 3.3.4, fixed gem set | — | [Pages dependency versions](https://pages.github.com/versions/) |
| mike (MkDocs versioning) | 743★, Python | BSD-3-Clause | [jimporter/mike](https://github.com/jimporter/mike) |
| mkdocs-static-i18n | **frozen as-is** (upstream unmaintained) | MIT | [ultrabug/mkdocs-static-i18n](https://github.com/ultrabug/mkdocs-static-i18n) |
| mdbook-i18n-helpers | 226★, Google (not officially supported) | Apache-2.0 | [google/mdbook-i18n-helpers](https://github.com/google/mdbook-i18n-helpers) |

---

## 1. Candidate generators

### 1.1 MkDocs + Material for MkDocs

- **Runtime in CI:** Python. MkDocs is configured by a single `mkdocs.yml` and
  writes static HTML to `site/`; it advertises "GitHub Pages, Amazon S3, or
  anywhere" ([MkDocs home](https://www.mkdocs.org/), [deploying](https://www.mkdocs.org/user-guide/deploying-your-docs/)).
  Material installs from PyPI (`pip install mkdocs-material`) and is also
  published as a Docker image ([Material home](https://squidfunk.github.io/mkdocs-material/)).
- **Licence:** MkDocs is **BSD-2-Clause** ("Redistribution and use in source and
  binary forms…", [licence page](https://www.mkdocs.org/about/license/));
  Material for MkDocs is **MIT** ([licence](https://squidfunk.github.io/mkdocs-material/license/)).
  mkdocs-static-i18n is MIT. All are GPL/AGPL-compatible (see §6).
- **Built-in search:** MkDocs ships a **`search` plugin enabled by default**
  which "uses lunr.js as a search engine", with per-language indexes
  (`lang: [en, fr]`) via Lunr Languages ([MkDocs configuration § Search](https://www.mkdocs.org/user-guide/configuration/#search)).
  Material layers its own richer search UI on top.
- **Versioning:** not in MkDocs itself; the documented route is **`mike`**
  ([Material versioning](https://squidfunk.github.io/mkdocs-material/setup/setting-up-versioning/), [mike](https://github.com/jimporter/mike)).
- **i18n:** the **theme UI** is translated (Material claims 60+ languages,
  [home](https://squidfunk.github.io/mkdocs-material/)), but **content**
  multi-language needs a plugin: `mkdocs-static-i18n` was the answer and is now
  **frozen** ("this project is frozen as-is" because "MkDocs upstream [is]
  unmaintained and uncertain", [repo](https://github.com/ultrabug/mkdocs-static-i18n)).
- **Authoring:** Markdown + `mkdocs.yml`; Python-Markdown extensions
  ([configuration](https://www.mkdocs.org/user-guide/configuration/)).
- **2026 status — the decisive fact.** MkDocs' last release is **1.6.1,
  30 Aug 2024** ([release notes](https://www.mkdocs.org/about/release-notes/)).
  Material's creator writes: "MkDocs must be considered a supply chain risk,
  since it's unmaintained since August 2024. It has seen no releases in over a
  year", and "**Material for MkDocs is in maintenance mode**", committed for
  "at least the next 12 months" ([Zensical announcement](https://squidfunk.github.io/mkdocs-material/blog/2025/11/05/zensical/),
  [Insiders now free](https://squidfunk.github.io/mkdocs-material/blog/2025/11/11/insiders-now-free-for-everyone/)).
  The successor is Zensical (MIT), which "can natively read `mkdocs.yml`" but has
  not reached full plugin parity; **i18n and versioning are on the Zensical
  roadmap, not yet shipped** ([Zensical](https://squidfunk.github.io/mkdocs-material/blog/2025/11/05/zensical/)).
  MkDocs 2.0 will "introduce breaking changes" ([MkDocs 2.0 article](https://squidfunk.github.io/mkdocs-material/blog/2026/02/18/mkdocs-2.0/)).

### 1.2 Docusaurus

- **Runtime in CI:** Node.js **≥ 20.0** ([installation § Requirements](https://docusaurus.io/docs/installation));
  current **3.10.2**. Build output goes to `build/` ([deployment](https://docusaurus.io/docs/deployment)).
- **Licence:** **MIT** ([`LICENSE`](https://raw.githubusercontent.com/facebook/docusaurus/main/LICENSE)).
- **Built-in search:** **first-class support is Algolia DocSearch** (hosted;
  "free for any developer documentation or technical blog" subject to a
  checklist/application); local search plugins are **community-maintained**
  ([search](https://docusaurus.io/docs/search)).
- **Versioning:** **first-class**, via the `docusaurus docs:version <x>` CLI,
  `versioned_docs/`, `versions.json`, a versions dropdown, and per-version
  banners/labels ([versioning](https://docusaurus.io/docs/versioning)).
- **i18n:** **first-class**. Declare locales in `docusaurus.config.js`, place
  translated Markdown/JSON under `i18n/<locale>/…`; supports Git, Crowdin or any
  manager, RTL, and `hreflang` SEO defaults ([i18n introduction](https://docusaurus.io/docs/i18n/introduction)).
- **Authoring:** **MDX** — Markdown plus embedded React components
  ([home](https://docusaurus.io/), [installation](https://docusaurus.io/docs/installation)).
- **2026 status:** actively released — the site's own banner reads "Docusaurus
  v3.10 is out!", docs pages show 3.10.2 and a per-version archive
  ([Docusaurus](https://docusaurus.io/), [versioning](https://docusaurus.io/docs/versioning)).

### 1.3 VitePress

- **Runtime in CI:** Node **20 or above** ("Node Version: 20 (or above)");
  output to `docs/.vitepress/dist` ([deploy](https://vitepress.dev/guide/deploy)).
  Current docs advertise **2.0.0-alpha.20** with **1.6.4** as the 1.x line
  ([site header](https://vitepress.dev/)) — i.e. **2.0 is still pre-release**.
- **Licence:** **MIT** ("Released under the MIT License") on every page footer,
  e.g. [guide](https://vitepress.dev/guide/what-is-vitepress).
- **Built-in search:** **local, built-in** — "fuzzy full-text search using an
  in-browser index thanks to minisearch", enabled with
  `themeConfig.search.provider: 'local'`; per-locale strings; Algolia optional
  ([search](https://vitepress.dev/reference/default-theme-search)).
- **Versioning:** **no first-class versioning** documented. The i18n/deploy pages
  show how to serve multiple locales; multi-version docs are a "build each
  version to its own path" pattern, not a product feature. *(Flagged —
  [§Unverified](#unverified-and-flagged).)*
- **i18n:** **first-class** via a `locales` map and `docs/<lang>/` directories,
  per-locale `lang`/`dir` (RTL supported with CSS logical properties), a navbar
  language menu, and a separate directory layout with a documented root-redirect
  recipe ([i18n](https://vitepress.dev/guide/i18n)).
- **Authoring:** Markdown + Vue (Vue components usable in Markdown)
  ([home](https://vitepress.dev/)).
- **2026 status:** active; the deploy and search docs are current and reference
  recent actions (`actions/setup-node@v6`), but VitePress itself is on a 2.0
  alpha with 1.6.x as the stable fallback ([deploy](https://vitepress.dev/guide/deploy),
  [site header](https://vitepress.dev/)).

### 1.4 mdBook

- **Runtime in CI:** Rust. mdBook is "a command line tool to create books with
  Markdown"; current **0.5.4**; ships a **GitHub Pages starter workflow**
  (`pages/mdbook.yml`) ([mdBook guide](https://rust-lang.github.io/mdBook/), [Pages starter workflows](https://github.com/actions/starter-workflows/tree/main/pages)).
- **Licence:** **MPL-2.0** ([repo](https://github.com/rust-lang/mdBook), [guide § License](https://rust-lang.github.io/mdBook/)).
- **Built-in search:** yes — "Integrated search support" ([guide](https://rust-lang.github.io/mdBook/), keybindings note "Press S or / to search").
- **Versioning:** **none first-class** in the guide. *(Flagged.)*
- **i18n:** **none built-in**; the de-facto mechanism is `mdbook-i18n-helpers`
  (Gettext), maintained by Google but "not an officially supported Google
  product" ([repo](https://github.com/google/mdbook-i18n-helpers)).
- **Authoring:** Markdown with a `SUMMARY.md` table of contents; preprocessors
  and backends for extension ([guide](https://rust-lang.github.io/mdBook/)).
- **2026 status:** maintained under the `rust-lang` org, 22.2k★, and it has a
  first-party Pages starter workflow ([repo](https://github.com/rust-lang/mdBook), [starter workflows](https://github.com/actions/starter-workflows/tree/main/pages)).

### 1.5 Just the Docs (Jekyll theme) and plain Jekyll

- **Runtime in CI:** Ruby / Jekyll. Just the Docs "is a theme for generating
  static websites with Jekyll"; its template "uses the GitHub Pages / Actions
  workflow" and a gem-based `Gemfile` ([Just the Docs home](https://just-the-docs.com/)).
  Standalone Jekyll is **4.4.1** ([jekyllrb.com](https://jekyllrb.com/)); **GitHub
  Pages pins Jekyll 3.10.0 with a fixed gem set** ([Pages dependency versions](https://pages.github.com/versions/)).
- **Licence:** both **MIT** ([Just the Docs](https://just-the-docs.com/), [Jekyll](https://jekyllrb.com/)).
- **Built-in search:** Just the Docs ships **client-side search** ("Search Just
  the Docs", [docs](https://just-the-docs.com/docs/search/)); **plain Jekyll has
  no search**.
- **Versioning:** neither has first-class versioning.
- **i18n:** not first-class in either. Jekyll i18n relies on third-party plugins,
  and GitHub Pages' branch build **cannot run unsupported plugins**: "GitHub
  Pages cannot build sites using unsupported plugins. If you want to use
  unsupported plugins, generate your site locally and then push your site's
  static files" ([GitHub Pages and Jekyll](https://docs.github.com/en/pages/setting-up-a-github-pages-site-with-jekyll/about-github-pages-and-jekyll)).
- **Authoring:** Markdown, **Liquid** templating and HTML ([Just the Docs](https://just-the-docs.com/)).
- **2026 status:** Just the Docs is actively maintained ("© 2017-2026 … primarily
  maintained by Matt Wang", [home](https://just-the-docs.com/)); Jekyll is
  maintained by a volunteer core team ([Jekyll](https://jekyllrb.com/)).

### 1.6 Docsify

- **Runtime in CI:** **none** — "Docsify turns one or more Markdown files into a
  Website, **with no build process required**". The browser renders Markdown at
  load time ([repo](https://github.com/docsifyjs/docsify)).
- **Licence:** **MIT** ([repo](https://github.com/docsifyjs/docsify)).
- **Built-in search:** "Smart full-text search plugin" ([repo](https://github.com/docsifyjs/docsify)).
- **Versioning / i18n:** not built in; supplied by plugins. *(Flagged.)*
- **Authoring:** plain Markdown; configuration in `index.html`/`window.$docsify`.
- **2026 status:** large and still-visible (31.5k★, 271 watchers) but with 63
  open issues and 42 open PRs on the default `develop` branch ([repo](https://github.com/docsifyjs/docsify)).
  Because it is client-side rendered, its **SEO and first-paint** are weaker than
  a pre-rendered SSG's — relevant for a "how to use the app" site that should be
  discoverable. *(Flagged as a judgement, not a cited fact.)*

---

## 2. GitHub Pages deployment mechanics

### The two publishing models

GitHub Pages accepts exactly two sources: **"Deploy from a branch"** (a branch
plus the repo root `/` or a `/docs` folder) or **"GitHub Actions"** ([configuring a publishing source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)).
GitHub's own guidance: publish from a branch "if you do not need any control over
the build process"; use an Actions workflow "if you want to use a build process
other than Jekyll or you do not want a dedicated branch to hold your compiled
static files" ([same page](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)).

Key constraints that shape the choice:

- **Branch publishing is Jekyll by default.** "If you publish your site from a
  source branch, GitHub Pages will use Jekyll to build your site by default. If
  you want to use a static site generator other than Jekyll, we recommend that
  you write a GitHub Actions [workflow] to build and publish your site instead."
  ([creating a GitHub Pages site § Static site generators](https://docs.github.com/en/pages/getting-started-with-github-pages/creating-a-github-pages-site#static-site-generators)).
- **Branch publishing pins Jekyll 3.10.0 and a fixed plugin allowlist**; sites
  using unsupported plugins must build locally/CI and push static files
  ([Pages dependency versions](https://pages.github.com/versions/), [Pages and Jekyll](https://docs.github.com/en/pages/setting-up-a-github-pages-site-with-jekyll/about-github-pages-and-jekyll)).
- **The entry file matters.** Branch publishing looks for `index.html`,
  `index.md` or `README.md` at the top level of the source folder; an Actions
  deploy must place the entry file at the top level of the **artifact** ([creating a site](https://docs.github.com/en/pages/getting-started-with-github-pages/creating-a-github-pages-site)).
- **`GITHUB_TOKEN` pushes do not trigger a branch Pages build** ("Commits pushed
  by a GitHub Actions workflow that uses the `GITHUB_TOKEN` do not trigger a
  GitHub Pages build") ([configuring a publishing source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)).
- **Pages has no server-side runtime** — "GitHub Pages does not support
  server-side languages such as PHP, Ruby, or Python" ([creating a site](https://docs.github.com/en/pages/getting-started-with-github-pages/creating-a-github-pages-site)).
  (This only bars *runtime*; building with Python/Ruby/Rust in Actions is fine.)
- **The site is public even if the repo is private** ([configuring a source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)).

### The official Actions pipeline

GitHub's documented general flow ([configuring a publishing source § Creating a custom workflow](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site#creating-a-custom-github-actions-workflow-to-publish-your-site),
[using custom workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)):

1. trigger on a push to the default branch (and/or `workflow_dispatch`);
2. `actions/checkout`;
3. build the static files;
4. **`actions/upload-pages-artifact`** — the artifact must be "a compressed `gzip`
   archive containing a single `tar` file", under 10 GB, with **no symbolic or
   hard links**;
5. **`actions/deploy-pages`** — skipped for pull requests.

And **`actions/configure-pages`** ("An action to enable Pages and extract various
metadata about a site", [repo](https://github.com/actions/configure-pages)) wires
the generator to Pages and emits `base_path`/`origin` metadata.

**Required repo settings and permissions** (all from [using custom workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)):

- In **Settings → Pages → Build and deployment → Source**, select **GitHub Actions**.
- The deploy job must have **`pages: write` and `id-token: write`**; the docs show
  `contents: read` alongside them.
- The deploy job must set **`needs:`** to the build job id ("Not setting this
  parameter may result in an independent deployment that continuously searches
  for an artifact that hasn't been created").
- An **`environment`** is required, default name **`github-pages`**, and its `url`
  is taken from `steps.deployment.outputs.page_url`.
- GitHub recommends a deployment protection rule so only the default branch can
  deploy the environment ([configuring a source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)).

Minimal shape (versions from [using custom workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)):

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/configure-pages@v5
      # ... build the site ...
      - uses: actions/upload-pages-artifact@v4
        with:
          path: ./site

  deploy:
    needs: build
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    permissions:
      pages: write
      id-token: write
    runs-on: ubuntu-latest
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

**`gh-pages` branch vs Actions.** GitHub still documents the legacy pattern:
"Most external CI workflows 'deploy' to GitHub Pages by committing the build
output to the `gh-pages` branch of the repository, and typically include a
`.nojekyll` file" ([configuring a source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)).
Both `mkdocs gh-deploy` and `mike` use exactly this model (§3, §4). It works, but
it is a **second branch to keep clean** and it bypasses the artifact/environment
protections the Actions pipeline gives you.

---

## 3. Versioned docs

Users of an installed app do not need historical docs the way an API's users do;
versioning is optional here, but the question asks how each generator does it.

| Generator | Mechanism | Tied to a tag/release how |
|---|---|---|
| **Docusaurus** | `docusaurus docs:version <x>` copies `docs/` into `versioned_docs/version-<x>/`, snapshots sidebars, appends to `versions.json`; `lastVersion`/`onlyIncludeVersions`/banners per version ([versioning](https://docusaurus.io/docs/versioning)) | Run `docs:version <tag>` in a workflow triggered by the release/tag; commit the new `versioned_docs` |
| **MkDocs + mike** | `mike deploy <version> <alias>` writes a commit to the docs branch; `mike set-default`, `mike alias`, `mike delete`; "once you've generated your docs for a particular version, you should never need to touch that version again" ([mike](https://github.com/jimporter/mike), [Material versioning](https://squidfunk.github.io/mkdocs-material/setup/setting-up-versioning/)) | Run `mike deploy "${{ github.ref_name }}" latest` on a tag/release; mike commits the built HTML, so old versions keep their old MkDocs |
| **mdBook** | none first-class | Deploy each build to its own path yourself |
| **VitePress / Just the Docs / Jekyll / Docsify** | none first-class | Build each version into its own prefix |

The mike design point is worth quoting because it is its main selling point:
versioning at the **built-HTML** level means "you never have to worry about
breaking changes in MkDocs, since your old docs … are already generated and
sitting in your `gh-pages` branch" ([mike](https://github.com/jimporter/mike)).

---

## 4. Automation

### Trigger on merge, with path filters

`on.push.paths` / `paths-ignore` run a workflow only when matching files change,
and "if you define both `branches`/`branches-ignore` and `paths`/`paths-ignore`,
the workflow will only run when both filters are satisfied" ([workflow syntax § paths](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#onpushpull_requestpull_request_targetpathspaths-ignore)).
That is the natural trigger for "docs changed, rebuild the site":

```yaml
on:
  push:
    branches: [main]
    paths:
      - 'docs/user/**'
      - 'mkdocs.yml'      # or vitepress.config, docusaurus.config.js, ...
```

**Caveat:** "Path filters are not evaluated for pushes of tags" ([same page](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#onpushpull_requestpull_request_targetpathspaths-ignore)),
so a release-triggered docs build cannot rely on `paths`.

### Trigger on tag / release

`on.push.tags` matches tag refs such as `v1.*` ([workflow syntax § on.push tags](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#onpushtagsbranches-ignore)). GitHub also
provides `create` ("Runs your workflow when someone creates a Git reference (Git
branch or tag)"), `page_build` (on a push to a Pages publishing branch) and a
`release` event ([events that trigger workflows](https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows)).
A release-time docs job typically does `docusaurus docs:version "$TAG"` or
`mike deploy "$TAG" latest`.

### `mkdocs gh-deploy` vs a Pages Actions pipeline

- **`mkdocs gh-deploy`**: "MkDocs will build your docs and use the
  [ghp-import](https://github.com/davisp/ghp-import) tool to commit them to the
  `gh-pages` branch and push" ([MkDocs deploying](https://www.mkdocs.org/user-guide/deploying-your-docs/)).
  Caveats MkDocs itself lists: "you will not be able to review the built site
  before it is pushed to GitHub" and "If there are untracked files or uncommitted
  work… these will be included".
- **Pages Actions pipeline**: build in CI, upload an artifact, deploy with
  `deploy-pages` (§2). This is what GitHub now recommends for non-Jekyll builds,
  and it gives you a PR build check before deploy.
- **mike + CI**: because mike writes commits, CI must fetch the docs branch first
  ("some CI systems make shallow clones … just manually fetch your `gh-pages`
  branch") and set a Git identity ([mike § Deploying via CI](https://github.com/jimporter/mike)).

### Keeping docs in sync with each merged feature

Two complementary triggers, both already the shape this repo uses:

1. **Every push to `main` that touches docs paths** → build and deploy the
   "current" site (path-filtered workflow). This keeps the published guide in
   lock-step with merged features, exactly as `ci.yml` runs `./gradlew check` on
   every push and PR (`.github/workflows/ci.yml`).
2. **On release/tag** → additionally cut a version (Docusaurus `docs:version`,
   mike `deploy`) so installed-version users keep matching docs.

A third, cheap guard: a **`--strict`/link-check** build on pull requests so a
broken internal link fails CI. MkDocs supports `--strict` ([configuration § strict](https://www.mkdocs.org/user-guide/configuration/#strict)),
and MkDocs 1.6 added anchor/link validation ([release notes 1.6.0](https://www.mkdocs.org/about/release-notes/)).

---

## 5. Multi-language docs (English/French)

| Generator | First-class i18n? | What it costs |
|---|---|---|
| **Docusaurus** | **Yes** | Declare locales; place translated files under `i18n/fr/…`; `write-translations` scaffolds JSON; supports Git, Crowdin or any manager; RTL + `hreflang` handled ([i18n](https://docusaurus.io/docs/i18n/introduction)) |
| **VitePress** | **Yes** | A `locales` map plus a `docs/fr/` (or `docs/<lang>/`) tree; per-locale `lang`/`dir`; navbar language menu; documented root-redirect recipe for hosts without server-side detection ([i18n](https://vitepress.dev/guide/i18n)) |
| **MkDocs + Material** | **Theme only** | Theme UI is translated; **content** i18n needed `mkdocs-static-i18n`, now frozen; Zensical's i18n is still on the roadmap ([Material](https://squidfunk.github.io/mkdocs-material/), [mkdocs-static-i18n](https://github.com/ultrabug/mkdocs-static-i18n), [Zensical](https://squidfunk.github.io/mkdocs-material/blog/2025/11/05/zensical/)) |
| **mdBook** | **No** | Add `mdbook-i18n-helpers` (Gettext) and run per-locale builds; "not an officially supported Google product" ([repo](https://github.com/google/mdbook-i18n-helpers)) |
| **Just the Docs / plain Jekyll** | **No** | Third-party plugins, which GitHub Pages' branch build refuses to run ([Pages and Jekyll](https://docs.github.com/en/pages/setting-up-a-github-pages-site-with-jekyll/about-github-pages-and-jekyll)) |
| **Docsify** | **No** | Plugin-based; not first-class |

Material's own docs page "Changing the language" is about the **theme's** locale,
and the search plugin ships per-language indexes (`lang` option lists, including
`fr`) ([MkDocs search config](https://www.mkdocs.org/user-guide/configuration/#search)).
For a genuinely bilingual **content** site on MkDocs, the frozen
`mkdocs-static-i18n` is the only established route — a real risk to record.

Note that **search must be per-locale** to be useful: MkDocs warns that while
multi-language search exists, "it is best not to add additional languages unless
you really need them" because each adds bandwidth ([search config](https://www.mkdocs.org/user-guide/configuration/#search));
VitePress documents multilingual local-search config ([search](https://vitepress.dev/reference/default-theme-search)).

---

## 6. Licence and AGPL constraints

ADR-0011 requires every dependency, **test-scope included**, to be
"AGPL-compatible for the way we link it", and the FSF list is the authority. For
**build-time tooling**, the same reasoning the testing harness applied to JUnit
applies: a generator is not linked into the shipped AGPL app and is not conveyed
with it ([`docs/research/testing-harness.md`](testing-harness.md) §2). So the bar
is "free and AGPL-compatible", not "permissive".

FSF verdicts ([GNU licence list](https://www.gnu.org/licenses/license-list.html)):

| Licence | Generators | FSF position |
|---|---|---|
| **Expat / MIT** | Docusaurus, VitePress, Material, Just the Docs, Docsify, Jekyll, mkdocs-static-i18n; the `actions/*` Pages actions (`configure-pages`, `upload-pages-artifact`, `deploy-pages`) | "a lax, permissive non-copyleft free software license, compatible with the GNU GPL" |
| **BSD-2-Clause (FreeBSD)** | MkDocs | "lax, permissive non-copyleft free software license, compatible with the GNU GPL" |
| **BSD-3-Clause (Modified BSD)** | mike | "lax, permissive non-copyleft free software license, compatible with the GNU GPL" |
| **Apache-2.0** | mdbook-i18n-helpers | "compatible with version 3 of the GNU GPL" |
| **MPL-2.0** | **mdBook** | "a free software license. Section 3.3 provides indirect compatibility between this license and … the GNU AGPL version 3" — **but** it is *weak file-level copyleft*, and a distributor that opts out with "Incompatible With Secondary Licenses" is **not** compatible |

Conclusions:

- **No candidate has a non-free or AGPL-incompatible licence.** The only one that
  needs a sentence is **mdBook's MPL-2.0**: section 3.3 makes it compatible when
  combined into a "Larger Work" with AGPL-3.0, and because mdBook is a build-only
  tool that never ships, there is no distribution event to trigger at all
  ([FSF, MPL-2.0 entry](https://www.gnu.org/licenses/license-list.html#MPL-2.0)).
- **The repo's own licence gate would not catch docs tooling.** `ci.yml`'s
  `licences` job runs `./gradlew checkDependencyLicences` over the **Gradle**
  dependency graph (`.github/workflows/ci.yml`). npm/Python/Cargo dependencies of
  a docs generator sit outside it. If the team wants the ADR-0011 rule enforced
  mechanically for docs, that is new work (e.g. a `license-checker`/`pip-licenses`
  lane), not something the current gate covers. *This is the single most concrete
  gap the AGPL rule exposes for this decision.*
- **Docusaurus/VitePress pull in large npm trees.** Every transitive build
  dependency is a licence to check if the rule is read strictly; mdBook
  (Cargo) and MkDocs (pip, pinned in `requirements.txt`) have smaller surfaces.
  Flagged as a governance cost, not a blocker.

---

## Comparison

| Criterion | MkDocs+Material | Docusaurus | VitePress | mdBook | Just the Docs / Jekyll | Docsify |
|---|---|---|---|---|---|---|
| **CI runtime** | Python | Node ≥20 | Node 20+ | Rust | Ruby/Jekyll | none |
| **Licence** | BSD-2 / MIT | MIT | MIT | **MPL-2.0** | MIT | MIT |
| **Built-in search** | ✅ lunr (default) | ⚠️ Algolia hosted (free if eligible); local = community | ✅ local MiniSearch | ✅ integrated | ✅ (JtD) / ❌ (Jekyll) | ✅ plugin |
| **Versioning** | via **mike** | ✅ first-class | ❌ | ❌ | ❌ | ❌ |
| **Content i18n** | ⚠️ plugin, **frozen** | ✅ first-class | ✅ first-class | ⚠️ external helper | ❌ (plugins blocked on branch) | ⚠️ plugin |
| **Authoring** | Markdown | MDX (Markdown+React) | Markdown+Vue | Markdown+SUMMARY | Markdown+Liquid | Markdown |
| **2026 status** | ⚠️ **maintenance mode**, MkDocs stale | ✅ active | ⚠️ 2.0 alpha / 1.6 stable | ✅ active | ✅ active | ⚠️ large but slow |
| **Lightest setup for a flat guide** | medium | heavy | **light** | light | medium | **lightest** |
| **GitHub Pages starter workflow** | ❌ (own docs) | ❌ (own docs) | ❌ (own docs) | ✅ `mdbook.yml` | ✅ `jekyll.yml` | ❌ |

---

## Shortlist and trade-offs

Not one verdict but three defensible shapes; pick against what Lekto actually
needs (an end-user guide that should track `main`, likely bilingual, possibly
per-release snapshots).

### A. VitePress — lightest fit for a bilingual end-user guide

- **Why:** MIT, Node-only, **built-in local search** (no Algolia application or
  hosted index), **first-class i18n** with an explicit English/French tree and
  RTL handling, and a copy-paste GitHub Pages Actions workflow in its own docs
  ([search](https://vitepress.dev/reference/default-theme-search), [i18n](https://vitepress.dev/guide/i18n), [deploy](https://vitepress.dev/guide/deploy)).
  The authoring model (Markdown + optional Vue) is the closest to "just
  Markdown" while still giving a polished site.
- **Cost:** **no first-class versioning** — a docs-per-release history means
  building each version to its own path yourself. VitePress 2.0 is still
  **alpha**; pin `1.6.x` for stability ([site header](https://vitepress.dev/)).
  Introduces a Node toolchain the Kotlin repo does not otherwise need.

### B. Docusaurus — if first-class versioning *and* i18n both matter

- **Why:** the only candidate that does **both versioning and content i18n as
  first-class features** ([versioning](https://docusaurus.io/docs/versioning),
  [i18n](https://docusaurus.io/docs/i18n/introduction)), with a documented
  GitHub Pages flow and a per-version archive already running on its own site.
  Its `docs:version` + release-tag trigger is the cleanest "docs match the
  release" story here.
- **Cost:** heaviest toolchain (Node ≥20, MDX/React, large npm tree);
  **first-class search is Algolia DocSearch**, a hosted service that is free only
  if the site qualifies — otherwise you supply local search from the community
  ([search](https://docusaurus.io/docs/search)). MDX means contributors can, but
  need not, write React.

### C. mdBook (+ `mdbook-i18n-helpers`) — smallest, most durable dependency surface

- **Why:** one Rust binary, a **first-party GitHub Pages starter workflow**
  (`pages/mdbook.yml`), integrated search, MPL-2.0 which is AGPL-compatible, and
  a project maintained by the `rust-lang` org ([guide](https://rust-lang.github.io/mdBook/), [repo](https://github.com/rust-lang/mdBook), [starter workflows](https://github.com/actions/starter-workflows/tree/main/pages), [FSF](https://www.gnu.org/licenses/license-list.html#MPL-2.0)).
  For a small, book-shaped guide this is the least machinery.
- **Cost:** **no first-class versioning**, and i18n is an external Gettext helper
  that is explicitly "not an officially supported Google product"
  ([repo](https://github.com/google/mdbook-i18n-helpers)). Adding Rust to CI is a
  new toolchain for a Kotlin repo.

**Explicitly not on the shortlist:** **MkDocs + Material**, despite being the
historical default, because MkDocs is unmaintained since Aug 2024 and Material is
in maintenance mode with a 12-month support commitment and a still-incomplete
successor ([Zensical](https://squidfunk.github.io/mkdocs-material/blog/2025/11/05/zensical/),
[Insiders now free](https://squidfunk.github.io/mkdocs-material/blog/2025/11/11/insiders-now-free-for-everyone/));
**plain Jekyll / Just the Docs**, because GitHub Pages' branch build pins Jekyll
3.10.0 and refuses unsupported plugins, which blocks the i18n plugins a
bilingual site needs ([Pages and Jekyll](https://docs.github.com/en/pages/setting-up-a-github-pages-site-with-jekyll/about-github-pages-and-jekyll),
[Pages versions](https://pages.github.com/versions/)); and **Docsify**, because
client-side rendering weakens SEO for a guide that should be findable
([repo](https://github.com/docsifyjs/docsify)).

### Cross-cutting choices to make whatever generator wins

1. **Which model: Actions artifact vs `gh-pages` branch.** GitHub recommends
   Actions for any non-Jekyll build, and it is the only model with an environment
   and a PR build check ([configuring a source](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)).
   mike forces the branch model; VitePress/Docusaurus/mdBook fit the Actions
   model cleanly.
2. **Where the end-user content lives.** Today `docs/` is **internal** — ADRs,
   research, `build.md`, `testing.md`, the product brief and UX spec. Publishing
   the `docs/` folder as the Pages source would expose all of that. A dedicated
   root (e.g. `docs/user/` as the generator's `docs_dir`, or a separate `site/`
   folder) keeps internal decisions out of the public site. This is independent of
   the generator and should be decided first.
3. **Enforce the ADR-0011 licence rule for docs tooling.** The existing
   `checkDependencyLicences` lane only sees Gradle dependencies
   (`.github/workflows/ci.yml`); add an equivalent check for the chosen
   ecosystem, or record explicitly that docs build tooling is out of scope.
4. **Add the docs workflow without a release workflow existing yet.** The
   path-filtered `main` workflow can ship first; the tag/release versioning step
   can be added when the release workflow lands.

---

## Unverified and flagged

- **VitePress and mdBook versioning.** Neither project documents first-class
  versioning in the pages I read ([VitePress deploy](https://vitepress.dev/guide/deploy),
  [VitePress i18n](https://vitepress.dev/guide/i18n), [mdBook guide](https://rust-lang.github.io/mdBook/)).
  I did not inventory every community plugin; treat "no first-class versioning"
  as true of the core product, not as "impossible".
- **Docsify's release cadence and exact current version.** The repository README
  does not pin a version and npm returned HTTP 403 to this session; the version
  in the snapshot table is inferred from the repo's `develop` branch, not read
  from a release page. Confirm on npm/PyPI/GitHub Releases before relying on it.
  ([repo](https://github.com/docsifyjs/docsify))
- **Docsify i18n support.** I did not find a first-party i18n feature; the claim
  "plugin-based" is inference from its plugin-API design, not a cited statement.
- **MkDocs' exact licence identifier.** The licence page shows the BSD
  two-clause text but does not print an SPDX identifier; "BSD-2-Clause" is my
  reading, corroborated by the FSF's "FreeBSD (2-clause BSD)" entry
  ([MkDocs licence](https://www.mkdocs.org/about/license/), [FSF](https://www.gnu.org/licenses/license-list.html#FreeBSD)).
- **Material for MkDocs' current patch version and the 12-month support clock.**
  The Insiders post (2025-11-11) says 9.7.0 is final and commits to "at least the
  next 12 months"; whether a newer patch shipped after I read the site is not
  verified. Re-check before depending on it.
- **Zensical's production readiness.** It is MIT and reads `mkdocs.yml`, but its
  own announcement says feature parity is incomplete and i18n/versioning are
  roadmap items ([Zensical](https://squidfunk.github.io/mkdocs-material/blog/2025/11/05/zensical/)).
  Do not assume it is a drop-in for a bilingual, versioned site today.
- **Docusaurus Algolia eligibility.** "Free for any developer documentation or
  technical blog" is subject to an application checklist I did not evaluate
  against Lekto's site ([search](https://docusaurus.io/docs/search)).
- **`actions/*` action major versions drift.** GitHub's Pages docs show
  `configure-pages@v5`, `upload-pages-artifact@v4`, `deploy-pages@v4`
  ([using custom workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)),
  while VitePress's own sample still shows older tags (`configure-pages@v4`,
  `upload-pages-artifact@v3`) ([deploy](https://vitepress.dev/guide/deploy)).
  Pin to the GitHub-docs majors and re-check at implementation time.
- **ADR-0011's reach over non-Gradle tooling.** The ADR text says "every
  dependency must be AGPL-compatible for the way we link it" but does not mention
  docs tooling, and the CI gate is Gradle-only ([`docs/adr/0011-agpl3-with-separate-ccbysa-pack.md`](../adr/0011-agpl3-with-separate-ccbysa-pack.md),
  `.github/workflows/ci.yml`). Whether a docs generator's npm/Cargo/pip tree is
  in scope is a policy question this report flags but does not decide.

---

## References

**Generators (official)**
- MkDocs home — https://www.mkdocs.org/
- MkDocs release notes (1.6.1, 2024-08-30) — https://www.mkdocs.org/about/release-notes/
- MkDocs licence (BSD) — https://www.mkdocs.org/about/license/
- MkDocs deploying your docs (`gh-deploy`) — https://www.mkdocs.org/user-guide/deploying-your-docs/
- MkDocs configuration (search plugin, strict, locale) — https://www.mkdocs.org/user-guide/configuration/
- Material for MkDocs — https://squidfunk.github.io/mkdocs-material/
- Material licence (MIT) — https://squidfunk.github.io/mkdocs-material/license/
- Material setting up versioning (mike) — https://squidfunk.github.io/mkdocs-material/setup/setting-up-versioning/
- Zensical announcement (Material maintenance mode; MkDocs supply-chain risk) — https://squidfunk.github.io/mkdocs-material/blog/2025/11/05/zensical/
- Material Insiders now free (9.7.0 final) — https://squidfunk.github.io/mkdocs-material/blog/2025/11/11/insiders-now-free-for-everyone/
- What MkDocs 2.0 means — https://squidfunk.github.io/mkdocs-material/blog/2026/02/18/mkdocs-2.0/
- Docusaurus — https://docusaurus.io/
- Docusaurus installation (Node ≥20) — https://docusaurus.io/docs/installation
- Docusaurus deployment (GitHub Pages, `.nojekyll`) — https://docusaurus.io/docs/deployment
- Docusaurus versioning — https://docusaurus.io/docs/versioning
- Docusaurus i18n introduction — https://docusaurus.io/docs/i18n/introduction
- Docusaurus search — https://docusaurus.io/docs/search
- Docusaurus LICENSE (MIT) — https://raw.githubusercontent.com/facebook/docusaurus/main/LICENSE
- VitePress — https://vitepress.dev/
- VitePress i18n — https://vitepress.dev/guide/i18n
- VitePress deploy (GitHub Pages workflow; Node 20+) — https://vitepress.dev/guide/deploy
- VitePress search (local MiniSearch, i18n) — https://vitepress.dev/reference/default-theme-search
- mdBook guide — https://rust-lang.github.io/mdBook/
- mdBook repository (MPL-2.0) — https://github.com/rust-lang/mdBook
- mdbook-i18n-helpers (Gettext; not officially supported) — https://github.com/google/mdbook-i18n-helpers
- Just the Docs — https://just-the-docs.com/
- Jekyll — https://jekyllrb.com/
- Docsify repository — https://github.com/docsifyjs/docsify
- mike (MkDocs versioning) — https://github.com/jimporter/mike
- mkdocs-static-i18n (frozen) — https://github.com/ultrabug/mkdocs-static-i18n

**GitHub Pages / Actions (official)**
- Configuring a publishing source — https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site
- Using custom workflows with GitHub Pages (configure/upload/deploy, permissions, environment) — https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages
- Creating a GitHub Pages site (entry file; static site generators; no server-side) — https://docs.github.com/en/pages/getting-started-with-github-pages/creating-a-github-pages-site
- About GitHub Pages and Jekyll (plugin allowlist) — https://docs.github.com/en/pages/setting-up-a-github-pages-site-with-jekyll/about-github-pages-and-jekyll
- GitHub Pages dependency versions (Jekyll 3.10.0) — https://pages.github.com/versions/
- Workflow syntax (`paths`, `tags`, path filters not evaluated for tags) — https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions
- Events that trigger workflows (`create`, `page_build`, `release`) — https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows
- `actions/configure-pages` — https://github.com/actions/configure-pages
- Pages starter workflows (incl. `mdbook.yml`, `jekyll.yml`) — https://github.com/actions/starter-workflows/tree/main/pages

**Licence**
- FSF GNU licence list (GPL-compatible licences; MPL-2.0 §3.3) — https://www.gnu.org/licenses/license-list.html

**Lekto internal**
- `docs/adr/0011-agpl3-with-separate-ccbysa-pack.md` (AGPL-3.0; dependency rule)
- `docs/research/testing-harness.md` (the JUnit/AGPL reading this report reuses)
- `.github/workflows/ci.yml` (the Gradle-only `checkDependencyLicences` lane; the
  existing `ci`/`nightly`/`dictionary-pack` workflows and the absent release workflow)
