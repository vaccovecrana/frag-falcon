import {after, before, test} from "node:test"
import assert from "node:assert/strict"
import {closeBrowser, exists, goto, openPage, requireUi, text} from "./harness.mjs"

let page

before(async () => {
  if (await requireUi()) page = await openPage()
})

after(async () => closeBrowser())

test("shows a Stack id field on the new-stack editor", async () => {
  if (!page) return
  await goto(page, "/stack/new")
  assert.ok(await exists(page, "input.ff-input"), "stack id input missing")
  assert.match(await text(page, "body"), /Stack id/)
})

test("blocks saving an invalid stack id and shows a validation error", async () => {
  if (!page) return
  await goto(page, "/stack/new")
  const input = await page.$("input.ff-input")
  await input.click({clickCount: 3})
  await input.type("Bad Id!")
  await page.evaluate(() =>
    [...document.querySelectorAll("button")].find((b) => b.textContent.trim() === "Save").click())
  await page.waitForSelector(".vf-error", {timeout: 5000})
  assert.match(await text(page, ".vf-error"), /id/i)
  assert.ok(page.url().includes("/stack/new"), "should not navigate on invalid id")
})

test("surfaces a backend rejection as an error toast", async () => {
  if (!page) return
  await goto(page, "/stack/new")
  // An unknown bridge passes client validation but is rejected by the API (400 + RvValidation).
  await page.evaluate(() => {
    const ta = document.querySelector(".ff-yaml")
    const set = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, "value").set
    set.call(ta, "id: e2e-toast\nbridge: nope0\nservices:\n  app:\n    image: docker.io/library/alpine:latest\n")
    ta.dispatchEvent(new Event("input", {bubbles: true}))
  })
  await new Promise((r) => setTimeout(r, 300))
  await page.evaluate(() =>
    [...document.querySelectorAll("button")].find((b) => b.textContent.trim() === "Save").click())
  await page.waitForSelector(".vf-toast--error", {timeout: 5000})
  assert.match(await text(page, ".vf-toast"), /[Uu]nknown Linux bridge/)
})

test("rejects a malformed service image from the backend with a mapped message", async () => {
  if (!page) return
  await goto(page, "/stack/new")
  // A syntactically invalid image passes the client form but fails yavi on the server.
  await page.evaluate(() => {
    const ta = document.querySelector(".ff-yaml")
    const set = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, "value").set
    set.call(ta, "id: e2e-bad-image\nservices:\n  app:\n    image: Not An Image\n")
    ta.dispatchEvent(new Event("input", {bubbles: true}))
  })
  await new Promise((r) => setTimeout(r, 300))
  await page.evaluate(() =>
    [...document.querySelectorAll("button")].find((b) => b.textContent.trim() === "Save").click())
  await page.waitForSelector(".vf-toast--error", {timeout: 5000})
  assert.match(await text(page, ".vf-toast"), /not a valid image reference/)
})

test("keeps the YAML editor and the form fields in sync (both directions)", async () => {
  if (!page) return
  await goto(page, "/stack/new")

  // form -> yaml
  await page.evaluate(() => {
    const el = document.querySelector(".vf-panel input[list='ff-images']")
    const set = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set
    set.call(el, "docker.io/library/busybox:latest")
    el.dispatchEvent(new Event("input", {bubbles: true}))
  })
  await new Promise((r) => setTimeout(r, 300))
  const yaml1 = await page.$eval(".ff-yaml", (el) => el.value)
  assert.match(yaml1, /busybox:latest/)

  // yaml -> form
  await page.evaluate(() => {
    const ta = document.querySelector(".ff-yaml")
    const set = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, "value").set
    set.call(ta, "id: synced\nservices:\n  web:\n    image: docker.io/library/nginx:latest\n")
    ta.dispatchEvent(new Event("input", {bubbles: true}))
  })
  await new Promise((r) => setTimeout(r, 400))
  const nameVal = await page.$eval(".ff-service-name", (el) => el.value)
  const imageVal = await page.$eval(".vf-panel input[list='ff-images']", (el) => el.value)
  assert.equal(nameVal, "web")
  assert.match(imageVal, /nginx:latest/)
})

test("reports no console errors on the editor", async () => {
  if (!page) return
  page.errors.length = 0 // clear errors from the intentional 400 in the previous test
  await goto(page, "/stack/new")
  assert.deepEqual(page.errors, [])
})
