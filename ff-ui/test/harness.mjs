import puppeteer from "puppeteer"
import {mkdir, writeFile} from "node:fs/promises"
import {fileURLToPath} from "node:url"
import path from "node:path"

export const UI_URL = (process.env.FF_UI_URL || "http://127.0.0.1:7070").replace(/\/+$/, "")
export const ARTIFACTS = fileURLToPath(new URL("../build/test-artifacts/", import.meta.url))
export const VISUALS = fileURLToPath(new URL("../build/test-artifacts/visual/", import.meta.url))

let browser

/** True when the UI is reachable; suites skip (with a notice) otherwise. */
export const uiReachable = async () => {
  try {
    const res = await fetch(UI_URL + "/", {method: "GET"})
    return res.ok
  } catch {
    return false
  }
}

export const apiUrl = (p) => UI_URL + "/api" + p

export const seedStack = async (stack) => {
  const res = await fetch(apiUrl("/v1/stack"), {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify(stack),
  })
  if (!res.ok) throw new Error(`seed failed (${res.status}): ${await res.text()}`)
  return res.json()
}

export const deleteStack = async (id) => {
  await fetch(apiUrl("/v1/stack/" + id), {method: "DELETE"}).catch(() => {
  })
}

export const listStacks = async () => (await fetch(apiUrl("/v1/stack")).then(r => r.json()))

/** Launches Chrome and returns a fresh page wired to capture console/page errors. */
export const openPage = async () => {
  if (!browser) {
    browser = await puppeteer.launch({
      headless: true,
      args: ["--no-sandbox", "--disable-setuid-sandbox"],
    })
  }
  const page = await browser.newPage()
  await page.setViewport({width: 1440, height: 950})
  const errors = []
  page.on("console", (m) => {
    if (m.type() === "error") errors.push(`console: ${m.text()}`)
  })
  page.on("pageerror", (e) => errors.push(`pageerror: ${e.message}`))
  page.errors = errors
  return page
}

export const closeBrowser = async () => {
  if (browser) {
    await browser.close()
    browser = undefined
  }
}

export const goto = async (page, urlPath) => {
  await page.goto(UI_URL + urlPath, {waitUntil: "networkidle0", timeout: 20000})
}

export const text = (page, sel) => page.$eval(sel, (el) => el.textContent.trim())

export const exists = async (page, sel) => (await page.$(sel)) !== null

export const click = async (page, sel) => {
  await page.waitForSelector(sel, {timeout: 5000})
  await page.click(sel)
}

export const clickButtonByText = async (page, label) => {
  const handle = await page.evaluateHandle((t) => {
    const btns = [...document.querySelectorAll("button, a")]
    return btns.find((b) => b.textContent.trim() === t) || null
  }, label)
  const el = handle.asElement()
  if (!el) throw new Error(`no button/link with text "${label}"`)
  await el.click()
}

export const setInput = async (page, sel, value) => {
  await page.waitForSelector(sel, {timeout: 5000})
  await page.$eval(sel, (el) => {
    el.value = ""
  })
  await page.type(sel, value)
}

export const waitForText = async (page, sel, expected, timeout = 6000) => {
  const start = Date.now()
  for (; ;) {
    const t = await page.$eval(sel, (el) => el.textContent || "").catch(() => "")
    if (t.includes(expected)) return
    if (Date.now() - start > timeout) {
      throw new Error(`timed out waiting for "${expected}" in ${sel} (last: "${t}")`)
    }
    await new Promise((r) => setTimeout(r, 100))
  }
}

/** Writes a screenshot and captured console log for a failing test. */
export const dumpArtifacts = async (page, name) => {
  await mkdir(ARTIFACTS, {recursive: true})
  await page.screenshot({path: path.join(ARTIFACTS, `${name}.png`), fullPage: true}).catch(() => {
  })
  await writeFile(path.join(ARTIFACTS, `${name}.log`), (page.errors || []).join("\n") + "\n").catch(() => {
  })
}

/** Writes a screenshot to build/test-artifacts/visual (used by the visual audit). */
export const snap = async (page, name, {fullPage = true} = {}) => {
  await mkdir(VISUALS, {recursive: true})
  const file = path.join(VISUALS, `${name}.png`)
  await page.screenshot({path: file, fullPage})
  return file
}

/**
 * Shared guard for a suite: skips (prints a notice, resolves false) when the UI
 * is not reachable so `node --test` stays green on machines without a server.
 */
export const requireUi = async () => {
  if (await uiReachable()) return true
  console.log(`E2E skipped: ${UI_URL} not reachable (set FF_UI_URL)`)
  return false
}
