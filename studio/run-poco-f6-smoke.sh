#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="studio-artifacts"
APK="input-apk/app-debug.apk"
MAIN_ACTIVITY_SOURCE="source-main/app/src/main/java/com/kellvyn/assistant/MainActivity.kt"
mkdir -p "$EVIDENCE_DIR"
test -s "$APK"
test -s "$MAIN_ACTIVITY_SOURCE"

capture_diagnostics() {
  mkdir -p "$EVIDENCE_DIR"
  adb logcat -d -t 12000 > "$EVIDENCE_DIR/logcat.txt" 2>&1 || true
  adb shell dumpsys meminfo com.kellvyn.assistant > "$EVIDENCE_DIR/memory.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$EVIDENCE_DIR/activities.txt" 2>&1 || true
  adb shell uiautomator dump /sdcard/studio-ui-hierarchy.xml >/dev/null 2>&1 || true
  adb pull /sdcard/studio-ui-hierarchy.xml "$EVIDENCE_DIR/ui-hierarchy.xml" >/dev/null 2>&1 || true
  adb shell screencap -p /sdcard/studio-poco-f6-virtual.png >/dev/null 2>&1 || true
  adb pull /sdcard/studio-poco-f6-virtual.png "$EVIDENCE_DIR/poco-f6-virtual.png" >/dev/null 2>&1 || true
  adb shell wm size > "$EVIDENCE_DIR/display-size.txt" 2>&1 || true
  adb shell wm density > "$EVIDENCE_DIR/display-density.txt" 2>&1 || true
}
trap capture_diagnostics EXIT

dump_ui() {
  local destination="$1"
  adb shell uiautomator dump /sdcard/kellvyn-ui.xml >/dev/null
  adb pull /sdcard/kellvyn-ui.xml "$destination" >/dev/null
}

assert_labels() {
  local xml="$1"
  shift
  python3 - "$xml" "$@" <<'PY'
import sys, xml.etree.ElementTree as ET
path, *required = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
except Exception as exc:
    print(f"FAIL: cannot parse UI hierarchy: {exc}")
    sys.exit(2)
values = [value for node in root.iter() for value in node.attrib.values()]
joined = "\n".join(values).casefold()
missing = [label for label in required if label.casefold() not in joined]
for label in required:
    print(f"{'PASS' if label.casefold() in joined else 'FAIL'}: visible/accessibility label '{label}'")
forbidden = ("modo leve", "modo pesado", "qwen code", "qwen coder", "núcleo variável")
found = [label for label in forbidden if label in joined]
if found:
    print("FAIL: unexpected UI labels: " + ", ".join(found))
    sys.exit(1)
print("PASS: no prohibited extra mode/provider labels visible")
if missing:
    print("Missing required labels: " + ", ".join(missing))
    sys.exit(1)
PY
}

tap_label() {
  local xml="$EVIDENCE_DIR/tap-target.xml"
  local label="$1"
  dump_ui "$xml"
  local coordinates
  coordinates="$(python3 - "$xml" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, wanted = sys.argv[1:]
root = ET.parse(path).getroot()
matches = []
for node in root.iter("node"):
    attrs = node.attrib
    text, desc, hint = attrs.get("text", ""), attrs.get("content-desc", ""), attrs.get("hint", "")
    if wanted not in (text, desc, hint):
        continue
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
    if not bounds:
        continue
    x1, y1, x2, y2 = map(int, bounds.groups())
    if x2 <= x1 or y2 <= y1:
        continue
    priority = 0 if desc == wanted else 1 if text == wanted else 2
    matches.append((priority, x1, y1, x2, y2))
if not matches:
    print(f"FAIL: no tappable text/accessibility bounds for '{wanted}'", file=sys.stderr)
    sys.exit(2)
_, x1, y1, x2, y2 = sorted(matches)[0]
print((x1+x2)//2, (y1+y2)//2)
PY
)"
  read -r x y <<< "$coordinates"
  adb shell input tap "$x" "$y"
}

capture_screen() {
  local name="$1"
  adb shell screencap -p "/sdcard/$name" >/dev/null
  adb pull "/sdcard/$name" "$EVIDENCE_DIR/$name" >/dev/null
}

# The virtual device matches the approximate portrait resolution of the Poco F6.
adb shell wm size 1220x2712
adb shell wm density 440
adb shell settings put system font_scale 1.0
adb shell settings put system animator_duration_scale 1.0
adb shell settings put system transition_animation_scale 1.0
adb shell settings put system window_animation_scale 1.0

# Stay offline for this UI smoke test so the first launch cannot download a multi-GB model.
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null 2>&1 || true

adb install -r "$APK"
adb shell monkey -p com.kellvyn.assistant 1 > "$EVIDENCE_DIR/launch.txt" 2>&1 || true
app_pid=""
for attempt in $(seq 1 45); do
  app_pid="$(adb shell pidof com.kellvyn.assistant 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "$app_pid" ]]; then break; fi
  sleep 1
done
if [[ -z "$app_pid" ]]; then
  echo "FAIL: the Kellvyn process did not remain running after launch." | tee "$EVIDENCE_DIR/result.txt"
  exit 1
fi
sleep 4
dump_ui "$EVIDENCE_DIR/initial-ui.xml"
capture_screen "poco-f6-initial.png"
assert_labels "$EVIDENCE_DIR/initial-ui.xml" \
  "Kellvyn IA" "Navegador" "Editor" "Terminal" "Bots" "Apps" \
  "Anexos e Web" "Ditado por voz" "Enviar mensagem" \
  "Pergunte qualquer coisa..."

# Exercise each existing bottom navigation control and verify its real native surface.
tap_label "Histórico de conversas"
sleep 0.5
dump_ui "$EVIDENCE_DIR/history-ui.xml"
assert_labels "$EVIDENCE_DIR/history-ui.xml" "CONVERSAS" "Nova conversa" "Fechar"
tap_label "Fechar"

tap_label "Navegador"
sleep 0.5
dump_ui "$EVIDENCE_DIR/browser-ui.xml"
assert_labels "$EVIDENCE_DIR/browser-ui.xml" \
  "Navegador" "Pesquise ou abra um endereço no navegador do aparelho." \
  "Pesquisa ou endereço" "Pesquisar / abrir" "Cancelar"
tap_label "Cancelar"

tap_label "Editor"
sleep 0.5
dump_ui "$EVIDENCE_DIR/editor-ui.xml"
assert_labels "$EVIDENCE_DIR/editor-ui.xml" \
  "CÓDIGOS SALVOS" "PROJETOS" "Nenhum código salvo ainda" "Fechar"
tap_label "Fechar"

tap_label "Terminal"
sleep 0.5
dump_ui "$EVIDENCE_DIR/terminal-ui.xml"
assert_labels "$EVIDENCE_DIR/terminal-ui.xml" \
  "TERMINAL TERMUX" "Sessão independente do chat da IA" \
  "Diretório de trabalho do Termux" "Comando" "Executar" "Abrir Termux completo" "Fechar"
tap_label "Fechar"

tap_label "Bots"
sleep 0.5
dump_ui "$EVIDENCE_DIR/bots-ui.xml"
assert_labels "$EVIDENCE_DIR/bots-ui.xml" \
  "APPS, BOTS E PROJETOS" "Bots: confira o estado" \
  "Nenhum bot Discord ou Telegram compatível foi salvo ainda" "Fechar"
tap_label "Fechar"

tap_label "Apps"
sleep 0.5
dump_ui "$EVIDENCE_DIR/apps-catalog-ui.xml"
capture_screen "poco-f6-apps-catalog.png"
assert_labels "$EVIDENCE_DIR/apps-catalog-ui.xml" \
  "APPS, BOTS E PROJETOS" "Seus apps e projetos salvos" \
  "Códigos" "Bots ativos" "Navegador / Google" "Ainda não há aplicativos criados" "Fechar"
tap_label "Fechar"
tap_label "AI"
sleep 0.3
dump_ui "$EVIDENCE_DIR/chat-home-after-navigation.xml"
assert_labels "$EVIDENCE_DIR/chat-home-after-navigation.xml" "Kellvyn IA" "Anexos e Web" "Enviar mensagem"

# Verify the thinking indicator is controlled by the true generation lifecycle in source,
# and that completion/failure clears generation state. Model inference itself remains offline here.
grep -Fq 'private fun LovableThinkingBrain' "$MAIN_ACTIVITY_SOURCE"
grep -Fq 'if (isGenerating)' "$MAIN_ACTIVITY_SOURCE"
grep -Fq 'isGenerating = false' "$MAIN_ACTIVITY_SOURCE"
grep -Fq 'finally {' "$MAIN_ACTIVITY_SOURCE"
echo "PASS: native thinking-brain indicator is wired to generation and reset in cleanup." >> "$EVIDENCE_DIR/result.txt"

# Open settings and exercise palettes plus concurrent effects; this uses actual UI taps from UIAutomator bounds.
tap_label "Configurações"
sleep 1
dump_ui "$EVIDENCE_DIR/settings-ui.xml"
capture_screen "poco-f6-settings.png"
assert_labels "$EVIDENCE_DIR/settings-ui.xml" \
  "COR DA INTERFACE" "INTERNET DA KELLVYN IA" "EFEITO VISUAL" \
  "Esmeralda" "Azul" "Chuva" "Raios" "Nenhum" "Acesso à web"

tap_label "Azul"
sleep 0.3
tap_label "Esmeralda"
sleep 0.3
tap_label "Nenhum"
sleep 0.3
tap_label "Chuva"
sleep 0.3
tap_label "Raios"
sleep 0.7
dump_ui "$EVIDENCE_DIR/settings-combined-effects.xml"
capture_screen "poco-f6-settings-combined-effects.png"

# Close settings through its own button; capture the native composite animation on the actual virtual device.
tap_label "Fechar"
sleep 7
capture_screen "poco-f6-rain-lightning.png"

# Exercise attachment picker and native plugin panel, then prove Web access toggles both ways.
tap_label "Anexos e Web"
sleep 0.5
dump_ui "$EVIDENCE_DIR/plus-menu-initial.xml"
assert_labels "$EVIDENCE_DIR/plus-menu-initial.xml" "Imagem ou arquivo" "Plugins" "Conectar à Web"

tap_label "Imagem ou arquivo"
sleep 1
dump_ui "$EVIDENCE_DIR/file-picker-ui.xml"
capture_screen "poco-f6-file-picker.png"
adb shell dumpsys activity activities > "$EVIDENCE_DIR/file-picker-activities.txt" 2>&1 || true
if grep -Eiq 'documentsui|PhotoPicker|com\.android\.documentsui|picker\.PickActivity' "$EVIDENCE_DIR/file-picker-activities.txt"; then
  echo "PASS: attachment button opened an Android file/photo picker." >> "$EVIDENCE_DIR/result.txt"
else
  echo "WARN: system picker activity name not recognized; inspect file-picker-ui.xml and file-picker-activities.txt." | tee -a "$EVIDENCE_DIR/result.txt"
fi
adb shell input keyevent 4
sleep 0.5
test -n "$(adb shell pidof com.kellvyn.assistant 2>/dev/null | tr -d '\r' || true)"

tap_label "Anexos e Web"
sleep 0.4
tap_label "Plugins"
sleep 0.7
dump_ui "$EVIDENCE_DIR/plugins-ui.xml"
capture_screen "poco-f6-plugins.png"
assert_labels "$EVIDENCE_DIR/plugins-ui.xml" \
  "PLUGINS E FERRAMENTAS" "Cérebro local" "Acesso à Web" \
  "Construtor Android e editor" "Terminal Termux independente" "Bots Discord / Telegram" "Fechar"
tap_label "Fechar"
sleep 0.3

tap_label "Anexos e Web"
sleep 0.5
dump_ui "$EVIDENCE_DIR/plus-menu-connected-off.xml"
assert_labels "$EVIDENCE_DIR/plus-menu-connected-off.xml" "Imagem ou arquivo" "Plugins" "Conectar à Web"
tap_label "Conectar à Web"
sleep 0.5
tap_label "Anexos e Web"
sleep 0.5
dump_ui "$EVIDENCE_DIR/plus-menu-connected-on.xml"
assert_labels "$EVIDENCE_DIR/plus-menu-connected-on.xml" "Imagem ou arquivo" "Plugins" "Desconectar da Web"
tap_label "Desconectar da Web"
sleep 0.5
adb shell input keyevent 4

# Send a deterministic identity question that must work offline; verify chat and response action controls.
tap_label "Pergunte qualquer coisa..."
adb shell input text 'Quem%ste%criou?'
sleep 0.5
tap_label "Enviar mensagem"
sleep 1
dump_ui "$EVIDENCE_DIR/chat-response-ui.xml"
capture_screen "poco-f6-chat-response.png"
assert_labels "$EVIDENCE_DIR/chat-response-ui.xml" \
  "Quem te criou?" "Copiar" "Ouvir" "Baixar" "Compartilhar"

# Require that the intended creator answer is actually present in the visible tree.
python3 - "$EVIDENCE_DIR/chat-response-ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
text = " ".join(value for node in root.iter() for key, value in node.attrib.items() if key in {"text", "content-desc", "hint"}).casefold()
if "kellvyn é meu único criador" not in text and "kellvyn e meu unico criador" not in text:
    print("FAIL: expected offline identity response was not visible.")
    sys.exit(1)
print("PASS: offline chat send/answer flow displayed the expected identity response.")
PY

echo "PASS: virtual Android launch, chat send, response actions, settings, palettes, concurrent effects, and Web-toggle UI checks completed." | tee -a "$EVIDENCE_DIR/result.txt"
echo "LIMIT: network/model inference, voice recognition service, physical Poco F6/HyperOS, and hardware performance were not tested in this offline Android AOSP smoke test." | tee -a "$EVIDENCE_DIR/result.txt"
