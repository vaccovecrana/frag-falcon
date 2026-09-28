import {after, before, test} from "node:test"
import assert from "node:assert/strict"
import {closeBrowser, deleteStack, goto, listStacks, openPage, requireUi, seedStack,} from "./harness.mjs"

const ID = "e2e-boot"
const BRIDGE = process.env.FF_E2E_BRIDGE || "virbr0"
const MARKER = "boot-ok"

let page
let canBoot = false

const clickText = (page, label) => page.evaluate((t) => {
  const el = [...document.querySelectorAll("button, a")].find((b) => b.textContent.trim() === t)
  if (!el) throw new Error(`no button/link "${t}"`)
  el.click()
}, label)

const waitForState = async (id, state, timeout = 90000) => {
  const start = Date.now()
  for (; ;) {
    const stacks = await listStacks()
    const s = stacks.find(x => x.id === id)
    if (s && s.state === state) return s
    if (Date.now() - start > timeout) {
      throw new Error(`timed out waiting for ${id} to be ${state} (last: ${s?.state})`)
    }
    await new Promise(r => setTimeout(r, 1000))
  }
}

before(async () => {
  canBoot = await requireUi()
  if (!canBoot) return
  page = await openPage()
  await deleteStack(ID)
  await seedStack({
    id: ID,
    bridge: BRIDGE,
    services: {
      app: {
        image: "alpine:latest",
        restart: "no",
        command: ["/bin/sh", "-c", `echo ${MARKER}; sleep 60`],
      },
    },
  })
})

after(async () => {
  if (page) await deleteStack(ID)
  await closeBrowser()
})

test("B) boots the stack", async () => {
  if (!canBoot) return
  await goto(page, `/stack/${ID}`)
  await clickText(page, "Start")
  const s = await waitForState(ID, "running")
  assert.equal(s.state, "running")
})

test("C) shows service logs", async () => {
  if (!canBoot) return
  await goto(page, `/stack/${ID}`)
  await page.waitForSelector("textarea.ff-log", {timeout: 20000}).catch(() => {
  })
  const start = Date.now()
  for (; ;) {
    // Logs are fetched on demand (not polled), so re-click to refresh.
    await clickText(page, "Logs")
    await page.waitForSelector("textarea.ff-log", {timeout: 20000})
    const log = await page.$eval("textarea.ff-log", el => el.value)
    if (log.includes(MARKER)) return
    if (Date.now() - start > 60000) throw new Error(`marker "${MARKER}" not in logs:\n${log}`)
    await new Promise(r => setTimeout(r, 2000))
  }
})

test("D) shuts the stack down", async () => {
  if (!canBoot) return
  await goto(page, `/stack/${ID}`)
  await clickText(page, "Stop")
  const s = await waitForState(ID, "stopped")
  assert.equal(s.state, "stopped")
})
