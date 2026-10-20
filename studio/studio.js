import "./scene-engine.js";

const $ = (id) => document.getElementById(id);
const q = (selector, root = document) => Array.from(root.querySelectorAll(selector));
const reportDate = () => new Date().toISOString();
const textSafe = (value) => String(value ?? "").replace(/[&<>"']/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const files = new Map();
const originals = new Map();
let results = [];
let currentFilter = "all";
let currentReport = null;
let inspectedNames = [];
let patchPlan = null;
let dependencyRows = [];

function downloadFile(filename, content, mime) {
  const blob = new Blob([content], {type:mime || "text/plain;charset=utf-8"});
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a"); link.href=href; link.download=filename;
  document.body.append(link); link.click(); link.remove(); setTimeout(()=>URL.revokeObjectURL(href),1200);
}
function showEmptyProject(message) {
  $("engineering-findings").innerHTML='<div class="empty-state">'+textSafe(message)+'</div>';
}
function setImportSummary(message) { $("import-summary").textContent=message; }

async function importProject(fileList) {
  files.clear(); originals.clear(); dependencyRows=[]; patchPlan=null;
  const input=Array.from(fileList || []);
  if(!input.length){setImportSummary("Nenhum arquivo selecionado.");return;}
  const accepted=input.slice(0,500);
  let skipped=0,bytes=0;
  for(const file of accepted) {
    if(file.size>1_500_000 || bytes+file.size>20_000_000){skipped++;continue;}
    const path=(file.webkitRelativePath||file.name).replace(/^\.\//,"");
    const ext=path.split(".").pop().toLowerCase();
    const likelyText=/^(kt|kts|java|xml|gradle|properties|toml|json|yaml|yml|md|txt|js|mjs|ts|tsx|jsx|css|html|svg|pro|sh|bat|c|h|cpp|py|dart|rs|swift|plist|manifest|gitignore|lock)$/.test(ext) || /(^|\/)(Makefile|Dockerfile|gradlew|gradlew\.bat|package\.json|settings\.gradle|AndroidManifest\.xml)$/i.test(path);
    if(!likelyText || file.size===0){skipped++;continue;}
    try{
      const content=await file.text();
      if(content.includes("\u0000")){skipped++;continue;}
      files.set(path,content); originals.set(path,content);bytes+=file.size;
    }catch(error){skipped++;}
  }
  inspectedNames=Array.from(files.keys()).sort();
  $("patch-file").innerHTML="";
  if(inspectedNames.length){
    inspectedNames.forEach(path=>{const opt=document.createElement("option");opt.value=path;opt.textContent=path;$("patch-file").append(opt);});
    $("patch-file").disabled=false;
  } else {
    const option=document.createElement("option");option.textContent="Nenhum arquivo de texto reconhecido";option.value="";$("patch-file").append(option);$("patch-file").disabled=true;
  }
  setImportSummary(files.size+" arquivos de texto carregados · "+formatBytes(bytes)+(skipped?" · "+skipped+" itens ignorados por formato/tamanho":""));
  $("workspace-status").textContent="Projeto carregado localmente · "+files.size+" arquivos";
  renderArchitecture();
  renderDependencies();
  showEngineeringFindings([]);
  const output=$("export-patch");output.disabled=true;patchPlan=null;
  currentReport=null;
  $("export-report").disabled=true;$("export-report-md").disabled=true;
  $("qa-run-state").textContent="Projeto alterado · executar QA";
}
function formatBytes(n){if(n<1024)return n+" B";if(n<1024*1024)return (n/1024).toFixed(1)+" KB";return (n/(1024*1024)).toFixed(1)+" MB";}
function findPaths(regex){return inspectedNames.filter(p=>regex.test(p));}
function combinedText(){return Array.from(files.entries()).map(([p,c])=>"\n/* FILE: "+p+" */\n"+c).join("\n");}
function renderArchitecture(){
  const allText=combinedText(), android=/(^|\/)AndroidManifest\.xml$/mi.test(inspectedNames.join("\n")) || /com\.android\.application/.test(allText);
  const gradle=inspectedNames.filter(p=>/(^|\/)(build|settings)\.gradle(\.kts)?$/.test(p));
  const kotlin=inspectedNames.filter(p=>/\.(kt|java)$/.test(p));
  const tests=inspectedNames.filter(p=>/(^|\/)(test|androidTest)\//i.test(p)||/Test\.(kt|java|js|ts)$/.test(p));
  const assets=inspectedNames.filter(p=>/\.(png|jpe?g|webp|svg|glb|gltf|obj|mtl|fbx)$/i.test(p));
  const stats=[{value:files.size,label:"Arquivos"},{value:gradle.length,label:"Config. Gradle"},{value:kotlin.length,label:"Fontes Kotlin/Java"},{value:tests.length,label:"Arquivos de teste"},{value:assets.length,label:"Assets 2D/3D"},{value:android?"Android":"Genérico",label:"Tipo provável"}];
  $("architecture-summary").innerHTML=stats.map(s=>'<div class="arch-stat"><strong>'+textSafe(s.value)+'</strong><small>'+textSafe(s.label)+'</small></div>').join("");
}
function parseDependencies(){
  const found=[];
  const linePattern=/(implementation|api|compileOnly|runtimeOnly|testImplementation|androidTestImplementation|kapt|ksp|classpath|debugImplementation|releaseImplementation)\s*(?:\(\s*)?["']([^"'\s]+:[^"'\s]+:[^"'\s)]+)["']\s*\)?/g;
  const aliasPattern=/^\s*([A-Za-z0-9_.-]+)\s*=\s*\{\s*module\s*=\s*["']([^"']+)["']\s*,\s*version\s*=\s*["']([^"']+)["']/gm;
  for(const [path,content] of files){
    let m;
    while((m=linePattern.exec(content))!==null){
      const parts=m[2].split(":");if(parts.length<3)continue;
      found.push({configuration:m[1],group:parts[0],artifact:parts[1],version:parts.slice(2).join(":"),path:path,line:content.slice(0,m.index).split("\n").length});
    }
    if(/libs\.versions\.toml$/.test(path)){
      while((m=aliasPattern.exec(content))!==null){const p=m[2].split(":");found.push({configuration:"version catalog · "+m[1],group:p[0]||"",artifact:p[1]||"",version:m[3],path:path,line:content.slice(0,m.index).split("\n").length});}
    }
  }
  const seen=new Set();dependencyRows=found.filter(d=>{const k=[d.configuration,d.group,d.artifact,d.version,d.path].join("|");if(seen.has(k))return false;seen.add(k);return true;});
  return dependencyRows;
}
function renderDependencies(){
  const deps=parseDependencies();
  if(!deps.length){$("dependency-list").innerHTML='<div class="empty-state compact">Nenhuma coordenada Maven/Gradle explícita identificada. Catálogos indiretos e plugins podem exigir inspeção manual.</div>';return;}
  $("dependency-list").innerHTML=deps.map(d=>{
    const dynamic=/\+|latest\.|SNAPSHOT|\[|\]|\(|\)/i.test(d.version);
    return '<div class="dependency-item"><div><b>'+textSafe(d.group+":"+d.artifact)+'</b><span>'+textSafe(d.configuration)+' · '+textSafe(d.path)+':'+d.line+'</span></div><em>'+textSafe(d.version)+(dynamic?" · dinâmica":"")+'</em></div>';
  }).join("");
}
function showEngineeringFindings(items){
  if(!items.length){showEmptyProject(files.size?"Nenhum achado de engenharia foi emitido ainda. Execute a bateria QA.":"Carregue um projeto para ver os achados.");return;}
  $("engineering-findings").innerHTML=items.slice(0,80).map(r=>'<div class="finding"><div class="finding-top"><span class="severity '+r.status+'">'+r.status.toUpperCase()+'</span><b>'+textSafe(r.title)+'</b></div><p>'+textSafe(r.detail)+'</p>'+(r.evidence?'<p><code>'+textSafe(r.evidence)+'</code></p>':"")+'</div>').join("");
}
function addResult(status,category,title,detail,evidence){
  results.push({id:"QA-"+String(results.length+1).padStart(3,"0"),status:status,category:category,title:title,detail:detail,evidence:evidence||"",timestamp:reportDate()});
}
function checkFilesFor(pattern){return inspectedNames.some(p=>pattern.test(p));}
function sourceContaining(pattern){for(const [p,c] of files)if(pattern.test(p))return {path:p,content:c};return null;}
function matchingLines(regex){
  const matches=[];
  for(const [path,content] of files){const lines=content.split("\n");lines.forEach((line,i)=>{if(regex.test(line))matches.push({path,line:i+1,text:line.trim().slice(0,240)});regex.lastIndex=0;});}
  return matches;
}
function runStudioChecks(){
  const api=globalThis.StudioScene;
  if(!api){addResult("fail","studio","Cena / módulo visual","A API pública do motor visual não ficou disponível.","StudioScene ausente");return;}
  const check=api.selfTest();
  addResult(check.moduleLoaded?"pass":"fail","studio","Motor de renderização importado",check.moduleLoaded?"Three.js e OrbitControls foram carregados.":"Não foi possível carregar os módulos Three.js/OrbitControls; confira conectividade, CSP e WebGL.", "THREE="+check.moduleLoaded);
  addResult(check.rendererReady?"pass":"fail","studio","Canvas, câmera e cena",check.rendererReady?"Câmera e renderizador WebGL estão ativos.":"Renderizador/câmera não inicializados; teste visual interativo não pode ser aprovado.", "rendererReady="+check.rendererReady);
  addResult(check.hasLights?"pass":"fail","studio","Rig de iluminação",check.hasLights?"Luz ambiente e direcional disponíveis.":"Falta uma ou mais fontes de luz previstas.","ambientLight/directionalLight");
  addResult(check.hasGroundAndGrid?"pass":"warn","studio","Grade e plano receptor",check.hasGroundAndGrid?"Grade e plano de sombras presentes.":"Grade/plano indisponível ou renderizador em fallback.","gridHelper/ground");
  addResult(check.sceneObjectCount>0?"pass":"warn","studio","Geometria inicial",check.sceneObjectCount>0?"A cena contém "+check.sceneObjectCount+" objeto(s).":"A cena não tem objetos para inspecionar.","objectCount="+check.sceneObjectCount);
  addResult(check.keyframeStoreAvailable?"pass":"fail","studio","Armazenamento de keyframes",check.keyframeStoreAvailable?"O armazenamento de poses está disponível.":"A estrutura de keyframes não foi inicializada.","keyframeStoreAvailable="+check.keyframeStoreAvailable);
  addResult(check.shadowControlAvailable?"pass":"warn","studio","Controle de sombras",check.shadowControlAvailable?"Há controle de shadow map disponível; a qualidade depende da GPU.":"Controle de shadow map indisponível.","shadowControl="+check.shadowControlAvailable);
  const duplicateIds=new Set(),ids=new Set();let duplicates=0;
  q("[id]").forEach(node=>{if(ids.has(node.id)){duplicates++;duplicateIds.add(node.id);}ids.add(node.id);});
  addResult(duplicates===0?"pass":"fail","studio","IDs únicos na interface",duplicates===0?"Nenhum ID duplicado encontrado no documento.":"IDs duplicados: "+Array.from(duplicateIds).join(", "),"count="+duplicates);
  const required=["scene-canvas","module-design","module-engineering","module-testing","project-files","run-tests","prepare-patch","export-report"];
  const missing=required.filter(id=>!$(id));
  addResult(missing.length?"fail":"pass","studio","Contratos entre interface e scripts",missing.length?"Elementos exigidos não encontrados: "+missing.join(", "):"Todos os principais pontos de entrada da oficina existem.","required="+required.join(","));
  const hasNoReplit=!/replit/i.test(document.documentElement.innerText);
  addResult(hasNoReplit?"pass":"fail","studio","Restrição de plataforma",hasNoReplit?"A interface não faz referência à plataforma proibida.":"A interface contém uma referência inesperada à plataforma proibida.","text scan");
}
function runProjectChecks(selectedChecks){
  const content=combinedText(),names=inspectedNames.join("\n");
  if(!files.size){addResult("warn","project","Projeto de origem","Nenhum projeto de código foi carregado. Só os autotestes internos da oficina podem ser executados.","Aguardando arquivos");return;}
  const rootGradle=checkFilesFor(/(^|\/)(build|settings)\.gradle(\.kts)?$/);
  const manifestPath=findPaths(/(^|\/)AndroidManifest\.xml$/i)[0];
  const isAndroid=rootGradle||Boolean(manifestPath)||/com\.android\.application|androidx\.compose/i.test(content);
  if(selectedChecks.has("structure")){
    addResult("pass","structure","Inventário de arquivos","Foram inspecionados "+files.size+" arquivos textuais.","total="+files.size+"; bytes="+Array.from(files.values()).reduce((n,c)=>n+c.length,0));
    const wrapper=checkFilesFor(/(^|\/)gradlew(\.bat)?$/);
    addResult(!isAndroid||wrapper?"pass":"warn","structure","Gradle Wrapper",!isAndroid?"Não é um projeto Android/Gradle reconhecido; Wrapper não se aplica automaticamente.":wrapper?"Wrapper localizado.":"Não foi encontrado gradlew/gradlew.bat; a reprodutibilidade do build pode depender de Gradle instalado.","gradlew presence="+wrapper);
    const sourceFiles=findPaths(/\.(kt|java|js|ts|tsx|jsx|py|dart|swift|rs)$/i);
    addResult(sourceFiles.length?"pass":"warn","structure","Fontes de código",sourceFiles.length+" arquivos de código identificados.",sourceFiles.slice(0,8).join(", ")||"nenhum");
  }
  if(selectedChecks.has("android")){
    if(!isAndroid){
      addResult("warn","android","Projeto Android","Não foi possível confirmar que os arquivos formam um projeto Android/Gradle.","Gradle ou AndroidManifest não identificado");
    } else {
      addResult(manifestPath?"pass":"fail","android","AndroidManifest.xml",manifestPath?"Manifesto localizado.":"Projeto parece Android, mas nenhum AndroidManifest.xml foi encontrado.",manifestPath||"missing manifest");
      const appConfig=findPaths(/(^|\/)app\/build\.gradle(\.kts)?$|(^|\/)build\.gradle(\.kts)?$/);
      const gradleContent=appConfig.map(p=>files.get(p)||"").join("\n");
      addResult(appConfig.length?"pass":"fail","android","Configuração de build",appConfig.length?"Configuração Gradle encontrada: "+appConfig.join(", "):"Projeto Android sem build.gradle(.kts) conhecido.","gradleFiles="+appConfig.join(","));
      const hasSdk=/compileSdk(?:Version)?\s*(?:=\s*)?\d+|compileSdk\s*\(/.test(gradleContent);
      addResult(hasSdk?"pass":"warn","android","compileSdk definido",hasSdk?"Encontrada uma declaração de compileSdk.":"Não foi possível identificar compileSdk com a expressão estática; pode estar centralizado em convention plugins.",gradleContent.split("\n").filter(l=>/compileSdk/.test(l)).slice(0,3).join(" | "));
      const hasNamespace=/namespace\s*(?:=\s*)?["']/.test(gradleContent)||/applicationId\s*(?:=\s*)?["']/.test(gradleContent);
      addResult(hasNamespace?"pass":"warn","android","Identidade do app",hasNamespace?"namespace/applicationId textual identificado.":"Não foi identificada namespace/applicationId no trecho de configuração lido.","namespace / applicationId");
      if(manifestPath){
        const manifest=files.get(manifestPath)||"";
        addResult(/<manifest\b/.test(manifest)&&/<application\b/.test(manifest)?"pass":"fail","android","Estrutura XML do manifesto",/<manifest\b/.test(manifest)&&/<application\b/.test(manifest)?"Elementos manifest e application encontrados.":"Manifesto sem uma estrutura básica identificável.","manifest path="+manifestPath);
      }
    }
  }
  if(selectedChecks.has("dependencies")){
    const deps=parseDependencies(),dynamic=deps.filter(d=>/\+|latest\.|SNAPSHOT|\[|\]/i.test(d.version));
    addResult(deps.length?"pass":"warn","dependencies","Referências Maven/Gradle",deps.length ? deps.length+" declarações de dependência extraídas." : "Nenhuma dependência Maven explícita foi extraída; aliases podem exigir resolução adicional.", "count="+deps.length);
    addResult(dynamic.length?"warn":"pass","dependencies","Versões dinâmicas",dynamic.length?"Encontradas "+dynamic.length+" referências dinâmicas ou SNAPSHOT: "+dynamic.slice(0,5).map(d=>d.group+":"+d.artifact+":"+d.version).join("; "):"Nenhuma versão dinâmica/SNAPSHOT reconhecida no padrão textual.","dynamic="+dynamic.length);
    const catalogs=findPaths(/libs\.versions\.toml$/);
    addResult(catalogs.length?"pass":"warn","dependencies","Version catalogs",catalogs.length?"Catálogos identificados: "+catalogs.join(", "):"Não há libs.versions.toml conhecido; dependências ainda podem ser gerenciadas por outro mecanismo.",catalogs.join(", ")||"none");
    addResult("warn","dependencies","Vulnerabilidades conhecidas","Não foi consultada uma base de CVEs. Esta verificação analisa versões declaradas, mas não certifica a ausência de vulnerabilidades.","Sem feed de CVE conectado");
  }
  if(selectedChecks.has("ui")){
    const ui=sourceContaining(/(MainActivity\.(kt|java)|App\.(tsx|jsx)|main\.(tsx|jsx|js)|index\.(html|tsx|jsx))/i);
    addResult(ui?"pass":"warn","ui","Ponto de entrada da interface",ui?"Arquivo provável de interface encontrado.":"Não foi identificado MainActivity/App/main/index entre os arquivos carregados.",ui?ui.path:"no entry point");
    const handlers=matchingLines(/setOnClickListener|onClick\s*=|addEventListener\(|\.click\s*\(/i);
    addResult(handlers.length?"pass":"warn","ui","Eventos/handlers de UI",handlers.length+" referências textuais de eventos reconhecidas. Isso não prova que cada botão seja funcional.",handlers.slice(0,6).map(m=>m.path+":"+m.line+" "+m.text).join(" | "));
    const placeholders=matchingLines(/TODO|FIXME|NotImplementedError|IMPLEMENT_ME|throw\s+new\s+Error\(['"]not implemented/i);
    addResult(placeholders.length?"warn":"pass","ui","Marcadores de implementação",placeholders.length?"Encontrados "+placeholders.length+" marcadores/lançamentos de erro possivelmente pendentes.":"Nenhum marcador comum de implementação pendente localizado.",placeholders.slice(0,7).map(m=>m.path+":"+m.line+" "+m.text).join(" | ")||"none found");
  }
  if(selectedChecks.has("security")){
    const secrets=matchingLines(/(api[_-]?key|client[_-]?secret|access[_-]?token|password|passwd|private[_-]?key)\s*[:=]\s*["'][A-Za-z0-9_\-./+=]{12,}["']/i).filter(m=>!/(example|placeholder|your_|changeme|replace_me|dummy|test_)/i.test(m.text));
    addResult(secrets.length?"fail":"pass","security","Possíveis segredos embutidos",secrets.length?"Detectados "+secrets.length+" valores longos semelhantes a segredo em texto. Revise sem publicar os valores no relatório.":"Nenhum padrão simples de credencial embutida foi detectado; isso não equivale a uma auditoria completa.",secrets.slice(0,5).map(m=>m.path+":"+m.line).join(", ")||"pattern scan");
    const webview=matchingLines(/WebView|android.webkit.WebView|loadUrl\(/i);
    addResult(webview.length?"fail":"pass","security","Superfície WebView",webview.length?"Referências a WebView/loadUrl encontradas. Para o app Kellvyn nativo isso exige revisão explícita; não implica vulnerabilidade por si só.":"Nenhuma referência a WebView/loadUrl encontrada no texto escaneado.",webview.slice(0,5).map(m=>m.path+":"+m.line+" "+m.text).join(" | ")||"none found");
    const net=matchingLines(/INTERNET|usesCleartextTraffic|http:\/\//i);
    addResult(net.some(m=>/usesCleartextTraffic|http:\/\//i.test(m.text))?"warn":"pass","security","Configuração de rede","Referências relacionadas a rede: "+net.length+". URLs HTTP ou tráfego claro pedem revisão; conexões HTTPS podem ser esperadas.","evidence="+net.slice(0,5).map(m=>m.path+":"+m.line).join(", "));
  }
  if(selectedChecks.has("tests")){
    const tests=findPaths(/(^|\/)(test|androidTest)\//i).concat(findPaths(/(Test|Spec)\.(kt|java|js|ts|tsx)$/i));
    addResult(tests.length?"pass":"warn","tests","Cobertura automatizada declarada",tests.length?tests.length+" arquivos/rótulos de testes foram encontrados. A existência não prova que executaram.":"Nenhuma localização/arquivo de teste convencional foi encontrado.",tests.slice(0,8).join(", ")||"none");
    const actions=findPaths(/(^|\/)(\.github\/workflows\/|\.gitlab-ci|Jenkinsfile)/i);
    addResult(actions.length?"pass":"warn","tests","Pipeline de CI",actions.length?"Arquivos de pipeline encontrados: "+actions.join(", "):"Não foi encontrado pipeline CI nos arquivos carregados.",actions.join(", ")||"none");
  }
  if(selectedChecks.has("parity")){
    const references=findPaths(/(PRODUCT_SPEC|DESIGN_SPEC|UI_SPEC|layout|wireframe|reference|lovable|screenshot)/i);
    const animation=matchingLines(/animate|animation|Canvas|graphicsLayer|drawWithCache|transition|keyframe|shadow|elevation/i);
    addResult(references.length?"pass":"warn","parity","Referência de interface preservada",references.length?"Possíveis specs/referências visuais encontradas: "+references.slice(0,8).join(", "):"Nenhuma spec/captura/referência foi carregada; a igualdade visual não pode ser comprovada por análise estática.","referenceFiles="+references.length);
    addResult(animation.length?"pass":"warn","parity","Animação/sombra declarada",animation.length?animation.length+" referências textuais a animação, canvas ou sombras foram encontradas.":"Nenhum marcador comum de animação/sombra foi localizado no texto lido.",animation.slice(0,6).map(m=>m.path+":"+m.line+" "+m.text).join(" | ")||"none");
  }
}
function runTests(options = {}) {
  results=[];
  const selected=new Set(q("[data-check]").filter(x=>x.checked).map(x=>x.dataset.check));
  if(options.selectedOnly && selected.size===0) addResult("warn","qa","Nenhuma categoria selecionada","Marque pelo menos uma categoria antes de executar.","0 selecionadas");
  else {
    runStudioChecks();
    runProjectChecks(options.selectedOnly ? selected : new Set(["structure","android","dependencies","ui","security","tests","parity"]));
  }
  const counts={pass:results.filter(r=>r.status==="pass").length,warn:results.filter(r=>r.status==="warn").length,fail:results.filter(r=>r.status==="fail").length};
  const max=results.length;
  say("passed-count",counts.pass);say("warning-count",counts.warn);say("failed-count",counts.fail);say("coverage-count",max?Math.round(((counts.pass+counts.warn+counts.fail)/Math.max(1,max))*100)+"%":"—");
  $("qa-run-state").textContent="Executado · "+new Date().toLocaleTimeString("pt-BR");
  currentReport={name:sceneName,createdAt:reportDate(),projectFiles:inspectedNames.slice(),counts:counts,results:results.slice(),disclaimer:"A análise estática não executa Gradle, não valida instalação em aparelho físico e não consulta um feed de CVEs. Não interpretar resultados como build aprovado."};
  $("export-report").disabled=false;$("export-report-md").disabled=false;
  renderResults();showEngineeringFindings(results.filter(r=>r.category!=="studio"));
}
function renderResults(){
  const filtered=results.filter(r=>currentFilter==="all"||(currentFilter==="fail"&&r.status==="fail")||(currentFilter==="warn"&&r.status==="warn")||(currentFilter==="pass"&&r.status==="pass"));
  if(!filtered.length){$("test-results").innerHTML='<div class="empty-state">Nenhum resultado nesta categoria.</div>';return;}
  $("test-results").innerHTML=filtered.map(r=>'<div class="test-result"><div class="test-result-head"><span class="status-pill '+r.status+'">'+r.status.toUpperCase()+'</span><b>'+textSafe(r.title)+'</b><span class="neutral-pill">'+textSafe(r.category)+'</span></div><p>'+textSafe(r.detail)+'</p>'+(r.evidence?'<div class="evidence">'+textSafe(r.evidence)+'</div>':"")+'</div>').join("");
}
function auditDependenciesOnly(){
  const deps=parseDependencies(),dynamic=deps.filter(d=>/\+|latest\.|SNAPSHOT|\[|\]/i.test(d.version));
  if(!deps.length){$("dependency-list").innerHTML='<div class="empty-state compact">Nenhuma coordenada conhecida encontrada nos arquivos carregados.</div>';return;}
  $("dependency-list").innerHTML=deps.map(d=>{
    const isDynamic=/\+|latest\.|SNAPSHOT|\[|\]/i.test(d.version);
    return '<div class="dependency-item"><div><b>'+textSafe(d.group+":"+d.artifact)+'</b><span>'+textSafe(d.path)+":"+d.line+'</span></div><em>'+textSafe(d.version)+(isDynamic?" · revisar":" · fixa")+'</em></div>';
  }).join("");
  const old=results;
  results=[];
  addResult(dynamic.length?"warn":"pass","dependencies","Dependências declaradas",deps.length+" declarações identificadas; "+dynamic.length+" usam versão dinâmica ou SNAPSHOT.","total="+deps.length+" dynamic="+dynamic.length);
  addResult("warn","dependencies","Feed de vulnerabilidades","A oficina não consultou CVE/OSV/Maven advisories online. Não afirma que as dependências estão livres de vulnerabilidades.","Somente inspeção textual local");
  showEngineeringFindings(results);
  results=old;
}
function preparePatch(){
  const path=$("patch-file").value,needle=$("patch-find").value,replacement=$("patch-replace").value,unique=$("patch-unique").checked;
  const out=$("patch-result");patchPlan=null;$("export-patch").disabled=true;
  if(!path||!files.has(path)){out.className="patch-result fail";out.textContent="Selecione um arquivo de texto carregado.";return;}
  if(!needle.trim()){out.className="patch-result fail";out.textContent="O trecho de busca não pode ficar vazio.";return;}
  const source=files.get(path),count=source.split(needle).length-1;
  if(count===0){out.className="patch-result fail";out.textContent="Trecho não localizado no arquivo. Nenhuma modificação foi aplicada.";return;}
  if(unique&&count!==1){out.className="patch-result fail";out.textContent="Foram encontradas "+count+" ocorrências; a regra exige uma única ocorrência. Refine o trecho.";return;}
  const proposed=source.split(needle).join(replacement);
  if(proposed===source){out.className="patch-result fail";out.textContent="A substituição não alteraria o arquivo.";return;}
  patchPlan={path:path,source:source,proposed:proposed,occurrences:count,createdAt:reportDate()};
  files.set(path,proposed);
  out.className="patch-result ok";out.textContent="Patch validado em memória: "+count+" ocorrência(s). Original preservado em memória; arquivo no dispositivo ainda não foi alterado. Use Exportar para baixar a nova cópia.";
  $("export-patch").disabled=false;
  const option=$("patch-file").selectedOptions[0];if(option)option.textContent=path+" · patch preparado";
  renderArchitecture();renderDependencies();
}
function exportPatchedFile(){
  if(!patchPlan)return;
  const basename=patchPlan.path.split("/").pop();
  downloadFile(basename+".patched",patchPlan.proposed,"text/plain;charset=utf-8");
  downloadFile(basename+".patch.json",JSON.stringify({schema:"kellvyn-studio-patch/v1",createdAt:patchPlan.createdAt,path:patchPlan.path,occurrences:patchPlan.occurrences,originalHash:null,proposedHash:null,warning:"Revisar diff antes de substituir o arquivo original.",content:patchPlan.proposed},null,2),"application/json");
}
function exportJSON(){
  if(!currentReport)return;
  const report={schema:"kellvyn-studio-qa-report/v1",...currentReport,dependencyRows:dependencyRows,patchPlan:patchPlan?{path:patchPlan.path,occurrences:patchPlan.occurrences,createdAt:patchPlan.createdAt}:null};
  downloadFile("kellvyn-qa-report.json",JSON.stringify(report,null,2),"application/json");
}
function exportMarkdown(){
  if(!currentReport)return;
  const out=[
    "# Relatório QA — Oficina de Produção",
    "",
    "- Data: "+currentReport.createdAt,
    "- Arquivos carregados: "+currentReport.projectFiles.length,
    "- Aprovados: "+currentReport.counts.pass,
    "- Alertas: "+currentReport.counts.warn,
    "- Falhas: "+currentReport.counts.fail,
    "",
    "## Resultados",
    "",
    ...currentReport.results.map(r=>"### ["+r.status.toUpperCase()+"] "+r.title+" ("+r.category+")\n"+r.detail+(r.evidence?"\n\nEvidência: "+r.evidence:"")),
    "",
    "## Dependências detectadas",
    "",
    ...(dependencyRows.length?dependencyRows.map(d=>"- "+d.group+":"+d.artifact+":"+d.version+" — "+d.configuration+" — "+d.path+":"+d.line):["Nenhuma dependência identificada."]),
    "",
    "## Limites da validação",
    "",
    currentReport.disclaimer,
    "Uma aprovação estática não é uma confirmação de compilação, de desempenho em GPU, de instalação ou de equivalência visual no dispositivo."
  ].join("\n");
  downloadFile("kellvyn-qa-report.md",out,"text/markdown;charset=utf-8");
}
function bindEngineering(){
  $("project-files").addEventListener("change",e=>importProject(e.target.files));
  $("audit-dependencies").addEventListener("click",auditDependenciesOnly);
  $("prepare-patch").addEventListener("click",preparePatch);
  $("export-patch").addEventListener("click",exportPatchedFile);
  $("run-all").addEventListener("click",()=>{switchToTesting();runTests({selectedOnly:false});});
  $("run-tests").addEventListener("click",()=>runTests({selectedOnly:false}));
  $("run-selected-tests").addEventListener("click",()=>runTests({selectedOnly:true}));
  $("export-report").addEventListener("click",exportJSON);
  $("export-report-md").addEventListener("click",exportMarkdown);
  q("[data-filter]").forEach(button=>button.addEventListener("click",()=>{currentFilter=button.dataset.filter;q("[data-filter]").forEach(b=>b.classList.toggle("selected",b===button));renderResults();}));
}
function switchToTesting(){
  q("[data-module]").forEach(button=>button.classList.toggle("active",button.dataset.module==="testing"));
  q(".module-panel").forEach(panel=>panel.classList.toggle("active",panel.id==="module-testing"));
}
function selfCheckHarness(){
  const cases=[
    ["módulo de cena publicado",()=>Boolean(globalThis.StudioScene)],
    ["engine expõe autotestes",()=>typeof globalThis.StudioScene?.selfTest==="function"],
    ["entrada de arquivos presente",()=>$("project-files")?.type==="file"],
    ["validador de patch não aceita busca vazia",()=>$("patch-find")!==null],
    ["exportação JSON presente",()=>$("export-report")!==null],
    ["filtros de resultado presentes",()=>q("[data-filter]").length===4],
    ["três módulos declarados",()=>q("[data-module]").length===3],
    ["sem URLs HTTP para lógica de projeto",()=>!Array.from(document.scripts).some(s=>s.src.startsWith("http:"))]
  ];
  return cases.map(([label,fn])=>({label,pass:Boolean(fn())}));
}
function startup(){
  bindEngineering();
  const cases=selfCheckHarness();
  const failures=cases.filter(c=>!c.pass);
  if(failures.length){console.error("Autoteste de interface falhou",failures);$("workspace-status").textContent="Aviso: autoteste de interface";} 
  else $("workspace-status").textContent="Oficina pronta · 3 módulos independentes";
  const navRepo=document.querySelector(".footer a");
  if(navRepo)navRepo.href="https://github.com/ar2233789-oss/Kellvyn/tree/studio-production-suite";
}
startup();
