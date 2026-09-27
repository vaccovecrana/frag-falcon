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
  assert.match(await text(page, ".vf-brand"), /frag-falcon/)
  const strayPills = await page.$$eval(".vf-nav a", (els) =>
    els.map((e) => e.textContent.trim()).filter((t) => t === "Stacks"))
  assert.equal(strayPills.length, 0, "unexpected 'Stacks' nav pill")
})

test("does not render a bare version placeholder", async () => {
  if (!page) return
  await goto(page, "/")
  const nav = (await page.$(".vf-nav")) ? await text(page, ".vf-nav") : ""
  assert.ok(!/^v$/.test(nav.trim()), `version shows a bare "v": "${nav}"`)
})

test("reports no console errors on the landing page", async () => {
  if (!page) return
  await goto(page, "/")
  assert.deepEqual(page.errors, [])
})
