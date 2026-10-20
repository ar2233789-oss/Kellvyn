const el = (id) => document.getElementById(id);
const all = (selector, root = document) => Array.from(root.querySelectorAll(selector));
const say = (id, value) => { const node = el(id); if (node) node.textContent = String(value); };
const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[char]));
const downloadText = (filename, content, mime = "text/plain;charset=utf-8") => {
  const blob = new Blob([content], {type:mime});
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url; anchor.download = filename; document.body.appendChild(anchor); anchor.click(); anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
};
const stamp = () => new Date().toISOString();
let THREE = null, OrbitControls = null, scene = null, renderer = null, camera = null, controls = null;
let ambientLight = null, directionalLight = null, gridHelper = null, ground = null, selected = null, selectionBox = null;
let sceneObjects = [], keyframes = [], playing = false, playStarted = 0, playOffset = 0, lastReport = null;
let showGrid = true, showShadows = true, currentView = "3d", sceneLoaded = false;
const defaultSceneName = "Cena sem título";
let sceneName = defaultSceneName;
const localProject = new Map();
const originalProject = new Map();
const patchedProject = new Map();
let activePatch = null;
const findings = [];

async function initializeRenderer() {
  const canvas = el("scene-canvas");
  try {
    const threeModule = await import("https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js");
    const controlsModule = await import("https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/controls/OrbitControls.js");
    THREE = threeModule; OrbitControls = controlsModule.OrbitControls;
    scene = new THREE.Scene();
    scene.background = new THREE.Color("#07120f");
    scene.fog = new THREE.Fog("#07120f", 18, 46);
    const width = Math.max(1, canvas.clientWidth), height = Math.max(1, canvas.clientHeight);
    camera = new THREE.PerspectiveCamera(48, width / height, 0.1, 200);
    camera.position.set(6, 5, 8);
    renderer = new THREE.WebGLRenderer({canvas, antialias:true, alpha:false, powerPreference:"high-performance"});
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setSize(width, height, false);
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1;
    controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true; controls.dampingFactor = 0.065; controls.target.set(0, 0.6, 0);
    ambientLight = new THREE.HemisphereLight("#d2ffe9", "#203e31", 0.65); scene.add(ambientLight);
    directionalLight = new THREE.DirectionalLight("#effff8", 2.2);
    directionalLight.position.set(4, 8, 5); directionalLight.castShadow = true;
    directionalLight.shadow.mapSize.set(1024, 1024); directionalLight.shadow.camera.near = 0.5;
    directionalLight.shadow.camera.far = 35; directionalLight.shadow.bias = -0.00015;
    directionalLight.shadow.radius = 4; scene.add(directionalLight);
    gridHelper = new THREE.GridHelper(20, 40, "#1f805b", "#173a2b");
    gridHelper.position.y = 0.001; scene.add(gridHelper);
    ground = new THREE.Mesh(new THREE.PlaneGeometry(200,200), new THREE.ShadowMaterial({color:"#000000", opacity:0.22}));
    ground.rotation.x = -Math.PI / 2; ground.position.y = -0.01; ground.receiveShadow = true; scene.add(ground);
    canvas.addEventListener("pointerup", pickObject);
    new ResizeObserver(resizeRenderer).observe(el("viewport"));
    makeObject("box", "Cubo 01", {x:0,y:0.5,z:0});
    sceneLoaded = true;
    say("render-status", "WebGL ativo · cena local");
    say("workspace-status", "Renderizador inicializado · projeto local");
    el("render-fallback").hidden = true;
    animate();
  } catch (error) {
    console.error("Não foi possível inicializar o renderizador:", error);
    say("render-status", "WebGL indisponível · modo de inspeção");
    say("workspace-status", "Interface carregada · renderizador indisponível");
    el("render-fallback").hidden = false;
    el("render-fallback").textContent = "Não foi possível carregar o renderizador 3D. Verifique WebGL/GPU e o acesso ao módulo Three.js. Os módulos de engenharia e QA continuam disponíveis.";
    const ctx = canvas.getContext("2d");
    if (ctx) { ctx.fillStyle = "#07120f"; ctx.fillRect(0,0,canvas.width,canvas.height); ctx.fillStyle="#779989"; ctx.font="13px system-ui"; ctx.fillText("Renderizador 3D indisponível",24,36); }
  }
}
function resizeRenderer() {
  if (!renderer || !camera) return;
  const canvas = el("scene-canvas"), width = Math.max(1, canvas.clientWidth), height = Math.max(1, canvas.clientHeight);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2)); renderer.setSize(width, height, false);
  if (camera.isPerspectiveCamera) { camera.aspect = width / height; }
  else { const aspect=width/height; camera.left=-7*aspect; camera.right=7*aspect; camera.top=7; camera.bottom=-7; }
  camera.updateProjectionMatrix();
}
function animate(now = 0) {
  requestAnimationFrame(animate);
  if (!scene || !renderer || !camera) return;
  if (controls) controls.update();
  if (playing) {
    const t = ((performance.now() - playStarted) / 1000 + playOffset) % 5;
    el("timeline-slider").value = String(t); updateTimeDisplay(t); applyTimeline(t);
  }
  if (currentView === "4d") applyFourthDimension(Number(el("timeline-slider").value));
  renderer.render(scene, camera);
  if (selectionBox && selected) selectionBox.update();
}
function makeObject(type, name, position = {x:0,y:0.5,z:0}, properties = {}) {
  if (!THREE || !scene) return null;
  let geometry;
  if (type === "sphere") geometry = new THREE.SphereGeometry(0.58, 40, 28);
  else if (type === "torus") geometry = new THREE.TorusKnotGeometry(0.42, 0.16, 100, 12);
  else if (type === "plane") geometry = new THREE.PlaneGeometry(1.8, 1.8, 1, 1);
  else geometry = new THREE.BoxGeometry(1, 1, 1);
  const material = new THREE.MeshStandardMaterial({
    color:properties.color || "#20d69a",
    metalness:Number(properties.metalness ?? 0.25),
    roughness:Number(properties.roughness ?? 0.42),
    transparent:Boolean(properties.transparent),
    opacity:Number(properties.opacity ?? 1),
    side:type === "plane" ? THREE.DoubleSide : THREE.FrontSide
  });
  const mesh = new THREE.Mesh(geometry, material);
  mesh.userData.baseColor = material.color.clone();
  mesh.name = name || type + " " + (sceneObjects.length + 1);
  mesh.position.set(Number(position.x)||0, Number(position.y)||0, Number(position.z)||0);
  if (properties.rotation) mesh.rotation.set(Number(properties.rotation.x)||0,Number(properties.rotation.y)||0,Number(properties.rotation.z)||0);
  if (properties.scale) mesh.scale.set(Number(properties.scale.x)||1,Number(properties.scale.y)||1,Number(properties.scale.z)||1);
  mesh.castShadow = properties.castShadow !== false; mesh.receiveShadow = properties.receiveShadow !== false;
  mesh.userData.kind = type; mesh.userData.w = Number(properties.w)||0;
  scene.add(mesh); sceneObjects.push(mesh); selectObject(mesh); updateSceneTree();
  say("scene-object-count", sceneObjects.length + (sceneObjects.length === 1 ? " objeto" : " objetos"));
  return mesh;
}
function selectObject(object) {
  selected = object || null;
  if (selectionBox && scene) { scene.remove(selectionBox); selectionBox.geometry.dispose(); selectionBox.material.dispose(); selectionBox=null; }
  if (selected && THREE && scene) {
    selectionBox = new THREE.BoxHelper(selected, "#42d9e8"); scene.add(selectionBox);
  }
  if (selected) {
    say("object-name", selected.name);
    ["x","y","z"].forEach(axis => { el("pos-"+axis).value = String(round(selected.position[axis])); });
    el("rot-y").value = String(round(THREE.MathUtils.radToDeg(selected.rotation.y)));
    ["x","y"].forEach(axis=>{ el("scale-"+axis).value=String(round(selected.scale[axis])); });
    el("object-color").value = "#" + selected.material.color.getHexString();
    say("color-hex", "#" + selected.material.color.getHexString().toUpperCase());
    el("metallic").value=String(selected.material.metalness); say("metallic-value",round(selected.material.metalness).toFixed(2));
    el("roughness").value=String(selected.material.roughness); say("roughness-value",round(selected.material.roughness).toFixed(2));
    el("opacity").value=String(selected.material.opacity); say("opacity-value",round(selected.material.opacity).toFixed(2));
    el("transparent").checked=selected.material.transparent; el("receive-shadow").checked=selected.receiveShadow;
    say("selection-note","Selecionado: "+selected.name+" · ID local "+selected.uuid.slice(0,8));
  } else {
    say("object-name",""); say("selection-note","Nenhum objeto selecionado.");
  }
  updateSceneTree();
}
function round(n) { return Math.round(Number(n)*1000)/1000; }
function updateSceneTree() {
  const root = el("scene-tree-list"); if (!root) return;
  root.replaceChildren();
  sceneObjects.forEach((object) => {
    const button=document.createElement("button"); button.className="tree-item"+(object===selected?" selected":"");
    button.style.width="100%"; button.style.textAlign="left"; button.type="button";
    const label=document.createElement("span"); label.textContent=object.name;
    const type=document.createElement("span"); type.className="tree-type"; type.textContent=object.userData.kind || "Mesh";
    button.append(label,type); button.addEventListener("click",()=>selectObject(object)); root.append(button);
  });
  say("scene-object-count",sceneObjects.length+(sceneObjects.length===1?" objeto":" objetos"));
}
function pickObject(event) {
  if (!THREE || !camera || !scene || event.button !== 0) return;
  const rect=el("scene-canvas").getBoundingClientRect();
  const pointer=new THREE.Vector2(((event.clientX-rect.left)/rect.width)*2-1,-((event.clientY-rect.top)/rect.height)*2+1);
  const raycaster=new THREE.Raycaster(); raycaster.setFromCamera(pointer,camera);
  const intersections=raycaster.intersectObjects(sceneObjects,false);
  if (intersections.length) selectObject(intersections[0].object);
}
function setView(view) {
  const previousView=currentView;
  if(previousView==="4d" && view!=="4d") resetFourthDimension();
  currentView=view;
  all("[data-view]").forEach(b=>b.classList.toggle("selected",b.dataset.view===view));
  say("viewport-label",view==="2d"?"Viewport · ortográfica / planta":view==="4d"?"Viewport · dimensão W experimental":"Viewport · perspectiva");
  say("viewport-hint",view==="2d"?"Vista ortográfica superior · arraste para navegar e use o zoom":view==="4d"?"W é um eixo experimental associado ao tempo; a cor e escala indicam o valor.":"Arraste para orbitar · rolagem para zoom · toque em um objeto para selecionar");
  if (!camera || !THREE) return;
  const viewport=el("viewport");
  if (view==="2d") {
    const aspect=Math.max(.1,viewport.clientWidth/Math.max(1,viewport.clientHeight));
    const cam=new THREE.OrthographicCamera(-7*aspect,7*aspect,7,-7,.1,100);
    cam.position.set(0,18,0.001); cam.up.set(0,0,-1); cam.lookAt(0,0,0); camera=cam;
    if (controls) controls.dispose();
    controls=new OrbitControls(camera,renderer.domElement); controls.enableRotate=false; controls.enableDamping=true; controls.target.set(0,0,0);
  } else {
    camera=new THREE.PerspectiveCamera(48,viewport.clientWidth/Math.max(1,viewport.clientHeight),.1,200);
    camera.position.set(6,5,8); if(controls)controls.dispose(); controls=new OrbitControls(camera,renderer.domElement); controls.enableDamping=true;controls.dampingFactor=.065;controls.target.set(0,.6,0);
  }
  resizeRenderer();
}
function applyFourthDimension(t) {
  if (!sceneObjects.length || !THREE) return;
  sceneObjects.forEach((object,index)=>{
    const w=(t/5)*2-1; object.userData.w=w;
    const hue=(0.42 + w*0.12 + index*0.015 + 1)%1;
    object.material.color.setHSL(hue,0.70,0.50);
    const pulse=1+Math.abs(w)*0.12;
    if (!object.userData.baseScale) object.userData.baseScale=object.scale.clone();
    object.scale.copy(object.userData.baseScale).multiplyScalar(pulse);
  });
}
function resetFourthDimension() {
  sceneObjects.forEach((object,index)=>{
    if (object.userData.baseColor) object.material.color.copy(object.userData.baseColor);
    if (object.userData.baseScale) object.scale.copy(object.userData.baseScale);
  });
}
function snapshotSelected(t) {
  if(!selected) { say("selection-note","Selecione um objeto antes de registrar uma pose."); return; }
  const frame={
    objectName:selected.name, objectId:selected.uuid, t:Number(t),
    position:{x:round(selected.position.x),y:round(selected.position.y),z:round(selected.position.z)},
    rotation:{x:round(selected.rotation.x),y:round(selected.rotation.y),z:round(selected.rotation.z)},
    scale:{x:round(selected.scale.x),y:round(selected.scale.y),z:round(selected.scale.z)}
  };
  keyframes=keyframes.filter(k=>!(k.objectId===frame.objectId && Math.abs(k.t-frame.t)<.015));
  keyframes.push(frame); keyframes.sort((a,b)=>a.t-b.t); updateKeyframes();
  say("selection-note","Pose registrada em "+frame.t.toFixed(2)+" s.");
}
function updateKeyframes() {
  const track=el("keyframe-track"); track.replaceChildren();
  keyframes.forEach((frame)=>{
    const dot=document.createElement("button"); dot.type="button"; dot.className="keyframe-dot";
    dot.style.left=(Math.max(0,Math.min(5,frame.t))/5*100)+"%"; dot.title=frame.objectName+" · "+frame.t.toFixed(2)+" s";
    dot.setAttribute("aria-label","Ir para pose "+frame.t.toFixed(2)+" segundos");
    dot.addEventListener("click",()=>{el("timeline-slider").value=String(frame.t);updateTimeDisplay(frame.t);applyTimeline(frame.t);});
    track.append(dot);
  });
  say("keyframe-count",keyframes.length+" "+(keyframes.length===1?"pose registrada":"poses registradas"));
}
function updateTimeDisplay(t) { say("timeline-time","00:"+String(Math.floor(t)).padStart(2,"0")+"."+String(Math.round((t%1)*100)).padStart(2,"0")); }
function applyTimeline(t) {
  if (!THREE) return;
  const targetFrames=keyframes.filter(k=>!selected||k.objectId===selected.uuid);
  if (!targetFrames.length) return;
  const left=targetFrames.filter(k=>k.t<=t).slice(-1)[0] || targetFrames[0];
  const right=targetFrames.find(k=>k.t>=t) || targetFrames[targetFrames.length-1];
  const factor=right.t===left.t?0:Math.max(0,Math.min(1,(t-left.t)/(right.t-left.t)));
  const object=sceneObjects.find(o=>o.uuid===left.objectId) || selected;
  if (!object || !left.position || !right.position) return;
  ["x","y","z"].forEach(axis=>{
    object.position[axis]=left.position[axis]+(right.position[axis]-left.position[axis])*factor;
    object.rotation[axis]=left.rotation[axis]+(right.rotation[axis]-left.rotation[axis])*factor;
    object.scale[axis]=left.scale[axis]+(right.scale[axis]-left.scale[axis])*factor;
  });
  if(object===selected)syncInspectorTransform();
}
function syncInspectorTransform() {
  if(!selected)return;
  ["x","y","z"].forEach(axis=>{el("pos-"+axis).value=String(round(selected.position[axis]));});
  el("rot-y").value=String(round(THREE.MathUtils.radToDeg(selected.rotation.y)));
  ["x","y"].forEach(axis=>{el("scale-"+axis).value=String(round(selected.scale[axis]));});
}
function stopPlayback() { playing=false;playOffset=Number(el("timeline-slider").value)||0;say("play-timeline","▶ Reproduzir"); }
function startPlayback() { if(playing){stopPlayback();return;} playing=true;playStarted=performance.now();playOffset=Number(el("timeline-slider").value)||0;say("play-timeline","Ⅱ Pausar"); }
function setTransform(axis,value) {
  if(!selected||!THREE)return;
  const n=Number(value); if(!Number.isFinite(n))return;
  if(axis==="rot-y") selected.rotation.y=THREE.MathUtils.degToRad(n);
  else if(axis.startsWith("pos-"))selected.position[axis.slice(-1)]=n;
  else if(axis.startsWith("scale-"))selected.scale[axis.slice(-1)]=Math.max(.05,n);
  if(selectionBox)selectionBox.update();
}
function updateSceneLighting() {
  if(!ambientLight||!directionalLight)return;
  ambientLight.intensity=Number(el("ambient-power").value);say("ambient-value",ambientLight.intensity.toFixed(2));
  directionalLight.intensity=Number(el("directional-power").value);say("directional-value",directionalLight.intensity.toFixed(2));
  directionalLight.color.set(el("light-color").value);showShadows=el("lighting-shadows").checked;
  renderer.shadowMap.enabled=showShadows;directionalLight.castShadow=showShadows; say("shadow-state",showShadows?"Ativo":"Desativado");
  sceneObjects.forEach(o=>{o.castShadow=showShadows;o.receiveShadow=el("receive-shadow").checked;});
  el("toggle-shadows").classList.toggle("selected",showShadows);
}
function sceneDocument() {
  return {
    schema:"kellvyn-studio-scene/v1", exportedAt:stamp(), sceneName:sceneName,
    note:"4D é uma representação experimental de W associado a tempo/cor/escala, não uma geometria 4D física.",
    settings:{view:currentView,showGrid:showGrid,showShadows:showShadows,ambientIntensity:ambientLight?ambientLight.intensity:.65,directionalIntensity:directionalLight?directionalLight.intensity:2.2},
    objects:sceneObjects.map(o=>({
      name:o.name,type:o.userData.kind||"box",
      position:{x:o.position.x,y:o.position.y,z:o.position.z},
      rotation:{x:o.rotation.x,y:o.rotation.y,z:o.rotation.z},
      scale:{x:o.scale.x,y:o.scale.y,z:o.scale.z},
      color:"#"+o.material.color.getHexString(),metalness:o.material.metalness,roughness:o.material.roughness,
      opacity:o.material.opacity,transparent:o.material.transparent,receiveShadow:o.receiveShadow,castShadow:o.castShadow,w:o.userData.w||0
    })),
    keyframes:keyframes.map(k=>JSON.parse(JSON.stringify(k)))
  };
}
function saveScene() { const doc=sceneDocument(); downloadText((sceneName.toLowerCase().replace(/[^a-z0-9]+/g,"-")||"kellvyn-scene")+".json",JSON.stringify(doc,null,2),"application/json"); say("workspace-status","Cena exportada · "+doc.objects.length+" objetos"); }
function clearSceneObjects() {
  if(!scene)return;
  sceneObjects.forEach(o=>{scene.remove(o);o.geometry.dispose();o.material.dispose();});
  sceneObjects=[];keyframes=[];selected=null;
  if(selectionBox){scene.remove(selectionBox);selectionBox.geometry.dispose();selectionBox.material.dispose();selectionBox=null;}
}
function loadSceneDocument(doc) {
  if(!doc||doc.schema!=="kellvyn-studio-scene/v1"||!Array.isArray(doc.objects))throw new Error("Arquivo não é uma cena válida da Oficina v1.");
  clearSceneObjects();sceneName=String(doc.sceneName||"Cena importada");say("project-label",sceneName);
  doc.objects.forEach((o,i)=>makeObject(["box","sphere","torus","plane"].includes(o.type)?o.type:"box",String(o.name||("Objeto "+(i+1))),o.position||{x:0,y:.5,z:0},o));
  const savedFrames=Array.isArray(doc.keyframes)?doc.keyframes.filter(k=>Number.isFinite(Number(k.t))):[];
  keyframes=savedFrames.map(frame=>{const match=sceneObjects.find(o=>o.name===frame.objectName);return {...frame,objectId:match?match.uuid:frame.objectId};});
  if(doc.settings){showGrid=doc.settings.showGrid!==false;gridHelper.visible=showGrid;showShadows=doc.settings.showShadows!==false;renderer.shadowMap.enabled=showShadows;directionalLight.castShadow=showShadows;if(Number.isFinite(doc.settings.ambientIntensity))ambientLight.intensity=doc.settings.ambientIntensity;if(Number.isFinite(doc.settings.directionalIntensity))directionalLight.intensity=doc.settings.directionalIntensity;}
  updateKeyframes();updateSceneTree();setView(["2d","3d","4d"].includes(doc.settings?.view)?doc.settings.view:"3d");say("workspace-status","Cena importada · "+sceneObjects.length+" objetos");
}
function bindDesignEvents() {
  all("[data-add]").forEach(button=>button.addEventListener("click",()=> {
    const type=button.dataset.add;makeObject(type,type.charAt(0).toUpperCase()+type.slice(1)+" "+(sceneObjects.length+1),{x:(Math.random()-.5)*2,y:.5,z:(Math.random()-.5)*2});
  }));
  all("[data-view]").forEach(button=>button.addEventListener("click",()=>setView(button.dataset.view)));
  el("object-name").addEventListener("change",()=>{if(selected){selected.name=el("object-name").value.trim()||"Objeto";updateSceneTree();}});
  ["pos-x","pos-y","pos-z","rot-y","scale-x","scale-y"].forEach(id=>el(id).addEventListener("change",()=>setTransform(id.replace(/^pos-/,"pos-"),el(id).value)));
  el("apply-color").addEventListener("click",()=>{if(selected){selected.material.color.set(el("object-color").value);selected.userData.baseColor=selected.material.color.clone();say("color-hex",el("object-color").value.toUpperCase());}});
  el("object-color").addEventListener("input",()=>say("color-hex",el("object-color").value.toUpperCase()));
  el("metallic").addEventListener("input",()=>{if(selected){selected.material.metalness=Number(el("metallic").value);say("metallic-value",Number(el("metallic").value).toFixed(2));}});
  el("roughness").addEventListener("input",()=>{if(selected){selected.material.roughness=Number(el("roughness").value);say("roughness-value",Number(el("roughness").value).toFixed(2));}});
  el("opacity").addEventListener("input",()=>{if(selected){selected.material.opacity=Number(el("opacity").value);selected.material.transparent=el("transparent").checked||selected.material.opacity<1;selected.material.needsUpdate=true;say("opacity-value",selected.material.opacity.toFixed(2));}});
  el("transparent").addEventListener("change",()=>{if(selected){selected.material.transparent=el("transparent").checked;selected.material.needsUpdate=true;}});
  el("receive-shadow").addEventListener("change",()=>{if(selected)selected.receiveShadow=el("receive-shadow").checked;});
  ["ambient-power","directional-power","light-color","lighting-shadows"].forEach(id=>el(id).addEventListener("input",updateSceneLighting));
  el("lighting-shadows").addEventListener("change",updateSceneLighting);
  el("add-light").addEventListener("click",()=>{if(!THREE||!scene)return;const light=new THREE.PointLight("#b3ffe0",1.4,15);light.position.set(1,3,1);light.name="Luz pontual "+(scene.children.filter(o=>o.isPointLight).length+1);scene.add(light);const marker=new THREE.Mesh(new THREE.SphereGeometry(.09,12,8),new THREE.MeshBasicMaterial({color:"#42d9e8"}));marker.position.copy(light.position);marker.name=light.name+" marcador";marker.userData.kind="light-marker";scene.add(marker);sceneObjects.push(marker);selectObject(marker);updateSceneTree();say("selection-note","Marcador de luz criado; a fonte luminosa fica separada na cena.");});
  el("delete-selected").addEventListener("click",()=>{if(!selected)return;const old=selected;scene.remove(old);old.geometry.dispose();old.material.dispose();sceneObjects=sceneObjects.filter(o=>o!==old);keyframes=keyframes.filter(k=>k.objectId!==old.uuid);selectObject(sceneObjects[0]||null);updateKeyframes();updateSceneTree();});
  el("frame-selected").addEventListener("click",()=>{if(!selected||!camera||!controls)return;controls.target.copy(selected.position);if(camera.isPerspectiveCamera){camera.position.copy(selected.position).add(new THREE.Vector3(4,3,5));}else{camera.position.x=selected.position.x;camera.position.z=selected.position.z;camera.position.y=18;}});
  el("toggle-grid").addEventListener("click",()=>{showGrid=!showGrid;if(gridHelper)gridHelper.visible=showGrid;el("toggle-grid").classList.toggle("selected",showGrid);});
  el("toggle-shadows").addEventListener("click",()=>{showShadows=!showShadows;el("lighting-shadows").checked=showShadows;updateSceneLighting();});
  el("reset-camera").addEventListener("click",()=>setView(currentView));
  el("add-keyframe").addEventListener("click",()=>snapshotSelected(Number(el("timeline-slider").value)));
  el("timeline-slider").addEventListener("input",()=>{if(playing)stopPlayback();const t=Number(el("timeline-slider").value);updateTimeDisplay(t);if(currentView!=="4d")applyTimeline(t);});
  el("play-timeline").addEventListener("click",startPlayback);
  all("[data-inspector]").forEach(button=>button.addEventListener("click",()=>{
    all("[data-inspector]").forEach(b=>b.classList.toggle("selected",b===button));
    all(".inspector-content").forEach(panel=>panel.classList.toggle("active",panel.id==="inspector-"+button.dataset.inspector));
  }));
  el("save-project").addEventListener("click",saveScene);
  el("open-project").addEventListener("click",()=>el("scene-file").click());
  el("scene-file").addEventListener("change",async(event)=>{
    const file=event.target.files?.[0];if(!file)return;
    try{loadSceneDocument(JSON.parse(await file.text()));}catch(error){alert("Não foi possível abrir a cena: "+error.message);}finally{event.target.value="";}
  });
}
function switchModule(name) {
  all("[data-module]").forEach(button=>button.classList.toggle("active",button.dataset.module===name));
  all(".module-panel").forEach(panel=>panel.classList.toggle("active",panel.id==="module-"+name));
  if(name==="design") resizeRenderer();
}
all("[data-module]").forEach(button=>button.addEventListener("click",()=>switchModule(button.dataset.module)));


globalThis.StudioScene = {
  initializeRenderer, bindDesignEvents, sceneDocument, loadSceneDocument, saveScene,
  get objects(){return sceneObjects;},
  get selected(){return selected;},
  get keyframes(){return keyframes;},
  get renderer(){return renderer;},
  get scene(){return scene;},
  get currentView(){return currentView;},
  get initialized(){return sceneLoaded;},
  switchModule,
  selfTest: () => ({
    moduleLoaded: Boolean(THREE && OrbitControls),
    rendererReady: Boolean(renderer && scene && camera),
    hasLights: Boolean(ambientLight && directionalLight),
    hasGroundAndGrid: Boolean(ground && gridHelper),
    sceneObjectCount: sceneObjects.length,
    keyframeStoreAvailable: Array.isArray(keyframes),
    shadowControlAvailable: Boolean(renderer && renderer.shadowMap)
  })
};
await initializeRenderer();
bindDesignEvents();
