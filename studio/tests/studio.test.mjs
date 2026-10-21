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
const resources = read("resource-lab.js");
const lab = read("studio.js");
const docs = read("README.md");
const profile = JSON.parse(read("memory/POCO_F6_PROFILE.json"));
const memory = read("memory/OPERATOR_MEMORY.md");
const deviceLab = read("device-lab/README.md");
const emulatorWorkflow = read("../.github/workflows/studio-poco-f6-lab.yml");
const smokeScript = read("run-poco-f6-smoke.sh");

test("interface is divided into four independent studio modules", () => {
  assert.equal((html.match(/data-module="/g) || []).length, 4);
  assert.match(html, /id="module-design"/);
  assert.match(html, /id="module-resources"/);
  assert.match(html, /data-module="resources"/);
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
  assert.match(lab, /import "\.\/resource-lab\.js"/);
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
  assert.match(engine, /function easeProgress/);
  assert.match(engine, /applyLightingPreset/);
  assert.match(engine, /TetrahedronGeometry/);
  assert.match(engine, /kellvyn-studio-scene\/v1/);
});


test("resource library is extensive and exposes real interactive tools", () => {
  const paletteBlock = resources.match(/const palettes = \[([\s\S]*?)\n\];/);
  assert.ok(paletteBlock, "palette library declaration exists");
  assert.ok((paletteBlock[1].match(/\{name:/g) || []).length >= 30, "at least 30 curated palettes");
  assert.ok((resources.match(/\{name:"/g) || []).length >= 60, "palette and material catalogues are populated");
  assert.match(resources, /function renderPaletteLibrary/);
  assert.match(resources, /function renderMaterials/);
  assert.match(resources, /function renderLights/);
  assert.match(resources, /function drawWeather/);
  assert.match(resources, /kellvyn-weather-studio\/v1/);
  assert.match(resources, /weather-apply-preset/);
  assert.match(resources, /function renderEasingOptions/);
  assert.match(html, /id="weather-preview"/);
  assert.match(html, /id="palette-library"/);
  assert.match(html, /id="material-library"/);
  assert.match(html, /id="lighting-library"/);
});

test("additional geometry, material, light and easing APIs are connected to scene engine", () => {
  assert.match(engine, /applySelectedColor, applyPalette, applyMaterialPreset, applyLightingPreset, addGeometry, setEasing/);
  assert.match(engine, /case "bounce-out"/);
  assert.match(engine, /case "elastic-out"/);
  assert.match(engine, /THREE\.VSMShadowMap/);
  assert.match(resources, /StudioScene\?\.applyMaterialPreset/);
  assert.match(resources, /StudioScene\?\.applyLightingPreset/);
  assert.match(resources, /StudioScene\?\.addGeometry/);
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
  assert.doesNotMatch(html + css + engine + resources + lab, /replit/i);
});

test("documentation records limitations instead of claiming full native conversion", () => {
  assert.match(docs, /não compila projetos Android/i);
  assert.match(docs, /paletas/i);
  assert.match(docs, /chuva/i);
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

test("virtual Poco F6 workflow builds the production native app and verifies UI interactions", () => {
  assert.match(emulatorWorkflow, /reactivecircus\/android-emulator-runner@v2/);
  assert.match(emulatorWorkflow, /Checkout current native Android app from main/);
  assert.match(emulatorWorkflow, /working-directory: source-main/);
  assert.match(emulatorWorkflow, /\.\/gradlew --no-daemon --stacktrace testDebugUnitTest assembleDebug/);
  assert.match(emulatorWorkflow, /script: bash studio\/run-poco-f6-smoke\.sh/);
  assert.doesNotMatch(emulatorWorkflow, /studio\/native-port\/android/);
  assert.doesNotMatch(emulatorWorkflow, /gh release download/);
  assert.match(emulatorWorkflow, /gh release create/);
  assert.match(emulatorWorkflow, /android-emulator-runner/);
  assert.match(emulatorWorkflow, /wm size 1220x2712/);
  assert.match(emulatorWorkflow, /sha256sum/);
  assert.match(smokeScript, /adb install -r/);
  assert.match(smokeScript, /uiautomator dump/);
  assert.match(smokeScript, /screencap/);
  assert.match(smokeScript, /Anexos e Web/);
  assert.match(smokeScript, /Conectar à Web/);
  assert.match(smokeScript, /Desconectar da Web/);
  assert.match(smokeScript, /Copiar/);
  assert.match(smokeScript, /Enviar mensagem/);
  assert.match(smokeScript, /LovableThinkingBrain/);
  assert.match(smokeScript, /1220x2712/);
});
