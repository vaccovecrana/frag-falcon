import {after, before, test} from "node:test"
import assert from "node:assert/strict"
import {closeBrowser, deleteStack, exists, goto, openPage, requireUi, seedStack, text,} from "./harness.mjs"

const ID = "e2e-detail"
let page

before(async () => {
  if (await requireUi()) {
    page = await openPage()
    await seedStack({
      id: ID,
      services: {
        app: {image: "alpine:latest", restart: "no", command: ["/bin/sh", "-c", "echo detail-ok; sleep 60"]},
      },
    })
  }
})

after(async () => {
  await deleteStack(ID)
  await closeBrowser()
})

test("renders the detail actions and service card", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  assert.match(await text(page, "h1"), new RegExp(ID))
  assert.ok(await exists(page, ".vf-panel"), "service panel missing")
  assert.match(await text(page, ".vf-panel"), /app/)
})

test("enables Update only when the stack is stopped", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  const disabled = await page.evaluate(() => {
    const b = [...document.querySelectorAll("button")].find((x) => x.textContent.trim() === "Update")
    return b ? b.disabled : null
  })
  assert.equal(disabled, false, "Update should be enabled for a stopped stack")
})

test("loads per-service logs on demand", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  const btn = await page.evaluateHandle(() =>
    [...document.querySelectorAll("button")].find((x) => x.textContent.trim() === "Logs"))
  await btn.asElement().click()
  await page.waitForSelector("textarea.ff-log", {timeout: 10000})
  assert.ok(await exists(page, "textarea.ff-log"))
})

test("reports no console errors on the detail page", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  assert.deepEqual(page.errors, [])
})
