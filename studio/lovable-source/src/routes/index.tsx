import { createFileRoute } from "@tanstack/react-router";
import { KellvynApp } from "@/components/kellvyn/KellvynApp";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Kellvyn Assistente — Kellvyn IA" },
      { name: "description", content: "Chat único da Kellvyn IA com histórico, catálogo de apps e configurações." },
      { property: "og:title", content: "Kellvyn Assistente — Kellvyn IA" },
      { property: "og:description", content: "Chat único da Kellvyn IA com histórico, catálogo de apps e configurações." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: KellvynApp,
});
