// Visual audit capture. Navigates each screen/state at desktop + mobile
// viewports and writes full-page screenshots to build/test-artifacts/visual/.
//
// Opt-in (NOT part of `npm run test:e2e`):
//
//   # against a running hypervisor (see README). For a deterministic empty
//   # landing shot, point --vm-dir at a fresh directory.
//   npm run visual
//
// Artifacts: ff-ui/build/test-artifacts/visual/<state>-<viewport>.png
import {closeBrowser, deleteStack, goto, openPage, requireUi, seedStack, UI_URL} from "./harness.mjs"
import {mkdir} from "node:fs/promises"
import {fileURLToPath} from "node:url"
import path from "node:path"

const VIS = fileURLToPath(new URL("../build/test-artifacts/visual/", import.meta.url))
const VIEWPORTS = [
  {name: "desktop", width: 1440, height: 950},
  {name: "mobile", width: 390, height: 844},
]
const ID_A = "visual-alpha"
const ID_B = "visual-beta"

const freeze = (page) =>
  page.addStyleTag({content: "*{animation:none!important;transition:none!important}"}).catch(() => {
  })

const clickText = (page, label) =>
  page.evaluate((t) => {
    const el = [...document.querySelectorAll("button, a")].find((b) => b.textContent.trim() === t)
    if (el) el.click()
  }, label)

const setInput = (page, sel, value) =>
  page.evaluate(([s, v]) => {
    const el = document.querySelector(s)
    const set = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set
    set.call(el, v)
    el.dispatchEvent(new Event("input", {bubbles: true}))
  }, [sel, value])

let page
const written = []

const capture = async (state, urlPath, setup) => {
  for (const vp of VIEWPORTS) {
    await page.setViewport({width: vp.width, height: vp.height})
    await goto(page, urlPath)
    if (setup) await setup()
    await page.evaluate(() => (document.fonts ? document.fonts.ready : Promise.resolve())).catch(() => {
    })
    await freeze(page)
    await new Promise((r) => setTimeout(r, 150))
    const name = `${state}-${vp.name}`
    const file = path.join(VIS, `${name}.png`)
    await page.screenshot({path: file, fullPage: true})
    written.push(name)
    console.log(`  captured ${name}`)
  }
}

const main = async () => {
  if (!(await requireUi())) {
    console.error("visual capture aborted: UI not reachable at " + UI_URL)
    return
  }
  await mkdir(VIS, {recursive: true})
  page = await openPage()

  console.log("capturing screens:")
  await capture("landing-empty", "/")

  try {
    await seedStack({
      id: ID_A,
      services: {
        web: {image: "docker.io/library/nginx:latest", restart: "unless-stopped", command: ["/bin/sh", "-c", "nginx -g 'daemon off;'"]},
        db: {image: "docker.io/library/postgres:16", restart: "always", environment: ["POSTGRES_PASSWORD=secret"]},
      },
    })
    await seedStack({
      id: ID_B,
      services: {app: {image: "docker.io/library/alpine:latest", restart: "no", command: ["/bin/sh", "-c", "sleep 3600"]}},
    })

    await capture("landing", "/")
    await capture("detail", `/stack/${ID_A}`)
    await capture("detail-logs", `/stack/${ID_A}`, async () => {
      await clickText(page, "Logs")
      await page.waitForSelector("textarea.ff-log", {timeout: 8000}).catch(() => {
      })
    })
    await capture("editor-new", "/stack/new")
    await capture("editor-expanded", "/stack/new", async () => {
      await clickText(page, "+ Add service")
      await clickText(page, "+ Add Volumes")
      await clickText(page, "+ Add Environment")
    })
    await capture("editor-error", "/stack/new", async () => {
      await setInput(page, "input.ff-input", "Bad Id!")
      await clickText(page, "Save")
      await page.waitForSelector(".vf-error", {timeout: 5000}).catch(() => {
      })
    })
    await capture("editor-edit", `/stack/${ID_A}/edit`)
  } finally {
    await deleteStack(ID_A)
    await deleteStack(ID_B)
    await closeBrowser()
  }
  console.log(`\nwrote ${written.length} screenshots to ${VIS}`)
}

main().catch((e) => {
  console.error(e)
  process.exitCode = 1
})
