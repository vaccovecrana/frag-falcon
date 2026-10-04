import {after, before, test} from "node:test"
import assert from "node:assert/strict"
import {closeBrowser, goto, openPage, requireUi, UI_URL} from "./harness.mjs"

let page

before(async () => {
  if (await requireUi()) page = await openPage()
})

after(async () => closeBrowser())

test("serves index.html at /", {skip: !UI_URL}, async () => {
  if (!page) return
  await goto(page, "/")
  assert.ok(await page.$("#app"))
  assert.match(await page.title(), /frag-falcon/i)
})

test("serves the JS bundle with a JS content type", async () => {
  if (!page) return
  const res = await fetch(UI_URL + "/index.js")
  assert.equal(res.status, 200)
  assert.match(res.headers.get("content-type") || "", /javascript/)
})

test("serves the stylesheet", async () => {
  if (!page) return
  const res = await fetch(UI_URL + "/index.css")
  assert.equal(res.status, 200)
  assert.match(res.headers.get("content-type") || "", /css/)
})

test("falls back to index.html for SPA deep links", async () => {
  if (!page) return
  const res = await fetch(UI_URL + "/stack/deep-link")
  assert.equal(res.status, 200)
  assert.match(res.headers.get("content-type") || "", /html/)
})

test("serves the stack API as JSON", async () => {
  if (!page) return
  const res = await fetch(UI_URL + "/api/v1/stack")
  assert.equal(res.status, 200)
  const body = await res.json()
  assert.ok(Array.isArray(body.stacks))
})
