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
import {closeBrowser, deleteStack, goto, listStacks, openPage, requireUi, seedStack, UI_URL} from "./harness.mjs"
import {mkdir} from "node:fs/promises"
import {fileURLToPath} from "node:url"
import path from "node:path"

const VIS = fileURLToPath(new URL("../build/test-artifacts/visual/", import.meta.url))
const VIEWPORTS = [
  {name: "desktop", width: 1440, height: 950},
  {name: "mobile", width: 390, height: 844},
]

const waitFor = async (predicate, timeout = 30000) => {
  const start = Date.now()
  for (; ;) {
    if (await predicate()) return
    if (Date.now() - start > timeout) throw new Error("visual capture: timed out waiting for condition")
    await new Promise((r) => setTimeout(r, 1000))
  }
}
const ID_A = "visual-alpha"
const ID_B = "visual-beta"
const ID_ERR = "visual-error"

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
        web: {
          image: "docker.io/library/nginx:latest",
          restart: "unless-stopped",
          command: ["/bin/sh", "-c", "nginx -g 'daemon off;'"]
        },
        db: {image: "docker.io/library/postgres:16", restart: "always", environment: ["POSTGRES_PASSWORD=secret"]},
      },
    })
    await seedStack({
      id: ID_B,
      services: {
        app: {
          image: "docker.io/library/alpine:latest",
          restart: "no",
          command: ["/bin/sh", "-c", "sleep 3600"]
        }
      },
    })

    await capture("landing", "/")
    await capture("detail", `/stack/${ID_A}`)
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

    // Error toast: a server-side rejection the client cannot catch (unknown
    // bridge), returns 400 + RvValidation -> error toast.
    await capture("editor-toast-error", "/stack/new", async () => {
      await page.evaluate(() => {
        const ta = document.querySelector(".ff-yaml")
        const set = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, "value").set
        set.call(ta, "id: visual-toast\nbridge: nope0\nservices:\n  app:\n    image: docker.io/library/alpine:latest\n")
        ta.dispatchEvent(new Event("input", {bubbles: true}))
      })
      await new Promise((r) => setTimeout(r, 300))
      await clickText(page, "Save")
      await page.waitForSelector(".vf-toast--error", {timeout: 5000}).catch(() => {
      })
    })

    // Running state: boot a stack on virbr0 and capture pills + populated logs.
    if (process.env.FF_VISUAL_BOOT !== "0") {
      await seedStack({
        id: ID_ERR,
        bridge: "virbr0",
        services: {
          app: {
            image: "docker.io/library/alpine:latest",
            restart: "no",
            command: ["/bin/sh", "-c", "echo visual-boot-ok; sleep 120"]
          },
        },
      })
      await page.goto(`${UI_URL}/stack/${ID_ERR}`, {waitUntil: "networkidle0"})
      await clickText(page, "Start")
      await waitFor(() => listStacks().then(ss => ss.find(s => s.id === ID_ERR)?.state === "running"), 90000)
      await capture("detail-running", `/stack/${ID_ERR}`)
      await capture("detail-logs-output", `/stack/${ID_ERR}`, async () => {
        // Logs poll automatically; give the guest time to emit and the poll to catch up.
        await page.waitForSelector("textarea.ff-log", {timeout: 30000}).catch(() => {
        })
        await new Promise((r) => setTimeout(r, 12000))
      })
    }
  } finally {
    await deleteStack(ID_A)
    await deleteStack(ID_B)
    await deleteStack(ID_ERR)
    await closeBrowser()
  }
  console.log(`\nwrote ${written.length} screenshots to ${VIS}`)
}

main().catch((e) => {
  console.error(e)
  process.exitCode = 1
})
