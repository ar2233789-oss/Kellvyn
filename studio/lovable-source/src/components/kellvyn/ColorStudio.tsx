import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import { normalizeHex } from "./store";

type HSV = { h: number; s: number; v: number };

export function hexToHsv(hex: string): HSV {
  const n = parseInt(hex.slice(1), 16);
  const r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return { h: (h * 60 + 360) % 360, s: max ? d / max : 0, v: max };
}

export function hsvToHex({ h, s, v }: HSV): string {
  const f = (n: number) => {
    const k = (n + h / 60) % 6;
    return Math.round((v - v * s * Math.max(0, Math.min(k, 4 - k, 1))) * 255);
  };
  return "#" + [f(5), f(3), f(1)].map((x) => x.toString(16).padStart(2, "0")).join("").toUpperCase();
}

const SOFT = ["#38E6BC", "#D4AF37", "#E5484D", "#4C9AFF", "#A78BFA", "#F472B6", "#C9CED6", "#F08A4B", "#9BCB5A", "#2DD4BF", "#0B1413", "#0A0907", "#111214", "#0E0A16", "#070D18", "#F2EFE9"];

function Slider({ label, value, onChange, track }: { label: string; value: number; onChange: (v: number) => void; track: string }) {
  return (
    <label className="block py-1.5">
      <div className="mb-1 flex justify-between text-[11px] text-muted-foreground"><span>{label}</span><span className="font-mono">{Math.round(value)}%</span></div>
      <input type="range" min={0} max={100} value={value} onChange={(e) => onChange(Number(e.target.value))}
        className="kv-range h-3 w-full cursor-pointer appearance-none rounded-full" style={{ background: track }} />
    </label>
  );
}

export function ColorStudio({ title, value, onChange, onClose }: { title: string; value: string; onChange: (hex: string) => void; onClose: () => void }) {
  const [hsv, setHsv] = useState<HSV>(() => hexToHsv(value));
  const [draft, setDraft] = useState(value);
  const box = useRef<HTMLDivElement>(null);
  const hex = hsvToHex(hsv);

  useEffect(() => { onChange(hex); setDraft(hex); }, [hex]); // eslint-disable-line react-hooks/exhaustive-deps

  const pick = (e: React.PointerEvent) => {
    const r = box.current!.getBoundingClientRect();
    const s = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    const v = 1 - Math.min(1, Math.max(0, (e.clientY - r.top) / r.height));
    setHsv((p) => ({ ...p, s, v }));
  };
  const pure = hsvToHex({ h: hsv.h, s: 1, v: 1 });

  return (
    <div className="fixed inset-0 z-[60] flex items-end bg-background/70 backdrop-blur-sm" onClick={onClose}>
      <div className="w-full rounded-t-2xl border-t border-input bg-popover p-4 pb-safe" onClick={(e) => e.stopPropagation()}>
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="h-6 w-6 rounded-md border border-input" style={{ background: hex }} />
            <span className="text-sm font-semibold tracking-wide">{title}</span>
          </div>
          <button aria-label="Fechar" onClick={onClose} className="p-1 text-muted-foreground"><X size={18} /></button>
        </div>

        <div ref={box} onPointerDown={(e) => { (e.target as HTMLElement).setPointerCapture(e.pointerId); pick(e); }}
          onPointerMove={(e) => e.buttons && pick(e)}
          className="relative h-40 w-full touch-none rounded-xl"
          style={{ background: `linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, ${pure})` }}>
          <span className="pointer-events-none absolute h-5 w-5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-foreground shadow"
            style={{ left: `${hsv.s * 100}%`, top: `${(1 - hsv.v) * 100}%`, background: hex }} />
        </div>

        <div className="mt-3">
          <label className="block py-1.5">
            <div className="mb-1 text-[11px] text-muted-foreground">Tom</div>
            <input type="range" min={0} max={360} value={hsv.h} onChange={(e) => setHsv((p) => ({ ...p, h: Number(e.target.value) }))}
              className="kv-range h-3 w-full cursor-pointer appearance-none rounded-full"
              style={{ background: "linear-gradient(to right,#f00,#ff0,#0f0,#0ff,#00f,#f0f,#f00)" }} />
          </label>
          <Slider label="Saturação" value={hsv.s * 100} onChange={(v) => setHsv((p) => ({ ...p, s: v / 100 }))}
            track={`linear-gradient(to right, ${hsvToHex({ ...hsv, s: 0 })}, ${hsvToHex({ ...hsv, s: 1 })})`} />
          <Slider label="Brilho / Sombra" value={hsv.v * 100} onChange={(v) => setHsv((p) => ({ ...p, v: v / 100 }))}
            track={`linear-gradient(to right, #000, ${hsvToHex({ ...hsv, v: 1 })})`} />
        </div>

        <div className="mt-2 grid grid-cols-8 gap-2">
          {SOFT.map((c) => (
            <button key={c} aria-label={c} onClick={() => setHsv(hexToHsv(c))} className="aspect-square rounded-md border border-input" style={{ background: c }} />
          ))}
        </div>

        <div className="mt-3 flex gap-2">
          <input value={draft} onChange={(e) => setDraft(e.target.value)} maxLength={7} aria-label="HEX"
            className="min-w-0 flex-1 rounded-lg border border-input bg-muted px-3 py-2 font-mono text-sm uppercase outline-none focus:ring-1 focus:ring-ring" />
          <button onClick={() => { const h = normalizeHex(draft); if (h) setHsv(hexToHsv(h)); }}
            className="rounded-lg border border-input px-3 text-sm">HEX</button>
          <button onClick={onClose} className="rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground">Pronto</button>
        </div>
      </div>
    </div>
  );
}
