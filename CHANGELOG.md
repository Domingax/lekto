# Changelog

## 1.0.0 (2026-05-02)


### Features

* **ai:** add dev-pipeline skill for autonomous story lifecycle ([c485096](https://github.com/Domingax/lekto/commit/c48509633ea26b0bb3195b9eea224e1c82bab1e4))
* **dev-pipeline:** add Dev Summary self-assessment before PR creation ([a19a34f](https://github.com/Domingax/lekto/commit/a19a34f0bbd9a90319f5cdf4e2af856e2a66263a)), closes [#7](https://github.com/Domingax/lekto/issues/7)
* **dev-pipeline:** dispatch one sub-agent per story subtask ([9604850](https://github.com/Domingax/lekto/commit/9604850aa1af830c6775cf00dfc925c41c5c2556))
* **dev-pipeline:** systematic documentation step — retro AI-3 ([#26](https://github.com/Domingax/lekto/issues/26)) ([2b91dde](https://github.com/Domingax/lekto/commit/2b91ddef6d66f9ce0f5738f89307f134a651661c))
* story 1.1 — project scaffold and core tooling ([14d18b6](https://github.com/Domingax/lekto/commit/14d18b695aa72729c4a86fb02e123ac36cd8af8c))
* story 1.2 — database infrastructure ([42796ed](https://github.com/Domingax/lekto/commit/42796ed750ac47eb129ffd7d0b1fcd2e12d812e7))
* story 1.4 — platform adapters and error handling foundation ([a37cb98](https://github.com/Domingax/lekto/commit/a37cb986edadd87677e5f7520dadd6f5fb93f4c9))
* story 1.5 — CI/CD and quality pipelines ([#2](https://github.com/Domingax/lekto/issues/2)) ([b0f6146](https://github.com/Domingax/lekto/commit/b0f61461834b19e1e94ecdafa3c1422a072ab724))
* story 2-1-desktop — Desktop Vault Setup & Web Adapter Cleanup ([#29](https://github.com/Domingax/lekto/issues/29)) ([45cbd93](https://github.com/Domingax/lekto/commit/45cbd9329874f7bdf1ec214b3a0f2d601941529c))
* story 2-2 — Open Existing Vault ([#30](https://github.com/Domingax/lekto/issues/30)) ([7e8f7da](https://github.com/Domingax/lekto/commit/7e8f7daf945311ee4f777ceac62eac42ff0bcd12))
* story 2-3 — vault relocation from settings ([#32](https://github.com/Domingax/lekto/issues/32)) ([00af7d7](https://github.com/Domingax/lekto/commit/00af7d73393c8e004fa0825b6b0dfc9d2fcd0401))
* story 3-1 — EPUB import & tokenization ([#33](https://github.com/Domingax/lekto/issues/33)) ([c2005b5](https://github.com/Domingax/lekto/commit/c2005b5ea3fd0ab6ccfd177e210186b2ee17ea65))
* story 3-2 — PDF & plain text import ([#35](https://github.com/Domingax/lekto/issues/35)) ([5d8565f](https://github.com/Domingax/lekto/commit/5d8565fadff4e17c210fae24576ade5c8db07cb5))
* story 3-3 — library view and book management ([#37](https://github.com/Domingax/lekto/issues/37)) ([ea222e1](https://github.com/Domingax/lekto/commit/ea222e171464dda7bdf8ef24edafa72bbda92b35))
* story 8.1 — Tauri initialization & project setup ([#17](https://github.com/Domingax/lekto/issues/17)) ([fefa832](https://github.com/Domingax/lekto/commit/fefa832354f8d9ee9a5d1c44e7747cf151314cbc))
* story 8.2 — Desktop DB layer (Tauri SQL plugin) ([#19](https://github.com/Domingax/lekto/issues/19)) ([98c91d1](https://github.com/Domingax/lekto/commit/98c91d1ccd0469db3855845d2cc56f6b721b526f))
* story 8.3 — Desktop Platform Adapters ([#20](https://github.com/Domingax/lekto/issues/20)) ([5e79df6](https://github.com/Domingax/lekto/commit/5e79df6623ea868588dcdd33228aa9e949abec4a))
* story 8.4 — Stronghold Key Generation via OS Keychain ([#23](https://github.com/Domingax/lekto/issues/23)) ([75b565d](https://github.com/Domingax/lekto/commit/75b565de08178e229ec31d05a63c9e65c6b38bf8))
* story 8.5 — Desktop CI/CD Pipelines ([#25](https://github.com/Domingax/lekto/issues/25)) ([e1ace29](https://github.com/Domingax/lekto/commit/e1ace29ead2fdb3d02949dd8aabae4e8c6139594))
* **story/2-1:** vault setup screen and create new vault ([#13](https://github.com/Domingax/lekto/issues/13)) ([7c6c7cc](https://github.com/Domingax/lekto/commit/7c6c7cc8da284de443f146c74ea8d0dfb19131a6))


### Bug Fixes

* **android:** remove largeHeap — not effective for renderer OOM ([652e3c7](https://github.com/Domingax/lekto/commit/652e3c792e17df98a5dcf05b052845f711b47aae))
* **android:** resolve startup crash and migration re-run ([#14](https://github.com/Domingax/lekto/issues/14)) ([fb28292](https://github.com/Domingax/lekto/commit/fb28292cf91015eb5531d344d1cccb84b29f100c))
* **android:** use FilePicker.pickDirectory() for vault folder selection ([#15](https://github.com/Domingax/lekto/issues/15)) ([aeae4df](https://github.com/Domingax/lekto/commit/aeae4df46939784adb9fce85b2feebe363ac554c))
* **ci:** add issues:write permission for release-please labels ([a15c00a](https://github.com/Domingax/lekto/commit/a15c00a7ca1bbc07457ae2136087eff2d77c9f69))
* **dev-pipeline:** review agent reads Dev Summary as context before reviewing ([71ce7be](https://github.com/Domingax/lekto/commit/71ce7bef124dd89e272bcfdcfcbbcbc6f3d450da))
* **hook:** use relative path in boundary hook command ([d7ec8f0](https://github.com/Domingax/lekto/commit/d7ec8f0b6349fbc3002c7e228ea1cc003771e351))
* **types:** resolve TypeScript compile errors blocking tsc -b ([#9](https://github.com/Domingax/lekto/issues/9)) ([07749a9](https://github.com/Domingax/lekto/commit/07749a9ce9b7294df1ff0f388db34dfc69ad9a6b))
