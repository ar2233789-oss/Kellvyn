const $ = (id) => document.getElementById(id);
const download = (name, body, mime = "application/json;charset=utf-8") => {
  const url = URL.createObjectURL(new Blob([body], { type: mime }));
  const a = document.createElement("a"); a.href = url; a.download = name; document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1200);
};

const palettes = [
  {name:"Esmeralda Profundo",group:"Verdes",tags:"kellvyn natureza verde",colors:["#031C16","#073B2C","#0B5C42","#11835B","#18A875","#20D69A","#7CF0C0","#D7FFF0"]},
  {name:"Verde Android",group:"Verdes",tags:"interface tech",colors:["#07120F","#0B1915","#10221C","#143D2D","#176B49","#20D69A","#70E8B5","#E4F4ED"]},
  {name:"Floresta Úmida",group:"Natureza",tags:"árvore mata terra",colors:["#15251B","#243D2A","#355C39","#4E7946","#789451","#AAB67A","#D3D5A5","#F0EBD2"]},
  {name:"Aurora Boreal",group:"Fantasia",tags:"aurora verde azul roxo",colors:["#071B2B","#124B5E","#1D8A8A","#41D6AF","#87FFD0","#6AA6FF","#9A7CFF","#F1D1FF"]},
  {name:"Oceano Abissal",group:"Azuis",tags:"mar profundo água",colors:["#020B1C","#071D3B","#0B3560","#125A86","#1688A8","#26B8C7","#87E7E2","#D3FCF7"]},
  {name:"Gelo Ártico",group:"Azuis",tags:"frio neve claro",colors:["#172A3A","#2B4D65","#49738B","#73A6BA","#A3D0D9","#C8E9EA","#E7F8F5","#FFFFFF"]},
  {name:"Neon Cyberpunk",group:"Neon",tags:"cyberpunk noite roxo rosa",colors:["#090A1A","#17113B","#3A1768","#7B2CFF","#C13DFF","#FF3CA6","#00E5FF","#D9FFF8"]},
  {name:"Raios Elétricos",group:"Neon",tags:"energia raio choque azul",colors:["#060B1E","#101F52","#1749AD","#297BFF","#58BDFF","#A2E8FF","#F2FFFF","#E4CAFF"]},
  {name:"Violeta Místico",group:"Roxos",tags:"fantasia magia púrpura",colors:["#170B24","#311342","#53206E","#7531A0","#9F55D8","#C88CFF","#E4C5FF","#FFF0FF"]},
  {name:"Pôr do Sol",group:"Quentes",tags:"sunset laranja rosa",colors:["#29113A","#57204A","#94364D","#D65350","#F47B52","#FFB565","#FFE1A6","#FFF4DF"]},
  {name:"Fogo Vivo",group:"Quentes",tags:"fogo chama calor",colors:["#1A0808","#48100B","#7D1C0B","#B9380B","#E85C0A","#FF8D1F","#FFC34D","#FFF0A8"]},
  {name:"Cinemático Teal & Orange",group:"Cinema",tags:"filme teal orange grade",colors:["#101D24","#1B343A","#28585A","#4D7C78","#C56A3B","#E89150","#F6C17B","#F1E3CF"]},
  {name:"Noir Monocromático",group:"Monocromáticas",tags:"preto branco cinza noir",colors:["#050607","#17191C","#2B3035","#464D54","#69727A","#929BA3","#CBD0D4","#F8FAFC"]},
  {name:"Prata e Titânio",group:"Metais",tags:"metal aço titânio",colors:["#171C20","#303A41","#4C5961","#6C7A82","#8999A1","#B5C0C6","#D8E0E4","#F7FAFC"]},
  {name:"Cobre e Bronze",group:"Metais",tags:"cobre bronze metal quente",colors:["#26150F","#4C2B1E","#70422B","#945E3B","#B77E51","#D6A878","#EBC99F","#F8E5C9"]},
  {name:"Ouro Real",group:"Metais",tags:"ouro dourado luxo",colors:["#241A07","#4A3509","#765513","#A67A18","#D0A52C","#EBC85B","#F6E39B","#FFF8D9"]},
  {name:"Pastel Suave",group:"Pastéis",tags:"pastel delicado claro",colors:["#F8D7E8","#F6C8D4","#FFDCC2","#FFF0C2","#D8F0D1","#BFE9E1","#C9DDF8","#E5D6FA"]},
  {name:"Candy Color",group:"Pastéis",tags:"doce rosa azul amarelo",colors:["#FFB3C7","#FF8FA8","#FFC6A8","#FFE19A","#CBF3B8","#9DE9D5","#A8D8FF","#D6B8FF"]},
  {name:"Terra e Argila",group:"Terra",tags:"terra barro pedra orgânico",colors:["#211711","#493025","#704634","#98634A","#B98767","#D3AD8B","#E7CEB1","#F8EAD9"]},
  {name:"Deserto Dourado",group:"Terra",tags:"areia deserto sol",colors:["#342518","#5A4024","#846033","#AC8548","#D2B66F","#E5D19A","#F2E4BD","#FFF6DF"]},
  {name:"Tropical Vibrante",group:"Natureza",tags:"tropical verde rosa cítrico",colors:["#073B37","#087F5B","#12A878","#9BD631","#FFE04F","#FF9B42","#F85E76","#C44BE7"]},
  {name:"Vermelho Energia",group:"Vermelhos",tags:"vermelho alerta potência",colors:["#21070D","#4B0D18","#781429","#AF203A","#E52D4D","#FF5D66","#FF9B9B","#FFE1E3"]},
  {name:"Rosa Galáxia",group:"Roxos",tags:"rosa galáxia cósmico",colors:["#1B0C2C","#3C1751","#65236F","#96347F","#CA4B94","#EC80B0","#F8BBD5","#FFE7F1"]},
  {name:"Sci-Fi Industrial",group:"Sci-fi",tags:"nave industrial robô aço",colors:["#111820","#202E3A","#354958","#526D79","#718F98","#B3C5C7","#D5DDE0","#FFB84D"]},
  {name:"Arcade Retrô",group:"Retrô",tags:"arcade retro 80s",colors:["#241447","#463078","#7C3FA1","#C64F9A","#F17B68","#FFBF69","#8FDB9A","#6FDCE0"]},
  {name:"Oliva Militar",group:"Verdes",tags:"oliva militar utilitário",colors:["#1B2114","#343D20","#525F2D","#78833B","#A7A85A","#C7C486","#DAD6A3","#F0ECD4"]},
  {name:"Vento e Nuvens",group:"Neutras",tags:"cinza céu nuvem chuva",colors:["#26323B","#465560","#637784","#8498A3","#A9B9C0","#CEDADD","#E4ECEB","#FFFFFF"]},
  {name:"Lagoa Noturna",group:"Azuis",tags:"lago azul noite ciano",colors:["#061923","#0B3540","#0F5960","#177F7A","#23A99A","#46D2B1","#9AEDD4","#E1FFF3"]},
  {name:"Dourado e Vinho",group:"Luxo",tags:"luxo vinho dourado",colors:["#1D0D16","#471323","#72233A","#9F344B","#C4565C","#CF9B4C","#E8C477","#FAEDCC"]},
  {name:"Alta Legibilidade",group:"Contraste",tags:"contraste acessibilidade UI",colors:["#000000","#FFFFFF","#005FCC","#FFB000","#007A5E","#7A0019","#5A2CA0","#FFF3B0"]},
  {name:"Biofluorescência",group:"Sci-fi",tags:"bioluminescência toxina neon",colors:["#071A17","#0D4437","#0DAA79","#52FF9D","#C2FF52","#DBFAF0","#2DDAE8","#A873FF"]},
  {name:"Tempestade Roxa",group:"Clima",tags:"tempestade relâmpago roxo",colors:["#0A0D1B","#1E2446","#383E78","#6565B1","#8FA2DD","#D5DBFF","#A25DFF","#F5E8FF"]},
  {name:"Neve Azulada",group:"Clima",tags:"inverno neve azul branca",colors:["#152532","#2C4657","#496778","#75939E","#A4C0C8","#D0E0E2","#ECF6F4","#FFFFFF"]},
  {name:"Flamingo",group:"Pastéis",tags:"rosa coral verão",colors:["#4D153A","#8C2556","#CB426B","#EE6680","#FF9E9B","#FFC3AA","#FFE3C9","#FFF6E8"]},
  {name:"Esmeralda e Ouro",group:"Luxo",tags:"verde ouro luxuoso",colors:["#06251D","#0A4C38","#087A56","#16A674","#6FD6A1","#B7E5B3","#D6B35A","#F8E7AF"]},
  {name:"Ametista Fria",group:"Roxos",tags:"violeta ametista frio",colors:["#171329","#302852","#4A3C78","#6E5AA1","#9680C5","#BEB1DE","#E2D9F4","#F8F3FF"]},
  {name:"Pântano e Musgo",group:"Natureza",tags:"musgo pântano orgânico",colors:["#161D14","#283523","#3C4D31","#536844","#768452","#9BA66D","#C2C79D","#E8E7D2"]}
];

const materials = [
  {name:"Plástico fosco",color:"#20D69A",metalness:0.02,roughness:0.88,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Plástico brilhante",color:"#1A9BFF",metalness:0.05,roughness:0.15,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Cerâmica acetinada",color:"#E6E9E4",metalness:0.04,roughness:0.36,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Porcelana branca",color:"#F5F4EE",metalness:0,roughness:0.18,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Alumínio escovado",color:"#AAB8C0",metalness:0.88,roughness:0.34,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Cromo espelhado",color:"#D7E1E8",metalness:1,roughness:0.08,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Titânio escuro",color:"#48545D",metalness:0.94,roughness:0.28,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Cobre polido",color:"#B8734C",metalness:0.91,roughness:0.2,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Ouro polido",color:"#D9AD46",metalness:0.94,roughness:0.17,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Bronze antigo",color:"#8C623E",metalness:0.77,roughness:0.62,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Borracha",color:"#242A2C",metalness:0,roughness:0.98,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Pedra vulcânica",color:"#34363B",metalness:0.05,roughness:0.94,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Mármore claro",color:"#DCDAD3",metalness:0.02,roughness:0.26,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Madeira quente",color:"#98633B",metalness:0,roughness:0.82,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Vidro translúcido",color:"#A9E9F0",metalness:0,roughness:0.08,opacity:0.36,transparent:true,emissive:"#000000",emissiveIntensity:0},
  {name:"Vidro fosco",color:"#BFE8E7",metalness:0,roughness:0.58,opacity:0.52,transparent:true,emissive:"#000000",emissiveIntensity:0},
  {name:"Cristal violeta",color:"#C49AFF",metalness:0.12,roughness:0.06,opacity:0.42,transparent:true,emissive:"#160025",emissiveIntensity:0.12},
  {name:"Gel luminoso",color:"#27DFA4",metalness:0.1,roughness:0.22,opacity:0.78,transparent:true,emissive:"#13FF9D",emissiveIntensity:1.1},
  {name:"Energia ciano",color:"#53E8FF",metalness:0.15,roughness:0.18,opacity:1,transparent:false,emissive:"#13CFFF",emissiveIntensity:1.8},
  {name:"Energia violeta",color:"#A36BFF",metalness:0.1,roughness:0.22,opacity:1,transparent:false,emissive:"#762BFF",emissiveIntensity:1.7},
  {name:"Energia rubra",color:"#FF4D63",metalness:0.08,roughness:0.28,opacity:1,transparent:false,emissive:"#FF122F",emissiveIntensity:1.4},
  {name:"Cerâmica obsidiana",color:"#12151B",metalness:0.34,roughness:0.12,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Tecido áspero",color:"#6D7D72",metalness:0,roughness:1,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0},
  {name:"Pintura automotiva",color:"#C6203D",metalness:0.46,roughness:0.2,opacity:1,transparent:false,emissive:"#000000",emissiveIntensity:0}
];

const lights = [
  {name:"Estúdio suave",ambient:0.95,directional:1.75,color:"#F1FFF7",ambientColor:"#D9FFF0",groundColor:"#243A32",shadow:"soft",map:1024,radius:4,bias:-0.00015},
  {name:"Sol do meio-dia",ambient:0.82,directional:3.1,color:"#FFF4DE",ambientColor:"#FFFFFF",groundColor:"#7E8A75",shadow:"soft",map:2048,radius:2,bias:-0.0001},
  {name:"Fim de tarde",ambient:0.42,directional:2.45,color:"#FFAA70",ambientColor:"#FFDFC2",groundColor:"#3B2831",shadow:"soft",map:2048,radius:5,bias:-0.0002},
  {name:"Luar azul",ambient:0.18,directional:1.15,color:"#8DBAFF",ambientColor:"#C7DFFF",groundColor:"#081326",shadow:"soft",map:1024,radius:6,bias:-0.00025},
  {name:"Noir dramático",ambient:0.1,directional:3.5,color:"#E7F4FF",ambientColor:"#8B9DB4",groundColor:"#090A11",shadow:"hard",map:2048,radius:1,bias:-0.00012},
  {name:"Horror carmesim",ambient:0.12,directional:2.3,color:"#FF334F",ambientColor:"#6F1C2C",groundColor:"#10090E",shadow:"hard",map:1024,radius:1,bias:-0.00018},
  {name:"Fantasia arcana",ambient:0.45,directional:2.0,color:"#BCA1FF",ambientColor:"#D9C5FF",groundColor:"#281739",shadow:"soft",map:2048,radius:5,bias:-0.0002},
  {name:"Bioluminescência",ambient:0.36,directional:1.25,color:"#47FFD0",ambientColor:"#A6FFE4",groundColor:"#07271E",shadow:"soft",map:1024,radius:7,bias:-0.00025},
  {name:"Luz industrial",ambient:0.4,directional:2.75,color:"#C6D0D7",ambientColor:"#DDE1E5",groundColor:"#293037",shadow:"variance",map:2048,radius:3,bias:-0.0001},
  {name:"Neon cyberpunk",ambient:0.24,directional:1.8,color:"#D34BFF",ambientColor:"#C2B7FF",groundColor:"#170B2A",shadow:"soft",map:1024,radius:6,bias:-0.00022},
  {name:"Céu nublado",ambient:1.12,directional:0.6,color:"#C8E0EE",ambientColor:"#E5F3FA",groundColor:"#778D9A",shadow:"soft",map:512,radius:8,bias:-0.0003},
  {name:"Luz clínica",ambient:1.2,directional:2.0,color:"#FFFFFF",ambientColor:"#FFFFFF",groundColor:"#B8C2C9",shadow:"soft",map:1024,radius:2,bias:-0.0001},
  {name:"Contraluz âmbar",ambient:0.22,directional:3.8,color:"#FFC06A",ambientColor:"#FFDEAD",groundColor:"#21140D",shadow:"hard",map:2048,radius:1,bias:-0.0001},
  {name:"Tempestade elétrica",ambient:0.28,directional:3.0,color:"#91B9FF",ambientColor:"#9FAEFF",groundColor:"#111627",shadow:"variance",map:2048,radius:6,bias:-0.0002}
];

const geometryNames = [
  ["cylinder","Cilindro"],["cone","Cone"],["capsule","Cápsula"],["ring","Anel"],
  ["octahedron","Octaedro"],["icosahedron","Icosaedro"],["dodecahedron","Dodecaedro"],
  ["tetrahedron","Tetraedro"],["torus-knot","Nó toroidal"]
];

function status(message) { const node = $("resource-status"); if (node) node.textContent = message; }
function makeButton(label, className, handler, title) {
  const button = document.createElement("button"); button.type = "button"; button.className = className || "secondary-button";
  button.textContent = label; if (title) button.title = title; button.addEventListener("click", handler); return button;
}
function renderPaletteLibrary() {
  const host = $("palette-library"); if (!host) return;
  const search = ($("palette-search").value || "").trim().toLowerCase();
  const group = $("palette-group").value;
  const filtered = palettes.filter(p => (group === "all" || p.group === group) &&
    (!search || (p.name + " " + p.group + " " + p.tags + " " + p.colors.join(" ")).toLowerCase().includes(search)));
  host.replaceChildren();
  filtered.forEach((palette) => {
    const card = document.createElement("article"); card.className = "resource-palette-card";
    const head = document.createElement("div"); head.className = "resource-palette-head";
    const title = document.createElement("div"); title.className = "resource-palette-title";
    const strong = document.createElement("b"); strong.textContent = palette.name;
    const small = document.createElement("small"); small.textContent = palette.group + " · " + palette.colors.length + " cores";
    title.append(strong, small);
    head.append(title, makeButton("Aplicar paleta", "mini-button", () => {
      const scene = globalThis.StudioScene;
      if (!scene?.applyPalette) { status("Motor de cena indisponível."); return; }
      const result = scene.applyPalette(palette.colors);
      status(result ? "Paleta aplicada aos objetos da cena: " + palette.name : "Adicione um objeto na cena antes de aplicar uma paleta.");
    }));
    const swatches = document.createElement("div"); swatches.className = "resource-swatches";
    palette.colors.forEach(color => {
      const btn = document.createElement("button"); btn.type = "button"; btn.className = "resource-swatch";
      btn.style.backgroundColor = color; btn.title = color.toUpperCase(); btn.setAttribute("aria-label", "Selecionar cor " + color);
      btn.addEventListener("click", () => {
        $("palette-active-color").value = color; $("palette-active-hex").textContent = color.toUpperCase();
        status("Cor selecionada: " + color.toUpperCase() + " · pressione Aplicar cor ao objeto.");
      });
      swatches.append(btn);
    });
    card.append(head, swatches); host.append(card);
  });
  if (!filtered.length) host.innerHTML = '<div class="empty-state">Nenhuma paleta corresponde à busca.</div>';
  $("palette-count").textContent = filtered.length + " paletas · " + filtered.reduce((sum,p) => sum + p.colors.length, 0) + " cores";
}
function renderMaterials() {
  const host = $("material-library"); host.replaceChildren();
  materials.forEach(material => {
    const card = document.createElement("article"); card.className = "resource-preset-card";
    const chip = document.createElement("span"); chip.className = "material-chip";
    chip.style.backgroundColor = material.color; chip.style.boxShadow = material.emissiveIntensity > 0 ? "0 0 18px " + material.emissive : "none";
    const copy = document.createElement("div"); copy.className = "resource-preset-copy";
    const strong = document.createElement("b"); strong.textContent = material.name;
    const detail = document.createElement("small");
    detail.textContent = "Metal " + material.metalness.toFixed(2) + " · Rugosidade " + material.roughness.toFixed(2) + (material.transparent ? " · translúcido" : "") + (material.emissiveIntensity ? " · emissivo" : "");
    copy.append(strong, detail);
    card.append(chip, copy, makeButton("Usar", "mini-button", () => {
      const result = globalThis.StudioScene?.applyMaterialPreset?.(material);
      status(result ? "Material aplicado: " + material.name : "Selecione um objeto na cena 3D antes de aplicar o material.");
    }));
    host.append(card);
  });
  $("material-count").textContent = materials.length + " materiais configurados";
}
function renderLights() {
  const host = $("lighting-library"); host.replaceChildren();
  lights.forEach(light => {
    const card = document.createElement("article"); card.className = "resource-light-card";
    const header = document.createElement("div"); header.className = "resource-light-head";
    const color = document.createElement("span"); color.className = "light-color-chip"; color.style.backgroundColor = light.color;
    const title = document.createElement("b"); title.textContent = light.name;
    header.append(color, title);
    const detail = document.createElement("p");
    detail.textContent = "Ambiente " + light.ambient.toFixed(2) + " · Direcional " + light.directional.toFixed(2) + " · " + light.shadow + " · mapa " + light.map;
    card.append(header, detail, makeButton("Aplicar iluminação", "mini-button", () => {
      const result = globalThis.StudioScene?.applyLightingPreset?.(light);
      status(result ? "Iluminação aplicada: " + light.name + "." : "Motor 3D indisponível; a iluminação não foi alterada.");
    }));
    host.append(card);
  });
  $("lighting-count").textContent = lights.length + " cenários de luz";
}
function renderGeometry() {
  const host = $("geometry-library"); host.replaceChildren();
  geometryNames.forEach(([type,label]) => {
    host.append(makeButton("＋ " + label, "geometry-resource-button", () => {
      const result = globalThis.StudioScene?.addGeometry?.(type);
      status(result ? "Forma adicionada: " + label + "." : "Inicialize o motor 3D primeiro.");
    }));
  });
  $("geometry-count").textContent = geometryNames.length + " formas extras";
}
function renderPaletteGroups() {
  const select = $("palette-group");
  [...new Set(palettes.map(p=>p.group))].sort((a,b)=>a.localeCompare(b,"pt-BR")).forEach(group => {
    const opt = document.createElement("option"); opt.value = group; opt.textContent = group; select.append(opt);
  });
}
function renderEasingOptions() {
  const descriptions = {
    linear:"Velocidade constante.",
    "ease-in":"Começa suave e acelera.",
    "ease-out":"Começa rápido e desacelera.",
    "ease-in-out":"Entrada e saída suaves.",
    "cubic-in":"Aceleração cúbica pronunciada.",
    "cubic-out":"Desaceleração cúbica pronunciada.",
    "cubic-in-out":"Curva cúbica simétrica.",
    "sine-in-out":"Movimento harmônico suave.",
    "back-out":"Ultrapassa levemente o alvo e volta.",
    "bounce-out":"Efeito de quique no final.",
    "elastic-out":"Oscilação elástica no final.",
    step:"Mantém a pose e troca no limite."
  };
  Object.entries(descriptions).forEach(([value,label]) => {
    const option = document.createElement("option"); option.value = value; option.textContent = value + " — " + label; $("easing-select").append(option);
  });
  $("easing-select").addEventListener("change", () => {
    const ok = globalThis.StudioScene?.setEasing?.($("easing-select").value);
    $("easing-description").textContent = descriptions[$("easing-select").value];
    status(ok ? "Curva de animação alterada para " + $("easing-select").value + "." : "Curva será aplicada quando o motor estiver pronto.");
  });
  $("easing-select").value = "linear";
  globalThis.StudioScene?.setEasing?.("linear");
  $("easing-description").textContent = descriptions.linear;
}

let weather = {
  schema:"kellvyn-weather-studio/v1", type:"rain", density:200, speed:6.0, wind:0.6,
  length:20, opacity:0.72, glow:0.42, color:"#BCEEFF", background:"#071722", paused:false
};
let weatherCtx = null, weatherWidth = 640, weatherHeight = 300, weatherParticles = [], lastWeatherFrame = 0, lightningFlash = 0, lightningPath = [];
function randomRange(min, max) { return min + Math.random() * (max - min); }
function syncWeatherInputs() {
  const ids = ["density","speed","wind","length","opacity","glow","color","background"];
  ids.forEach(key => { const input = $("weather-" + key); if (input) input.value = weather[key]; });
  ["density","speed","wind","length","opacity","glow"].forEach(key => {
    const out = $("weather-" + key + "-value"); if (out) out.textContent = String(weather[key]);
  });
  $("weather-type").value = weather.type;
  $("weather-pause").textContent = weather.paused ? "▶ Continuar prévia" : "Ⅱ Pausar prévia";
  $("weather-state").textContent = "Prévia local · " + weather.type + " · " + weather.density + " partículas";
  resetWeatherParticles();
}
function resetWeatherParticles() {
  const count = Math.max(10, Math.min(650, Number(weather.density) || 100));
  weatherParticles = Array.from({length:count}, () => ({
    x:randomRange(0,weatherWidth), y:randomRange(0,weatherHeight),
    vx:randomRange(-0.3,0.3), vy:randomRange(0.4,1.0),
    radius:randomRange(0.7,2.8), phase:randomRange(0,Math.PI*2), alpha:randomRange(0.35,1)
  }));
}
function resizeWeatherCanvas() {
  const canvas = $("weather-preview"); if (!canvas) return;
  const rect = canvas.getBoundingClientRect(); const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
  weatherWidth = Math.max(1, rect.width); weatherHeight = Math.max(1, rect.height);
  canvas.width = Math.floor(weatherWidth * dpr); canvas.height = Math.floor(weatherHeight * dpr);
  weatherCtx = canvas.getContext("2d"); if (weatherCtx) weatherCtx.setTransform(dpr,0,0,dpr,0,0);
  resetWeatherParticles();
}
function drawLightning(ctx, now) {
  if (lightningFlash <= 0 && Math.random() < 0.003) {
    lightningFlash = randomRange(0.12,0.3);
    let x = randomRange(weatherWidth*0.18, weatherWidth*0.82), y = 0;
    lightningPath = [{x,y}];
    while (y < weatherHeight*0.68) {
      x += randomRange(-24,24); y += randomRange(12,34); lightningPath.push({x,y});
    }
  }
  if (lightningFlash > 0) {
    lightningFlash -= 0.016;
    ctx.fillStyle = "rgba(170,195,255," + Math.min(0.22,lightningFlash) + ")";
    ctx.fillRect(0,0,weatherWidth,weatherHeight);
    ctx.save(); ctx.strokeStyle = "#F1F4FF"; ctx.lineWidth = 2.2; ctx.shadowBlur = 22; ctx.shadowColor = weather.color;
    ctx.beginPath(); lightningPath.forEach((p,i) => i ? ctx.lineTo(p.x,p.y) : ctx.moveTo(p.x,p.y)); ctx.stroke(); ctx.restore();
  }
}
function drawWeather(now) {
  requestAnimationFrame(drawWeather);
  const canvas = $("weather-preview"); if (!canvas || !weatherCtx) return;
  const ctx = weatherCtx;
  const dt = Math.min(0.04, lastWeatherFrame ? (now - lastWeatherFrame) / 1000 : 0.016);
  lastWeatherFrame = now;
  ctx.clearRect(0,0,weatherWidth,weatherHeight);
  const bg = ctx.createLinearGradient(0,0,0,weatherHeight); bg.addColorStop(0,weather.background); bg.addColorStop(1,"#02070c");
  ctx.fillStyle = bg; ctx.fillRect(0,0,weatherWidth,weatherHeight);
  if (weather.paused) {
    ctx.fillStyle = "#B8D1DE"; ctx.font = "12px system-ui"; ctx.fillText("Prévia pausada",14,24); return;
  }
  if (weather.type === "mist") {
    ctx.save();
    for (let i=0;i<12;i++) {
      const x = ((now*0.008*(i%2?1:-1) + i*97) % (weatherWidth+180)) - 90;
      const y = weatherHeight*(0.35 + (i%5)*0.12);
      const gradient = ctx.createRadialGradient(x,y,4,x,y,90+i%4*25);
      gradient.addColorStop(0,"rgba(190,220,225,"+(0.035+weather.opacity*0.07)+")"); gradient.addColorStop(1,"rgba(190,220,225,0)");
      ctx.fillStyle=gradient; ctx.fillRect(x-150,y-100,300,200);
    }
    ctx.restore();
  }
  ctx.save(); ctx.globalAlpha = weather.opacity;
  weatherParticles.forEach((p) => {
    const velocity = weather.speed;
    if (weather.type === "rain" || weather.type === "lightning") {
      p.x += (weather.wind*velocity*22 + p.vx*10)*dt;
      p.y += velocity*95*p.vy*dt;
      if(p.y > weatherHeight+weather.length || p.x < -80 || p.x > weatherWidth+80) {p.x=randomRange(-30,weatherWidth+30);p.y=randomRange(-weatherHeight*0.4,0);}
      ctx.beginPath(); ctx.strokeStyle=weather.color;ctx.lineWidth=0.7+velocity*0.12;ctx.globalAlpha=weather.opacity*p.alpha;
      if(weather.glow>0.01){ctx.shadowBlur=weather.glow*14;ctx.shadowColor=weather.color;}
      ctx.moveTo(p.x,p.y);ctx.lineTo(p.x+weather.wind*weather.length*0.65,p.y+weather.length*(0.35+velocity*0.12));ctx.stroke();ctx.shadowBlur=0;
    } else if (weather.type === "snow") {
      p.x += (Math.sin(now*0.001+p.phase)*0.4 + weather.wind*0.65)*velocity*dt;
      p.y += velocity*17*p.vy*dt;
      if(p.y > weatherHeight+5){p.y=-5;p.x=randomRange(0,weatherWidth);}
      ctx.globalAlpha=weather.opacity*p.alpha;ctx.fillStyle=weather.color;
      if(weather.glow>0.01){ctx.shadowBlur=weather.glow*10;ctx.shadowColor=weather.color;}
      ctx.beginPath();ctx.arc(p.x,p.y,p.radius*(0.7+velocity*0.08),0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;
    } else {
      p.x += (weather.wind*7 + p.vx*10)*dt;p.y += p.vy*velocity*8*dt;
      if(p.x>weatherWidth+20)p.x=-20;if(p.x < -20)p.x=weatherWidth+20;
      if(p.y>weatherHeight+20)p.y=-20;
      ctx.globalAlpha=weather.opacity*p.alpha*0.4;ctx.fillStyle=weather.color;
      ctx.beginPath();ctx.arc(p.x,p.y,p.radius+weather.glow*4,0,Math.PI*2);ctx.fill();
    }
  });
  ctx.restore();
  if(weather.type==="lightning")drawLightning(ctx,now);
  if(weather.type==="rain" && weather.glow>0.2 && Math.random()<0.03) {
    ctx.fillStyle="rgba(130,190,220,0.045)";ctx.fillRect(0,weatherHeight*0.88,weatherWidth,weatherHeight*0.12);
  }
}
function bindWeather() {
  const weatherPresets = {
    fine:{type:"rain",density:130,speed:4.8,wind:0.2,length:13,opacity:0.55,glow:0.18,color:"#D1F4FF",background:"#071722"},
    storm:{type:"rain",density:320,speed:8.5,wind:2.6,length:27,opacity:0.82,glow:0.5,color:"#B9E7FF",background:"#07101D"},
    downpour:{type:"rain",density:520,speed:10.2,wind:0.9,length:36,opacity:0.9,glow:0.25,color:"#D4F2FF",background:"#06101A"},
    snow:{type:"snow",density:190,speed:3.2,wind:0.3,length:13,opacity:0.88,glow:0.25,color:"#F1FCFF",background:"#172C40"},
    blizzard:{type:"snow",density:500,speed:8.4,wind:3.8,length:25,opacity:0.96,glow:0.16,color:"#FFFFFF",background:"#102333"},
    mist:{type:"mist",density:180,speed:2.2,wind:0.4,length:25,opacity:0.58,glow:0.72,color:"#B8D0D7",background:"#0B2027"},
    lightning:{type:"lightning",density:290,speed:7.4,wind:1.8,length:25,opacity:0.78,glow:0.9,color:"#B8C8FF",background:"#080B20"}
  };
  const applyWeatherPreset = () => {
    const preset=weatherPresets[$("weather-preset").value];
    if(!preset)return;
    Object.assign(weather,preset);weather.paused=false;syncWeatherInputs();
    status("Preset de clima aplicado à prévia: "+$("weather-preset").selectedOptions[0].textContent+".");
  };
  $("weather-type").addEventListener("change",()=>{weather.type=$("weather-type").value;syncWeatherInputs();});
  $("weather-preset").addEventListener("change",applyWeatherPreset);
  $("weather-apply-preset").addEventListener("click",applyWeatherPreset);
  ["density","speed","wind","length","opacity","glow","color","background"].forEach(key=>{
    $("weather-"+key).addEventListener("input",()=>{
      const value = $("weather-"+key).value;
      weather[key] = ["color","background"].includes(key) ? value : Number(value);
      const out = $("weather-"+key+"-value"); if(out)out.textContent=String(weather[key]);
      if(key==="density")resetWeatherParticles();
      $("weather-state").textContent="Prévia local · "+weather.type+" · "+weather.density+" partículas";
    });
  });
  $("weather-pause").addEventListener("click",()=>{weather.paused=!weather.paused;syncWeatherInputs();});
  $("weather-reset").addEventListener("click",()=>{
    weather={schema:"kellvyn-weather-studio/v1",type:"rain",density:200,speed:6,wind:0.6,length:20,opacity:0.72,glow:0.42,color:"#BCEEFF",background:"#071722",paused:false};
    syncWeatherInputs();status("Parâmetros de clima restaurados para chuva de referência.");
  });
  $("weather-export").addEventListener("click",()=>{
    const config={...weather,exportedAt:new Date().toISOString(),note:"Configuração do protótipo de partículas da Oficina. Não é código Android nativo nem afirma paridade automática com a chuva Lovable."};
    download("kellvyn-weather-"+weather.type+".json",JSON.stringify(config,null,2));
    status("Configuração climática exportada em JSON.");
  });
  $("weather-import-trigger").addEventListener("click",()=>$("weather-file").click());
  $("weather-file").addEventListener("change",async event=>{
    const file=event.target.files?.[0];if(!file)return;
    try {
      const parsed=JSON.parse(await file.text());
      if(parsed.schema!=="kellvyn-weather-studio/v1")throw new Error("Esse arquivo não é uma configuração climática da Oficina.");
      const limits={density:[10,650],speed:[0.2,12],wind:[-5,5],length:[2,60],opacity:[0.05,1],glow:[0,1]};
      Object.entries(limits).forEach(([key,range])=>weather[key]=Math.max(range[0],Math.min(range[1],Number(parsed[key])||range[0])));
      if(["rain","snow","mist","lightning"].includes(parsed.type))weather.type=parsed.type;
      if(/^#[0-9a-f]{6}$/i.test(parsed.color))weather.color=parsed.color;
      if(/^#[0-9a-f]{6}$/i.test(parsed.background))weather.background=parsed.background;
      weather.paused=false;syncWeatherInputs();status("Configuração climática carregada.");
    } catch(error) { alert("Não foi possível importar: "+error.message); }
    finally { event.target.value=""; }
  });
  syncWeatherInputs();resizeWeatherCanvas();new ResizeObserver(resizeWeatherCanvas).observe($("weather-preview"));
  requestAnimationFrame(drawWeather);
}
function bindPaletteActions() {
  renderPaletteGroups();renderPaletteLibrary();
  $("palette-search").addEventListener("input",renderPaletteLibrary);
  $("palette-group").addEventListener("change",renderPaletteLibrary);
  $("palette-active-color").addEventListener("input",()=>{$("palette-active-hex").textContent=$("palette-active-color").value.toUpperCase();});
  $("palette-apply-color").addEventListener("click",()=>{
    const ok=globalThis.StudioScene?.applySelectedColor?.($("palette-active-color").value);
    status(ok ? "Cor aplicada ao objeto selecionado: "+$("palette-active-color").value.toUpperCase() : "Selecione um objeto 3D antes de aplicar a cor.");
  });
  $("palette-custom-add").addEventListener("click",()=>{
    const color=$("palette-active-color").value.toUpperCase();
    let custom=palettes.find(p=>p.name==="Minha paleta");
    if(!custom){custom={name:"Minha paleta",group:"Personalizada",tags:"customizada pessoal",colors:[]};palettes.push(custom);}
    if(!custom.colors.includes(color))custom.colors.push(color);
    if(custom.colors.length>24)custom.colors.shift();
    if(![...$("palette-group").options].some(o=>o.value==="Personalizada")){
      const opt=document.createElement("option");opt.value="Personalizada";opt.textContent="Personalizada";$("palette-group").append(opt);
    }
    $("palette-group").value="Personalizada";renderPaletteLibrary();status("Cor incluída na sua paleta pessoal.");
  });
  $("palette-export").addEventListener("click",()=>{
    const output={schema:"kellvyn-resource-library/v1",exportedAt:new Date().toISOString(),palettes,materials,lightingPresets:lights};
    download("kellvyn-resource-library.json",JSON.stringify(output,null,2));
    status("Biblioteca de paletas e presets exportada em JSON.");
  });
}
function initializeResourceLab() {
  if(!$("resource-palette-panel"))return;
  renderPaletteLibrary();renderMaterials();renderLights();renderGeometry();
  bindPaletteActions();renderEasingOptions();bindWeather();
  $("resource-inventory").textContent=palettes.length+" paletas · "+palettes.reduce((sum,p)=>sum+p.colors.length,0)+" cores · "+materials.length+" materiais · "+lights.length+" luzes · "+geometryNames.length+" formas extras · 12 curvas";
  $("weather-state").textContent="Prévia local · chuva";
}
globalThis.StudioResourceLab = {
  get palettes(){return palettes;},get materials(){return materials;},get lightingPresets(){return lights;},
  get geometryNames(){return geometryNames;},
  selfTest:()=>({paletteCount:palettes.length,colorCount:palettes.reduce((sum,p)=>sum+p.colors.length,0),materialCount:materials.length,lightingPresetCount:lights.length,geometryPresetCount:geometryNames.length,easingOptions:12,weatherCanvasAvailable:Boolean($("weather-preview"))})
};
initializeResourceLab();
