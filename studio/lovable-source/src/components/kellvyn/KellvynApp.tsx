import { ColorStudio } from "./ColorStudio";
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import {
  Menu, LayoutGrid, MoreVertical, Plus, Mic, ArrowUp, Copy, Volume2, Download, Share2, X,
  Paperclip, Globe, Check, MessageSquare, Code2, GitBranch, ExternalLink, Trash2, Square,
  ChevronDown, ChevronRight, Sparkles, TerminalSquare, Brain, Search, ListChecks, Bot, Puzzle,
} from "lucide-react";
import { DigitalRain } from "./DigitalRain";
import { askLocalBrain, textFromAttachment } from "./localBrain";
import {
  DEFAULT_SETTINGS, EFFECTS, PALETTES, normalizeHex, storage, toggleEffectSelection, uid,
  type Attachment, type Conversation, type Memory, type Msg, type Settings,
} from "./store";

type Panel = null | "history" | "apps" | "settings" | "memory" | "phoneapps" | "editor" | "browser" | "tasks" | "bots" | "plugins";
type Status = "Inicializando" | "Pronta" | "Executando" | "Erro";

function KellvynLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" className="icon-glow shrink-0 text-primary" aria-hidden>
      <path d="M16 2 28 9v14l-12 7L4 23V9z" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M12 9v14M12 16l7-7M12 16l7 7" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
      <circle cx="16" cy="16" r="1.4" fill="currentColor" />
    </svg>
  );
}

function IconBtn({ label, onClick, children, active, disabled }: { label: string; onClick?: () => void; children: ReactNode; active?: boolean; disabled?: boolean }) {
  return (
    <button
      type="button" aria-label={label} title={label} onClick={onClick} disabled={disabled}
      className={`grid h-9 w-9 place-items-center rounded-lg text-primary transition hover:bg-accent disabled:opacity-35 ${active ? "bg-accent" : ""}`}
    >
      {children}
    </button>
  );
}

function Sheet({ title, subtitle, onClose, side = "bottom", children }: { title: string; subtitle?: string; onClose: () => void; side?: "left" | "bottom"; children: ReactNode }) {
  const pos = side === "left"
    ? "left-0 top-0 h-full w-[84%] max-w-sm animate-in slide-in-from-left border-r"
    : "inset-x-0 bottom-0 max-h-[88dvh] rounded-t-2xl border-t animate-in slide-in-from-bottom";
  return (
    <div className="fixed inset-0 z-50">
      <button aria-label="Fechar" className="absolute inset-0 bg-background/70 backdrop-blur-sm" onClick={onClose} />
      <section className={`absolute flex flex-col bg-popover pt-safe ${pos}`}>
        <header className="flex items-center justify-between border-b px-4 py-3">
          <div>
            <h2 className="text-sm font-semibold tracking-[0.18em] text-primary text-glow">{title}</h2>
            {subtitle && <p className="text-xs text-muted-foreground">{subtitle}</p>}
          </div>
          <IconBtn label="Fechar" onClick={onClose}><X size={18} /></IconBtn>
        </header>
        <div className="flex-1 overflow-y-auto px-4 py-3">{children}</div>
        <div className="border-t p-3 pb-safe">
          <button onClick={onClose} className="w-full rounded-xl border border-input py-2.5 text-sm text-primary hover:bg-accent">Fechar</button>
        </div>
      </section>
    </div>
  );
}

function Toggle({ on, onChange, label, disabled }: { on: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }) {
  return (
    <button
      role="switch" aria-checked={on} aria-label={label} disabled={disabled} onClick={() => onChange(!on)}
      className={`relative h-6 w-11 shrink-0 rounded-full border border-input transition disabled:opacity-40 ${on ? "bg-primary" : "bg-muted"}`}
    >
      <span className={`absolute top-0.5 h-4.5 w-4.5 rounded-full transition-all ${on ? "left-5.5 bg-primary-foreground" : "left-0.5 bg-muted-foreground"}`} />
    </button>
  );
}

function Row({ title, desc, children }: { title: string; desc?: string; children?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 py-2.5">
      <div className="min-w-0">
        <p className="text-sm text-foreground">{title}</p>
        {desc && <p className="mt-0.5 text-xs leading-snug text-muted-foreground">{desc}</p>}
      </div>
      {children}
    </div>
  );
}

function Category({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mb-3 rounded-xl border bg-card/60 px-3 py-1">
      <h3 className="pt-2.5 text-[11px] font-semibold tracking-[0.16em] text-primary">{title}</h3>
      {children}
    </div>
  );
}

const NATIVE_NOTE = "Requer o app Android nativo — indisponível nesta versão web.";

export function KellvynApp() {
  const [hydrated, setHydrated] = useState(false);
  const [settings, setSettings] = useState<Settings>(() => ({ ...DEFAULT_SETTINGS }));
  const [convs, setConvs] = useState<Conversation[]>([]);
  const [activeId, setActiveId] = useState<string>("");
  const [memories, setMemories] = useState<Memory[]>([]);
  const [panel, setPanel] = useState<Panel>(null);
  const [plusOpen, setPlusOpen] = useState(false);
  const [input, setInput] = useState("");
  const [attachment, setAttachment] = useState<Attachment | null>(null);
  const [status, setStatus] = useState<Status>("Inicializando");
  const [listening, setListening] = useState(false);
  const [speakingId, setSpeakingId] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [hexDraft, setHexDraft] = useState("");
  const [studio, setStudio] = useState<null | "color" | "bg" | "text">(null);
  const [memDraft, setMemDraft] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const taRef = useRef<HTMLTextAreaElement>(null);
  const recRef = useRef<{ stop: () => void } | null>(null);

  // Load persisted state once on the client (idempotent bootstrap).
  useEffect(() => {
    const s = storage.settings();
    let c = storage.conversations();
    let a = storage.activeId();
    if (c.length === 0) {
      const first: Conversation = { id: uid(), title: "Nova conversa", updatedAt: Date.now(), messages: [] };
      c = [first]; a = first.id; storage.saveConversations(c); storage.saveActiveId(first.id);
    }
    if (!a || !c.some((x) => x.id === a)) a = c[0]?.id ?? null;
    if (!a) return;
    setSettings(s); setConvs(c); setActiveId(a); setMemories(storage.memories());
    setHexDraft(s.color); setHydrated(true); setStatus("Pronta");
  }, []);

  useEffect(() => {
    document.documentElement.style.setProperty("--brand", settings.color);
    document.documentElement.style.setProperty("--bg", settings.bg);
    document.documentElement.style.setProperty("--fg", settings.text);
    document.documentElement.style.setProperty("--glow-k", `${settings.glow}%`);
  }, [settings.color, settings.bg, settings.text, settings.glow]);

  const flash = (t: string) => { setToast(t); window.setTimeout(() => setToast(null), 2600); };
  const updateSettings = (patch: Partial<Settings>) => setSettings((p) => { const n = { ...p, ...patch }; storage.saveSettings(n); return n; });
  const updateConvs = useCallback((fn: (c: Conversation[]) => Conversation[]) => setConvs((p) => { const n = fn(p); storage.saveConversations(n); return n; }), []);

  const active = convs.find((c) => c.id === activeId);
  const messages = active?.messages ?? [];

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [messages.length]);

  const newConversation = () => {
    const c: Conversation = { id: uid(), title: "Nova conversa", updatedAt: Date.now(), messages: [] };
    updateConvs((p) => [c, ...p]); setActiveId(c.id); storage.saveActiveId(c.id); setPanel(null);
    taRef.current?.focus();
  };
  const deleteConversation = (id: string) => {
    const rest = convs.filter((c) => c.id !== id);
    if (rest.length === 0) {
      const c: Conversation = { id: uid(), title: "Nova conversa", updatedAt: Date.now(), messages: [] };
      updateConvs(() => [c]); setActiveId(c.id); storage.saveActiveId(c.id);
    } else {
      updateConvs(() => rest);
      if (id === activeId) { setActiveId(rest[0]!.id); storage.saveActiveId(rest[0]!.id); }
    }
    flash("Conversa apagada.");
  };
  const openConversation = (id: string) => { setActiveId(id); storage.saveActiveId(id); setPanel(null); };

  const send = async () => {
    const text = input.trim();
    if ((!text && !attachment) || !active || status === "Executando") return;

    const cid = active.id;
    const submittedAttachment = attachment ?? undefined;
    const userMsg: Msg = { id: uid(), role: "user", text, attachment: submittedAttachment };
    const replyId = uid();
    const reply: Msg = { id: replyId, role: "assistant", text: "" };
    const history = messages
      .filter((m) => !m.error && m.text.trim())
      .slice(-12)
      .map((m) => ({ role: m.role, text: m.text, error: m.error }));

    let prompt = text;
    try {
      if (submittedAttachment) {
        const fileText = textFromAttachment(submittedAttachment);
        prompt = [
          text || "Analise o conteúdo textual do arquivo anexado.",
          "",
          `Arquivo anexado: ${submittedAttachment.name}`,
          "Conteúdo do arquivo:",
          fileText,
        ].join("\n");
      }
    } catch (error) {
      const explanation = error instanceof Error ? error.message : "Não foi possível ler o anexo.";
      updateConvs((p) => p.map((c) => c.id !== cid ? c : {
        ...c,
        updatedAt: Date.now(),
        title: c.messages.length === 0 && text ? text.slice(0, 40) : c.title,
        messages: [...c.messages, userMsg, { ...reply, error: true, text: explanation }],
      }).sort((a, b) => b.updatedAt - a.updatedAt));
      setInput(""); setAttachment(null); setStatus("Erro");
      taRef.current?.focus();
      return;
    }

    updateConvs((p) => p.map((c) => c.id !== cid ? c : {
      ...c,
      updatedAt: Date.now(),
      title: c.messages.length === 0 && text ? text.slice(0, 40) : c.title,
      messages: [...c.messages, userMsg, reply],
    }).sort((a, b) => b.updatedAt - a.updatedAt));
    setInput(""); setAttachment(null); setStatus("Executando");

    try {
      const answer = await askLocalBrain(cid, prompt, history, (partial) => {
        updateConvs((p) => p.map((c) => c.id !== cid ? c : {
          ...c,
          messages: c.messages.map((m) => m.id === replyId ? { ...m, text: partial, error: false } : m),
        }));
      });
      updateConvs((p) => p.map((c) => c.id !== cid ? c : {
        ...c,
        messages: c.messages.map((m) => m.id === replyId ? { ...m, text: answer, error: false } : m),
      }));
      setStatus("Pronta");
    } catch (error) {
      const explanation = error instanceof Error ? error.message : "Falha inesperada ao iniciar a IA local.";
      updateConvs((p) => p.map((c) => c.id !== cid ? c : {
        ...c,
        messages: c.messages.map((m) => m.id === replyId ? { ...m, text: explanation, error: true } : m),
      }));
      setStatus("Erro");
    } finally {
      taRef.current?.focus();
    }
  };

  const toggleMic = () => {
    if (listening) { recRef.current?.stop(); return; }
    const W = window as unknown as { SpeechRecognition?: new () => any; webkitSpeechRecognition?: new () => any };
    const SR = W.SpeechRecognition ?? W.webkitSpeechRecognition;
    if (!SR) { flash("Ditado por voz não disponível neste navegador."); return; }
    const rec = new SR();
    rec.lang = "pt-BR"; rec.interimResults = false; rec.continuous = false;
    rec.onresult = (e: any) => {
      const t = Array.from(e.results as ArrayLike<any>).map((r: any) => r[0].transcript).join(" ");
      setInput((p) => (p ? p + " " : "") + t);
    };
    rec.onerror = (e: any) => flash(`Microfone: ${e.error === "not-allowed" ? "permissão negada" : e.error}`);
    rec.onend = () => setListening(false);
    recRef.current = rec; rec.start(); setListening(true);
  };

  const onFile = (f: File | undefined) => {
    if (!f) return;
    if (f.size > 8 * 1024 * 1024) { flash("Arquivo maior que 8 MB."); return; }
    const r = new FileReader();
    r.onload = () => setAttachment({ name: f.name, type: f.type, dataUrl: String(r.result) });
    r.readAsDataURL(f);
    setPlusOpen(false);
  };

  const copy = async (t: string) => {
    try { await navigator.clipboard.writeText(t); flash("Resposta copiada."); } catch { flash("Não foi possível copiar."); }
  };
  const speak = (m: Msg) => {
    if (!("speechSynthesis" in window)) { flash("Leitura em voz alta indisponível."); return; }
    if (speakingId === m.id) { speechSynthesis.cancel(); setSpeakingId(null); return; }
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(m.text); u.lang = "pt-BR";
    u.onend = () => setSpeakingId(null); u.onerror = () => setSpeakingId(null);
    setSpeakingId(m.id); speechSynthesis.speak(u);
  };
  const download = (m: Msg) => {
    const url = URL.createObjectURL(new Blob([m.text], { type: "text/plain;charset=utf-8" }));
    const a = document.createElement("a"); a.href = url; a.download = `kellvyn-resposta-${m.id}.txt`; a.click();
    URL.revokeObjectURL(url);
  };
  const share = async (m: Msg) => {
    if (!navigator.share) { flash("Compartilhamento não suportado neste navegador."); return; }
    try { await navigator.share({ text: m.text, title: "Kellvyn IA" }); } catch { /* cancelled */ }
  };

  const toggleWeb = (v: boolean) => {
    updateSettings({ web: v });
    flash(v ? "Acesso à Web ativado (será usado quando o modelo estiver conectado)." : "Acesso à Web desativado. A IA permanece local.");
  };

  const applyHex = () => {
    const h = normalizeHex(hexDraft);
    if (!h) { flash("HEX inválido. Use 6 dígitos, ex.: 38E6BC."); return; }
    updateSettings({ color: h }); setHexDraft(h); flash("Cor aplicada.");
  };

  const userCount = (c: Conversation) => c.messages.filter((m) => m.role === "user").length;

  return (
    <div className="relative flex h-dvh flex-col overflow-hidden">
      <DigitalRain effect={hydrated ? settings.effect : "nenhum"} />

      {/* Header */}
      <header className="relative z-10 flex items-center gap-3 border-b bg-panel px-3 py-2.5 pt-safe backdrop-blur-md">
        <IconBtn label="Histórico de conversas" onClick={() => setPanel("history")}><Menu size={22} strokeWidth={1.6} /></IconBtn>
        <h1 className="min-w-0 flex-1 truncate text-[18px] text-primary">Kellvyn Assistente</h1>
        <IconBtn label="Configurações" onClick={() => setPanel("settings")}><MoreVertical size={20} /></IconBtn>
      </header>

      {/* Conversation */}
      <main ref={scrollRef} className="relative z-10 flex-1 overflow-y-auto px-3 py-4">
        {messages.length === 0 && (
          <div className="mt-24 flex flex-col items-center gap-3 text-center">
            <KellvynLogo size={56} />
            <p className="max-w-xs text-sm text-muted-foreground">Converse com a Kellvyn IA ou peça para criar um aplicativo.</p>
          </div>
        )}
        <div className="mx-auto flex max-w-2xl flex-col gap-4">
          {messages.map((m) => m.role === "user" ? (
            <div key={m.id} className="ml-auto max-w-[82%] rounded-2xl rounded-br-md border border-input bg-bubble-user px-3.5 py-2.5 text-[15px] leading-relaxed text-foreground">
              {m.attachment && (m.attachment.type.startsWith("image/")
                ? <img src={m.attachment.dataUrl} alt={m.attachment.name} className="mb-2 max-h-60 w-full rounded-lg object-contain" />
                : <p className="mb-1 flex items-center gap-1.5 text-xs text-primary"><Paperclip size={13} />{m.attachment.name}</p>)}
              {m.text && <p className="whitespace-pre-wrap break-words">{m.text}</p>}
            </div>
          ) : (
            <div key={m.id} className="flex">
              <div className="min-w-0 flex-1">
                <p className="mb-1.5 flex items-center gap-1.5 text-[13px] text-muted-foreground"><Globe size={13} className="text-primary" /> Thinking <ChevronRight size={13} /></p>
                <p className={`whitespace-pre-wrap break-words text-[15px] leading-relaxed ${m.error ? "text-muted-foreground" : "text-foreground"}`}>{m.text}</p>
                <div className="mt-1 flex gap-0.5 text-muted-foreground">
                  <IconBtn label="Copiar" onClick={() => copy(m.text)}><Copy size={15} /></IconBtn>
                  <IconBtn label={speakingId === m.id ? "Parar leitura" : "Ouvir"} onClick={() => speak(m)} active={speakingId === m.id}>
                    {speakingId === m.id ? <Square size={14} /> : <Volume2 size={16} />}
                  </IconBtn>
                  <IconBtn label="Baixar" onClick={() => download(m)}><Download size={16} /></IconBtn>
                  <IconBtn label="Compartilhar" onClick={() => share(m)}><Share2 size={15} /></IconBtn>
                </div>
              </div>
            </div>
          ))}
          {status === "Executando" && <ThinkingBrain />}
        </div>
      </main>

      {/* Composer */}
      <footer className="relative z-20 px-3 pb-safe pt-1">
        {plusOpen && (
          <div className="absolute bottom-full left-3 mb-2 w-64 rounded-xl border bg-popover p-1.5 glow-soft animate-in fade-in zoom-in-95">
            <button onClick={() => fileRef.current?.click()} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm hover:bg-accent">
              <Paperclip size={17} className="text-primary" /> Imagem ou arquivo
            </button>
            <button onClick={() => { setPlusOpen(false); setPanel("plugins"); }} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm hover:bg-accent">
              <Puzzle size={17} className="text-primary" /> Plugins
            </button>
            <div className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm">
              <Globe size={17} className="text-primary" /><span className="flex-1">Acesso à Web</span>
              <button role="checkbox" aria-checked={settings.web} aria-label="Acesso à Web" onClick={() => toggleWeb(!settings.web)}
                className="flex items-center gap-1.5 rounded-md px-1 py-0.5 text-xs hover:bg-accent">
                <span className={`grid h-5 w-5 place-items-center rounded border border-input ${settings.web ? "bg-primary text-primary-foreground" : "bg-muted"}`}>
                  {settings.web && <Check size={14} strokeWidth={3} />}
                </span>
                <span className={settings.web ? "text-primary" : "text-muted-foreground"}>{settings.web ? "Sim" : "Não"}</span>
              </button>
            </div>
          </div>
        )}
        <input ref={fileRef} type="file" accept="image/*,.txt,.md,.json,.pdf,.zip,.kt,.java,.ts,.tsx,.js,.xml" className="hidden"
          onChange={(e) => { onFile(e.target.files?.[0]); e.target.value = ""; }} />
        <div className="mx-auto max-w-2xl rounded-2xl border border-input bg-panel p-2 backdrop-blur-md glow-soft">
          {attachment && (
            <div className="mb-2 flex items-center gap-2 rounded-lg border bg-muted p-1.5">
              {attachment.type.startsWith("image/")
                ? <img src={attachment.dataUrl} alt="" className="h-12 w-12 rounded-md object-cover" />
                : <Paperclip size={18} className="mx-2 text-primary" />}
              <span className="flex-1 truncate text-xs">{attachment.name}</span>
              <IconBtn label="Remover anexo" onClick={() => setAttachment(null)}><X size={16} /></IconBtn>
            </div>
          )}
          <textarea
            ref={taRef} value={input} onChange={(e) => setInput(e.target.value)} rows={1}
            placeholder="Pergunte qualquer coisa..."
            className="max-h-36 min-h-[40px] w-full resize-none bg-transparent px-2 py-1.5 text-[15px] text-foreground outline-none placeholder:text-muted-foreground field-sizing-content"
          />
          <div className="flex items-center gap-1">
            <IconBtn label="Anexos e Web" onClick={() => setPlusOpen((v) => !v)} active={plusOpen}><Plus size={19} /></IconBtn>
            <span className="flex items-center gap-1 rounded-lg border border-input px-2.5 py-1 text-[12px] text-foreground/80">
              <span className={`h-1.5 w-1.5 rounded-full ${status === "Erro" ? "bg-destructive" : "bg-primary glow"}`} />
              Kellvyn <ChevronDown size={13} />
            </span>
            <div className="flex-1" />
            <IconBtn label={listening ? "Parar ditado" : "Ditado por voz"} onClick={toggleMic} active={listening}>
              <Mic size={18} className={listening ? "animate-pulse" : ""} />
            </IconBtn>
            <button
              aria-label="Enviar" onClick={send} disabled={(!input.trim() && !attachment) || status === "Executando"}
              className="grid h-8 w-8 place-items-center rounded-full border border-input text-primary transition hover:bg-accent disabled:opacity-50"
            >
              <ArrowUp size={17} />
            </button>
          </div>
        </div>
      </footer>

      {/* Bottom tab bar — as in the reference image */}
      <nav className="relative z-20 border-t bg-panel pb-safe backdrop-blur-md">
        <div className="mx-auto grid max-w-2xl grid-cols-6">
          {([
            ["AI", Sparkles, () => setPanel(null)],
            ["Navegador", Globe, () => setPanel("browser")],
            ["Editor", Code2, () => setPanel("editor")],
            ["Terminal", TerminalSquare, () => flash("Terminal: requer o app Android nativo.")],
            ["Bots", Bot, () => setPanel("bots")],
            ["Apps", LayoutGrid, () => setPanel("phoneapps")],
          ] as const).map(([label, Icon, fn], i) => (
            <button key={label} onClick={fn} className={`flex flex-col items-center gap-1 py-2 text-[11px] ${i === 0 ? "text-primary" : "text-muted-foreground"}`}>
              <Icon size={22} className={i === 0 ? "icon-glow" : "text-primary/80"} strokeWidth={1.6} />
              {label}
            </button>
          ))}
        </div>
      </nav>

      {toast && (
        <div role="status" className="fixed inset-x-4 bottom-44 z-[60] mx-auto max-w-sm rounded-xl border bg-popover px-4 py-2.5 text-center text-sm glow-soft animate-in fade-in">
          {toast}
        </div>
      )}

      {/* History */}
      {panel === "history" && (
        <Sheet title="CONVERSAS" side="left" onClose={() => setPanel(null)}>
          <button onClick={newConversation} className="mb-3 flex w-full items-center gap-2 rounded-xl border border-input px-3 py-2.5 text-sm text-primary hover:bg-accent">
            <Plus size={17} /> Nova conversa
          </button>
          <ul className="flex flex-col gap-1">
            {[...convs].sort((a, b) => b.updatedAt - a.updatedAt).map((c) => (
              <SwipeRow key={c.id} onDelete={() => deleteConversation(c.id)}>
                <button onClick={() => openConversation(c.id)}
                  className={`flex w-full items-center gap-2.5 rounded-lg px-3 py-2.5 text-left text-sm hover:bg-accent ${c.id === activeId ? "border border-input bg-accent" : ""}`}>
                  <MessageSquare size={16} className="shrink-0 text-primary" />
                  <span className="flex-1 truncate">{c.title}</span>
                  <span className="text-xs text-muted-foreground">{userCount(c)}</span>
                </button>
              </SwipeRow>
            ))}
          </ul>
        </Sheet>
      )}

      {/* Apps / APKs */}
      {panel === "apps" && (
        <Sheet title="APPS, BOTS E PROJETOS" subtitle="Seus apps e projetos salvos" onClose={() => setPanel(null)}>
          <div className="grid grid-cols-3 gap-2">
            <button disabled title={NATIVE_NOTE} className="flex flex-col items-center gap-1.5 rounded-xl border p-3 text-xs opacity-40"><Code2 size={20} className="text-primary" />Códigos</button>
            <button disabled title={NATIVE_NOTE} className="flex flex-col items-center gap-1.5 rounded-xl border p-3 text-xs opacity-40"><GitBranch size={20} className="text-primary" />Git</button>
            <button onClick={() => window.open("https://www.google.com", "_blank", "noopener")} className="flex flex-col items-center gap-1.5 rounded-xl border border-input p-3 text-xs hover:bg-accent">
              <ExternalLink size={20} className="text-primary" />Navegador
            </button>
          </div>
          <p className="mt-2 text-[11px] text-muted-foreground">Códigos e Git: {NATIVE_NOTE}</p>
          <div className="mt-5 rounded-xl border border-dashed p-5 text-center text-sm text-muted-foreground">
            Nenhum projeto salvo ainda. Peça no chat principal para a Kellvyn IA criar um app ou bot.
          </div>
        </Sheet>
      )}

      {/* Settings */}
      {panel === "settings" && (
        <Sheet title="CONFIGURAÇÕES DO KELLVYN" onClose={() => setPanel(null)}>
          <Category title="CORES">
            <div className="grid grid-cols-3 gap-2 py-3">
              {([["color", "Destaque"], ["bg", "Fundo"], ["text", "Texto"]] as const).map(([k, label]) => (
                <button key={k} onClick={() => setStudio(k)} className="flex flex-col items-center gap-1.5 rounded-xl border border-input bg-muted/40 p-2.5">
                  <span className="h-10 w-10 rounded-full border border-input shadow-inner" style={{ background: settings[k] }} />
                  <span className="text-[11px]">{label}</span>
                  <span className="font-mono text-[10px] text-muted-foreground">{settings[k]}</span>
                </button>
              ))}
            </div>
            <label className="block pb-3">
              <div className="mb-1 flex justify-between text-[11px] text-muted-foreground"><span>Intensidade do brilho</span><span className="font-mono">{settings.glow}%</span></div>
              <input type="range" min={0} max={60} value={settings.glow} onChange={(e) => updateSettings({ glow: Number(e.target.value) })}
                className="kv-range h-2 w-full cursor-pointer appearance-none rounded-full bg-muted" />
            </label>
            <p className="text-[11px] text-muted-foreground">Temas prontos</p>
            <div className="grid grid-cols-2 gap-2 py-2.5">
              {PALETTES.map(([name, accent, bg, text]) => {
                const on = settings.color.toUpperCase() === accent && settings.bg.toUpperCase() === bg;
                return (
                  <button key={name} onClick={() => updateSettings({ color: accent, bg, text })}
                    className={`flex items-center gap-2 rounded-lg border p-2 text-left text-xs ${on ? "border-primary" : "border-input"}`}>
                    <span className="flex shrink-0 -space-x-1.5">
                      {[bg, accent, text].map((c, i) => <span key={i} className="h-5 w-5 rounded-full border border-input" style={{ background: c }} />)}
                    </span>
                    <span className="truncate">{name}</span>
                  </button>
                );
              })}
            </div>
          </Category>
          {studio && (
            <ColorStudio key={studio} title={studio === "color" ? "Cor de destaque" : studio === "bg" ? "Cor de fundo" : "Cor do texto"}
              value={settings[studio]} onChange={(h) => updateSettings({ [studio]: h })} onClose={() => setStudio(null)} />
          )}

          <Category title="EFEITO NA TELA">
            <div className="flex flex-wrap gap-2 py-2.5">
              {EFFECTS.map(([id, label]) => {
                const selected = settings.effect.includes(id);
                return (
                  <button key={id} onClick={() => updateSettings({ effect: toggleEffectSelection(settings.effect, id) })}
                    className={`rounded-full border px-3 py-1.5 text-xs ${selected ? "border-primary bg-primary text-primary-foreground" : "border-input text-foreground"}`}>
                    {label}
                  </button>
                );
              })}
            </div>
          </Category>

          <Category title="ATALHOS">
            <Row title="Memória local" desc={`${memories.length} informação(ões) salva(s).`}>
              <button onClick={() => setPanel("memory")} className="rounded-lg border border-input px-3 py-1.5 text-xs text-primary hover:bg-accent">Abrir</button>
            </Row>
            <Row title="Assistente virtual" >
              <Toggle on={false} onChange={() => {}} label="Assistente virtual" disabled />
            </Row>
            <Row title="Leitura de tela" />
          </Category>

          <Category title="MODO DE JOGO">
            <Row title="Modo de jogo" >
              <Toggle on={false} onChange={() => {}} label="Modo de jogo" disabled />
            </Row>
          </Category>

          <Category title="SEGURANÇA">
            <Row title="Proteção reforçada da IA">
              <Toggle on={settings.strongProtection} onChange={(v) => updateSettings({ strongProtection: v })} label="Proteção reforçada" />
            </Row>
          </Category>
        </Sheet>
      )}

      {panel === "editor" && <CodeEditor onClose={() => setPanel(null)} />}
      {panel === "browser" && <BrowserPanel onClose={() => setPanel(null)} />}
      {panel === "tasks" && <TasksPanel onClose={() => setPanel(null)} />}
      {panel === "bots" && <BotsPanel onClose={() => setPanel(null)} />}
      {panel === "plugins" && <PluginsPanel onClose={() => setPanel(null)} />}

      {/* Phone apps — opens the Android launcher via intent (works in Chrome on Android) */}
      {panel === "phoneapps" && (
        <Sheet title="APPS DO CELULAR" onClose={() => setPanel(null)}>
          <a href="intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.HOME;end"
            className="flex items-center gap-3 rounded-xl border border-input px-4 py-4 text-sm text-primary hover:bg-accent">
            <LayoutGrid size={22} /> Abrir apps do celular
          </a>
        </Sheet>
      )}

      {/* Memory */}
      {panel === "memory" && (
        <Sheet title="MEMÓRIA LOCAL" subtitle="Informações salvas explicitamente por você" onClose={() => setPanel("settings")}>
          <div className="mb-3 flex gap-2">
            <input value={memDraft} onChange={(e) => setMemDraft(e.target.value)} placeholder="Nova informação…"
              className="min-w-0 flex-1 rounded-lg border border-input bg-muted px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-ring" />
            <button onClick={() => {
              const t = memDraft.trim(); if (!t) return;
              const n = [...memories, { id: uid(), text: t }]; setMemories(n); storage.saveMemories(n); setMemDraft("");
            }} className="rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground">Salvar</button>
          </div>
          {memories.length === 0 && <p className="text-sm text-muted-foreground">Nenhuma memória salva.</p>}
          <ul className="flex flex-col gap-2">
            {memories.map((m) => (
              <li key={m.id} className="flex items-center gap-2 rounded-lg border p-2">
                <input defaultValue={m.text} aria-label="Editar memória"
                  onBlur={(e) => { const n = memories.map((x) => x.id === m.id ? { ...x, text: e.target.value } : x); setMemories(n); storage.saveMemories(n); }}
                  className="min-w-0 flex-1 bg-transparent text-sm outline-none" />
                <IconBtn label="Excluir memória" onClick={() => {
                  if (!confirm("Excluir esta memória?")) return;
                  const n = memories.filter((x) => x.id !== m.id); setMemories(n); storage.saveMemories(n);
                }}><Trash2 size={15} /></IconBtn>
              </li>
            ))}
          </ul>
        </Sheet>
      )}
    </div>
  );
}

function ThinkingBrain() {
  const steps = ["Analisando", "Pensando", "Organizando a resposta"];
  const [i, setI] = useState(0);
  useEffect(() => { const t = setInterval(() => setI((x) => (x + 1) % steps.length), 900); return () => clearInterval(t); }, []);
  return (
    <div className="flex items-center gap-2 py-2 text-[13px] text-muted-foreground">
      <span className="relative flex h-7 w-7 items-center justify-center">
        <span className="absolute inset-0 animate-ping rounded-full bg-primary/25" />
        <Brain size={18} className="relative animate-pulse text-primary icon-glow" />
      </span>
      <span className="shimmer-text">{steps[i]}…</span>
    </div>
  );
}

type Snip = { id: string; name: string; code: string };
function CodeEditor({ onClose }: { onClose: () => void }) {
  const [list, setList] = useState<Snip[]>([]);
  const [cur, setCur] = useState<Snip>({ id: "", name: "", code: "" });
  useEffect(() => { try { setList(JSON.parse(localStorage.getItem("kellvyn.snippets") || "[]")); } catch { /* empty */ } }, []);
  const persist = (n: Snip[]) => { setList(n); localStorage.setItem("kellvyn.snippets", JSON.stringify(n)); };
  const save = () => {
    if (!cur.code.trim()) return;
    const s = { ...cur, id: cur.id || uid(), name: cur.name.trim() || "Sem nome" };
    persist(cur.id ? list.map((x) => x.id === s.id ? s : x) : [s, ...list]); setCur(s);
  };
  return (
    <Sheet title="EDITOR DE CÓDIGO" onClose={onClose}>
      <input value={cur.name} onChange={(e) => setCur({ ...cur, name: e.target.value })} placeholder="Nome do arquivo"
        className="mb-2 w-full rounded-lg border border-input bg-muted px-3 py-2 text-sm outline-none" />
      <textarea value={cur.code} onChange={(e) => setCur({ ...cur, code: e.target.value })} spellCheck={false} placeholder="Escreva seu código aqui…"
        className="h-56 w-full resize-none rounded-lg border border-input bg-muted p-3 font-mono text-[13px] outline-none" />
      <div className="mt-2 flex gap-2">
        <button onClick={save} className="flex-1 rounded-lg bg-primary py-2 text-sm font-medium text-primary-foreground">Salvar</button>
        <button onClick={() => setCur({ id: "", name: "", code: "" })} className="rounded-lg border border-input px-4 text-sm">Novo</button>
      </div>
      <ul className="mt-3 flex flex-col gap-2">
        {list.map((x) => (
          <li key={x.id} className="flex items-center gap-2 rounded-lg border p-2">
            <button onClick={() => setCur(x)} className="flex-1 truncate text-left text-sm">{x.name}</button>
            <IconBtn label="Excluir" onClick={() => { if (confirm("Excluir este código?")) persist(list.filter((y) => y.id !== x.id)); }}><Trash2 size={15} /></IconBtn>
          </li>
        ))}
      </ul>
    </Sheet>
  );
}

function BrowserPanel({ onClose }: { onClose: () => void }) {
  const [q, setQ] = useState("");
  const go = () => {
    const t = q.trim(); if (!t) return;
    const url = /^https?:\/\//.test(t) ? t : /^[\w-]+(\.[\w-]+)+(\/.*)?$/.test(t) ? `https://${t}` : `https://www.google.com/search?q=${encodeURIComponent(t)}`;
    window.open(url, "_blank", "noopener");
  };
  return (
    <Sheet title="NAVEGADOR" onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); go(); }} className="flex gap-2">
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Pesquise ou digite um site" inputMode="url"
          className="min-w-0 flex-1 rounded-lg border border-input bg-muted px-3 py-2 text-sm outline-none" />
        <button className="rounded-lg bg-primary px-3 text-primary-foreground" aria-label="Ir"><Search size={18} /></button>
      </form>
      <div className="mt-3 grid grid-cols-4 gap-2">
        {[["Google","https://www.google.com"],["YouTube","https://www.youtube.com"],["Gmail","https://mail.google.com"],["Wikipédia","https://pt.wikipedia.org"]].map(([n,u]) => (
          <button key={n} onClick={() => window.open(u, "_blank", "noopener")} className="rounded-lg border border-input py-2 text-xs hover:bg-accent">{n}</button>
        ))}
      </div>
    </Sheet>
  );
}

type Task = { id: string; text: string; done: boolean };
function TasksPanel({ onClose }: { onClose: () => void }) {
  const [list, setList] = useState<Task[]>([]);
  const [draft, setDraft] = useState("");
  useEffect(() => { try { setList(JSON.parse(localStorage.getItem("kellvyn.tasks") || "[]")); } catch { /* empty */ } }, []);
  const persist = (n: Task[]) => { setList(n); localStorage.setItem("kellvyn.tasks", JSON.stringify(n)); };
  const left = list.filter((t) => !t.done).length;
  return (
    <Sheet title="TAREFAS" subtitle={`${left} pendente(s)`} onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); const t = draft.trim(); if (!t) return; persist([{ id: uid(), text: t, done: false }, ...list]); setDraft(""); }} className="mb-3 flex gap-2">
        <input value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="Nova tarefa…"
          className="min-w-0 flex-1 rounded-lg border border-input bg-muted px-3 py-2 text-sm outline-none" />
        <button className="rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground">Adicionar</button>
      </form>
      <ul className="flex flex-col gap-2">
        {list.map((t) => (
          <li key={t.id} className="flex items-center gap-2 rounded-lg border p-2">
            <button aria-label="Concluir" onClick={() => persist(list.map((x) => x.id === t.id ? { ...x, done: !x.done } : x))}
              className={`flex h-5 w-5 shrink-0 items-center justify-center rounded border ${t.done ? "border-primary bg-primary text-primary-foreground" : "border-input"}`}>
              {t.done && <Check size={13} />}
            </button>
            <span className={`flex-1 text-sm ${t.done ? "text-muted-foreground line-through" : ""}`}>{t.text}</span>
            <IconBtn label="Excluir" onClick={() => persist(list.filter((x) => x.id !== t.id))}><Trash2 size={15} /></IconBtn>
          </li>
        ))}
      </ul>
    </Sheet>
  );
}

const BRAND_ICONS: Record<string, ReactNode> = {
  discord: <svg viewBox="0 0 24 24" width="22" height="22" fill="#5865F2"><path d="M20.3 4.4A19.8 19.8 0 0 0 15.4 3l-.6 1.3a18.4 18.4 0 0 0-5.6 0L8.6 3a19.7 19.7 0 0 0-4.9 1.5C.6 9.1-.3 13.6.1 18.1a19.9 19.9 0 0 0 6 3l1.3-2.1a13 13 0 0 1-2-1l.5-.4a14.2 14.2 0 0 0 12.2 0l.5.4a12.9 12.9 0 0 1-2 1l1.3 2.1a19.8 19.8 0 0 0 6-3c.5-5.2-.9-9.7-3.6-13.7ZM8 15.4c-1.2 0-2.2-1.1-2.2-2.4S6.8 10.6 8 10.6s2.2 1.1 2.2 2.4-1 2.4-2.2 2.4Zm8 0c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4Z"/></svg>,
  whatsapp: <svg viewBox="0 0 24 24" width="22" height="22" fill="#25D366"><path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2Zm5.4 14.1c-.2.6-1.3 1.2-1.8 1.3-.5 0-1 .2-3.3-.7-2.8-1.1-4.5-4-4.7-4.2-.1-.2-1.1-1.5-1.1-2.9s.7-2 1-2.3c.2-.3.5-.3.7-.3h.5c.2 0 .4 0 .6.5l.8 2c.1.2.1.4 0 .5l-.3.5-.4.4c-.1.1-.3.3-.1.6.2.3.7 1.2 1.6 2 1.1 1 2 1.3 2.3 1.4.3.2.5.1.6 0l.9-1c.2-.3.4-.2.6-.1l1.9.9c.3.1.5.2.5.3.1.1.1.6-.1 1.1Z"/></svg>,
  telegram: <svg viewBox="0 0 24 24" width="22" height="22" fill="#29A9EB"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm4.6 6.8-1.6 7.7c-.1.6-.5.7-1 .4l-2.6-1.9-1.3 1.2c-.1.1-.3.3-.6.3l.2-2.7 4.9-4.4c.2-.2 0-.3-.3-.1l-6 3.8-2.6-.8c-.6-.2-.6-.6.1-.8l10.1-3.9c.5-.2.9.1.7.9Z"/></svg>,
};

function GreenSwitch({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button role="switch" aria-checked={on} aria-label={label} onClick={() => onChange(!on)}
      className="relative h-7 w-12 shrink-0 rounded-full transition-colors duration-300"
      style={{ background: on ? "#22c55e" : "#4b5563", boxShadow: on ? "0 0 12px #22c55e88" : "none" }}>
      <span className="absolute top-1 h-5 w-5 rounded-full bg-white shadow transition-all duration-300" style={{ left: on ? 24 : 4 }} />
    </button>
  );
}

function BotsPanel({ onClose }: { onClose: () => void }) {
  const [st, setSt] = useState<Record<string, boolean>>({});
  useEffect(() => { try { setSt(JSON.parse(localStorage.getItem("kellvyn.bots") || "{}")); } catch { /* empty */ } }, []);
  const set = (k: string, v: boolean) => { const n = { ...st, [k]: v }; setSt(n); localStorage.setItem("kellvyn.bots", JSON.stringify(n)); };
  return (
    <Sheet title="BOTS ATIVOS" onClose={onClose}>
      <p className="mb-4 rounded-lg border border-input bg-muted p-3 text-sm text-muted-foreground">
        Essa área serve para não deixar a tarefa morrer: mantenha seus bots ligados ou desligue quando quiser.
      </p>
      <ul className="flex flex-col gap-2">
        {([["discord", "Discord"], ["whatsapp", "WhatsApp"], ["telegram", "Telegram"]] as const).map(([k, n]) => (
          <li key={k} className="flex items-center gap-3 rounded-xl border p-3">
            {BRAND_ICONS[k]}
            <div className="flex-1">
              <p className="text-sm font-medium">{n}</p>
              <p className="text-xs" style={{ color: st[k] ? "#22c55e" : undefined }}>{st[k] ? "Ativo" : "Desativado"}</p>
            </div>
            <GreenSwitch on={!!st[k]} onChange={(v) => set(k!, v)} label={`Bot do ${n}`} />
          </li>
        ))}
      </ul>
    </Sheet>
  );
}

type Plugin = { id: string; name: string; url: string; on: boolean };
function PluginsPanel({ onClose }: { onClose: () => void }) {
  const [list, setList] = useState<Plugin[]>([]);
  const [name, setName] = useState(""); const [url, setUrl] = useState("");
  useEffect(() => { try { setList(JSON.parse(localStorage.getItem("kellvyn.plugins") || "[]")); } catch { /* empty */ } }, []);
  const persist = (n: Plugin[]) => { setList(n); localStorage.setItem("kellvyn.plugins", JSON.stringify(n)); };
  return (
    <Sheet title="PLUGINS" subtitle="Adicione outras IAs e ferramentas" onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); if (!name.trim()) return; persist([...list, { id: uid(), name: name.trim(), url: url.trim(), on: true }]); setName(""); setUrl(""); }} className="mb-3 flex flex-col gap-2">
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Nome (ex: Gemini, Claude)" className="rounded-lg border border-input bg-muted px-3 py-2 text-sm outline-none" />
        <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="Endereço (opcional)" inputMode="url" className="rounded-lg border border-input bg-muted px-3 py-2 text-sm outline-none" />
        <button className="rounded-lg bg-primary py-2 text-sm font-medium text-primary-foreground">Adicionar plugin</button>
      </form>
      <ul className="flex flex-col gap-2">
        {list.map((p) => (
          <li key={p.id} className="flex items-center gap-3 rounded-xl border p-3">
            <Puzzle size={20} className="text-primary" />
            <div className="min-w-0 flex-1"><p className="truncate text-sm">{p.name}</p>{p.url && <p className="truncate text-xs text-muted-foreground">{p.url}</p>}</div>
            <GreenSwitch on={p.on} onChange={(v) => persist(list.map((x) => x.id === p.id ? { ...x, on: v } : x))} label={p.name} />
            <IconBtn label="Excluir" onClick={() => persist(list.filter((x) => x.id !== p.id))}><Trash2 size={15} /></IconBtn>
          </li>
        ))}
      </ul>
    </Sheet>
  );
}

function SwipeRow({ children, onDelete }: { children: ReactNode; onDelete: () => void }) {
  const [x, setX] = useState(0);
  const start = useRef<number | null>(null);
  const base = useRef(0);
  const OPEN = -72;
  return (
    <li className="relative overflow-hidden rounded-lg">
      <button aria-label="Apagar conversa" onClick={() => { if (confirm("Apagar esta conversa?")) onDelete(); else setX(0); }}
        className="absolute inset-y-0 right-0 flex w-[72px] items-center justify-center bg-destructive text-destructive-foreground">
        <Trash2 size={20} />
      </button>
      <div className="relative bg-background" style={{ transform: `translateX(${x}px)`, transition: start.current === null ? "transform .25s" : "none", touchAction: "pan-y" }}
        onPointerDown={(e) => { start.current = e.clientX; base.current = x; }}
        onPointerMove={(e) => { if (start.current === null) return; setX(Math.max(OPEN - 20, Math.min(0, base.current + e.clientX - start.current))); }}
        onPointerUp={() => { start.current = null; setX((v) => (v < OPEN / 2 ? OPEN : 0)); }}
        onPointerCancel={() => { start.current = null; setX(0); }}
        onClickCapture={(e) => { if (x !== 0) { e.stopPropagation(); setX(0); } }}>
        {children}
      </div>
    </li>
  );
}
