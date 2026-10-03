import {after, before, test} from "node:test"
import assert from "node:assert/strict"
import {apiUrl, closeBrowser, deleteStack, goto, listStacks, openPage, requireUi,} from "./harness.mjs"

const ID = "e2e-flow"

let page

const clickText = (page, label) => page.evaluate((t) => {
  const el = [...document.querySelectorAll("button, a")].find((b) => b.textContent.trim() === t)
  if (!el) throw new Error(`no button/link "${t}"`)
  el.click()
}, label)

before(async () => {
  if (await requireUi()) {
    page = await openPage()
    await deleteStack(ID)
  }
})

after(async () => {
  await deleteStack(ID)
  await closeBrowser()
})

test("A) creates a stack with one service via the editor", async () => {
  if (!page) return
  await goto(page, "/stack/new")
  // stack id
  await page.evaluate((id) => {
    const el = document.querySelector("input.ff-input")
    const d = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")
    d.set.call(el, id);
    el.dispatchEvent(new Event("input", {bubbles: true}))
  }, ID)
  // service image
  await page.evaluate(() => {
    const el = document.querySelector(".vf-panel input[list='ff-images']")
    const d = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")
    d.set.call(el, "docker.io/library/alpine:latest");
    el.dispatchEvent(new Event("input", {bubbles: true}))
  })
  await clickText(page, "Save")
  await page.waitForFunction(() => location.pathname === "/", {timeout: 8000})

  const stacks = await listStacks()
  const mine = stacks.find(s => s.id === ID)
  assert.ok(mine, `stack ${ID} was not created`)
  assert.ok(Object.keys(mine.services || {}).includes("app"))
})

test("A2) seeds a second image change (update path)", async () => {
  if (!page) return
  // The stack is stopped, so Update should be enabled on its detail page.
  await goto(page, `/stack/${ID}`)
  const updateDisabled = await page.evaluate(() => {
    const b = [...document.querySelectorAll("button")].find(x => x.textContent.trim() === "Update")
    return b ? b.disabled : null
  })
  assert.equal(updateDisabled, false, "Update should be enabled while stopped")
})

test("E) clears provisioning on update (PATCH)", async () => {
  if (!page) return
  const res = await fetch(apiUrl(`/v1/stack/${ID}`), {method: "PATCH"})
  assert.equal(res.status, 200)
  const body = await res.json()
  assert.equal(body.status.id, ID)
  assert.equal(body.status.state, "stopped")
})

test("F) deletes the stack", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  // The Delete button asks for confirmation first. The dialog must be handled
  // inside the listener (accepting it) so the click's synchronous confirm()
  // returns — capturing the prompt for the assertion.
  let prompt
  page.once("dialog", (d) => {
    prompt = {type: d.type(), message: d.message()}
    d.accept().catch(() => {
    })
  })
  await clickText(page, "Delete")
  assert.ok(prompt, "expected a confirmation dialog")
  assert.equal(prompt.type, "confirm")
  assert.match(prompt.message, new RegExp(`Delete stack "${ID}"`))
  await page.waitForFunction(() => location.pathname === "/", {timeout: 8000})
  const stacks = await listStacks()
  assert.ok(!stacks.some(s => s.id === ID), `stack ${ID} still present`)
})
