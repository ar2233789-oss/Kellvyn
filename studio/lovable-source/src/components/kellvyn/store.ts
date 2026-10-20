export type Attachment = { name: string; type: string; dataUrl: string };
export type Msg = { id: string; role: "user" | "assistant"; text: string; attachment?: Attachment | undefined; error?: boolean };
export type Conversation = { id: string; title: string; updatedAt: number; messages: Msg[] };
export type Effect = "chuva" | "neve" | "folhas" | "raios" | "nenhum";
export type Settings = { color: string; bg: string; effect: Effect[]; text: string; glow: number; web: boolean; rain: boolean; strongProtection: boolean };

export const EFFECTS: [Effect, string][] = [["chuva", "Chuva"], ["neve", "Neve"], ["folhas", "Folhas"], ["raios", "Raios"], ["nenhum", "Nenhum"]];

const isEffect = (value: unknown): value is Effect =>
  typeof value === "string" && EFFECTS.some(([id]) => id === value);

/** Read the old single-effect value or the new list without losing saved preferences. */
export function normalizeEffects(value: unknown): Effect[] {
  if (typeof value === "string") return isEffect(value) ? [value] : ["chuva"];
  if (!Array.isArray(value)) return ["chuva"];

  const valid = value.filter(isEffect);
  if (valid.includes("nenhum")) return ["nenhum"];
  const unique = Array.from(new Set(valid));
  return unique.length > 0 ? unique : ["nenhum"];
}

/** Toggle an existing effect control; no additional UI option is needed. */
export function toggleEffectSelection(current: Effect[] | Effect, selected: Effect): Effect[] {
  if (selected === "nenhum") return ["nenhum"];
  const active = normalizeEffects(current);
  if (active.includes("nenhum")) return [selected];
  const next = active.includes(selected)
    ? active.filter((effect) => effect !== selected)
    : [...active, selected];
  return next.length > 0 ? next : ["nenhum"];
}

/** [name, accent, background, text] */
export const PALETTES: [string, string, string, string][] = [
  ["Esmeralda", "#38E6BC", "#0B1413", "#E3EEEC"], ["Preto e Dourado", "#D4AF37", "#0A0907", "#EFE6D2"],
  ["Preto e Vermelho", "#E5484D", "#0D0707", "#F1E4E4"], ["Azul Noturno", "#4C9AFF", "#070D18", "#E2E9F5"],
  ["Roxo Real", "#A78BFA", "#0E0A16", "#ECE6F7"], ["Rosa Noite", "#F472B6", "#140A11", "#F5E6EE"],
  ["Prata Grafite", "#C9CED6", "#111214", "#ECEEF1"], ["Laranja Brasa", "#F08A4B", "#120A06", "#F4E9E1"],
  ["Lima Escuro", "#9BCB5A", "#0A0F06", "#E8F0DE"], ["Branco e Preto", "#F2F2F2", "#000000", "#FFFFFF"],
];
export type Memory = { id: string; text: string };

export const COLORS: [string, string][] = [
  ["Esmeralda", "#38E6BC"], ["Azul", "#5CB8FF"], ["Violeta", "#C59BFF"], ["Vermelho", "#FF8A8A"],
  ["Dourado", "#FFD166"], ["Rosa", "#FF79C6"], ["Lima", "#B5E853"], ["Prata", "#E6E6E6"],
  ["Laranja", "#FF7A45"], ["Índigo", "#7C83FF"], ["Ciano", "#38D6E8"], ["Branco", "#FFFFFF"],
];

export const DEFAULT_SETTINGS: Settings = { color: "#38E6BC", bg: "#0B1413", effect: ["chuva"], text: "#E3EEEC", glow: 40, web: false, rain: true, strongProtection: true };

export function normalizeHex(input: string): string | null {
  const v = input.trim().replace(/^#/, "");
  return /^[0-9a-fA-F]{6}$/.test(v) ? `#${v.toUpperCase()}` : null;
}

export const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36);

function read<T>(key: string, fallback: T): T {
  try { const v = localStorage.getItem(key); return v ? (JSON.parse(v) as T) : fallback; } catch { return fallback; }
}
function write(key: string, v: unknown) { try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* quota */ } }

export const storage = {
  settings: () => {
    const saved = read<Record<string, unknown>>("kv.settings", {});
    return {
      ...DEFAULT_SETTINGS,
      ...(saved as Partial<Settings>),
      effect: normalizeEffects(saved["effect"] ?? DEFAULT_SETTINGS.effect),
    };
  },
  saveSettings: (s: Settings) => write("kv.settings", s),
  conversations: () => read<Conversation[]>("kv.conversations", []),
  saveConversations: (c: Conversation[]) => write("kv.conversations", c),
  activeId: () => read<string | null>("kv.active", null),
  saveActiveId: (id: string) => write("kv.active", id),
  memories: () => read<Memory[]>("kv.memories", []),
  saveMemories: (m: Memory[]) => write("kv.memories", m),
};
