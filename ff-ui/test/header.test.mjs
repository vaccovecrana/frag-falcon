import {after, before, test} from "node:test"
import assert from "node:assert/strict"
import {closeBrowser, goto, openPage, requireUi, text} from "./harness.mjs"

let page

before(async () => {
  if (await requireUi()) page = await openPage()
})

after(async () => closeBrowser())

test("shows the brand without a redundant nav pill", async () => {
  if (!page) return
  await goto(page, "/")
  assert.match(await text(page, ".ff-brand"), /frag-falcon/)
  const strayPills = await page.$$eval(".ff-nav a", (els) =>
    els.map((e) => e.textContent.trim()).filter((t) => t === "Stacks"))
  assert.equal(strayPills.length, 0, "unexpected 'Stacks' nav pill")
})

test("does not render a bare version placeholder", async () => {
  if (!page) return
  await goto(page, "/")
  const nav = (await page.$(".ff-nav")) ? await text(page, ".ff-nav") : ""
  assert.ok(!/^v$/.test(nav.trim()), `version shows a bare "v": "${nav}"`)
})

test("reports no console errors on the landing page", async () => {
  if (!page) return
  await goto(page, "/")
  assert.deepEqual(page.errors, [])
})

test("renders the host name in the browser tab title", async () => {
  if (!page) return
  await goto(page, "/")
  await page.waitForFunction(() => document.title.startsWith("frag-falcon · "), {timeout: 5000})
    .catch(() => {})
  const title = await page.title()
  assert.match(title, /^frag-falcon · \S+/)
})
