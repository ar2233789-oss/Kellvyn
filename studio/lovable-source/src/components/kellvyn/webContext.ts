/**
 * Conexão do botão "Acesso à Web" ao cérebro.
 *
 * Pertence ao cérebro, não ao layout: só lê a configuração que o botão já salva
 * (kv.settings.web). Com a Web ligada e uma pergunta de consulta, busca trechos
 * na Wikipédia em português e entrega ao modelo como contexto. Se estiver offline,
 * der erro ou não achar nada, a pergunta segue normal, sem contexto.
 */
import { isBuildRequest, withBuilderGuidelines } from "./brainPrompt";

const SETTINGS_KEY = "kv.settings";
const WIKI_API = "https://pt.wikipedia.org/w/api.php";
const TIMEOUT_MS = 6000;
const QUESTION_START =
  /^(quem|o que|oque|quando|onde|qual|quais|como|por ?que|quanto|quantos|quantas|me (fale|explique|diga)|fale sobre|explique)\b/i;

type WikiPage = { title?: string; extract?: string; index?: number };

function webEnabled(): boolean {
  try {
    if (typeof localStorage === "undefined") return false;
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return false;
    const parsed: unknown = JSON.parse(raw);
    return (parsed as { web?: unknown } | null)?.web === true;
  } catch {
    return false;
  }
}

function looksLikeLookup(text: string): boolean {
  const trimmed = text.trim();
  if (trimmed.length === 0 || trimmed.length > 300) return false;
  return trimmed.includes("?") || QUESTION_START.test(trimmed);
}

async function wikipediaContext(query: string): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const params = new URLSearchParams({
      action: "query",
      generator: "search",
      gsrsearch: query,
      gsrlimit: "2",
      prop: "extracts",
      exintro: "1",
      explaintext: "1",
      exchars: "700",
      format: "json",
      origin: "*",
    });
    const response = await fetch(`${WIKI_API}?${params.toString()}`, { signal: controller.signal });
    if (!response.ok) return "";
    const data = (await response.json()) as { query?: { pages?: Record<string, WikiPage> } };
    const pages = Object.values(data.query?.pages ?? {})
      .filter((page) => (page.extract ?? "").trim().length > 0)
      .sort((a, b) => (a.index ?? 99) - (b.index ?? 99))
      .slice(0, 2);
    if (pages.length === 0) return "";
    return pages
      .map((page) => `[${page.title ?? "Wikipédia"}] ${(page.extract ?? "").trim()}`)
      .join("\n\n");
  } catch {
    return "";
  } finally {
    clearTimeout(timer);
  }
}

/** Texto final enviado ao modelo. A tela de chat continua mostrando o texto original. */
export async function prepareUserText(userText: string): Promise<string> {
  if (isBuildRequest(userText)) return withBuilderGuidelines(userText);
  if (!webEnabled() || !looksLikeLookup(userText)) return userText;
  if (typeof navigator !== "undefined" && navigator.onLine === false) return userText;

  const context = await wikipediaContext(userText);
  if (!context) return userText;
  return [
    "Contexto da Wikipédia (trechos de apoio, podem estar incompletos; se usar, diga que veio da Wikipédia):",
    context,
    "",
    "Pergunta:",
    userText,
  ].join("\n");
}
