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

test("keeps the YAML editor and the form fields in sync (both directions)", async () => {
  if (!page) return
  await goto(page, "/stack/new")

  // form -> yaml
  const img = await page.$(".ff-service-name")
  assert.ok(img, "service card missing")
  const imageInput = await page.$(".vf-panel input[list='ff-images']")
  await imageInput.click({clickCount: 3})
  await imageInput.type("docker.io/library/busybox:latest")
  await new Promise((r) => setTimeout(r, 300))
  const yaml1 = await page.$eval(".ff-yaml", (el) => el.value)
  assert.match(yaml1, /busybox:latest/)

  // yaml -> form
  const ta = await page.$(".ff-yaml")
  await page.$eval(".ff-yaml", (el) => {
    el.value = ""
  })
  await ta.type("id: synced\nservices:\n  web:\n    image: docker.io/library/nginx:latest\n")
  await new Promise((r) => setTimeout(r, 300))
  const nameVal = await page.$eval(".ff-service-name", (el) => el.value)
  assert.equal(nameVal, "web")
  assert.match(await text(page, "body"), /nginx/)
})

test("reports no console errors on the editor", async () => {
  if (!page) return
  await goto(page, "/stack/new")
  assert.deepEqual(page.errors, [])
})
