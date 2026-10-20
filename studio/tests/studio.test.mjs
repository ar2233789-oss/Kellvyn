import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const read = (name) => readFileSync(path.join(root, name), "utf8");
const html = read("index.html");
const css = read("studio.css");
const engine = read("scene-engine.js");
const lab = read("studio.js");
const docs = read("README.md");
const profile = JSON.parse(read("memory/POCO_F6_PROFILE.json"));
const memory = read("memory/OPERATOR_MEMORY.md");
const deviceLab = read("device-lab/README.md");
const emulatorWorkflow = read("../.github/workflows/studio-poco-f6-lab.yml");

test("interface is divided into exactly three independent studio modules", () => {
  assert.equal((html.match(/data-module="/g) || []).length, 3);
  assert.match(html, /id="module-design"/);
  assert.match(html, /id="module-engineering"/);
  assert.match(html, /id="module-testing"/);
});

test("the static interface has unique IDs", () => {
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((match) => match[1]);
  const duplicates = ids.filter((id, index) => ids.indexOf(id) !== index);
  assert.deepEqual([...new Set(duplicates)], []);
});

test("UI contract references both stylesheet and main ES module", () => {
  assert.match(html, /href="\.\/studio\.css"/);
  assert.match(html, /src="\.\/studio\.js"/);
  assert.match(lab, /import "\.\/scene-engine\.js"/);
});

test("visual engine contains 2D/3D controls, materials, shadows and keyframes", () => {
  assert.match(html, /data-view="2d"/);
  assert.match(html, /data-view="3d"/);
  assert.match(html, /data-view="4d"/);
  assert.match(engine, /PerspectiveCamera/);
  assert.match(engine, /OrthographicCamera/);
  assert.match(engine, /MeshStandardMaterial/);
  assert.match(engine, /PCFSoftShadowMap/);
  assert.match(engine, /snapshotSelected/);
  assert.match(engine, /kellvyn-studio-scene\/v1/);
});

test("visual engine exposes meaningful startup diagnostics", () => {
  assert.match(engine, /globalThis\.StudioScene\s*=/);
  assert.match(engine, /rendererReady:/);
  assert.match(engine, /hasLights:/);
  assert.match(engine, /keyframeStoreAvailable:/);
  assert.match(engine, /await initializeRenderer\(\)/);
});

test("engineering bench includes local import, dependency scan and guarded patch preparation", () => {
  assert.match(lab, /async function importProject/);
  assert.match(lab, /function parseDependencies/);
  assert.match(lab, /function preparePatch/);
  assert.match(lab, /count===0/);
  assert.match(lab, /unique\s*&&\s*count!==1/);
  assert.match(lab, /files\.set\(path,proposed\)/);
});

test("QA report includes explicit static-validation and CVE limitations", () => {
  assert.match(lab, /Vulnerabilidades conhecidas/);
  assert.match(lab, /não equivale a uma auditoria completa/i);
  assert.match(lab, /Não interpretar resultados como build aprovado/);
  assert.match(html, /Matriz de alternativas de validação/);
  assert.match(lab, /kellvyn-qa-report\.json/);
  assert.match(lab, /kellvyn-qa-report\.md/);
});

test("mobile layout breakpoints and the hidden file picker exist", () => {
  assert.match(css, /@media\(max-width:600px\)/);
  assert.match(css, /#project-files\{display:none!important\}/);
});

test("no forbidden Replit reference in the production studio source", () => {
  assert.doesNotMatch(html + css + engine + lab, /replit/i);
});

test("documentation records limitations instead of claiming full native conversion", () => {
  assert.match(docs, /não compila projetos Android/i);
  assert.match(docs, /motor completo de geometria 4D/i);
  assert.match(docs, /interface Lovable em interface Android nativa ainda precisa/i);
});

test("persistent Poco F6 memory defines the target profile and honest emulator limitations", () => {
  assert.equal(profile.profile_id, "poco-f6-hyperos-reference");
  assert.equal(profile.user_reported.android_version, 15);
  assert.equal(profile.user_reported.ram_gb, 8);
  assert.equal(profile.user_reported.storage_gb, 512);
  assert.equal(profile.virtual_emulator.android_api_level, 35);
  assert.deepEqual(profile.virtual_emulator.screen_override_portrait_px, { width: 1220, height: 2712 });
  assert.equal(profile.validation_policy.never_claim_physical_validation_from_emulator, true);
  assert.match(memory, /interface original aprovada do Lovable/i);
  assert.match(deviceLab, /HyperOS/);
});

test("virtual-device workflow uses a prebuilt APK and captures evidence without cloud building", () => {
  assert.match(emulatorWorkflow, /reactivecircus\/android-emulator-runner@v2/);
  assert.match(emulatorWorkflow, /gh release download/);
  assert.match(emulatorWorkflow, /adb install -r input-apk\/app-debug\.apk/);
  assert.match(emulatorWorkflow, /wm size 1220x2712/);
  assert.match(emulatorWorkflow, /screencap/);
  assert.match(emulatorWorkflow, /uiautomator dump/);
  assert.match(emulatorWorkflow, /logcat/);
  assert.match(emulatorWorkflow, /dumpsys meminfo/);
  assert.match(emulatorWorkflow, /sha256sum/);
  assert.doesNotMatch(emulatorWorkflow, /gradlew|assembleDebug|testDebugUnitTest/);
  assert.match(deviceLab, /APK de release já compilado/);
});
